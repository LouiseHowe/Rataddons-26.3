package me.rattokens.rataddon.feature;

import me.rattokens.rataddon.RaTAddonClient;
import me.rattokens.rataddon.util.ModUtils;
import me.rattokens.rataddon.util.ScreenUtils;
import me.rattokens.rataddon.util.TickScheduler;
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.inventory.ChestMenu;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public final class AutoExperiment {
	private static final List<Integer> CHRONOMATRON = new ArrayList<>();
	private static final int[] ULTRASEQUENCER = new int[45];
	private static double cachedMasterVolume;
	private static long lastClickTime;
	private static int latestChronomatronSlot;
	private static boolean addChronomatronSlot;
	private static int chronomatronIndex;
	private static ItemStack ultrasequencerMarker = ItemStack.EMPTY;
	private static int ultrasequencerIndex;
	private static int ultrasequencerLength;
	private static boolean closing;

	private AutoExperiment() {
	}

	public static void init() {
		ScreenEvents.AFTER_INIT.register((client, screen, width, height) -> {
			if (!RaTAddonClient.CONFIG.autoExperiment || client.player == null
				|| !(client.player.containerMenu instanceof ChestMenu menu)) {
				return;
			}

			String title = screen.getTitle().getString();
			if (title.startsWith("Chronomatron (")) {
				CHRONOMATRON.clear();
				closing = false;
				mute(client);
				registerChronomatron(client, screen, menu);
			} else if (title.startsWith("Ultrasequencer (")) {
				ultrasequencerLength = 0;
				closing = false;
				mute(client);
				registerUltrasequencer(client, screen, menu);
			} else if (title.startsWith("Experiment Over")) {
				closing = false;
				closeScreen(client);
			}
		});
	}

	private static void registerChronomatron(Minecraft client, Screen screen, ChestMenu menu) {
		ScreenEvents.afterTick(screen).register(ignored -> {
			if (closing) {
				return;
			}
			Component name = menu.getSlot(49).getItem().getCustomName();
			if (name == null) {
				return;
			}
			findLatestChronomatronSlot(menu);
			String timer = name.getString();
			if (timer.contains("Remember the pattern")) {
				if (CHRONOMATRON.size() >= 9) {
					closeScreen(client);
					return;
				}
				chronomatronIndex = 0;
				addChronomatronSlot = true;
				lastClickTime = ModUtils.now();
			} else if (timer.contains("Timer:")) {
				if (addChronomatronSlot) {
					CHRONOMATRON.add(latestChronomatronSlot);
					addChronomatronSlot = false;
				}
				if (chronomatronIndex < CHRONOMATRON.size()
					&& ModUtils.now() - lastClickTime > ModUtils.randomBetween(300, 500)) {
					ScreenUtils.clickSlot(menu, CHRONOMATRON.get(chronomatronIndex++));
					lastClickTime = ModUtils.now();
				}
			}
		});
		restoreVolumeOnClose(client, screen);
	}

	private static void findLatestChronomatronSlot(ChestMenu menu) {
		for (int slot = 10; slot < 44; slot++) {
			String id = BuiltInRegistries.ITEM.getKey(menu.getSlot(slot).getItem().getItem()).getPath();
			if (id.contains("terracotta")) {
				latestChronomatronSlot = slot;
				return;
			}
		}
	}

	private static void registerUltrasequencer(Minecraft client, Screen screen, ChestMenu menu) {
		ScreenEvents.afterTick(screen).register(ignored -> {
			if (closing) {
				return;
			}
			ItemStack timerStack = menu.getSlot(49).getItem();
			Component name = timerStack.getCustomName();
			if (name == null) {
				return;
			}
			String timer = name.getString();
			if (timer.contains("Remember the pattern!")) {
				if (ultrasequencerLength >= 6) {
					closeScreen(client);
					return;
				}
				if (timerStack != ultrasequencerMarker) {
					Arrays.fill(ULTRASEQUENCER, 0);
					ultrasequencerIndex = 0;
					readUltrasequencer(menu);
					ultrasequencerMarker = timerStack;
					lastClickTime = ModUtils.now();
				}
			}
			if (timer.contains("Timer:") && ultrasequencerIndex < ultrasequencerLength
				&& ModUtils.now() - lastClickTime > ModUtils.randomBetween(300, 500)) {
				ScreenUtils.clickSlot(menu, ULTRASEQUENCER[ultrasequencerIndex++]);
				lastClickTime = ModUtils.now();
			}
		});
		restoreVolumeOnClose(client, screen);
	}

	private static void readUltrasequencer(ChestMenu menu) {
		ultrasequencerLength = 0;
		for (int slot = 9; slot < 45; slot++) {
			ItemStack stack = menu.getSlot(slot).getItem();
			String id = BuiltInRegistries.ITEM.getKey(stack.getItem()).getPath();
			int number = stack.getCount();
			if (!stack.isEmpty() && !id.contains("glass_pane") && number > 0 && number <= 45) {
				ULTRASEQUENCER[number - 1] = slot;
				ultrasequencerLength = Math.max(ultrasequencerLength, number);
			}
		}
	}

	private static void closeScreen(Minecraft client) {
		if (closing) {
			return;
		}
		closing = true;
		TickScheduler.schedule(0, () -> {
			if (client.player != null) {
				client.player.closeContainer();
			}
		});
	}

	private static void mute(Minecraft client) {
		cachedMasterVolume = client.options.getFinalSoundSourceVolume(SoundSource.MASTER);
		client.options.getSoundSourceOptionInstance(SoundSource.MASTER).set(0.0);
	}

	private static void restoreVolumeOnClose(Minecraft client, Screen screen) {
		ScreenEvents.remove(screen).register(ignored ->
			client.options.getSoundSourceOptionInstance(SoundSource.MASTER).set(cachedMasterVolume));
	}
}
