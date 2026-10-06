package com.settlementpressure.mixin;

import com.settlementpressure.SettlementPressure;
import com.settlementpressure.config.SPConfig;
import com.settlementpressure.server.DimensionData;
import com.settlementpressure.server.ServerState;
import com.settlementpressure.structure.StructureManager;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Fallable;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Catches block changes that bypass {@link Level#setBlock} by writing straight into the chunk, which
 * is how Valkyrien Skies / Aeronautics physicalizes a ship (treated as removal) and how automated
 * builders such as Create's deployer or MineColonies place blocks (treated as placement).
 *
 * <p>Changes already handled by the {@code Level.setBlock} mixin (player placements, Create
 * contraptions / pistons) are marked via {@link StructureManager#markPreserved} and skipped here.</p>
 */
@Mixin(LevelChunk.class)
public abstract class LevelChunkMixin {
    @Inject(
            method = "setBlockState(Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/level/block/state/BlockState;Z)Lnet/minecraft/world/level/block/state/BlockState;",
            at = @At("HEAD"))
    private void settlementpressure$onChunkBlockChange(BlockPos pos, BlockState newState, boolean isMoving, CallbackInfoReturnable<BlockState> cir) {
        LevelChunk chunk = (LevelChunk) (Object) this;
        Level level = chunk.getLevel();
        if (level.isClientSide || !(level instanceof ServerLevel serverLevel)) {
            return;
        }
        if (serverLevel.dimension() != Level.OVERWORLD) {
            return;
        }
        DimensionData data = ServerState.getExisting(serverLevel);
        if (data == null) {
            return;
        }
        StructureManager structures = data.structures();
        if (structures.consumePreserved(pos)) {
            return; // already handled (player placement / Create move) by the Level.setBlock mixin
        }
        BlockState oldState = level.getBlockState(pos);
        if (oldState.isAir()) {
            // Air -> block: a block appeared through a direct chunk write. Track it only if it is a "full"
            // block (full collision shape), which covers automated builders (planks, stone, ...) while
            // excluding natural growth that lacks a full shape (vines, kelp, cave vines, amethyst buds,
            // sugar cane, bamboo, cactus, snow, mushrooms, fire, fluids). Falling blocks (sand / gravel /
            // concrete powder) are full but are excluded separately: their appearance is a gravity move.
            boolean natural = !newState.isCollisionShapeFullBlock(level, pos)
                    || newState.getBlock() instanceof Fallable;
            if (!natural) {
                if (SPConfig.DEBUG_LOGGING.get()) {
                    SettlementPressure.LOGGER.info("[sp] autoTrack block={} pos={}", newState.getBlock(), pos);
                }
                // A Create contraption / piston was assembled and a block reappeared. If it came back at
                // the same position, just overwrite its record below; if it came back elsewhere, the
                // contraption disassembled in a new spot — drop the old records so no ghost remains.
                if (!structures.consumeContraptionMoved(pos) && structures.hasContraptionMoved()) {
                    structures.clearContraptionMoved(serverLevel.getGameTime());
                }
                structures.onBlockPlaced(pos, serverLevel.getGameTime());
            }
            return;
        }
        // Block -> block: not handled here (in-place transformations are the Level.setBlock mixin's job).
        if (!newState.isAir()) {
            return;
        }
        // Tracked block -> air through a direct chunk write (Aeronautics physicalization): remove it.
        if (structures.isTracked(pos)) {
            structures.onBlockRemoved(pos, serverLevel.getGameTime());
        }
    }
}
