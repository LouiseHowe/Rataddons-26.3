package me.rattokens.rataddon.feature;

import me.rattokens.rataddon.util.PhysicalInput;
import me.rattokens.rataddon.RaTAddonClient;
import me.rattokens.rataddon.util.ModUtils;
import me.rattokens.rataddon.util.SkyblockItem;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLevelEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.message.v1.ClientReceiveMessageEvents;
import net.fabricmc.fabric.api.event.player.UseItemCallback;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.animal.frog.Frog;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.entity.projectile.FishingHook;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

import java.util.Objects;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

public final class AutoFish {
	private static final Minecraft CLIENT = Minecraft.getInstance();
	private static final String GOLDEN_FISH_HEAD_ID = "12d21312-80ed-3fab-b96f-099d14f365ea";
	private static final ScheduledExecutorService HYPE_EXECUTOR = Executors.newSingleThreadScheduledExecutor(runnable -> {
		Thread thread = new Thread(runnable, "RatAddons-HYPE");
		thread.setDaemon(true);
		return thread;
	});
	private static FishingState state = FishingState.NORMAL;
	private static GoldState goldState = GoldState.NOT_FOUND;
	private static long lastReelTime;
	private static long lastCastTime;
	private static long fluidEnteredTime;
	private static long hookedEntityTime;
	private static int retryCount;
	private static boolean entityWasHooked;
	private static boolean pendingUse;
	private static boolean jumpingForAir;
	private static Entity puddleJumper;
	private static boolean autoKillPending;
	private static int autoKillWaitTicks;
	private static int returnToRodWaitTicks;
	private static float preKillPitch;
	private static float preKillYaw;
	private static boolean hypeFacingGround;
	private static int autoKillSequenceId;
	private static FbWalkPhase fbWalkPhase = FbWalkPhase.IDLE;
	private static boolean fbWalkNextLeft = true;
	private static long fbWalkEndsAt;
	private static long nextFbWalkAt;

	private AutoFish() {
	}

	public static void init() {
		UseItemCallback.EVENT.register((player, world, hand) -> {
			if (!RaTAddonClient.CONFIG.autoFish || state != FishingState.NORMAL
				|| !world.isClientSide() || hand != InteractionHand.MAIN_HAND
				|| player.fishing != null || !isFishingRod(player.getMainHandItem())) {
				return InteractionResult.PASS;
			}
			startFishing();
			return InteractionResult.PASS;
		});

		ClientLevelEvents.AFTER_CLIENT_LEVEL_CHANGE.register((client, level) -> stop());
		ClientReceiveMessageEvents.GAME.register((message, overlay) -> handleGameMessage(message.getString()));
		ClientTickEvents.END_CLIENT_TICK.register(AutoFish::tick);
	}

	public static void fishBaitCheck(ArmorStand entity) {
		if (!RaTAddonClient.CONFIG.autoFish || !entity.hasCustomName()) {
			return;
		}
		LocalPlayer player = CLIENT.player;
		if (player == null || player.fishing == null || !isFishingRod(player.getMainHandItem())) {
			return;
		}
		String name = Objects.requireNonNull(entity.getCustomName()).getString();
		if (name.equals("!!!")
			&& player.fishing.getBoundingBox().inflate(4.0).intersects(entity.blockPosition())
			&& (RaTAddonClient.CONFIG.slugFish
				? readyToReel()
				: ModUtils.now() - lastCastTime > RaTAddonClient.CONFIG.fishMinWaitTime * 1000L)
			&& state == FishingState.WAITING) {
			state = FishingState.BAIT;
			retryCount = 0;
		}
	}

	public static void puddleJumperCheck(ArmorStand entity) {
		if (!RaTAddonClient.CONFIG.autoFish || !RaTAddonClient.CONFIG.puddleJumper || CLIENT.player == null) {
			return;
		}
		if (entity.getName().getString().contains("Puddle") && entity.distanceTo(CLIENT.player) < 10.0F) {
			if (state == FishingState.WAITING && CLIENT.player.fishing != null) {
				useRod();
			}
			state = FishingState.PUDDLE_JUMPER;
			puddleJumper = entity;
		}
	}

