package me.rattokens.rataddon.feature;

import me.rattokens.rataddon.RaTAddonClient;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.hud.VanillaHudElements;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;

/** Large, centered, short-lived notification shared by the SBE-style alerts. */
public final class CenterAlert {
    private static Component message;
    private static long expiresAt;

    private CenterAlert() {}

    public static void init() {
        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> clear());
        HudElementRegistry.attachElementAfter(VanillaHudElements.TITLE_AND_SUBTITLE,
            Identifier.fromNamespaceAndPath(RaTAddonClient.MOD_ID, "center_alert"),
            (graphics, delta) -> render(graphics));
    }

    public static void show(Component text, long durationMs) {
        message = text;
        expiresAt = System.currentTimeMillis() + durationMs;
    }

    private static void clear() {
        message = null;
        expiresAt = 0L;
    }

    private static void render(GuiGraphicsExtractor graphics) {
        Minecraft client = Minecraft.getInstance();
        if (client.player == null || message == null) return;
        if (System.currentTimeMillis() >= expiresAt) {
            clear();
            return;
        }
        int width = client.font.width(message);
        float scale = width == 0 ? 3.5F : Math.min(3.5F,
            (client.getWindow().getGuiScaledWidth() - 20.0F) / width);
        graphics.pose().pushMatrix();
        graphics.pose().translate(client.getWindow().getGuiScaledWidth() / 2.0F,
            client.getWindow().getGuiScaledHeight() / 2.0F);
        graphics.pose().scale(scale, scale);
        graphics.text(client.font, message, -width / 2, -10, 0xFFFFFFFF, true);
        graphics.pose().popMatrix();
    }
}
