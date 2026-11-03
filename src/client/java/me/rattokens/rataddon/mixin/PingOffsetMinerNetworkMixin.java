package me.rattokens.rataddon.mixin;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import net.minecraft.client.multiplayer.ClientPacketListener;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/** Keep the vanilla debug RTT monitor active, as POM does. */
@Mixin(ClientPacketListener.class)
public class PingOffsetMinerNetworkMixin {
	@ModifyExpressionValue(method = "tick", at = @At(value = "INVOKE",
		target = "Lnet/minecraft/client/gui/components/DebugScreenOverlay;showNetworkCharts()Z"))
	private boolean rataddon$pomSendPing(boolean original) {
		return true;
	}
}
