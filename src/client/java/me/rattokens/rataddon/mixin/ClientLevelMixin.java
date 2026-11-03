package me.rattokens.rataddon.mixin;

import me.rattokens.rataddon.util.BoneTracker;
import me.rattokens.rataddon.feature.CorpseEsp;
import me.rattokens.rataddon.feature.WillOWisp;
import me.rattokens.rataddon.util.KuudraState;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ClientLevel.class)
public class ClientLevelMixin {
	@Inject(method = "setTimeFromServer", at = @At("HEAD"))
	private void rataddon$wispServerTime(long gameTime, CallbackInfo ci) {
		WillOWisp.onServerTimeUpdate(gameTime);
	}

	@Inject(method = "addEntity", at = @At("TAIL"))
	private void rataddon$onAddEntity(Entity entity, CallbackInfo ci) {
		BoneTracker.onAddEntity(entity);
		KuudraState.onEntityAdded(entity);
	}

	@Inject(method = "removeEntity", at = @At("HEAD"))
	private void rataddon$kittyBuildRemoved(int id, Entity.RemovalReason reason, CallbackInfo ci) {
		CorpseEsp.onEntityRemoved(id);
	}
}
