package com.settlementpressure.base;

import com.settlementpressure.config.SPConfig;
import com.settlementpressure.region.RegionType;
import com.settlementpressure.server.ServerState;
import com.settlementpressure.structure.StructureManager;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Difficulty;
import net.minecraft.world.level.ChunkPos;

/**
 * Derives bases from the {@link StructureManager}, tracks activation, computes threat values and
 * classifies every chunk as SAFE / PERIPHERY / WILD. Transient (rebuilt on every server start).
 */
public final class BaseManager {
    private final StructureManager structures;

    private final List<Base> bases = new ArrayList<>();
    private final Map<Long, Base> chunkToBase = new HashMap<>();
    private final Map<Long, RegionInfo> regions = new HashMap<>();

    private long lastDecayTick = Long.MIN_VALUE;
    private long lastRebuildTick = Long.MIN_VALUE;
    private long lastActivationTick = Long.MIN_VALUE;

    public BaseManager(StructureManager structures) {
        this.structures = structures;
    }

    public void tick(ServerLevel level, long nowTick) {
        if (lastDecayTick == Long.MIN_VALUE || nowTick - lastDecayTick >= SPConfig.DECAY_RECOMPUTE_INTERVAL.get()) {
            structures.refreshDecay(nowTick, maintainedChunks(level));
            lastDecayTick = nowTick;
        }
        boolean changed = false;
        if (lastRebuildTick == Long.MIN_VALUE || nowTick - lastRebuildTick >= SPConfig.BASE_REBUILD_INTERVAL.get()) {
            rebuild(level);
            lastRebuildTick = nowTick;
            changed = true;
        }
        if (lastActivationTick == Long.MIN_VALUE || nowTick - lastActivationTick >= SPConfig.ACTIVATION_CHECK_INTERVAL.get()) {
            updateActivation(level);
            lastActivationTick = nowTick;
            changed = true;
        }
        if (changed) {
            rebuildRegions(level); // threat + regions depend on the latest bases and activation
        }
    }

    /** Chunks of bases that currently have an online player inside (their activity maintains them). */
    private Set<Long> maintainedChunks(ServerLevel level) {
        Set<Long> maintained = new HashSet<>();
        for (ServerPlayer player : level.players()) {
            Base base = chunkToBase.get(player.chunkPosition().toLong());
            if (base != null) {
                maintained.addAll(base.chunks);
            }
        }
        return maintained;
    }

    // --- detection ---------------------------------------------------------

