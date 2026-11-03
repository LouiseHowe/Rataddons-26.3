package me.rattokens.rataddon.feature;

import me.rattokens.rataddon.RaTAddonClient;
import me.rattokens.rataddon.config.RaTAddonConfig;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;

public final class HudEditorScreen extends Screen {
	private final Screen parent;
	private boolean draggingAlert;
	private KittyHud.Slot draggingKitty;
	private double grabX;
	private double grabY;

	public HudEditorScreen(Screen parent) {
		super(Component.literal("RatAddons HUD Editor"));
		this.parent = parent;
	}

	@Override
	protected void init() {
		addRenderableWidget(Button.builder(Component.literal("Done"), button -> onClose())
			.bounds(width / 2 - 50, height - 28, 100, 20)
			.build());
	}

	@Override
	public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
		super.extractRenderState(graphics, mouseX, mouseY, delta);
		graphics.centeredText(font, Component.literal("Drag to move, scroll to resize, right click to reset"),
			width / 2, 8, 0xFFAAAAAA);
		int alertX = (int) HudAlert.x(font, width, HudAlert.PREVIEW_TITLE, HudAlert.PREVIEW_SUBTITLE);
		int alertY = (int) HudAlert.y(height);
		int alertRight = alertX + (int) Math.ceil(alertWidth());
		int alertBottom = alertY + (int) Math.ceil(alertHeight());
		boolean alertHovered = draggingAlert || insideAlert(mouseX, mouseY);
		graphics.fill(alertX - 2, alertY - 2, alertRight + 2, alertBottom + 1,
			alertHovered ? 0x6055AAFF : 0x40000000);
		HudAlert.draw(graphics, font, HudAlert.PREVIEW_TITLE, HudAlert.PREVIEW_SUBTITLE, width, height, 255);
		if (alertHovered) {
			graphics.text(font, Component.literal("Title Alerts" + String.format(" (%.1fx)",
				HudAlert.setting().scale)), alertX, alertBottom + 3, 0xFF55AAFF, true);
		}
		for (KittyHud.Slot slot : KittyHud.Slot.values()) {
			int x = (int) KittyHud.x(font, width, slot), y = (int) KittyHud.y(height, slot);
			int right = x + (int) Math.ceil(KittyHud.width(font, slot));
			int bottom = y + (int) Math.ceil(KittyHud.height(slot));
			boolean hovered = slot == draggingKitty || insideKitty(slot, mouseX, mouseY);
			graphics.fill(x - 2, y - 2, right + 2, bottom + 1, hovered ? 0x6055AAFF : 0x40000000);
			KittyHud.draw(graphics, font, slot, Component.literal(slot.preview), width, height, 0xFFFFFFFF);
			if (hovered) graphics.text(font, Component.literal(slot.label + String.format(" (%.1fx)", slot.setting().scale)),
				x, bottom + 3, 0xFF55AAFF, true);
		}
	}

	@Override
	public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
		for (KittyHud.Slot slot : KittyHud.Slot.values()) {
			if (!insideKitty(slot, event.x(), event.y())) continue;
			if (event.button() == com.mojang.blaze3d.platform.InputConstants.MOUSE_BUTTON_RIGHT) {
				slot.setting().x = slot.setting().y = -1;
				slot.setting().scale = slot == KittyHud.Slot.LITTLEFOOT_POPUP ? 4.5F
					: slot == KittyHud.Slot.PICKAXE_POPUP || slot == KittyHud.Slot.WISP_POPUP ? 3.0F : 1.0F;
				return true;
			}
			if (event.button() == com.mojang.blaze3d.platform.InputConstants.MOUSE_BUTTON_LEFT) {
				draggingKitty = slot;
				grabX = event.x() - KittyHud.x(font, width, slot);
				grabY = event.y() - KittyHud.y(height, slot);
				return true;
			}
		}
		if (insideAlert(event.x(), event.y())) {
			if (event.button() == com.mojang.blaze3d.platform.InputConstants.MOUSE_BUTTON_RIGHT) {
				RaTAddonConfig.HudSetting setting = HudAlert.setting();
				setting.x = -1.0F;
				setting.y = -1.0F;
				setting.scale = 2.0F;
				return true;
			}
			if (event.button() == com.mojang.blaze3d.platform.InputConstants.MOUSE_BUTTON_LEFT) {
				draggingAlert = true;
				grabX = event.x() - HudAlert.x(font, width, HudAlert.PREVIEW_TITLE, HudAlert.PREVIEW_SUBTITLE);
				grabY = event.y() - HudAlert.y(height);
				return true;
			}
		}
		return super.mouseClicked(event, doubleClick);
	}

	@Override
	public boolean mouseDragged(MouseButtonEvent event, double dx, double dy) {
		if (draggingKitty != null) {
			var setting = draggingKitty.setting();
			setting.x = (float) Math.clamp(event.x() - grabX, 0, Math.max(0, width - KittyHud.width(font, draggingKitty)));
			setting.y = (float) Math.clamp(event.y() - grabY, 0, Math.max(0, height - KittyHud.height(draggingKitty)));
			return true;
		}
		if (draggingAlert) {
			RaTAddonConfig.HudSetting setting = HudAlert.setting();
			setting.x = (float) Math.clamp(event.x() - grabX, 0.0, Math.max(0.0, width - alertWidth()));
			setting.y = (float) Math.clamp(event.y() - grabY, 0.0, Math.max(0.0, height - alertHeight()));
			return true;
		}
		return super.mouseDragged(event, dx, dy);
	}

	@Override
	public boolean mouseReleased(MouseButtonEvent event) {
		draggingAlert = false;
		draggingKitty = null;
		return super.mouseReleased(event);
	}

	@Override
	public boolean mouseScrolled(double mouseX, double mouseY, double horizontal, double vertical) {
		for (KittyHud.Slot slot : KittyHud.Slot.values()) {
			if (!insideKitty(slot, mouseX, mouseY)) continue;
			slot.setting().scale = Math.clamp(slot.setting().scale + (vertical > 0 ? 0.1F : -0.1F),
				0.5F, slot == KittyHud.Slot.LITTLEFOOT_POPUP ? 5.0F : 4.0F);
			return true;
		}
		if (insideAlert(mouseX, mouseY)) {
			RaTAddonConfig.HudSetting setting = HudAlert.setting();
			setting.scale = Math.clamp(setting.scale + (vertical > 0 ? 0.1F : -0.1F), 0.5F, 4.0F);
			return true;
		}
		return super.mouseScrolled(mouseX, mouseY, horizontal, vertical);
	}

	private boolean insideKitty(KittyHud.Slot slot, double x, double y) {
		float left = KittyHud.x(font, width, slot), top = KittyHud.y(height, slot);
		return x >= left - 2 && x <= left + KittyHud.width(font, slot) + 2
			&& y >= top - 2 && y <= top + KittyHud.height(slot) + 1;
	}

	private boolean insideAlert(double mouseX, double mouseY) {
		float x = HudAlert.x(font, width, HudAlert.PREVIEW_TITLE, HudAlert.PREVIEW_SUBTITLE);
		float y = HudAlert.y(height);
		return mouseX >= x - 2 && mouseX <= x + alertWidth() + 2
			&& mouseY >= y - 2 && mouseY <= y + alertHeight() + 1;
	}

	private float alertWidth() {
		return HudAlert.width(font, HudAlert.PREVIEW_TITLE, HudAlert.PREVIEW_SUBTITLE);
	}

	private float alertHeight() {
		return HudAlert.height(HudAlert.PREVIEW_TITLE, HudAlert.PREVIEW_SUBTITLE);
	}

	@Override
	public void onClose() {
		RaTAddonClient.CONFIG.save();
		if (minecraft != null) {
			minecraft.gui.setScreen(parent);
		}
	}
}
