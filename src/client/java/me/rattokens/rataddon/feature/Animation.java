package me.rattokens.rataddon.feature;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import me.rattokens.rataddon.RaTAddonClient;
import me.rattokens.rataddon.config.RaTAddonConfig;
import net.minecraft.client.Minecraft;
import net.minecraft.tags.ItemTags;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Avatar;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ItemUseAnimation;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.HitResult;

public final class Animation {
	public enum SwingMode {
		Vanilla,
		Flux
	}

	public enum BlockMode {
		V1_7,
		Pushdown,
		Scale,
		Leaked,
		Ninja,
		Down
	}

	private Animation() {
	}

	public static void init() {
	}

	private static RaTAddonConfig cfg() {
		return RaTAddonClient.CONFIG;
	}

	private static Minecraft mc() {
		return Minecraft.getInstance();
	}

	public static boolean enabled() {
		return cfg().animationEnabled;
	}

	public static boolean disableSwapMain() {
		return enabled() && cfg().animDisableSwapMain;
	}

	public static boolean disableSwapOff() {
		return enabled() && cfg().animDisableSwapOff;
	}

	public static boolean isBlocking() {
		Minecraft client = mc();
		if (client.player == null) {
			return false;
		}
		return isBlockableWeapon(client.player.getMainHandItem()) && client.options.keyUse.isDown();
	}

	public static boolean shouldApplyBlockingAnimation(InteractionHand hand, ItemStack itemStack) {
		return hand == InteractionHand.MAIN_HAND && canRenderMainHand(itemStack) && isBlocking();
	}

	public static boolean shouldApplyThirdPersonBlockingAnim(Avatar avatar, HumanoidArm arm) {
		Minecraft client = mc();
		if (client.player == null) {
			return false;
		}
		ItemStack itemStack = avatar.getItemInHand(InteractionHand.MAIN_HAND);
		return avatar == client.player && avatar.getMainArm() == arm && canRenderMainHand(itemStack) && isBlocking();
	}

	public static boolean shouldSkipFluxTranslate() {
		if (!enabled() || swingMode() != SwingMode.Flux) {
			return false;
		}
		if (!cfg().animOnlyWeapon) {
			return true;
		}
		Minecraft client = mc();
		if (client.player == null) {
			return false;
		}
		ItemStack item = client.player.getMainHandItem();
		return item.is(ItemTags.SWORDS) || item.is(ItemTags.AXES);
	}

	public static boolean shouldAddSwingWhileUsing(float attack) {
		return enabled() && cfg().animSwingWhileUsing && attack > 0.0F;
	}

	public static boolean shouldSwingOnUseKey() {
		Minecraft client = mc();
		if (!enabled() || !cfg().animSwingWhileUsing || client.player == null || client.hitResult == null) {
			return false;
		}
		if (!client.options.keyAttack.isDown() || !client.options.keyAttack.consumeClick()) {
			return false;
		}
		return !cfg().animOnlyOnBlock || client.hitResult.getType() == HitResult.Type.BLOCK;
	}

	public static boolean modifySwingDuration() {
		return enabled() && cfg().animModifySwingDuration;
	}

	public static int swingDuration() {
		return cfg().animSwingDuration;
	}

	public static SwingMode swingMode() {
		return SwingMode.values()[Math.clamp(cfg().animSwingMode, 0, SwingMode.values().length - 1)];
	}

	public static BlockMode blockMode() {
		return BlockMode.values()[Math.clamp(cfg().animBlockingMode, 0, BlockMode.values().length - 1)];
	}

	private static boolean canRenderMainHand(ItemStack itemStack) {
		return enabled() && cfg().animBlockingAnimation && isBlockableWeapon(itemStack) && !isOffhandBlocking();
	}

	private static boolean isBlockableWeapon(ItemStack itemStack) {
		return !itemStack.isEmpty() && itemStack.is(ItemTags.WEAPON_ENCHANTABLE);
	}

