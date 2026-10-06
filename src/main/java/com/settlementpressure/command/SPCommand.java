package com.settlementpressure.command;

import com.mojang.brigadier.context.CommandContext;
import com.settlementpressure.base.Base;
import com.settlementpressure.base.RegionInfo;
import com.settlementpressure.region.RegionType;
import com.settlementpressure.server.DimensionData;
import com.settlementpressure.server.ServerState;
import java.util.List;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.neoforged.neoforge.event.RegisterCommandsEvent;

public final class SPCommand {
    private SPCommand() {
    }

    public static void register(RegisterCommandsEvent event) {
        event.getDispatcher().register(
                Commands.literal("settlementpressure")
                        .requires(src -> src.hasPermission(2))
                        .then(Commands.literal("status").executes(SPCommand::status))
                        .then(Commands.literal("here").executes(SPCommand::here))
                        .then(Commands.literal("bases").executes(SPCommand::bases)));
    }

    private static int status(CommandContext<CommandSourceStack> ctx) {
        CommandSourceStack src = ctx.getSource();
        DimensionData data = ServerState.getExisting(src.getLevel());
        int baseCount = data == null ? 0 : data.bases().baseCount();
        int activeCount = data == null ? 0 : data.bases().activeBaseCount();
        int blockCount = data == null ? 0 : data.structures().trackedBlockCount();
        src.sendSuccess(() -> Component.literal(String.format(
                "Settlement Pressure: %d bases (%d active), %d tracked blocks in this dimension.",
                baseCount, activeCount, blockCount)), false);
        return 1;
    }

    private static int here(CommandContext<CommandSourceStack> ctx) {
        CommandSourceStack src = ctx.getSource();
        ServerLevel level = src.getLevel();
        BlockPos pos = BlockPos.containing(src.getPosition());
        DimensionData data = ServerState.getExisting(level);
        RegionInfo region = data == null ? RegionInfo.WILD : data.bases().getRegion(new ChunkPos(pos));
        String extra = region.type() == RegionType.PERIPHERY
                ? String.format(" threat=%.1f dangerCap=%.1f", region.threat(), region.dangerCap())
                : "";
        src.sendSuccess(() -> Component.literal("Region at " + pos.toShortString() + ": " + region.type() + extra), false);
        return 1;
    }

    private static int bases(CommandContext<CommandSourceStack> ctx) {
        CommandSourceStack src = ctx.getSource();
        DimensionData data = ServerState.getExisting(src.getLevel());
        List<Base> list = data == null ? List.of() : data.bases().bases();
        if (list.isEmpty()) {
            src.sendSuccess(() -> Component.literal("No bases detected."), false);
            return 1;
        }
        src.sendSuccess(() -> Component.literal("Bases (" + list.size() + "):"), false);
        for (Base b : list) {
            src.sendSuccess(() -> Component.literal(String.format(
                    "  chunks=%d score=%.1f threat=%.1f active=%s",
                    b.chunks.size(), b.structureScore, b.threat, b.active)), false);
        }
        return 1;
    }
}
