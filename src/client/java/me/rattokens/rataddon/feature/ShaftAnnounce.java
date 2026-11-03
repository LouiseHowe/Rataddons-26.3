package me.rattokens.rataddon.feature;

import me.rattokens.rataddon.RaTAddonClient;
import me.rattokens.rataddon.util.SkyblockLocation;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.decoration.ArmorStand;

import java.util.ArrayDeque;
import java.util.Comparator;
import java.util.Deque;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

public final class ShaftAnnounce {
	public static final String DEFAULT_MESSAGE = "/pc Entered a {type} mineshaft ({id}). Corpses: {corpses}.";
	private static final int ANNOUNCE_DELAY_TICKS = 40;
	private static final Pattern SHAFT_ID = Pattern.compile("\\b([A-Z]{4}_[12C]|LITT_L)\\b");
	private static final Pattern CORPSE_TAB =
		Pattern.compile("(?i)\\b(Lapis|Tungsten|Umber|Vanguard): (?:NOT )?LOOTED\\b");
	private static final Map<String, String> SHAFT_TYPES = shaftTypes();
	private static final Map<String, CorpseLocation> DISCOVERED_CORPSES = new LinkedHashMap<>();
	private static final Deque<String> PARTY_QUEUE = new ArrayDeque<>();
	private static String cachedRegex = "";
	private static Pattern cachedPattern;
	private static String currentId = "";
	private static String currentType = "Unknown";
	private static int ticksInShaft;
	private static boolean detected;
	private static boolean configuredMessageSent;
	private static long nextPartyMessageAt;
	private static Object lastLevel;
	private static int scanTicker;

	private ShaftAnnounce() {
	}

