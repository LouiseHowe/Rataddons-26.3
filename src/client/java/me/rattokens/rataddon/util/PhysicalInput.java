package me.rattokens.rataddon.util;

import com.mojang.blaze3d.platform.InputConstants;
import org.lwjgl.sdl.SDLMouse;

/** Reads physical SDL input without relying on a KeyMapping's synthetic state. */
public final class PhysicalInput {
	private PhysicalInput() {}

	public static boolean isDown(InputConstants.Key key) {
		int value = key.getValue();
		if (value <= 0) return false;
		return switch (key.getType()) {
			case MOUSE -> value <= 32 && (SDLMouse.SDL_GetMouseState(null, null) & (1 << (value - 1))) != 0;
			case KEYBOARD -> InputConstants.isKeyDown(value);
		};
	}
}
