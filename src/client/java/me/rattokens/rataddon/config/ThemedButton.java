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

/** Compact Phantom-style rows with RatAddons' fixed cyan accent. */
final class ThemedButton extends AbstractWidget {
    private static final int CYAN = 0xFF38D8E7;
    private static final int TEXT = 0xFFDCE8EC;
    private static final int MUTED = 0xFF85969E;
    private final Consumer<ThemedButton> action;
    private boolean selected;
    private boolean childPage;
    private boolean sidebar;

    private ThemedButton(int x, int y, int width, int height, Component text, Consumer<ThemedButton> action) {
        super(x, y, width, height, text);
        this.action = action;
    }

    static Builder builder(Component text, Consumer<ThemedButton> action) {
        return new Builder(text, action);
    }

    void selected(boolean value) { selected = value; }
    void childPage(boolean value) { childPage = value; }
    void sidebar(boolean value) { sidebar = value; }

    @Override
    protected void extractWidgetRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
        boolean highlighted = active && (isHovered() || isFocused());
        Font font = Minecraft.getInstance().font;
        String raw = ChatFormatting.stripFormatting(getMessage().getString());
        if (raw == null) raw = "";
        int x = getX(), y = getY(), right = getRight(), bottom = getBottom();
        int textY = y + (getHeight() - font.lineHeight) / 2 + 1;

        if (sidebar) {
            if (selected) {
                graphics.fill(x, y, right, bottom, 0xFF1C3038);
                graphics.fill(x, y, x + 2, bottom, CYAN);
            } else if (highlighted) {
                graphics.fill(x, y, right, bottom, 0xFF1B262C);
            }
            graphics.text(font, Component.literal(fit(font, raw, getWidth() - 24)),
                x + 13, textY, selected ? CYAN : highlighted ? TEXT : MUTED, false);
            return;
        }

        if (selected) graphics.fill(x, y, right, bottom, 0x1638D8E7);
        else if (highlighted) graphics.fill(x, y, right, bottom, 0x0DFFFFFF);
        graphics.fill(x, bottom - 1, right, bottom, 0x1EFFFFFF);

        int suffix = raw.lastIndexOf(": ");
        boolean hasValue = suffix > 0 && suffix < raw.length() - 2;
        String label = hasValue ? raw.substring(0, suffix) : raw;
        String value = hasValue ? raw.substring(suffix + 2) : "";
        boolean isSwitch = value.equalsIgnoreCase("ON") || value.equalsIgnoreCase("OFF");
        int textEnd = right - 9;

        if (isSwitch) {
            boolean on = value.equalsIgnoreCase("ON");
            int sx = right - 42;
            int sy = y + (getHeight() - 12) / 2;
            UiShapes.rounded(graphics, sx - 1, sy - 1, sx + 35, sy + 13, 7, 0xFF53646B);
            UiShapes.rounded(graphics, sx, sy, sx + 34, sy + 12, 6, on ? CYAN : 0xFF34444A);
            int knobX = on ? sx + 22 : sx + 2;
            UiShapes.rounded(graphics, knobX, sy + 1, knobX + 10, sy + 11, 5, 0xFFF8FEFF);
            textEnd = sx - (childPage ? 16 : 5);
        } else if (hasValue) {
            int valueWidth = font.width(value);
            graphics.text(font, Component.literal(value), right - valueWidth - 8,
                textY, active ? CYAN : MUTED, false);
            textEnd = right - valueWidth - 15;
        }

        if (childPage) {
            int arrowX = isSwitch ? right - 57 : right - 18;
            graphics.text(font, Component.literal(selected ? "⌄" : "›"), arrowX,
                textY - 1, active ? CYAN : MUTED, false);
            if (!hasValue) textEnd = arrowX - 5;
        }
        graphics.text(font, Component.literal(fit(font, label, textEnd - x - 9)),
            x + 8, textY, active ? selected ? CYAN : TEXT : MUTED, false);
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
