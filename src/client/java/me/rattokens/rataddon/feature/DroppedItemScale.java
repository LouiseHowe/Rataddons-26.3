// SPDX-License-Identifier: LGPL-3.0-only
// Adapted from Baity's DroppedItemScale.
package me.rattokens.rataddon.feature;

import com.google.gson.JsonParser;
import me.rattokens.rataddon.RaTAddonClient;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.component.ItemLore;

public final class DroppedItemScale {
	private DroppedItemScale() {}

	public static float multiplier(ItemStack stack) {
		var config = RaTAddonClient.CONFIG;
		if (config == null || !config.droppedItemScale || !config.droppedItemRarityScale
			|| stack == null || stack.isEmpty()) return 1.0F;
		SkyblockItemRarity rarity = resolveRarity(stack);
		return rarity == SkyblockItemRarity.UNKNOWN ? 1.0F : (float) config.rarityScale(rarity);
	}

	static SkyblockItemRarity resolveRarity(ItemStack stack) {
		CustomData customData = stack.get(DataComponents.CUSTOM_DATA);
		if (customData != null) {
			var tag = customData.copyTag();
			if (tag != null && "PET".equals(tag.getString("id").orElse(""))) {
				String petInfo = tag.getString("petInfo").orElse("");
				if (petInfo.isEmpty()) return SkyblockItemRarity.UNKNOWN;
				try {
					var json = JsonParser.parseString(petInfo).getAsJsonObject();
					return json.has("tier") ? SkyblockItemRarity.fromTierName(json.get("tier").getAsString())
						: SkyblockItemRarity.UNKNOWN;
				} catch (RuntimeException ignored) { return SkyblockItemRarity.UNKNOWN; }
			}
		}
		ItemLore lore = stack.get(DataComponents.LORE);
		if (lore == null) return SkyblockItemRarity.UNKNOWN;
		var lines = lore.lines();
		for (int i = lines.size() - 1; i >= 0; i--) {
			SkyblockItemRarity rarity = SkyblockItemRarity.fromLoreLine(lines.get(i).getString());
			if (rarity != SkyblockItemRarity.UNKNOWN) return rarity;
		}
		return SkyblockItemRarity.UNKNOWN;
	}
}
