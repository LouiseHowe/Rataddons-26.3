package me.rattokens.rataddon.feature;

import me.rattokens.rataddon.RaTAddonClient;
import me.rattokens.rataddon.config.RaTAddonConfig;
import me.rattokens.rataddon.util.HumanRotation;
import me.rattokens.rataddon.util.KuudraState;
import me.rattokens.rataddon.util.SkyblockItem;
import me.rattokens.rataddon.util.TickScheduler;
import me.rattokens.rataddon.util.WorldRender;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderEvents;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundOpenScreenPacket;
import net.minecraft.network.protocol.game.ClientboundPlayerPositionPacket;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.concurrent.ThreadLocalRandom;

public final class RendMacro {
	private static boolean clickLoadout;
	private static boolean edging;
	private static boolean throwRod;
	private static ClientLevel lastLevel;

	private RendMacro() {
	}

	private static RaTAddonConfig cfg() {
		return RaTAddonClient.CONFIG;
	}

	private static boolean enabled() {
		return cfg().rendMacro;
	}

	public static void init() {
		LevelRenderEvents.END_MAIN.register(context -> {
			Minecraft client = Minecraft.getInstance();
			if (client.level == null || client.player == null || !enabled()) {
				return;
			}
			checkRodAndEdge();
			if (!cfg().rendRenderArea) {
				return;
			}
			AABB area = new AABB(
				-111.0 - cfg().rendOffsetRight, 0.0, -118.0 - cfg().rendOffsetBack,
				-95.0 + cfg().rendOffsetLeft, 6.0, -97.0 + cfg().rendOffsetFront
			);
			WorldRender.box(context, area, 0xFFFF0000, 2.0F, true);
		});
		ClientTickEvents.END_CLIENT_TICK.register(client -> {
			if (client.level != lastLevel) {
				lastLevel = client.level;
				clickLoadout = false;
				edging = false;
				throwRod = false;
			}
		});
	}

	public static void onPositionChange(ClientboundPlayerPositionPacket packet) {
		if (!enabled() || !KuudraState.kuudra()) {
			return;
		}
		if (!KuudraState.stun() && !KuudraState.dps()) {
			return;
		}
		if (KuudraState.isDead) {
			return;
		}
		Vec3 pos = packet.change().position();
		if (pos.x < -102.0 || pos.x > -101.0 || pos.y < 5.0 || pos.y > 7.0 || pos.z < -106.0 || pos.z > -105.0) {
			return;
		}
		Minecraft client = Minecraft.getInstance();

		getRotationGoal();

		if (cfg().rendAutoSneak) {
			debug("Sneaking");
			client.options.keyShift.setDown(true);
			TickScheduler.schedule(2, () -> client.options.keyShift.setDown(false));
		}
		if (cfg().rendAutoJump) {
			debug("Jumping");
			edging = true;
		}
		if (cfg().rendAutoHollowWand) {
			debug("Auto hollow start");
			if ("HOLLOW_WAND".equals(SkyblockItem.id(client.player.getMainHandItem()))) {
				debug("Hollow in hand -> clicking");
				TickScheduler.schedule(cfg().rendFirstClickDelay, RendMacro::castHollow);
			} else {
				debug("Searching hollow");
				Integer slot = hotbarSlotFromId("HOLLOW_WAND");
				if (slot == null) {
					debug("Hollow not found skipping");
					prepareRod();
					return;
				}
				debug("Hollow found -> swapping and clicking");
				client.player.getInventory().setSelectedSlot(slot);
				TickScheduler.schedule(1 + cfg().rendFirstClickDelay, RendMacro::castHollow);
			}
		} else {
			prepareRod();
		}
	}

	private static void prepareRod() {
		if (!enabled() || !KuudraState.dps() && !KuudraState.stun()) {
			return;
		}
		debug("Looking for rod");
		Integer rodSlot = hotbarSlotFromItem(Items.FISHING_ROD);
		if (rodSlot == null) {
			return;
		}
		debug("Rod found");
		if (cfg().rendAutoRod) {
			throwRod = true;
			Minecraft.getInstance().player.getInventory().setSelectedSlot(rodSlot);
		}
	}

