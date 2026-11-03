package me.rattokens.rataddon.util;

import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLevelEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.network.protocol.game.ClientboundPlayerInfoUpdatePacket;
import net.minecraft.network.protocol.game.ClientboundSetObjectivePacket;
import net.minecraft.network.protocol.game.ClientboundSetPlayerTeamPacket;

import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Tracks the SkyBlock island reported by the Tab list, as Kitty does. */
public final class LocationManager {
    public enum Island {
        SINGLE_PLAYER("Singleplayer"), PRIVATE_ISLAND("Private Island"), GARDEN("Garden"),
        SPIDER_DEN("Spider's Den"), CRIMSON_ISLE("Crimson Isle"), THE_END("The End"),
        GOLD_MINE("Gold Mine"), DEEP_CAVERNS("Deep Caverns"), DWARVEN_MINES("Dwarven Mines"),
        CRYSTAL_HOLLOWS("Crystal Hollows"), FARMING_ISLAND("The Farming Islands"),
		THE_PARK("The Park"), DUNGEON("Catacombs"), DUNGEON_HUB("Dungeon Hub"),
		FORGOTTEN_SKULL("Forgotten Skull"),
        HUB("Hub"), DARK_AUCTION("Dark Auction"), JERRY_WORKSHOP("Jerry's Workshop"),
        KUUDRA("Kuudra"), MINESHAFT("Mineshaft"), RIFT("The Rift"),
        BACKWATER_BAYOU("Backwater Bayou"), GALATEA("Galatea"),
        TORRHUS_CANYON("Torrhus Canyon"), SAFARI("Safari"), UNKNOWN("(Unknown)");

        private final String displayName;
        Island(String displayName) { this.displayName = displayName; }
    }

    private static final Pattern LOBBY = Pattern.compile("\\d\\d/\\d\\d/\\d\\d (\\w{0,6}) *");
    private static Island currentArea = Island.UNKNOWN;
    private static boolean inSkyblock;
    private static String lobbyId;

    private LocationManager() {}

    public static void init() {
        ClientLevelEvents.AFTER_CLIENT_LEVEL_CHANGE.register((client, level) -> {
            currentArea = client.hasSingleplayerServer() ? Island.SINGLE_PLAYER : Island.UNKNOWN;
            inSkyblock = false;
            lobbyId = null;
        });
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            if (client.level == null || client.getConnection() == null) return;
            for (PlayerInfo info : client.getConnection().getListedOnlinePlayers()) {
                String text = info.getTabListDisplayName() == null ? info.getProfile().name()
                    : info.getTabListDisplayName().getString();
                if (updateArea(text)) return;
            }
        });
    }

    public static boolean isCurrentArea(Island... areas) {
        if (currentArea == Island.SINGLE_PLAYER) return true;
        for (Island area : areas) if (currentArea == area) return true;
        return false;
    }

    public static Island currentArea() { return currentArea; }
    public static boolean inSkyblock() { return inSkyblock; }
    public static String lobbyId() { return lobbyId; }

    public static void handlePlayerInfo(ClientboundPlayerInfoUpdatePacket packet) {
        for (var entry : packet.entries()) {
            if (entry.displayName() == null) continue;
            if (updateArea(entry.displayName().getString())) return;
        }
    }

    private static boolean updateArea(String text) {
        if (text == null) return false;
        int marker = text.indexOf("Area:");
        int length = "Area:".length();
        if (marker < 0) { marker = text.indexOf("Dungeon:"); length = "Dungeon:".length(); }
        if (marker < 0) return false;
        String area = ChatFormatting.stripFormatting(text.substring(marker + length)).trim().toLowerCase(Locale.ROOT);
        inSkyblock = true;
        currentArea = Island.UNKNOWN;
        for (Island island : Island.values()) {
            if (area.contains(island.displayName.toLowerCase(Locale.ROOT))) {
                currentArea = island;
                break;
            }
        }
        return true;
    }

    public static void handleObjective(ClientboundSetObjectivePacket packet) {
        if ("SBScoreboard".equals(packet.getObjectiveName())) inSkyblock = true;
    }

    public static void handlePlayerTeam(ClientboundSetPlayerTeamPacket packet) {
        if (currentArea != Island.UNKNOWN) return;
        var parameters = packet.getParameters();
        if (parameters.isEmpty()) return;
        String text = (parameters.get().playerPrefix().getString()
            + parameters.get().playerSuffix().getString()).replaceAll("§.", "");
        Matcher match = LOBBY.matcher(text);
        if (match.find()) lobbyId = match.group(1);
    }
}
