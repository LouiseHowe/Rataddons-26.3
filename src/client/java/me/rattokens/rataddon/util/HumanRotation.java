package me.rattokens.rataddon.util;

import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;

import java.util.concurrent.ThreadLocalRandom;

public final class HumanRotation {
	private static final float TWO_PI = (float) (Math.PI * 2.0);
	private static final double NANOS_PER_SECOND = 1_000_000_000.0;

	private static Rotation current;
	private static long lastFrameNanos;

	private HumanRotation() {
	}

	/** Speeds are degrees/second; durations are seconds. */
	public record Profile(float minSpeed, float maxSpeed, float minDuration, float maxDuration,
						  float overshootChance, float minOvershoot, float maxOvershoot,
						  float flickChance, float maxFlick, float microCorrection, float timeout) {
		public static Profile speeds(float minSpeed, float maxSpeed) {
			return new Profile(minSpeed, Math.max(minSpeed, maxSpeed), 0.10F, 0.90F, 0.78F, 0.20F, 1.65F,
				0.0F, 0.05F, 0.075F, 1.5F);
		}
	}

	private record MovementKeys(boolean forward, boolean back, boolean left, boolean right) {
	}

	private static final class Rotation {
		float startYaw;
		float startPitch;
		float goalYaw;
		float goalPitch;
		float overYawOffset;
		float overPitchOffset;
		float mainTime;
		float correctionTime;
		float flickCenter;
		float flickWidth;
		float flickYaw;
		float flickPitch;
		float noise;
		float yawPhase;
		float pitchPhase;
		float yawFrequency;
		float pitchFrequency;
		Vec3 worldTarget;
		boolean trackTargetYaw;
		boolean trackTargetPitch;
		float targetYawOffset;
		float targetPitchOffset;
		MovementKeys movementKeys;
		long startedAtNanos;
		long timeoutNanos;
		Runnable onComplete;
		float elapsed;

		float duration() {
			return mainTime + correctionTime;
		}
	}

	public static boolean isRotating() {
		return current != null;
	}

	public static float[] look(Vec3 target, Vec3 origin) {
		double dx = target.x - origin.x;
		double dy = target.y - origin.y;
		double dz = target.z - origin.z;
		double horizontal = Math.sqrt(dx * dx + dz * dz);
		return new float[]{
			(float) Math.toDegrees(Math.atan2(-dx, dz)),
			(float) Math.toDegrees(-Math.atan2(dy, horizontal))
		};
	}

	/** Rotates yaw toward the target's X/Z while rotating to an explicit pitch. */
	public static void lookAt(Vec3 target, float pitch, float yawOffset, float pitchOffset, Profile profile,
							  boolean walk, Runnable onComplete) {
		LocalPlayer player = Minecraft.getInstance().player;
		if (player == null) {
			return;
		}
		float yaw = look(target, player.getEyePosition(partialTick()))[0];
		start(yaw + yawOffset, pitch + pitchOffset, target, true, false, yawOffset, pitchOffset, walk,
			profile, onComplete);
	}

	/** Rotates to explicit yaw/pitch without tracking a world position. */
	public static void lookAt(float yaw, float pitch, Profile profile, Runnable onComplete) {
		start(yaw, pitch, null, false, false, 0.0F, 0.0F, false, profile, onComplete);
	}

	public static void cancel() {
		restoreMovement(current);
		current = null;
		lastFrameNanos = 0L;
	}

