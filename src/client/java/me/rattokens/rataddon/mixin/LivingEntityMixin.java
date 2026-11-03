package me.rattokens.rataddon.mixin;

import me.rattokens.rataddon.feature.Animation;
import net.minecraft.client.Minecraft;
import net.minecraft.core.Holder;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.component.SwingAnimation;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(LivingEntity.class)
public abstract class LivingEntityMixin {
	@Inject(method = "getModifiedSwingDuration", at = @At("HEAD"), cancellable = true)
	private void rataddon$swingDuration(SwingAnimation swingAnimation, CallbackInfoReturnable<Integer> cir) {
		if (Animation.modifySwingDuration()) {
			cir.setReturnValue(Animation.swingDuration());
		}
	}
}
