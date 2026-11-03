package me.rattokens.rataddon.config;

import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;

import java.util.function.Consumer;

/** Cyan settings control shared by navigation and option rows. */
final class ThemedButton extends AbstractWidget {
	private static final int CYAN = 0xFF38D8E7;
	private final Consumer<ThemedButton> action;
	private boolean selected;
	private boolean childPage;

	private ThemedButton(int x, int y, int width, int height, Component text, Consumer<ThemedButton> action) {
		super(x, y, width, height, text);
		this.action = action;
	}

	static Builder builder(Component text, Consumer<ThemedButton> action) {
		return new Builder(text, action);
	}

	void selected(boolean value) {
		selected = value;
	}
	void childPage(boolean value) {
		childPage = value;
	}

	@Override
	protected void extractWidgetRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
		boolean highlighted = active && (isHovered() || isFocused());
		int background = selected ? 0xFF214148 : highlighted ? 0xFF273840 : 0xFF202A31;
		UiShapes.rounded(graphics, getX(), getY(), getRight(), getBottom(), 4, background);
		if (selected) UiShapes.rounded(graphics, getX(), getY() + 5, getX() + 3, getBottom() - 5, 1, CYAN);
		Font font = Minecraft.getInstance().font;
		String raw = ChatFormatting.stripFormatting(getMessage().getString());
		if (raw == null) raw = "";
		int textY = getY() + (getHeight() - 9) / 2;
		int suffix = raw.lastIndexOf(": ");
		int rightLimit = getRight() - (childPage ? 23 : 9);
		if (childPage) {
			graphics.text(font, Component.literal(selected ? "⌄" : "›"), getRight() - 15, textY - 1,
				active ? CYAN : 0xFF819DA3, false);
		}
		if (suffix > 0 && suffix < raw.length() - 2) {
			String label = raw.substring(0, suffix);
			String value = raw.substring(suffix + 2);
			boolean switchValue = value.equalsIgnoreCase("ON") || value.equalsIgnoreCase("OFF");
			if (switchValue) {
				boolean on = value.equalsIgnoreCase("ON");
				int sx = rightLimit - 31;
				UiShapes.rounded(graphics, sx, getY() + (getHeight() - 13) / 2,
					sx + 31, getY() + (getHeight() + 13) / 2, 6, on ? CYAN : 0xFF53666C);
				int knobX = on ? sx + 19 : sx + 2;
				UiShapes.rounded(graphics, knobX, getY() + (getHeight() - 9) / 2,
					knobX + 9, getY() + (getHeight() + 9) / 2, 4, 0xFFF4FBFC);
				graphics.text(font, Component.literal(fit(font, label, sx - getX() - 15)),
					getX() + 9, textY, active ? 0xFFE6F6F8 : 0xFF7F9AA2, false);
			} else {
				int valueWidth = font.width(value);
				graphics.text(font, Component.literal(fit(font, label, rightLimit - valueWidth - getX() - 19)),
					getX() + 9, textY, active ? 0xFFE6F6F8 : 0xFF7F9AA2, false);
				graphics.text(font, Component.literal(value), rightLimit - valueWidth, textY,
					active ? CYAN : 0xFF7F9AA2, false);
			}
		} else {
			graphics.text(font, Component.literal(fit(font, raw, rightLimit - getX() - 11)), getX() + 9,
				textY, active ? selected ? CYAN : 0xFFE6F6F8 : 0xFF7F9AA2, false);
		}
	}

	static String fit(Font font, String text, int maxWidth) {
		if (maxWidth <= 0) return "";
		if (font.width(text) <= maxWidth) return text;
		String ellipsis = "…";
		while (!text.isEmpty() && font.width(text + ellipsis) > maxWidth) {
			text = text.substring(0, text.length() - 1);
		}
		return text + ellipsis;
	}

	@Override
	public void onClick(MouseButtonEvent event, boolean doubleClick) {
		press();
	}

	@Override
	public boolean keyPressed(KeyEvent event) {
		if (!active || !isFocused() || !event.isConfirmation()) return false;
		press();
		return true;
	}

	private void press() {
		if (!active) return;
		Minecraft client = Minecraft.getInstance();
		playButtonClickSound(client.getSoundManager());
		action.accept(this);
	}

	@Override
	protected void updateWidgetNarration(NarrationElementOutput output) {
		defaultButtonNarrationText(output);
	}

	static final class Builder {
		private final Component text;
		private final Consumer<ThemedButton> action;
		private int x;
		private int y;
		private int width = 150;
		private int height = 20;

		private Builder(Component text, Consumer<ThemedButton> action) {
			this.text = text;
			this.action = action;
		}

		Builder bounds(int x, int y, int width, int height) {
			this.x = x;
			this.y = y;
			this.width = width;
			this.height = height;
			return this;
		}

		ThemedButton build() {
			return new ThemedButton(x, y, width, height, text, action);
		}
	}
}
