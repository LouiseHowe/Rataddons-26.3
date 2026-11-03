package me.rattokens.rataddon.feature;

import me.rattokens.rataddon.RaTAddonClient;
import me.rattokens.rataddon.config.RaTAddonConfig;
import me.rattokens.rataddon.util.SkyblockItem;
import me.rattokens.rataddon.util.TickScheduler;
import me.rattokens.rataddon.util.WorldRender;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.protocol.game.ClientboundOpenScreenPacket;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.item.BowItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

public final class Storm {
	private static final Vec3 AIM_POS = new Vec3(100.0, 181.0, 64.0);
	private static final Vec3 STORM_POS = new Vec3(83.56969386901531, 184.0, 33.911591511095395);
	private static final int CYAN = 0xFF00FFFF;

	private static boolean maxor;
	private static boolean storm;
	private static boolean necron;
	private static boolean swapping;
	private static boolean deathBowReleased;
	private static int useTime;
	private static int stormTicks;
	private static boolean tintActive;
	private static ClientLevel lastLevel;

	private Storm() {
	}

	private static RaTAddonConfig cfg() {
		return RaTAddonClient.CONFIG;
	}

	private static boolean enabled() {
		return cfg().stormEnabled;
	}

	public static void init() {
		LevelRenderEvents.END_MAIN.register(context -> {
			Minecraft client = Minecraft.getInstance();
			if (client.player == null || !enabled()) {
				return;
			}
			if (storm) {
				WorldRender.box(context, box(AIM_POS.add(cfg().stormWaypointOffset, 0.0, 0.0)), CYAN, 2.0F, false);
				WorldRender.box(context, box(STORM_POS), CYAN, 2.0F, false);
			}
		});
		ClientTickEvents.END_CLIENT_TICK.register(Storm::tick);
	}

	private static AABB box(Vec3 center) {
		return AABB.ofSize(center, 0.2, 0.2, 0.2);
	}

	private static void tick(Minecraft client) {
		if (client.level != lastLevel) {
			lastLevel = client.level;
			maxor = false;
			storm = false;
			necron = false;
			swapping = false;
			deathBowReleased = false;
		}
	}

	public static void bowReleased(ItemStack item, LivingEntity entity) {
		Minecraft client = Minecraft.getInstance();
		if (entity != client.player || !enabled()) {
			return;
		}
		if (maxor) {
			if (!item.getHoverName().getString().contains("Death Bow")) {
				return;
			}
			deathBowReleased = true;
			if (!cfg().stormAutoSwapCritItem) {
				return;
			}
			TickScheduler.schedule(2, () -> maxor = false);
			TickScheduler.schedule(cfg().stormSwapDelay, () -> selectSlot(cfg().stormSwapSlot - 1));
		}

		if (storm && stormTicks > cfg().stormReleaseTime - 5 && inArea()) {
			if (!item.getHoverName().getString().contains("Last Breath") || !cfg().stormAutoSwapTerm) {
				return;
			}
			storm = false;
			for (int slot = 0; slot < 9; slot++) {
				if (client.player.getInventory().getItem(slot).getHoverName().getString().contains("Terminator")) {
					client.player.getInventory().setSelectedSlot(slot);
					break;
				}
			}
		}
	}

	public static void handleChat(String unformatted) {
		if (!enabled()) {
			return;
		}
		if (unformatted.contains("[BOSS] Maxor: WELL! WELL! WELL! LOOK WHO'S HERE!")) {
			maxor = true;
		} else if (unformatted.contains("[BOSS] Storm: Pathetic Maxor, just like expected.")) {
			swapping = false;
			deathBowReleased = false;
			maxor = false;
			storm = true;
			stormTicks = 0;
		} else if (unformatted.contains("[BOSS] Goldor: Who dares trespass into my domain")) {
			storm = false;
		} else if (unformatted.contains("[BOSS] Necron: You went further than any human before, congratulations.")) {
			necron = true;
		} else if (unformatted.contains("[BOSS] Necron: All this, for nothing...")) {
			necron = false;
		}
	}

	public static void handleScreen(ClientboundOpenScreenPacket packet) {
		Minecraft client = Minecraft.getInstance();
		if (!packet.getTitle().getString().contains("Loadout") || !swapping || client.player == null) {
			return;
		}
		swapping = false;
		int slot = cfg().stormLoadoutSlot;
		TickScheduler.schedule(cfg().stormClickDelay, true,
			() -> clickLoadout(slot, cfg().stormCloseLoadout, null));
	}