	public static void stop() {
		stopFbWalk();
		state = FishingState.NORMAL;
		fluidEnteredTime = 0L;
		goldState = GoldState.NOT_FOUND;
		retryCount = 0;
		entityWasHooked = false;
		puddleJumper = null;
		autoKillPending = false;
		hypeFacingGround = false;
		autoKillSequenceId++;
		if (jumpingForAir) {
			jumpingForAir = false;
			CLIENT.options.keyJump.setDown(false);
		}
	}

	public static void stopFbWalk() {
		releaseFbWalkKeys();
		fbWalkPhase = FbWalkPhase.IDLE;
		fbWalkNextLeft = true;
		fbWalkEndsAt = 0L;
		nextFbWalkAt = 0L;
	}

	private static void releaseFbWalkKeys() {
		if (fbWalkPhase == FbWalkPhase.IDLE) return;
		CLIENT.options.keyLeft.setDown(physicalDown(CLIENT.options.keyLeft));
		CLIENT.options.keyRight.setDown(physicalDown(CLIENT.options.keyRight));
		CLIENT.options.keyShift.setDown(physicalDown(CLIENT.options.keyShift));
	}

	private static void tick(Minecraft client) {
		LocalPlayer player = client.player;
		if (player == null || client.level == null || !RaTAddonClient.CONFIG.autoFish) {
			stopFbWalk();
			return;
		}
		tickFbWalk(client, player);
		if (state == FishingState.WAITING || state == FishingState.BAIT) {
			trackFluidEntry(player);
		}

		switch (state) {
			case WAITING -> tickWaiting(player);
			case BAIT -> {
				if (player.fishing == null) {
					stop();
				} else if (!readyToReel()) {
					state = FishingState.WAITING;
				} else {
					useRod();
					autoKillPending = RaTAddonClient.CONFIG.autoKill;
					state = FishingState.DELAYING;
					lastReelTime = ModUtils.now();
				}
			}
			case DELAYING -> {
				int delay = autoKillPending ? ModUtils.randomBetween(50, 100) : ModUtils.randomBetween(300, 500);
				if (player.fishing == null && ModUtils.now() - lastReelTime > delay) {
					if (autoKillPending) {
						startAutoKill(player);
					} else {
						useRod();
						startFishing();
					}
				}
			}
			case AUTO_KILL -> tickAutoKill();
			case AUTO_KILL_RETURN -> tickAutoKillReturn();
			case AUTO_KILL_HITTING -> {
			}
			case AUTO_KILL_ROTATING_BACK -> {
			}
			case GOLDEN_FISH -> tickGoldenFish(player);
			case PUDDLE_JUMPER -> {
				if (puddleJumper == null || !puddleJumper.isAlive() || player.distanceTo(puddleJumper) > 12.0F) {
					puddleJumper = null;
					useRod();
					startFishing();
				}
			}
			case NORMAL -> {
			}
		}

		if (state != FishingState.NORMAL && state != FishingState.AUTO_KILL
			&& state != FishingState.AUTO_KILL_RETURN
			&& state != FishingState.AUTO_KILL_HITTING
			&& state != FishingState.AUTO_KILL_ROTATING_BACK
			&& !isFishingRod(player.getMainHandItem())) {
			stop();
		}
		if (pendingUse) {
			useRod();
			pendingUse = false;
		}
	}

	private static void tickWaiting(LocalPlayer player) {
		if (RaTAddonClient.CONFIG.autoBreath) {
			if (player.getAirSupply() < 20) {
				jumpingForAir = true;
				CLIENT.options.keyJump.setDown(true);
			} else if (jumpingForAir) {
				jumpingForAir = false;
				CLIENT.options.keyJump.setDown(false);
			}
		}

		long elapsed = ModUtils.now() - lastCastTime;
		if (elapsed > 5000L) {
			if (player.fishing == null) {
				stop();
				return;
			}
			BlockState hookBlock = CLIENT.level.getBlockState(player.fishing.blockPosition());
			if (!hookBlock.is(Blocks.WATER) && !hookBlock.is(Blocks.LAVA)) {
				useRod();
				if (retryCount > 0) {
					stop();
				} else {
					state = FishingState.DELAYING;
					lastReelTime = ModUtils.now();
					retryCount++;
				}
				return;
			}
		}

		if (elapsed > RaTAddonClient.CONFIG.fishMaxWaitTime * 1000L) {
			if (player.fishing != null) {
				useRod();
			}
			stop();
			return;
		}

		if (!RaTAddonClient.CONFIG.entityCheck || player.fishing == null) {
			return;
		}
		Entity hooked = player.fishing.getHookedIn();
		if (hooked instanceof Frog frog && frog.getScale() == 4.0F) {
			stop();
			return;
		}
		if (hooked == null) {
			entityWasHooked = false;
			return;
		}
		if (!entityWasHooked) {
			if (!(hooked instanceof ArmorStand)) {
				entityWasHooked = true;
				hookedEntityTime = ModUtils.now();
			}
			return;
		}
		if (ModUtils.now() - hookedEntityTime > 500L && readyToReelAt(hookedEntityTime)) {
			if (!(hooked instanceof ArmorStand) && hooked.isAlive()) {
				useRod();
				state = FishingState.DELAYING;
				lastReelTime = ModUtils.now();
			}
			entityWasHooked = false;
		}
	}

