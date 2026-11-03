package me.rattokens.rataddon.feature;

import com.mojang.authlib.properties.Property;
import me.rattokens.rataddon.RaTAddonClient;
import me.rattokens.rataddon.config.RaTAddonConfig;
import me.rattokens.rataddon.util.SkyblockItem;
import me.rattokens.rataddon.util.ColorOptions;
import me.rattokens.rataddon.util.SkyblockLocation;
import me.rattokens.rataddon.util.WorldRender;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLevelEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.message.v1.ClientReceiveMessageEvents;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.hud.VanillaHudElements;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderContext;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.ResolvableProfile;
import net.minecraft.world.phys.Vec3;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** JMU-style confirmed Will-o-Wisp timer and 30-block deployable radius. */
public final class WillOWisp {
	public enum RadiusMode { OFF, YOU_ONLY, EVERYONE }
	public enum RenderMode { RING, HIGHLIGHT, SPHERE, SPHERE_2 }
	private static final Set<String> IDS = Set.of("WILL_O_WISP", "GLACITE_LANTERN", "TITANIUM_LANTERN", "MITHRIL_LANTERN", "LANTERN");
	private static final Pattern TEXTURE_HASH = Pattern.compile("https?://textures\\.minecraft\\.net/texture/([a-fA-F0-9]+)");
	private static final Pattern MANA = Pattern.compile("(\\d[\\d,]*)\\s*/\\s*(\\d[\\d,]*)");
	private static final Map<String, Lantern> TEXTURES = Map.of(
		"6ec1a6957f02a4258e64dc7c520463d92647888499845d784ef0e7abe8f38402", Lantern.WISP,
		"e75c66c2a8f92762da538bf349d207f813028cc1fa56527e20a494fc85b30734", Lantern.GLACITE,
		"df46eb02f6860f276f3b0157752a6adca6fc443c28e1e9e41ed2dcf304288210", Lantern.TITANIUM,
		"d2a6952d13acf631ce1bebaa0815fcf8a57f66fff69ba51599a936a1160b615b", Lantern.MITHRIL,
		"b7b06b329237e7469d0650ba167aa9611ef1373c3c2c6fbdda7b13773c6aa69f", Lantern.BASIC);
	private static final Map<UUID, Marker> MARKERS = new HashMap<>();
	private static Pending pending;
	private static double endGameTick;
	private static long endFallbackMs;
	private static long lastServerTick;
	private static long lastServerUpdateMs;
	private static double ticksPerSecondEma = 20.0;
	private static long placeReminderUntilMs;
	private static long lastAttemptMs;
	private static boolean wasUseDown;
	private static int scanTicks;
	private static int lastRadiusMode;
	private static int lastMana = -1;
	private static int lastMaxMana = -1;
	private static long lastManaAtMs;
	private static int lastPingSecond = -1;

	private WillOWisp() {}

	private enum Lantern {
		WISP(0xD01E4C66, 0x4A66D9FF), GLACITE(0xD0101C31, 0x4A2D3D59),
		TITANIUM(0xD03D4042, 0x4A6D7175), MITHRIL(0xD007342D, 0x4A44BAAD),
		BASIC(0xD0903C04, 0x4AE97E00);
		final int color;
		final int fillColor;
		Lantern(int color, int fillColor) { this.color = color; this.fillColor = fillColor; }
	}

	private record Marker(Vec3 position, Lantern type, long seenTick, boolean own) {}
	private record Pending(long startedMs, Vec3 position, ItemStack stack, float cooldownBefore,
		int manaBefore, int maxManaBefore, Set<Integer> standIdsBefore) {}

	public static void init() {
		ClientTickEvents.END_CLIENT_TICK.register(WillOWisp::tick);
		ClientReceiveMessageEvents.GAME.register((message, overlay) -> onChat(message.getString()));
		ClientLevelEvents.AFTER_CLIENT_LEVEL_CHANGE.register((client, level) -> reset());
		HudElementRegistry.attachElementBefore(VanillaHudElements.CHAT,
			Identifier.fromNamespaceAndPath(RaTAddonClient.MOD_ID, "wisp_timer"),
			(graphics, delta) -> renderHud(graphics));
		LevelRenderEvents.END_MAIN.register(WillOWisp::renderRadius);
	}

