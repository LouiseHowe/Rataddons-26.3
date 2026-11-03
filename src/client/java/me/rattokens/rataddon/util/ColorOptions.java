package me.rattokens.rataddon.util;

public final class ColorOptions {
	private static final String[] NAMES = {
		"Orange", "Red", "Gold", "Yellow", "Green", "Aqua", "Blue", "Purple", "White"
	};
	private static final int[] RGB = {
		0xB62F00, 0xFF5555, 0xFFAA00, 0xFFFF55, 0x55FF55,
		0x55FFFF, 0x5555FF, 0xFF55FF, 0xFFFFFF
	};

	private ColorOptions() {
	}

	public static int count() {
		return NAMES.length;
	}

	public static String name(int index) {
		return NAMES[normalize(index)];
	}

	public static int rgb(int index) {
		return RGB[normalize(index)];
	}

	public static int argb(int index) {
		return 0xFF000000 | rgb(index);
	}

	public static int next(int index) {
		return (normalize(index) + 1) % NAMES.length;
	}

	private static int normalize(int index) {
		return Math.clamp(index, 0, NAMES.length - 1);
	}
}
