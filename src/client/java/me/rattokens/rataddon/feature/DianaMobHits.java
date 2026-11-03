package me.rattokens.rataddon.feature;

import me.rattokens.rataddon.RaTAddonClient;
import me.rattokens.rataddon.util.LocationManager;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLevelEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.hud.VanillaHudElements;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;
import net.minecraft.network.protocol.game.ClientboundAnimatePacket;
import net.minecraft.network.protocol.game.ClientboundDamageEventPacket;
import net.minecraft.network.protocol.game.ClientboundEntityEventPacket;
import net.minecraft.resources.Identifier;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.entity.player.Player;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** XJA 4.0.3 Diana health-label HUD and player melee-hit reconciliation. */
public final class DianaMobHits {
	private static final long BOSS_TTL_MS = 60_000, DEAD_TTL_MS = 3_000;
	private static final long SWING_MS = 300, FALLBACK_WAIT_MS = 450, RECONCILE_MS = 1_500;
	private static final int MAX_BOSSES = 8, MAX_HITS = 128, MAX_HUD_HITS = 12, MAX_LABELS = 16;
	private static final Pattern HEALTH_LABEL = Pattern.compile("(?i)([\\d,.]+)([KMB]?)\\s*/");
	private static final Pattern HEALTH_PAIR = Pattern.compile("(?i)([\\d,.]+\\s*[KMB]?)\\s*/\\s*([\\d,.]+\\s*[KMB]?)");
	private static final Pattern LEVEL_PREFIX = Pattern.compile("\\[Lv\\d+]\\s*", Pattern.CASE_INSENSITIVE);
	private static final Pattern CORRUPTED_PREFIX = Pattern.compile("Corrupted\\s+", Pattern.CASE_INSENSITIVE);
	private static final Map<UUID, BossSession> BOSSES = new LinkedHashMap<>();
	private static final Map<Integer, UUID> BOSS_BY_ENTITY_ID = new HashMap<>();
	private static final Map<Integer, HealthLabel> HEALTH_LABELS = new LinkedHashMap<>();
	private static final Map<UUID, Swing> RECENT_SWINGS = new LinkedHashMap<>();
	private static final ArrayDeque<PendingFallback> PENDING = new ArrayDeque<>();
	private static boolean bootstrapped;

	private DianaMobHits() {}

	public static void init() {
		ClientTickEvents.END_CLIENT_TICK.register(DianaMobHits::tick);
		ClientLevelEvents.AFTER_CLIENT_LEVEL_CHANGE.register((client, level) -> clear());
		HudElementRegistry.attachElementBefore(VanillaHudElements.CHAT,
			Identifier.fromNamespaceAndPath(RaTAddonClient.MOD_ID, "diana_mob_hits"), (graphics, delta) -> {
			Minecraft client = Minecraft.getInstance();
			if (!active() || client.level == null || client.player == null || client.gui.screen() instanceof HudEditorScreen
				|| KittyHud.Slot.DIANA_MOB_HITS.setting().hide) return;
			List<Component> lines = lines(client);
			if (!lines.isEmpty()) KittyHud.drawLines(graphics, client.font, KittyHud.Slot.DIANA_MOB_HITS,
				lines, client.getWindow().getGuiScaledWidth(), client.getWindow().getGuiScaledHeight());
		});
	}

	private static boolean active() {
		return RaTAddonClient.CONFIG != null && RaTAddonClient.CONFIG.dianaMobHits
			&& LocationManager.currentArea() == LocationManager.Island.HUB;
	}

