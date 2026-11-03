package me.rattokens.rataddon.mixin;

import com.llamalad7.mixinextras.sugar.Local;
import me.rattokens.rataddon.feature.PlayerAlphaContext;
import net.minecraft.client.renderer.entity.layers.EquipmentLayerRenderer;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.resources.Identifier;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArgs;
import org.spongepowered.asm.mixin.injection.invoke.arg.Args;

@Mixin(EquipmentLayerRenderer.class)
public class EquipmentLayerRendererAlphaMixin {
	private static final String METHOD = "renderLayers(Lnet/minecraft/client/resources/model/EquipmentClientInfo$LayerType;Lnet/minecraft/resources/ResourceKey;Lnet/minecraft/client/model/Model;Ljava/lang/Object;Lnet/minecraft/world/item/ItemStack;Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/SubmitNodeCollector;ILnet/minecraft/resources/Identifier;II)V";
	private static final String TARGET = "Lnet/minecraft/client/renderer/OrderedSubmitNodeCollector;submitModel(Lnet/minecraft/client/model/Model;Ljava/lang/Object;Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/rendertype/RenderType;IIILnet/minecraft/client/renderer/texture/UvMapping;I)V";

	@ModifyArgs(method = METHOD, at = @At(value = "INVOKE", target = TARGET, ordinal = 0))
	private void rataddon$fadeArmor(Args args, @Local(ordinal = 1) Identifier texture) {
		if (PlayerAlphaContext.get() == 255) return;
		args.set(3, RenderTypes.entityTranslucent(texture));
		fade(args);
	}

	@ModifyArgs(method = METHOD, at = @At(value = "INVOKE", target = TARGET, ordinal = 1))
	private void rataddon$fadeArmorGlint(Args args) { fade(args); }

	@ModifyArgs(method = METHOD, at = @At(value = "INVOKE", target = TARGET, ordinal = 2))
	private void rataddon$fadeArmorTrim(Args args) { fade(args); }

	private static void fade(Args args) {
		int alpha = PlayerAlphaContext.get();
		if (alpha == 255) return;
		int color = args.get(6);
		args.set(6, (color & 0x00FFFFFF) | ((color >>> 24) * alpha / 255 << 24));
	}
}
