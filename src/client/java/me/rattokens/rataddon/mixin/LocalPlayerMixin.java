package me.rattokens.rataddon.mixin;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import me.rattokens.rataddon.feature.DianaFeet;
import me.rattokens.rataddon.feature.KittyKuudraFixes;
import me.rattokens.rataddon.feature.ClickThroughPlayers;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;

import java.util.function.Predicate;

@Mixin(LocalPlayer.class)
public abstract class LocalPlayerMixin {
	@ModifyExpressionValue(
		method = "raycastHitResult",
		at = @At(value = "FIELD", target = "Lnet/minecraft/world/entity/EntitySelector;CAN_BE_PICKED:Ljava/util/function/Predicate;")
	)
	private Predicate<Entity> rataddon$filterAttackRangePick(Predicate<Entity> original) {
		return rataddon$filterPickableEntities(original);
	}

	@ModifyExpressionValue(
		method = "pick",
		at = @At(value = "FIELD", target = "Lnet/minecraft/world/entity/EntitySelector;CAN_BE_PICKED:Ljava/util/function/Predicate;")
	)
	private static Predicate<Entity> rataddon$filterStandardPick(Predicate<Entity> original) {
		return rataddon$filterPickableEntities(original);
	}

	@Unique
	private static Predicate<Entity> rataddon$filterPickableEntities(Predicate<Entity> original) {
		return ClickThroughPlayers.filter(entity -> original.test(entity) && !KittyKuudraFixes.skipTarget(entity)
			&& !(entity instanceof LivingEntity living && DianaFeet.shouldSkip(living)));
	}
}
