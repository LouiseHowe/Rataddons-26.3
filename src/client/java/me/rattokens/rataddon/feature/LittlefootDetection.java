package me.rattokens.rataddon.feature;

import me.rattokens.rataddon.RaTAddonClient;
import me.rattokens.rataddon.config.RaTAddonConfig;
import me.rattokens.rataddon.util.ColorOptions;
import me.rattokens.rataddon.util.SkyblockLocation;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.hud.VanillaHudElements;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;

import java.util.UUID;

/** Mineshaft-only Littlefoot detection, following JMU's UUID/name and once-per-shaft rules. */
public final class LittlefootDetection {
	private static final UUID LITTLEFOOT_UUID = UUID.fromString("d79e2198-3ccc-27fd-98b8-6c2781406a96");
	private static boolean wasInShaft;
	private static boolean announced;
	private static int popupTicks;
	private static UUID detected;

	private LittlefootDetection() {}

	public static void init() {
		ClientTickEvents.END_CLIENT_TICK.register(LittlefootDetection::tick);
		HudElementRegistry.attachElementBefore(VanillaHudElements.CHAT,
			Identifier.fromNamespaceAndPath(RaTAddonClient.MOD_ID, "littlefoot_popup"),
			(graphics, delta) -> render(graphics));
	}

	public static boolean isDetected(UUID uuid) {
		return uuid != null && uuid.equals(detected);
	}

	private static void tick(Minecraft client) {
		if (popupTicks > 0) popupTicks--;
		boolean inShaft = SkyblockLocation.inMineshaft();
		RaTAddonConfig cfg = RaTAddonClient.CONFIG;
		if (!inShaft) {
			wasInShaft = false;
			announced = false;
			detected = null;
			popupTicks = 0;
			return;
		}
		if (!wasInShaft) announced = false;
		wasInShaft = true;
		if (cfg == null || !cfg.littlefootDetection || client.level == null || client.player == null) {
			detected = null;
			return;
		}
		detected = null;
		for (AbstractClientPlayer player : client.level.players()) {
			if (player == client.player || !isLittlefoot(player)) continue;
			detected = player.getUUID();
			if (!announced) {
				announced = true;
				if (cfg.littlefootMessage)
					client.player.sendSystemMessage(Component.literal("§b[RatAddons] §aLittlefoot is here!"));
				if (cfg.littlefootPopup) popupTicks = totalTicks(cfg);
			}
			break;
		}
	}

	private static boolean isLittlefoot(AbstractClientPlayer player) {
		if (LITTLEFOOT_UUID.equals(player.getUUID())) return true;
		if ("Littlefoot".equalsIgnoreCase(player.getScoreboardName().trim())) return true;
		if (player.getDisplayName() != null && "Littlefoot".equalsIgnoreCase(player.getDisplayName().getString().trim())) return true;
		return "Littlefoot".equalsIgnoreCase(player.getGameProfile().name().trim());
	}

	private static int totalTicks(RaTAddonConfig cfg) {
		return Math.clamp(cfg.littlefootPopupFadeIn, 0, 100)
			+ Math.clamp(cfg.littlefootPopupStay, 0, 200)
			+ Math.clamp(cfg.littlefootPopupFadeOut, 0, 100);
	}

	private static void render(GuiGraphicsExtractor graphics) {
		Minecraft client = Minecraft.getInstance();
		RaTAddonConfig cfg = RaTAddonClient.CONFIG;
		if (client.player == null || cfg == null || !cfg.littlefootDetection || !cfg.littlefootPopup
			|| popupTicks <= 0 || client.gui.screen() instanceof HudEditorScreen) return;
		int fadeIn = Math.clamp(cfg.littlefootPopupFadeIn, 0, 100);
		int fadeOut = Math.clamp(cfg.littlefootPopupFadeOut, 0, 100);
		int elapsed = totalTicks(cfg) - popupTicks;
		float alpha = fadeIn > 0 && elapsed < fadeIn ? (elapsed + 1F) / fadeIn
			: fadeOut > 0 && popupTicks <= fadeOut ? (float) popupTicks / fadeOut : 1F;
		String text = cfg.littlefootPopupText == null || cfg.littlefootPopupText.isBlank()
			? "Littlefoot!" : cfg.littlefootPopupText;
		int color = ((int) (Math.clamp(alpha, 0F, 1F) * 255)) << 24 | ColorOptions.rgb(cfg.littlefootPopupColor);
		KittyHud.draw(graphics, client.font, KittyHud.Slot.LITTLEFOOT_POPUP,
			Component.literal(text), client.getWindow().getGuiScaledWidth(),
			client.getWindow().getGuiScaledHeight(), color);
	}
}
