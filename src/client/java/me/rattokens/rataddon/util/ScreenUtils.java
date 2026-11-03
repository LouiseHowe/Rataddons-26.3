package me.rattokens.rataddon.util;

import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.core.NonNullList;
import net.minecraft.network.HashedStack;
import net.minecraft.network.protocol.game.ServerboundContainerClickPacket;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;

public final class ScreenUtils {
	private ScreenUtils() {
	}

	public static void clickSlot(AbstractContainerMenu menu, int slotId) {
		Minecraft client = Minecraft.getInstance();
		ClientPacketListener connection = client.getConnection();
		if (connection == null) {
			return;
		}

		NonNullList<Slot> slots = menu.slots;
		List<ItemStack> before = new ArrayList<>(slots.size());
		for (Slot slot : slots) {
			before.add(slot.getItem().copy());
		}

		Int2ObjectOpenHashMap<HashedStack> changedSlots = new Int2ObjectOpenHashMap<>();
		for (int index = 0; index < slots.size(); index++) {
			ItemStack current = slots.get(index).getItem();
			if (!ItemStack.matches(before.get(index), current)) {
				changedSlots.put(index, HashedStack.create(
					current,
					connection.decoratedHashOpsGenenerator()
				));
			}
		}

		HashedStack carried = HashedStack.create(
			menu.getCarried(),
			connection.decoratedHashOpsGenenerator()
		);
		connection.send(new ServerboundContainerClickPacket(
			menu.containerId,
			menu.getStateId(),
			(short) slotId,
			(byte) 0,
			ContainerInput.PICKUP,
			changedSlots,
			carried
		));
	}
}
