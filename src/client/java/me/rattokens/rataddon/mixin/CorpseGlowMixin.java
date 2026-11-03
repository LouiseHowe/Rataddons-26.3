package me.rattokens.rataddon.mixin;

import me.rattokens.rataddon.feature.CorpseEsp;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(Entity.class)
public abstract class CorpseGlowMixin {
	@Inject(method = "isCurrentlyGlowing", at = @At("HEAD"), cancellable = true)
	private void rataddon$corpseGlowing(CallbackInfoReturnable<Boolean> cir) {
		if (CorpseEsp.glowing((Entity) (Object) this)) cir.setReturnValue(true);
	}
}
