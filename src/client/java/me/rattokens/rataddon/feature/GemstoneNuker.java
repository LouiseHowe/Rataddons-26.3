package me.rattokens.rataddon.feature;

import com.mojang.blaze3d.platform.InputConstants;
import me.rattokens.rataddon.RaTAddonClient;
import me.rattokens.rataddon.util.HumanRotation;
import me.rattokens.rataddon.util.PhysicalInput;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLevelEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.VoxelShape;

import java.util.concurrent.ThreadLocalRandom;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Visible gemstone miner. The client owns block breaking through its normal held attack key.
 * Its target selection and smooth rotation follow MelodySky's Nuker, with through-wall targets removed.
 */
public final class GemstoneNuker {
    private static final double RANGE = 3.5;
    private static final double RANGE_SQR = RANGE * RANGE;
    private static final int MAX_TARGET_TICKS = 120;
    private static final int SKIP_TICKS = 20;
    private static final int TICK_OFFSET = 1;
    private static final int MAX_TURN_LEAD_TICKS = 8;
    private static final long ABILITY_RETRY_MS = 5_000L;
    private static final Pattern BOOST_MULTIPLIER = Pattern.compile("\\+([0-9.]+)%");

    private static KeyMapping toggleKey;
    private static boolean enabled;
    private static boolean holdingAttack;
    private static BlockPos target;
    private static Vec3 aim;
    private static int targetSinceTick;
    private static BlockPos miningPos;
    private static int breakStartTick = -1;
    private static double minedWork;
    private static int nextSwitchAttemptTick;
    private static double miningSpeed = -1;
    private static int lastSpeedSampleTick = -5;
    private static int sampledSpeedMode = -1;
    private static int missingSpeedSamples;
    private static boolean warnedMissingSpeed;
    private static BlockPos skipped;
    private static int skipUntilTick;
    private static int tick;
    private static int selectedSlot = -1;
    private static double yawNoise;
    private static double pitchNoise;
    private static long lastAbilityAttemptMs;

    private GemstoneNuker() {}

    public static void init() {
        KeyMapping.Category category = KeyMapping.Category.register(
            Identifier.fromNamespaceAndPath(RaTAddonClient.MOD_ID, "mining"));
        toggleKey = KeyMappingHelper.registerKeyMapping(new KeyMapping(
            "key.rataddon.gemstone_nuker", InputConstants.Type.KEYBOARD,
            InputConstants.KEY_N, category));
        ClientTickEvents.END_CLIENT_TICK.register(GemstoneNuker::tick);
        ClientLevelEvents.AFTER_CLIENT_LEVEL_CHANGE.register((client, level) -> setEnabled(false));
    }

    public static boolean isEnabled() {
        return enabled;
    }

    public static void setEnabled(boolean value) {
        Minecraft client = Minecraft.getInstance();
        if (value && (client.player == null || client.level == null)) return;
        if (enabled == value) return;
        enabled = value;
        clearTarget(client);
        selectedSlot = value ? client.player.getInventory().getSelectedSlot() : -1;
        skipped = null;
        miningSpeed = -1;
        lastSpeedSampleTick = tick - 5;
        sampledSpeedMode = -1;
        missingSpeedSamples = 0;
        warnedMissingSpeed = false;
        lastAbilityAttemptMs = 0L;
        if (client.player != null) client.player.sendSystemMessage(Component.literal(
            "§b[Gemstone Nuker] " + (enabled ? "§aEnabled" : "§cDisabled")));
    }