	private static void start(float yaw, float pitch, Vec3 worldTarget, boolean trackYaw, boolean trackPitch,
							  float targetYawOffset, float targetPitchOffset, boolean walk, Profile profile,
							  Runnable onComplete) {
		LocalPlayer player = Minecraft.getInstance().player;
		if (player == null) {
			return;
		}
		restoreMovement(current);
		current = null;

		float startYaw = player.getYRot();
		float startPitch = player.getXRot();
		float goalYaw = startYaw + Mth.wrapDegrees(yaw - startYaw);
		float goalPitch = Math.clamp(pitch, -90.0F, 90.0F);
		float dy = goalYaw - startYaw;
		float dp = goalPitch - startPitch;
		float distance = (float) Math.hypot(dy, dp);

		if (distance < 0.01F) {
			applyGcd(goalYaw, goalPitch);
			lastFrameNanos = 0L;
			if (onComplete != null) {
				onComplete.run();
			}
			return;
		}

		float duration = Math.clamp(distance / random(profile.minSpeed(), profile.maxSpeed()),
			profile.minDuration(), profile.maxDuration());
		float overshoot = distance > 3.0F && nextFloat() < profile.overshootChance()
			? random(profile.minOvershoot(), profile.maxOvershoot()) * Math.clamp(distance / 45.0F, 0.25F, 1.0F)
			: 0.0F;
		float unitYaw = dy / distance;
		float unitPitch = dp / distance;
		float correctionTime = overshoot > 0.0F ? Math.max(0.065F, duration * random(0.12F, 0.22F)) : 0.0F;
		float mainTime = Math.max(0.035F, duration - correctionTime);
		boolean hasFlick = distance > 8.0F && profile.maxFlick() > 0.0F && nextFloat() < profile.flickChance();
		float flick = hasFlick ? random(profile.maxFlick() * 0.25F, profile.maxFlick()) : 0.0F;
		float side = ThreadLocalRandom.current().nextBoolean() ? 1.0F : -1.0F;

		long now = System.nanoTime();
		Rotation rotation = new Rotation();
		rotation.startYaw = startYaw;
		rotation.startPitch = startPitch;
		rotation.goalYaw = goalYaw;
		rotation.goalPitch = goalPitch;
		rotation.overYawOffset = unitYaw * overshoot;
		rotation.overPitchOffset = Math.clamp(goalPitch + unitPitch * overshoot, -90.0F, 90.0F) - goalPitch;
		rotation.mainTime = mainTime;
		rotation.correctionTime = correctionTime;
		rotation.flickCenter = random(0.34F, 0.72F);
		rotation.flickWidth = random(0.045F, 0.09F);
		rotation.flickYaw = -unitPitch * flick * side;
		rotation.flickPitch = unitYaw * flick * side;
		rotation.noise = profile.microCorrection() * Math.clamp(distance / 30.0F, 0.25F, 1.0F);
		rotation.yawPhase = random(0.0F, TWO_PI);
		rotation.pitchPhase = random(0.0F, TWO_PI);
		rotation.yawFrequency = random(1.7F, 2.8F);
		rotation.pitchFrequency = random(2.0F, 3.2F);
		rotation.worldTarget = worldTarget;
		rotation.trackTargetYaw = trackYaw;
		rotation.trackTargetPitch = trackPitch;
		rotation.targetYawOffset = targetYawOffset;
		rotation.targetPitchOffset = targetPitchOffset;
		rotation.movementKeys = walk ? captureMovement() : null;
		rotation.startedAtNanos = now;
		rotation.timeoutNanos = (long) (profile.timeout() * NANOS_PER_SECOND);
		rotation.onComplete = onComplete;
		current = rotation;
		lastFrameNanos = now;
		updateMovement(rotation, player.getYRot());
	}

	/** Advances the active rotation once per rendered frame. */
	public static void onFrame() {
		Minecraft client = Minecraft.getInstance();
		if (client.gui.screen() != null) {
			return;
		}
		Rotation state = current;
		if (state == null) {
			return;
		}
		LocalPlayer player = client.player;
		if (player == null || client.level == null) {
			cancel();
			return;
		}

		long now = System.nanoTime();
		if (now - state.startedAtNanos >= state.timeoutNanos) {
			restoreMovement(state);
			current = null;
			lastFrameNanos = 0L;
			return;
		}
		if (lastFrameNanos == 0L) {
			lastFrameNanos = now;
			return;
		}
		float frameTime = Math.clamp((float) ((now - lastFrameNanos) / NANOS_PER_SECOND), 0.0F, 0.05F);
		lastFrameNanos = now;
		state.elapsed += frameTime;

		if (state.worldTarget != null) {
			float[] look = look(state.worldTarget, player.getEyePosition(partialTick()));
			if (state.trackTargetYaw) {
				state.goalYaw += Mth.wrapDegrees(look[0] + state.targetYawOffset - state.goalYaw);
			}
			if (state.trackTargetPitch) {
				state.goalPitch = Math.clamp(look[1] + state.targetPitchOffset, -90.0F, 90.0F);
			}
		}

		if (state.elapsed >= state.duration()) {
			applyGcd(state.goalYaw, state.goalPitch);
			restoreMovement(state);
			current = null;
			lastFrameNanos = 0L;
			if (state.onComplete != null) {
				state.onComplete.run();
			}
			return;
		}

		float totalProgress = Math.clamp(state.elapsed / state.duration(), 0.0F, 1.0F);
		float overYaw = state.goalYaw + state.overYawOffset;
		float overPitch = Math.clamp(state.goalPitch + state.overPitchOffset, -90.0F, 90.0F);
		float baseYaw;
		float basePitch;
		if (state.elapsed < state.mainTime || state.correctionTime == 0.0F) {
			float p = smootherStep(Math.clamp(state.elapsed / state.mainTime, 0.0F, 1.0F));
			baseYaw = lerp(state.startYaw, overYaw, p);
			basePitch = lerp(state.startPitch, overPitch, p);
		} else {
			float p = smootherStep(Math.clamp((state.elapsed - state.mainTime) / state.correctionTime, 0.0F, 1.0F));
			baseYaw = lerp(overYaw, state.goalYaw, p);
			basePitch = lerp(overPitch, state.goalPitch, p);
		}

		float flick = pulse(totalProgress, state.flickCenter, state.flickWidth);
		float envelope = (float) Math.sin(Math.PI * totalProgress);
		float yawNoise = (float) Math.sin(state.yawPhase + totalProgress * TWO_PI * state.yawFrequency)
			* state.noise * envelope;
		float pitchNoise = (float) Math.sin(state.pitchPhase + totalProgress * TWO_PI * state.pitchFrequency)
			* state.noise * 0.65F * envelope;
		applyGcd(baseYaw + state.flickYaw * flick + yawNoise, basePitch + state.flickPitch * flick + pitchNoise);
		updateMovement(state, player.getYRot());
	}

