package me.rattokens.rataddon.feature;

import me.rattokens.rataddon.RaTAddonClient;
import me.rattokens.rataddon.config.RaTAddonConfig;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.hud.VanillaHudElements;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.animal.wolf.WolfSoundVariants;

import java.util.Locale;

/** Ragnarock Axe duration, triggered by the server's classic wolf death sound. */
public final class RagTimer {
	private static final int TOTAL_TICKS = 200;
	private static final SoundEvent WOLF_DEATH = SoundEvents.WOLF_SOUNDS
		.get(WolfSoundVariants.SoundSet.CLASSIC).adultSounds().deathSound().value();
	private static final SoundEvent OLD_RAG_SOUND = SoundEvent.createVariableRangeEvent(
		Identifier.fromNamespaceAndPath(RaTAddonClient.MOD_ID, "wolf-howl"));
	private static int ticks;

	private RagTimer() {}

	public static void init() {
		HudElementRegistry.attachElementBefore(VanillaHudElements.CHAT,
			Identifier.fromNamespaceAndPath(RaTAddonClient.MOD_ID, "rag_timer"),
			(graphics, deltaTracker) -> render(graphics));
	}

	/** Return true when the original packet sound should be cancelled. */
	public static boolean onSound(SoundEvent sound, float volume, float pitch) {
		RaTAddonConfig config = RaTAddonClient.CONFIG;
		if (!config.ragTimer && !config.ragOldSound && !config.ragCustomSound) return false;
		if (sound != WOLF_DEATH) return false;
		// Match Blade Addons' predicate: either the volume or pitch may match.
		if (volume != 1.0F && pitch != 1.4920635223388672F) return false;
		Minecraft client = Minecraft.getInstance();
		if (client.player == null || !client.player.getMainHandItem().getHoverName().getString().contains("Ragnarock"))
			return false;

		if (config.ragTimer) ticks = TOTAL_TICKS;
		if (config.ragOldSound) {
			client.player.playSound(OLD_RAG_SOUND, volume, pitch);
			return true;
		}
		if (config.ragCustomSound) {
			Identifier id = Identifier.tryParse(config.ragCustomSoundId);
			if (id == null) return false;
			client.player.playSound(SoundEvent.createVariableRangeEvent(id),
				(float) config.ragCustomSoundVolume, (float) config.ragCustomSoundPitch);
			return true;
		}
		return false;
	}

	public static void serverTick() {
		ticks = Math.max(0, ticks - 1);
	}

	private static void render(GuiGraphicsExtractor graphics) {
		Minecraft client = Minecraft.getInstance();
		if (!RaTAddonClient.CONFIG.ragTimer || ticks == 0 || client.player == null || client.level == null
			|| client.gui.screen() instanceof HudEditorScreen || KittyHud.Slot.RAG_TIMER.setting().hide) return;
		String text = String.format(Locale.ROOT, "%.2f", ticks * 0.05);
		KittyHud.draw(graphics, client.font, KittyHud.Slot.RAG_TIMER, Component.literal(text),
			client.getWindow().getGuiScaledWidth(), client.getWindow().getGuiScaledHeight(), 0xFFFFFF55);
	}
}
