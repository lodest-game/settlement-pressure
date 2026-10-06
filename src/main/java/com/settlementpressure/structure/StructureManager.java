package com.settlementpressure.structure;

import com.settlementpressure.config.SPConfig;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.util.datafix.DataFixTypes;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.saveddata.SavedData;

/**
 * Per-dimension persistent store of player-placed blocks, their structure score and the cached
 * per-chunk score totals. This is the source of truth for base detection.
 *
 * <p>Data layout: {@code chunkKey -> (blockKey -> BlockRecord)}. Block removal is detected by the
 * {@link com.settlementpressure.mixin.LevelMixin} block-change hook, which covers breaks, explosions,
 * pistons, fluids and fire alike.</p>
 */
public final class StructureManager extends SavedData {
    public static final String ID = "settlementpressure_structures";

    private final Map<Long, Map<Long, BlockRecord>> blocks = new HashMap<>();
    private final Map<Long, Double> chunkScores = new HashMap<>();
    /** Day-time when each chunk first became part of a base; persisted so restarts do not re-trigger the grace period. */
    private final Map<Long, Long> chunkFormedDayTime = new HashMap<>();

    /** Positions just placed by a player; consumed by the block-change mixin so placements are not
     *  mistaken for removals. Cleared at the start of every server tick. */
    private final Set<BlockPos> pendingPlacements = new HashSet<>();

    /** Positions whose score was preserved by the {@code Level.setBlock} mixin (Create contraption /
     *  piston moves). Consumed by the {@code LevelChunk.setBlockState} mixin so the direct chunk-level
     *  change (which the same {@code Level.setBlock} triggers internally) is not double-handled. */
    private final Set<BlockPos> preservedPositions = new HashSet<>();

    public StructureManager() {
    }

    // --- persistence -------------------------------------------------------

    public static StructureManager load(CompoundTag tag, HolderLookup.Provider registries) {
        StructureManager sm = new StructureManager();
        ListTag chunkList = tag.getList("chunks", Tag.TAG_COMPOUND);
        for (int i = 0; i < chunkList.size(); i++) {
            CompoundTag chunkTag = chunkList.getCompound(i);
            long chunkKey = chunkTag.getLong("chunk");
            ListTag blockList = chunkTag.getList("blocks", Tag.TAG_COMPOUND);
            Map<Long, BlockRecord> map = new HashMap<>();
            for (int j = 0; j < blockList.size(); j++) {
                CompoundTag bt = blockList.getCompound(j);
                map.put(bt.getLong("pos"), new BlockRecord(bt.getDouble("score"), bt.getLong("touch")));
            }
            if (!map.isEmpty()) {
                sm.blocks.put(chunkKey, map);
            }
            long formedDay = chunkTag.getLong("formedDay");
            if (formedDay > 0) {
                sm.chunkFormedDayTime.put(chunkKey, formedDay);
            }
        }
        return sm;
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        ListTag chunkList = new ListTag();
        for (Map.Entry<Long, Map<Long, BlockRecord>> entry : blocks.entrySet()) {
            CompoundTag chunkTag = new CompoundTag();
            chunkTag.putLong("chunk", entry.getKey());
            ListTag blockList = new ListTag();
            for (Map.Entry<Long, BlockRecord> be : entry.getValue().entrySet()) {
                CompoundTag bt = new CompoundTag();
                bt.putLong("pos", be.getKey());
                bt.putDouble("score", be.getValue().score);
                bt.putLong("touch", be.getValue().lastTouchTick);
                blockList.add(bt);
            }
            chunkTag.put("blocks", blockList);
            chunkTag.putLong("formedDay", chunkFormedDayTime.getOrDefault(entry.getKey(), 0L));
            chunkList.add(chunkTag);
        }
        tag.put("chunks", chunkList);
        return tag;
    }

    public static SavedData.Factory<StructureManager> factory() {
        return new SavedData.Factory<>(StructureManager::new, StructureManager::load, DataFixTypes.SAVED_DATA_MAP_DATA);
    }

    // --- lookups -----------------------------------------------------------

    private static long chunkKey(BlockPos pos) {
        return new ChunkPos(pos).toLong();
    }

    public boolean isTracked(BlockPos pos) {
        Map<Long, BlockRecord> chunk = blocks.get(chunkKey(pos));
        return chunk != null && chunk.containsKey(pos.asLong());
    }

    private BlockRecord getRecord(BlockPos pos) {
        Map<Long, BlockRecord> chunk = blocks.get(chunkKey(pos));
        return chunk == null ? null : chunk.get(pos.asLong());
    }

    /** Snapshot of cached chunk scores (decay already applied). */
    public Map<Long, Double> chunkScores() {
        return chunkScores;
    }

    // --- placement / removal ----------------------------------------------

    public void onBlockPlaced(BlockPos pos, long nowTick) {
        pendingPlacements.add(pos);
        int adjacent = countPlayerPlacedNeighbors(pos);
        putRecord(pos, new BlockRecord(scoreFor(adjacent), nowTick));
        recomputeNeighbours(pos, nowTick);
        recomputeChunkAndNeighbourChunks(pos, nowTick);
    }