	private static void tick(Minecraft client) {
		if (!active() || client.level == null || client.player == null) {
			if (bootstrapped || !BOSSES.isEmpty() || !HEALTH_LABELS.isEmpty() || !RECENT_SWINGS.isEmpty() || !PENDING.isEmpty()) clear();
			return;
		}
		if (!bootstrapped) {
			bootstrapped = true;
			for (Entity entity : client.level.entitiesForRendering()) observe(entity);
		}
		long now = System.currentTimeMillis();
		HEALTH_LABELS.entrySet().removeIf(entry -> {
			Entity entity = client.level.getEntity(entry.getKey());
			return !(entity instanceof ArmorStand) || !entity.isAlive() || entity.isRemoved()
				|| !entity.getUUID().equals(entry.getValue().uuid);
		});
		while (!PENDING.isEmpty() && now - PENDING.peekFirst().at >= FALLBACK_WAIT_MS) {
			PendingFallback pending = PENDING.removeFirst();
			BossSession boss = BOSSES.get(pending.bossUuid);
			if (boss != null && boss.deadAt == 0 && !boss.attributedDamageSeen && now - boss.startedAt < BOSS_TTL_MS)
				increment(boss, pending.player, true, pending.at);
		}
		RECENT_SWINGS.values().removeIf(swing -> now - swing.at > RECONCILE_MS);
		Iterator<BossSession> it = BOSSES.values().iterator();
		while (it.hasNext()) {
			BossSession boss = it.next();
			Entity actor = client.level.getEntity(boss.entityId);
			if (boss.deadAt == 0 && actor instanceof LivingEntity living && living.isDeadOrDying()) onDeath(actor);
			long age = now - (boss.deadAt > 0 ? boss.deadAt : boss.startedAt);
			if (age >= (boss.deadAt > 0 ? DEAD_TTL_MS : BOSS_TTL_MS)) {
				BOSS_BY_ENTITY_ID.remove(boss.entityId, boss.uuid);
				PENDING.removeIf(pending -> pending.bossUuid.equals(boss.uuid));
				it.remove();
			}
		}
	}

	public static void onEntityChanged(Entity entity) {
		if (!active() || entity == null || entity.isRemoved() || !entity.isAlive()) return;
		observe(entity);
		if (entity instanceof LivingEntity && !(entity instanceof ArmorStand)) {
			Minecraft client = Minecraft.getInstance();
			if (client.level != null) observe(client.level.getEntity(entity.getId() + 1));
		}
	}

	private static void observe(Entity entity) {
		if (entity == null || entity.isRemoved() || !entity.isAlive()) return;
		if (entity instanceof ArmorStand stand) {
			Component text = stand.getCustomName();
			String raw = text == null ? "" : text.getString();
			Kind kind = kind(raw);
			if (kind != null && raw.indexOf('\uE07E') >= 0 && HEALTH_LABEL.matcher(raw).find()) {
				HEALTH_LABELS.remove(stand.getId());
				while (HEALTH_LABELS.size() >= MAX_LABELS) HEALTH_LABELS.remove(HEALTH_LABELS.keySet().iterator().next());
				HEALTH_LABELS.put(stand.getId(), new HealthLabel(stand.getUUID(), kind, text.copy()));
			} else HEALTH_LABELS.remove(stand.getId());
			if (kind != null) {
				Minecraft client = Minecraft.getInstance();
				Entity actor = client.level == null ? null : client.level.getEntity(stand.getId() - 1);
				if (actor instanceof LivingEntity && !(actor instanceof ArmorStand) && actor.isAlive() && !actor.isRemoved()) {
					BossSession boss = session(actor, kind, System.currentTimeMillis());
					boss.labelId = stand.getId(); boss.labelUuid = stand.getUUID();
				}
			}
		} else if (entity instanceof LivingEntity) {
			Kind kind = kind(entity.getCustomName() == null ? entity.getName().getString() : entity.getCustomName().getString());
			if (kind != null) session(entity, kind, System.currentTimeMillis());
		}
	}

	public static void onLocalAttack(Player player, Entity target) {
		Minecraft client = Minecraft.getInstance();
		if (!active() || player == null || player != client.player || target == null || target.isRemoved()) return;
		Kind kind = classify(target);
		if (kind == null) return;
		long now = System.currentTimeMillis();
		BossSession boss = session(target, kind, now);
		if (boss.deadAt == 0) {
			boss.lastLocalAttackAt = now;
			increment(boss, identity(player), true, now);
		}
	}

