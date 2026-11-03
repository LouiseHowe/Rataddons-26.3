package me.rattokens.rataddon.feature;

import me.rattokens.rataddon.RaTAddonClient;
import me.rattokens.rataddon.util.LocationManager;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.StainedGlassBlock;
import net.minecraft.world.level.block.StainedGlassPaneBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;

/** Quoi's gemstone pane connection repair, restricted to its five mining areas. */
public final class NoGemstoneDesync {
	private NoGemstoneDesync() {}

	public static boolean active() {
		if (RaTAddonClient.CONFIG == null || !RaTAddonClient.CONFIG.noGemstoneDesync) return false;
		return switch (LocationManager.currentArea()) {
			case DWARVEN_MINES, CRYSTAL_HOLLOWS, MINESHAFT, CRIMSON_ISLE, RIFT -> true;
			default -> false;
		};
	}

	public static void onBlockUpdate(BlockPos pos, BlockState oldState, BlockState newState) {
		if (!active() || !newState.isAir() || !(oldState.getBlock() instanceof StainedGlassBlock
			|| oldState.getBlock() instanceof StainedGlassPaneBlock)) return;
		if (Minecraft.getInstance().level != null)
			newState.updateNeighbourShapes(Minecraft.getInstance().level, pos, Block.UPDATE_ALL);
	}

	public static BlockState fillDisconnectedPane(BlockState state) {
		if (!active() || !(state.getBlock() instanceof StainedGlassPaneBlock)
			|| state.getValue(BlockStateProperties.NORTH) || state.getValue(BlockStateProperties.EAST)
			|| state.getValue(BlockStateProperties.SOUTH) || state.getValue(BlockStateProperties.WEST)) return state;
		return state.setValue(BlockStateProperties.NORTH, true)
			.setValue(BlockStateProperties.EAST, true)
			.setValue(BlockStateProperties.SOUTH, true)
			.setValue(BlockStateProperties.WEST, true);
	}
}
