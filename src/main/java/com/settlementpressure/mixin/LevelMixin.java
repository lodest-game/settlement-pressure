package com.settlementpressure.mixin;

import com.settlementpressure.server.DimensionData;
import com.settlementpressure.server.ServerState;
import com.settlementpressure.structure.StructureManager;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Detects removal of a player-placed block regardless of cause (break, explosion, piston, fluid,
 * fire). A position just placed by a player is skipped via the pending-placement set populated by
 * the {@link net.neoforged.neoforge.event.level.BlockEvent.EntityPlaceEvent} handler.
 */
@Mixin(Level.class)
public abstract class LevelMixin {
    @Inject(
            method = "setBlock(Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/level/block/state/BlockState;I)Z",
            at = @At("HEAD"))
    private void settlementpressure$onBlockChange(BlockPos pos, BlockState newState, int flags, CallbackInfoReturnable<Boolean> cir) {
        Level level = (Level) (Object) this;
        if (level.isClientSide || !(level instanceof ServerLevel serverLevel)) {
            return;
        }
        if (serverLevel.dimension() != Level.OVERWORLD) {
            return; // only track player builds in the Overworld
        }
        DimensionData data = ServerState.getExisting(serverLevel);
        if (data == null) {
            return;
        }
        StructureManager structures = data.structures();
        if (!structures.isTracked(pos)) {
            return;
        }
        if (structures.consumePendingPlacement(pos)) {
            return; // this is a player placement, not a removal
        }
        BlockState oldState = level.getBlockState(pos);
        if (oldState.isAir() || oldState.getBlock() == newState.getBlock()) {
            return; // placement, or a same-block state change (e.g. redstone), not a removal
        }
        structures.onBlockRemoved(pos, serverLevel.getGameTime());
    }
}
