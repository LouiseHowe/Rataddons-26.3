package me.rattokens.rataddon.feature;

import me.rattokens.rataddon.RaTAddonClient;
import me.rattokens.rataddon.util.KittyKuudraClick;
import me.rattokens.rataddon.util.KittyAimAssist;
import me.rattokens.rataddon.util.KuudraState;
import me.rattokens.rataddon.util.SkyblockItem;
import me.rattokens.rataddon.util.TickScheduler;
import me.rattokens.rataddon.util.WorldRender;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.protocol.game.ClientboundContainerSetSlotPacket;
import net.minecraft.network.protocol.game.ClientboundOpenScreenPacket;
import net.minecraft.network.protocol.game.ClientboundPlayerPositionPacket;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.lwjgl.sdl.SDLMouse;

/** Kittycat's Kuudra shop and stun helpers. */
public final class KittyStun {
	private static final Vec3 SHOP_WAYPOINT = new Vec3(-71.5, 79, -102.5);
	private static boolean purchased;
	private static boolean podDestroyed;
	private static boolean wasBlind;
	private static Object lastLevel;

	private KittyStun() {}

	public static void init() {
		ClientTickEvents.START_CLIENT_TICK.register(client -> {
			if (client.level != lastLevel) {
				lastLevel = client.level;
				purchased = podDestroyed = wasBlind = false;
			}
			if (!active() || !RaTAddonClient.CONFIG.kittyAutoStun || client.player == null) {
				wasBlind = false;
				return;
			}
			boolean blind = client.player.getEffect(MobEffects.BLINDNESS) != null;
			if (!wasBlind && blind) {
				TickScheduler.schedule(9 + RaTAddonClient.CONFIG.kittyStunEtherDelay,
					RaTAddonClient.CONFIG.kittyStunServerTicks, () -> {
					if (client.player == null || !etherItem(client.player)) return;
					KittyKuudraClick.useItem(-10.5F, 36.5F);
					int slot = pickobulusSlot(client.player);
					if (slot < 0) return;
					client.player.getInventory().setSelectedSlot(slot);
					TickScheduler.schedule(1, () -> KittyKuudraClick.useItem(-10.5F, 36.5F));
				});
			}
			wasBlind = blind;
		});
		LevelRenderEvents.END_MAIN.register(context -> {
			if (!active()) return;
			if (RaTAddonClient.CONFIG.kittyStunShopArea && RaTAddonClient.CONFIG.kittyStunAutoOpenShop)
				WorldRender.boxOverlay(context, new AABB(-74, 79, -104, -70, 79.05, -101), 0xFF00FFFF, 2.5F);
			if (RaTAddonClient.CONFIG.kittyStunShopWaypoint)
				WorldRender.boxOverlay(context,
					new AABB(SHOP_WAYPOINT.x - 0.5, SHOP_WAYPOINT.y, SHOP_WAYPOINT.z - 0.5,
						SHOP_WAYPOINT.x + 0.5, SHOP_WAYPOINT.y + 0.05, SHOP_WAYPOINT.z + 0.5),
					0xFFFF0000, 2.5F);
			if (RaTAddonClient.CONFIG.kittyStunWaypoint && KuudraState.stun() && !podDestroyed) {
				Minecraft client = Minecraft.getInstance();
				if (client.player != null) {
					Vec3 pos = client.player.getPosition(me.rattokens.rataddon.util.HumanRotation.partialTick())
						.add(podOffset());
					WorldRender.boxOverlay(context, new AABB(pos, pos).inflate(0.5), 0xFF00FFFF, 2.5F);
				}
			}
		});
	}

	public static void handleChat(String message) {
		if (message.endsWith(" destroyed one of Kuudra's pods!")) podDestroyed = true;
		if (message.equals("You purchased Human Cannonball!")) {
			purchased = true;
			Minecraft client = Minecraft.getInstance();
			if (active() && RaTAddonClient.CONFIG.kittyStunAutoCloseShop && client.player != null)
				client.player.closeContainer();
			TickScheduler.schedule(40, () -> purchased = false);
		}
	}

	public static void useItem(Player player, InteractionHand hand, InteractionResult result) {
		if (!active() || !RaTAddonClient.CONFIG.kittyStunAutoOpenShop || !player.isCrouching()) return;
		String id = SkyblockItem.id(player.getMainHandItem());
		if (!id.equals("ETHERWARP_CONDUIT") && !id.equals("ASPECT_OF_THE_VOID")) return;
		Vec3 start = player.getEyePosition();
		var hit = player.level().clip(new ClipContext(start, start.add(player.getLookAngle().scale(50)),
			ClipContext.Block.OUTLINE, ClipContext.Fluid.NONE, player));
		var pos = hit.getBlockPos();
		if (pos.getX() < -74 || pos.getX() > -70 || pos.getY() != 78 || pos.getZ() < -104 || pos.getZ() > -101) return;
		int slot = AutomationItems.hotbar(stack -> "KUUDRA_SHOP_ITEM".equals(SkyblockItem.id(stack)));
		if (slot < 0) return;
		player.getInventory().setSelectedSlot(slot);
		TickScheduler.schedule(1, () -> {
			Minecraft client = Minecraft.getInstance();
			if (client.gameMode != null && client.player != null)
				client.gameMode.useItem(client.player, InteractionHand.MAIN_HAND);
		});
	}

