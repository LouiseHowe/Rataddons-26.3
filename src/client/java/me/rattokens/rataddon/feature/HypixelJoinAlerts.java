package me.rattokens.rataddon.feature;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import me.rattokens.rataddon.RaTAddonClient;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.minecraft.client.Minecraft;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Locale;
import java.util.UUID;

/** Reports a Hypixel connection once, and listens for join announcements. */
public final class HypixelJoinAlerts {
	private static final HttpClient HTTP = HttpClient.newBuilder()
		.connectTimeout(Duration.ofSeconds(8)).build();
	private static final String DEFAULT_SERVER_URL = "https://presence.rattokens.asia";
	private static final int MAX_BANNER_BYTES = 4 * 1024 * 1024;
	private static boolean reportedThisConnection;

	private HypixelJoinAlerts() {}

	public static void init() {
		ClientPlayConnectionEvents.JOIN.register((handler, sender, client) -> {
			if (!reportedThisConnection && client.getCurrentServer() != null
				&& isHypixelAddress(client.getCurrentServer().ip)) {
				reportedThisConnection = true;
				URI server = serverUri();
				String token = token();
				UUID accountUuid = client.getUser().getProfileId();
				if (server != null && token != null && accountUuid != null) {
					String eventId = UUID.randomUUID().toString();
					Thread.startVirtualThread(() -> reportJoin(server, token, eventId, accountUuid));
				}
			}
		});
		ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> reportedThisConnection = false);
		Thread.startVirtualThread(HypixelJoinAlerts::receiveLoop);
	}

	static boolean isHypixelAddress(String address) {
		if (address == null) return false;
		String host = address.trim().toLowerCase(Locale.ROOT);
		int port = host.indexOf(':');
		if (port >= 0) host = host.substring(0, port);
		if (host.endsWith(".")) host = host.substring(0, host.length() - 1);
		return host.equals("hypixel.net") || host.endsWith(".hypixel.net");
	}

	private static URI serverUri() {
		String raw = RaTAddonClient.CONFIG.hypixelJoinServerUrl;
		if (raw == null || raw.isBlank()) raw = DEFAULT_SERVER_URL;
		try {
			URI uri = URI.create(raw.trim());
			String host = uri.getHost();
			String scheme = uri.getScheme();
			String path = uri.getPath();
			if (host == null || uri.getUserInfo() != null || uri.getQuery() != null
				|| uri.getFragment() != null || !(path == null || path.isEmpty() || path.equals("/"))) {
				return null;
			}
			boolean loopback = host.equalsIgnoreCase("localhost") || host.equals("127.0.0.1");
			if (scheme == null || !scheme.equalsIgnoreCase("https")
				&& !(loopback && scheme.equalsIgnoreCase("http"))) return null;
			return uri;
		} catch (IllegalArgumentException exception) {
			return null;
		}
	}

	private static String token() {
		String value = RaTAddonClient.CONFIG.hypixelJoinToken;
		return value != null && value.length() >= 32 && value.length() <= 128 ? value : null;
	}

	private static void reportJoin(URI server, String token, String eventId, UUID accountUuid) {
		String body = "{\"eventId\":\"" + eventId + "\",\"uuid\":\"" + accountUuid + "\"}";
		HttpRequest request = HttpRequest.newBuilder(server.resolve("/v1/join"))
			.timeout(Duration.ofSeconds(10))
			.header("Authorization", "Bearer " + token)
			.header("Content-Type", "application/json")
			.POST(HttpRequest.BodyPublishers.ofString(body)).build();
		for (int attempt = 0; attempt < 3; attempt++) {
			try {
				HttpResponse<Void> response = HTTP.send(request, HttpResponse.BodyHandlers.discarding());
				if (response.statusCode() >= 200 && response.statusCode() < 300) return;
				if (response.statusCode() < 500) return;
			} catch (InterruptedException exception) {
				Thread.currentThread().interrupt();
				return;
			} catch (IOException exception) {
				RaTAddonClient.LOGGER.debug("Hypixel join report failed: {}", exception.toString());
			}
			try {
				Thread.sleep((attempt + 1) * 2000L);
			} catch (InterruptedException exception) {
				Thread.currentThread().interrupt();
				return;
			}
		}
	}

	private static void receiveLoop() {
		while (!Thread.currentThread().isInterrupted()) {
			URI server = serverUri();
			if (server != null) {
				try {
					HttpRequest request = HttpRequest.newBuilder(server.resolve("/v1/events"))
						.header("Accept", "text/event-stream")
						.GET().build();
					HttpResponse<InputStream> response = HTTP.send(request, HttpResponse.BodyHandlers.ofInputStream());
					try (InputStream stream = response.body()) {
						if (response.statusCode() == 200) readEvents(stream, server);
					}
				} catch (InterruptedException exception) {
					Thread.currentThread().interrupt();
					return;
				} catch (IOException exception) {
					RaTAddonClient.LOGGER.debug("Hypixel join event stream disconnected: {}", exception.toString());
				}
			}
			try {
				Thread.sleep(5000L);
			} catch (InterruptedException exception) {
				Thread.currentThread().interrupt();
				return;
			}
		}
	}

	private static void readEvents(InputStream stream, URI server) throws IOException {
		BufferedReader reader = new BufferedReader(new InputStreamReader(stream, StandardCharsets.UTF_8));
		String event = "";
		String data = "";
		String line;
		while ((line = reader.readLine()) != null) {
			if (!server.equals(serverUri())) return;
			if (line.isEmpty()) {
				if (event.equals("join") && !data.isEmpty()) onJoinEvent(data);
				event = "";
				data = "";
			} else if (line.startsWith("event: ")) {
				event = line.substring(7);
			} else if (line.startsWith("data: ") && line.length() < 2048) {
				data = line.substring(6);
			}
		}
	}

	private static void onJoinEvent(String data) {
		try {
			JsonObject event = JsonParser.parseString(data).getAsJsonObject();
			if (!"hypixel_join".equals(event.get("type").getAsString())) return;
			String name = event.get("name").getAsString();
			if (!name.matches("[A-Za-z0-9_]{1,16}")) return;
			String uuid = UUID.fromString(event.get("uuid").getAsString()).toString();
			URI server = serverUri();
			Minecraft.getInstance().execute(() -> {
				HypixelJoinBanner.prepare(name, uuid);
				if (server != null) Thread.startVirtualThread(() -> downloadBanner(server, uuid, name));
			});
		} catch (RuntimeException ignored) {
			// Ignore malformed relay events without affecting the game client.
		}
	}

	private static void downloadBanner(URI server, String uuid, String name) {
		byte[] png = null;
		try {
			HttpRequest request = HttpRequest.newBuilder(server.resolve("/v1/banners/" + uuid + ".png"))
				.timeout(Duration.ofSeconds(10)).GET().build();
			HttpResponse<InputStream> response = HTTP.send(request, HttpResponse.BodyHandlers.ofInputStream());
			try (InputStream stream = response.body()) {
				if (response.statusCode() == 200
					&& response.headers().firstValue("Content-Type").orElse("").startsWith("image/png")) {
					byte[] bytes = stream.readNBytes(MAX_BANNER_BYTES + 1);
					if (bytes.length <= MAX_BANNER_BYTES) png = bytes;
				}
			}
		} catch (InterruptedException exception) {
			Thread.currentThread().interrupt();
			return;
		} catch (IOException exception) {
			RaTAddonClient.LOGGER.debug("Join banner download failed: {}", exception.toString());
		}
		if (png != null) {
			byte[] result = png;
			Minecraft.getInstance().execute(() -> HypixelJoinBanner.show(name, uuid, result));
		}
	}
}
