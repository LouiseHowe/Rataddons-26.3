package me.rattokens.rataddon.mixin;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import me.rattokens.rataddon.feature.PingOffsetMiner;
import net.minecraft.client.renderer.LevelRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(LevelRenderer.class)
public class PingOffsetMinerProgressMixin {
	@ModifyExpressionValue(method = "submitBlockDestroyAnimation", at = @At(value = "INVOKE",
		target = "Lnet/minecraft/client/renderer/state/level/BlockBreakingRenderState;progress()I"))
	private int rataddon$pomProgress(int stage) {
		return PingOffsetMiner.progressStage(stage);
	}
}