    private void rebuild(ServerLevel level) {
        Map<Long, Double> scored = structures.chunkScores();

        // A chunk qualifies as a base seed when its 3x3 neighbourhood total reaches the threshold
        // and at least minScoredChunks chunks in that neighbourhood have score.
        Set<Long> seeds = new HashSet<>();
        double threshold = SPConfig.STRUCTURE_THRESHOLD.get();
        int minChunks = SPConfig.MIN_SCORED_CHUNKS.get();
        for (long ck : scored.keySet()) {
            if (neighbourhoodQualifies(ck, scored, threshold, minChunks)) {
                seeds.add(ck);
            }
        }

        // Flood fill through scored chunks to form connected base networks (expansion + merging).
        Set<Long> visited = new HashSet<>();
        List<Base> newBases = new ArrayList<>();
        for (long seed : seeds) {
            if (visited.contains(seed)) {
                continue;
            }
            Base base = new Base();
            Deque<Long> stack = new ArrayDeque<>();
            stack.push(seed);
            while (!stack.isEmpty()) {
                long ck = stack.pop();
                if (!visited.add(ck)) {
                    continue;
                }
                if (!scored.containsKey(ck)) {
                    continue;
                }
                base.chunks.add(ck);
                ChunkPos cp = new ChunkPos(ck);
                for (int dx = -1; dx <= 1; dx++) {
                    for (int dz = -1; dz <= 1; dz++) {
                        if (dx == 0 && dz == 0) {
                            continue;
                        }
                        long nk = ChunkPos.asLong(cp.x + dx, cp.z + dz);
                        if (scored.containsKey(nk) && !visited.contains(nk)) {
                            stack.push(nk);
                        }
                    }
                }
            }
            if (!base.chunks.isEmpty()) {
                double total = 0.0;
                for (long ck : base.chunks) {
                    total += scored.getOrDefault(ck, 0.0);
                }
                base.structureScore = total;
                newBases.add(base);
            }
        }

        // Determine each base's grace state from the persisted per-chunk formed day,
        // and detect merges / splits / abandonments vs the previous rebuild.
        long nowDayTime = ServerState.totalDayTime();
        long protection = SPConfig.newbieProtectionTicks();
        List<Base> previous = new ArrayList<>(bases);

        // An old base survives if any of its chunks are still part of a new base (kept/merged).
        // Otherwise it was genuinely abandoned (dismantled or decayed away).
        Set<Base> survivingOld = new HashSet<>();
        for (Base old : previous) {
            int containedIn = 0;
            for (Base nb : newBases) {
                if (overlapCount(nb.chunks, old.chunks) > 0) {
                    containedIn++;
                    survivingOld.add(old);
                }
            }
            if (containedIn >= 2) {
                announceToNearby(level, old, "settlementpressure.msg.base_split",
                        "定居点被分割开来，怪物们失去了原有的目标。");
            }
        }
        for (Base nb : newBases) {
            int absorbedOld = 0;
            for (Base old : previous) {
                if (overlapCount(nb.chunks, old.chunks) > 0) {
                    absorbedOld++;
                }
            }
            if (absorbedOld >= 2) {
                announceToNearby(level, nb, "settlementpressure.msg.base_merged",
                        "两座基地已完成对接，合为更大的定居点，危险也随之提升。");
            }
        }

        for (Base base : newBases) {
            long formedDay = 0L;
            for (long ck : base.chunks) {
                long fd = structures.getFormedDay(ck);
                if (fd > 0 && (formedDay == 0L || fd < formedDay)) {
                    formedDay = fd;
                }
            }
            boolean isNew = formedDay == 0L;
            if (isNew) {
                formedDay = nowDayTime; // genuinely new base: record its formation day
            }
            // Unify every chunk to the base's formed day. A merge collapses all chunks onto the oldest
            // chunk's day; a later split therefore keeps every part on that same day (no per-chunk drift).
            for (long ck : base.chunks) {
                if (structures.getFormedDay(ck) != formedDay) {
                    structures.setFormedDay(ck, formedDay);
                }
            }
            base.protectionUntilTick = formedDay + protection;
            if (isNew) {
                announceBaseFormed(level, base, protection);
            }
        }
        // Genuinely abandoned bases (no chunk survives in any new base) clear their formed day.
        for (Base old : previous) {
            if (!survivingOld.contains(old)) {
                for (long ck : old.chunks) {
                    structures.clearFormedDay(ck);
                }
                announceBaseAbandoned(level, old);
            }
        }

        bases.clear();
        bases.addAll(newBases);
        chunkToBase.clear();
        for (Base base : bases) {
            for (long ck : base.chunks) {
                chunkToBase.put(ck, base);
            }
        }
    }

    private static int overlapCount(Set<Long> a, Set<Long> b) {
        int n = 0;
        for (long ck : a) {
            if (b.contains(ck)) {
                n++;
            }
        }
        return n;
    }

    private void announceBaseFormed(ServerLevel level, Base base, long protectionTicks) {
        long days = Math.max(1L, protectionTicks / 24000L);
        announceToNearby(level, base, "settlementpressure.msg.base_formed",
                "基地建设的动静惊动了附近的怪物……它们将在 %s 天后逼近基地。", days);
    }

    private void announceBaseAbandoned(ServerLevel level, Base base) {
        announceToNearby(level, base, "settlementpressure.msg.base_abandoned",
                "这座基地已被废弃，怪物们失去了目标，渐渐散去。");
    }

    /** Send a translatable message only to players inside the base's safe zone (base + buffer).
     *  A Chinese fallback is provided so dedicated-server clients (which do not ship the mod's lang
     *  files) still see the text instead of the raw translation key. */
    private void announceToNearby(ServerLevel level, Base base, String key, String fallback, Object... args) {
        int safeR = SPConfig.BUFFER_DISTANCE.get();
        for (ServerPlayer player : level.players()) {
            if (isWithinSafeZone(base, player.chunkPosition(), safeR)) {
                player.sendSystemMessage(Component.translatableWithFallback(key, fallback, args));
            }
        }
    }

    private static boolean isWithinSafeZone(Base base, ChunkPos pc, int safeR) {
        for (long ck : base.chunks) {
            ChunkPos bc = new ChunkPos(ck);
            if (Math.max(Math.abs(bc.x - pc.x), Math.abs(bc.z - pc.z)) <= safeR) {
                return true;
            }
        }
        return false;
    }

