package me.rattokens.rataddon.mixin;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;

import me.rattokens.rataddon.feature.RendMacro;
import me.rattokens.rataddon.feature.Storm;
import me.rattokens.rataddon.feature.KittyKuudraFixes;
import me.rattokens.rataddon.feature.DianaMobHits;
import me.rattokens.rataddon.feature.PingOffsetMiner;
import net.minecraft.world.entity.Entity;
import me.rattokens.rataddon.util.BoneTracker;
import net.minecraft.client.multiplayer.MultiPlayerGameMode;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.BlockHitResult;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(MultiPlayerGameMode.class)
public class MultiPlayerGameModeMixin {
	@Inject(method = "startDestroyBlock", at = @At("HEAD"))
	private void rataddon$pomStartBreak(BlockPos pos, Direction direction, CallbackInfoReturnable<Boolean> cir) {
		PingOffsetMiner.startBreak(pos);
	}

	@Inject(method = "attack", at = @At("HEAD"))
	private void rataddon$dianaMobAttack(Player player, Entity target, CallbackInfo ci) {
		DianaMobHits.onLocalAttack(player, target);
	}
	@Inject(
		method = "useItem(Lnet/minecraft/world/entity/player/Player;Lnet/minecraft/world/InteractionHand;)Lnet/minecraft/world/InteractionResult;",
		at = @At("HEAD"),
		cancellable = true
	)
	private void rataddon$kuudraBeforeUseItem(Player player, InteractionHand hand, CallbackInfoReturnable<InteractionResult> cir) {
		if (KittyKuudraFixes.cancelUse(player)) {
			cir.setReturnValue(InteractionResult.PASS);
		}
	}

	@Inject(
		method = "useItemOn(Lnet/minecraft/client/player/LocalPlayer;Lnet/minecraft/world/InteractionHand;Lnet/minecraft/world/phys/BlockHitResult;)Lnet/minecraft/world/InteractionResult;",
		at = @At("HEAD"), cancellable = true
	)
	private void rataddon$kittyKuudraFixes(LocalPlayer player, InteractionHand hand,
		net.minecraft.world.phys.BlockHitResult hit, CallbackInfoReturnable<InteractionResult> cir) {
		if (KittyKuudraFixes.cancelPlacement(player) || KittyKuudraFixes.cancelPearlInteract(player)) {
			cir.setReturnValue(InteractionResult.PASS);
		}
	}

	@Inject(
		method = "useItem(Lnet/minecraft/world/entity/player/Player;Lnet/minecraft/world/InteractionHand;)Lnet/minecraft/world/InteractionResult;",
		at = @At("RETURN")
	)
	private void rataddon$useItem(Player player, InteractionHand hand, CallbackInfoReturnable<InteractionResult> cir) {
		Storm.useItem(player, hand, cir.getReturnValue());
		BoneTracker.useItem(player, hand, cir.getReturnValue());
		RendMacro.useItem(player, hand, cir.getReturnValue());
	}
}
