package me.rattokens.rataddon.util;

import me.rattokens.rataddon.feature.RendMacro;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Display;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.Vec3;

public final class BoneTracker {
	private static Display.ItemDisplay boneStand;
	private static Vec3 prev;
	private static Vec3 initialDir;
	private static boolean returning;
	private static boolean awaitBone;
	private static Vec3 throwOrigin;
	private static ClientLevel lastLevel;

	private BoneTracker() {
	}

	public static void init() {
		ClientTickEvents.END_CLIENT_TICK.register(client -> {
			if (client.level != lastLevel) {
				lastLevel = client.level;
				boneStand = null;
				prev = null;
				initialDir = null;
				returning = false;
				awaitBone = false;
				throwOrigin = null;
			}
			handleBone();
		});
	}

	public static void onAddEntity(Entity entity) {
		if (!awaitBone || throwOrigin == null || !(entity instanceof Display.ItemDisplay display)) {
			return;
		}
		Vec3 rel = throwOrigin.subtract(entity.position());
		if (Math.abs(rel.x) <= 0.3 && Math.abs(rel.z) <= 0.3) {
			TickScheduler.schedule(1, () -> addBone(display));
		}
	}

	public static void useItem(Player player, InteractionHand hand, InteractionResult result) {
		if (!(result instanceof InteractionResult.Pass)) {
			return;
		}
		if (!"STARRED_BONE_BOOMERANG".equals(SkyblockItem.id(player.getItemInHand(hand)))) {
			return;
		}
		awaitBone = true;
		throwOrigin = player.position();
		TickScheduler.schedule(12, () -> awaitBone = false);
	}

	private static void addBone(Display.ItemDisplay entity) {
		if (entity.getItemStack().is(Items.GHAST_TEAR)) {
			boneStand = entity;
			prev = entity.position();
			initialDir = null;
			returning = false;
		}
	}

	private static void handleBone() {
		Display.ItemDisplay stand = boneStand;
		if (stand == null) {
			return;
		}
		if (!stand.isAlive()) {
			prev = null;
			initialDir = null;
			returning = false;
			return;
		}
		Vec3 curr = stand.position();
		if (prev != null) {
			Vec3 move = curr.subtract(prev);
			if (move.lengthSqr() > 1.0E-6) {
				if (initialDir == null) {
					initialDir = move.normalize();
				} else if (!returning && move.normalize().dot(initialDir) < 0.995) {
					returning = true;
					RendMacro.onBackbone();
				}
			}
		}
		prev = curr;
	}
}
