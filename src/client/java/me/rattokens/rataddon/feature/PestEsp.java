package me.rattokens.rataddon.feature;

import com.mojang.authlib.properties.Property;
import me.rattokens.rataddon.RaTAddonClient;
import me.rattokens.rataddon.util.ColorOptions;
import me.rattokens.rataddon.util.SkyblockLocation;
import me.rattokens.rataddon.util.WorldRender;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.ResolvableProfile;
import net.minecraft.world.phys.Vec3;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;

public final class PestEsp {
	public static final int OUTLINE_COLOR = 0xFFB62F00;
	private static final Set<String> LEGACY_OWNER_IDS = Set.of(
		"5bef72fa-93bb-3e97-8dbe-6595ee7c64b4", "8842a457-5e25-3ad0-958e-dfbebbb1129c",
		"b9acc486-f7c7-39ed-9258-d24942e9e8dc", "6b159ec3-fa0b-3049-a0a7-37027bc22968",
		"d4903c23-3dad-3880-900e-d05c1d519497", "58feded7-33e7-310a-862f-7c4c392e8397",
		"1894b3f5-35d5-359d-b74d-370a57c8f175", "61bcdaae-56f5-302e-9f05-11345b9610c9"
	);
	private static final Set<String> TEXTURE_HASHES = Set.of(
		"feb3a834baf781f93b88cb8e9d288c170c33057d4a07938d0b90ae433f9387fd",
		"802a9e374ed4e4fa45dc87cf1ed937289e47b5f3988c41cb8994c046b9966ea7",
		"1bc1a5a281d3b1747fe3dd21416852518f27fd138733053916c363c25d371684",
		"32ab834e616c8ec9941a8bd4133141eaa1225af3a786c1e681d65d053ecdeaed",
		"94eee78fe99c892c8c272f3ff015005daa95898c2e5ca90f6b1898512738ffb3",
		"49105077050f4733f43ecb5080fcf7fba2ad83b0da11abdc472e45ab94b7b733",
		"55f1cc9ce74a2700b73ec8ae7e0171e797149a607e2a91b5d3f8c077fddfd194",
		"d8a915a26c19d446fe40c7e942127c1bd59e0b7d1699ef3c6e026769f125dc73",
		"1b5797457ff7f86d88a26641000ea0d127d4a72ea5f1450701057e1d02bbf2db",
		"21f7dbdd7f2643d7d96c13d743fcc53a22645fbd29b3d6852162682e15d7bdf7",
		"b19750ee59d84da041b16cee92a8ad38c9c8ece8113c598723a15f2181055a34",
		"1fbce9db52dcc7ff0c1286612ce7f840529fd72e44bb7cf388c15bad9bb81cf0",
		"74ba88b1e5a24f581d4c2b0f1ea5d9476b01e815efb2e036fa84d5e9da7e6a3e",
		"923d1283ae2623e06b98cf9888e6830bd544994835db6801462df827fac9a1d2",
		"681e9573f230f265c882d6b34c06a9ecf1df7db4fdd4dd73fb3603348e4827f0",
		"925c5852cbbac84e7c10bd9ffac5948264c44b04a515e1c30068058523570507",
		"79a893c72c85aba8ade3e167f238b9b25ce9eb4ae533187c8043dfc195ac87c6"
	);
	private static final Set<Integer> PEST_ENTITY_IDS = new HashSet<>();
	private static boolean wasActive;
	private static int scanTicker;

	private PestEsp() {
	}

	public static void init() {
		ClientTickEvents.END_CLIENT_TICK.register(PestEsp::tick);
		LevelRenderEvents.END_MAIN.register(PestEsp::render);
	}

