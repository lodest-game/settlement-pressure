package com.settlementpressure.api;

import com.settlementpressure.base.Base;
import com.settlementpressure.base.BaseManager;
import com.settlementpressure.base.RegionInfo;
import com.settlementpressure.region.RegionType;
import com.settlementpressure.server.DimensionData;
import com.settlementpressure.server.ServerState;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.ChunkPos;

/**
 * Read-only public API for external defence mods (design section 8). Provides base / safe zone /
 * threat / activation queries.
 */
public final class SettlementPressureAPI {
    private SettlementPressureAPI() {
    }

    // --- region queries ----------------------------------------------------

    public static RegionType getRegionType(ServerLevel level, BlockPos pos) {
        return getRegion(level, pos).type();
    }

    public static RegionInfo getRegion(ServerLevel level, BlockPos pos) {
        BaseManager bases = bases(level);
        return bases == null ? RegionInfo.WILD : bases.getRegion(new ChunkPos(pos));
    }

    public static boolean isBaseChunk(ServerLevel level, ChunkPos chunkPos) {
        return baseAt(level, chunkPos) != null;
    }

    public static boolean isActiveBaseChunk(ServerLevel level, ChunkPos chunkPos) {
        Base base = baseAt(level, chunkPos);
        return base != null && base.active;
    }

    /** Threat value of the base owning the chunk, or 0 if not a base chunk. */
    public static double getThreat(ServerLevel level, ChunkPos chunkPos) {
        Base base = baseAt(level, chunkPos);
        return base == null ? 0.0 : base.threat;
    }

    public static Base getBaseAt(ServerLevel level, ChunkPos chunkPos) {
        return baseAt(level, chunkPos);
    }

    public static List<Base> getBases(ServerLevel level) {
        BaseManager bases = bases(level);
        return bases == null ? List.of() : bases.bases();
    }

    public static int getBaseCount(ServerLevel level) {
        BaseManager bases = bases(level);
        return bases == null ? 0 : bases.baseCount();
    }

    public static int getActiveBaseCount(ServerLevel level) {
        BaseManager bases = bases(level);
        return bases == null ? 0 : bases.activeBaseCount();
    }

    public static int getTrackedBlockCount(ServerLevel level) {
        DimensionData data = ServerState.getExisting(level);
        return data == null ? 0 : data.structures().trackedBlockCount();
    }

    // --- players -----------------------------------------------------------

    public static boolean isNewbie(ServerPlayer player) {
        return ServerState.isNewbie(player);
    }

    // --- helpers -----------------------------------------------------------

    private static BaseManager bases(ServerLevel level) {
        DimensionData data = ServerState.getExisting(level);
        return data == null ? null : data.bases();
    }

    private static Base baseAt(ServerLevel level, ChunkPos chunkPos) {
        BaseManager bases = bases(level);
        return bases == null ? null : bases.getBaseAt(chunkPos);
    }
}
