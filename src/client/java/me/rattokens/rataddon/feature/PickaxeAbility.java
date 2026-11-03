package me.rattokens.rataddon.feature;

import me.rattokens.rataddon.RaTAddonClient;
import me.rattokens.rataddon.config.RaTAddonConfig;
import me.rattokens.rataddon.util.LocationManager;
import me.rattokens.rataddon.util.ColorOptions;
import me.rattokens.rataddon.util.SkyblockLocation;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLevelEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.message.v1.ClientReceiveMessageEvents;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.hud.VanillaHudElements;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.component.ItemLore;

import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Pickaxe ability clock and modifiers from JMU 0.5.0, adapted to Minecraft 26.2. */
public final class PickaxeAbility {
	private static final Pattern USED = Pattern.compile("^You used your (.+) Pickaxe Ability!$");
	private static final Pattern MAYHEM_ABILITY = Pattern.compile(
		"(?:MAYHEM!?|Mayhem:?)(?:.*?)(Pickaxe Ability)", Pattern.CASE_INSENSITIVE);
	private static final double COOLDOWN_MULTIPLIER = 0.9;
	private static final String[] NAMES = {"Pickobulus", "Tunnel Vision", "Maniac Miner",
		"Gemstone Infusion", "Sheer Force", "Mining Speed Boost"};
	private static final int[][] COOLDOWNS = {{60, 50, 40}, {120, 110, 100}, {120, 120, 120},
		{120, 120, 120}, {120, 120, 120}, {120, 120, 120}};
	private static final int[][] DURATIONS = {{0, 0, 0}, {30, 30, 30}, {25, 30, 35},
		{20, 25, 30}, {20, 25, 30}, {10, 15, 20}};
	private static long castMs;
	private static long cooldownMs;
	private static long durationMs;
	private static long readyPopupUntilMs;
	private static long readyShownForCast;
	private static long resetReadyUntilMs;
	private static String abilityName = "";
	private static ItemStack clickedStack = ItemStack.EMPTY;
	private static long clickedAtMs;
	private static boolean wasUseDown;
	private static boolean wasInShaft;
	private static int lastPingSecond = -1;
	private static double skymallMultiplier = 1.0;
	private static long skymallUntilMs;
	private static int castBaseCooldownSec;
	private static double castFuelReduction;
	private static double castSkymallMultiplier;
	private static boolean castMayhemBonus;

	private PickaxeAbility() {}

	public static void init() {
		ClientTickEvents.END_CLIENT_TICK.register(PickaxeAbility::tick);
		ClientReceiveMessageEvents.GAME.register((message, overlay) -> onChat(message.getString()));
		ClientLevelEvents.AFTER_CLIENT_LEVEL_CHANGE.register((client, level) -> reset());
		HudElementRegistry.attachElementBefore(VanillaHudElements.CHAT,
			Identifier.fromNamespaceAndPath(RaTAddonClient.MOD_ID, "pickaxe_ability"),
			(graphics, delta) -> render(graphics));
	}

	private static boolean miningIsland() {
		return switch (LocationManager.currentArea()) {
			case GOLD_MINE, DEEP_CAVERNS, DWARVEN_MINES, CRYSTAL_HOLLOWS, MINESHAFT -> true;
			default -> false;
		};
	}

	private static void tick(Minecraft client) {
		if (client.player == null) { wasUseDown = false; return; }
		boolean useDown = client.options.keyUse.isDown();
		if (useDown && !wasUseDown) {
			clickedStack = client.player.getMainHandItem().copy();
			clickedAtMs = System.currentTimeMillis();
		}
		wasUseDown = useDown;
		boolean inShaft = SkyblockLocation.inMineshaft();
		if (inShaft && !wasInShaft && castMs > 0 && System.currentTimeMillis() < castMs + cooldownMs) {
			cooldownMs = Math.max(0L, System.currentTimeMillis() - castMs);
			readyShownForCast = castMs;
			readyPopupUntilMs = 0;
			resetReadyUntilMs = System.currentTimeMillis() + 3000L;
		}
		if (!inShaft && wasInShaft && castMayhemBonus && castMs > 0) {
			long normalCooldown = Math.round(castBaseCooldownSec * Math.clamp(
				1.0 - castFuelReduction - (1.0 - castSkymallMultiplier), 0.0, 1.0)
				* COOLDOWN_MULTIPLIER * 1000.0);
			cooldownMs = Math.max(cooldownMs, normalCooldown);
			castMayhemBonus = false;
		}
		wasInShaft = inShaft;
	}

