package com.settlementpressure.event;

import com.settlementpressure.command.SPCommand;
import com.settlementpressure.config.SPConfig;
import com.settlementpressure.server.DimensionData;
import com.settlementpressure.server.ServerState;
import com.settlementpressure.spawn.SpawnController;
import com.settlementpressure.structure.StructureManager;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.common.util.BlockSnapshot;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;
import net.neoforged.neoforge.event.entity.EntityLeaveLevelEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.level.BlockEvent;
import net.neoforged.neoforge.event.level.LevelEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

/** Game-event handlers registered on {@code NeoForge.EVENT_BUS}. */
public final class ServerEventHandlers {
    private ServerEventHandlers() {
    }

    @SubscribeEvent
    public static void onServerTickPre(ServerTickEvent.Pre event) {
        for (ServerLevel level : event.getServer().getAllLevels()) {
            if (level.dimension() == Level.OVERWORLD) {
                ServerState.tickDayCounter(level);
            }
            DimensionData data = ServerState.getExisting(level);
            if (data != null) {
                data.structures().clearPendingPlacements();
            }
        }
    }

    @SubscribeEvent
    public static void onServerTickPost(ServerTickEvent.Post event) {
        MinecraftServer server = event.getServer();
        for (ServerLevel level : server.getAllLevels()) {
            if (level.dimension() != Level.OVERWORLD) {
                continue;
            }
            DimensionData data = ServerState.get(level); // ensure StructureManager is loaded on (re)start
            data.bases().tick(level, level.getGameTime());
        }
    }

    @SubscribeEvent
    public static void onBlockPlaced(BlockEvent.EntityPlaceEvent event) {
        if (!(event.getEntity() instanceof Player) || !(event.getLevel() instanceof ServerLevel level)) {
            return;
        }
        if (level.dimension() != Level.OVERWORLD) {
            return; // only track player builds in the Overworld
        }
        StructureManager structures = ServerState.get(level).structures();
        long now = level.getGameTime();
        if (event instanceof BlockEvent.EntityMultiPlaceEvent multi) {
            for (BlockSnapshot snapshot : multi.getReplacedBlockSnapshots()) {
                if (!isExcluded(snapshot.getCurrentState())) {
                    structures.onBlockPlaced(snapshot.getPos(), now);
                }
            }
        } else if (!isExcluded(event.getPlacedBlock())) {
            structures.onBlockPlaced(event.getPos(), now);
        }
    }

    @SubscribeEvent
    public static void onEntityJoin(EntityJoinLevelEvent event) {
        SpawnController.onEntityJoin(event.getEntity(), event.getLevel());
    }

    @SubscribeEvent
    public static void onEntityLeave(EntityLeaveLevelEvent event) {
        SpawnController.onEntityLeave(event.getEntity(), event.getLevel());
    }

    @SubscribeEvent
    public static void onPlayerLogin(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            ServerState.onPlayerLogin(player);
        }
    }

    @SubscribeEvent
    public static void onLevelUnload(LevelEvent.Unload event) {
        if (event.getLevel() instanceof ServerLevel level) {
            ServerState.remove(level);
            SpawnController.clearDimension(level.dimension()); // drop hostile counts so they don't leak across worlds
        }
    }

    @SubscribeEvent
    public static void onServerStopped(ServerStoppedEvent event) {
        ServerState.clear();
        SpawnController.clearCaches();
    }

    @SubscribeEvent
    public static void onRegisterCommands(RegisterCommandsEvent event) {
        SPCommand.register(event);
    }

    private static boolean isExcluded(BlockState state) {
        ResourceLocation id = BuiltInRegistries.BLOCK.getKey(state.getBlock());
        return id != null && SPConfig.EXCLUDED_BLOCKS.get().contains(id.toString());
    }
}
