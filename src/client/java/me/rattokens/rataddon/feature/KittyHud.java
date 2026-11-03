package me.rattokens.rataddon.feature;

import me.rattokens.rataddon.RaTAddonClient;
import me.rattokens.rataddon.config.RaTAddonConfig;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;

import java.util.List;

/** Positions Kittycat-style overlays through RatAddons' HUD editor. */
public final class KittyHud {
	public enum Slot {
		ARROWS("Arrow count", "§a2304x §eFlint Arrow", 140),
		DIANA_MOB_HITS("Diana mob hits", "§cMinos Inquisitor §a12M/20M❤\n§bPlayer §ex3", 210),
		POM_TICKS("POM ticks", "§c4/8", 55),
		POM_PROFILER("POM ping/TPS", "§bPing: §f120 §b| TPS: §f20.0", 68),
		PICKAXE_ABILITY("Pickaxe ability", "§9§lMining Speed Boost: §b42s", 8),
		PICKAXE_DURATION("Pickaxe duration", "§6§lDUR§f: §e12.00", 20),
		WISP_TIMER("Will-o-Wisp", "§fWisp: §b300s", 32),
		PICKAXE_POPUP("Pickaxe popup", "Ability Ready!", -50),
		WISP_POPUP("Wisp popup", "Place Wisp!", -20),
		LITTLEFOOT_POPUP("Littlefoot popup", "Littlefoot!", 0);

		public final String label;
		public final String preview;
		public final int defaultY;
		Slot(String label, String preview, int defaultY) {
			this.label = label; this.preview = preview; this.defaultY = defaultY;
		}
		public RaTAddonConfig.HudSetting setting() {
			String key = "kitty_" + name().toLowerCase();
			boolean firstUse = RaTAddonClient.CONFIG.hudSettings == null
				|| !RaTAddonClient.CONFIG.hudSettings.containsKey(key);
			RaTAddonConfig.HudSetting setting = RaTAddonClient.CONFIG.hud(key);
			if (firstUse && this == LITTLEFOOT_POPUP) setting.scale = 4.5F;
			if (firstUse && (this == PICKAXE_POPUP || this == WISP_POPUP)) setting.scale = 3.0F;
			return setting;
		}
	}

	private KittyHud() {}

	public static float width(Font font, Slot slot) {
		if (slot == Slot.DIANA_MOB_HITS) {
			int widest = 0;
			for (String line : slot.preview.split("\\n")) widest = Math.max(widest, font.width(line));
			return widest * slot.setting().scale;
		}
		return font.width(slot.preview) * slot.setting().scale;
	}
	public static float height(Slot slot) {
		if (slot == Slot.DIANA_MOB_HITS)
			return slot.preview.split("\\n").length * 10 * slot.setting().scale;
		return 9 * slot.setting().scale;
	}
	public static float x(Font font, int guiWidth, Slot slot) {
		float custom = slot.setting().x;
				if (custom < 0 && slot == Slot.DIANA_MOB_HITS) return 300;
		if (custom < 0 && (slot == Slot.PICKAXE_ABILITY || slot == Slot.PICKAXE_DURATION
			|| slot == Slot.WISP_TIMER)) return 5;
		return custom < 0 ? (guiWidth - width(font, slot)) / 2 : custom;
	}
	public static float y(int guiHeight, Slot slot) {
		float custom = slot.setting().y;
		if (custom < 0 && slot == Slot.LITTLEFOOT_POPUP)
			return guiHeight * 0.35F - height(slot) / 2F;
		return custom < 0 ? (slot.defaultY < 0 ? guiHeight / 2F + slot.defaultY : slot.defaultY) : custom;
	}
	public static void draw(GuiGraphicsExtractor graphics, Font font, Slot slot, Component text,
		int guiWidth, int guiHeight, int color) {
		if (slot == Slot.DIANA_MOB_HITS) {
			drawLines(graphics, font, slot,
				java.util.Arrays.stream(text.getString().split("\\n"))
					.map(line -> (Component) Component.literal(line)).toList(),
				guiWidth, guiHeight);
			return;
		}
		var setting = slot.setting();
		graphics.pose().pushMatrix();
		graphics.pose().translate(x(font, guiWidth, slot), y(guiHeight, slot));
		graphics.pose().scale(setting.scale, setting.scale);
		graphics.text(font, text, 0, 0, color, true);
		graphics.pose().popMatrix();
	}

	public static void drawLines(GuiGraphicsExtractor graphics, Font font, Slot slot,
		List<Component> lines, int guiWidth, int guiHeight) {
		var setting = slot.setting();
		graphics.pose().pushMatrix();
		float x = x(font, guiWidth, slot);
		graphics.pose().translate(x, y(guiHeight, slot));
		graphics.pose().scale(setting.scale, setting.scale);
		for (int i = 0; i < lines.size(); i++)
			graphics.text(font, lines.get(i), 0, i * 10, 0xFFFFFFFF, true);
		graphics.pose().popMatrix();
	}
}