    public void onBlockRemoved(BlockPos pos, long nowTick) {
        long ck = chunkKey(pos);
        Map<Long, BlockRecord> chunk = blocks.get(ck);
        if (chunk == null || chunk.remove(pos.asLong()) == null) {
            return;
        }
        recomputeNeighbours(pos, nowTick);
        recomputeChunkAndNeighbourChunks(pos, nowTick);
        setDirty();
    }

    private void recomputeNeighbours(BlockPos pos, long nowTick) {
        for (Direction d : Direction.values()) {
            BlockPos n = pos.relative(d);
            BlockRecord rec = getRecord(n);
            if (rec != null) {
                rec.score = scoreFor(countPlayerPlacedNeighbors(n));
                rec.lastTouchTick = nowTick;
            }
        }
    }

    private void recomputeChunkAndNeighbourChunks(BlockPos pos, long nowTick) {
        long ck = chunkKey(pos);
        recomputeChunkScore(ck, nowTick);
        for (Direction d : Direction.values()) {
            BlockPos n = pos.relative(d);
            long nk = chunkKey(n);
            if (nk != ck && blocks.containsKey(nk)) {
                recomputeChunkScore(nk, nowTick);
            }
        }
    }

    private int countPlayerPlacedNeighbors(BlockPos pos) {
        int count = 0;
        for (Direction d : Direction.values()) {
            if (isTracked(pos.relative(d))) {
                count++;
            }
        }
        return count;
    }

    private void putRecord(BlockPos pos, BlockRecord record) {
        blocks.computeIfAbsent(chunkKey(pos), k -> new HashMap<>()).put(pos.asLong(), record);
        setDirty();
    }

    public static double scoreFor(int adjacent) {
        return switch (adjacent) {
            case 0 -> SPConfig.SCORE_ISOLATED.get();
            case 1 -> SPConfig.SCORE_ONE_NEIGHBOR.get();
            case 2 -> SPConfig.SCORE_TWO_NEIGHBORS.get();
            default -> SPConfig.SCORE_THREE_PLUS_NEIGHBORS.get();
        };
    }

    // --- decay -------------------------------------------------------------

    private void recomputeChunkScore(long chunkKey, long nowTick) {
        Map<Long, BlockRecord> chunk = blocks.get(chunkKey);
        if (chunk == null || chunk.isEmpty()) {
            blocks.remove(chunkKey); // chunk fully dismantled by the player
            chunkScores.remove(chunkKey);
            return;
        }
        double halfLife = SPConfig.decayHalfLifeTicks();
        double total = 0.0;
        for (BlockRecord rec : chunk.values()) {
            long age = Math.max(0L, nowTick - rec.lastTouchTick);
            total += rec.score * Math.pow(0.5, (double) age / halfLife);
        }
        // Blocks are never removed here: an inactive base decays toward zero score but keeps its
        // blocks, so re-activation (player activity) restores the full size.
        chunkScores.put(chunkKey, total);
    }

    /** Refreshes the maintenance timestamp of every block in the chunk (player activity resets decay). */
    public void touchChunk(long chunkKey, long nowTick) {
        Map<Long, BlockRecord> chunk = blocks.get(chunkKey);
        if (chunk == null) {
            return;
        }
        for (BlockRecord rec : chunk.values()) {
            rec.lastTouchTick = nowTick;
        }
    }

    /** Recompute every chunk's decayed score. Chunks in {@code maintainedChunks} (player activity)
     *  have their maintenance timestamps refreshed first, so they do not decay. */
    public void refreshDecay(long nowTick, Set<Long> maintainedChunks) {
        Set<Long> keys = new HashSet<>(blocks.keySet());
        for (long ck : keys) {
            if (maintainedChunks.contains(ck)) {
                touchChunk(ck, nowTick);
            }
            recomputeChunkScore(ck, nowTick);
        }
        if (!keys.isEmpty()) {
            setDirty();
        }
    }

    // --- formed day (persisted base-grace state) ---------------------------

    public long getFormedDay(long chunkKey) {
        return chunkFormedDayTime.getOrDefault(chunkKey, 0L);
    }

    public void setFormedDay(long chunkKey, long dayTime) {
        if (dayTime > 0) {
            chunkFormedDayTime.put(chunkKey, dayTime);
            setDirty();
        }
    }

    public void clearFormedDay(long chunkKey) {
        if (chunkFormedDayTime.remove(chunkKey) != null) {
            setDirty();
        }
    }

    // --- transient per-tick bookkeeping (used by the block-change mixins) --

    public boolean consumePendingPlacement(BlockPos pos) {
        return pendingPlacements.remove(pos);
    }

    /** Mark a position as preserved by the {@code Level.setBlock} mixin (Create contraption / piston). */
    public void markPreserved(BlockPos pos) {
        preservedPositions.add(pos);
    }

    /** Consume the preserved mark for a position (returns true if it was marked). */
    public boolean consumePreserved(BlockPos pos) {
        return preservedPositions.remove(pos);
    }

    /** Clear both transient sets at the start of every server tick. */
    public void clearTransientState() {
        pendingPlacements.clear();
        preservedPositions.clear();
    }

    // --- debug -------------------------------------------------------------

    public int trackedBlockCount() {
        int n = 0;
        for (Map<Long, BlockRecord> chunk : blocks.values()) {
            n += chunk.size();
        }
        return n;
    }
}
