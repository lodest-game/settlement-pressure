package com.settlementpressure.mixin;

import com.settlementpressure.server.DimensionData;
import com.settlementpressure.server.ServerState;
import com.settlementpressure.structure.StructureManager;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseFireBlock;
import net.minecraft.world.level.block.Fallable;
import net.minecraft.world.level.block.MushroomBlock;
import net.minecraft.world.level.block.SnowLayerBlock;
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
            // Air -> block: a block appeared through a direct chunk write. Track it as a player-placed
            // block unless it is obviously natural. Excluded: air, fluids, fire, falling blocks (sand /
            // gravel / concrete powder — a gravity move, not a placement), snow layers and mushrooms.
            // This still covers automated builders (Create deployer, MineColonies, ...) without depending on them.
            if (!newState.isAir() && newState.getFluidState().isEmpty()
                    && !(newState.getBlock() instanceof BaseFireBlock)
                    && !(newState.getBlock() instanceof Fallable)
                    && !(newState.getBlock() instanceof SnowLayerBlock)
                    && !(newState.getBlock() instanceof MushroomBlock)) {
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