	public static void onDamage(ClientboundDamageEventPacket packet) {
		Minecraft client = Minecraft.getInstance();
		if (!active() || packet == null || client.level == null) return;
		Entity target = client.level.getEntity(packet.entityId());
		Kind kind = classify(target);
		if (kind == null) return;
		long now = System.currentTimeMillis();
		DamageSource source = packet.getSource(client.level);
		Entity attacker = source.getEntity();
		if (!(attacker instanceof Player) && source.getDirectEntity() instanceof Player) attacker = source.getDirectEntity();
		if (attacker instanceof Player player) {
			if (attacker == target || isBossActor(player)) return;
			boolean recent = recentMelee(player, target, now);
			if (source.is(DamageTypes.PLAYER_ATTACK) || source.getDirectEntity() == attacker && recent) {
				BossSession boss = session(target, kind, now);
				if (boss.deadAt > 0) return;
				boss.attributedDamageSeen = true;
				PENDING.removeIf(pending -> pending.bossUuid.equals(boss.uuid));
				PlayerIdentity who = identity(player);
				HitEntry entry = boss.hits.get(who.uuid);
				if (entry != null && entry.reconcile(now)) entry.update(who);
				else increment(boss, who, false, now);
				return;
			}
		}
		onUnattributedHurt(target, kind, now);
	}

	public static void onAnimate(ClientboundAnimatePacket packet) {
		Minecraft client = Minecraft.getInstance();
		if (!active() || packet == null || packet.getAction() != 0 || client.level == null) return;
		if (!(client.level.getEntity(packet.getId()) instanceof Player player) || player.isRemoved()
			|| client.player != null && player.getUUID().equals(client.player.getUUID()) || isBossActor(player)) return;
		long now = System.currentTimeMillis();
		PlayerIdentity who = identity(player);
		RECENT_SWINGS.remove(who.uuid);
		while (RECENT_SWINGS.size() >= 128) RECENT_SWINGS.remove(RECENT_SWINGS.keySet().iterator().next());
		RECENT_SWINGS.put(who.uuid, new Swing(who, now));
		for (BossSession boss : BOSSES.values()) {
			Entity actor = liveBoss(boss, client);
			if (boss.deadAt == 0 && !boss.attributedDamageSeen && actor != null && player.distanceToSqr(actor) < 20.0) {
				while (PENDING.size() >= 256) PENDING.removeFirst();
				PENDING.addLast(new PendingFallback(boss.uuid, who, now));
			}
		}
	}

	public static void onEntityEvent(ClientboundEntityEventPacket packet) {
		Minecraft client = Minecraft.getInstance();
		if (!active() || packet == null || client.level == null) return;
		Entity target = packet.getEntity(client.level);
		Kind kind = classify(target);
		if (kind == null) return;
		if (packet.getEventId() == 2) onUnattributedHurt(target, kind, System.currentTimeMillis());
		else if (packet.getEventId() == 3) onDeath(target);
	}

	public static void onEntitiesRemoved(Iterable<Integer> ids) {
		if (ids == null) return;
		for (int id : ids) {
			HEALTH_LABELS.remove(id);
			for (BossSession boss : BOSSES.values()) if (boss.labelId == id) { boss.labelId = -1; boss.labelUuid = null; }
			UUID uuid = BOSS_BY_ENTITY_ID.remove(id);
			if (uuid != null) {
				BossSession boss = BOSSES.get(uuid);
				if (boss != null && boss.entityId == id) {
					BOSSES.remove(uuid);
					PENDING.removeIf(pending -> pending.bossUuid.equals(uuid));
				}
			}
		}
	}

	private static void onUnattributedHurt(Entity target, Kind kind, long now) {
		if (target == null || BOSSES.containsKey(target.getUUID())) return;
		BossSession boss = session(target, kind, now);
		Swing closest = closestSwing(target, now);
		if (closest != null) {
			increment(boss, closest.player, true, closest.at);
			RECENT_SWINGS.remove(closest.player.uuid, closest);
		}
	}

	private static void onDeath(Entity target) {
		if (target == null) return;
		BossSession boss = BOSSES.get(target.getUUID());
		if (boss == null || boss.deadAt > 0) return;
		boss.deadAt = System.currentTimeMillis();
		PENDING.removeIf(pending -> pending.bossUuid.equals(boss.uuid));
		if (RaTAddonClient.CONFIG.dianaMobHitsChatSummary) {
			Component summary = summary(boss);
			Minecraft client = Minecraft.getInstance();
			if (summary != null && client.player != null) client.player.sendSystemMessage(summary);
		}
	}

