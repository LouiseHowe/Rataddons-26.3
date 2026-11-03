package me.rattokens.rataddon.feature;

import me.rattokens.rataddon.RaTAddonClient;
import me.rattokens.rataddon.feature.HudAlert;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.ItemLore;

import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

final class AutoCroesus {
	private static final List<Integer> RUN_SLOTS = List.of(10, 11, 12, 13, 14, 15, 16, 19, 20, 21, 22, 23, 24, 25, 28, 29, 30, 31, 32, 33, 34, 37, 38, 39, 40, 41, 42, 43);
	private static final Set<Integer> PROCESSED = new HashSet<>();
	private static final Set<Integer> SKIPPED = new HashSet<>();
	private static long tick;
	private static long nextClick;
	private static Integer currentRun;
	private static Integer queuedChest;
	private static int skippedCount;
	private static boolean warnedNoKey;

	private AutoCroesus() {
	}

	static void init() {
		ClientTickEvents.END_CLIENT_TICK.register(AutoCroesus::tick);
	}

	private static void tick(Minecraft client) {
		tick++;
		if (!RaTAddonClient.CONFIG.autoCroesus || client.player == null || client.level == null || client.gameMode == null) {
			reset();
			return;
		}
		if (tick < nextClick || !(client.gui.screen() instanceof AbstractContainerScreen<?> screen)) return;
		String title = plain(screen.getTitle().getString()).toLowerCase(Locale.ROOT);
		if (title.contains("vesuvius") || title.contains("croesus")) handleMain(client, screen);
		else if (title.matches("^kuudra - (basic|hot|burning|fiery|infernal)$")) handleRun(client, screen, title.contains("infernal"));
		else if (title.matches("^(free|paid) chest( chest)?$")) handleChest(client, screen);
	}

	private static void handleMain(Minecraft client, AbstractContainerScreen<?> screen) {
		int page = page(screen);
		if (hasKey(client)) { SKIPPED.clear(); warnedNoKey = false; }
		currentRun = null;
		for (int slotId : RUN_SLOTS) {
			if (slotId >= screen.getMenu().slots.size()) continue;
			Slot slot = screen.getMenu().slots.get(slotId);
			if (!slot.hasItem()) continue;
			int global = slotId + (Math.max(1, page) - 1) * 54;
			if (PROCESSED.contains(global) || SKIPPED.contains(global)) continue;
			ItemStack stack = slot.getItem();
			if (!AutomationItems.nameContains(stack, "kuudra's hollow")) return;
			if (loreContains(stack, "Chests expire in")) {
				currentRun = global;
				click(client, screen, slot.index);
				return;
			}
		}
		if (screen.getMenu().slots.size() > 53) {
			Slot next = screen.getMenu().slots.get(53);
			if (next.hasItem() && AutomationItems.nameContains(next.getItem(), "next page")) click(client, screen, next.index);
		}
	}

	private static void handleRun(Minecraft client, AbstractContainerScreen<?> screen, boolean infernal) {
		if (queuedChest != null) {
			int slot = queuedChest;
			queuedChest = null;
			click(client, screen, slot);
			return;
		}
		Slot paid = null;
		Slot free = null;
		boolean tentacle = false;
		for (int i = 0; i < Math.min(27, screen.getMenu().slots.size()); i++) {
			Slot slot = screen.getMenu().slots.get(i);
			if (!slot.hasItem()) continue;
			if (AutomationItems.nameContains(slot.getItem(), "paid chest")) { paid = slot; tentacle = loreContains(slot.getItem(), "Kuudra Tentacle"); }
			else if (AutomationItems.nameContains(slot.getItem(), "free chest")) free = slot;
		}
		boolean key = hasKey(client);
		Slot first = paid != null && key && (infernal || tentacle) ? paid : null;
		Slot second = null;
		if (free != null && (!infernal || paid == null)) {
			if (first == null) first = free;
			else second = free;
		}
		if (first == null) {
			if (!key && paid != null) {
				skippedCount++;
				if (currentRun != null) SKIPPED.add(currentRun);
				if (!warnedNoKey) {
					warnedNoKey = true;
					HudAlert.show(Component.literal("§c§lNO KUUDRA KEY"),
						Component.literal("§eAuto Croesus paused paid chest"), 0, 30, 10);
					client.player.playSound(SoundEvents.NOTE_BLOCK_PLING.value(), 1.6F, .8F);
				}
				client.player.closeContainer();
				nextClick = tick + Math.max(1, RaTAddonClient.CONFIG.croesusClickDelay);
			} else if (key && currentRun != null) {
				PROCESSED.add(currentRun);
			}
			return;
		}
		if (currentRun != null) PROCESSED.add(currentRun);
		if (second != null) queuedChest = second.index;
		click(client, screen, first.index);
	}

