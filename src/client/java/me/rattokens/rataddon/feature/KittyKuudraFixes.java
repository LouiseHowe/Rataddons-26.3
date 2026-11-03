package me.rattokens.rataddon.feature;

import me.rattokens.rataddon.RaTAddonClient;
import me.rattokens.rataddon.util.KuudraState;
import me.rattokens.rataddon.util.SkyblockItem;
import net.minecraft.client.Minecraft;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.monster.Giant;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.HitResult;

public final class KittyKuudraFixes {
	private KittyKuudraFixes() {}

	public static boolean skipTarget(Entity target) {
		if (!RaTAddonClient.CONFIG.kittyKuudraFixes || !KuudraState.kuudra()) return false;
		if (RaTAddonClient.CONFIG.kittyClickThroughGiants && target instanceof Giant) return true;
		return RaTAddonClient.CONFIG.kittyHollowFix && Minecraft.getInstance().player != null
			&& "HOLLOW_WAND".equals(SkyblockItem.id(Minecraft.getInstance().player.getMainHandItem()));
	}

	public static boolean cancelUse(Player player) {
		if (!RaTAddonClient.CONFIG.kittyKuudraFixes || KuudraState.phase != KuudraState.Phase.SUPPLIES
			|| player.getY() < 79) return false;
		if (RaTAddonClient.CONFIG.kittyNoSkyblockMenu && player.getInventory().getSelectedSlot() == 8) return true;
		if (!RaTAddonClient.CONFIG.kittyCancelLavaWarp) return false;
		String id = SkyblockItem.id(player.getMainHandItem());
		if (!"ETHERWARP_CONDUIT".equals(id)
			&& !("ASPECT_OF_THE_VOID".equals(id) && player.isCrouching())) return false;
		var start = player.getEyePosition();
		var end = start.add(player.getLookAngle().scale(60));
		var hit = player.level().clip(new ClipContext(start, end, ClipContext.Block.OUTLINE,
			ClipContext.Fluid.ANY, player));
		return hit.getType() == HitResult.Type.BLOCK
			&& player.level().getFluidState(((net.minecraft.world.phys.BlockHitResult) hit).getBlockPos()).is(FluidTags.LAVA);
	}

	public static boolean cancelPlacement(Player player) {
		return RaTAddonClient.CONFIG.kittyKuudraFixes && KuudraState.kuudra()
			&& RaTAddonClient.CONFIG.kittyCancelConduitPlacement
			&& "ETHERWARP_CONDUIT".equals(SkyblockItem.id(player.getMainHandItem()));
	}

	public static boolean cancelPearlInteract(Player player) {
		return RaTAddonClient.CONFIG.kittyKuudraFixes && KuudraState.kuudra()
			&& RaTAddonClient.CONFIG.kittyCancelPearlInteract
			&& player.getMainHandItem().is(Items.ENDER_PEARL);
	}
}