	private static BossSession session(Entity entity, Kind kind, long now) {
		BossSession boss = BOSSES.get(entity.getUUID());
		if (boss == null) {
			while (BOSSES.size() >= MAX_BOSSES) {
				BossSession oldest = BOSSES.values().iterator().next();
				BOSSES.remove(oldest.uuid); BOSS_BY_ENTITY_ID.remove(oldest.entityId, oldest.uuid);
				PENDING.removeIf(pending -> pending.bossUuid.equals(oldest.uuid));
			}
			boss = new BossSession(entity.getUUID(), entity.getId(), kind, now);
			BOSSES.put(boss.uuid, boss);
		} else if (boss.entityId != entity.getId()) {
			BOSS_BY_ENTITY_ID.remove(boss.entityId, boss.uuid); boss.entityId = entity.getId();
		}
		BOSS_BY_ENTITY_ID.put(entity.getId(), boss.uuid);
		boss.lastActivityAt = now;
		return boss;
	}

	private static void increment(BossSession boss, PlayerIdentity who, boolean reconcilable, long at) {
		HitEntry hit = boss.hits.get(who.uuid);
		if (hit == null) {
			if (boss.hits.size() >= MAX_HITS) return;
			hit = new HitEntry(who);
			boss.hits.put(who.uuid, hit);
		}
		hit.update(who);
		hit.hits++;
		if (reconcilable) hit.remember(at);
		boss.lastActivityAt = at;
	}

	private static boolean recentMelee(Player player, Entity target, long now) {
		Minecraft client = Minecraft.getInstance();
		if (client.player != null && player.getUUID().equals(client.player.getUUID())) {
			BossSession boss = BOSSES.get(target.getUUID());
			return boss != null && boss.lastLocalAttackAt > 0 && now - boss.lastLocalAttackAt <= SWING_MS;
		}
		Swing swing = RECENT_SWINGS.get(player.getUUID());
		return swing != null && now - swing.at <= SWING_MS && player.distanceToSqr(target) < 20.0;
	}

	private static Swing closestSwing(Entity target, long now) {
		Minecraft client = Minecraft.getInstance();
		if (client.level == null) return null;
		Swing closest = null; double distance = Double.POSITIVE_INFINITY;
		for (Swing swing : RECENT_SWINGS.values()) {
			Entity player = client.level.getEntity(swing.player.entityId);
			if (now - swing.at > SWING_MS || !(player instanceof Player) || player.isRemoved()
				|| !player.getUUID().equals(swing.player.uuid)) continue;
			double candidate = player.distanceToSqr(target);
			if (candidate < 20.0 && candidate < distance) { closest = swing; distance = candidate; }
		}
		return closest;
	}

	private static Entity liveBoss(BossSession boss, Minecraft client) {
		Entity entity = client.level == null ? null : client.level.getEntity(boss.entityId);
		return entity != null && !entity.isRemoved() && entity.getUUID().equals(boss.uuid) ? entity : null;
	}

	private static Kind classify(Entity entity) {
		if (entity == null || entity.isRemoved()) return null;
		Kind custom = kind(entity.getCustomName() == null ? "" : entity.getCustomName().getString());
		return custom != null ? custom : kind(entity.getName().getString());
	}

	private static Kind kind(String text) {
		String clean = ChatFormatting.stripFormatting(text == null ? "" : text);
		if (clean == null) return null;
		clean = clean.toLowerCase(Locale.ROOT).replaceAll("\\s+", " ");
		if (clean.contains("inquisitor")) return Kind.INQUISITOR;
		return clean.contains("king minos") ? Kind.KING_MINOS : null;
	}

	private static boolean isBossActor(Player player) {
		UUID uuid = BOSS_BY_ENTITY_ID.get(player.getId());
		return player.getUUID().equals(uuid) || BOSSES.containsKey(player.getUUID()) || classify(player) != null;
	}