	public static void onPositionChange(ClientboundPlayerPositionPacket packet) {
		if (!active() || !RaTAddonClient.CONFIG.kittyStunAutoPickobulus || !KuudraState.stun()) return;
		Vec3 pos = packet.change().position();
		if (RaTAddonClient.CONFIG.kittyStunEarlyPicko) {
			if (!pos.equals(new Vec3(-161, 49, -186))) return;
		} else if (podDestroyed || pos.x < -171 || pos.x > -148 || pos.y < 26 || pos.y > 31 || pos.z < -174 || pos.z > -151) return;
		Minecraft client = Minecraft.getInstance();
		if (client.player == null) return;
		int slot = pickobulusSlot(client.player);
		if (slot < 0) return;
		if (client.player.getInventory().getSelectedSlot() == slot) {
			if (client.gameMode != null) client.gameMode.useItem(client.player, InteractionHand.MAIN_HAND);
		} else {
			client.player.getInventory().setSelectedSlot(slot);
			TickScheduler.schedule(1, () -> {
				if (client.gameMode != null && client.player != null)
					client.gameMode.useItem(client.player, InteractionHand.MAIN_HAND);
			});
		}
	}

	public static boolean closeShop(ClientboundOpenScreenPacket packet) {
		return active() && purchased && RaTAddonClient.CONFIG.kittyStunAutoCloseShop;
	}

	public static void onSetSlot(ClientboundContainerSetSlotPacket packet) {
		if (!active() || !RaTAddonClient.CONFIG.kittyStunAutoCursor
			|| !packet.getItem().getHoverName().getString().equals("Human Cannonball")) return;
		Minecraft client = Minecraft.getInstance();
		if (!(client.gui.screen() instanceof net.minecraft.client.gui.screens.inventory.AbstractContainerScreen<?> screen)
			|| screen.getMenu().containerId != packet.getContainerId()
			|| packet.getSlot() < 0 || packet.getSlot() >= screen.getMenu().slots.size()) return;
		var slot = screen.getMenu().getSlot(packet.getSlot());
		double guiX = screen.leftPos + slot.x + 8, guiY = screen.topPos + slot.y + 8;
		var window = client.getWindow();
		SDLMouse.SDL_WarpMouseInWindow(window.handle(), (float) (guiX * window.getScreenWidth() / window.getGuiScaledWidth()),
			(float) (guiY * window.getScreenHeight() / window.getGuiScaledHeight()));
	}

	public static boolean noBlind() {
		return active() && RaTAddonClient.CONFIG.kittyStunNoBlind;
	}

	public static double[] onTurn(double dx, double dy) {
		if (!active()) return null;
		KittyAimAssist.Candidate best = null;
		Minecraft client = Minecraft.getInstance();
		if (RaTAddonClient.CONFIG.kittyStunShopWaypoint && RaTAddonClient.CONFIG.kittyStunShopAimAssist
			&& client.player != null && (!RaTAddonClient.CONFIG.kittyStunShopOnlyLeft || client.player.getX() >= -102)) {
			best = KittyAimAssist.candidate(SHOP_WAYPOINT,
				RaTAddonClient.CONFIG.kittyStunShopAimFov, RaTAddonClient.CONFIG.kittyStunShopAimStrength);
		}
		if (KuudraState.stun() && !podDestroyed && RaTAddonClient.CONFIG.kittyStunWaypoint
			&& RaTAddonClient.CONFIG.kittyStunAimAssist) {
			if (client.player != null) {
				var candidate = KittyAimAssist.candidate(client.player.position().add(podOffset()),
					RaTAddonClient.CONFIG.kittyStunAimFov, RaTAddonClient.CONFIG.kittyStunAimStrength);
				if (candidate != null && (best == null || candidate.distance() < best.distance())) best = candidate;
			}
		}
		return KittyAimAssist.adjust(dx, dy, best);
	}

	private static int pickobulusSlot(Player player) {
		for (int i = 0; i < 8; i++) {
			var lore = player.getInventory().getItem(i).get(DataComponents.LORE);
			if (lore != null && lore.lines().stream().anyMatch(line -> line.getString().contains("Ability: Pickobulus"))) return i;
		}
		return -1;
	}

	private static boolean etherItem(Player player) {
		String id = SkyblockItem.id(player.getMainHandItem());
		return id.equals("ETHERWARP_CONDUIT") || id.equals("ASPECT_OF_THE_VOID");
	}

	private static Vec3 podOffset() {
		return switch (RaTAddonClient.CONFIG.kittyStunPod) {
			case 0 -> new Vec3(8.5, -21.5, 13.5);
			case 2 -> new Vec3(-6.5, -21.5, 18.5);
			default -> new Vec3(5.5, -20.5, 29.5);
		};
	}

	private static boolean active() {
		return RaTAddonClient.CONFIG.kittyStun && (KuudraState.stun() || KuudraState.phase == KuudraState.Phase.BUILD);
	}
}