	/** Called by ClientLevel when a server time update arrives. */
	public static void onServerTimeUpdate(long gameTime) {
		long now = System.currentTimeMillis();
		if (lastServerUpdateMs > 0) {
			long delta = gameTime - lastServerTick;
			if (delta >= 0 && delta <= 500) {
				double sample = Math.clamp(delta * 1000.0 / Math.max(1L, now - lastServerUpdateMs), 0.0, 20.0);
				ticksPerSecondEma += (sample - ticksPerSecondEma) * 0.2;
			} else ticksPerSecondEma = 20.0;
		}
		lastServerTick = gameTime;
		lastServerUpdateMs = now;
	}

	private static double serverTicksNow(long now) {
		if (lastServerUpdateMs == 0) return 0;
		return lastServerTick + Math.min(40.0,
			Math.max(0L, now - lastServerUpdateMs) * ticksPerSecondEma / 1000.0);
	}

	private static boolean active() {
		return RaTAddonClient.CONFIG != null && RaTAddonClient.CONFIG.willOWisp && SkyblockLocation.inSkyblock();
	}

	private static void tick(Minecraft client) {
		if (client.level == null || client.player == null || !active()) {
			if (pending != null || endGameTick > 0 || !MARKERS.isEmpty()) reset();
			return;
		}
		if (lastRadiusMode != RaTAddonClient.CONFIG.wispRadiusMode) {
			MARKERS.clear();
			lastRadiusMode = RaTAddonClient.CONFIG.wispRadiusMode;
		}
		boolean useDown = client.options.keyUse.isDown();
		if (useDown && !wasUseDown) beginAttempt(client);
		wasUseDown = useDown;
		if (pending != null) checkPending(client);
		if (RaTAddonClient.CONFIG.wispRadiusMode == RadiusMode.EVERYONE.ordinal() && ++scanTicks % 20 == 0)
			scanStands(client);
		long tick = client.level.getGameTime();
		MARKERS.entrySet().removeIf(entry -> entry.getValue().own
			? tick > entry.getValue().seenTick + 6000L
			: tick > entry.getValue().seenTick + 60L);
	}

	private static void beginAttempt(Minecraft client) {
		ItemStack main = client.player.getMainHandItem();
		ItemStack off = client.player.getOffhandItem();
		ItemStack stack = isLantern(main) ? main : isLantern(off) ? off : ItemStack.EMPTY;
		long now = System.currentTimeMillis();
		if (stack.isEmpty() || now - lastAttemptMs < 125L) return;
		lastAttemptMs = now;
		int[] mana = manaFromSidebar();
		pending = new Pending(now, client.player.position(), stack.copy(), cooldown(client, stack),
			mana[0], mana[1], nearbyStandIds(client, client.player.position()));
	}

	private static void checkPending(Minecraft client) {
		Pending attempt = pending;
		long now = System.currentTimeMillis();
		if (now - attempt.startedMs > 5000L) { pending = null; return; }
		float currentCooldown = cooldown(client, attempt.stack);
		if (currentCooldown > 0 && (attempt.cooldownBefore <= 0 ||
			currentCooldown >= 0.5F && currentCooldown - attempt.cooldownBefore >= 0.01F)) {
			confirm(client); return;
		}
		int[] mana = manaFromSidebar();
		if (now - attempt.startedMs <= 3000L && mana[0] >= 0 && mana[1] > 0
			&& attempt.manaBefore >= 0 && attempt.maxManaBefore > 0) {
			double drop = attempt.manaBefore - mana[0];
			double expected = attempt.maxManaBefore * 0.5;
			if (drop >= expected * 0.8 && drop <= expected * 1.2) { confirm(client); return; }
		}
		for (Entity entity : client.level.entitiesForRendering()) {
			if (!(entity instanceof ArmorStand stand) || stand.position().distanceToSqr(attempt.position) > 36
				|| !isLanternStand(stand) || attempt.standIdsBefore.contains(stand.getId()) && stand.tickCount > 8) continue;
			confirm(client); return;
		}
	}

