package me.rattokens.rataddon.feature;

import me.rattokens.rataddon.RaTAddonClient;
import me.rattokens.rataddon.util.SkyblockLocation;
import net.fabricmc.fabric.api.client.message.v1.ClientReceiveMessageEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;

import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class RareDropAlerts {
    // SBE 2.2.0-pre18's selected Slayer drops, with current Tarantula additions
    // and the requested Inferno exclusions. Common drops fail SBE's rank checks.
    private static final Set<String> ANNOUNCED_DROPS = Set.of(
        // Sven
        "Red Claw Egg", "Couture Rune", "Grizzly Bait", "Overflux Capacitor",
        // Tarantula
        "Tarantula Talisman", "Fly Swatter", "Digested Mosquito",
        "Primordial Eye", "Shriveled Wasp",
        // Revenant
        "Beheaded Horror", "Snake Rune", "Scythe Blade", "Shard of the Shredded", "Warden Heart",
        // Voidgloom
        "Enchant Rune", "Handy Blood Chalice", "Pocket Espresso Machine", "Void Conqueror",
        "Judgement Core", "Exceedingly Rare Ender Artifact", "End Stone Idol",
        // Inferno
        "Scorched Power", "Archfiend Dice", "Duplex I", "High Class Archfiend",
        "Engineering Plans", "Subzero Inverter",
        // Riftstalker
        "Guardian Lucky Block", "McGrubber's Burger", "Unfanged Vampire Part",
        "Legendary Book Bundle"
    );
    private static final Pattern RARE_DROP = Pattern.compile(
        "^(RARE DROP!|VERY RARE DROP!|CRAZY RARE DROP!|INSANE DROP!)\\s*(.+)$",
        Pattern.CASE_INSENSITIVE);
    private static String lastMessage = "";
    private static long lastMessageAt;

    private RareDropAlerts() {}

    public static void init() {
        ClientReceiveMessageEvents.GAME.register((message, overlay) -> {
            if (!overlay) onMessage(message.getString());
        });
        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> {
            lastMessage = "";
            lastMessageAt = 0L;
        });
    }

    private static void onMessage(String raw) {
        if (!RaTAddonClient.CONFIG.announceRareDrops || !SkyblockLocation.inSkyblock()) return;
        for (String line : raw.split("\\R")) {
            String text = ChatFormatting.stripFormatting(line).trim();
            Matcher match = RARE_DROP.matcher(text);
            if (!match.matches()) continue;
            String itemName = announcedDropName(match.group(2));
            if (itemName == null) continue;
            long now = System.currentTimeMillis();
            if (text.equals(lastMessage) && now - lastMessageAt < 750L) continue;
            lastMessage = text;
            lastMessageAt = now;
            CenterAlert.show(Component.literal(itemName)
                .withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD), 2_000L);
        }
    }

    private static String announcedDropName(String detail) {
        String normalized = detail.toLowerCase(Locale.ROOT);
        return ANNOUNCED_DROPS.stream()
            .filter(name -> normalized.contains(name.toLowerCase(Locale.ROOT)))
            .findFirst().orElse(null);
    }
}