    private static void tick(Minecraft client) {
        while (toggleKey.consumeClick()) {
            if (client.gui.screen() == null) setEnabled(!enabled);
        }
        if (!enabled) return;
        tick++;
        if (client.player == null || client.level == null || client.gameMode == null) {
            setEnabled(false);
            return;
        }
        if (client.gui.screen() != null || !client.mouseHandler.isMouseGrabbed()
            || !PingOffsetMiner.isMiningTool(client, client.player.getMainHandItem())
            || HumanRotation.isRotating()) {
            clearTarget(client);
            return;
        }
        int slot = client.player.getInventory().getSelectedSlot();
        if (slot != selectedSlot) {
            selectedSlot = slot;
            lastSpeedSampleTick = tick - 5;
            clearTarget(client);
            return;
        }
        sampleMiningSpeed(client);
        BlockPos focused = focusedGemstone(client);
        // Vanilla starts a new break when the held crosshair moves onto another block.
        // Time the block actually under the crosshair, including gemstones crossed mid-turn.
        if (focused == null) {
            releaseAttack(client);
        } else if (!focused.equals(miningPos)) {
            // When the key was already held, vanilla processes the new block in this tick.
            boolean continuedAttack = holdingAttack;
            miningPos = focused;
            breakStartTick = tick - (continuedAttack ? 1 : 0);
            minedWork = continuedAttack && miningSpeed > 0
                ? miningSpeed / (30.0 * GemstoneMiningTiming.strength(client.level.getBlockState(focused)))
                : 0;
        } else if (holdingAttack && miningSpeed > 0 && tick > breakStartTick) {
            BlockState state = client.level.getBlockState(focused);
            minedWork += miningSpeed / (30.0 * GemstoneMiningTiming.strength(state));
        }
        if (target != null && ((tick - targetSinceTick > MAX_TARGET_TICKS
            && !target.equals(miningPos))
            || !isGemstone(client.level.getBlockState(target))
            || visibleAim(client, target) == null)) {
            if (tick - targetSinceTick > MAX_TARGET_TICKS) {
                skipped = target;
                skipUntilTick = tick + SKIP_TICKS;
            }
            clearAim();
        }
        if (target != null && target.equals(miningPos) && holdingAttack && miningSpeed > 0
            && tick >= nextSwitchAttemptTick) {
            BlockState state = client.level.getBlockState(target);
            int expected = GemstoneMiningTiming.predictedTicks(state, miningSpeed);
            int elapsed = tick - breakStartTick;
            if (expected >= 0 && elapsed >= (expected == 0 ? 1 : 2)
                && (expected == 0 || remainingMiningTicks(state, elapsed) <= MAX_TURN_LEAD_TICKS)) {
                Target next = select(client, target);
                if (next != null) {
                    int turnTicks = estimateTurnTicks(client, next.aim());
                    if (expected == 0 || remainingMiningTicks(state, elapsed) <= turnTicks) {
                        skipped = target;
                        skipUntilTick = tick + SKIP_TICKS;
                        setTarget(next);
                    }
                } else {
                    // Keep mining the current block until another visible target exists.
                    nextSwitchAttemptTick = tick + 5;
                }
            }
        }
        if (target == null) {
            Target next = select(client, null);
            if (next == null) {
                clearAim();
            } else {
                setTarget(next);
            }
        }
        if (focused != null) {
            holdingAttack = true;
            client.options.keyAttack.setDown(true);
            useAbility(client);
        } else {
            releaseAttack(client);
        }
    }

    /** Adds the reference Nuker's 12-23 degree smooth turn to vanilla mouse movement. */
    public static double[] onTurn(double frameTime) {
        if (!enabled || aim == null || HumanRotation.isRotating()) return null;
        Minecraft client = Minecraft.getInstance();
        if (client.player == null || client.level == null || client.gui.screen() != null
            || !client.mouseHandler.isMouseGrabbed()) return null;

        float[] goal = HumanRotation.look(aim, client.player.getEyePosition());
        float yaw = Mth.wrapDegrees(goal[0] - client.player.getYRot());
        float pitch = goal[1] - client.player.getXRot();
        float absYaw = Math.abs(yaw);
        float absPitch = Math.abs(pitch);
        float error = Math.max(absYaw, absPitch);
        if (absYaw <= 0.12F && absPitch <= 0.12F) {
            yawNoise = pitchNoise = 0;
            return null;
        }

        double curve = error <= 1.0E-4F ? 0 : error / (error + 26.0F);
        float step = (float) ((12.0 + (23.0 - 12.0) * curve)
            * Math.clamp(frameTime * 20.0, 0.05, 2.0));
        float fraction = error <= 1.0E-4F ? 0 : Math.clamp(step / error, 0.0F, 1.0F);
        if (error < 12.0F) fraction /= 3.0F;
        float yawStep = yaw * fraction;
        float pitchStep = pitch * fraction;

        ThreadLocalRandom random = ThreadLocalRandom.current();
        double noiseSize = 0.015 + 0.075 * curve;
        yawNoise = yawNoise * 0.72 + random.nextDouble(-1.0, 1.0)
            * noiseSize * (absYaw / error) * 0.28;
        pitchNoise = pitchNoise * 0.72 + random.nextDouble(-1.0, 1.0)
            * noiseSize * (absPitch / error) * 0.28;
        yawStep = Math.clamp(yawStep + (float) yawNoise, -absYaw - 0.6F, absYaw + 0.6F);
        pitchStep = Math.clamp(pitchStep + (float) pitchNoise, -absPitch - 0.6F, absPitch + 0.6F);

        double sensitivity = client.options.sensitivity().get();
        double scaled = sensitivity * 0.6F + 0.2F;
        double cube = scaled * scaled * scaled;
        boolean scoped = client.options.getCameraType().isFirstPerson() && client.player.isScoping();
        double factor = !client.options.smoothCamera && scoped ? cube : cube * 8.0;
        if (factor <= 1.0E-6) return null;
        double invertX = client.options.invertMouseX().get() ? -1.0 : 1.0;
        double invertY = client.options.invertMouseY().get() ? -1.0 : 1.0;
        double dx = Math.clamp((yawStep / (0.15 * invertX)) / factor, -500.0, 500.0);
        double dy = Math.clamp((pitchStep / (0.15 * invertY)) / factor, -500.0, 500.0);
        return new double[] {dx, dy};
    }

