package me.rattokens.rataddon.util;

import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.world.scores.DisplaySlot;
import net.minecraft.world.scores.Objective;
import net.minecraft.world.scores.PlayerScoreEntry;
import net.minecraft.world.scores.PlayerTeam;

import java.util.ArrayList;
import java.util.List;

public final class SkyblockLocation {
	private SkyblockLocation() {
	}

	public static boolean inGarden() {
		return LocationManager.isCurrentArea(LocationManager.Island.GARDEN);
	}

	public static boolean inMineshaft() {
		return LocationManager.currentArea() == LocationManager.Island.MINESHAFT;
	}

	public static boolean inSkyblock() {
		return LocationManager.inSkyblock();
	}

	public static boolean inDungeon() {
		return LocationManager.isCurrentArea(LocationManager.Island.DUNGEON);
	}

	public static boolean inKuudra() {
		return LocationManager.isCurrentArea(LocationManager.Island.KUUDRA);
	}

	public static List<String> sidebarStrings() {
		Minecraft client = Minecraft.getInstance();
		if (client.level == null) {
			return List.of();
		}
		Objective sidebar = client.level.getScoreboard().getDisplayObjective(DisplaySlot.SIDEBAR);
		if (sidebar == null) {
			return List.of();
		}
		List<String> lines = new ArrayList<>();
		for (PlayerScoreEntry entry : client.level.getScoreboard().listPlayerScores(sidebar)) {
			if (entry.isHidden()) continue;
			PlayerTeam team = client.level.getScoreboard().getPlayersTeam(entry.owner());
			String visible = team == null ? "" : team.getPlayerPrefix().getString() + team.getPlayerSuffix().getString();
			if (visible.isBlank()) visible = PlayerTeam.formatNameForTeam(team, entry.ownerName()).getString();
			lines.add(visible);
		}
		return lines;
	}

	public static String zone() {
		for (String line : sidebarStrings()) {
			int marker = line.indexOf('⏣');
			if (marker >= 0) {
				return line.substring(marker + 1).trim();
			}
		}
		return "";
	}

	public static String area() {
		Minecraft client = Minecraft.getInstance();
		if (client.getConnection() != null) {
			for (PlayerInfo info : client.getConnection().getListedOnlinePlayers()) {
				String text = info.getTabListDisplayName() == null ? info.getProfile().name()
					: info.getTabListDisplayName().getString();
				int marker = text.indexOf("Area:");
				int length = "Area:".length();
				if (marker < 0) { marker = text.indexOf("Dungeon:"); length = "Dungeon:".length(); }
				if (marker >= 0) {
					return ChatFormatting.stripFormatting(text.substring(marker + length)).trim();
				}
			}
		}
		return zone();
	}

}
