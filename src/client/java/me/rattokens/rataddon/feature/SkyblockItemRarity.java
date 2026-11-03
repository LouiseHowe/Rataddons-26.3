// SPDX-License-Identifier: LGPL-3.0-only
// Adapted from Baity's dropped-item rarity parser.
package me.rattokens.rataddon.feature;

import java.util.Locale;

public enum SkyblockItemRarity {
	COMMON("Common", 1.0), UNCOMMON("Uncommon", 1.25), RARE("Rare", 1.65),
	EPIC("Epic", 1.95), LEGENDARY("Legendary", 2.45), MYTHIC("Mythic", 2.80),
	DIVINE("Divine", 3.10), SPECIAL("Special", 2.20), VERY_SPECIAL("Very Special", 2.20),
	ULTIMATE("Ultimate", 3.50), ADMIN("Admin", 3.50), UNKNOWN("Unknown", 1.0);

	public final String label;
	public final double defaultScale;
	private final String loreToken;

	SkyblockItemRarity(String label, double defaultScale) {
		this.label = label;
		this.defaultScale = defaultScale;
		this.loreToken = name().replace('_', ' ');
	}

	/** Keep Baity's last matching token, so VERY SPECIAL wins over SPECIAL. */
	public static SkyblockItemRarity fromLoreLine(String line) {
		if (line == null || line.isEmpty()) return UNKNOWN;
		SkyblockItemRarity found = UNKNOWN;
		for (SkyblockItemRarity rarity : values())
			if (rarity != UNKNOWN && line.contains(rarity.loreToken)) found = rarity;
		return found;
	}

	public static SkyblockItemRarity fromTierName(String tier) {
		if (tier == null || tier.isEmpty()) return UNKNOWN;
		try { return valueOf(tier.trim().toUpperCase(Locale.ROOT).replace(' ', '_')); }
		catch (IllegalArgumentException ignored) { return UNKNOWN; }
	}
}
