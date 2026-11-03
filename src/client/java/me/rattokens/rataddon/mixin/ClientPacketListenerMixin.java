package me.rattokens.rataddon.mixin;

import com.llamalad7.mixinextras.sugar.Local;
import me.rattokens.rataddon.RaTAddonClient;
import me.rattokens.rataddon.feature.AutoFish;
import me.rattokens.rataddon.feature.DianaMobHits;
import me.rattokens.rataddon.feature.IceSprayTracker;
import me.rattokens.rataddon.feature.RendMacro;
import me.rattokens.rataddon.feature.Storm;
import me.rattokens.rataddon.util.LocationManager;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.protocol.game.ClientboundAddEntityPacket;
import net.minecraft.network.protocol.game.ClientboundAnimatePacket;
import net.minecraft.network.protocol.game.ClientboundDamageEventPacket;
import net.minecraft.network.protocol.game.ClientboundEntityEventPacket;
import net.minecraft.network.protocol.game.ClientboundRemoveEntitiesPacket;
import net.minecraft.network.protocol.game.ClientboundLevelParticlesPacket;
import net.minecraft.network.protocol.game.ClientboundOpenScreenPacket;
import net.minecraft.network.protocol.game.ClientboundPlayerPositionPacket;
import net.minecraft.network.protocol.game.ClientboundSetEntityDataPacket;
import net.minecraft.network.protocol.game.ClientboundSetTitleTextPacket;
import net.minecraft.network.protocol.game.ClientboundContainerSetSlotPacket;
import net.minecraft.network.protocol.game.ClientboundPlayerInfoUpdatePacket;
import net.minecraft.network.protocol.game.ClientboundSetObjectivePacket;
import net.minecraft.network.protocol.game.ClientboundSetPlayerTeamPacket;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.decoration.ArmorStand;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ClientPacketListener.class)
public abstract class ClientPacketListenerMixin {
	@Inject(method = "handlePlayerInfoUpdate", at = @At("TAIL"))
	private void rataddon$locationPlayerInfo(ClientboundPlayerInfoUpdatePacket packet, CallbackInfo ci) {
		LocationManager.handlePlayerInfo(packet);
	}

	@Inject(method = "handleAddObjective", at = @At("TAIL"))
	private void rataddon$locationObjective(ClientboundSetObjectivePacket packet, CallbackInfo ci) {
		LocationManager.handleObjective(packet);
	}

	@Inject(method = "handleSetPlayerTeamPacket", at = @At("TAIL"))
	private void rataddon$locationPlayerTeam(ClientboundSetPlayerTeamPacket packet, CallbackInfo ci) {
		LocationManager.handlePlayerTeam(packet);
	}

	@Inject(method = "handleAddEntity", at = @At("TAIL"))
	private void rataddon$iceSprayItem(ClientboundAddEntityPacket packet, CallbackInfo ci) {
		IceSprayTracker.onAddEntity(packet);
		if (Minecraft.getInstance().level != null)
			DianaMobHits.onEntityChanged(Minecraft.getInstance().level.getEntity(packet.getId()));
	}
	@Inject(method = "handleRemoveEntities", at = @At("TAIL"))
	private void rataddon$dianaRemove(ClientboundRemoveEntitiesPacket packet, CallbackInfo ci) {
		DianaMobHits.onEntitiesRemoved(packet.entityIds());
	}
	@Inject(method = "handleAnimate", at = @At("TAIL"))
	private void rataddon$dianaAnimate(ClientboundAnimatePacket packet, CallbackInfo ci) {
		DianaMobHits.onAnimate(packet);
	}
	@Inject(method = "handleDamageEvent", at = @At("TAIL"))
	private void rataddon$dianaDamage(ClientboundDamageEventPacket packet, CallbackInfo ci) {
		DianaMobHits.onDamage(packet);
	}
	@Inject(method = "handleEntityEvent", at = @At("TAIL"))
	private void rataddon$dianaEvent(ClientboundEntityEventPacket packet, CallbackInfo ci) {
		DianaMobHits.onEntityEvent(packet);
	}
	@Inject(method = "handleOpenScreen(Lnet/minecraft/network/protocol/game/ClientboundOpenScreenPacket;)V", at = @At("HEAD"))
	private void rataddon$handleOpenScreen(ClientboundOpenScreenPacket packet, CallbackInfo ci) {
		Storm.handleScreen(packet);
		RendMacro.openScreen(packet);
	}

	@Inject(method = "handleMovePlayer(Lnet/minecraft/network/protocol/game/ClientboundPlayerPositionPacket;)V", at = @At("TAIL"))
	private void rataddon$handleMovePlayer(ClientboundPlayerPositionPacket packet, CallbackInfo ci) {
		RendMacro.onPositionChange(packet);
	}

	@Inject(
		method = "handleParticleEvent",
		at = @At(
			value = "INVOKE",
			target = "Lnet/minecraft/network/protocol/PacketUtils;ensureRunningOnSameThread(Lnet/minecraft/network/protocol/Packet;Lnet/minecraft/network/PacketListener;Lnet/minecraft/network/PacketProcessor;)V",
			shift = At.Shift.AFTER
		),
		cancellable = true
	)
	private void rataddon$hideIceSprayParticles(ClientboundLevelParticlesPacket packet, CallbackInfo ci) {
		if (RaTAddonClient.CONFIG.hideIceSprayParticles
			&& packet.particle().getType().equals(ParticleTypes.POOF)
			&& packet.count() == 3
			&& packet.xMaxSpeed() == 0.0F && packet.yMaxSpeed() == 0.0F && packet.zMaxSpeed() == 0.0F
			&& packet.xDist() == 0.0F
			&& packet.yDist() == 0.0F
			&& packet.zDist() == 0.0F) {
			ci.cancel();
		}
	}

	@Inject(method = "handleSetEntityData", at = @At("TAIL"))
	private void rataddon$handleEntityData(ClientboundSetEntityDataPacket packet, CallbackInfo ci,
										  @Local Entity entity) {
		DianaMobHits.onEntityChanged(entity);
		if (entity instanceof ArmorStand armorStand) {
			AutoFish.fishBaitCheck(armorStand);
			AutoFish.puddleJumperCheck(armorStand);
		}
	}
}
