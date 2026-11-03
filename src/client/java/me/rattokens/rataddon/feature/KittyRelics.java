package me.rattokens.rataddon.feature;

import me.rattokens.rataddon.RaTAddonClient;
import me.rattokens.rataddon.util.TickScheduler;
import me.rattokens.rataddon.util.WorldRender;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLevelEvents;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.level.GameType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.Vec3;

/** Kittycat's M7 relic interaction, cauldron, and spawn-box helpers. */
public final class KittyRelics {
	private enum Relic {
		GREEN("Corrupted Green Relic", new BlockPos(49, 7, 44),
			new AABB(20.914, 6.726, 94.914, 21.086, 6.898, 95.086)),
		PURPLE("Corrupted Purple Relic", new BlockPos(54, 7, 41),
			new AABB(56.914, 8.726, 132.914, 57.086, 8.898, 133.086)),
		BLUE("Corrupted Blue Relic", new BlockPos(59, 7, 44),
			new AABB(91.914, 6.726, 94.914, 92.086, 6.898, 95.086)),
		ORANGE("Corrupted Orange Relic", new BlockPos(57, 7, 42),
			new AABB(92.914, 6.726, 56.914, 93.086, 6.898, 57.086)),
		RED("Corrupted Red Relic", new BlockPos(51, 7, 42),
			new AABB(20.914, 6.726, 59.914, 21.086, 6.898, 60.086));

		final String hoverName;
		final BlockPos cauldron;
		final AABB spawnBox;

		Relic(String hoverName, BlockPos cauldron, AABB spawnBox) {
			this.hoverName = hoverName;
			this.cauldron = cauldron;
			this.spawnBox = spawnBox;
		}
	}

	private static boolean inF7Boss;
	private static boolean carryingRelic;
	private static int pickupCooldown;

	private KittyRelics() {}

	public static void init() {
		ClientLevelEvents.AFTER_CLIENT_LEVEL_CHANGE.register((client, level) -> {
			inF7Boss = carryingRelic = false;
			pickupCooldown = 0;
		});
		ClientTickEvents.START_CLIENT_TICK.register(client -> {
			if (!RaTAddonClient.CONFIG.kittyRelics || !inF7Boss || client.player == null
				|| client.gameMode == null || client.gameMode.getPlayerMode() == GameType.SPECTATOR
				|| client.gui.screen() != null) return;
			if (RaTAddonClient.CONFIG.kittyCauldronTriggerbot && carryingRelic
				&& client.hitResult instanceof BlockHitResult hit) {
				for (Relic relic : Relic.values()) {
					if (!relic.hoverName.equals(client.player.getMainHandItem().getHoverName().getString())) continue;
					if (hit.getBlockPos().equals(relic.cauldron)
						|| hit.getBlockPos().equals(relic.cauldron.below())) {
						TickScheduler.click(client.options.keyUse);
						carryingRelic = false;
					}
					break;
				}
			}
			if (!RaTAddonClient.CONFIG.kittyRelicTriggerbot
				|| !(client.hitResult instanceof EntityHitResult hit)
				|| !(hit.getEntity() instanceof ArmorStand stand)
				|| !stand.getItemBySlot(EquipmentSlot.HEAD).getDisplayName().getString().contains("Relic")
				|| --pickupCooldown >= 0) return;
			Vec3 eye = client.player.getEyePosition();
			AABB box = stand.getBoundingBox();
			Vec3 point = new Vec3(Math.clamp(eye.x, box.minX, box.maxX),
				Math.clamp(eye.y, box.minY, box.maxY), Math.clamp(eye.z, box.minZ, box.maxZ));
			client.gameMode.interact(client.player, stand, new EntityHitResult(stand, point), InteractionHand.MAIN_HAND);
			pickupCooldown = 10;
		});
		LevelRenderEvents.END_MAIN.register(context -> {
			if (!RaTAddonClient.CONFIG.kittyRelics || !RaTAddonClient.CONFIG.kittyRelicSpawnBoxes
				|| !inF7Boss) return;
			Minecraft client = Minecraft.getInstance();
			if (client.player == null) return;
			Vec3 eye = client.player.getEyePosition();
			Vec3 end = eye.add(client.player.getLookAngle().scale(3));
			for (Relic relic : Relic.values()) {
				int color = relic.spawnBox.clip(eye, end).isPresent() ? 0xFF00FF00 : 0xFFFF0000;
				WorldRender.box(context, relic.spawnBox, color, 2, true);
			}
		});
	}

	public static void handleChat(String message) {
		if (message.contains("[BOSS] Maxor: WELL! WELL! WELL! LOOK WHO'S HERE!")) inF7Boss = true;
		if (!RaTAddonClient.CONFIG.kittyRelics || !inF7Boss) return;
		Minecraft client = Minecraft.getInstance();
		if (client.player != null && message.contains(client.player.getName().getString())
			&& message.contains("picked the Corrupted")) carryingRelic = true;
	}
}
