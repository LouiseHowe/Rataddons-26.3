package me.rattokens.rataddon.feature;

import me.rattokens.rataddon.RaTAddonClient;
import me.rattokens.rataddon.util.LocationManager;
import me.rattokens.rataddon.util.SkyblockLocation;
import me.rattokens.rataddon.util.WorldRender;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLevelEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.message.v1.ClientReceiveMessageEvents;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderEvents;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.hud.VanillaHudElements;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundLevelParticlesPacket;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.List;

import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Ping-compensated visual timing hint for supported SkyBlock mining blocks. */
public final class PingOffsetMiner {
	private static final Pattern SPEED = Pattern.compile("(?i)Mining Speed\\s*:\\s*[^0-9]*([0-9][0-9,.]*)");
	private static final Pattern MSB_COOLDOWN = Pattern.compile("\\b([0-9.]+)\\s*s\\b");
	private static final Pattern MSB_MULTIPLIER = Pattern.compile("\\+([0-9.]+)%");
	private static final Map<String, Integer> STRENGTH = Map.ofEntries(
		Map.entry("coal_block", 600), Map.entry("obsidian", 500),
		Map.entry("gold_block", 600), Map.entry("diamond_block", 600),
		Map.entry("emerald_block", 600), Map.entry("iron_block", 600),
		Map.entry("lapis_block", 600), Map.entry("redstone_block", 600),
		Map.entry("gray_wool", 500), Map.entry("cyan_terracotta", 500),
		Map.entry("prismarine", 800), Map.entry("prismarine_bricks", 800),
		Map.entry("dark_prismarine", 800), Map.entry("light_blue_wool", 1500),
		Map.entry("polished_diorite", 2000), Map.entry("clay", 5600),
		Map.entry("infested_cobblestone", 5600), Map.entry("brown_terracotta", 5600),
		Map.entry("terracotta", 5600), Map.entry("smooth_red_sandstone", 5600),
		Map.entry("packed_ice", 6000),
		Map.entry("red_stained_glass", 2300), Map.entry("orange_stained_glass", 3000),
		Map.entry("light_blue_stained_glass", 3000), Map.entry("lime_stained_glass", 3000),
		Map.entry("purple_stained_glass", 3000), Map.entry("white_stained_glass", 3000),
		Map.entry("yellow_stained_glass", 3800), Map.entry("magenta_stained_glass", 4800),
		Map.entry("black_stained_glass", 5200), Map.entry("blue_stained_glass", 5200),
		Map.entry("brown_stained_glass", 5200), Map.entry("green_stained_glass", 5200)
	);

	private static BlockPos target;
	private static BlockPos breakingTarget;
	private static int startedAtTick;
	private static int ticksElapsed;
	private static int ticksNeeded;
	private static double miningSpeed = -1;
	private static boolean miningTool;
	private static int missingSpeedSamples;
	private static boolean warnedMissingSpeed;
	private static boolean speedBoostActive;
	private static long speedBoostUntilMs;
	private static double speedBoostMultiplier = 1.0;
	private static int speedBoostCooldownTicks;
	private static Vec3 precisionParticle;
	private static long precisionParticleAtMs;
	private static double averagePingMs;
	private static volatile double measuredRttMs;
	private static volatile long lastRttNs;
	private static final int[] PING_SAMPLES = new int[10];
	private static int sampleCount;
	private static int sampleCursor;
	private static long totalPingMs;
	private static volatile double serverTps = 20.0;
	private static volatile long lastServerTickNs;
	private static volatile int lastServerPingId;
	private static final long[] SERVER_TICK_INTERVALS = new long[100];
	private static int serverIntervalCount;
	private static int serverIntervalCursor;
	private static long serverIntervalSum;
	private static int tick;

	private PingOffsetMiner() {}

