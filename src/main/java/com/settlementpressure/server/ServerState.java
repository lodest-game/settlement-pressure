package com.settlementpressure.server;

import com.settlementpressure.config.SPConfig;
import com.settlementpressure.structure.StructureManager;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;

/**
 * Server-wide registry of per-dimension data and per-player first-join day-times.
 *
 * <p>Newbie tracking is intentionally in-memory for v1 (reset on server restart).</p>
 */
public final class ServerState {
    private static final Map<ResourceKey<Level>, DimensionData> DATA = new HashMap<>();
    private static final Map<UUID, Long> FIRST_JOIN_DAY_TIME = new HashMap<>();

    private ServerState() {
    }

    /** Gets or lazily creates (and registers) the dimension data. */
    public static DimensionData get(ServerLevel level) {
        ResourceKey<Level> key = level.dimension();
        DimensionData data = DATA.get(key);
        if (data == null) {
            StructureManager structures = level.getDataStorage().computeIfAbsent(StructureManager.factory(), StructureManager.ID);
            data = new DimensionData(structures);
            DATA.put(key, data);
        }
        return data;
    }

    /** Returns existing data without creating it (used on hot paths such as block changes). */
    public static DimensionData getExisting(ServerLevel level) {
        return DATA.get(level.dimension());
    }

    public static void remove(ServerLevel level) {
        DATA.remove(level.dimension());
    }

    public static void clear() {
        DATA.clear();
        FIRST_JOIN_DAY_TIME.clear();
    }

    // --- newbie tracking ---------------------------------------------------
    //
    // Uses getDayTime() (raw, persistent) rather than getGameTime(): getDayTime() advances with
    // /time add and when players sleep, so both count toward the protection period.

    public static void onPlayerLogin(ServerPlayer player) {
        FIRST_JOIN_DAY_TIME.putIfAbsent(player.getUUID(), player.serverLevel().getDayTime());
    }

    public static boolean isNewbie(ServerPlayer player) {
        Long first = FIRST_JOIN_DAY_TIME.get(player.getUUID());
        if (first == null) {
            return false;
        }
        return player.serverLevel().getDayTime() - first < SPConfig.newbieProtectionTicks();
    }
}
