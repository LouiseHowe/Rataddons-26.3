package me.rattokens.rataddon.feature;

import me.rattokens.rataddon.RaTAddonClient;
import me.rattokens.rataddon.config.RaTAddonConfig;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.hud.VanillaHudElements;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;

public final class HudAlert {
	public static final String HUD_KEY = "TITLE_ALERT";
	public static final Component PREVIEW_TITLE = Component.literal("§c§lBONE BROKE");
	public static final Component PREVIEW_SUBTITLE = Component.literal("§eHUD Alert Preview");
	private static Component title = Component.empty();
	private static Component subtitle = Component.empty();
	private static int fadeIn;
	private static int stay;
	private static int fadeOut;
	private static int age;
	private static boolean active;

	private HudAlert() {
	}

	public static void init() {
		ClientTickEvents.END_CLIENT_TICK.register(client -> {
			if (active && ++age >= fadeIn + stay + fadeOut) {
				active = false;
			}
		});
		HudElementRegistry.attachElementBefore(
			VanillaHudElements.CHAT,
			Identifier.fromNamespaceAndPath(RaTAddonClient.MOD_ID, "title_alert"),
			(graphics, deltaTracker) -> render(graphics)
		);
	}

	public static void show(Component newTitle, Component newSubtitle, int newFadeIn, int newStay, int newFadeOut) {
		title = newTitle == null ? Component.empty() : newTitle;
		subtitle = newSubtitle == null ? Component.empty() : newSubtitle;
		fadeIn = Math.max(0, newFadeIn);
		stay = Math.max(0, newStay);
		fadeOut = Math.max(0, newFadeOut);
		age = 0;
		active = fadeIn + stay + fadeOut > 0;
	}

	private static void render(GuiGraphicsExtractor graphics) {
		Minecraft client = Minecraft.getInstance();
		if (!active || client.player == null || client.gui.screen() instanceof HudEditorScreen || setting().hide) {
			return;
		}
		draw(graphics, client.font, title, subtitle, client.getWindow().getGuiScaledWidth(),
			client.getWindow().getGuiScaledHeight(), alpha());
	}

	public static void draw(GuiGraphicsExtractor graphics, Font font, Component drawTitle, Component drawSubtitle,
		int guiWidth, int guiHeight, int alpha) {
		RaTAddonConfig.HudSetting setting = setting();
		float width = width(font, drawTitle, drawSubtitle);
		graphics.pose().pushMatrix();
		graphics.pose().translate(x(font, guiWidth, drawTitle, drawSubtitle), y(guiHeight));
		graphics.pose().scale(setting.scale, setting.scale);
		int color = Math.clamp(alpha, 0, 255) << 24 | 0xFFFFFF;
		int unscaledWidth = Math.round(width / setting.scale);
		int lineY = 0;
		if (!drawTitle.getString().isEmpty()) {
			graphics.text(font, drawTitle, (unscaledWidth - font.width(drawTitle)) / 2, lineY, color, true);
			lineY += 11;
		}
		if (!drawSubtitle.getString().isEmpty()) {
			graphics.text(font, drawSubtitle, (unscaledWidth - font.width(drawSubtitle)) / 2, lineY, color, true);
		}
		graphics.pose().popMatrix();
	}

	public static RaTAddonConfig.HudSetting setting() {
		boolean newSetting = RaTAddonClient.CONFIG.hudSettings == null
			|| !RaTAddonClient.CONFIG.hudSettings.containsKey(HUD_KEY);
		RaTAddonConfig.HudSetting setting = RaTAddonClient.CONFIG.hud(HUD_KEY);
		if (newSetting) {
			setting.scale = 2.0F;
		}
		return setting;
	}

	public static float x(Font font, int guiWidth, Component drawTitle, Component drawSubtitle) {
		RaTAddonConfig.HudSetting setting = setting();
		return setting.x < 0.0F ? (guiWidth - width(font, drawTitle, drawSubtitle)) / 2.0F : setting.x;
	}

	public static float y(int guiHeight) {
		float y = setting().y;
		return y < 0.0F ? guiHeight * 0.42F : y;
	}

	public static float width(Font font, Component drawTitle, Component drawSubtitle) {
		return Math.max(font.width(drawTitle), font.width(drawSubtitle)) * setting().scale;
	}

	public static float height(Component drawTitle, Component drawSubtitle) {
		int lines = (drawTitle.getString().isEmpty() ? 0 : 1) + (drawSubtitle.getString().isEmpty() ? 0 : 1);
		return (lines == 2 ? 20.0F : lines == 1 ? 9.0F : 0.0F) * setting().scale;
	}

	private static int alpha() {
		if (fadeIn > 0 && age < fadeIn) {
			return Math.clamp((age + 1) * 255 / fadeIn, 0, 255);
		}
		int fadeOutStart = fadeIn + stay;
		if (fadeOut > 0 && age >= fadeOutStart) {
			return Math.clamp((fadeIn + stay + fadeOut - age) * 255 / fadeOut, 0, 255);
		}
		return 255;
	}
}
