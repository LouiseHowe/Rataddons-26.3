package me.rattokens.rataddon.config;

import net.minecraft.client.gui.GuiGraphicsExtractor;

final class UiShapes {
    private UiShapes() {}

    static void rounded(GuiGraphicsExtractor g, int x1, int y1, int x2, int y2, int radius, int color) {
        if (x2 <= x1 || y2 <= y1) return;
        int r = Math.min(radius, Math.min((x2 - x1) / 2, (y2 - y1) / 2));
        g.fill(x1 + r, y1, x2 - r, y2, color);
        g.fill(x1, y1 + r, x2, y2 - r, color);
        for (int row = 0; row < r; row++) {
            int side = (int) Math.ceil(r - Math.sqrt(r * r - (r - row - .5) * (r - row - .5)));
            g.fill(x1 + side, y1 + row, x2 - side, y1 + row + 1, color);
            g.fill(x1 + side, y2 - row - 1, x2 - side, y2 - row, color);
        }
    }
}
