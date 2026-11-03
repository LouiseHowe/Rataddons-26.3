package me.rattokens.rataddon.feature;

import me.rattokens.rataddon.RaTAddonClient;
import me.rattokens.rataddon.util.SkyblockLocation;
import me.rattokens.rataddon.util.WorldRender;
import me.rattokens.rataddon.util.ColorOptions;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLevelEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.message.v1.ClientReceiveMessageEvents;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderContext;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.status.ChunkStatus;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/** Quoi-style corpse waypoints, fossil block boxes, and Mineshaft mob boxes. */
public final class MineshaftEsp {
	private static final Map<BlockPos, CorpseEsp.CorpseKind> CORPSES = new HashMap<>();
	private static final Set<BlockPos> LOOTED = new HashSet<>();
	private static final Set<BlockPos> FOSSILS = new HashSet<>();
	private static final Map<Long, LevelChunk> SCANNED = new HashMap<>();
	private static boolean wasActive;
	private static int tick;
	private static int chunkCursor;

	private MineshaftEsp() {}

	public static void init() {
		ClientTickEvents.END_CLIENT_TICK.register(MineshaftEsp::tick);
		ClientLevelEvents.AFTER_CLIENT_LEVEL_CHANGE.register((client, level) -> reset());
		ClientReceiveMessageEvents.GAME.register((message, overlay) -> onChat(message.getString()));
		LevelRenderEvents.END_MAIN.register(MineshaftEsp::render);
	}

	private static boolean active() {
		return RaTAddonClient.CONFIG != null && RaTAddonClient.CONFIG.mineshaftEsp && SkyblockLocation.inMineshaft();
	}

	private static void reset() {
		CORPSES.clear();
		LOOTED.clear();
		FOSSILS.clear();
		SCANNED.clear();
		chunkCursor = 0;
		tick = 0;
	}

	private static void tick(Minecraft client) {
		if (!active() || client.level == null || client.player == null) {
			if (wasActive) reset();
			wasActive = false;
			return;
		}
		wasActive = true;
		if (++tick % 5 == 0 && RaTAddonClient.CONFIG.mineshaftCorpseWaypoints) {
			for (Entity entity : client.level.entitiesForRendering()) {
				if (entity instanceof ArmorStand stand && entity.isAlive()) {
					CorpseEsp.CorpseKind kind = CorpseEsp.fromHelmet(stand);
					BlockPos pos = stand.blockPosition();
					if (kind != null && !LOOTED.contains(pos)) CORPSES.putIfAbsent(pos.immutable(), kind);
				}
			}
		}
		if (RaTAddonClient.CONFIG.mineshaftFossilEsp) scanNextChunk(client);
	}

	private static void scanNextChunk(Minecraft client) {
		int radius = Math.max(2, client.options.getEffectiveRenderDistance());
		int side = radius * 2 + 1;
		int index = chunkCursor++ % (side * side);
		int x = client.player.chunkPosition().x() + index % side - radius;
		int z = client.player.chunkPosition().z() + index / side - radius;
		LevelChunk chunk = client.level.getChunkSource().getChunk(x, z, ChunkStatus.FULL, false);
		if (chunk == null) return;
		long key = ((long) x << 32) ^ (z & 0xFFFFFFFFL);
		if (SCANNED.get(key) == chunk) return;
		if (SCANNED.put(key, chunk) != null) FOSSILS.removeIf(pos -> (pos.getX() >> 4) == x && (pos.getZ() >> 4) == z);
		BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
		for (int y = client.level.getMinY(); y < client.level.getMaxY(); y++) {
			for (int dx = 0; dx < 16; dx++) for (int dz = 0; dz < 16; dz++) {
				pos.set(x * 16 + dx, y, z * 16 + dz);
				if (isFossil(chunk.getBlockState(pos))) FOSSILS.add(pos.immutable());
			}
		}
	}

	private static boolean isFossil(net.minecraft.world.level.block.state.BlockState state) {
		return state.is(Blocks.QUARTZ_BLOCK) || state.is(Blocks.QUARTZ_STAIRS) || state.is(Blocks.QUARTZ_SLAB);
	}

	/** Called after client block updates to remove mined fossils. */
	public static void onBlockUpdate(BlockPos pos, net.minecraft.world.level.block.state.BlockState state) {
		if (active() && RaTAddonClient.CONFIG.mineshaftFossilEsp && FOSSILS.contains(pos) && !isFossil(state))
			FOSSILS.remove(pos);
	}

