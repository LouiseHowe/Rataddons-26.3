package me.rattokens.rataddon.mixin;

import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import me.rattokens.rataddon.feature.NoGemstoneDesync;
import net.minecraft.world.level.block.IronBarsBlock;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(IronBarsBlock.class)
public abstract class NoGemstoneDesyncPaneMixin {
	@ModifyReturnValue(method = "updateShape", at = @At("RETURN"))
	private BlockState rataddon$keepGemstonePaneConnections(BlockState original) {
		return NoGemstoneDesync.fillDisconnectedPane(original);
	}
}
