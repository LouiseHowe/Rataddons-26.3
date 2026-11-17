package me.rattokens.rataddon.mixin;

import me.rattokens.rataddon.feature.KittyStun;
import me.rattokens.rataddon.feature.GemstoneNuker;
import net.minecraft.client.MouseHandler;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(MouseHandler.class)
public abstract class MouseHandlerAimAssistMixin {
    @Shadow private double accumulatedDX;
    @Shadow private double accumulatedDY;

    @Inject(method = "turnPlayer", at = @At("HEAD"))
    private void rataddon$kittyAimAssist(double frameTime, CallbackInfo ci) {
        double[] adjusted = KittyStun.onTurn(accumulatedDX, accumulatedDY);
        if (adjusted != null) { accumulatedDX = adjusted[0]; accumulatedDY = adjusted[1]; }
        double[] gemstone = GemstoneNuker.onTurn(frameTime);
        if (gemstone != null) {
            accumulatedDX += gemstone[0];
            accumulatedDY += gemstone[1];
        }
    }
}
