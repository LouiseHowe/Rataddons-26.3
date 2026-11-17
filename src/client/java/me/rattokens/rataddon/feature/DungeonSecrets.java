package me.rattokens.rataddon.feature;

import me.rattokens.rataddon.RaTAddonClient;
import me.rattokens.rataddon.util.SkyblockItem;
import me.rattokens.rataddon.util.SkyblockLocation;
import me.rattokens.rataddon.util.WorldRender;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderContext;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.alchemy.PotionContents;
import net.minecraft.world.item.alchemy.Potions;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.SkullBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.VoxelShape;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/** Dungeon secret markers based on Devonian's click/pickup events and locked-chest matching. */
public final class DungeonSecrets {
	public enum Style { OUTLINE, HIGHLIGHT }

	private static final Set<UUID> SECRET_SKULLS = Set.of(
		UUID.fromString("2865274b-3097-394e-8149-ec629c72d850"),
		UUID.fromString("fed95410-aba1-39df-9b95-1d4f361eb66e"));
	private static final Set<String> SECRET_ITEMS = Set.of(
		"POTION", "DUNGEON_DECOY", "INFLATABLE_JERRY", "SPIRIT_LEAP", "DUNGEON_TRAP",
		"TRAINING_WEIGHTS", "DEFUSE_KIT", "DUNGEON_CHEST_KEY", "TREASURE_TALISMAN",
		"REVIVE_STONE", "ARCHITECT_FIRST_DRAFT", "CANDYCOMB");
	private static final Map<BlockPos, Marker> MARKERS = new LinkedHashMap<>();
	private static final Map<Integer, Long> PICKED_ENTITY_IDS = new HashMap<>();
	private static final int OUTLINE_TICKS = 20;
	private static final long HIGHLIGHT_MILLIS = 3000;
	private static final int OUTLINE_COLOR = 0xFF00FFFF;
	private static final int LOCKED_OUTLINE_COLOR = 0xFFFF0000;
	private static BlockPos lastClickedBlock;
	private static long tick;
	private static ClientLevel world;

	private DungeonSecrets() {}

	public static void init() {
		ClientTickEvents.END_CLIENT_TICK.register(DungeonSecrets::tick);
		LevelRenderEvents.END_MAIN.register(DungeonSecrets::render);
	}

	private static boolean active() {
		return RaTAddonClient.CONFIG != null && RaTAddonClient.CONFIG.dungeonSecretHighlights
			&& SkyblockLocation.inDungeon();
	}

	private static void tick(Minecraft client) {
		if (client.level != world || !active()) {
			MARKERS.clear();
			PICKED_ENTITY_IDS.clear();
			lastClickedBlock = null;
			world = client.level;
		}
		tick++;
		boolean outline = RaTAddonClient.CONFIG.dungeonSecretHighlightStyle == Style.OUTLINE.ordinal();
		long now = System.currentTimeMillis();
		MARKERS.values().removeIf(marker -> marker.type == SecretType.ITEM || outline
			? tick - marker.createdTick >= OUTLINE_TICKS
			: now - marker.createdAt >= HIGHLIGHT_MILLIS);
		PICKED_ENTITY_IDS.values().removeIf(createdTick -> tick - createdTick >= OUTLINE_TICKS);
	}

	/** Called on the outgoing block-use packet, before the server confirms the interaction. */
	public static void onBlockClick(BlockHitResult hit) {
		if (!active()) return;
		Minecraft client = Minecraft.getInstance();
		if (client.level == null || hit == null) return;
		BlockPos pos = hit.getBlockPos().immutable();
		lastClickedBlock = pos;
		BlockState state = client.level.getBlockState(pos);
		SecretType type;
		if (state.is(Blocks.CHEST)) type = SecretType.CHEST;
		else if (state.is(Blocks.TRAPPED_CHEST)) type = SecretType.TRAPPED_CHEST;
		else if (state.is(Blocks.LEVER)) type = SecretType.LEVER;
		else if (state.is(Blocks.PLAYER_HEAD) && client.level.getBlockEntity(pos) instanceof SkullBlockEntity skull
			&& skull.getOwnerProfile() != null
			&& SECRET_SKULLS.contains(skull.getOwnerProfile().partialProfile().id())) type = SecretType.WITHER_ESSENCE;
		else return;
		Marker marker = new Marker(pos, null, type, tick, System.currentTimeMillis());
		if (RaTAddonClient.CONFIG.dungeonSecretHighlightStyle == Style.HIGHLIGHT.ordinal()) MARKERS.put(pos, marker);
		else MARKERS.putIfAbsent(pos, marker);
		if (RaTAddonClient.CONFIG.dungeonSecretClickSound)
			play(SoundEvents.NOTE_BLOCK_XYLOPHONE.value(), 0.70F);
	}