	private static void onChat(String message) {
		if (RaTAddonClient.CONFIG == null) return;
		String clean = net.minecraft.ChatFormatting.stripFormatting(message).trim();
		String lower = clean.toLowerCase(Locale.ROOT);
		if (lower.contains("skymall") || lower.contains("sky mall") || lower.contains("new buff")) {
			if (lower.contains("ability") && lower.contains("cooldown")) {
				skymallMultiplier = 0.8;
				skymallUntilMs = System.currentTimeMillis() + 20 * 60_000L;
			} else if (lower.contains("new buff") || lower.contains("buff changed")) {
				skymallMultiplier = 1.0;
				skymallUntilMs = 0;
			}
		}
		Matcher match = USED.matcher(clean);
		if (!match.matches()) return;
		int index = -1;
		for (int i = 0; i < NAMES.length; i++) {
			if (NAMES[i].equalsIgnoreCase(match.group(1).trim())) { index = i; break; }
		}
		if (index < 0) return;
		if (Minecraft.getInstance().player == null) return;
		long now = System.currentTimeMillis();
		ItemStack drill = now - clickedAtMs <= 2000L ? clickedStack : Minecraft.getInstance().player.getMainHandItem();
		String search = itemSearchText(drill);
		int level = search.contains("GOBLIN_OMELETTE_BLUE_CHEESE") ? 3 : 2;
		double fuel = search.contains("PERFECTLY_CUT_FUEL_TANK") ? 0.10
			: search.contains("GEMSTONE_FUEL_TANK") ? 0.06
			: search.contains("TITANIUM_FUEL_TANK") ? 0.04
			: search.contains("MITHRIL_FUEL_TANK") ? 0.02 : 0;
		double mall = now < skymallUntilMs ? skymallMultiplier : 1.0;
		boolean mayhem = SkyblockLocation.inMineshaft() && mayhemAbilityActive();
		double multiplier = Math.clamp(1.0 - fuel - (1.0 - mall), 0.0, 1.0)
			* COOLDOWN_MULTIPLIER * (mayhem ? 0.75 : 1.0);
		abilityName = NAMES[index];
		castMs = now - clickedAtMs <= 2000L ? clickedAtMs : now;
		cooldownMs = Math.round(COOLDOWNS[index][level - 1] * multiplier * 1000.0);
		durationMs = DURATIONS[index][level - 1] * 1000L;
		castBaseCooldownSec = COOLDOWNS[index][level - 1];
		castFuelReduction = fuel;
		castSkymallMultiplier = mall;
		castMayhemBonus = mayhem;
		readyShownForCast = 0;
		readyPopupUntilMs = 0;
		resetReadyUntilMs = 0;
		lastPingSecond = -1;
	}

	private static boolean mayhemAbilityActive() {
		Minecraft client = Minecraft.getInstance();
		if (client.getConnection() == null) return false;
		for (var info : client.getConnection().getListedOnlinePlayers()) {
			Component display = info.getTabListDisplayName();
			if (display == null) continue;
			String line = net.minecraft.ChatFormatting.stripFormatting(display.getString());
			if (MAYHEM_ABILITY.matcher(line).find()) return true;
		}
		return false;
	}

	private static String itemSearchText(ItemStack stack) {
		if (stack == null || stack.isEmpty()) return "";
		StringBuilder text = new StringBuilder(stack.getHoverName().getString());
		CustomData data = stack.get(DataComponents.CUSTOM_DATA);
		if (data != null) text.append(' ').append(data.copyTag());
		ItemLore lore = stack.get(DataComponents.LORE);
		if (lore != null) for (Component line : lore.lines()) text.append(' ').append(line.getString());
		return text.toString().toUpperCase(Locale.ROOT);
	}