	private static void handleGameMessage(String message) {
		if (!RaTAddonClient.CONFIG.autoFish) {
			return;
		}
		if (RaTAddonClient.CONFIG.goldenFish) {
			if (message.contains("You spot a Golden Fish surface from beneath the lava!")) {
				GoldenFish.begin();
			} else if (message.contains("The Golden Fish escapes your hook")) {
				GoldenFish.escaped();
			} else if (message.contains("The Golden Fish is weak!")) {
				GoldenFish.weak();
			}
		} else if (message.contains("The Golden Fish escapes your hook")) {
			state = FishingState.DELAYING;
			lastReelTime = ModUtils.now();
		} else if (message.contains("The Golden Fish is weak!")) {
			useRod();
			state = FishingState.DELAYING;
			lastReelTime = ModUtils.now();
		}
	}

	private static void startAutoKill(LocalPlayer player) {
		autoKillPending = false;
		preKillPitch = player.getXRot();
		preKillYaw = player.getYRot();
		player.getInventory().setSelectedSlot(RaTAddonClient.CONFIG.autoKillHotbarSlot - 1);
		autoKillWaitTicks = RaTAddonClient.CONFIG.autoKillSwitchTicks;
		state = FishingState.AUTO_KILL;
		hypeFacingGround = !RaTAddonClient.CONFIG.hypeWipe;
		if (RaTAddonClient.CONFIG.hypeWipe) {
			float groundPitch = 87.5F + ModUtils.randomBetween(0, 20) / 10.0F;
			float naturalYaw = preKillYaw + ModUtils.randomBetween(-10, 10) / 10.0F;
			SmoothRotation.lookAt(groundPitch, naturalYaw, () -> hypeFacingGround = true);
		} else if (autoKillWaitTicks == 0) {
			tickAutoKill();
		}
	}

	private static void tickAutoKill() {
		if (autoKillWaitTicks > 0) {
			autoKillWaitTicks--;
		}
		if (autoKillWaitTicks > 0 || !hypeFacingGround) {
			return;
		}

		if (RaTAddonClient.CONFIG.hypeWipe) {
			startHypeHits();
		} else {
			ModUtils.useItem(InteractionHand.MAIN_HAND);
			beginReturnToRod();
		}
	}

	private static void startHypeHits() {
		state = FishingState.AUTO_KILL_HITTING;
		int sequenceId = ++autoKillSequenceId;
		int hitCount = RaTAddonClient.CONFIG.hypeHitCount;
		for (int hit = 0; hit < hitCount; hit++) {
			boolean lastHit = hit == hitCount - 1;
			HYPE_EXECUTOR.schedule(() -> CLIENT.execute(() -> {
				if (state != FishingState.AUTO_KILL_HITTING || autoKillSequenceId != sequenceId) {
					return;
				}
				ModUtils.useItem(InteractionHand.MAIN_HAND);
				if (lastHit) {
					beginReturnToRod();
				}
			}), (long) hit * 20L, TimeUnit.MILLISECONDS);
		}
	}

	private static void beginReturnToRod() {
		returnToRodWaitTicks = RaTAddonClient.CONFIG.autoKillReturnTicks;
		state = FishingState.AUTO_KILL_RETURN;
	}

	private static void tickAutoKillReturn() {
		if (--returnToRodWaitTicks > 0) {
			return;
		}
		if (CLIENT.player == null) {
			stop();
			return;
		}
		CLIENT.player.getInventory().setSelectedSlot(RaTAddonClient.CONFIG.fishingRodHotbarSlot - 1);
		if (RaTAddonClient.CONFIG.hypeWipe) {
			hypeFacingGround = false;
			state = FishingState.AUTO_KILL_ROTATING_BACK;
			SmoothRotation.lookAt(preKillPitch, preKillYaw, AutoFish::castRod);
		} else {
			castRod();
		}
	}

