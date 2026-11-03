package me.rattokens.rataddon.mixin;

import me.rattokens.rataddon.feature.PlayerAlphaRenderState;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;

@Mixin(AvatarRenderState.class)
public class AvatarRenderStateMixin implements PlayerAlphaRenderState {
	@Unique private int rataddon$playerAlpha = 255;

	@Override
	public int rataddon$getPlayerAlpha() { return rataddon$playerAlpha; }

	@Override
	public void rataddon$setPlayerAlpha(int alpha) { rataddon$playerAlpha = alpha; }
}