	public static void init() {
		ClientTickEvents.END_CLIENT_TICK.register(client -> tick());
		ClientPlayConnectionEvents.JOIN.register((handler, sender, client) -> onWorldChanged());
		ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> onWorldChanged());
	}

	public static void handleChat(String message) {
		if (!RaTAddonClient.CONFIG.shaftAnnounce || message == null || message.isBlank()) {
			return;
		}
		Pattern pattern = triggerPattern();
		if (pattern != null && pattern.matcher(message.trim()).matches()) {
			ticksInShaft = 0;
			configuredMessageSent = false;
		}
	}

	private static void tick() {
		Minecraft client = Minecraft.getInstance();
		if (client.level != lastLevel) {
			lastLevel = client.level;
			onWorldChanged();
		}
		drainPartyQueue();
		if (!RaTAddonClient.CONFIG.shaftAnnounce || !SkyblockLocation.inMineshaft()) {
			return;
		}
		if (client.level != null && ++scanTicker % 5 == 0) {
			for (Entity entity : client.level.entitiesForRendering()) observeCorpse(entity);
		}
		ticksInShaft++;
		String id = shaftId();
		if (!id.isBlank() && !id.equals(currentId)) {
			DISCOVERED_CORPSES.clear();
			currentId = id;
			currentType = SHAFT_TYPES.getOrDefault(id, id);
			detected = true;
			configuredMessageSent = false;
			ticksInShaft = 0;
		}
		if (detected && !configuredMessageSent && ticksInShaft >= ANNOUNCE_DELAY_TICKS) {
			queueMessages(List.of(shaftPartyMessage()));
			configuredMessageSent = true;
		}
	}

	public static void shareShaft() {
		if (!SkyblockLocation.inMineshaft()) return;
		String id = shaftId();
		if (!id.isBlank()) {
			currentId = id;
			currentType = SHAFT_TYPES.getOrDefault(id, id);
		}
		queueMessages(List.of(shaftPartyMessage()));
	}

	public static void shareAllCorpses() {
		Minecraft client = Minecraft.getInstance();
		if (!SkyblockLocation.inMineshaft() || client.level == null || client.player == null) return;
		for (Entity entity : client.level.entitiesForRendering()) observeCorpse(entity);
		queueMessages(corpsePartyMessages());
	}

	private static void observeCorpse(Entity entity) {
		if (!(entity instanceof ArmorStand stand) || entity.isRemoved() || !SkyblockLocation.inMineshaft()) {
			return;
		}
		CorpseEsp.CorpseKind kind = CorpseEsp.fromHelmet(stand);
		if (kind == null) {
			return;
		}
		BlockPos pos = entity.blockPosition().above();
		DISCOVERED_CORPSES.put(kind.label + ":" + pos.asLong(), new CorpseLocation(pos, kind.label));
	}

	private static void onWorldChanged() {
		currentId = "";
		currentType = "Unknown";
		ticksInShaft = 0;
		detected = false;
		configuredMessageSent = false;
		scanTicker = 0;
		DISCOVERED_CORPSES.clear();
		PARTY_QUEUE.clear();
		nextPartyMessageAt = 0L;
	}

	private static String shaftPartyMessage() {
		String template = RaTAddonClient.CONFIG.shaftAnnounceMessage == null
			? "" : RaTAddonClient.CONFIG.shaftAnnounceMessage.trim();
		if (template.isEmpty() || template.equals("/pc Glacite Mineshaft!")) {
			template = DEFAULT_MESSAGE;
		}
		return template
			.replace("{message}", "Entered a " + currentType + " mineshaft")
			.replace("{area}", SkyblockLocation.area())
			.replace("{zone}", SkyblockLocation.zone())
			.replace("{id}", value(currentId, "Unknown ID"))
			.replace("{type}", currentType)
			.replace("{corpses}", corpseSummary());
	}

	private static List<String> corpsePartyMessages() {
		return DISCOVERED_CORPSES.values().stream()
			.sorted(Comparator.comparing(CorpseLocation::type)
				.thenComparingInt(corpse -> corpse.pos().getX())
				.thenComparingInt(corpse -> corpse.pos().getY())
				.thenComparingInt(corpse -> corpse.pos().getZ()))
			.map(corpse -> "/pc x: " + corpse.pos().getX() + ", y: " + corpse.pos().getY()
				+ ", z: " + corpse.pos().getZ() + " | (" + corpse.type() + " Corpse)")
			.toList();
	}

	private static void queueMessages(List<String> messages) {
		PARTY_QUEUE.clear();
		for (String message : messages) {
			if (message != null && !message.isBlank()) {
				PARTY_QUEUE.addLast(message);
			}
		}
		nextPartyMessageAt = 0L;
	}

	private static void drainPartyQueue() {
		if (PARTY_QUEUE.isEmpty()) {
			return;
		}
		long now = System.currentTimeMillis();
		if (now < nextPartyMessageAt) {
			return;
		}
		sendChatOrCommand(PARTY_QUEUE.removeFirst());
		nextPartyMessageAt = now + 1_000L;
	}

	private static String shaftId() {
		for (String line : SkyblockLocation.sidebarStrings()) {
			Matcher matcher = SHAFT_ID.matcher(line);
			if (matcher.find()) {
				return matcher.group(1);
			}
		}
		return "";
	}

	private static String corpseSummary() {
		Minecraft client = Minecraft.getInstance();
		if (client.getConnection() == null) {
			return "None";
		}
		Map<String, Integer> counts = new LinkedHashMap<>();
		for (PlayerInfo info : client.getConnection().getListedOnlinePlayers()) {
			Component display = info.getTabListDisplayName();
			String text = display == null ? info.getProfile().name()
				: ChatFormatting.stripFormatting(display.getString());
			if (text == null) {
				continue;
			}
			Matcher matcher = CORPSE_TAB.matcher(text);
			if (matcher.find()) {
				counts.merge(title(matcher.group(1)), 1, Integer::sum);
			}
		}
		if (counts.isEmpty()) {
			return "None";
		}
		StringBuilder summary = new StringBuilder();
		for (Map.Entry<String, Integer> entry : counts.entrySet()) {
			if (summary.length() > 0) {
				summary.append(", ");
			}
			summary.append(entry.getValue()).append("x ").append(entry.getKey());
		}
		return summary.toString();
	}

	private static void sendChatOrCommand(String message) {
		Minecraft client = Minecraft.getInstance();
		if (client.player == null || client.getConnection() == null || message == null || message.isBlank()) {
			return;
		}
		client.execute(() -> {
			if (message.startsWith("/")) {
				client.getConnection().sendCommand(message.substring(1));
			} else {
				client.getConnection().sendChat(message);
			}
		});
	}

	private static Pattern triggerPattern() {
		String regex = RaTAddonClient.CONFIG.shaftAnnounceTrigger == null
			? "" : RaTAddonClient.CONFIG.shaftAnnounceTrigger.trim();
		if (regex.isEmpty()) {
			return null;
		}
		if (regex.equals(cachedRegex) && cachedPattern != null) {
			return cachedPattern;
		}
		cachedRegex = regex;
		try {
			cachedPattern = Pattern.compile(regex);
		} catch (PatternSyntaxException exception) {
			cachedPattern = null;
		}
		return cachedPattern;
	}

	private static String title(String text) {
		if (text == null || text.isBlank()) {
			return "Unknown";
		}
		String lower = text.toLowerCase(Locale.ROOT);
		return Character.toUpperCase(lower.charAt(0)) + lower.substring(1);
	}

	private static String value(String text, String fallback) {
		return text == null || text.isBlank() ? fallback : text;
	}

	private static Map<String, String> shaftTypes() {
		Map<String, String> types = new LinkedHashMap<>();
		types.put("TOPA_1", "Topaz 1");
		types.put("TOPA_2", "Topaz 2");
		types.put("SAPP_1", "Sapphire 1");
		types.put("SAPP_2", "Sapphire 2");
		types.put("AMET_1", "Amethyst 1");
		types.put("AMET_2", "Amethyst 2");
		types.put("AMBE_1", "Amber 1");
		types.put("AMBE_2", "Amber 2");
		types.put("JADE_1", "Jade 1");
		types.put("JADE_2", "Jade 2");
		types.put("TITA_1", "Titanium");
		types.put("UMBE_1", "Umber");
		types.put("TUNG_1", "Tungsten");
		types.put("FAIR_1", "Vanguard");
		types.put("RUBY_1", "Ruby 1");
		types.put("RUBY_2", "Ruby 2");
		types.put("RUBY_C", "Ruby Crystal");
		types.put("ONYX_1", "Onyx 1");
		types.put("ONYX_2", "Onyx 2");
		types.put("ONYX_C", "Onyx Crystal");
		types.put("AQUA_1", "Aquamarine 1");
		types.put("AQUA_2", "Aquamarine 2");
		types.put("AQUA_C", "Aquamarine Crystal");
		types.put("CITR_1", "Citrine 1");
		types.put("CITR_2", "Citrine 2");
		types.put("CITR_C", "Citrine Crystal");
		types.put("PERI_1", "Peridot 1");
		types.put("PERI_2", "Peridot 2");
		types.put("PERI_C", "Peridot Crystal");
		types.put("JASP_1", "Jasper");
		types.put("JASP_C", "Jasper Crystal");
		types.put("OPAL_1", "Opal");
		types.put("OPAL_C", "Opal Crystal");
		types.put("LITT_L", "Littlefoot's Den");
		return Map.copyOf(types);
	}

	private record CorpseLocation(BlockPos pos, String type) {
	}
}