	private static void handleChest(Minecraft client, AbstractContainerScreen<?> screen) {
		int fallback = -1;
		for (Slot slot : screen.getMenu().slots) {
			if (!slot.hasItem()) continue;
			if (slot.getContainerSlot() == 31) fallback = slot.index;
			if (AutomationItems.nameContains(slot.getItem(), "open reward chest")) {
				reset();
				click(client, screen, slot.index);
				return;
			}
		}
		if (fallback >= 0) click(client, screen, fallback);
	}

	private static int page(AbstractContainerScreen<?> screen) {
		if (screen.getMenu().slots.size() > 53) {
			ItemStack next = screen.getMenu().slots.get(53).getItem();
			if (!next.isEmpty() && AutomationItems.nameContains(next, "next page")) return Math.max(1, pageInLore(next) - 1);
		}
		if (screen.getMenu().slots.size() > 45) {
			ItemStack previous = screen.getMenu().slots.get(45).getItem();
			if (!previous.isEmpty() && AutomationItems.nameContains(previous, "previous page")) return pageInLore(previous) + 1;
		}
		return 1;
	}

	private static int pageInLore(ItemStack stack) {
		for (String line : lore(stack)) {
			if (!line.toLowerCase(Locale.ROOT).contains("page")) continue;
			String digits = line.replaceAll("[^0-9]", "");
			if (!digits.isEmpty()) try { return Integer.parseInt(digits); } catch (NumberFormatException ignored) { return 1; }
		}
		return 1;
	}

	private static boolean hasKey(Minecraft client) {
		return client.player.getInventory().getNonEquipmentItems().stream()
			.anyMatch(stack -> AutomationItems.nameContains(stack, "kuudra key"));
	}

	private static boolean loreContains(ItemStack stack, String text) {
		return lore(stack).stream().anyMatch(line -> line.contains(text));
	}

	private static List<String> lore(ItemStack stack) {
		ItemLore lore = stack.get(DataComponents.LORE);
		if (lore == null) return List.of();
		return lore.lines().stream().map(Component::getString).map(AutoCroesus::plain).toList();
	}

	private static String plain(String text) {
		String stripped = ChatFormatting.stripFormatting(text);
		return stripped == null ? "" : stripped;
	}

	private static void click(Minecraft client, AbstractContainerScreen<?> screen, int slot) {
		if (screen.getMenu().slots.stream().noneMatch(candidate -> candidate.index == slot)) return;
		client.gameMode.handleContainerInput(screen.getMenu().containerId, slot, 0, ContainerInput.PICKUP, client.player);
		nextClick = tick + Math.max(1, RaTAddonClient.CONFIG.croesusClickDelay);
	}

	private static void reset() {
		if (skippedCount > 0 && Minecraft.getInstance().player != null) {
			Minecraft.getInstance().player.sendSystemMessage(Component.literal("§eAuto Croesus skipped " + skippedCount + " paid chest(s): no Kuudra key."));
		}
		PROCESSED.clear(); SKIPPED.clear(); currentRun = null; queuedChest = null;
		nextClick = 0; skippedCount = 0; warnedNoKey = false;
	}
}