	private static void confirm(Minecraft client) {
		if (pending == null || client.level == null) return;
		Lantern type = lanternForItem(pending.stack);
		if (type != null && RaTAddonClient.CONFIG.wispRadiusMode == RadiusMode.YOU_ONLY.ordinal())
			MARKERS.put(UUID.nameUUIDFromBytes(("rataddon-wisp-own" + pending.startedMs).getBytes(StandardCharsets.UTF_8)),
				new Marker(pending.position, type, client.level.getGameTime(), true));
		long now = System.currentTimeMillis();
		double serverTick = serverTicksNow(now);
		endGameTick = serverTick > 0 ? serverTick + 6000.0 : 0;
		endFallbackMs = serverTick > 0 ? 0 : now + 300_000L;
		placeReminderUntilMs = 0;
		lastPingSecond = -1;
		pending = null;
	}

	private static void onChat(String message) {
		if (!active()) return;
		updateMana(message);
		String text = normalize(message);
		if (text.startsWith("sending to server ")) { reset(); return; }
		if ((text.contains("wisp") || text.contains("lantern")) && text.contains("expired")) {
			if (endGameTick > 0) placeReminderUntilMs = System.currentTimeMillis() + 5000L;
			endGameTick = endFallbackMs = 0;
			pending = null;
			return;
		}
		if (pending == null) return;
		boolean recent = System.currentTimeMillis() - pending.startedMs <= 6000L;
		if (!recent) return;
		if (text.contains("previous will o") && text.contains("removed") ||
			(containsLanternName(text) && (text.contains("placed") || text.contains("summoned")
				|| text.contains("spawned") || text.contains("activated")))) {
			confirm(Minecraft.getInstance());
		}
	}

	private static boolean isLantern(ItemStack stack) {
		if (stack == null || stack.isEmpty()) return false;
		String id = SkyblockItem.id(stack).toUpperCase(Locale.ROOT);
		if (!id.isBlank()) return IDS.contains(id);
		return containsLanternName(normalize(stack.getHoverName().getString()));
	}

	private static Lantern lanternForItem(ItemStack stack) {
		if (!isLantern(stack)) return null;
		String id = SkyblockItem.id(stack).toUpperCase(Locale.ROOT);
		if (id.contains("WISP")) return Lantern.WISP;
		if (id.contains("GLACITE")) return Lantern.GLACITE;
		if (id.contains("TITANIUM")) return Lantern.TITANIUM;
		if (id.contains("MITHRIL")) return Lantern.MITHRIL;
		return Lantern.BASIC;
	}

	private static boolean containsLanternName(String text) {
		String compact = text.replaceAll("[^a-z0-9]", "");
		return compact.contains("willowisp") || compact.contains("glacitelantern")
			|| compact.contains("titaniumlantern") || compact.contains("mithrillantern")
			|| compact.contains("dwarvenlantern");
	}

	private static String normalize(String text) {
		return net.minecraft.ChatFormatting.stripFormatting(text).toLowerCase(Locale.ROOT)
			.replace('’', '\'').replace('‘', '\'').replace('-', ' ').replace("'", "").trim();
	}

	private static float cooldown(Minecraft client, ItemStack stack) {
		if (client.player == null || stack == null || stack.isEmpty()) return 0;
		return client.player.getCooldowns().getCooldownPercent(stack, 0F);
	}

