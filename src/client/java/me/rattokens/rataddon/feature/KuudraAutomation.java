package me.rattokens.rataddon.feature;

import com.mojang.blaze3d.platform.InputConstants;
import me.rattokens.rataddon.RaTAddonClient;
import me.rattokens.rataddon.util.KuudraState;
import me.rattokens.rataddon.util.PhysicalInput;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;

/** Keeps Auto Sneak in Build, Auto Croesus and Kuudra Distance active. */
public final class KuudraAutomation {
	private static ClientLevel lastLevel;
	private static boolean toggleSneakHeld;
	private static boolean lastPhysicalSneak;
	private static boolean sneakControlled;

	private KuudraAutomation() {}

	public static void init() {
		ClientTickEvents.END_CLIENT_TICK.register(KuudraAutomation::tick);
		AutoCroesus.init();
		KuudraDistance.init();
	}

	private static void tick(Minecraft client) {
		if (client.level != lastLevel) {
			lastLevel = client.level;
			toggleSneakHeld = false;
			lastPhysicalSneak = false;
			releaseSneak(client);
		}
		if (client.player == null || client.level == null) return;
		handleToggleSneak(client);
	}

	private static void handleToggleSneak(Minecraft client) {
		boolean physicalSneak = physicalSneakDown(client);
		boolean pressed = physicalSneak && !lastPhysicalSneak;
		lastPhysicalSneak = physicalSneak;
		if (!RaTAddonClient.CONFIG.autoSneakBuild || KuudraState.phase != KuudraState.Phase.BUILD) {
			toggleSneakHeld = false;
			releaseSneak(client);
			return;
		}
		if (client.gui.screen() != null) {
			releaseSneak(client);
			return;
		}
		if (pressed) toggleSneakHeld = !toggleSneakHeld;
		client.options.keyShift.setDown(toggleSneakHeld);
		sneakControlled = true;
	}

	private static boolean physicalSneakDown(Minecraft client) {
		InputConstants.Key key = KeyMappingHelper.getBoundKeyOf(client.options.keyShift);
		return PhysicalInput.isDown(key);
	}

	private static void releaseSneak(Minecraft client) {
		if (!sneakControlled) return;
		client.options.keyShift.setDown(physicalSneakDown(client));
		sneakControlled = false;
	}
}
