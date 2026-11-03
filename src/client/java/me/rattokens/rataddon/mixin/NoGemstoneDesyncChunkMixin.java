package me.rattokens.rataddon.mixin;

import me.rattokens.rataddon.feature.NoGemstoneDesync;
import me.rattokens.rataddon.feature.MineshaftEsp;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(LevelChunk.class)
public abstract class NoGemstoneDesyncChunkMixin {
	@Shadow public abstract BlockState getBlockState(BlockPos pos);

	@Inject(method = "setBlockState", at = @At("HEAD"))
	private void rataddon$onBlockChange(BlockPos pos, BlockState state, int flags,
			CallbackInfoReturnable<BlockState> cir) {
		BlockState old = getBlockState(pos);
		if (old != state) {
			NoGemstoneDesync.onBlockUpdate(pos, old, state);
			MineshaftEsp.onBlockUpdate(pos, state);
		}
	}
}
