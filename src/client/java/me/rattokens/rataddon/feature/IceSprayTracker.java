package me.rattokens.rataddon.feature;

import me.rattokens.rataddon.RaTAddonClient;
import me.rattokens.rataddon.util.SkyblockItem;
import me.rattokens.rataddon.util.SkyblockLocation;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.protocol.game.ClientboundAddEntityPacket;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentLinkedQueue;

public final class IceSprayTracker {
	private static final double NEAR = 0.01;
	private static final double FAR = 8.0;
	private static final double FOV = Math.toRadians(90.0);
	private static final double ASPECT = 1.0;
	private static final ConcurrentLinkedQueue<int[]> ITEM_CANDIDATES = new ConcurrentLinkedQueue<>();
	private static int tick;
	private static int lastHeldSpray = -100;
	private static boolean inDungeon;
	private static ClientLevel lastLevel;

	private IceSprayTracker() {
	}

	public static void init() {
		ClientTickEvents.END_CLIENT_TICK.register(IceSprayTracker::tick);
	}

	public static void onAddEntity(ClientboundAddEntityPacket packet) {
		if (RaTAddonClient.CONFIG.iceSprayChat && dungeonRequirementMet()
			&& packet.getType() == EntityTypes.ITEM) {
			ITEM_CANDIDATES.offer(new int[]{5, packet.getId()});
		}
	}

	private static void tick(Minecraft client) {
		tick++;
		if (client.level != lastLevel) {
			lastLevel = client.level;
			ITEM_CANDIDATES.clear();
			lastHeldSpray = -100;
		}
		if (tick % 20 == 0) {
			inDungeon = SkyblockLocation.inDungeon();
		}
		if (!RaTAddonClient.CONFIG.iceSprayChat || client.level == null || client.player == null
			|| !dungeonRequirementMet()) {
			ITEM_CANDIDATES.clear();
			return;
		}
		if (isSpray(client.player.getMainHandItem())) {
			lastHeldSpray = tick;
		}

		boolean foundIce = false;
		for (int remaining = ITEM_CANDIDATES.size(); remaining > 0; remaining--) {
			int[] candidate = ITEM_CANDIDATES.poll();
			if (candidate == null) {
				break;
			}
			if (!(client.level.getEntity(candidate[1]) instanceof ItemEntity item)) {
				if (--candidate[0] >= 0) {
					ITEM_CANDIDATES.offer(candidate);
				}
				continue;
			}
			if (item.getItem().is(Items.ICE)) {
				foundIce = true;
				ITEM_CANDIDATES.clear();
				break;
			}
		}
		if (!foundIce) {
			return;
		}

		List<Player> icers = new ArrayList<>();
		for (Player player : client.level.players()) {
			if (player != client.player && isTeammate(client, player) && isSpray(player.getMainHandItem())) {
				icers.add(player);
			}
		}
		if (icers.isEmpty() && tick - lastHeldSpray < 5) {
			icers.add(client.player);
		}
		for (Player icer : icers) {
			report(client, icer, frozenBy(client, icer));
		}
	}

	private static List<LivingEntity> frozenBy(Minecraft client, Player icer) {
		double x = icer.getX();
		double y = icer.getEyeY();
		double z = icer.getZ();
		Vec3 look = icer.getLookAngle();
		double[][] planes = {
			plane(rotate(look, (FOV - Math.PI) * 0.5, 0.0), x, y, z),
			plane(rotate(look, (-FOV + Math.PI) * 0.5, 0.0), x, y, z),
			plane(rotate(look, 0.0, (FOV * ASPECT - Math.PI) * 0.5), x, y, z),
			plane(rotate(look, 0.0, (-FOV * ASPECT + Math.PI) * 0.5), x, y, z),
			{look.x, look.y, look.z,
				-(look.x * (x + look.x * NEAR) + look.y * (y + look.y * NEAR) + look.z * (z + look.z * NEAR))},
			{-look.x, -look.y, -look.z,
				look.x * (x + look.x * FAR) + look.y * (y + look.y * FAR) + look.z * (z + look.z * FAR)}
		};
		AABB hull = new AABB(x - FAR, y - FAR, z - FAR, x + FAR, y + FAR, z + FAR);
		List<LivingEntity> hit = new ArrayList<>();
		for (Entity entity : client.level.entitiesForRendering()) {
			if (!(entity instanceof LivingEntity living) || entity instanceof ArmorStand
				|| !living.isAlive() || entity == icer) {
				continue;
			}
			if (entity instanceof Player player && isTeammate(client, player)) {
				continue;
			}
			AABB box = entity.getBoundingBox();
			if (box.intersects(hull) && !outside(box, planes)) {
				hit.add(living);
			}
		}
		return hit;
	}

