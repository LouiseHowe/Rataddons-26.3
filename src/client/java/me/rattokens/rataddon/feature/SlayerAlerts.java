package me.rattokens.rataddon.feature;

import me.rattokens.rataddon.RaTAddonClient;
import me.rattokens.rataddon.util.SkyblockLocation;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.message.v1.ClientReceiveMessageEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.hud.VanillaHudElements;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.world.scores.DisplaySlot;
import net.minecraft.world.scores.Objective;
import net.minecraft.world.scores.PlayerScoreEntry;
import net.minecraft.world.scores.PlayerTeam;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Slayer title behavior adapted from Skysoft (LGPL-3.0-only):
 * https://github.com/Akinsoft/Skysoft/tree/main/src/main/kotlin/com/skysoft/features/slayer
 * Uses RatAddons' existing warning sound in place of Skysoft's alert sounds.
 */
public final class SlayerAlerts {
    private static final Pattern MINIBOSS = Pattern.compile("^SLAYER MINIBOSS! The (.+?) spawned!$");
    private static final Pattern TIER = Pattern.compile(" (I|II|III|IV|V)$");
    private static final SoundEvent WARNING = SoundEvent.createVariableRangeEvent(
        Identifier.fromNamespaceAndPath(RaTAddonClient.MOD_ID, "warning"));
    private static final long DURATION_MS = 2_500L;
    private static final float TITLE_SCALE = 2.7F;
    private static final float SUBTITLE_SCALE = 1.55F;
    private static final float TITLE_Y_OFFSET = -82.0F;
    private static final List<Alert> ACTIVE = new ArrayList<>();
    private static boolean bossActive;
    private static boolean questActive;

    private SlayerAlerts() {}