	private static void checkRodAndEdge() {
		if (!enabled() || !KuudraState.dps()) {
			return;
		}
		Minecraft client = Minecraft.getInstance();
		LocalPlayer player = client.player;
		if (player == null || client.level == null) {
			return;
		}
		Vec3 pos = player.getPosition(HumanRotation.partialTick());
		BlockPos blockPos = BlockPos.containing(pos);

		if (edging && client.level.getBlockState(blockPos.below()).isAir()) {
			debug("Jumping");
			client.options.keyJump.setDown(true);
			edging = false;
			TickScheduler.schedule(3, () -> client.options.keyJump.setDown(false));
		}

		boolean outside = pos.x < -111.0 - cfg().rendOffsetRight || pos.x > -95.0 + cfg().rendOffsetLeft
			|| pos.z < -118.0 - cfg().rendOffsetBack || pos.z > -97.0 + cfg().rendOffsetFront;
		if (throwRod && outside) {
			if (!player.getMainHandItem().is(Items.FISHING_ROD)) {
				return;
			}
			debug("Throwing rod");
			TickScheduler.click(client.options.keyUse);
			throwRod = false;
		}
	}

	public static void useItem(Player player, InteractionHand hand, InteractionResult result) {
		if (!enabled() || !KuudraState.kuudra() || player.getY() > 20) {
			return;
		}
		if (!(result instanceof InteractionResult.Pass) && !(result instanceof InteractionResult.Success)) {
			return;
		}
		Minecraft client = Minecraft.getInstance();
		ItemStack item = player.getItemInHand(hand);
		String id = SkyblockItem.id(item);

		if ("STARRED_BONE_BOOMERANG".equals(id) && cfg().rendAutoHalberd) {
			debug("Threw bone");
			Integer slot = hotbarSlotFromId("AXE_OF_THE_SHREDDED");
			if (slot == null) {
				return;
			}
			client.player.getInventory().setSelectedSlot(slot);
			TickScheduler.schedule(1, () -> {
				debug("Throwing aots");
				TickScheduler.click(client.options.keyUse);
			});
		}

		if ("AXE_OF_THE_SHREDDED".equals(id)) {
			debug("Threw aots");
			if (cfg().rendAutoLoadout && client.getConnection() != null) {
				debug("Opening loadout");
				client.getConnection().sendCommand("loadout");
				clickLoadout = true;
				client.options.keyUp.setDown(false);
			}
		}

		if (item.is(Items.FISHING_ROD)) {
			debug("Rod thrown");
			if (cfg().rendAutoBone) {
				Integer boneSlot = hotbarSlotFromId("STARRED_BONE_BOOMERANG");
				if (boneSlot == null) {
					return;
				}
				client.player.getInventory().setSelectedSlot(boneSlot);
				TickScheduler.schedule(cfg().rendBoneDelay, () -> TickScheduler.click(client.options.keyUse));
			}
		}
	}

	public static void openScreen(ClientboundOpenScreenPacket packet) {
		Minecraft client = Minecraft.getInstance();
		if (!enabled() || !packet.getTitle().getString().contains("Loadout") || !clickLoadout
			|| client.player == null) {
			return;
		}
		clickLoadout = false;
		TickScheduler.schedule(cfg().rendClickDelay, true,
			() -> Storm.clickLoadout(cfg().rendLoadoutSlot, RendMacro::afterLoadout));
	}

	private static void afterLoadout() {
		Minecraft client = Minecraft.getInstance();
		if (!cfg().rendUseIceSpray || client.player == null) {
			return;
		}
		if ("STARRED_BONE_BOOMERANG".equals(SkyblockItem.id(client.player.getMainHandItem()))) {
			return;
		}
		Integer iceSpray = hotbarSlotFromId("STARRED_ICE_SPRAY_WAND");
		if (iceSpray == null) {
			return;
		}
		client.player.getInventory().setSelectedSlot(iceSpray);
		TickScheduler.schedule(1, () -> {
			TickScheduler.click(client.options.keyUse);
			TickScheduler.schedule(0, () -> {
				if (!cfg().rendAutoHalberd || client.player == null) {
					return;
				}
				if ("STARRED_BONE_BOOMERANG".equals(SkyblockItem.id(client.player.getMainHandItem()))) {
					return;
				}
				Integer aotsSlot = hotbarSlotFromId("AXE_OF_THE_SHREDDED");
				if (aotsSlot != null) {
					client.player.getInventory().setSelectedSlot(aotsSlot);
				}
			});
		});
	}

