package me.rattokens.rataddon.util;

import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLevelEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.cubemob.MagmaCube;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.Vec3;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

public final class KuudraState {
	public static final String SUPPLIES_START_MESSAGE = "[NPC] Elle: Okay adventurers, I will go and fish up Kuudra!";
	public enum Phase { NONE, SUPPLIES, BUILD, STUN, DPS }

	public static Phase phase = Phase.NONE;
	public static boolean isDead;
	public static Entity kuudraEntity;
	public record Dropoff(String name, Vec3 pos, int color) {}
	public static final List<Dropoff> DROPOFFS = List.of(
		new Dropoff("Shop", new Vec3(-98, 79, -113), 0xFFFF5555),
		new Dropoff("X", new Vec3(-106, 79, -113), 0xFFFFAA00),
		new Dropoff("xCannon", new Vec3(-110, 79, -106), 0xFFFFFFFF),
		new Dropoff("Equals", new Vec3(-106, 79, -99), 0xFF5555FF),
		new Dropoff("Slash", new Vec3(-98, 79, -99), 0xFF55FF55),
		new Dropoff("Triangle", new Vec3(-94, 79, -106), 0xFFFF55FF));
	public static final List<Dropoff> activeDropoffs = new ArrayList<>();
	public static String square = "None";
	public static int waypointRevision;
	private static final Pattern PARTY_SQUARE = Pattern.compile("No (X Cannon|Triangle|X|Equals|Slash|xCannon|Shop)");

	private KuudraState() {
	}

	public static void init() {
		ClientLevelEvents.AFTER_CLIENT_LEVEL_CHANGE.register((client, level) -> {
			phase = Phase.NONE;
			isDead = false;
			kuudraEntity = null;
			square = "None";
			activeDropoffs.clear();
			activeDropoffs.addAll(DROPOFFS);
			waypointRevision++;
		});
		ClientTickEvents.END_CLIENT_TICK.register(KuudraState::tick);
	}

	private static void tick(Minecraft client) {
		if (client.player == null || client.level == null) {
			return;
		}
		if (client.player.getY() < 20 && phase == Phase.STUN) {
			phase = Phase.DPS;
		}
		if (!kuudra()) {
			kuudraEntity = null;
			return;
		}
		kuudraEntity = null;
		for (Entity entity : client.level.entitiesForRendering()) {
			if (entity instanceof MagmaCube cube && cube.isAlive() && cube.getSize() == 30
				&& cube.getAttributeBaseValue(Attributes.MAX_HEALTH) == 100_000.0) {
				kuudraEntity = cube;
				break;
			}
		}
	}

	public static void handleChat(String unformatted) {
		if (unformatted.contains(SUPPLIES_START_MESSAGE)) {
			phase = Phase.SUPPLIES;
		} else if (unformatted.equals("[NPC] Elle: OMG! Great work collecting my supplies!")) {
			phase = Phase.BUILD;
		} else if (unformatted.equals("[NPC] Elle: Phew! The Ballista is finally ready! It should be strong enough to tank Kuudra's blows now!")) {
			phase = Phase.STUN;
		}
		var match = PARTY_SQUARE.matcher(unformatted);
		if (match.find()) {
			String nextSquare = match.group(1).equals("X Cannon") ? "xCannon" : match.group(1);
			if (!square.equals(nextSquare)) {
				square = nextSquare;
				waypointRevision++;
			}
		}
		Minecraft client = Minecraft.getInstance();
		if (client.player != null
			&& unformatted.contains(client.player.getName().getString() + " was FINAL KILLED by Kuudra!")) {
			isDead = true;
		}
	}

	public static void onEntityAdded(Entity entity) {
		if (!(entity instanceof ArmorStand stand) || phase != Phase.SUPPLIES) return;
		TickScheduler.schedule(1, () -> {
			if (stand.isRemoved() || !(stand.getMainHandItem().is(Items.SPRUCE_PLANKS)
				|| stand.getMainHandItem().is(Items.ACACIA_LOG))) return;
			Dropoff closest = null;
			double distance = 9;
			for (Dropoff dropoff : activeDropoffs) {
				double current = dropoff.pos.distanceToSqr(stand.position());
				if (current < distance) { closest = dropoff; distance = current; }
			}
			if (closest != null && activeDropoffs.remove(closest)) waypointRevision++;
		});
	}

	public static boolean kuudra() {
		return LocationManager.isCurrentArea(LocationManager.Island.KUUDRA);
	}

	public static boolean stun() {
		return phase == Phase.STUN;
	}

	public static boolean dps() {
		return phase == Phase.DPS;
	}
}
