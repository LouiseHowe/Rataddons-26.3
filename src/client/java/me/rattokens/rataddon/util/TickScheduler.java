package me.rattokens.rataddon.util;

import me.rattokens.rataddon.RaTAddonClient;
import me.rattokens.rataddon.mixin.KeyMappingAccessor;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/**
 * schedule(0) runs on the next tick, schedule(1) on the one after.
 * Server ticks are driven by Hypixel's per-tick ping packets.
 */
public final class TickScheduler {
	private static final List<Task> CLIENT = new ArrayList<>();
	private static final List<Task> SERVER = new ArrayList<>();

	private TickScheduler() {
	}

	public static void init() {
		ClientTickEvents.START_CLIENT_TICK.register(client -> tick(CLIENT));
	}

	public static void schedule(int ticks, Runnable callback) {
		schedule(ticks, false, callback);
	}

	public static void schedule(int ticks, boolean server, Runnable callback) {
		if (ticks < 0) {
			return;
		}
		(server ? SERVER : CLIENT).add(new Task(ticks, callback));
	}

	public static void schedule(double ticks, Runnable callback) {
		schedule((int) ticks, false, callback);
	}

	public static void schedule(double ticks, boolean server, Runnable callback) {
		schedule((int) ticks, server, callback);
	}

	public static void tickServer() {
		tick(SERVER);
	}

	private static void tick(List<Task> tasks) {
		List<Task> due = new ArrayList<>();
		Iterator<Task> iterator = tasks.iterator();
		while (iterator.hasNext()) {
			Task task = iterator.next();
			if (task.ticks-- <= 0) {
				due.add(task);
				iterator.remove();
			}
		}
		for (Task task : due) {
			Minecraft.getInstance().execute(() -> {
				try {
					task.callback.run();
				} catch (Throwable throwable) {
					RaTAddonClient.LOGGER.error("Scheduled task failed", throwable);
				}
			});
		}
	}

	public static void click(KeyMapping key) {
		KeyMappingAccessor accessor = (KeyMappingAccessor) key;
		accessor.rataddon$setClickCount(accessor.rataddon$getClickCount() + 1);
	}

	private static final class Task {
		private int ticks;
		private final Runnable callback;

		private Task(int ticks, Runnable callback) {
			this.ticks = ticks;
			this.callback = callback;
		}
	}
}
