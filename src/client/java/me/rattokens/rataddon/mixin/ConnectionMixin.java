package me.rattokens.rataddon.mixin;

import io.netty.channel.ChannelHandlerContext;
import me.rattokens.rataddon.feature.ChatMacros;
import me.rattokens.rataddon.feature.DungeonSecrets;
import me.rattokens.rataddon.feature.KittyRelics;
import me.rattokens.rataddon.feature.KittyStun;
import me.rattokens.rataddon.feature.ShaftAnnounce;
import me.rattokens.rataddon.feature.Storm;
import me.rattokens.rataddon.feature.PingOffsetMiner;
import me.rattokens.rataddon.feature.RagTimer;
import me.rattokens.rataddon.util.KuudraState;
import me.rattokens.rataddon.util.TickScheduler;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.common.ClientboundPingPacket;
import net.minecraft.network.protocol.game.ClientboundLevelParticlesPacket;
import net.minecraft.network.protocol.game.ClientboundSystemChatPacket;
import net.minecraft.network.protocol.game.ServerboundUseItemOnPacket;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Connection.class)
public class ConnectionMixin {
	@Inject(method = "send(Lnet/minecraft/network/protocol/Packet;)V", at = @At("HEAD"))
	private void rataddon$secretBlockClick(Packet<?> packet, CallbackInfo ci) {
		if (packet instanceof ServerboundUseItemOnPacket click) DungeonSecrets.onBlockClick(click.hitResult());
	}

	@Inject(
		method = "channelRead0(Lio/netty/channel/ChannelHandlerContext;Lnet/minecraft/network/protocol/Packet;)V",
		at = @At("HEAD")
	)
	private void rataddon$serverTick(ChannelHandlerContext context, Packet<?> packet, CallbackInfo ci) {
		if (packet instanceof ClientboundSystemChatPacket chat) {
			String message = ChatFormatting.stripFormatting(chat.content().getString());
			if (message != null) Minecraft.getInstance().execute(() -> {
				ChatMacros.handleChat(message);
				DungeonSecrets.onChat(message);
				ShaftAnnounce.handleChat(message);
				Storm.handleChat(message);
                KittyRelics.handleChat(message);
                KittyStun.handleChat(message);
				KuudraState.handleChat(message);
			});
		}
		if (packet instanceof ClientboundPingPacket ping && ping.getId() != 0) {
			Minecraft.getInstance().execute(RagTimer::serverTick);
			PingOffsetMiner.serverTick(ping.getId());
			Storm.serverTick();
			TickScheduler.tickServer();
		}
		if (packet instanceof ClientboundLevelParticlesPacket particles) {
			Minecraft.getInstance().execute(() -> PingOffsetMiner.onParticle(particles));
		}
	}
}