	/** Devonian's whitelist, with only a healing POTION counting as a secret. */
	public static void onItemPickup(ItemEntity entity) {
		if (!active() || entity == null || PICKED_ENTITY_IDS.containsKey(entity.getId())) return;
		ItemStack stack = entity.getItem();
		String id = SkyblockItem.id(stack);
		if (!SECRET_ITEMS.contains(id)) return;
		if (id.equals("POTION")) {
			PotionContents contents = stack.get(DataComponents.POTION_CONTENTS);
			if (contents == null || contents.potion().orElse(null) != Potions.HEALING) return;
		}
		PICKED_ENTITY_IDS.put(entity.getId(), tick);
		Vec3 origin = new Vec3(entity.xo, entity.yo, entity.zo);
		BlockPos key = new BlockPos((int) origin.x, (int) origin.y, (int) origin.z);
		MARKERS.putIfAbsent(key, new Marker(key, origin, SecretType.ITEM, tick, System.currentTimeMillis()));
		if (RaTAddonClient.CONFIG.dungeonSecretClickSound)
			play(SoundEvents.NOTE_BLOCK_XYLOPHONE.value(), 0.70F);
	}

	/** Match the exact server message to the most recently clicked block position. */
	public static void onChat(String message) {
		if (!active() || !"That chest is locked!".equals(message)) return;
		Marker marker = MARKERS.get(lastClickedBlock);
		if (marker != null && marker.type.isChest()) marker.locked = true;
		if (RaTAddonClient.CONFIG.dungeonSecretLockedSound) play(SoundEvents.ANVIL_PLACE);
	}

	private static void play(SoundEvent sound) {
		play(sound, 1.0F);
	}

	private static void play(SoundEvent sound, float pitch) {
		Minecraft client = Minecraft.getInstance();
		if (client.player != null) client.player.playSound(sound, 1.0F, pitch);
	}

	private static void render(LevelRenderContext context) {
		if (!active() || Minecraft.getInstance().level == null) return;
		Minecraft client = Minecraft.getInstance();
		boolean outline = RaTAddonClient.CONFIG.dungeonSecretHighlightStyle == Style.OUTLINE.ordinal();
		long now = System.currentTimeMillis();
		for (Marker marker : MARKERS.values()) {
			if (marker.type == SecretType.ITEM) {
				if (tick - marker.createdTick >= OUTLINE_TICKS) continue;
				Vec3 pos = marker.itemOrigin;
				AABB cube = new AABB(pos.x + .25, pos.y, pos.z + .25,
					pos.x + .75, pos.y + .5, pos.z + .75);
				WorldRender.filledBox(context, cube, 0x3200FFFF, false);
				WorldRender.box(context, cube, OUTLINE_COLOR, 1.0F, true);
				continue;
			}
			if (outline && tick - marker.createdTick >= OUTLINE_TICKS) continue;
			if (!outline && now - marker.createdAt >= HIGHLIGHT_MILLIS) continue;
			BlockPos pos = marker.position;
			int color = outline ? (marker.locked ? LOCKED_OUTLINE_COLOR : OUTLINE_COLOR)
				: marker.locked ? 0xFFFF5555 : marker.type.highlightColor;
			if (outline) {
				VoxelShape shape = client.level.getBlockState(pos).getShape(client.level, pos);
				Vec3 origin = Vec3.atLowerCornerOf(pos);
				WorldRender.shapeOutline(context, shape, origin, color, 1.0F, true);
				for (AABB part : shape.toAabbs())
					WorldRender.filledBox(context, part.move(origin),
						marker.locked ? 0x32FF0000 : 0x3200FFFF, false);
			} else {
				Vec3 camera = context.levelState().cameraRenderState.pos;
				double distSq = camera.distanceToSqr(Vec3.atCenterOf(pos));
				int alpha = (int) (Math.clamp(0.1 + 0.005 * distSq, 0.2, 1.0) * 255);
				WorldRender.filledBox(context, new AABB(pos), (alpha << 24) | (color & 0xFFFFFF), true);
			}
			String label = marker.locked ? "Locked" : "Clicked " + marker.type.label;
			WorldRender.text(context, Vec3.atCenterOf(pos), Component.literal(label), 0.025F, true, color);
		}
	}

	private enum SecretType {
		CHEST("Chest", 0xFF55FF55),
		TRAPPED_CHEST("Trapped Chest", 0xFF00AA00),
		LEVER("Lever", 0xFFFFFF55),
		WITHER_ESSENCE("Wither Essence", 0xFFFF55FF),
		ITEM("Secret", OUTLINE_COLOR);

		final String label;
		final int highlightColor;
		SecretType(String label, int highlightColor) {
			this.label = label;
			this.highlightColor = highlightColor;
		}
		boolean isChest() { return this == CHEST || this == TRAPPED_CHEST; }
	}

	private static final class Marker {
		final BlockPos position;
		final Vec3 itemOrigin;
		final SecretType type;
		final long createdTick;
		final long createdAt;
		boolean locked;

		Marker(BlockPos position, Vec3 itemOrigin, SecretType type, long createdTick, long createdAt) {
			this.position = position;
			this.itemOrigin = itemOrigin;
			this.type = type;
			this.createdTick = createdTick;
			this.createdAt = createdAt;
		}
	}
}