	private static void onChat(String message) {
		if (!active() || !RaTAddonClient.CONFIG.mineshaftHideLootedCorpses
			|| !RaTAddonClient.CONFIG.mineshaftCorpseWaypoints) return;
		String text = message.trim().toUpperCase(Locale.ROOT);
		Minecraft client = Minecraft.getInstance();
		if (client.player == null) return;
		for (CorpseEsp.CorpseKind kind : CorpseEsp.CorpseKind.values()) {
			if (!text.equals(kind.name() + " CORPSE LOOT!")) continue;
			BlockPos closest = null;
			double distance = 25.0;
			for (var entry : CORPSES.entrySet()) {
				if (entry.getValue() != kind) continue;
				double candidate = client.player.distanceToSqr(Vec3.atCenterOf(entry.getKey()));
				if (candidate <= distance) { closest = entry.getKey(); distance = candidate; }
			}
			if (closest != null) {
				CORPSES.remove(closest);
				LOOTED.add(closest);
			}
			return;
		}
	}

	private static void render(LevelRenderContext context) {
		if (!active()) return;
		Minecraft client = Minecraft.getInstance();
		if (client.player == null || client.level == null) return;
		List<WorldRender.BoxOverlay> boxes = new ArrayList<>();
		if (RaTAddonClient.CONFIG.mineshaftCorpseWaypoints) {
			for (var entry : CORPSES.entrySet()) {
				BlockPos pos = entry.getKey();
				CorpseEsp.CorpseKind kind = entry.getValue();
					int color = kind == CorpseEsp.CorpseKind.VANGUARD ? 0xFF55FFFF : kind.color;
					boxes.add(new WorldRender.BoxOverlay(new AABB(pos), color, 2.0F, 50));
				Vec3 textPos = Vec3.atCenterOf(pos).add(0, 2, 0);
				float scale = (float) (0.0125 * (0.5 + Math.sqrt(client.player.distanceToSqr(textPos)) / 10.0));
				WorldRender.text(context, textPos, Component.literal(kind.label), scale, true, color);
			}
		}
		if (RaTAddonClient.CONFIG.mineshaftFossilEsp) {
			for (BlockPos pos : FOSSILS)
				boxes.add(new WorldRender.BoxOverlay(new AABB(pos), 0xFFFFAA00, 2.0F, 60));
		}
		if (RaTAddonClient.CONFIG.mineshaftMobEsp) {
			for (Entity entity : client.level.entitiesForRendering()) {
				Mob type = mob(entity);
				if (type != null && RaTAddonClient.CONFIG.mineshaftMobs.getOrDefault(type.name(), true)) {
					int color = ColorOptions.argb(RaTAddonClient.CONFIG.mineshaftMobColors.getOrDefault(type.name(), 5));
					boxes.add(new WorldRender.BoxOverlay(entity.getBoundingBox(), color, 2.0F, 50));
				}
			}
		}
		WorldRender.boxOverlays(context, boxes);
	}

	public enum Mob {
		GLACITE_BOWMAN, GLACITE_CAVER, GLACITE_MAGE, LITTLEFOOT, GLACITE_MUTT;

		public String label() {
			String[] words = name().toLowerCase(Locale.ROOT).split("_");
			StringBuilder label = new StringBuilder();
			for (String word : words) {
				if (!label.isEmpty()) label.append(' ');
				label.append(Character.toUpperCase(word.charAt(0))).append(word.substring(1));
			}
			return label.toString();
		}
	}

	private static Mob mob(Entity entity) {
		if (!entity.isAlive()) return null;
		if (EntityType.getKey(entity.getType()).getPath().equals("wolf")) return Mob.GLACITE_MUTT;
		if (!(entity instanceof Player)) return null;
		String name = (entity.getCustomName() == null ? entity.getDisplayName() : entity.getCustomName())
			.getString().trim().toLowerCase(Locale.ROOT);
		return switch (name) {
			case "glacite bowman" -> Mob.GLACITE_BOWMAN;
			case "glacite caver" -> Mob.GLACITE_CAVER;
			case "glacite mage" -> Mob.GLACITE_MAGE;
			case "littlefoot" -> Mob.LITTLEFOOT;
			case "glacite mutt" -> Mob.GLACITE_MUTT;
			default -> null;
		};
	}
}