    public static void init() {
        ClientReceiveMessageEvents.GAME.register((message, overlay) -> {
            if (!overlay) onMessage(message.getString());
        });
        ClientReceiveMessageEvents.CHAT.register((message, signedMessage, sender, params, receptionTimestamp) ->
            onMessage(message.getString()));
        ClientTickEvents.END_CLIENT_TICK.register(SlayerAlerts::tick);
        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> clear());
        HudElementRegistry.attachElementAfter(VanillaHudElements.TITLE_AND_SUBTITLE,
            Identifier.fromNamespaceAndPath(RaTAddonClient.MOD_ID, "slayer_alerts"),
            (graphics, delta) -> render(graphics));
    }

    private static void onMessage(String raw) {
        var config = RaTAddonClient.CONFIG;
        if ((!config.slayerMinibossAlert && !config.slayerMinibossSpawnSound)
            || !SkyblockLocation.inSkyblock()) return;
        String text = ChatFormatting.stripFormatting(raw).trim();
        Matcher match = MINIBOSS.matcher(text);
        if (!match.matches()) return;
        alert("miniboss",
            List.of(new Line(Component.literal(match.group(1).trim())
                    .withStyle(ChatFormatting.RED, ChatFormatting.BOLD), TITLE_SCALE),
                new Line(Component.literal("Miniboss Spawned!")
                    .withStyle(ChatFormatting.GRAY), SUBTITLE_SCALE)),
            config.slayerMinibossAlert, config.slayerMinibossSpawnSound);
    }

    private static void tick(Minecraft client) {
        long now = System.currentTimeMillis();
        ACTIVE.removeIf(alert -> now >= alert.expiresAt);
        if (!SkyblockLocation.inSkyblock() || client.level == null || client.player == null) {
            bossActive = false;
            questActive = false;
            ACTIVE.clear();
            return;
        }
        List<String> lines = scoreboardLines(client);
        int header = lines.indexOf("Slayer Quest");
        String questName = header >= 0 && header + 1 < lines.size() ? lines.get(header + 1) : "";
        boolean nextQuestActive = TIER.matcher(questName).find() && isKnownBoss(questName);
        boolean nextBossActive = nextQuestActive && lines.stream()
            .anyMatch(line -> line.equalsIgnoreCase("Slay the boss!"));
        var config = RaTAddonClient.CONFIG;
        if ((config.slayerBossSpawnAlert || config.slayerBossSpawnSound)
            && questActive && !bossActive && nextBossActive) {
            alert("boss",
                List.of(new Line(Component.literal("Boss Spawned!")
                    .withStyle(ChatFormatting.WHITE, ChatFormatting.BOLD), TITLE_SCALE)),
                config.slayerBossSpawnAlert, config.slayerBossSpawnSound);
        }
        questActive = nextQuestActive;
        bossActive = nextBossActive;
    }

    private static boolean isKnownBoss(String questName) {
        return questName.startsWith("Revenant Horror ")
            || questName.startsWith("Atoned Horror ")
            || questName.startsWith("Tarantula Broodfather ")
            || questName.startsWith("Conjoined Brood ")
            || questName.startsWith("Sven Packmaster ")
            || questName.startsWith("Voidgloom Seraph ")
            || questName.startsWith("Inferno Demonlord ")
            || questName.startsWith("Riftstalker Bloodfiend ");
    }

    private static List<String> scoreboardLines(Minecraft client) {
        Objective objective = client.level.getScoreboard().getDisplayObjective(DisplaySlot.SIDEBAR);
        if (objective == null) return List.of();
        return client.level.getScoreboard().listPlayerScores(objective).stream()
            .filter(entry -> !entry.isHidden())
            .sorted(Comparator.comparingInt(PlayerScoreEntry::value).reversed()
                .thenComparing(PlayerScoreEntry::owner, String.CASE_INSENSITIVE_ORDER))
            .limit(15)
            .map(entry -> {
                PlayerTeam team = client.level.getScoreboard().getPlayersTeam(entry.owner());
                return ChatFormatting.stripFormatting(
                    PlayerTeam.formatNameForTeam(team, Component.empty()).getString()).trim();
            })
            .toList();
    }

    private static void alert(String id, List<Line> lines, boolean showWarn, boolean playSound) {
        if (showWarn) {
            ACTIVE.removeIf(existing -> existing.id.equals(id));
            long now = System.currentTimeMillis();
            ACTIVE.add(new Alert(id, lines, now, now + DURATION_MS));
        }
        if (playSound) {
            Minecraft.getInstance().getSoundManager().play(SimpleSoundInstance.forUI(WARNING, 1.0F, 0.8F));
        }
    }

    private static void render(GuiGraphicsExtractor graphics) {
        Minecraft client = Minecraft.getInstance();
        if (client.player == null || ACTIVE.isEmpty()) return;
        float centerY = client.getWindow().getGuiScaledHeight() / 2.0F + TITLE_Y_OFFSET;
        float occupiedTop = Float.POSITIVE_INFINITY;
        for (Alert alert : ACTIVE.stream().sorted(Comparator.comparingLong(a -> a.createdAt)).toList()) {
            float height = alert.lines.stream().mapToInt(line -> (int) (10 * line.scale)).sum();
            float top = centerY - height / 2.0F;
            if (top + height + 6.0F > occupiedTop) top = occupiedTop - 6.0F - height;
            occupiedTop = top;
            float y = top;
            for (Line line : alert.lines) {
                graphics.pose().pushMatrix();
                graphics.pose().translate(client.getWindow().getGuiScaledWidth() / 2.0F, y);
                graphics.pose().scale(line.scale, line.scale);
                graphics.text(client.font, line.text, -client.font.width(line.text) / 2, 0, 0xFFFFFFFF, true);
                graphics.pose().popMatrix();
                y += (int) (10 * line.scale);
            }
        }
    }

    private static void clear() {
        ACTIVE.clear();
        questActive = false;
        bossActive = false;
    }

    private record Line(Component text, float scale) {}

    private static final class Alert {
        private final String id;
        private final List<Line> lines;
        private final long createdAt;
        private final long expiresAt;
        private Alert(String id, List<Line> lines, long createdAt, long expiresAt) {
            this.id = id;
            this.lines = lines;
            this.createdAt = createdAt;
            this.expiresAt = expiresAt;
        }
    }
}
