package me.rattokens.rataddon.util;

import net.minecraft.client.Minecraft;
import net.minecraft.network.protocol.game.ServerboundUseItemPacket;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.level.GameType;
import net.minecraft.util.Mth;

/** Sends an item-use packet with a selected server-side look direction. */
public final class KittyKuudraClick {
	private KittyKuudraClick() {}

	public static void useItem(float yaw, float pitch) {
		Minecraft client = Minecraft.getInstance();
		if (client.gameMode == null || client.player == null || client.level == null
			|| client.gameMode.getPlayerMode() == GameType.SPECTATOR) return;
		client.gameMode.startPrediction(client.level, sequence -> {
			ServerboundUseItemPacket packet = new ServerboundUseItemPacket(InteractionHand.MAIN_HAND, sequence, yaw, pitch);
			var stack = client.player.getMainHandItem();
			if (!client.player.getCooldowns().isOnCooldown(stack)) {
				InteractionResult result = stack.use(client.level, client.player, InteractionHand.MAIN_HAND);
				if (result instanceof InteractionResult.Success success && success.heldItemTransformedTo() != null)
					client.player.setItemInHand(InteractionHand.MAIN_HAND, success.heldItemTransformedTo());
			}
			return packet;
		});
	}

	public static void applyGcd(float yaw, float pitch) {
		Minecraft client = Minecraft.getInstance();
		if (client.player == null) return;
		float sensitivity = client.options.sensitivity().get().floatValue();
		float multiplier = sensitivity * 0.6F + 0.2F;
		float step = multiplier * multiplier * multiplier * 1.2F;
		client.player.setYRot(client.player.getYRot()
			+ Math.round(Mth.wrapDegrees(yaw - client.player.getYRot()) / step) * step);
		client.player.setXRot(Math.clamp(client.player.getXRot()
			+ Math.round((pitch - client.player.getXRot()) / step) * step, -90F, 90F));
	}

	public static float yawTo(net.minecraft.world.phys.Vec3 from, net.minecraft.world.phys.Vec3 target) {
		return (float) Math.toDegrees(Math.atan2(-(target.x - from.x), target.z - from.z));
	}

	public static float pitchTo(net.minecraft.world.phys.Vec3 from, net.minecraft.world.phys.Vec3 target) {
		return (float) Math.toDegrees(Math.atan2(-(target.y - from.y),
			Math.hypot(target.x - from.x, target.z - from.z)));
	}
}
