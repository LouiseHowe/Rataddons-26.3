package me.rattokens.rataddon.mixin;

import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import me.rattokens.rataddon.feature.PlayerAlphaContext;
import net.minecraft.client.renderer.blockentity.SkullBlockRenderer;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.block.SkullBlock;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArgs;
import org.spongepowered.asm.mixin.injection.invoke.arg.Args;

import java.util.Map;

@Mixin(SkullBlockRenderer.class)
public class SkullBlockRendererAlphaMixin {
	@Shadow @Final private static Map<SkullBlock.Type, Identifier> SKIN_BY_TYPE;

	@ModifyReturnValue(method = "getSkullRenderType", at = @At("RETURN"))
	private static RenderType rataddon$translucentWornSkull(RenderType original, SkullBlock.Type type,
			Identifier textureOverride) {
		if (PlayerAlphaContext.get() == 255) return original;
		Identifier texture = textureOverride != null ? textureOverride : SKIN_BY_TYPE.get(type);
		return texture != null ? RenderTypes.entityTranslucent(texture) : original;
	}
	@ModifyArgs(
		method = "submitSkull(FLcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/SubmitNodeCollector;ILnet/minecraft/client/model/object/skull/SkullModelBase;Lnet/minecraft/client/renderer/rendertype/RenderType;ILnet/minecraft/client/renderer/feature/ModelFeatureRenderer$CrumblingOverlay;)V",
		at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/SubmitNodeCollector;submitModel(Lnet/minecraft/client/model/Model;Ljava/lang/Object;Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/rendertype/RenderType;III)V")
	)
	private static void rataddon$fadeWornSkull(Args args) {
		int alpha = PlayerAlphaContext.get();
		if (alpha == 255) return;
		int color = args.get(6);
		if (color == 0) color = -1;
		args.set(6, (color & 0x00FFFFFF) | ((color >>> 24) * alpha / 255 << 24));
	}
}
