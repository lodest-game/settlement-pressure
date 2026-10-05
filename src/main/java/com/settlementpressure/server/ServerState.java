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

    /**
     * Monotonic day-time counter (in ticks). It accumulates the positive delta of the overworld's
     * {@link Level#getDayTime()}, so it advances with normal play, {@code /time add} and sleeping,
     * but never goes backwards on {@code /time set}. Initialized from {@code getDayTime()} on the
     * first tick so it also survives a server restart.
     */
    private static long totalDayTime = 0L;
    private static long prevDayTime = 0L;
    private static boolean dayInit = false;

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
        totalDayTime = 0L;
        dayInit = false;
    }

    // --- monotonic day counter --------------------------------------------

    /** Call once per server tick for the Overworld to advance the cumulative day-time. */
    public static void tickDayCounter(ServerLevel overworld) {
        long dayTime = overworld.getDayTime();
        if (dayInit) {
            long delta = dayTime - prevDayTime;
            if (delta > 0) {
                totalDayTime += delta;
            }
            // Ignore non-positive deltas: /time set backwards must not rewind the protection timer.
        } else {
            totalDayTime = dayTime; // seed from the persisted day-time (continues across restarts)
            dayInit = true;
        }
        prevDayTime = dayTime;
    }

    /** Monotonic day-time in ticks (advances with /time add and sleeping, never with /time set). */
    public static long totalDayTime() {
        return totalDayTime;
    }

    // --- newbie tracking ---------------------------------------------------

    public static void onPlayerLogin(ServerPlayer player) {
        FIRST_JOIN_DAY_TIME.putIfAbsent(player.getUUID(), totalDayTime);
    }

    public static boolean isNewbie(ServerPlayer player) {
        Long first = FIRST_JOIN_DAY_TIME.get(player.getUUID());
        if (first == null) {
            return false;
        }
        return totalDayTime - first < SPConfig.newbieProtectionTicks();
    }
}