	private static boolean outside(AABB box, double[][] planes) {
		double[] xs = {box.minX, box.maxX};
		double[] ys = {box.minY, box.maxY};
		double[] zs = {box.minZ, box.maxZ};
		for (double cx : xs) {
			for (double cy : ys) {
				for (double cz : zs) {
					boolean anyNegative = false;
					for (double[] p : planes) {
						if (cx * p[0] + cy * p[1] + cz * p[2] + p[3] < 0.0) {
							anyNegative = true;
							break;
						}
					}
					if (!anyNegative) {
						return false;
					}
				}
			}
		}
		return true;
	}

	private static double[] plane(double[] normal, double x, double y, double z) {
		return new double[]{normal[0], normal[1], normal[2], -(normal[0] * x + normal[1] * y + normal[2] * z)};
	}

	private static double[] rotate(Vec3 v, double t, double p) {
		double ct = Math.cos(t);
		double st = Math.sin(t);
		double cp = Math.cos(p);
		double sp = Math.sin(p);
		return new double[]{
			v.x * ct * cp - v.z * st + v.y * ct * sp,
			-v.x * sp + v.y * cp,
			v.x * st * cp + v.z * ct + v.y * st * sp
		};
	}

	private static void report(Minecraft client, Player icer, List<LivingEntity> hit) {
		Map<String, Integer> counts = new LinkedHashMap<>();
		for (LivingEntity entity : hit) {
			counts.merge(displayName(client, entity), 1, Integer::sum);
		}
		MutableComponent message = Component.literal("[RaT] ").withStyle(ChatFormatting.DARK_AQUA)
			.append(Component.literal(icer.getGameProfile().name()).withStyle(ChatFormatting.AQUA));
		if (counts.isEmpty()) {
			message.append(Component.literal(" sprayed ice, hit nothing").withStyle(ChatFormatting.GRAY));
		} else {
			message.append(Component.literal(" iced " + hit.size() + ": ").withStyle(ChatFormatting.GRAY));
			boolean first = true;
			for (Map.Entry<String, Integer> entry : counts.entrySet()) {
				if (!first) {
					message.append(Component.literal(", ").withStyle(ChatFormatting.GRAY));
				}
				first = false;
				message.append(Component.literal(entry.getKey()).withStyle(ChatFormatting.WHITE));
				if (entry.getValue() > 1) {
					message.append(Component.literal(" x" + entry.getValue()).withStyle(ChatFormatting.YELLOW));
				}
			}
		}
		client.player.sendSystemMessage(message);
		if (RaTAddonClient.CONFIG.iceAnnouncePartyChat && client.getConnection() != null) {
			client.getConnection().sendCommand("pc " + message.getString());
		}
	}

	private static boolean dungeonRequirementMet() {
		return !RaTAddonClient.CONFIG.iceAnnounceDungeonOnly || inDungeon;
	}

	private static String displayName(Minecraft client, LivingEntity entity) {
		AABB area = entity.getBoundingBox().inflate(0.5, 0.0, 0.5).expandTowards(0.0, 3.0, 0.0);
		ArmorStand best = null;
		double bestDistance = Double.MAX_VALUE;
		for (ArmorStand stand : client.level.getEntitiesOfClass(ArmorStand.class, area,
			stand -> stand.hasCustomName() && stand.getCustomName() != null
				&& !isDamageIndicator(stand.getCustomName().getString()))) {
			double distance = stand.distanceToSqr(entity);
			if (distance < bestDistance) {
				bestDistance = distance;
				best = stand;
			}
		}
		if (best != null) {
			String name = ChatFormatting.stripFormatting(best.getCustomName().getString());
			if (name != null && !name.isBlank()) {
				return name.replaceAll("\\s*[\\d,.]+[kKmMbB]?/[\\d,.]+[kKmMbB]?❤?\\s*$", "").trim();
			}
		}
		return entity.getName().getString();
	}

	private static boolean isDamageIndicator(String name) {
		String plain = ChatFormatting.stripFormatting(name);
		return plain != null && plain.matches("^[\\p{S}\\p{P}\\s]*[+-]?[\\d,.]+[kKmMbBtT]?[\\p{S}\\p{P}\\s]*$");
	}

	private static boolean isTeammate(Minecraft client, Player player) {
		return client.getConnection() != null && client.getConnection().getPlayerInfo(player.getUUID()) != null;
	}

	private static boolean isSpray(ItemStack stack) {
		return stack != null && SkyblockItem.id(stack).endsWith("ICE_SPRAY_WAND");
	}
}
