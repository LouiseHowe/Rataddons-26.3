package me.rattokens.rataddon.feature;

import com.mojang.blaze3d.platform.InputConstants;
import me.rattokens.rataddon.RaTAddonClient;
import me.rattokens.rataddon.config.RaTAddonConfig;
import me.rattokens.rataddon.util.SkyblockItem;
import me.rattokens.rataddon.util.PhysicalInput;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;

import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.ThreadLocalRandom;

public final class AutoClicker {
	private static final ScheduledExecutorService TIMER = Executors.newSingleThreadScheduledExecutor(task -> {
		Thread thread = new Thread(task, "RatAddons Auto Clicker");
		thread.setDaemon(true);
		return thread;
	});
	private static KeyMapping leftKey;
	private static KeyMapping rightKey;
	private static boolean leftRunning;
	private static boolean rightRunning;
	private static boolean mining;
	private static int leftGeneration;
	private static int rightGeneration;
	private static int lastSlot = -1;
	private static Object lastLevel;

	private AutoClicker() {}

	public static void init() {
		KeyMapping.Category category = KeyMapping.Category.register(Identifier.fromNamespaceAndPath("rataddon", "autoclicker"));
		leftKey = KeyMappingHelper.registerKeyMapping(new KeyMapping("key.rataddon.autoclick.left", InputConstants.Type.MOUSE, InputConstants.MOUSE_BUTTON_LEFT, category));
		rightKey = KeyMappingHelper.registerKeyMapping(new KeyMapping("key.rataddon.autoclick.right", InputConstants.Type.MOUSE, InputConstants.MOUSE_BUTTON_RIGHT, category));
		ClientTickEvents.END_CLIENT_TICK.register(AutoClicker::tick);
	}

	private static RaTAddonConfig cfg() { return RaTAddonClient.CONFIG; }

	private static void tick(Minecraft client) {
		if (client.level != lastLevel) {
			reset(client);
			lastLevel = client.level;
		}
		if (client.player == null) { reset(client); return; }
		int slot = client.player.getInventory().getSelectedSlot();
		if (lastSlot >= 0 && slot != lastSlot && cfg().autoClickStopOnSwap) reset(client);
		lastSlot = slot;
		if (client.gui.screen() != null || !cfg().autoClicker) { reset(client); return; }
		setRunning(client, true, shouldClick(client, true));
		setRunning(client, false, shouldClick(client, false));
		boolean shouldMine = leftRunning && lookingAtBreakable(client);
		if (mining != shouldMine) {
			mining = shouldMine;
			client.options.keyAttack.setDown(shouldMine || physicalDown(client, client.options.keyAttack));
		}
	}

	private static boolean shouldClick(Minecraft client, boolean left) {
		if (!(left ? cfg().autoClickLeft : cfg().autoClickRight) || !physicalDown(client, left ? leftKey : rightKey)) return false;
		ItemStack item = client.player.getMainHandItem();
		if (cfg().autoClickBlockDungeonBreaker && SkyblockItem.id(item).equals("DUNGEONBREAKER")) return false;
		return !cfg().autoClickWhitelistOnly || (left ? cfg().autoClickLeftItems : cfg().autoClickRightItems).contains(heldItemKey(item));
	}

	public static boolean suppressPhysicalClick(int button) {
		Minecraft client = Minecraft.getInstance();
		if (!cfg().autoClicker || client.gui.screen() != null || client.player == null
			|| button != InputConstants.MOUSE_BUTTON_LEFT && button != InputConstants.MOUSE_BUTTON_RIGHT) return false;
		boolean left = button == InputConstants.MOUSE_BUTTON_LEFT;
		InputConstants.Key binding = KeyMappingHelper.getBoundKeyOf(left ? leftKey : rightKey);
		return binding.getType() == InputConstants.Type.MOUSE && binding.getValue() == button && shouldClick(client, left);
	}

	private static boolean physicalDown(Minecraft client, KeyMapping key) {
		InputConstants.Key bound = KeyMappingHelper.getBoundKeyOf(key);
		return PhysicalInput.isDown(bound);
	}

