package me.rattokens.rataddon.mixin;

import com.mojang.blaze3d.platform.InputConstants;
import me.rattokens.rataddon.feature.AutoClicker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.MouseHandler;
import net.minecraft.client.input.MouseButtonInfo;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(MouseHandler.class)
public abstract class MouseHandlerAutoClickMixin {
	@Inject(method = "onButton", at = @At("HEAD"), cancellable = true)
	private void rataddon$suppressNativeAutoClick(long window, MouseButtonInfo input, int action, CallbackInfo ci) {
		if (action == InputConstants.PRESS && window == Minecraft.getInstance().getWindow().handle()
			&& AutoClicker.suppressPhysicalClick(input.button())) ci.cancel();
	}
}
