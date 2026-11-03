package me.rattokens.rataddon.feature;

import me.rattokens.rataddon.RaTAddonClient;
import me.rattokens.rataddon.util.SkyblockItem;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.boss.enderdragon.EnderDragon;

import java.util.regex.Pattern;

public final class DianaFeet {
	private static final Pattern LIVID_NAME = Pattern.compile("^\\w+ Livid$");

	private DianaFeet() {
	}

	public static boolean shouldSkip(LivingEntity entity) {
		if (!RaTAddonClient.CONFIG.dianaFeet || !entity.level().isClientSide()
			|| entity instanceof EnderDragon || (entity.isAlive() && entity.getHealth() > 0.0F)) {
			return false;
		}
		var player = Minecraft.getInstance().player;
		if (player == null) {
			return false;
		}

		boolean holdingSpade = switch (SkyblockItem.id(player.getMainHandItem())) {
			case "ANCESTRAL_SPADE", "ARCHAIC_SPADE", "DEIFIC_SPADE" -> true;
			default -> false;
		};

		return holdingSpade && !LIVID_NAME.matcher(entity.getName().getString()).matches();
	}
}
