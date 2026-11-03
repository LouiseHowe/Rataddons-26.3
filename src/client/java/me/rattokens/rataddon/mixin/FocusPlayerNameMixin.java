package me.rattokens.rataddon.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import me.rattokens.rataddon.RaTAddonClient;
import me.rattokens.rataddon.util.AntiBotUtils;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(LivingEntityRenderer.class)
public class FocusPlayerNameMixin {
	@WrapOperation(method = "shouldShowName(Lnet/minecraft/world/entity/LivingEntity;D)Z",
		at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/LivingEntity;isInvisibleTo(Lnet/minecraft/world/entity/player/Player;)Z"))
	private boolean rataddon$focusPlayerName(LivingEntity entity, Player viewer, Operation<Boolean> original) {
		// Match AvatarRenderer's body visibility without bypassing distance/team name rules.
		return !(RaTAddonClient.CONFIG.focusPlayer && entity instanceof Player player && !AntiBotUtils.isBot(player))
			&& original.call(entity, viewer);
	}
}
