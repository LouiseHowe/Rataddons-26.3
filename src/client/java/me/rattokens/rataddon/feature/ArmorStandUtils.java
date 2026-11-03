package me.rattokens.rataddon.feature;

import me.rattokens.rataddon.RaTAddonClient;
import me.rattokens.rataddon.util.ColorOptions;
import net.minecraft.ChatFormatting;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.decoration.ArmorStand;

import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.util.Locale;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class ArmorStandUtils {
	private static final Pattern NUMBER = Pattern.compile("(?<![A-Za-z])\\d[\\d,]*(?:\\.\\d+)?(?![A-Za-z])");
	private static final Pattern COMPACT_DAMAGE =
		Pattern.compile("^✧?✯?((?:\\d+,?)*)✧?✯?❤?$");
	private static final ChatFormatting[] CRIT_COLORS = {
		ChatFormatting.YELLOW, ChatFormatting.WHITE, ChatFormatting.RED, ChatFormatting.GOLD
	};
	private static final String DAMAGE_MARKERS = "✧✯✦✪⚚☄✷✹❁❤♥";

	private ArmorStandUtils() {
	}

	public static void shortenDamageNumber(Entity entity, EntityRenderState state) {
		if (!RaTAddonClient.CONFIG.shortDamageNumbers || !(entity instanceof ArmorStand)
			|| state == null || state.nameTag == null) {
			return;
		}
		String text = ChatFormatting.stripFormatting(state.nameTag.getString());
		if (RaTAddonClient.CONFIG.compactDamageStyle) {
			applyCompactDamageStyle(state, text);
			return;
		}
		if (!looksLikeDamage(text)) {
			return;
		}
		String shortened = shortenNumbers(text);
		if (!shortened.equals(text)) {
			state.nameTag = Component.literal(shortened)
				.withStyle(style -> style.withColor(ColorOptions.rgb(
					RaTAddonClient.CONFIG.shortDamageNumbersColor)));
		}
	}

	private static void applyCompactDamageStyle(EntityRenderState state, String text) {
		Matcher matcher = COMPACT_DAMAGE.matcher(text);
		if (!matcher.matches() || matcher.group(1).isEmpty()) {
			return;
		}
		String rawNumber = matcher.group(1);
		double value = parse(rawNumber);
		String compact = value >= 1_000.0 ? format(value) : rawNumber;
		boolean critical = text.contains("✧") || text.contains("✯");
		if (!critical) {
			List<Component> siblings = state.nameTag.getSiblings();
			Style style = siblings.isEmpty() ? state.nameTag.getStyle() : siblings.getFirst().getStyle();
			state.nameTag = Component.literal(compact).setStyle(style);
			return;
		}

		char symbol = text.charAt(0);
		String styledText = symbol + " " + compact + " " + symbol;
		MutableComponent result = Component.empty();
		int colorIndex = 0;
		for (char character : styledText.toCharArray()) {
			MutableComponent part = Component.literal(String.valueOf(character));
			if (character != ' ') {
				part.withStyle(CRIT_COLORS[colorIndex++ % CRIT_COLORS.length]);
			}
			result.append(part);
		}
		if (text.contains("❤")) {
			result.append(Component.literal(" ❤").withStyle(ChatFormatting.LIGHT_PURPLE));
		}
		state.nameTag = result;
	}

	private static boolean looksLikeDamage(String text) {
		if (text == null || text.isBlank()) {
			return false;
		}
		String lower = text.toLowerCase(Locale.ROOT);
		if (lower.contains("[lv") || lower.contains("hp") || lower.contains("❤/")) {
			return false;
		}
		Matcher matcher = NUMBER.matcher(text);
		if (!matcher.find()) {
			return false;
		}
		for (int index = 0; index < DAMAGE_MARKERS.length(); index++) {
			if (text.indexOf(DAMAGE_MARKERS.charAt(index)) >= 0) {
				return true;
			}
		}
		String remainder = NUMBER.matcher(text).replaceAll("")
			.replace(",", "")
			.replace(".", "")
			.replace("+", "")
			.replace("-", "")
			.trim();
		return remainder.isBlank();
	}

	private static String shortenNumbers(String text) {
		Matcher matcher = NUMBER.matcher(text);
		StringBuilder result = new StringBuilder();
		while (matcher.find()) {
			String raw = matcher.group();
			double value = parse(raw);
			String replacement = value >= 1_000.0 ? format(value) : raw;
			matcher.appendReplacement(result, Matcher.quoteReplacement(replacement));
		}
		matcher.appendTail(result);
		return result.toString();
	}

	private static double parse(String value) {
		try {
			return Double.parseDouble(value.replace(",", ""));
		} catch (NumberFormatException ignored) {
			return 0.0;
		}
	}

	private static String format(double value) {
		double divisor;
		String suffix;
		if (value >= 1_000_000_000.0) {
			divisor = 1_000_000_000.0;
			suffix = "b";
		} else if (value >= 1_000_000.0) {
			divisor = 1_000_000.0;
			suffix = "m";
		} else {
			divisor = 1_000.0;
			suffix = "k";
		}
		double shortened = value / divisor;
		String pattern = shortened >= 10.0 ? "0.#" : "0.##";
		DecimalFormat format = new DecimalFormat(pattern, DecimalFormatSymbols.getInstance(Locale.ROOT));
		return format.format(shortened) + suffix;
	}
}