    private boolean neighbourhoodQualifies(long ck, Map<Long, Double> scored, double threshold, int minChunks) {
        ChunkPos cp = new ChunkPos(ck);
        double sum = 0.0;
        int scoredCount = 0;
        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                Double s = scored.get(ChunkPos.asLong(cp.x + dx, cp.z + dz));
                if (s != null && s > 0.0) {
                    sum += s;
                    scoredCount++;
                }
            }
        }
        return sum >= threshold && scoredCount >= minChunks;
    }

    // --- activation --------------------------------------------------------

    private void updateActivation(ServerLevel level) {
        for (Base base : bases) {
            base.active = false;
        }
        for (ServerPlayer player : level.players()) {
            Base base = chunkToBase.get(player.chunkPosition().toLong());
            if (base != null) {
                base.active = true;
            }
        }
    }

    // --- threat + regions --------------------------------------------------

    private void rebuildRegions(ServerLevel level) {
        regions.clear();

        int safeR = SPConfig.BUFFER_DISTANCE.get();
        int outerR = safeR + SPConfig.DANGER_DISTANCE.get();

        // 1) Safe zone: all bases (even inactive) forbid natural spawns.
        for (Base base : bases) {
            for (long ck : base.chunks) {
                ChunkPos cp = new ChunkPos(ck);
                for (int dx = -safeR; dx <= safeR; dx++) {
                    for (int dz = -safeR; dz <= safeR; dz++) {
                        if (Math.max(Math.abs(dx), Math.abs(dz)) <= safeR) {
                            regions.put(ChunkPos.asLong(cp.x + dx, cp.z + dz), RegionInfo.SAFE);
                        }
                    }
                }
            }
        }

        long nowDayTime = ServerState.totalDayTime();

        // Compute the threat for every base, even inactive ones: threat is a property of the base
        // (its development level), not of its activation, so queries always show the correct value.
        for (Base base : bases) {
            base.threat = computeThreat(base, level);
        }

        // 2) Danger zone: only active bases whose grace period has ended spawn.
        for (Base base : bases) {
            if (!base.active || nowDayTime < base.protectionUntilTick) {
                continue;
            }
            double cap = dangerCapFor(base.threat);
            for (long ck : base.chunks) {
                ChunkPos cp = new ChunkPos(ck);
                for (int dx = -outerR; dx <= outerR; dx++) {
                    for (int dz = -outerR; dz <= outerR; dz++) {
                        int d = Math.max(Math.abs(dx), Math.abs(dz));
                        if (d <= safeR) {
                            continue; // already safe
                        }
                        long nk = ChunkPos.asLong(cp.x + dx, cp.z + dz);
                        RegionInfo existing = regions.get(nk);
                        if (existing != null && existing.type() == RegionType.SAFE) {
                            continue; // safe zone wins over any periphery
                        }
                        if (existing == null || existing.type() != RegionType.PERIPHERY || cap > existing.dangerCap()) {
                            regions.put(nk, new RegionInfo(RegionType.PERIPHERY, base.threat, cap));
                        }
                    }
                }
            }
        }
    }

    private static double computeThreat(Base base, ServerLevel level) {
        double b = base.structureScore + base.chunks.size() * SPConfig.ACTIVE_WEIGHT.get();
        double h = difficultyMultiplier(level.getDifficulty());
        return b * h;
    }

    private static double dangerCapFor(double threat) {
        int min = SPConfig.DANGER_SPAWN_MIN.get();
        int max = SPConfig.DANGER_SPAWN_MAX.get();
        double t0 = SPConfig.THREAT_BASE.get();
        double normalized = threat / (threat + t0);
        return min + (max - min) * normalized;
    }

    private static double difficultyMultiplier(Difficulty difficulty) {
        return switch (difficulty) {
            case PEACEFUL -> SPConfig.DIFF_PEACEFUL.get();
            case EASY -> SPConfig.DIFF_EASY.get();
            case NORMAL -> SPConfig.DIFF_NORMAL.get();
            case HARD -> SPConfig.DIFF_HARD.get();
        };
    }

    // --- queries -----------------------------------------------------------

    public RegionInfo getRegion(ChunkPos chunkPos) {
        return regions.getOrDefault(chunkPos.toLong(), RegionInfo.WILD);
    }

    /** Returns the base owning the chunk (base chunks only), or null. */
    public Base getBaseAt(ChunkPos chunkPos) {
        return chunkToBase.get(chunkPos.toLong());
    }

    public List<Base> bases() {
        return bases;
    }

    public int baseCount() {
        return bases.size();
    }

    public int activeBaseCount() {
        int n = 0;
        for (Base b : bases) {
            if (b.active) {
                n++;
            }
        }
        return n;
    }
}
