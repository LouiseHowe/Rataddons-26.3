package me.rattokens.rataddon.feature;

import me.rattokens.rataddon.RaTAddonClient;
import me.rattokens.rataddon.util.AntiBotUtils;
import me.rattokens.rataddon.util.SkyblockLocation;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;

import java.util.Collections;
import java.util.Set;
import java.util.WeakHashMap;
import java.util.function.Predicate;

public final class ClickThroughPlayers {
	private static final Set<Entity> CLICKED_THROUGH = Collections.newSetFromMap(new WeakHashMap<>());

	private ClickThroughPlayers() {}

	public static boolean active() {
		var config = RaTAddonClient.CONFIG;
		return config != null && config.clickThroughPlayers && (!config.clickThroughDungeonKuudraOnly
			|| SkyblockLocation.inDungeon() || SkyblockLocation.inKuudra());
	}

	public static Predicate<Entity> filter(Predicate<Entity> original) {
		CLICKED_THROUGH.clear();
		if (!active()) return original;
		return entity -> {
			if (!original.test(entity)) return false;
			if (entity instanceof Player player && !AntiBotUtils.isBot(player)) {
				CLICKED_THROUGH.add(entity);
				return false;
			}
			return true;
		};
	}

	public static int alpha(Entity entity) {
		if (entity instanceof Player player && AntiBotUtils.isBot(player)) return 255;
		if (!RaTAddonClient.CONFIG.clickThroughPlayerAlpha || !active() || !CLICKED_THROUGH.contains(entity)) return 255;
		var player = Minecraft.getInstance().player;
		if (player == null) return 255;
		return (int) Math.min(255, 16 * entity.distanceToSqr(player.getEyePosition()));
	}
}