	private static int[] manaFromSidebar() {
		for (String line : SkyblockLocation.sidebarStrings()) {
			Matcher match = MANA.matcher(line);
			if (match.find() && (line.contains("✎") || line.toLowerCase(Locale.ROOT).contains("mana"))) {
				try {
					lastMana = Integer.parseInt(match.group(1).replace(",", ""));
					lastMaxMana = Integer.parseInt(match.group(2).replace(",", ""));
					lastManaAtMs = System.currentTimeMillis();
					return new int[] {lastMana, lastMaxMana};
				}
				catch (NumberFormatException ignored) { }
			}
		}
		return System.currentTimeMillis() - lastManaAtMs <= 3000L
			? new int[] {lastMana, lastMaxMana} : new int[] {-1, -1};
	}

	private static void updateMana(String message) {
		if (!message.contains("✎") && !message.toLowerCase(Locale.ROOT).contains("mana")) return;
		Matcher match = MANA.matcher(message);
		if (!match.find()) return;
		try {
			lastMana = Integer.parseInt(match.group(1).replace(",", ""));
			lastMaxMana = Integer.parseInt(match.group(2).replace(",", ""));
			lastManaAtMs = System.currentTimeMillis();
		} catch (NumberFormatException ignored) { }
	}

	private static Set<Integer> nearbyStandIds(Minecraft client, Vec3 center) {
		Set<Integer> ids = new HashSet<>();
		if (client.level == null) return ids;
		for (Entity entity : client.level.entitiesForRendering())
			if (entity instanceof ArmorStand stand && stand.position().distanceToSqr(center) <= 36 && isLanternStand(stand))
				ids.add(stand.getId());
		return ids;
	}

	private static boolean isLanternStand(ArmorStand stand) {
		ItemStack head = stand.getItemBySlot(EquipmentSlot.HEAD);
		if (isLantern(head)) return true;
		return head.isEmpty() && stand.getCustomName() != null
			&& containsLanternName(normalize(stand.getCustomName().getString()));
	}

	private static void scanStands(Minecraft client) {
		int radius = Math.clamp(RaTAddonClient.CONFIG.wispMaxMarkers, 32, 512);
		for (Entity entity : client.level.entitiesForRendering()) {
			if (!(entity instanceof ArmorStand stand) || entity.distanceToSqr(client.player) > radius * radius) continue;
			Lantern type = lanternFromTexture(stand.getItemBySlot(EquipmentSlot.HEAD));
			if (type == null) continue;
			if (MARKERS.size() >= 512 && !MARKERS.containsKey(stand.getUUID())) continue;
			MARKERS.put(stand.getUUID(), new Marker(stand.position(), type, client.level.getGameTime(), false));
		}
	}

	private static Lantern lanternFromTexture(ItemStack stack) {
		if (stack == null || stack.isEmpty()) return null;
		ResolvableProfile profile = stack.get(DataComponents.PROFILE);
		if (profile == null) return null;
		for (Property property : profile.partialProfile().properties().get("textures")) {
			try {
				String json = new String(Base64.getDecoder().decode(property.value()), StandardCharsets.UTF_8);
				Matcher hash = TEXTURE_HASH.matcher(json);
				if (hash.find()) return TEXTURES.get(hash.group(1).toLowerCase(Locale.ROOT));
			} catch (IllegalArgumentException ignored) { }
		}
		return null;
	}

	private static void renderRadius(LevelRenderContext context) {
		if (!active() || RaTAddonClient.CONFIG.wispRadiusMode == RadiusMode.OFF.ordinal()) return;
		RenderMode mode = RenderMode.values()[RaTAddonClient.CONFIG.wispRenderMode];
		for (Marker marker : MARKERS.values()) {
			if (SkyblockLocation.inMineshaft() && marker.type == Lantern.WISP) continue;
			switch (mode) {
				case RING -> WorldRender.ring(context, marker.position.add(0, 0.05, 0), 30.0,
					0.12, 96, marker.type.color, true);
				case HIGHLIGHT -> {
					WorldRender.ring(context, marker.position.add(0, 0.05, 0), 30.0,
						0.12, 96, marker.type.color, true);
					WorldRender.disc(context, marker.position.add(0, 0.08, 0), 30.0,
						96, marker.type.fillColor, true);
				}
				case SPHERE, SPHERE_2 -> WorldRender.sphere(context, marker.position,
					30.0, marker.type.fillColor, true);
			}
		}
	}