	static void clickLoadout(int loadoutSlot, Runnable afterClose) {
		clickLoadout(loadoutSlot, true, afterClose);
	}

	private static void clickLoadout(int loadoutSlot, boolean closeAfterClick, Runnable afterClose) {
		Minecraft client = Minecraft.getInstance();
		if (!(client.gui.screen() instanceof AbstractContainerScreen<?> screen) || client.player == null
			|| client.gameMode == null || !screen.getTitle().getString().contains("Loadout")) {
			return;
		}
		int index = loadoutIndex(loadoutSlot);
		if (index < 0) {
			return;
		}
		client.gameMode.handleContainerInput(screen.getMenu().containerId, index, 0, ContainerInput.PICKUP,
			client.player);
		if (!closeAfterClick) {
			return;
		}
		TickScheduler.schedule(0, () -> {
			if (client.player != null && client.player.containerMenu != null) {
				client.player.closeContainer();
				if (afterClose != null) {
					TickScheduler.schedule(0, afterClose);
				}
			}
		});
	}

	static int loadoutIndex(int num) {
		int k = 14 + (num - 1) / 3 * 9 + (num - 1) % 3;
		return k >= 14 && k <= 43 ? k : -1;
	}

	public static void useItem(Player player, InteractionHand hand, InteractionResult result) {
		if (!enabled()) {
			return;
		}
		Minecraft client = Minecraft.getInstance();
		String held = SkyblockItem.id(player.getMainHandItem());
		if ("STARRED_BONE_BOOMERANG".equals(held) && necron && cfg().stormAutoSwapCritItem && player.getY() > 20) {
			if (cfg().stormAutoSwapArmor && client.getConnection() != null) {
				client.getConnection().sendCommand("loadout");
				swapping = true;
			}
			TickScheduler.schedule(cfg().stormSwapDelay, () -> selectSlot(cfg().stormSwapSlot - 1));
		}
	}

	public static void serverTick() {
		Minecraft client = Minecraft.getInstance();
		LocalPlayer player = client.player;
		if (player == null || !enabled()) {
			return;
		}
		stormTicks++;

		if (player.getMainHandItem().getHoverName().getString().contains("Death Bow") && player.isUsingItem()) {
			useTime++;
		} else {
			if (useTime >= 20 && cfg().stormAutoSwapArmor && (maxor || storm || deathBowReleased)
				&& client.getConnection() != null) {
				client.getConnection().sendCommand("loadout");
				swapping = true;
			}
			deathBowReleased = false;
			useTime = 0;
		}

		if (storm && stormTicks >= cfg().stormReleaseTime * 20 && inArea()) {
			if (player.getMainHandItem().getHoverName().getString().contains("Last Breath")
				&& player.isUsingItem() && cfg().stormAutoReleaseLB) {
				client.options.keyUse.setDown(false);
			}
		}
	}

	private static void selectSlot(int slot) {
		LocalPlayer player = Minecraft.getInstance().player;
		if (player != null && player.getInventory().getSelectedSlot() != slot) {
			player.getInventory().setSelectedSlot(slot);
		}
	}

	private static boolean inArea() {
		LocalPlayer player = Minecraft.getInstance().player;
		if (player == null) {
			return false;
		}
		Vec3 pos = player.position();
		return pos.x >= 86.0 && pos.x <= 110.0 && pos.y >= 164.0 && pos.y <= 170.0
			&& pos.z >= 60.0 && pos.z <= 78.0;
	}

	public static void setTintActive(boolean active) {
		tintActive = active;
	}

	public static boolean tintBow() {
		return enabled() && cfg().stormBowTint && tintActive && BowItem.getPowerForTime(useTime) == 1.0F;
	}

	public static int tintArgb(int argb) {
		int a = (argb >>> 24) & 0xFF;
		int r = (argb >>> 16) & 0xFF;
		int g = Math.clamp((int) (((argb >>> 8) & 0xFF) * 0.3F), 0, 255);
		int b = Math.clamp((int) ((argb & 0xFF) * 0.3F), 0, 255);
		return (a << 24) | (r << 16) | (g << 8) | b;
	}
}
