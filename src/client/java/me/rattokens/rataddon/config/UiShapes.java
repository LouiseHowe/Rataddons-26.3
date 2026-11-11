package me.rattokens.rataddon.config;

import me.rattokens.rataddon.RaTAddonClient;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.resources.Identifier;

final class UiShapes {
    private static final int CORNER_SIZE = 64;
    private static final Identifier CORNER_TL = texture("corner_tl");
    private static final Identifier CORNER_TR = texture("corner_tr");
    private static final Identifier CORNER_BL = texture("corner_bl");
    private static final Identifier CORNER_BR = texture("corner_br");
    private static final Identifier RADIAL_GLOW = texture("radial_glow");

    private UiShapes() {}

    private static Identifier texture(String name) {
        return Identifier.fromNamespaceAndPath(RaTAddonClient.MOD_ID, "textures/gui/" + name + ".png");
    }

    static void rounded(GuiGraphicsExtractor g, int x1, int y1, int x2, int y2, int radius, int color) {
        if (x2 <= x1 || y2 <= y1) return;
        int r = Math.min(radius, Math.min((x2 - x1) / 2, (y2 - y1) / 2));
        if (r <= 0) {
            g.fill(x1, y1, x2, y2, color);
            return;
        }
        g.fill(x1 + r, y1, x2 - r, y2, color);
        g.fill(x1, y1 + r, x1 + r, y2 - r, color);
        g.fill(x2 - r, y1 + r, x2, y2 - r, color);
        corner(g, CORNER_TL, x1, y1, r, color);
        corner(g, CORNER_TR, x2 - r, y1, r, color);
        corner(g, CORNER_BL, x1, y2 - r, r, color);
        corner(g, CORNER_BR, x2 - r, y2 - r, r, color);
    }

    static void radialGlow(GuiGraphicsExtractor g, int x, int y, int width, int height, int color) {
        g.blit(RenderPipelines.GUI_TEXTURED, RADIAL_GLOW, x, y, 0.0F, 0.0F,
            width, height, 128, 128, 128, 128, color);
    }

    private static void corner(GuiGraphicsExtractor g, Identifier texture, int x, int y, int radius, int color) {
        g.blit(RenderPipelines.GUI_TEXTURED, texture, x, y, 0.0F, 0.0F,
            radius, radius, CORNER_SIZE, CORNER_SIZE, CORNER_SIZE, CORNER_SIZE, color);
    }
}
