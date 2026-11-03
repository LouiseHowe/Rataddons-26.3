package me.rattokens.rataddon.util;

import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;

public final class SkyblockItem {
	private SkyblockItem() {
	}

	public static String id(ItemStack stack) {
		if (stack == null || stack.isEmpty()) {
			return "";
		}
		CustomData data = stack.get(DataComponents.CUSTOM_DATA);
		if (data == null) {
			return "";
		}
		CompoundTag tag = data.copyTag();
		String id = tag.getString("id").orElse("");
		if (!id.isBlank()) return id;
		id = tag.getCompoundOrEmpty("ExtraAttributes").getString("id").orElse("");
		if (!id.isBlank()) return id;
		return tag.getCompoundOrEmpty("tag").getCompoundOrEmpty("ExtraAttributes")
			.getString("id").orElse("");
	}
}
