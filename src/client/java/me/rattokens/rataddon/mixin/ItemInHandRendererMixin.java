package me.rattokens.rataddon.mixin;

import me.rattokens.rataddon.feature.Storm;
import net.minecraft.client.renderer.FirstPersonHandsAndItemsRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(FirstPersonHandsAndItemsRenderer.class)
public class ItemInHandRendererMixin {
	@Inject(method = "submitArmWithItem", at = @At("HEAD"))
	private void rataddon$tintBegin(CallbackInfo ci) {
		Storm.setTintActive(true);
	}

	@Inject(method = "submitArmWithItem", at = @At("RETURN"))
	private void rataddon$tintEnd(CallbackInfo ci) {
		Storm.setTintActive(false);
	}
}