	private static boolean lookingAtBreakable(Minecraft client) {
		if (!cfg().autoClickBreakBlocks || client.hitResult == null || client.hitResult.getType() != HitResult.Type.BLOCK) return false;
		BlockHitResult hit = (BlockHitResult) client.hitResult;
		var state = client.level.getBlockState(hit.getBlockPos());
		return !state.isAir() && state.getFluidState().isEmpty();
	}

	private static void setRunning(Minecraft client, boolean left, boolean active) {
		boolean running = left ? leftRunning : rightRunning;
		if (running == active) return;
		if (left) { leftRunning = active; leftGeneration++; }
		else { rightRunning = active; rightGeneration++; }
		if (active) schedule(client, left, left ? leftGeneration : rightGeneration, 0);
	}

	private static void schedule(Minecraft client, boolean left, int generation, long delayMs) {
		TIMER.schedule(() -> client.execute(() -> {
			if ((left ? leftGeneration : rightGeneration) != generation) return;
			if (!cfg().autoClicker || client.player == null || client.gui.screen() != null || !shouldClick(client, left)) {
				setRunning(client, left, false);
				return;
			}
			if (!left || !lookingAtBreakable(client)) KeyMapping.click(KeyMappingHelper.getBoundKeyOf(left ? client.options.keyAttack : client.options.keyUse));
			int min = left ? cfg().autoClickLeftMinCps : cfg().autoClickRightMinCps;
			int max = left ? cfg().autoClickLeftMaxCps : cfg().autoClickRightMaxCps;
			long fastest = 1000L / Math.max(1, max);
			long slowest = 1000L / Math.max(1, min);
			schedule(client, left, generation, fastest >= slowest ? fastest : ThreadLocalRandom.current().nextLong(fastest, slowest + 1));
		}), delayMs, TimeUnit.MILLISECONDS);
	}

	private static void reset(Minecraft client) {
		if (leftRunning || rightRunning || mining) {
			leftRunning = rightRunning = mining = false;
			leftGeneration++;
			rightGeneration++;
			client.options.keyAttack.setDown(physicalDown(client, client.options.keyAttack));
		}
		lastSlot = -1;
	}

	public static String heldItemKey(ItemStack stack) {
		if (stack.isEmpty()) return null;
		CustomData data = stack.get(DataComponents.CUSTOM_DATA);
		if (data != null) {
			CompoundTag tag = data.copyTag();
			String uuid = tag.getString("uuid").orElse("");
			if (!uuid.isEmpty()) return uuid;
		}
		String id = SkyblockItem.id(stack);
		return !id.isEmpty() ? id : stack.getHoverName().getString();
	}

	public static int editList(boolean left, String operation) {
		Minecraft client = Minecraft.getInstance();
		if (client.player == null) return 0;
		List<String> list = left ? cfg().autoClickLeftItems : cfg().autoClickRightItems;
		String key = heldItemKey(client.player.getMainHandItem());
		if (!operation.equals("clear") && key == null) {
			client.player.sendSystemMessage(Component.literal("Hold an item first.")
				.withStyle(net.minecraft.ChatFormatting.RED));
			return 0;
		}
		boolean changed = switch (operation) {
			case "add" -> !list.contains(key) && list.add(key);
			case "remove" -> list.remove(key);
			case "clear" -> { boolean had = !list.isEmpty(); list.clear(); yield had; }
			default -> false;
		};
		if (changed) cfg().save();
		client.player.sendSystemMessage(Component.literal(changed ? "Updated " : "No change to ")
			.withStyle(changed ? net.minecraft.ChatFormatting.GREEN : net.minecraft.ChatFormatting.YELLOW)
			.append(Component.literal(left ? "left" : "right").withStyle(net.minecraft.ChatFormatting.AQUA))
			.append(Component.literal(" auto clicker whitelist.").withStyle(net.minecraft.ChatFormatting.GRAY)));
		return 1;
	}
}
