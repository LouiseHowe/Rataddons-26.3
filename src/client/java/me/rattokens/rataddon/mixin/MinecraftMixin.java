package me.rattokens.rataddon.mixin;

import me.rattokens.rataddon.feature.Animation;
import net.minecraft.client.Minecraft;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.component.SwingAnimation;
import org.objectweb.asm.Opcodes;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Minecraft.class)
public class MinecraftMixin {
	@Inject(
		method = "handleKeybinds",
		at = @At(value = "FIELD", target = "Lnet/minecraft/client/Options;keyUse:Lnet/minecraft/client/KeyMapping;", ordinal = 0, opcode = Opcodes.GETFIELD)
	)
	private void rataddon$swingWhileUsing(CallbackInfo ci) {
		Minecraft client = (Minecraft) (Object) this;
		if (client.player != null && Animation.shouldSwingOnUseKey()) {
			client.player.swing(InteractionHand.MAIN_HAND, SwingAnimation.DEFAULT, false);
		}
	}
}