	public static void init() {
		ClientTickEvents.END_CLIENT_TICK.register(PingOffsetMiner::tick);
		ClientLevelEvents.AFTER_CLIENT_LEVEL_CHANGE.register((client, level) -> reset());
		ClientReceiveMessageEvents.GAME.register((message, overlay) -> {
			if (message.getString().toLowerCase(java.util.Locale.ROOT)
				.contains("you used your mining speed boost pickaxe ability!")) {
				speedBoostActive = true;
				speedBoostUntilMs = System.currentTimeMillis() + speedBoostCooldownTicks * 50L;
			}
		});
		LevelRenderEvents.END_MAIN.register(context -> {
			Minecraft client = Minecraft.getInstance();
			if (!showing(client)) return;
			BlockState state = client.level.getBlockState(target);
			boolean ready = ticksNeeded > 0 && ticksElapsed >= ticksNeeded;
			int color = ready ? 0xFF55FF55 : 0xFFFF5555;
			List<WorldRender.BoxOverlay> boxes = new ArrayList<>();
			for (AABB part : state.getShape(client.level, target).toAabbs())
				boxes.add(new WorldRender.BoxOverlay(part.move(target), color, RaTAddonClient.CONFIG.pingOffsetOutlineWidth,
					RaTAddonClient.CONFIG.pingOffsetHighlight ? 51 : 0));
			if (!RaTAddonClient.CONFIG.pingOffsetOutline) {
				if (RaTAddonClient.CONFIG.pingOffsetHighlight)
					for (AABB part : state.getShape(client.level, target).toAabbs())
						WorldRender.filledBox(context, part.move(target), 0x33000000 | (color & 0xFFFFFF), false);
				return;
			}
			WorldRender.boxOverlays(context, boxes, false);
		});
		LevelRenderEvents.BEFORE_BLOCK_OUTLINE.register((context, state) -> {
			Minecraft client = Minecraft.getInstance();
			return !showing(client);
		});
		HudElementRegistry.attachElementBefore(VanillaHudElements.CHAT,
			Identifier.fromNamespaceAndPath(RaTAddonClient.MOD_ID, "pom_ticks"), (graphics, delta) -> {
			Minecraft client = Minecraft.getInstance();
			if (!RaTAddonClient.CONFIG.pingOffsetTickHud || !eligible(client)) return;
			KittyHud.draw(graphics, client.font, KittyHud.Slot.POM_TICKS,
				Component.literal((ticksElapsed >= ticksNeeded ? "§a" : "§c")
					+ ticksElapsed + "/" + ticksNeeded),
				client.getWindow().getGuiScaledWidth(), client.getWindow().getGuiScaledHeight(), 0xFFFFFFFF);
		});
		HudElementRegistry.attachElementBefore(VanillaHudElements.CHAT,
			Identifier.fromNamespaceAndPath(RaTAddonClient.MOD_ID, "pom_profiler"), (graphics, delta) -> {
			Minecraft client = Minecraft.getInstance();
			if (!RaTAddonClient.CONFIG.pingOffsetProfilerHud || !active() || client.player == null) return;
			double ping = currentPing();
			KittyHud.draw(graphics, client.font, KittyHud.Slot.POM_PROFILER,
				Component.literal(String.format(java.util.Locale.ROOT,
					"§bPing: §f%.0f §b| TPS: §f%.1f", ping, currentTps())),
				client.getWindow().getGuiScaledWidth(), client.getWindow().getGuiScaledHeight(), 0xFFFFFFFF);
		});
	}