	private static void tick(Minecraft client) {
		boolean active = active();
		if (!active) {
			if (wasActive) {
				PEST_ENTITY_IDS.clear();
			}
			wasActive = false;
			scanTicker = 0;
			return;
		}
		wasActive = true;
		if (client.level == null || ++scanTicker < 5) {
			return;
		}
		scanTicker = 0;
		Set<Integer> visible = new HashSet<>();
		for (Entity entity : client.level.entitiesForRendering()) {
			if (entity instanceof ArmorStand stand && entity.isAlive()
				&& matchesPestHead(stand.getItemBySlot(EquipmentSlot.HEAD))) {
				visible.add(entity.getId());
			}
		}
		PEST_ENTITY_IDS.clear();
		PEST_ENTITY_IDS.addAll(visible);
	}

	public static int outlineColor(Entity entity) {
		return active() && RaTAddonClient.CONFIG.gardenPestEsp
			&& entity != null && PEST_ENTITY_IDS.contains(entity.getId())
			? ColorOptions.argb(RaTAddonClient.CONFIG.gardenPestEspColor) : 0;
	}

	private static void render(net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderContext context) {
		Minecraft client = Minecraft.getInstance();
		if (!active() || !RaTAddonClient.CONFIG.gardenPestEsp || client.player == null || client.level == null) {
			return;
		}
		int color = ColorOptions.argb(RaTAddonClient.CONFIG.gardenPestEspColor);
		for (int id : PEST_ENTITY_IDS) {
			Entity entity = client.level.getEntity(id);
			if (entity instanceof ArmorStand && entity.isAlive()) {
				Vec3 center = entity.position().add(0.0, Math.max(0.5, entity.getBbHeight()), 0.0);
				WorldRender.box(context, net.minecraft.world.phys.AABB.ofSize(center, 0.8, 0.8, 0.8),
					color, 4.0F, true);
			}
		}
		if (!RaTAddonClient.CONFIG.gardenPestLine) {
			return;
		}
		Vec3 eye = client.player.getEyePosition();
		Entity nearest = null;
		double nearestDistance = Double.MAX_VALUE;
		for (int id : PEST_ENTITY_IDS) {
			Entity entity = client.level.getEntity(id);
			if (!(entity instanceof ArmorStand) || !entity.isAlive()) {
				continue;
			}
			double distance = entity.distanceToSqr(eye);
			if (distance < nearestDistance) {
				nearestDistance = distance;
				nearest = entity;
			}
		}
		if (nearest == null) {
			return;
		}
		float yaw = (float) Math.toRadians(context.levelState().cameraRenderState.yRot);
		float pitch = (float) Math.toRadians(context.levelState().cameraRenderState.xRot);
		Vec3 camera = context.levelState().cameraRenderState.pos;
		Vec3 start = camera.add(new Vec3(
			-Math.sin(yaw) * Math.cos(pitch),
			-Math.sin(pitch),
			Math.cos(yaw) * Math.cos(pitch)
		).scale(2.0));
		WorldRender.line(context, start, nearest.getEyePosition(), color, 3.0F, true);
	}
	public static boolean matchesPestHead(ItemStack stack) {
		if (stack == null || stack.isEmpty()) {
			return false;
		}
		ResolvableProfile profile = stack.get(DataComponents.PROFILE);
		if (profile == null) {
			return false;
		}
		if (profile.partialProfile().id() != null
			&& LEGACY_OWNER_IDS.contains(profile.partialProfile().id().toString().toLowerCase(Locale.ROOT))) {
			return true;
		}
		for (Property texture : profile.partialProfile().properties().get("textures")) {
			if (TEXTURE_HASHES.contains(sha256(texture.value()))) {
				return true;
			}
		}
		return false;
	}

	private static boolean active() {
		return RaTAddonClient.CONFIG.gardenPestEsp && SkyblockLocation.inGarden();
	}

	private static String sha256(String value) {
		try {
			byte[] bytes = MessageDigest.getInstance("SHA-256")
				.digest(value.getBytes(StandardCharsets.UTF_8));
			return HexFormat.of().formatHex(bytes);
		} catch (Exception exception) {
			throw new IllegalStateException("Unable to fingerprint Pest head texture", exception);
		}
	}
}