	private static boolean isOffhandBlocking() {
		Minecraft client = mc();
		if (client.player == null || !client.options.keyUse.isDown() || !client.player.getOffhandItem().is(Items.SHIELD)) {
			return false;
		}
		return !client.player.isUsingItem()
			? client.player.getMainHandItem().getUseAnimation() == ItemUseAnimation.NONE
			: client.player.getUsedItemHand() == InteractionHand.OFF_HAND;
	}

	public static void applyBlockingTransform(PoseStack poseStack, HumanoidArm arm, float attack) {
		switch (blockMode()) {
			case V1_7 -> {
				poseStack.translate((arm == HumanoidArm.RIGHT ? -0.1F : 0.1F), 0.05F, 0.0F);
				applyAttackTransform(poseStack, arm, attack, 0.9F);
				applyBlockPose(poseStack, arm, 0.0F, 0.0F, 0.0F, -102.25F, 13.365F, 78.05F);
			}
			case Pushdown -> {
				poseStack.translate((arm == HumanoidArm.RIGHT ? -0.1F : 0.1F), 0.1F, 0.0F);
				float swing = Mth.sin(Mth.sqrt(attack) * (float) Math.PI);
				poseStack.rotateDegrees(Axis.ZP, (arm == HumanoidArm.RIGHT ? 1 : -1) * swing * 10.0F);
				poseStack.rotateDegrees(Axis.XP, swing * -35.0F);
				applyBlockPose(poseStack, arm, 0.0F, 0.0F, 0.0F, -102.25F, 13.365F, 78.05F);
			}
			case Scale -> {
				poseStack.translate((arm == HumanoidArm.RIGHT ? -0.16F : 0.16F), 0.12F, 0.16F);
				poseStack.scale(0.85F, 0.85F, 0.85F);
				applyAttackTransform(poseStack, arm, attack, 0.35F);
				applyBlockPose(poseStack, arm, 0.0F, 0.0F, 0.0F, -100.0F, 18.0F, 72.0F);
			}
			case Leaked -> {
				applyAttackTransform(poseStack, arm, attack, 0.6F);
				applyBlockPose(poseStack, arm, -0.18F, 0.18F, 0.1F, -96.0F, 24.0F, 68.0F);
			}
			case Ninja -> {
				applyAttackTransform(poseStack, arm, attack, 0.25F);
				applyBlockPose(poseStack, arm, -0.05F, 0.22F, 0.2F, -88.0F, 35.0F, 82.0F);
			}
			case Down -> {
				applyAttackTransform(poseStack, arm, attack, 0.2F);
				applyBlockPose(poseStack, arm, -0.25F, -0.02F, 0.05F, -120.0F, 8.0F, 72.0F);
			}
		}
	}

	private static void applyAttackTransform(PoseStack poseStack, HumanoidArm arm, float attack, float scale) {
		int invert = arm == HumanoidArm.RIGHT ? 1 : -1;
		float ySwingRotation = Mth.sin(attack * attack * (float) Math.PI);
		poseStack.rotateDegrees(Axis.YP, invert * (45.0F + ySwingRotation * -20.0F * scale));
		float xzSwingRotation = Mth.sin(Mth.sqrt(attack) * (float) Math.PI);
		poseStack.rotateDegrees(Axis.ZP, invert * xzSwingRotation * -20.0F * scale);
		poseStack.rotateDegrees(Axis.XP, xzSwingRotation * -80.0F * scale);
		poseStack.rotateDegrees(Axis.YP, invert * -45.0F);
	}

	private static void applyBlockPose(PoseStack poseStack, HumanoidArm arm, float translateX, float translateY,
									   float translateZ, float rotateX, float rotateY, float rotateZ) {
		int invert = arm == HumanoidArm.RIGHT ? 1 : -1;
		poseStack.translate(invert * translateX, translateY, translateZ);
		poseStack.rotateDegrees(Axis.XP, rotateX);
		poseStack.rotateDegrees(Axis.YP, invert * rotateY);
		poseStack.rotateDegrees(Axis.ZP, invert * rotateZ);
	}
}