	private static void tick(Minecraft client) {
		if (!active() || client.level == null || client.player == null || client.getConnection() == null) {
			reset();
			return;
		}
		if (client.hitResult instanceof BlockHitResult hit
			&& strength(client.level.getBlockState(hit.getBlockPos())) != null) {
			BlockPos next = hit.getBlockPos();
			if (!next.equals(target)) {
				target = next.immutable();
				ticksElapsed = 0;
			}
		} else {
			target = null;
		}
		if (client.gameMode != null && client.gameMode.isDestroying()
			&& target != null && target.equals(breakingTarget)) {
			ticksElapsed = Math.max(0, client.player.tickCount - startedAtTick);
		} else {
			ticksElapsed = 0;
		}
		if (++tick % 5 == 0) {
			ItemStack item = client.player.getMainHandItem();
			miningTool = isMiningTool(client, item);
			miningSpeed = readMiningSpeed(client);
			readSpeedBoost(client, item);
			if (miningTool && miningSpeed <= 0 && !RaTAddonClient.CONFIG.pingOffsetCustomStats) {
				if (++missingSpeedSamples >= 8 && !warnedMissingSpeed) {
					client.player.sendSystemMessage(Component.literal(
						"§b[POM] §cMining Speed not found. Enable Mining Speed in /tab → Stats Widget → Shown Stats."));
					warnedMissingSpeed = true;
				}
			} else missingSpeedSamples = 0;
		}
		ticksNeeded = calculateTicks(client);
		if (tick % 5 != 0) return;
		PlayerInfo info = client.getConnection().getPlayerInfo(client.player.getUUID());
		if (info != null && info.getLatency() >= 0) {
			if (sampleCount == PING_SAMPLES.length) totalPingMs -= PING_SAMPLES[sampleCursor];
			else sampleCount++;
			PING_SAMPLES[sampleCursor] = info.getLatency();
			totalPingMs += info.getLatency();
			sampleCursor = (sampleCursor + 1) % PING_SAMPLES.length;
			averagePingMs = (double) totalPingMs / sampleCount;
		}
	}

	/** The reference starts the clock in MultiPlayerGameMode.startDestroyBlock. */
	public static void startBreak(BlockPos pos) {
		Minecraft client = Minecraft.getInstance();
		if (!active() || client.player == null) return;
		breakingTarget = pos.immutable();
		startedAtTick = client.player.tickCount;
		ticksElapsed = 0;
	}

	private static int calculateTicks(Minecraft client) {
		if (target == null || (!miningTool && !RaTAddonClient.CONFIG.pingOffsetCustomStats) || miningSpeed <= 0) return 0;
		Integer hardness = strength(client.level.getBlockState(target));
		if (hardness == null) return 0;
		double speed = miningSpeed;
		String name = blockName(client.level.getBlockState(target));
		if (name.contains("stained_glass")) speed += RaTAddonClient.CONFIG.pingOffsetExtraGemstoneSpeed;
		else if (name.endsWith("_block") || name.equals("obsidian"))
			speed += RaTAddonClient.CONFIG.pingOffsetExtraOreSpeed;
		else if (hardness >= 500)
			speed += RaTAddonClient.CONFIG.pingOffsetExtraMetalSpeed;
		if (speedBoostActive) speed *= speedBoostMultiplier;
		if (RaTAddonClient.CONFIG.pingOffsetPrecisionMining && aimingAtPrecision(client)) speed *= 1.3;
		if (speed >= hardness * 30.0) return 0;
		int rawTicks = Math.max(4, (int) (hardness * 30.0 / speed));
		double tps = currentTps();
		double pingMs = currentPing();
		return (int) Math.max(4, rawTicks - (pingMs / 1000.0 * tps) / 2.0
			+ RaTAddonClient.CONFIG.pingOffsetTickMargin);
	}

	private static boolean isMiningTool(Minecraft client, ItemStack stack) {
		if (stack.isEmpty()) return false;
		Item.TooltipContext context = Item.TooltipContext.of(client.player.registryAccess());
		for (var line : stack.getTooltipLines(context, client.player, TooltipFlag.ADVANCED)) {
			String text = line.getString();
			if (text.contains("DRILL") || text.contains("GAUNTLET") || text.contains("PICKAXE")) return true;
		}
		return false;
	}