	private static PlayerIdentity identity(Player player) {
		return new PlayerIdentity(player.getUUID(), player.getId(), player.getGameProfile().name());
	}

	private static List<Component> lines(Minecraft client) {
		BossSession selected = null;
		for (BossSession boss : BOSSES.values()) {
			if (boss.deadAt > 0 || liveBoss(boss, client) == null) continue;
			if (selected == null || compare(boss, selected) > 0) selected = boss;
		}
		if (selected == null) return List.of();
		HealthLabel label = null, unique = null; boolean ambiguous = false;
		for (Map.Entry<Integer, HealthLabel> entry : HEALTH_LABELS.entrySet()) {
			HealthLabel candidate = entry.getValue();
			Entity entity = client.level.getEntity(entry.getKey());
			if (candidate.kind != selected.kind || !(entity instanceof ArmorStand) || !entity.isAlive()
				|| entity.isRemoved() || !entity.getUUID().equals(candidate.uuid)) continue;
			if (entry.getKey() == selected.labelId) label = candidate;
			if (unique == null) unique = candidate; else ambiguous = true;
		}
		if (label == null && !ambiguous) label = unique;
		List<Component> result = new ArrayList<>();
		if (label == null) result.add(Component.literal(selected.kind.label + " ?").withStyle(ChatFormatting.RED));
		else {
			Matcher match = HEALTH_PAIR.matcher(label.text.getString());
			result.add(match.find() ? compactNameTag(label.text) : Component.literal(selected.kind.label + " ?").withStyle(ChatFormatting.RED));
		}
		List<HitEntry> hits = sortedHits(selected);
		for (int i = 0; i < Math.min(MAX_HUD_HITS, hits.size()); i++) {
			HitEntry hit = hits.get(i);
			MutableComponent name = Component.literal(hit.name).withStyle(ChatFormatting.AQUA);
			if (hit.uuid.equals(client.player.getUUID())) name.withStyle(ChatFormatting.BOLD);
			result.add(Component.empty().append(name)
				.append(Component.literal("  ").withStyle(ChatFormatting.DARK_GRAY))
				.append(Component.literal("x" + hit.hits).withStyle(ChatFormatting.YELLOW)));
		}
		return result;
	}

	private static Component compactNameTag(Component source) {
		String plain = source.getString();
		boolean[] keep = new boolean[plain.length()];
		Arrays.fill(keep, true);
		Matcher level = LEVEL_PREFIX.matcher(plain);
		if (level.find()) Arrays.fill(keep, 0, level.end(), false);
		Matcher corrupted = CORRUPTED_PREFIX.matcher(plain);
		while (corrupted.find()) Arrays.fill(keep, corrupted.start(), corrupted.end(), false);
		for (int i = 0; i < plain.length(); i++) {
			if (plain.charAt(i) == '﴾' || plain.charAt(i) == '﴿') {
				keep[i] = false;
				if (i > 0 && Character.isWhitespace(plain.charAt(i - 1))) keep[i - 1] = false;
			}
		}
		int[] offset = {0};
		source.visit((style, run) -> {
			if (style.isObfuscated()) Arrays.fill(keep, Math.min(keep.length, offset[0]),
				Math.min(keep.length, offset[0] + run.length()), false);
			offset[0] += run.length();
			return Optional.empty();
		}, Style.EMPTY);
		boolean sawText = false, sawSpace = false;
		for (int i = 0; i < keep.length; i++) {
			if (!keep[i]) continue;
			if (!Character.isWhitespace(plain.charAt(i))) { sawText = true; sawSpace = false; }
			else if (sawText && !sawSpace) sawSpace = true;
			else keep[i] = false;
		}
		for (int i = keep.length - 1; i >= 0; i--) {
			if (keep[i]) { if (Character.isWhitespace(plain.charAt(i))) keep[i] = false; break; }
		}
		MutableComponent result = Component.empty();
		int[] start = {0};
		source.visit((style, run) -> {
			int first = -1;
			for (int i = 0; i <= run.length(); i++) {
				int absolute = start[0] + i;
				boolean include = i < run.length() && absolute < keep.length && keep[absolute] && !style.isObfuscated();
				if (include && first < 0) first = i;
				if (!include && first >= 0) { result.append(Component.literal(run.substring(first, i)).setStyle(style)); first = -1; }
			}
			start[0] += run.length();
			return Optional.empty();
		}, Style.EMPTY);
		return result;
	}

