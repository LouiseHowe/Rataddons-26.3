package me.rattokens.rataddon.feature;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import me.rattokens.rataddon.RaTAddonClient;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Minecraft;

import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

public final class ChatMacros {
	private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
	private static final Path PATH = FabricLoader.getInstance().getConfigDir().resolve("rataddon-macros.json");
	private static final int MAX_LENGTH = 160;
	private static final List<Macro> MACROS = new ArrayList<>();

	private ChatMacros() {}

	public static void init() {
		if (!Files.exists(PATH)) return;
		try (Reader reader = Files.newBufferedReader(PATH, StandardCharsets.UTF_8)) {
			MacroFile file = GSON.fromJson(reader, MacroFile.class);
			if (file == null || file.macros == null) return;
			for (Macro macro : file.macros) {
				if (macro != null) MACROS.add(new Macro(sanitize(macro.trigger), sanitize(macro.command), macro.active));
			}
		} catch (Exception e) {
			RaTAddonClient.LOGGER.warn("Could not load chat macros", e);
		}
	}

	public static List<Macro> list() {
		return List.copyOf(MACROS);
	}

	public static int add() {
		MACROS.add(new Macro("", "", true));
		save();
		return MACROS.size() - 1;
	}

	public static void delete(int index) {
		if (index < 0 || index >= MACROS.size()) return;
		MACROS.remove(index);
		save();
	}

	public static void set(int index, String trigger, String command, boolean active) {
		if (index < 0 || index >= MACROS.size()) return;
		MACROS.set(index, new Macro(sanitize(trigger), sanitize(command), active));
		save();
	}

	public static void handleChat(String message) {
		if (!RaTAddonClient.CONFIG.chatMacros) return;
		for (Macro macro : MACROS) {
			String trigger = macro.trigger.trim();
			String command = macro.command.trim().replaceFirst("^/", "");
			if (!macro.active || trigger.isEmpty() || command.isEmpty()) continue;
			try {
				if (Pattern.compile(trigger).matcher(message).find()) {
					var connection = Minecraft.getInstance().getConnection();
					if (connection != null) connection.sendCommand(command);
				}
			} catch (PatternSyntaxException ignored) {
				// Invalid expressions are shown in the manager and cannot match chat.
			}
		}
	}

	private static String sanitize(String value) {
		return value == null ? "" : value.replace('\r', ' ').replace('\n', ' ').substring(0, Math.min(value.length(), MAX_LENGTH));
	}

	private static void save() {
		try {
			Files.createDirectories(PATH.getParent());
			try (Writer writer = Files.newBufferedWriter(PATH, StandardCharsets.UTF_8)) {
				GSON.toJson(new MacroFile(MACROS), writer);
			}
		} catch (Exception e) {
			RaTAddonClient.LOGGER.warn("Could not save chat macros", e);
		}
	}

	public record Macro(String trigger, String command, boolean active) {}
	private record MacroFile(List<Macro> macros) {}
}