	private static void readSpeedBoost(Minecraft client, ItemStack stack) {
		if (speedBoostActive && System.currentTimeMillis() >= speedBoostUntilMs) speedBoostActive = false;
		if (!miningTool) return;
		var lines = stack.getTooltipLines(Item.TooltipContext.of(client.player.registryAccess()),
			client.player, TooltipFlag.ADVANCED);
		boolean found = false;
		boolean cooldownFound = false;
		boolean multiplierFound = false;
		speedBoostCooldownTicks = 0;
		speedBoostMultiplier = 1.0;
		for (var line : lines) {
			String value = line.getString();
			if (value.contains("Ability: Mining Speed")) { found = true; continue; }
			if (!found) continue;
			Matcher cooldown = MSB_COOLDOWN.matcher(value);
			if (!cooldownFound && cooldown.find()) {
				try { speedBoostCooldownTicks = (int) (Double.parseDouble(cooldown.group(1)) * 20); }
				catch (NumberFormatException ignored) { }
				cooldownFound = true;
			}
			Matcher multiplier = MSB_MULTIPLIER.matcher(value);
			if (!multiplierFound && multiplier.find()) {
				try { speedBoostMultiplier = 1.0 + Double.parseDouble(multiplier.group(1)) / 100.0; }
				catch (NumberFormatException ignored) { }
				multiplierFound = true;
			}
		}
	}

	public static void onParticle(ClientboundLevelParticlesPacket packet) {
		if (!active() || !RaTAddonClient.CONFIG.pingOffsetPrecisionMining || target == null) return;
		if (packet.particle().getType() != ParticleTypes.HAPPY_VILLAGER
			&& packet.particle().getType() != ParticleTypes.CRIT) return;
		Vec3 point = new Vec3(packet.x(), packet.y(), packet.z());
		if (new AABB(target).inflate(0.12).contains(point)) {
			precisionParticle = point;
			precisionParticleAtMs = System.currentTimeMillis();
		}
	}

	private static boolean aimingAtPrecision(Minecraft client) {
		if (precisionParticle == null || System.currentTimeMillis() - precisionParticleAtMs > 250L
			|| !client.options.keyAttack.isDown()) return false;
		Vec3 eye = client.player.getEyePosition();
		Vec3 sight = client.player.getViewVector(1.0F);
		Vec3 toward = precisionParticle.subtract(eye);
		double length = toward.dot(sight);
		return length >= 0 && precisionParticle.distanceTo(eye.add(sight.scale(length))) <= 0.3;
	}

	private static double readMiningSpeed(Minecraft client) {
		if (RaTAddonClient.CONFIG.pingOffsetCustomStats) return RaTAddonClient.CONFIG.pingOffsetCustomSpeed;
		for (PlayerInfo info : client.getConnection().getListedOnlinePlayers()) {
			if (info.getTabListDisplayName() == null) continue;
			Matcher match = SPEED.matcher(info.getTabListDisplayName().getString());
			if (match.find()) {
				try { return Double.parseDouble(match.group(1).replace(",", "")); }
				catch (NumberFormatException ignored) { return -1; }
			}
		}
		return -1;
	}

	private static boolean showing(Minecraft client) {
		return eligible(client)
			&& (RaTAddonClient.CONFIG.pingOffsetOutline || RaTAddonClient.CONFIG.pingOffsetHighlight);
	}

	private static boolean eligible(Minecraft client) {
		return active() && target != null && client.level != null
			&& (miningTool || RaTAddonClient.CONFIG.pingOffsetCustomStats)
			&& ticksNeeded > 0 && (!RaTAddonClient.CONFIG.pingOffsetMsbOnly || speedBoostActive)
			&& client.hitResult instanceof BlockHitResult hit && hit.getBlockPos().equals(target);
	}

	public static int progressStage(int stage) {
		if (stage < 0 || stage > 9 || RaTAddonClient.CONFIG == null
			|| !RaTAddonClient.CONFIG.pingOffsetProgress || !eligible(Minecraft.getInstance())) return stage;
		int progress = (int) Math.clamp(1.0 + (double) ticksElapsed / ticksNeeded * 8.0, 1.0, 9.0);
		return progress;
	}

	private static boolean active() {
		return RaTAddonClient.CONFIG != null && RaTAddonClient.CONFIG.pingOffsetMiner
			&& (RaTAddonClient.CONFIG.pingOffsetCustomStats || SkyblockLocation.inSkyblock()
			&& RaTAddonClient.CONFIG.pingOffsetIslands.getOrDefault(
				LocationManager.currentArea().name(), defaultIsland(LocationManager.currentArea())));
	}