	private static void renderHud(GuiGraphicsExtractor graphics) {
		Minecraft client = Minecraft.getInstance();
		RaTAddonConfig cfg = RaTAddonClient.CONFIG;
		if (client.player == null || client.level == null || cfg == null || !active()
			|| client.gui.screen() instanceof HudEditorScreen) return;
		long now = System.currentTimeMillis();
		double remaining = endGameTick > 0 ? (endGameTick - serverTicksNow(now)) / 20.0
			: (endFallbackMs - now) / 1000.0;
		if ((endGameTick > 0 || endFallbackMs > 0) && remaining <= 0) {
			endGameTick = endFallbackMs = 0;
			placeReminderUntilMs = now + cfg.wispPopupStayTicks * 50L;
		}
		if (remaining > 0 && cfg.wispHud) {
			KittyHud.draw(graphics, client.font, KittyHud.Slot.WISP_TIMER,
				Component.literal("§fWisp: §b" + (int) Math.ceil(remaining) + "s"),
				client.getWindow().getGuiScaledWidth(), client.getWindow().getGuiScaledHeight(), 0xFFFFFFFF);
		}
		if (cfg.wispPopup) {
			if (remaining > 0 && remaining <= 5) {
				int bucket = Math.clamp((int) Math.ceil(remaining), 1, 5);
				if (cfg.wispPopupSound && bucket != lastPingSecond) {
					lastPingSecond = bucket;
					client.player.playSound(SoundEvents.NOTE_BLOCK_PLING.value(), 1.0F, 1.4F);
				}
				popup(graphics, cfg.wispPopupCountdownText.replace("{seconds}",
					String.format(Locale.US, "%.1f", remaining)), ColorOptions.argb(cfg.wispPopupCountdownColor));
			}
			else if (now < placeReminderUntilMs) popup(graphics, cfg.wispPopupFinalText,
				ColorOptions.argb(cfg.wispPopupFinalColor));
			else lastPingSecond = -1;
		}
		if (cfg.wispRadiusMode != RadiusMode.OFF.ordinal()
			&& cfg.wispRenderMode == RenderMode.SPHERE_2.ordinal()) {
			Vec3 position = client.player.getEyePosition();
			for (Marker marker : MARKERS.values()) {
				if (SkyblockLocation.inMineshaft() && marker.type == Lantern.WISP) continue;
				if (marker.position.distanceToSqr(position) > 900.0) continue;
				graphics.fill(0, 0, client.getWindow().getGuiScaledWidth(),
					client.getWindow().getGuiScaledHeight(), 858304511);
				break;
			}
		}
	}

	private static void popup(GuiGraphicsExtractor graphics, String text, int color) {
		Minecraft client = Minecraft.getInstance();
		KittyHud.Slot slot = KittyHud.Slot.WISP_POPUP;
		float centerX = KittyHud.x(client.font, client.getWindow().getGuiScaledWidth(), slot)
			+ KittyHud.width(client.font, slot) / 2F;
		graphics.pose().pushMatrix();
		graphics.pose().translate(centerX, KittyHud.y(client.getWindow().getGuiScaledHeight(), slot));
		graphics.pose().scale(slot.setting().scale, slot.setting().scale);
		graphics.text(client.font, Component.literal(text), -client.font.width(text) / 2, 0, color, true);
		graphics.pose().popMatrix();
	}

	private static void reset() {
		pending = null;
		endGameTick = endFallbackMs = placeReminderUntilMs = lastAttemptMs = 0;
		lastServerTick = lastServerUpdateMs = 0;
		ticksPerSecondEma = 20.0;
		lastMana = lastMaxMana = -1;
		lastManaAtMs = 0;
		lastPingSecond = -1;
		wasUseDown = false;
		scanTicks = 0;
		lastRadiusMode = 0;
		MARKERS.clear();
	}
}
