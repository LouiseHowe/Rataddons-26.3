package me.rattokens.rataddon.feature;

import me.rattokens.rataddon.RaTAddonClient;
import me.rattokens.rataddon.util.ColorOptions;
import me.rattokens.rataddon.util.KuudraState;
import me.rattokens.rataddon.util.WorldRender;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderEvents;
import net.minecraft.world.entity.Entity;

/** Draws only the configurable Kuudra hitbox. */
public final class KuudraExtras {
	private KuudraExtras() {}

	public static void init() {
		LevelRenderEvents.END_MAIN.register(context -> {
			if (!RaTAddonClient.CONFIG.kuudraHitbox || !KuudraState.kuudra()) return;
			Entity kuudra = KuudraState.kuudraEntity;
			if (kuudra != null && kuudra.isAlive()) {
				WorldRender.box(context, kuudra.getBoundingBox().inflate(0.05),
					ColorOptions.argb(RaTAddonClient.CONFIG.kuudraHitboxColor),
					RaTAddonClient.CONFIG.kuudraHitboxWidth, true);
			}
		});
	}
}