	public static boolean defaultIsland(LocationManager.Island island) {
		return switch (island) {
			case GOLD_MINE, DEEP_CAVERNS, DWARVEN_MINES, CRYSTAL_HOLLOWS, MINESHAFT -> true;
			default -> false;
		};
	}

	public static List<String> supportedBlocks() {
		return STRENGTH.keySet().stream().sorted().toList();
	}

	private static double currentTps() {
		if (RaTAddonClient.CONFIG.pingOffsetCustomStats) return RaTAddonClient.CONFIG.pingOffsetCustomTps;
		return lastServerTickNs == 0 ? 20.0
			: System.nanoTime() - lastServerTickNs < 1_000_000_000L ? serverTps : 0.0;
	}

	private static double currentPing() {
		if (RaTAddonClient.CONFIG.pingOffsetCustomStats) return RaTAddonClient.CONFIG.pingOffsetCustomPing;
		return System.nanoTime() - lastRttNs < 30_000_000_000L ? measuredRttMs : averagePingMs;
	}

	private static Integer strength(BlockState state) {
		String name = blockName(state);
		if (!RaTAddonClient.CONFIG.pingOffsetBlocks.getOrDefault(name, true)) return null;
		return STRENGTH.get(name);
	}

	private static String blockName(BlockState state) {
		String name = state.getBlock().getDescriptionId().replace("block.minecraft.", "");
		return name.endsWith("_pane") ? name.substring(0, name.length() - "_pane".length()) : name;
	}

	/** Server tick packets are already observed by RatAddons' connection mixin. */
	public static synchronized void serverTick(int pingId) {
		if (pingId == lastServerPingId) return;
		lastServerPingId = pingId;
		long now = System.nanoTime();
		if (lastServerTickNs > 0) {
			long intervalNs = now - lastServerTickNs;
			if (intervalNs >= 20_000_000L && intervalNs < 1_000_000_000L) {
				if (serverIntervalCount == SERVER_TICK_INTERVALS.length)
					serverIntervalSum -= SERVER_TICK_INTERVALS[serverIntervalCursor];
				else serverIntervalCount++;
				SERVER_TICK_INTERVALS[serverIntervalCursor] = intervalNs;
				serverIntervalSum += intervalNs;
				serverIntervalCursor = (serverIntervalCursor + 1) % SERVER_TICK_INTERVALS.length;
				serverTps = Math.clamp(1_000_000_000.0 * serverIntervalCount / serverIntervalSum, 0.0, 20.0);
			}
		}
		lastServerTickNs = now;
	}

	/** Debug pong packets measure round-trip latency, separately from server ticks. */
	public static void onPingRtt(long rttMs) {
		if (rttMs < 0 || rttMs > 10_000) return;
		measuredRttMs = measuredRttMs == 0 ? rttMs : measuredRttMs * 0.8 + rttMs * 0.2;
		lastRttNs = System.nanoTime();
	}

	private static synchronized void reset() {
		target = null;
		breakingTarget = null;
		startedAtTick = 0;
		ticksElapsed = 0;
		ticksNeeded = 0;
		miningSpeed = -1;
		miningTool = false;
		missingSpeedSamples = 0;
		warnedMissingSpeed = false;
		speedBoostActive = false;
		speedBoostUntilMs = 0;
		speedBoostMultiplier = 1.0;
		speedBoostCooldownTicks = 0;
		precisionParticle = null;
		precisionParticleAtMs = 0;
		averagePingMs = 0;
		measuredRttMs = 0;
		lastRttNs = 0;
		sampleCount = 0;
		sampleCursor = 0;
		totalPingMs = 0;
		serverTps = 20.0;
		lastServerTickNs = 0;
		lastServerPingId = 0;
		serverIntervalCount = 0;
		serverIntervalCursor = 0;
		serverIntervalSum = 0;
		tick = 0;
	}
}
