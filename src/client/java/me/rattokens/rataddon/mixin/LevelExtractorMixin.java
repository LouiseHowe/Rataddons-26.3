package me.rattokens.rataddon.mixin;

import com.mojang.blaze3d.vertex.PoseStack;
import me.rattokens.rataddon.RaTAddonClient;
import me.rattokens.rataddon.feature.ArmorStandUtils;
import me.rattokens.rataddon.feature.ClickThroughPlayers;
import me.rattokens.rataddon.feature.CorpseEsp;
import me.rattokens.rataddon.util.KuudraState;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.client.renderer.entity.EntityRenderDispatcher;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(EntityRenderDispatcher.class)
public abstract class LevelExtractorMixin {
	@Inject(method = "shouldRender", at = @At("HEAD"), cancellable = true)
	private void rataddon$filterEntities(Entity entity, Frustum frustum,
											 double cameraX, double cameraY, double cameraZ, float partialTick,
											 CallbackInfoReturnable<Boolean> cir) {
		if (entity instanceof Player && ClickThroughPlayers.alpha(entity) <= 0) {
			cir.setReturnValue(false);
		}
	}

	@Inject(method = "extractEntity", at = @At("RETURN"))
	private void rataddon$shortenDamageNumber(Entity entity, float partialTick,
											  CallbackInfoReturnable<EntityRenderState> cir) {
		EntityRenderState state = cir.getReturnValue();
		ArmorStandUtils.shortenDamageNumber(entity, state);
		int corpseColor = CorpseEsp.outlineColor(entity);
		if (state != null && corpseColor != 0) state.outlineColor = corpseColor;
	}
}
