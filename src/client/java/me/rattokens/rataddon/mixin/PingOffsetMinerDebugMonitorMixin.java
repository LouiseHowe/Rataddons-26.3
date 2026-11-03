package me.rattokens.rataddon.mixin;

import me.rattokens.rataddon.feature.PingOffsetMiner;
import net.minecraft.client.multiplayer.PingDebugMonitor;
import net.minecraft.network.protocol.ping.ClientboundPongResponsePacket;
import net.minecraft.util.Util;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(PingDebugMonitor.class)
public abstract class PingOffsetMinerDebugMonitorMixin {
	@Inject(method = "onPongReceived", at = @At("TAIL"))
	private void rataddon$recordPingRtt(ClientboundPongResponsePacket packet, CallbackInfo ci) {
		PingOffsetMiner.onPingRtt(Util.getMillis() - packet.time());
	}
}
