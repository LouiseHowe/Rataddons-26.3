package me.rattokens.rataddon.mixin;

import me.rattokens.rataddon.feature.Animation;
import me.rattokens.rataddon.feature.ClickThroughPlayers;
import me.rattokens.rataddon.feature.PlayerAlphaRenderState;
import me.rattokens.rataddon.RaTAddonClient;
import me.rattokens.rataddon.util.AntiBotUtils;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.renderer.entity.player.AvatarRenderer;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.world.entity.Avatar;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(AvatarRenderer.class)
public class AvatarRendererMixin {
	@Inject(
		method = "extractRenderState(Lnet/minecraft/world/entity/Avatar;Lnet/minecraft/client/renderer/entity/state/AvatarRenderState;F)V",
		at = @At("RETURN")
	)
	private void rataddon$applyThirdPersonBlockingPose(Avatar entity, AvatarRenderState state, float partialTicks, CallbackInfo ci) {
		((PlayerAlphaRenderState) state).rataddon$setPlayerAlpha(ClickThroughPlayers.alpha(entity));
		if (RaTAddonClient.CONFIG.focusPlayer && entity instanceof Player player && !AntiBotUtils.isBot(player)) {
			state.isInvisible = false;
			state.isInvisibleToPlayer = false;
		}

		if (Animation.shouldApplyThirdPersonBlockingAnim(entity, HumanoidArm.RIGHT)) {
			state.rightArmPose = HumanoidModel.ArmPose.BLOCK;
		}
		if (Animation.shouldApplyThirdPersonBlockingAnim(entity, HumanoidArm.LEFT)) {
			state.leftArmPose = HumanoidModel.ArmPose.BLOCK;
		}
	}
}