	private static void castRod() {
		if (CLIENT.player == null) {
			stop();
			return;
		}
		useRod();
		startFishing();
	}

	private static void tickGoldenFish(LocalPlayer player) {
		FishingHook hook = player.fishing;
		switch (goldState) {
			case FIND -> {
				GoldenFish.entity = GoldenFish.findEntity();
				if (GoldenFish.entity != null) {
					goldState = GoldState.NOT_HOOKED;
				} else if (ModUtils.now() - GoldenFish.foundTime > 3000L) {
					startFishing();
					goldState = GoldState.NOT_FOUND;
				}
			}
			case NOT_HOOKED -> {
				if (ModUtils.now() - GoldenFish.lastAttemptTime <= 1000L) {
					return;
				}
				if (GoldenFish.isHooked(hook)) {
					goldState = GoldState.HOOKED;
					return;
				}
				if (hook != null) {
					if (Math.abs(hook.getKnownMovement().x) + Math.abs(hook.getKnownMovement().z) > 0.05) {
						GoldenFish.lastAttemptTime = ModUtils.now();
						return;
					}
					useRod();
				}
				if (GoldenFish.entity != null
					&& player.hasLineOfSight(GoldenFish.entity, ClipContext.Block.COLLIDER,
					ClipContext.Fluid.NONE, GoldenFish.entity.getEyeY() + 1.0)) {
					GoldenFish.faceEntity(player);
				}
				GoldenFish.lastAttemptTime = ModUtils.now();
				if (++GoldenFish.attempts > 5) {
					SmoothRotation.lookAt(GoldenFish.cachedPitch, GoldenFish.cachedYaw, () -> pendingUse = true);
					startFishing();
					goldState = GoldState.NOT_FOUND;
				}
			}
			case WEAK -> {
				useRod();
				SmoothRotation.lookAt(GoldenFish.cachedPitch, GoldenFish.cachedYaw, () -> pendingUse = true);
				startFishing();
				goldState = GoldState.NOT_FOUND;
			}
			case NOT_FOUND, HOOKED -> {
			}
		}
	}

	private static void useRod() {
		if (CLIENT.player != null && isFishingRod(CLIENT.player.getMainHandItem())) {
			ModUtils.useItem(InteractionHand.MAIN_HAND);
		}
	}

	private static boolean isFishingRod(ItemStack stack) {
		if (!stack.is(Items.FISHING_ROD)) {
			return false;
		}
		String id = SkyblockItem.id(stack);
		return !id.equals("CARNIVAL_FISHING_ROD") && id.contains("ROD");
	}

	private static void startFishing() {
		state = FishingState.WAITING;
		lastCastTime = ModUtils.now();
		fluidEnteredTime = 0L;
		entityWasHooked = false;
	}

	private static void tickFbWalk(Minecraft client, LocalPlayer player) {
		if (!RaTAddonClient.CONFIG.fbWalk || state == FishingState.NORMAL
			|| client.gui.screen() != null || !isFishingRod(player.getMainHandItem())) {
			stopFbWalk();
			return;
		}
		if (fbWalkPhase != FbWalkPhase.IDLE) {
			if (physicalDown(client.options.keyLeft) || physicalDown(client.options.keyRight)) {
				stopFbWalk();
				return;
			}
			if (ModUtils.now() < fbWalkEndsAt) return;
			releaseFbWalkKeys();
			fbWalkNextLeft = fbWalkPhase == FbWalkPhase.RIGHT;
			fbWalkPhase = FbWalkPhase.IDLE;
			fbWalkEndsAt = 0L;
			scheduleFbWalk();
			return;
		}
		if ((state != FishingState.WAITING && state != FishingState.BAIT) || player.fishing == null) return;

		if (nextFbWalkAt == 0L) scheduleFbWalk();
		if (ModUtils.now() < nextFbWalkAt
			|| client.options.keyLeft.isDown() || client.options.keyRight.isDown()
			|| client.options.keyShift.isDown()) return;
		client.options.keyShift.setDown(true);
		if (fbWalkNextLeft) client.options.keyLeft.setDown(true);
		else client.options.keyRight.setDown(true);
		fbWalkPhase = fbWalkNextLeft ? FbWalkPhase.LEFT : FbWalkPhase.RIGHT;
		fbWalkEndsAt = ModUtils.now() + 500L;
	}