	private static void castHollow() {
		Minecraft client = Minecraft.getInstance();
		boolean wind = cfg().rendRagingWind;
		TickScheduler.click(wind ? client.options.keyUse : client.options.keyAttack);
		TickScheduler.schedule(cfg().rendSecondClickDelay, () -> {
			TickScheduler.click(wind ? client.options.keyAttack : client.options.keyUse);
			TickScheduler.schedule(2, RendMacro::prepareRod);
		});
	}

	private static void getRotationGoal() {
		if (!cfg().rendAutoRotate) {
			return;
		}
		TickScheduler.schedule(cfg().rendDelay, () -> {
			debug("Searching for kuudra...");
			if (KuudraState.kuudraEntity == null) {
				return;
			}
			Vec3 pos = KuudraState.kuudraEntity.position();
			debug("Found at " + pos.x + ", " + pos.z);
			if (pos.x < -128.0) {
				handleRotation(new Vec3(-123.5, 16.0, -105.5), cfg().rendRightMin, cfg().rendRightMax);
			} else if (pos.z > -84.0) {
				handleRotation(new Vec3(-104.5, 16.0, -82.5), cfg().rendFrontMin, cfg().rendFrontMax);
			} else if (pos.x > -72.0) {
				handleRotation(new Vec3(-80.5, 16.0, -104.5), cfg().rendLeftMin, cfg().rendLeftMax);
			} else if (pos.z < -132.0) {
				handleRotation(new Vec3(-98.5, 16.0, -129.5), cfg().rendBackMin, cfg().rendBackMax);
			} else {
				message("No valid kuudra spots found!");
			}
		});
	}

	private static void handleRotation(Vec3 goal, double minSpeed, double maxSpeed) {
		debug("Rotating");
		boolean walk = cfg().rendAutoWalk;
		HumanRotation.lookAt(goal, -30.0F,
			randomOffset(cfg().rendYawOffsetMin, cfg().rendYawOffsetMax),
			randomOffset(cfg().rendPitchOffsetMin, cfg().rendPitchOffsetMax),
			HumanRotation.Profile.speeds((float) minSpeed, (float) maxSpeed),
			walk,
			() -> {
				if (walk) {
					Minecraft.getInstance().options.keyUp.setDown(true);
				}
			});
	}

	private static float randomOffset(double lower, double upper) {
		return lower < upper ? (float) ThreadLocalRandom.current().nextDouble(lower, upper) : (float) lower;
	}

	public static void onBackbone() {
		Minecraft client = Minecraft.getInstance();
		if (!KuudraState.kuudra() || !enabled() || !KuudraState.dps() || !cfg().rendAutoPull
			|| client.gui.screen() != null) {
			return;
		}
		TickScheduler.schedule(cfg().rendPullDelay, () -> {
			if (client.player != null) {
				client.player.getInventory().setSelectedSlot(cfg().rendPullItemSlot - 1);
			}
			TickScheduler.schedule(2, () -> TickScheduler.click(client.options.keyAttack));
		});
	}

	private static Integer hotbarSlotFromId(String id) {
		LocalPlayer player = Minecraft.getInstance().player;
		for (int i = 0; i <= 7; i++) {
			if (id.equals(SkyblockItem.id(player.getInventory().getItem(i)))) {
				return i;
			}
		}
		return null;
	}

	private static Integer hotbarSlotFromItem(Item item) {
		LocalPlayer player = Minecraft.getInstance().player;
		for (int i = 0; i <= 7; i++) {
			if (player.getInventory().getItem(i).is(item)) {
				return i;
			}
		}
		return null;
	}

	private static void debug(String text) {
		if (cfg().rendDebug) {
			message(text);
		}
	}

	private static void message(String text) {
		LocalPlayer player = Minecraft.getInstance().player;
		if (player != null) {
			player.sendSystemMessage(Component.literal("[RaT] ").withStyle(ChatFormatting.DARK_AQUA)
				.append(Component.literal(text).withStyle(ChatFormatting.WHITE)));
		}
	}
}
