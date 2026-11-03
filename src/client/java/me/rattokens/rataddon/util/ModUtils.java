package me.rattokens.rataddon.util;

import net.minecraft.client.Minecraft;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.ResolvableProfile;
import net.minecraft.world.item.component.SwingAnimation;

import java.util.concurrent.ThreadLocalRandom;

public final class ModUtils {
	private ModUtils() {
	}

	public static long now() {
		return System.currentTimeMillis();
	}

	public static int randomBetween(int min, int max) {
		return ThreadLocalRandom.current().nextInt(min, max + 1);
	}

	public static boolean useItem(InteractionHand hand) {
		return useItem(hand, true);
	}

	public static boolean useItem(InteractionHand hand, boolean swing) {
		Minecraft client = Minecraft.getInstance();
		if (client.gameMode == null || client.player == null) {
			return false;
		}
		InteractionResult result = client.gameMode.useItem((Player) client.player, hand);
		if (swing && result.consumesAction()) {
			client.player.swing(hand, SwingAnimation.DEFAULT, true);
		}
		return result.consumesAction();
	}

	public static boolean isPlayerHeadWithId(ItemStack stack, String id) {
		if (stack == null || !stack.is(Items.PLAYER_HEAD)) {
			return false;
		}
		ResolvableProfile profile = stack.get(DataComponents.PROFILE);
		return profile != null
			&& profile.partialProfile().id().toString().equals(id);
	}
}
