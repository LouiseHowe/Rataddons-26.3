package me.rattokens.rataddon.util;

import net.minecraft.client.Minecraft;
import net.minecraft.world.phys.Vec3;

/** Kittycat-style mouse pull toward an in-FOV waypoint. */
public final class KittyAimAssist {
	public record Candidate(float yawDifference, float pitchDifference, double strength, double distance) {}
	private KittyAimAssist() {}

	public static Candidate candidate(Vec3 target, double fov, double strength) {
		Minecraft client = Minecraft.getInstance();
		if (client.player == null) return null;
		Vec3 delta = target.subtract(client.player.getEyePosition());
		float yaw = (float) Math.toDegrees(Math.atan2(-delta.x, delta.z));
		float pitch = (float) Math.toDegrees(Math.atan2(-delta.y, Math.hypot(delta.x, delta.z)));
		return candidate(yaw, pitch, fov, strength);
	}

	public static Candidate candidate(float yaw, float pitch, double fov, double strength) {
		Minecraft client = Minecraft.getInstance();
		if (client.player == null) return null;
		float dyaw = angleDifference(yaw, client.player.getYRot());
		float dpitch = pitch - client.player.getXRot();
		if (!withinFov(yaw, pitch, client.player.getYRot(), client.player.getXRot(), fov)) return null;
		return new Candidate(dyaw, dpitch, strength, Math.hypot(dyaw, dpitch));
	}

	public static boolean withinFov(float yaw, float pitch, float currentYaw, float currentPitch, double fov) {
		Vec3 a = direction(yaw, pitch), b = direction(currentYaw, currentPitch);
		return Math.toDegrees(Math.acos(Math.clamp(a.dot(b), -1, 1))) <= fov / 2 + 1e-6;
	}

	public static double[] adjust(double dx, double dy, Candidate candidate) {
		if (candidate == null || Math.abs(dx) < 0.001 && Math.abs(dy) < 0.001) return null;
		Minecraft client = Minecraft.getInstance();
		double sensitivity = client.options.sensitivity().get();
		double base = sensitivity * 0.6 + 0.2;
		double scale = Math.max(base * base * base * 8.0 * 0.15, 0.0001) / 0.15;
		double neededX = candidate.yawDifference / scale;
		double neededY = candidate.pitchDifference / scale;
		double needed = Math.hypot(neededX, neededY);
		if (needed < 1e-6) return null;
		double pull = Math.min(Math.hypot(dx, dy) * candidate.strength, needed);
		return new double[] {dx * (1 - candidate.strength) + neededX / needed * pull,
			dy * (1 - candidate.strength) + neededY / needed * pull};
	}

	public static float angleDifference(float target, float current) {
		float difference = (target - current) % 360;
		if (difference > 180) difference -= 360;
		if (difference < -180) difference += 360;
		return difference;
	}

	private static Vec3 direction(float yaw, float pitch) {
		double y = Math.toRadians(yaw), p = Math.toRadians(pitch);
		return new Vec3(-Math.sin(y) * Math.cos(p), -Math.sin(p), Math.cos(y) * Math.cos(p));
	}
}
