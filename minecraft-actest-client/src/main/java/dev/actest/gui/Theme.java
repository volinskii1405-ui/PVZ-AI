package dev.actest.gui;

import net.minecraft.client.gui.DrawContext;

/** Цвета и примитивы отрисовки меню (тёмная тема с синим акцентом). */
public final class Theme {
	public static final int BACKDROP = 0x50000000;
	public static final int SHADOW = 0x70000000;
	public static final int PANEL = 0xFF161A22;
	public static final int SIDEBAR = 0xFF111419;
	public static final int HEADER = 0xFF1C2230;
	public static final int HEADER_LINE = 0xA05B8CFF;
	public static final int BORDER = 0xFF2C3442;
	public static final int ACCENT = 0xFF5B8CFF;
	public static final int CONTROL = 0xFF272E3B;
	public static final int CONTROL_HOVER = 0xFF323A4B;
	public static final int TEXT = 0xFFE6E9EF;
	public static final int TEXT_MUTED = 0xFF8B93A3;
	public static final int TEXT_DISABLED = 0xFF586070;
	public static final int ROW_HOVER = 0x10FFFFFF;
	public static final int GOOD = 0xFF4ADE80;
	public static final int BAD = 0xFFF87171;

	private Theme() {
	}

	/**
	 * Прямоугольник со скруглёнными углами радиусом 1–4 px.
	 * Полосы не перекрываются, поэтому полупрозрачные цвета не «двоятся» на стыках.
	 */
	public static void roundRect(DrawContext context, int x1, int y1, int x2, int y2, int radius, int color) {
		roundRect(context, x1, y1, x2, y2, radius, color, true, true, true, true);
	}

	/** То же, но скругляются только выбранные углы (tl, tr, bl, br). */
	public static void roundRect(DrawContext context, int x1, int y1, int x2, int y2, int radius, int color,
			boolean tl, boolean tr, boolean bl, boolean br) {
		int r = Math.max(0, Math.min(radius, Math.min((x2 - x1) / 2, (y2 - y1) / 2)));
		int[] insets = switch (r) {
			case 0 -> new int[0];
			case 1 -> new int[] {1};
			case 2 -> new int[] {2, 1};
			case 3 -> new int[] {3, 1, 1};
			default -> new int[] {4, 2, 1, 1};
		};
		int n = insets.length;
		for (int i = 0; i < n; i++) {
			context.fill(x1 + (tl ? insets[i] : 0), y1 + i, x2 - (tr ? insets[i] : 0), y1 + i + 1, color);
			context.fill(x1 + (bl ? insets[i] : 0), y2 - i - 1, x2 - (br ? insets[i] : 0), y2 - i, color);
		}
		if (y2 - n > y1 + n) {
			context.fill(x1, y1 + n, x2, y2 - n, color);
		}
	}

	/** Рамка толщиной 1 px (x2/y2 не включительно). */
	public static void outline(DrawContext context, int x1, int y1, int x2, int y2, int color) {
		context.fill(x1, y1, x2, y1 + 1, color);
		context.fill(x1, y2 - 1, x2, y2, color);
		context.fill(x1, y1 + 1, x1 + 1, y2 - 1, color);
		context.fill(x2 - 1, y1 + 1, x2, y2 - 1, color);
	}

	/** Линейная интерполяция двух ARGB-цветов, t = 0..1. */
	public static int lerpColor(float t, int from, int to) {
		t = Math.max(0f, Math.min(1f, t));
		int a = lerp(t, from >>> 24, to >>> 24);
		int r = lerp(t, (from >> 16) & 0xFF, (to >> 16) & 0xFF);
		int g = lerp(t, (from >> 8) & 0xFF, (to >> 8) & 0xFF);
		int b = lerp(t, from & 0xFF, to & 0xFF);
		return (a << 24) | (r << 16) | (g << 8) | b;
	}

	private static int lerp(float t, int from, int to) {
		return Math.round(from + (to - from) * t);
	}
}
