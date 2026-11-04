package me.rattokens.rataddon.mixin;

import me.rattokens.rataddon.feature.AutoFish;
import net.minecraft.client.player.KeyboardInput;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(KeyboardInput.class)
public class KeyboardInputMixin {
	@Inject(method = "tick", at = @At("HEAD"))
	private void rataddon$holdFbWalkKeys(CallbackInfo ci) {
		AutoFish.holdFbWalkKeys();
	}
}