	private static int compare(BossSession a, BossSession b) {
		int first = Long.compare(a.lastLocalAttackAt, b.lastLocalAttackAt);
		if (first != 0) return first;
		int second = Long.compare(a.lastActivityAt, b.lastActivityAt);
		return second != 0 ? second : Long.compare(a.startedAt, b.startedAt);
	}

	private static List<HitEntry> sortedHits(BossSession boss) {
		List<HitEntry> hits = new ArrayList<>(boss.hits.values());
		hits.removeIf(hit -> hit.name == null || hit.name.isBlank() || hit.hits <= 0 || boss.kind.actorName.equalsIgnoreCase(hit.name));
		hits.sort(Comparator.comparingInt((HitEntry hit) -> hit.hits).reversed().thenComparing(hit -> hit.name, String.CASE_INSENSITIVE_ORDER));
		return hits;
	}

	private static Component summary(BossSession boss) {
		List<HitEntry> hits = sortedHits(boss);
		if (hits.isEmpty()) return null;
		MutableComponent result = Component.literal(boss.kind.label + " hits").withStyle(ChatFormatting.LIGHT_PURPLE)
			.append(Component.literal(": ").withStyle(ChatFormatting.GRAY));
		int length = boss.kind.label.length() + 7;
		boolean first = true;
		for (HitEntry hit : hits) {
			int addition = hit.name.length() + ("x" + hit.hits).length() + 1 + (first ? 0 : 2);
			if (length + addition > 180) break;
			if (!first) result.append(Component.literal(", ").withStyle(ChatFormatting.GRAY));
			result.append(Component.literal(hit.name).withStyle(ChatFormatting.AQUA))
				.append(Component.literal(" x" + hit.hits).withStyle(ChatFormatting.YELLOW));
			length += addition; first = false;
		}
		return first ? null : result;
	}

	private static void clear() {
		BOSSES.clear(); BOSS_BY_ENTITY_ID.clear(); HEALTH_LABELS.clear(); RECENT_SWINGS.clear(); PENDING.clear();
		bootstrapped = false;
	}

	private enum Kind {
		INQUISITOR("Inquisitor", "Minos Inquisitor"), KING_MINOS("King Minos", "King Minos");
		final String label, actorName;
		Kind(String label, String actorName) { this.label = label; this.actorName = actorName; }
	}
	private static final class BossSession {
		final UUID uuid; final Kind kind; final long startedAt;
		int entityId, labelId = -1; UUID labelUuid;
		boolean attributedDamageSeen;
		long lastLocalAttackAt, lastActivityAt, deadAt;
		final Map<UUID, HitEntry> hits = new LinkedHashMap<>();
		BossSession(UUID uuid, int entityId, Kind kind, long now) {
			this.uuid = uuid; this.entityId = entityId; this.kind = kind;
			this.startedAt = now; this.lastActivityAt = now;
		}
	}
	private static final class HitEntry {
		final UUID uuid; String name; int entityId, hits;
		final ArrayDeque<Long> reconcilable = new ArrayDeque<>();
		HitEntry(PlayerIdentity who) { uuid = who.uuid; update(who); }
		void update(PlayerIdentity who) { name = who.name; entityId = who.entityId; }
		void remember(long at) { while (reconcilable.size() >= 16) reconcilable.removeFirst(); reconcilable.addLast(at); }
		boolean reconcile(long now) {
			while (!reconcilable.isEmpty() && now - reconcilable.peekFirst() > RECONCILE_MS) reconcilable.removeFirst();
			if (reconcilable.isEmpty()) return false;
			reconcilable.removeFirst(); return true;
		}
	}
	private record HealthLabel(UUID uuid, Kind kind, Component text) {}
	private record PlayerIdentity(UUID uuid, int entityId, String name) {}
	private record Swing(PlayerIdentity player, long at) {}
	private record PendingFallback(UUID bossUuid, PlayerIdentity player, long at) {}
}
