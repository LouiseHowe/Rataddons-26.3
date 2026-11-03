package me.rattokens.rataddon.mixin;

import me.rattokens.rataddon.RaTAddonClient;
import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.AbstractSignEditScreen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.network.chat.Component;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(AbstractSignEditScreen.class)
public abstract class AbstractSignEditScreenMixin extends Screen {
	protected AbstractSignEditScreenMixin(Component title) {
		super(title);
	}

	@Inject(method = "keyPressed", at = @At("HEAD"), cancellable = true)
	private void rataddon$confirmOnEnter(KeyEvent event, CallbackInfoReturnable<Boolean> cir) {
		if (RaTAddonClient.CONFIG.signEnterKey && event.isConfirmation()
			&& !InputConstants.isKeyDown(InputConstants.KEY_LSHIFT)
			&& !InputConstants.isKeyDown(InputConstants.KEY_RSHIFT)) {
			this.minecraft.gui.setScreen(null);
			cir.setReturnValue(true);
		}
	}
}
