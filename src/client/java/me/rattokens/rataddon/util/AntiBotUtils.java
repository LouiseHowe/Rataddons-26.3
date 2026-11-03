package me.rattokens.rataddon.util;

import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.player.Player;

/** Baity's NPC predicate used only by player-targeting visual and click features. */
public final class AntiBotUtils {
	private AntiBotUtils() {}

	public static boolean isBot(Player player) {
		return player != null && player != Minecraft.getInstance().player
			&& player.getUUID().version() == 2 && player.getHealth() == 20.0F;
	}
}
