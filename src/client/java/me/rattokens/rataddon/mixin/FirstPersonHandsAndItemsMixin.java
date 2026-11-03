package me.rattokens.rataddon.mixin;

import me.rattokens.rataddon.feature.Animation;
import net.minecraft.client.player.FirstPersonHandsAndItems;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(FirstPersonHandsAndItems.class)
public class FirstPersonHandsAndItemsMixin {
	@Shadow private float mainHandHeight;
	@Shadow private float offHandHeight;
	@Shadow private ItemStack mainHandItem;
	@Shadow private ItemStack offHandItem;

	@Inject(method = "tick", at = @At("RETURN"))
	private void rataddon$hideHotbarSwitchAnimation(LocalPlayer player, CallbackInfo ci) {
		if (Animation.disableSwapMain()) {
			mainHandHeight = 1.0F;
			mainHandItem = player.getMainHandItem();
		}
		if (Animation.disableSwapOff()) {
			offHandHeight = 1.0F;
			offHandItem = player.getOffhandItem();
		}
	}
}
