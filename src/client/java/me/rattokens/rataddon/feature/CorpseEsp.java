package me.rattokens.rataddon.feature;

import me.rattokens.rataddon.RaTAddonClient;
import me.rattokens.rataddon.util.SkyblockLocation;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLevelEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.decoration.ArmorStand;

import java.util.HashMap;
import java.util.EnumMap;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** XJA-style corpse recognition and vanilla entity outline colors. */
public final class CorpseEsp {
	private static final Pattern LOOT_STATUS = Pattern.compile(
		"(?i)\\b(Lapis|Tungsten|Umber|Vanguard):\\s*(NOT\\s+)?LOOTED\\b");
	private static volatile Map<Integer, Integer> corpseColors = Map.of();
	private CorpseEsp() {}

	public static void init() {
		ClientTickEvents.END_CLIENT_TICK.register(CorpseEsp::tick);
		ClientLevelEvents.AFTER_CLIENT_LEVEL_CHANGE.register((client, level) -> corpseColors = Map.of());
	}

	private static void tick(Minecraft client) {
		if (!active() || client.level == null || client.player == null) {
			corpseColors = Map.of();
			return;
		}
		Map<CorpseKind, Boolean> unlooted = tabLootStatus(client);
		Map<Integer, Integer> next = new HashMap<>();
		for (Entity entity : client.level.entitiesForRendering()) {
			if (!(entity instanceof ArmorStand stand) || entity.isRemoved()
				|| !entity.isAlive() || entity.distanceToSqr(client.player) > 128.0 * 128.0) continue;
			CorpseKind kind = fromHelmet(stand);
			if (kind != null && unlooted.getOrDefault(kind, true)) next.put(entity.getId(), kind.color);
		}
		corpseColors = next.isEmpty() ? Map.of() : Map.copyOf(next);
	}

	public static boolean glowing(Entity entity) {
		return entity != null && active() && corpseColors.containsKey(entity.getId());
	}

	public static int outlineColor(Entity entity) {
		return entity == null || !active() ? 0 : corpseColors.getOrDefault(entity.getId(), 0);
	}

	public static void onEntityRemoved(int id) {
		if (!corpseColors.containsKey(id)) return;
		Map<Integer, Integer> next = new HashMap<>(corpseColors);
		next.remove(id);
		corpseColors = next.isEmpty() ? Map.of() : Map.copyOf(next);
	}

	private static Map<CorpseKind, Boolean> tabLootStatus(Minecraft client) {
		Map<CorpseKind, Boolean> status = new EnumMap<>(CorpseKind.class);
		if (client.getConnection() == null) return status;
		for (PlayerInfo info : client.getConnection().getListedOnlinePlayers()) {
			Component display = info.getTabListDisplayName();
			if (display == null) continue;
			Matcher matcher = LOOT_STATUS.matcher(display.getString());
			if (!matcher.find()) continue;
			CorpseKind kind = switch (matcher.group(1).toLowerCase(Locale.ROOT)) {
				case "lapis" -> CorpseKind.LAPIS;
				case "tungsten" -> CorpseKind.TUNGSTEN;
				case "umber" -> CorpseKind.UMBER;
				case "vanguard" -> CorpseKind.VANGUARD;
				default -> null;
			};
			if (kind != null) status.merge(kind, matcher.group(2) != null, Boolean::logicalOr);
		}
		return status;
	}

	public static CorpseKind fromHelmet(ArmorStand stand) {
		if (stand == null || stand.getItemBySlot(EquipmentSlot.HEAD).isEmpty()) return null;
		String name = ChatFormatting.stripFormatting(
			stand.getItemBySlot(EquipmentSlot.HEAD).getHoverName().getString());
		if (name == null) return null;
		return switch (name.trim()) {
			case "Lapis Armor Helmet" -> CorpseKind.LAPIS;
			case "Mineral Helmet" -> CorpseKind.TUNGSTEN;
			case "Yog Helmet" -> CorpseKind.UMBER;
			case "Vanguard Helmet" -> CorpseKind.VANGUARD;
			default -> null;
		};
	}

	private static boolean active() {
		return RaTAddonClient.CONFIG.corpseEsp && SkyblockLocation.inMineshaft();
	}

	public enum CorpseKind {
		LAPIS("Lapis", 0xFF5555FF),
		TUNGSTEN("Tungsten", 0xFFAAAAAA),
		UMBER("Umber", 0xFFFFAA00),
		VANGUARD("Vanguard", 0xFFFF55FF),
		UNKNOWN("Corpse", 0xFF55FFFF);

		public final String label;
		public final int color;
		CorpseKind(String label, int color) { this.label = label; this.color = color; }
	}
}
