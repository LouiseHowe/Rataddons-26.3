package me.rattokens.rataddon.feature;

import com.mojang.blaze3d.platform.NativeImage;
import me.rattokens.rataddon.RaTAddonClient;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.hud.VanillaHudElements;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;

/** Displays the supplied RatTokens image when a registered player joins Hypixel. */
public final class HypixelJoinBanner {
	private static final long DURATION_MS = 5200L;
	private static long startedAt = -DURATION_MS;
	private static String joinedName = "";
	private static String joinedUuid = "";
	private static Identifier texture;
	private static Identifier remoteTexture;
	private static int imageWidth;
	private static int imageHeight;

	private HypixelJoinBanner() {}

	public static void init() {
		HudElementRegistry.attachElementBefore(VanillaHudElements.CHAT,
			Identifier.fromNamespaceAndPath(RaTAddonClient.MOD_ID, "hypixel_join_banner"),
			(graphics, deltaTracker) -> render(graphics));
	}

	/** Reserve the latest join event; nothing is rendered until its server image is ready. */
	public static void prepare(String playerName, String playerUuid) {
		clearRemoteTexture(Minecraft.getInstance());
		texture = null;
		joinedName = playerName;
		joinedUuid = playerUuid;
		startedAt = -DURATION_MS;
	}

	/** Called on the client thread after the PNG has been downloaded. */
	public static void show(String playerName, String playerUuid, byte[] png) {
		Minecraft client = Minecraft.getInstance();
		if (!joinedUuid.equals(playerUuid) || !joinedName.equals(playerName)) return;
		NativeImage image = null;
		try {
			if (!validPngHeader(png)) throw new IllegalArgumentException("Invalid banner header");
			image = NativeImage.read(png);
			int width = image.getWidth();
			int height = image.getHeight();
			if (width < 1 || height < 1 || width > 4096 || height > 4096
				|| (long) width * height > 16_000_000L) throw new IllegalArgumentException("Invalid banner dimensions");
			Identifier id = Identifier.fromNamespaceAndPath(RaTAddonClient.MOD_ID,
				"join_banners/" + playerUuid.replace("-", ""));
			clearRemoteTexture(client);
			NativeImage upload = image;
			client.getTextureManager().register(id, new DynamicTexture(() -> "RatAddons join banner", upload));
			remoteTexture = id;
			texture = id;
			imageWidth = width;
			imageHeight = height;
			startedAt = System.currentTimeMillis();
			image = null; // TextureManager now owns the image.
		} catch (Exception exception) {
			if (image != null) image.close();
			RaTAddonClient.LOGGER.warn("Invalid remote join banner for {}: {}", playerUuid, exception.toString());
		}
	}

	private static boolean validPngHeader(byte[] png) {
		if (png == null || png.length < 24 || png.length > 4 * 1024 * 1024) return false;
		byte[] signature = {(byte) 0x89, 'P', 'N', 'G', 13, 10, 26, 10};
		for (int i = 0; i < signature.length; i++) if (png[i] != signature[i]) return false;
		if (png[12] != 'I' || png[13] != 'H' || png[14] != 'D' || png[15] != 'R') return false;
		long width = ((png[16] & 255L) << 24) | ((png[17] & 255L) << 16)
			| ((png[18] & 255L) << 8) | (png[19] & 255L);
		long height = ((png[20] & 255L) << 24) | ((png[21] & 255L) << 16)
			| ((png[22] & 255L) << 8) | (png[23] & 255L);
		return width > 0 && width <= 4096 && height > 0 && height <= 4096
			&& width * height <= 16_000_000L;
	}

	private static void clearRemoteTexture(Minecraft client) {
		if (remoteTexture != null) {
			client.getTextureManager().release(remoteTexture);
			remoteTexture = null;
		}
	}

	private static void render(GuiGraphicsExtractor graphics) {
		Minecraft client = Minecraft.getInstance();
		long age = System.currentTimeMillis() - startedAt;
		if (client.player == null || texture == null || age < 0 || age > DURATION_MS
			|| client.gui.screen() instanceof HudEditorScreen) return;
		int width = client.getWindow().getGuiScaledWidth();
		int height = client.getWindow().getGuiScaledHeight();
		int maxWidth = Math.max(1, Math.min(width - 28, 160));
		int maxHeight = Math.max(1, Math.min(height / 3, 100));
		int bannerWidth = Math.max(1, Math.min(maxWidth, (int) ((long) maxHeight * imageWidth / imageHeight)));
		int bannerHeight = Math.max(1, (int) ((long) bannerWidth * imageHeight / imageWidth));
		int x = (width - bannerWidth) / 2;
		int y = 8;
		graphics.blit(RenderPipelines.GUI_TEXTURED, texture, x, y, 0.0F, 0.0F,
			bannerWidth, bannerHeight, imageWidth, imageHeight, imageWidth, imageHeight);
		Component label = Component.literal(joinedName + " joined Hypixel");
		graphics.text(client.font, label, (width - client.font.width(label)) / 2,
			y + bannerHeight + 2, 0xFFFFFFFF, true);
	}
}
