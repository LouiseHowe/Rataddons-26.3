package me.rattokens.rataddon.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.mojang.blaze3d.vertex.PoseStack;
import me.rattokens.rataddon.feature.Animation;
import net.minecraft.client.renderer.FirstPersonHandsAndItemsRenderer;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.state.level.FirstPersonHandsAndItemsRenderState;
import net.minecraft.client.renderer.state.level.PlayerRenderState;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(FirstPersonHandsAndItemsRenderer.class)
public abstract class ItemInHandRendererAnimationMixin {
	@Unique
	private boolean rataddon$blocked;

	@Shadow
	protected abstract void applyItemArmAttackTransform(PoseStack poseStack, HumanoidArm arm, float attack);

	@Inject(method = "submitArmWithItem", at = @At("HEAD"))
	private void rataddon$cacheBlockingState(PlayerRenderState player, FirstPersonHandsAndItemsRenderState handState,
											 float frameInterp, float xRot,
											 InteractionHand hand, float attack, ItemStack itemStack,
											 float inverseArmHeight, PoseStack poseStack,
											 SubmitNodeCollector submitNodeCollector, int lightCoords, CallbackInfo ci) {
		this.rataddon$blocked = Animation.shouldApplyBlockingAnimation(hand, itemStack);
	}

	@Inject(method = "submitArmWithItem", at = @At("RETURN"))
	private void rataddon$clearBlockingState(CallbackInfo ci) {
		this.rataddon$blocked = false;
	}

	@Inject(
		method = "submitArmWithItem",
		at = @At(
			value = "INVOKE",
			target = "Lnet/minecraft/client/renderer/FirstPersonHandsAndItemsRenderer;applyItemArmTransform(Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/world/entity/HumanoidArm;F)V",
			ordinal = 2,
			shift = At.Shift.AFTER
		)
	)
	private void rataddon$addSwingToEating(PlayerRenderState player, FirstPersonHandsAndItemsRenderState handState,
										   float frameInterp, float xRot,
										   InteractionHand hand, float attack, ItemStack itemStack,
										   float inverseArmHeight, PoseStack poseStack,
										   SubmitNodeCollector submitNodeCollector, int lightCoords, CallbackInfo ci) {
		if (Animation.shouldAddSwingWhileUsing(attack)) {
			HumanoidArm arm = hand == InteractionHand.MAIN_HAND ? player.avatarRenderState.mainArm : player.avatarRenderState.mainArm.getOpposite();
			this.applyItemArmAttackTransform(poseStack, arm, attack);
		}
	}

	@Inject(method = "swingArm", at = @At("HEAD"), cancellable = true)
	private void rataddon$cancelSwingForBlocking(float attack, PoseStack poseStack, int invert, HumanoidArm arm, CallbackInfo ci) {
		if (this.rataddon$blocked) {
			ci.cancel();
		}
	}

	@Inject(
		method = "submitArmWithItem",
		at = @At(
			value = "INVOKE",
			target = "Lnet/minecraft/client/renderer/item/ItemStackRenderState;submit(Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/SubmitNodeCollector;III)V",
			ordinal = 1,
			shift = At.Shift.BEFORE
		)
	)
	private void rataddon$applyBlockingAnimation(PlayerRenderState player, FirstPersonHandsAndItemsRenderState handState,
												 float frameInterp, float xRot,
												 InteractionHand hand, float attack, ItemStack itemStack,
												 float inverseArmHeight, PoseStack poseStack,
												 SubmitNodeCollector submitNodeCollector, int lightCoords, CallbackInfo ci) {
		if (this.rataddon$blocked) {
			HumanoidArm arm = hand == InteractionHand.MAIN_HAND ? player.avatarRenderState.mainArm : player.avatarRenderState.mainArm.getOpposite();
			Animation.applyBlockingTransform(poseStack, arm, attack);
		}
	}

	@WrapOperation(method = "swingArm", at = @At(value = "INVOKE", target = "Lcom/mojang/blaze3d/vertex/PoseStack;translate(FFF)V"))
	private void rataddon$wrapSwingArmTranslate(PoseStack poseStack, float x, float y, float z, Operation<Void> original) {
		if (!Animation.shouldSkipFluxTranslate()) {
			original.call(poseStack, x, y, z);
		}
	}
}
