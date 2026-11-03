package me.rattokens.rataddon.mixin;

import me.rattokens.rataddon.feature.Storm;
import me.rattokens.rataddon.feature.PlayerAlphaContext;
import net.minecraft.client.renderer.item.ItemStackRenderState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;

@Mixin(ItemStackRenderState.LayerRenderState.class)
public class LayerRenderStateMixin {
	@ModifyArg(
		method = "submit(Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/SubmitNodeCollector;III)V",
		at = @At(
			value = "INVOKE",
			target = "Lnet/minecraft/client/renderer/SubmitNodeCollector;submitItem(Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/world/item/ItemDisplayContext;III[ILnet/minecraft/client/resources/model/geometry/ItemQuads;Lnet/minecraft/client/renderer/item/ItemStackRenderState$FoilType;)V"
		),
		index = 5
	)
	private int[] rataddon$tintHeldItemNamed(int[] tints) {
		return tintHeldItem(tints);
	}

	private int[] tintHeldItem(int[] tints) {
		int alpha = PlayerAlphaContext.get();
		if (!Storm.tintBow() && alpha == 255) {
			return tints;
		}
		int n = Storm.tintBow() ? Math.max(tints.length, 8) : tints.length;
		int[] out = new int[n];
		for (int i = 0; i < n; i++) {
			int argb = i < tints.length ? tints[i] : -1;
			if (Storm.tintBow()) argb = Storm.tintArgb(argb);
			out[i] = (argb & 0x00FFFFFF) | ((argb >>> 24) * alpha / 255 << 24);
		}
		return out;
	}
}
