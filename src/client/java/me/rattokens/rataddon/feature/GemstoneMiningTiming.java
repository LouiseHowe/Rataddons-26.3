package me.rattokens.rataddon.feature;

import net.minecraft.world.item.DyeColor;
import net.minecraft.world.level.block.StainedGlassBlock;
import net.minecraft.world.level.block.StainedGlassPaneBlock;
import net.minecraft.world.level.block.state.BlockState;

/** Gemstone strengths and the tick formula used by MelodySky's Gemstone Nuker. */
final class GemstoneMiningTiming {
    private GemstoneMiningTiming() {}

    static int strength(BlockState state) {
        DyeColor color;
        if (state.getBlock() instanceof StainedGlassBlock glass) color = glass.getColor();
        else if (state.getBlock() instanceof StainedGlassPaneBlock pane) color = pane.getColor();
        else return 0;
        return switch (color) {
            case RED -> 2300;         // Ruby
            case YELLOW -> 3800;      // Topaz
            case MAGENTA -> 4800;     // Jasper
            case BLUE, BROWN, BLACK, GREEN -> 5200;
            case ORANGE, LIGHT_BLUE, PURPLE, LIME, WHITE -> 3000;
            default -> 0;
        };
    }

    static int predictedTicks(BlockState state, double miningSpeed) {
        int strength = strength(state);
        if (strength == 0 || miningSpeed <= 0 || !Double.isFinite(miningSpeed)) return -1;
        int ticks = Math.round((float) (30 * strength) / (float) miningSpeed);
        return ticks >= 1 && ticks < 4 ? 4 : ticks;
    }
}
