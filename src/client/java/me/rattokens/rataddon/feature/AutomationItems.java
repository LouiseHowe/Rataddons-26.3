package me.rattokens.rataddon.feature;

import me.rattokens.rataddon.util.SkyblockItem;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.FishingRodItem;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.component.ItemLore;

import java.util.Locale;
import java.util.function.Predicate;

final class AutomationItems {
	private AutomationItems() {
	}

	static String name(ItemStack stack) {
		if (stack == null || stack.isEmpty()) return "";
		String value = ChatFormatting.stripFormatting(stack.getHoverName().getString());
		return value == null ? "" : value.toLowerCase(Locale.ROOT);
	}

	static boolean nameContains(ItemStack stack, String text) {
		return name(stack).contains(text.toLowerCase(Locale.ROOT));
	}

	static boolean idContains(ItemStack stack, String text) {
		return SkyblockItem.id(stack).toUpperCase(Locale.ROOT).contains(text.toUpperCase(Locale.ROOT));
	}

	static int hotbar(Predicate<ItemStack> predicate) {
		LocalPlayer player = Minecraft.getInstance().player;
		if (player == null) return -1;
		for (int slot = 0; slot < 9; slot++) {
			if (predicate.test(player.getInventory().getItem(slot))) return slot;
		}
		return -1;
	}

	static boolean isFishingRod(ItemStack stack) {
		if (stack == null || stack.isEmpty()) return false;
		if (stack.getItem() instanceof FishingRodItem || nameContains(stack, "fishing rod")) return true;
		String id = SkyblockItem.id(stack);
		if (id.endsWith("_ROD") || id.contains("_ROD_")) return true;
		ItemLore lore = stack.get(DataComponents.LORE);
		return lore != null && lore.lines().stream().map(line -> line.getString().toLowerCase(Locale.ROOT))
			.anyMatch(line -> line.contains("fishing rod") || line.contains("lava rod"));
	}

	static boolean isRendTerminator(ItemStack stack) {
		if (!"TERMINATOR".equals(SkyblockItem.id(stack))) return false;
		CustomData data = stack.get(DataComponents.CUSTOM_DATA);
		if (data == null) return false;
		CompoundTag tag = data.copyTag();
		return tag.getCompound("enchantments").map(enchantments -> enchantments.contains("ultimate_rend")).orElse(false);
	}
}
