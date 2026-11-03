package me.rattokens.rataddon.feature;

import me.rattokens.rataddon.RaTAddonClient;
import me.rattokens.rataddon.util.KuudraState;
import me.rattokens.rataddon.util.WorldRender;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.Locale;

/** Kuudra distance display. */
final class KuudraDistance {

	private KuudraDistance() {
	}

	static void init() {
		LevelRenderEvents.END_MAIN.register(KuudraDistance::render);
	}

	private static void render(net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderContext context) {
		Minecraft client = Minecraft.getInstance();
		if (!RaTAddonClient.CONFIG.kuudraDistance || !KuudraState.dps()
			|| client.player == null || client.level == null) {
			return;
		}
		Entity kuudra = KuudraState.kuudraEntity;
		if (kuudra == null || !kuudra.isAlive()) {
			return;
		}

		Vec3 playerPosition = client.player.position();
		Vec3 kuudraPosition = kuudra.position();
		Vec3 target = closestHorizontalPoint(playerPosition, kuudra.getBoundingBox());
		double distance = horizontalDistance(playerPosition, target);
		WorldRender.text(context, kuudraPosition.add(0, 8.5, 0),
			Component.literal(String.format(Locale.ROOT, "%.1fm", distance)), .20F, true, color(distance));

	}

	private static Vec3 closestHorizontalPoint(Vec3 player, AABB box) {
		double x = Math.max(box.minX, Math.min(box.maxX, player.x));
		double z = Math.max(box.minZ, Math.min(box.maxZ, player.z));
		return new Vec3(x, player.y, z);
	}

	private static double horizontalDistance(Vec3 from, Vec3 to) {
		double x = from.x - to.x;
		double z = from.z - to.z;
		return Math.sqrt(x * x + z * z);
	}

	private static int color(double distance) {
		double min = minDistance();
		double max = maxDistance();
		if (distance >= min && distance <= max) return 0xFF55FF55;
		if (distance < min - 2) return 0xFFFF5555;
		if (distance < min - 1) return 0xFFFFAA00;
		if (distance < min || distance <= max + 2) return 0xFFFFFF55;
		return 0xFFFFAA00;
	}

	private static double minDistance() {
		return Math.min(RaTAddonClient.CONFIG.boneMinDistance, RaTAddonClient.CONFIG.boneMaxDistance);
	}

	private static double maxDistance() {
		return Math.max(RaTAddonClient.CONFIG.boneMinDistance, RaTAddonClient.CONFIG.boneMaxDistance);
	}

}
