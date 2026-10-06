package com.settlementpressure.mixin;

import com.settlementpressure.base.RegionInfo;
import com.settlementpressure.region.RegionType;
import com.settlementpressure.spawn.SpawnController;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.NaturalSpawner;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.levelgen.Heightmap;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Directly controls natural spawns per chunk: bans the safe zone, caps wilderness hostile spawns
 * (lone wolf / newbie) per player, and caps the danger zone at its dynamic total monster cap.
 */
@Mixin(NaturalSpawner.class)
public abstract class NaturalSpawnerMixin {
    @Inject(method = "spawnCategoryForChunk", at = @At("HEAD"), cancellable = true)
    private static void settlementpressure$controlSpawns(MobCategory category, ServerLevel level, LevelChunk chunk,
            NaturalSpawner.SpawnPredicate predicate, NaturalSpawner.AfterSpawnCallback afterSpawnCallback, CallbackInfo ci) {
        if (level.dimension() != Level.OVERWORLD) {
            return; // this mod only affects the Overworld
        }
        RegionInfo region = SpawnController.getRegion(level, chunk.getPos());
        if (region.type() == RegionType.SAFE) {
            ci.cancel(); // safe zone: no natural spawns of any category
            return;
        }
        if (category != MobCategory.MONSTER) {
            return;
        }
        if (region.type() == RegionType.WILD) {
            if (SpawnController.isWildernessCapReached(level, chunk.getPos())) {
                ci.cancel(); // lone wolf / newbie: keep 1~3 hostile mobs
            }
        } else if (region.type() == RegionType.PERIPHERY) {
            if (SpawnController.isPeripheryCapReached(level, chunk.getPos())) {
                ci.cancel(); // hard cap on periphery hostile mobs
            }
        }
    }

    @Inject(method = "spawnCategoryForChunk", at = @At("RETURN"))
    private static void settlementpressure$fillWildernessMinimum(MobCategory category, ServerLevel level, LevelChunk chunk,
            NaturalSpawner.SpawnPredicate predicate, NaturalSpawner.AfterSpawnCallback afterSpawnCallback, CallbackInfo ci) {
        if (level.dimension() != Level.OVERWORLD || category != MobCategory.MONSTER) {
            return;
        }
        int extra = SpawnController.extraWildernessCalls(level, chunk.getPos());
        if (extra <= 0) {
            return;
        }
        for (int i = 0; i < extra; i++) {
            BlockPos pos = randomPosInChunk(level, chunk);
            if (pos.getY() >= level.getMinBuildHeight() + 1) {
                NaturalSpawner.spawnCategoryForPosition(category, level, chunk, pos, predicate, afterSpawnCallback);
            }
        }
    }

    private static BlockPos randomPosInChunk(ServerLevel level, LevelChunk chunk) {
        ChunkPos chunkPos = chunk.getPos();
        int x = chunkPos.getMinBlockX() + level.getRandom().nextInt(16);
        int z = chunkPos.getMinBlockZ() + level.getRandom().nextInt(16);
        int y = chunk.getHeight(Heightmap.Types.WORLD_SURFACE, x, z) + 1;
        return new BlockPos(x, y, z);
    }
}
