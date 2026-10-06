package com.settlementpressure.mixin;

import com.settlementpressure.SettlementPressure;
import com.settlementpressure.config.SPConfig;
import com.settlementpressure.server.DimensionData;
import com.settlementpressure.server.ServerState;
import com.settlementpressure.structure.StructureManager;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Detects removal of a player-placed block regardless of cause (break, explosion, fluid, fire).
 *
 * <p>Compatibility with other mods' multiblock / contraption / physics structures:</p>
 * <ul>
 *   <li>In-place transformation — a tracked block replaced by a different non-air block (e.g.
 *       Immersive Engineering multiblock formation) — keeps its score.</li>
 *   <li>Genuine removal — a tracked block that becomes air (broken, exploded, moved away by a
 *       Create contraption / piston, or a Valkyrien Skies / Aeronautics ship being physicalized) —
 *       is removed. A Create contraption that later disassembles is re-added by the chunk mixin,
 *       so moving it (e.g. rotating 90&deg;) does not leave ghost records.</li>
 * </ul>
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
            structures.markPreserved(pos); // player placement; let the chunk mixin skip it too
            return; // this is a player placement, not a removal
        }
        BlockState oldState = level.getBlockState(pos);
        if (oldState.isAir() || oldState.getBlock() == newState.getBlock()) {
            return; // placement, or a same-block state change (e.g. redstone), not a removal
        }
        if (SPConfig.DEBUG_LOGGING.get()) {
            SettlementPressure.LOGGER.info("[sp] blockChange old={} new={} flags={}",
                    oldState.getBlock(), newState.getBlock(), flags);
        }
        // In-place transformation (a multiblock forming): the block is still there, just as a
        // different non-air block — keep its score.
        if (!newState.isAir()) {
            return;
        }
        // Became air while being moved/absorbed by a Create contraption or piston: keep its score and
        // remember the position. If the contraption later disassembles elsewhere, the chunk mixin drops
        // these remembered records, so moving (e.g. rotating) leaves no ghost records.
        if ((flags & Block.UPDATE_SUPPRESS_DROPS) != 0 || (flags & Block.UPDATE_MOVE_BY_PISTON) != 0) {
            structures.markPreserved(pos);
            structures.markContraptionMoved(pos);
            return;
        }
        // Became air otherwise: genuinely gone (broken, exploded, or a physics structure leaving the
        // ground) — remove its score.
        structures.onBlockRemoved(pos, serverLevel.getGameTime());
    }
}
