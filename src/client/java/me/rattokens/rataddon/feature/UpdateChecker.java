package me.rattokens.rataddon.feature;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import me.rattokens.rataddon.RaTAddonClient;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class UpdateChecker {
	private static final URI MANIFEST = URI.create("https://presence.rattokens.asia/v1/releases.json");
	private static final String RELEASE_PATH = "/LouiseHowe/RatAddons-Releases/releases/";
	private static final Pattern VERSION = Pattern.compile("^(\\d+)\\.(\\d+)\\.(\\d+)$");
	private static final HttpClient HTTP = HttpClient.newBuilder()
		.connectTimeout(Duration.ofSeconds(5))
		.followRedirects(HttpClient.Redirect.NEVER)
		.build();
	private static boolean checked;

	private UpdateChecker() {
	}

	public static void init() {
		ClientPlayConnectionEvents.JOIN.register((handler, sender, client) -> {
			if (checked) return;
			checked = true;
			Thread.startVirtualThread(() -> check(client));
		});
	}

	private static void check(net.minecraft.client.Minecraft client) {
		try {
			var loader = FabricLoader.getInstance();
			String minecraftVersion = loader.getModContainer("minecraft").orElseThrow()
				.getMetadata().getVersion().getFriendlyString();
			String installedVersion = loader.getModContainer(RaTAddonClient.MOD_ID).orElseThrow()
				.getMetadata().getVersion().getFriendlyString();
			HttpRequest request = HttpRequest.newBuilder(MANIFEST)
				.timeout(Duration.ofSeconds(8))
				.header("Accept", "application/json")
				.GET().build();
			HttpResponse<String> response = HTTP.send(request, HttpResponse.BodyHandlers.ofString());
			if (response.statusCode() != 200 || response.body().length() > 16_384) return;
			JsonObject manifest = JsonParser.parseString(response.body()).getAsJsonObject();
			if (!manifest.has(minecraftVersion) || !manifest.get(minecraftVersion).isJsonObject()) return;
			JsonObject release = manifest.getAsJsonObject(minecraftVersion);
			String latestVersion = release.get("version").getAsString();
			if (!isNewer(latestVersion, installedVersion)) return;
			URI url = URI.create(release.get("url").getAsString());
			if (!"https".equalsIgnoreCase(url.getScheme()) || !"github.com".equalsIgnoreCase(url.getHost())
				|| !url.getPath().startsWith(RELEASE_PATH) || url.getUserInfo() != null) return;
			client.execute(() -> {
				if (client.player == null) return;
				client.player.sendSystemMessage(Component.literal("[RatAddons] ").withStyle(ChatFormatting.AQUA)
					.append(Component.literal("发现适用于 MC " + minecraftVersion + " 的新版本 " + latestVersion + " ")
						.withStyle(ChatFormatting.WHITE))
					.append(Component.literal("[查看更新]").withStyle(style -> style
						.withColor(ChatFormatting.AQUA).withUnderlined(true)
						.withClickEvent(new ClickEvent.OpenUrl(url)))));
			});
		} catch (Exception ignored) {
			// Update checks are optional and must not interrupt joining a server.
		}
	}

	private static boolean isNewer(String latest, String installed) {
		Matcher next = VERSION.matcher(latest);
		Matcher current = VERSION.matcher(installed);
		if (!next.matches() || !current.matches()) return false;
		for (int i = 1; i <= 3; i++) {
			int comparison = Integer.compare(Integer.parseInt(next.group(i)), Integer.parseInt(current.group(i)));
			if (comparison != 0) return comparison > 0;
		}
		return false;
	}
}