	private static void scheduleFbWalk() {
		int min = Math.clamp(RaTAddonClient.CONFIG.fbWalkMinSeconds, 1, 300);
		int max = Math.clamp(RaTAddonClient.CONFIG.fbWalkMaxSeconds, min, 300);
		nextFbWalkAt = ModUtils.now() + ModUtils.randomBetween(min, max) * 1000L;
	}

	private static boolean physicalDown(KeyMapping key) {
		return PhysicalInput.isDown(KeyMappingHelper.getBoundKeyOf(key));
	}

	private static void trackFluidEntry(LocalPlayer player) {
		if (fluidEnteredTime != 0L || player.fishing == null || CLIENT.level == null) return;
		BlockState block = CLIENT.level.getBlockState(player.fishing.blockPosition());
		if (block.is(Blocks.WATER) || block.is(Blocks.LAVA)) fluidEnteredTime = ModUtils.now();
	}

	private static boolean readyToReel() {
		return readyToReelAt(ModUtils.now());
	}

	private static boolean readyToReelAt(long biteTime) {
		return !RaTAddonClient.CONFIG.slugFish
			|| (fluidEnteredTime != 0L && biteTime - fluidEnteredTime >= 10_000L);
	}

	private static final class GoldenFish {
		private static ArmorStand entity;
		private static long foundTime;
		private static long lastAttemptTime;
		private static int attempts;
		private static float cachedPitch;
		private static float cachedYaw;

		private static void begin() {
			if (CLIENT.player == null) {
				return;
			}
			cachedPitch = CLIENT.player.getXRot();
			cachedYaw = CLIENT.player.getYRot();
			foundTime = ModUtils.now();
			attempts = 0;
			state = FishingState.GOLDEN_FISH;
			goldState = GoldState.FIND;
		}

		private static void escaped() {
			if (entity != null) {
				lastAttemptTime = ModUtils.now();
				attempts = 0;
				goldState = GoldState.NOT_HOOKED;
			}
		}

		private static void weak() {
			if (entity != null) {
				goldState = GoldState.WEAK;
			}
		}

		private static ArmorStand findEntity() {
			if (CLIENT.level == null) {
				return null;
			}
			for (Entity candidate : CLIENT.level.entitiesForRendering()) {
				if (candidate instanceof ArmorStand stand
					&& ModUtils.isPlayerHeadWithId(stand.getItemBySlot(EquipmentSlot.HEAD), GOLDEN_FISH_HEAD_ID)) {
					return stand;
				}
			}
			return null;
		}

		private static boolean isHooked(FishingHook hook) {
			return hook != null && entity != null
				&& hook.distanceToSqr(entity.getX(), entity.getY() + 1.5, entity.getZ()) < 0.5;
		}

		private static void faceEntity(LocalPlayer player) {
			float fishYaw = (float) Math.toRadians(entity.getYRot() + 90.0F);
			double frontX = entity.getX() + 0.6 * Math.cos(fishYaw);
			double frontZ = entity.getZ() + 0.6 * Math.sin(fishYaw);
			double x = player.getX() - entity.getX();
			double y = player.getY() - entity.getY() - 0.6;
			double z = player.getZ() - entity.getZ();
			float pitch = (float) (Math.atan2(y, Math.sqrt(x * x + z * z)) / Math.PI * 180.0);
			float yaw = (float) (Math.atan2(frontZ - player.getZ(), frontX - player.getX()) / Math.PI * 180.0 - 90.0);
			SmoothRotation.lookAt(pitch, yaw, () -> pendingUse = true);
		}
	}

	private enum FishingState {
		NORMAL,
		WAITING,
		BAIT,
		DELAYING,
		AUTO_KILL,
		AUTO_KILL_HITTING,
		AUTO_KILL_RETURN,
		AUTO_KILL_ROTATING_BACK,
		GOLDEN_FISH,
		PUDDLE_JUMPER
	}

	private enum FbWalkPhase {
		IDLE,
		LEFT,
		RIGHT
	}

	private enum GoldState {
		NOT_FOUND,
		FIND,
		NOT_HOOKED,
		HOOKED,
		WEAK
	}
}