	private static void render(GuiGraphicsExtractor graphics) {
		Minecraft client = Minecraft.getInstance();
		RaTAddonConfig cfg = RaTAddonClient.CONFIG;
		if (client.player == null || cfg == null || !cfg.pickaxeAbility || !miningIsland()
			|| castMs == 0 || client.gui.screen() instanceof HudEditorScreen) return;
		long now = System.currentTimeMillis();
		long left = cooldownMs - (now - castMs);
		long duration = durationMs - (now - castMs);
		boolean readyHud = cfg.pickaxeAbilityNoPopup && left <= 0 && now - castMs <= cooldownMs + 30_000L
			|| now <= resetReadyUntilMs;
		if (left > 0 || readyHud) {
			if (cfg.pickaxeAbilityNoPopup || left > 5000L || now <= resetReadyUntilMs) {
				String line = "§9§l" + abilityName + ": §r" + (left > 0 ? "§b" + seconds(left) : "§aReady");
				KittyHud.draw(graphics, client.font, KittyHud.Slot.PICKAXE_ABILITY,
					Component.literal(line), client.getWindow().getGuiScaledWidth(),
					client.getWindow().getGuiScaledHeight(), 0xFFFFFFFF);
			}
		}
		if (cfg.pickaxeAbilityDuration && duration > 0) {
			KittyHud.draw(graphics, client.font, KittyHud.Slot.PICKAXE_DURATION,
				Component.literal("§6§lDUR§r§f: §e" + String.format(Locale.US, "%.2f", Math.floor(duration / 10.0) / 100.0)),
				client.getWindow().getGuiScaledWidth(), client.getWindow().getGuiScaledHeight(), 0xFFFFFFFF);
		}
		if (!cfg.pickaxeAbilityNoPopup && now > resetReadyUntilMs) {
			if (left > 0 && left <= 5000L) {
				int bucket = Math.clamp((int) Math.ceil(left / 1000.0), 1, 5);
				if (cfg.pickaxePopupSound && bucket != lastPingSecond) {
					lastPingSecond = bucket;
					client.player.playSound(SoundEvents.NOTE_BLOCK_PLING.value(), 1.0F, 1.2F);
				}
				String seconds = String.format(Locale.US, "%.1f", Math.floor(left / 100.0) / 10.0);
				popup(graphics, cfg.pickaxePopupCountdownText.replace("{seconds}", seconds),
					ColorOptions.argb(cfg.pickaxePopupCountdownColor));
			}
			else if (left <= 0) {
				if (readyShownForCast != castMs) {
					readyShownForCast = castMs;
					readyPopupUntilMs = now + cfg.pickaxePopupStayTicks * 50L;
					if (cfg.pickaxePopupSound)
						client.player.playSound(SoundEvents.NOTE_BLOCK_PLING.value(), 1.0F, 1.4F);
				}
				if (now < readyPopupUntilMs) popup(graphics, cfg.pickaxePopupReadyText,
					ColorOptions.argb(cfg.pickaxePopupReadyColor));
			}
		} else lastPingSecond = -1;
	}

	private static void popup(GuiGraphicsExtractor graphics, String text, int color) {
		Minecraft client = Minecraft.getInstance();
		KittyHud.Slot slot = KittyHud.Slot.PICKAXE_POPUP;
		float scale = slot.setting().scale;
		float centerX = KittyHud.x(client.font, client.getWindow().getGuiScaledWidth(), slot)
			+ KittyHud.width(client.font, slot) / 2F;
		graphics.pose().pushMatrix();
		graphics.pose().translate(centerX, KittyHud.y(client.getWindow().getGuiScaledHeight(), slot));
		graphics.pose().scale(scale, scale);
		graphics.text(client.font, Component.literal(text), -client.font.width(text) / 2, 0, color, true);
		graphics.pose().popMatrix();
	}

	private static String seconds(long ms) {
		if (ms >= 10_000L) return (ms / 1000L) + "s";
		return String.format(Locale.US, "%.2fs", Math.floor(ms / 10.0) / 100.0);
	}

	private static void reset() {
		castMs = cooldownMs = durationMs = readyPopupUntilMs = readyShownForCast = resetReadyUntilMs = 0;
		clickedStack = ItemStack.EMPTY;
		clickedAtMs = 0;
		abilityName = "";
		wasUseDown = wasInShaft = false;
		skymallMultiplier = 1.0;
		skymallUntilMs = 0;
		castBaseCooldownSec = 0;
		castFuelReduction = 0;
		castSkymallMultiplier = 1.0;
		castMayhemBonus = false;
		lastPingSecond = -1;
	}
}
