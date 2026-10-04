package com.settlementpressure.spawn;

import com.settlementpressure.base.RegionInfo;
import com.settlementpressure.config.SPConfig;
import com.settlementpressure.region.RegionType;
import com.settlementpressure.server.DimensionData;
import com.settlementpressure.server.ServerState;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;

/**
 * Spawn-pressure control, driven from the {@link com.settlementpressure.mixin.NaturalSpawnerMixin}.
 *
 * <ul>
 *   <li>SAFE zone (base + buffer): no natural spawns of any category.</li>
 *   <li>WILD: per-player hostile spawns kept between {@code wanderSpawnMin} and {@code wanderSpawnMax}.</li>
 *   <li>DANGER zone: main spawn area; hostile total capped by the base's dynamic cap.</li>
 * </ul>
 *
 * <p>The hostile-mob count is maintained incrementally (via entity join/leave events) so it is always
 * fresh and enforced immediately, without costly entity scanning.</p>
 */
public final class SpawnController {
    /** Per-dimension per-chunk count of hostile (MONSTER category) mobs. */
    private static final Map<ResourceKey<Level>, Map<Long, Integer>> HOSTILE_COUNTS = new ConcurrentHashMap<>();

    private SpawnController() {
    }

    public static RegionInfo getRegion(ServerLevel level, ChunkPos chunkPos) {
        DimensionData data = ServerState.getExisting(level);
        if (data == null) {
            return RegionInfo.WILD;
        }
        return data.bases().getRegion(chunkPos);
    }

    /** The wilderness quota around the given chunk: {nearby players, hostile mobs near them}. */
    private static WildernessState wildernessState(ServerLevel level, ChunkPos chunkPos) {
        int radius = SPConfig.WANDER_PROTECTION_RADIUS.get();

        ServerPlayer nearest = null;
        long bestDist = Long.MAX_VALUE;
        for (ServerPlayer player : level.players()) {
            long d = chebyshev(player.chunkPosition(), chunkPos);
            if (d < bestDist) {
                bestDist = d;
                nearest = player;
            }
        }
        if (nearest == null) {
            return new WildernessState(0, 0);
        }
        ChunkPos nearestChunk = nearest.chunkPosition();
        int players = 0;
        for (ServerPlayer player : level.players()) {
            if (chebyshev(player.chunkPosition(), nearestChunk) <= radius) {
                players++;
            }
        }
        if (players < 1) {
            players = 1;
        }
        return new WildernessState(players, hostileCountNear(level, nearestChunk, radius));
    }

    /** Lone wolf / newbie wilderness hostile cap reached? The cap is per-player. */
    public static boolean isWildernessCapReached(ServerLevel level, ChunkPos chunkPos) {
        WildernessState state = wildernessState(level, chunkPos);
        if (state.players() <= 0) {
            return false;
        }
        return state.count() >= SPConfig.WANDER_SPAWN_MAX.get() * state.players();
    }

    /** Danger-zone dynamic total cap reached? */
    public static boolean isPeripheryCapReached(ServerLevel level, ChunkPos chunkPos) {
        RegionInfo region = getRegion(level, chunkPos);
        if (region.type() != RegionType.PERIPHERY) {
            return false;
        }
        int cap = (int) Math.round(region.dangerCap());
        return hostileCountNear(level, chunkPos, dangerRadius()) >= cap;
    }

    private static int dangerRadius() {
        return SPConfig.BUFFER_DISTANCE.get() + SPConfig.DANGER_DISTANCE.get();
    }

    /** Extra spawn-attempt calls to keep the wilderness at/above its per-player minimum. */
    public static int extraWildernessCalls(ServerLevel level, ChunkPos chunkPos) {
        RegionInfo region = getRegion(level, chunkPos);
        if (region.type() != RegionType.WILD) {
            return 0;
        }
        WildernessState state = wildernessState(level, chunkPos);
        if (state.players() <= 0) {
            return 0;
        }
        int min = SPConfig.WANDER_SPAWN_MIN.get() * state.players();
        return state.count() < min ? min - state.count() : 0;
    }

    public static void onEntityJoin(Entity entity, Level level) {
        if (!(level instanceof ServerLevel sl) || !isHostileMob(entity)) {
            return;
        }
        ChunkPos cp = entity.chunkPosition();
        HOSTILE_COUNTS.computeIfAbsent(sl.dimension(), k -> new ConcurrentHashMap<>())
                .merge(cp.toLong(), 1, Integer::sum);
    }

    public static void onEntityLeave(Entity entity, Level level) {
        if (!(level instanceof ServerLevel sl) || !isHostileMob(entity)) {
            return;
        }
        ChunkPos cp = entity.chunkPosition();
        Map<Long, Integer> perChunk = HOSTILE_COUNTS.get(sl.dimension());
        if (perChunk == null) {
            return;
        }
        perChunk.computeIfPresent(cp.toLong(), (k, v) -> v <= 1 ? null : v - 1);
    }

    private static boolean isHostileMob(Entity entity) {
        return entity instanceof Mob mob && mob.getType().getCategory() == MobCategory.MONSTER;
    }

    public static int hostileCountNear(ServerLevel level, ChunkPos center, int radiusChunks) {
        Map<Long, Integer> perChunk = HOSTILE_COUNTS.get(level.dimension());
        if (perChunk == null) {
            return 0;
        }
        int total = 0;
        for (int dx = -radiusChunks; dx <= radiusChunks; dx++) {
            for (int dz = -radiusChunks; dz <= radiusChunks; dz++) {
                Integer c = perChunk.get(ChunkPos.asLong(center.x + dx, center.z + dz));
                if (c != null) {
                    total += c;
                }
            }
        }
        return total;
    }

    public static void clearCaches() {
        HOSTILE_COUNTS.clear();
    }

    private static long chebyshev(ChunkPos a, ChunkPos b) {
        return Math.max(Math.abs((long) a.x - b.x), Math.abs((long) a.z - b.z));
    }

    private record WildernessState(int players, int count) {
    }
}
