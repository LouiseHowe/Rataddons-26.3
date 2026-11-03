// SPDX-License-Identifier: LGPL-3.0-only
// Baity's 3D item scaling injection, adapted for RatAddons.
package me.rattokens.rataddon.mixin;

import com.mojang.blaze3d.vertex.PoseStack;
import me.rattokens.rataddon.feature.DroppedItemRenderState;
import me.rattokens.rataddon.feature.DroppedItemScale;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.ItemEntityRenderer;
import net.minecraft.client.renderer.entity.state.ItemEntityRenderState;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.world.entity.item.ItemEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ItemEntityRenderer.class)
public class DroppedItemRendererMixin {
	@Inject(method = "extractRenderState", at = @At("TAIL"))
	private void rataddon$prepareDrop(ItemEntity entity, ItemEntityRenderState state, float partialTick, CallbackInfo ci) {
		((DroppedItemRenderState) state).rataddon$setDropScale(DroppedItemScale.multiplier(entity.getItem()));
	}

	@Inject(method = "submit", at = @At(value = "INVOKE",
		target = "Lnet/minecraft/client/renderer/entity/ItemEntityRenderer;submitMultipleFromCount(Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/SubmitNodeCollector;ILnet/minecraft/client/renderer/entity/state/ItemClusterRenderState;Lnet/minecraft/util/RandomSource;Lnet/minecraft/world/phys/AABB;)V",
		shift = At.Shift.BEFORE))
	private void rataddon$scaleDrop(ItemEntityRenderState state, PoseStack poseStack,
		SubmitNodeCollector collector, CameraRenderState cameraState, CallbackInfo ci) {
		float scale = ((DroppedItemRenderState) state).rataddon$getDropScale();
		if (scale != 1.0F) poseStack.scale(scale, scale, scale);
	}
}
