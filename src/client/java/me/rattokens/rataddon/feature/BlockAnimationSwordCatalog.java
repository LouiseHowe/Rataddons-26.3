package me.rattokens.rataddon.feature;

import me.rattokens.rataddon.util.SkyblockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;

/** Snapshot of Baity's blockanimation-sword-catalog.tsv, packaged with the mod. */
final class BlockAnimationSwordCatalog {
    private static final String RESOURCE = "/assets/rataddon/blockanimation-sword-catalog.tsv";
    private static final Set<String> IDS = loadIds();

    private BlockAnimationSwordCatalog() {}

    static boolean allows(ItemStack stack) {
        if (stack == null || stack.isEmpty()) return false;
        String id = SkyblockItem.id(stack);
        if (id.isBlank()) id = vanillaSwordId(stack);
        return IDS.contains(id.toUpperCase(Locale.ROOT));
    }

    private static String vanillaSwordId(ItemStack stack) {
        if (stack.is(Items.WOODEN_SWORD)) return "WOOD_SWORD";
        if (stack.is(Items.STONE_SWORD)) return "STONE_SWORD";
        if (stack.is(Items.IRON_SWORD)) return "IRON_SWORD";
        if (stack.is(Items.GOLDEN_SWORD)) return "GOLD_SWORD";
        if (stack.is(Items.DIAMOND_SWORD)) return "DIAMOND_SWORD";
        return "";
    }

    private static Set<String> loadIds() {
        InputStream stream = BlockAnimationSwordCatalog.class.getResourceAsStream(RESOURCE);
        if (stream == null) throw new IllegalStateException("Missing blocking sword catalog: " + RESOURCE);
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(stream, StandardCharsets.UTF_8))) {
            if (!"item_id\tdisplay_name".equals(reader.readLine())) {
                throw new IllegalStateException("Invalid blocking sword catalog header");
            }
            Set<String> ids = new HashSet<>();
            String row;
            while ((row = reader.readLine()) != null) {
                if (row.isBlank()) continue;
                int separator = row.indexOf('\t');
                if (separator <= 0 || !ids.add(row.substring(0, separator))) {
                    throw new IllegalStateException("Invalid blocking sword catalog row: " + row);
                }
            }
            if (ids.isEmpty()) throw new IllegalStateException("Empty blocking sword catalog");
            return Set.copyOf(ids);
        } catch (IOException exception) {
            throw new IllegalStateException("Cannot read blocking sword catalog", exception);
        }
    }
}