	private static MovementKeys captureMovement() {
		var options = Minecraft.getInstance().options;
		return new MovementKeys(options.keyUp.isDown(), options.keyDown.isDown(),
			options.keyLeft.isDown(), options.keyRight.isDown());
	}

	private static void updateMovement(Rotation state, float playerYaw) {
		if (state.movementKeys == null || state.worldTarget == null) {
			return;
		}
		LocalPlayer player = Minecraft.getInstance().player;
		if (player == null) {
			return;
		}
		double dx = state.worldTarget.x - player.getX();
		double dz = state.worldTarget.z - player.getZ();
		if (dx * dx + dz * dz < 0.0001) {
			setMovement(false, false, false, false);
			return;
		}
		float targetYaw = (float) Math.toDegrees(Math.atan2(-dx, dz));
		int direction = Math.round(Mth.wrapDegrees(targetYaw - playerYaw) / 45.0F);
		setMovement(direction >= -1 && direction <= 1, direction <= -3 || direction >= 3,
			direction >= -3 && direction <= -1, direction >= 1 && direction <= 3);
	}

	private static void restoreMovement(Rotation state) {
		if (state == null || state.movementKeys == null) {
			return;
		}
		MovementKeys keys = state.movementKeys;
		setMovement(keys.forward(), keys.back(), keys.left(), keys.right());
	}

	private static void setMovement(boolean forward, boolean back, boolean left, boolean right) {
		var options = Minecraft.getInstance().options;
		options.keyUp.setDown(forward);
		options.keyDown.setDown(back);
		options.keyLeft.setDown(left);
		options.keyRight.setDown(right);
	}

	/** Quantizes each delta to the same step produced by Minecraft's mouse sensitivity. */
	public static void applyGcd(float yaw, float pitch) {
		Minecraft client = Minecraft.getInstance();
		LocalPlayer player = client.player;
		if (player == null) {
			return;
		}
		float sensitivity = client.options.sensitivity().get().floatValue();
		float multiplier = sensitivity * 0.6F + 0.2F;
		float gcd = multiplier * multiplier * multiplier * 1.2F;
		float yawStep = Math.round(Mth.wrapDegrees(yaw - player.getYRot()) / gcd) * gcd;
		float pitchStep = Math.round((pitch - player.getXRot()) / gcd) * gcd;
		player.setYRot(player.getYRot() + yawStep);
		player.setXRot(Math.clamp(player.getXRot() + pitchStep, -90.0F, 90.0F));
	}

	public static float partialTick() {
		return Minecraft.getInstance().getDeltaTracker().getGameTimeDeltaPartialTick(true);
	}

	private static float smootherStep(float p) {
		return p * p * p * (p * (p * 6.0F - 15.0F) + 10.0F);
	}

	private static float lerp(float a, float b, float p) {
		return a + (b - a) * p;
	}

	private static float pulse(float progress, float center, float width) {
		float distance = Math.abs(progress - center);
		if (distance >= width) {
			return 0.0F;
		}
		return (float) Math.sin((1.0F - distance / width) * (Math.PI / 2.0));
	}

	private static float nextFloat() {
		return ThreadLocalRandom.current().nextFloat();
	}

	private static float random(float min, float max) {
		return min == max ? min : nextFloat() * (max - min) + min;
	}
}