    private static void sampleMiningSpeed(Minecraft client) {
        int mode = RaTAddonClient.CONFIG.gemstoneNukerSpeedMode;
        if (mode == 0 && sampledSpeedMode == mode && tick - lastSpeedSampleTick < 5) return;
        sampledSpeedMode = mode;
        if (mode == 1) {
            miningSpeed = PickaxeAbility.miningSpeedBoostActive()
                ? RaTAddonClient.CONFIG.gemstoneNukerBoostedSpeed
                : RaTAddonClient.CONFIG.gemstoneNukerNormalSpeed;
        } else {
            miningSpeed = PingOffsetMiner.readTabMiningSpeed(client);
            if (miningSpeed > 0 && PickaxeAbility.miningSpeedBoostActive())
                miningSpeed *= readBoostMultiplier(client);
        }
        lastSpeedSampleTick = tick;
        if (miningSpeed > 0) {
            missingSpeedSamples = 0;
        } else if (++missingSpeedSamples >= 8 && !warnedMissingSpeed) {
            client.player.sendSystemMessage(Component.literal(
                RaTAddonClient.CONFIG.gemstoneNukerSpeedMode == 1
                    ? "§b[Gemstone Nuker] §cEnter both Mining Speed values in Normal mode."
                    : "§b[Gemstone Nuker] §cMining Speed not found. Show it in /tab → Stats Widget → Shown Stats."));
            warnedMissingSpeed = true;
        }
    }

    private static double readBoostMultiplier(Minecraft client) {
        boolean foundAbility = false;
        for (var line : client.player.getMainHandItem().getTooltipLines(
            Item.TooltipContext.of(client.player.registryAccess()),
            client.player, TooltipFlag.ADVANCED)) {
            String text = line.getString();
            if (text.contains("Ability: Mining Speed")) {
                foundAbility = true;
                continue;
            }
            if (!foundAbility) continue;
            Matcher match = BOOST_MULTIPLIER.matcher(text);
            if (match.find()) {
                try { return 1.0 + Double.parseDouble(match.group(1)) / 100.0; }
                catch (NumberFormatException ignored) { return 1.0; }
            }
        }
        return 1.0;
    }

    private static void setTarget(Target next) {
        target = next.pos();
        aim = next.aim();
        targetSinceTick = tick;
        nextSwitchAttemptTick = tick;
    }

    private static Target select(Minecraft client, BlockPos excluded) {
        Vec3 eye = client.player.getEyePosition();
        Vec3 view = client.player.getViewVector(1.0F);
        double halfFov = Math.min(90.0, client.options.fov().get()) / 2.0 + 10.0;
        double minDot = Math.cos(Math.toRadians(halfFov));
        Target best = null;
        double bestScore = Double.MAX_VALUE;
        BlockPos origin = BlockPos.containing(eye);
        for (int x = -4; x <= 4; x++) {
            for (int y = -4; y <= 4; y++) {
                for (int z = -4; z <= 4; z++) {
                    BlockPos pos = origin.offset(x, y, z);
                    if (pos.equals(excluded) || pos.equals(skipped) && tick < skipUntilTick) continue;
                    if (!isGemstone(client.level.getBlockState(pos))) continue;
                    Vec3 point = visibleAim(client, pos);
                    if (point == null) continue;
                    Vec3 difference = point.subtract(eye);
                    double distance = difference.lengthSqr();
                    if (distance > RANGE_SQR || distance == 0 || difference.normalize().dot(view) < minDot) continue;
                    float[] rotation = HumanRotation.look(point, eye);
                    double yaw = Mth.wrapDegrees(rotation[0] - client.player.getYRot());
                    double pitch = rotation[1] - client.player.getXRot();
                    double score = 0.375 * yaw * yaw + 0.375 * pitch * pitch + 0.25 * distance;
                    if (score < bestScore) {
                        bestScore = score;
                        best = new Target(pos.immutable(), point);
                    }
                }
            }
        }
        return best;
    }

