package me.rattokens.rataddon.mixin;

import com.mojang.blaze3d.vertex.PoseStack;
import me.rattokens.rataddon.RaTAddonClient;
import net.minecraft.client.renderer.FirstPersonHandsAndItemsRenderer;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.state.level.FirstPersonHandsAndItemsRenderState;
import net.minecraft.client.renderer.state.level.PlayerRenderState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(FirstPersonHandsAndItemsRenderer.class)
public class FirstPersonHandsAndItemsRendererMixin {
	@Inject(method = "submitHandsWithItems", at = @At("HEAD"))
	private void rataddon$showFocusedPlayerHands(float partialTicks, PoseStack poseStack,
			SubmitNodeCollector collector, PlayerRenderState playerState,
			FirstPersonHandsAndItemsRenderState handsState, CallbackInfo ci) {
		if (RaTAddonClient.CONFIG != null && RaTAddonClient.CONFIG.focusPlayer
				&& playerState.avatarRenderState != null) {
			playerState.avatarRenderState.isInvisible = false;
		}
	}
}
