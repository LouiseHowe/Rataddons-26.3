package me.rattokens.rataddon.feature;

import me.rattokens.rataddon.util.HumanRotation;

public final class SmoothRotation {
	private static final HumanRotation.Profile PROFILE = HumanRotation.Profile.speeds(90.0F, 160.0F);

	private SmoothRotation() {
	}

	public static void lookAt(float pitch, float yaw, Runnable completed) {
		HumanRotation.lookAt(yaw, pitch, PROFILE, completed);
	}
}