    private static Vec3 visibleAim(Minecraft client, BlockPos pos) {
        VoxelShape shape = client.level.getBlockState(pos).getShape(client.level, pos);
        if (shape.isEmpty()) return null;
        AABB box = shape.bounds();
        Vec3 center = Vec3.atLowerCornerOf(pos).add(box.getCenter());
        double hx = (box.maxX - box.minX) / 2.0 + 0.01;
        double hy = (box.maxY - box.minY) / 2.0 + 0.01;
        double hz = (box.maxZ - box.minZ) / 2.0 + 0.01;
        Vec3[] points = {center, center.add(hx, 0, 0), center.add(-hx, 0, 0),
            center.add(0, hy, 0), center.add(0, -hy, 0),
            center.add(0, 0, hz), center.add(0, 0, -hz)};
        Vec3 eye = client.player.getEyePosition();
        Vec3 best = null;
        double nearest = Double.MAX_VALUE;
        for (Vec3 point : points) {
            if (eye.distanceToSqr(point) > RANGE_SQR) continue;
            BlockHitResult hit = client.level.clip(new ClipContext(eye, point,
                ClipContext.Block.OUTLINE, ClipContext.Fluid.NONE, client.player));
            if (hit.getType() != HitResult.Type.BLOCK || !hit.getBlockPos().equals(pos)) continue;
            double distance = eye.distanceToSqr(point);
            if (distance < nearest) {
                nearest = distance;
                best = point;
            }
        }
        return best;
    }

    private static boolean isGemstone(BlockState state) {
        return GemstoneMiningTiming.strength(state) > 0;
    }

    private static BlockPos focusedGemstone(Minecraft client) {
        if (!(client.hitResult instanceof BlockHitResult hit)
            || hit.getType() != HitResult.Type.BLOCK) return null;
        BlockPos pos = hit.getBlockPos();
        if (!isGemstone(client.level.getBlockState(pos))
            || client.player.getEyePosition().distanceToSqr(hit.getLocation()) > RANGE_SQR)
            return null;
        return pos;
    }

    private static int remainingMiningTicks(BlockState state, int elapsed) {
        double workPerTick = miningSpeed / (30.0 * GemstoneMiningTiming.strength(state));
        int workTicks = (int) Math.ceil(Math.max(0, 1.0 - minedWork) / workPerTick);
        return Math.max(Math.max(0, 4 - elapsed), workTicks + TICK_OFFSET);
    }

    private static int estimateTurnTicks(Minecraft client, Vec3 point) {
        float[] rotation = HumanRotation.look(point, client.player.getEyePosition());
        double yaw = Math.abs(Mth.wrapDegrees(rotation[0] - client.player.getYRot()));
        double pitch = Math.abs(rotation[1] - client.player.getXRot());
        double error = Math.max(yaw, pitch);
        // The reference rotation slows near its goal. Include a short margin for
        // entering a pane's small hitbox before the aim settles at its center.
        return Math.clamp((int) Math.ceil(error / 12.0) + 2, 2, MAX_TURN_LEAD_TICKS);
    }

    private static void useAbility(Minecraft client) {
        if (!RaTAddonClient.CONFIG.gemstoneNukerUseAbility
            || !PickaxeAbility.hasSupportedAbility(client.player.getMainHandItem())
            || !PickaxeAbility.readyForAutoUse()) return;
        long now = System.currentTimeMillis();
        if (now - lastAbilityAttemptMs < ABILITY_RETRY_MS) return;
        lastAbilityAttemptMs = now;
        PickaxeAbility.noteAutoUse(client.player.getMainHandItem());
        client.gameMode.useItem(client.player, InteractionHand.MAIN_HAND);
    }

    private static void clearTarget(Minecraft client) {
        releaseAttack(client);
        clearAim();
    }

    private static void clearAim() {
        target = null;
        aim = null;
        yawNoise = pitchNoise = 0;
    }

    private static void releaseAttack(Minecraft client) {
        miningPos = null;
        breakStartTick = -1;
        minedWork = 0;
        if (!holdingAttack) return;
        holdingAttack = false;
        client.options.keyAttack.setDown(
            PhysicalInput.isDown(KeyMappingHelper.getBoundKeyOf(client.options.keyAttack)));
    }

    private record Target(BlockPos pos, Vec3 aim) {}
}
