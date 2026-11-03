// SPDX-License-Identifier: LGPL-3.0-only
package me.rattokens.rataddon.mixin;

import me.rattokens.rataddon.feature.DroppedItemRenderState;
import net.minecraft.client.renderer.entity.state.ItemEntityRenderState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;

@Mixin(ItemEntityRenderState.class)
public class DroppedItemRenderStateMixin implements DroppedItemRenderState {
	@Unique private float rataddon$dropScale = 1.0F;
	@Override public float rataddon$getDropScale() { return rataddon$dropScale; }
	@Override public void rataddon$setDropScale(float scale) { rataddon$dropScale = scale; }
}
