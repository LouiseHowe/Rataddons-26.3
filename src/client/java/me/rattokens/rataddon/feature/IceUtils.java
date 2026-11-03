package me.rattokens.rataddon.feature;

import me.rattokens.rataddon.RaTAddonClient;
import me.rattokens.rataddon.util.ModUtils;
import me.rattokens.rataddon.util.SkyblockItem;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;

public final class IceUtils {
	private static final long COOLDOWN_MS = 5_000L;
	private static boolean ascending;
	private static long lastTriggerTime;

	private IceUtils() {
	}

	public static void init() {
		ClientTickEvents.END_CLIENT_TICK.register(client -> {
			if (client.player == null || !RaTAddonClient.CONFIG.iceHelper
				|| !isIceSprayWand(client.player.getMainHandItem())) {
				ascending = false;
				return;
			}

			double verticalSpeed = client.player.getDeltaMovement().y;
			if (client.player.onGround()) {
				ascending = false;
				return;
			}
			if (verticalSpeed > 0.05) {
				ascending = true;
				return;
			}
			if (!ascending || verticalSpeed > 0.03) {
				return;
			}

			ascending = false;
			if (client.player.getXRot() >= 0.0F
				|| ModUtils.now() - lastTriggerTime < COOLDOWN_MS) {
				return;
			}
			if (ModUtils.useItem(InteractionHand.MAIN_HAND, false)) {
				lastTriggerTime = ModUtils.now();
			}
		});
	}

	private static boolean isIceSprayWand(ItemStack stack) {
		String skyblockId = SkyblockItem.id(stack);
		return skyblockId.endsWith("ICE_SPRAY_WAND")
			|| stack.getHoverName().getString().contains("Ice Spray Wand");
	}
}
