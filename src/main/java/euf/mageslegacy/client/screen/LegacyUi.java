package euf.mageslegacy.client.screen;

import com.mojang.blaze3d.systems.RenderSystem;
import euf.mageslegacy.MagesLegacy;
import euf.mageslegacy.race.Race;
import java.util.HashMap;
import java.util.Map;
import net.minecraft.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

/**
 * The look of the Mage's Legacy screens: dark carved stone with an antique gold rim, the race's own colour as the
 * accent, embers drifting up in the background, the Codex-drawn emblems. Everything else is drawn with fills, so it
 * scales with the GUI.
 */
public final class LegacyUi {
	public static final int RIM = 0xFF6B5636;
	public static final int RIM_DARK = 0xFF3A2E22;
	public static final int GOLD = 0xFFE8C060;
	public static final int PARCHMENT = 0xFFE2D6C0;
	public static final int TEXT = 0xFFEDE6DA;
	public static final int MUTED = 0xFF9A9088;
	public static final int BLACK = 0xFF050406;

	private static final Map<ResourceLocation, Boolean> PRESENT = new HashMap<>();

	private LegacyUi() {
	}

	public static ResourceLocation gui(String path) {
		return MagesLegacy.id("textures/gui/" + path + ".png");
	}

	public static ResourceLocation emblem(Race race) {
		return gui("emblem/" + race.id());
	}

	/** Whether a texture ships with the mod, so screens can fall back for art not drawn yet. */
	public static boolean exists(ResourceLocation texture) {
		return PRESENT.computeIfAbsent(texture, id -> Minecraft.getInstance().getResourceManager().getResource(id).isPresent());
	}

	public static int argb(int rgb) {
		return 0xFF000000 | rgb;
	}

	/** Seconds since start, for animations. */
	public static float time() {
		return (Util.getMillis() % 3_600_000L) / 1000f;
	}

	public static float hash(int i, int salt) {
		int h = i * 0x9E3779B1 + salt * 0x85EBCA77;
		h ^= h >>> 15;
		h *= 0x2C1B3C6D;
		h ^= h >>> 12;
		return (h & 0xFFFFFF) / (float) 0x1000000;
	}

	public static int alpha(int color, float alpha) {
		int a = (int) (((color >>> 24) & 0xFF) * Math.max(0, Math.min(1, alpha)));
		return (a << 24) | (color & 0xFFFFFF);
	}

	/** Mixes two colours, t = 0 gives a, 1 gives b. */
	public static int mix(int a, int b, float t) {
		int r = (int) (((a >> 16) & 0xFF) * (1 - t) + ((b >> 16) & 0xFF) * t);
		int g = (int) (((a >> 8) & 0xFF) * (1 - t) + ((b >> 8) & 0xFF) * t);
		int bl = (int) ((a & 0xFF) * (1 - t) + (b & 0xFF) * t);
		int al = (int) (((a >>> 24) & 0xFF) * (1 - t) + ((b >>> 24) & 0xFF) * t);
		return (al << 24) | (r << 16) | (g << 8) | bl;
	}

	/** The screen behind: a dark haze with embers drifting up in all the races' colours. */
	public static void background(GuiGraphics graphics, int width, int height) {
		graphics.fillGradient(0, 0, width, height, 0xE8100C12, 0xF4050406);
		float t = time();
		Race[] races = Race.values();
		for (int i = 0; i < 54; i++) {
			float speed = 5 + hash(i, 1) * 12;
			int x = (int) (hash(i, 2) * width + Math.sin(t * 0.6 + i) * 6);
			int y = (int) (height + 10 - (t * speed + hash(i, 3) * (height + 20)) % (height + 20));
			float glow = 0.3f + 0.35f * (float) Math.sin(t * 1.8 + i * 1.7);
			int s = hash(i, 4) > 0.8f ? 2 : 1;
			graphics.fill(x, y, x + s, y + s, alpha(argb(races[i % races.length].color), glow));
		}
	}

	/** A carved stone slab: black outline, dark body, an antique gold rim and a rivet in every corner. */
	public static void panel(GuiGraphics graphics, int x0, int y0, int x1, int y1) {
		panel(graphics, x0, y0, x1, y1, RIM);
	}

	public static void panel(GuiGraphics graphics, int x0, int y0, int x1, int y1, int rim) {
		graphics.fill(x0 - 1, y0 - 1, x1 + 1, y1 + 1, BLACK);
		graphics.fillGradient(x0, y0, x1, y1, 0xF018131C, 0xF00C090E);
		outline(graphics, x0, y0, x1, y1, rim);
		outline(graphics, x0 + 1, y0 + 1, x1 - 1, y1 - 1, 0x60000000);
		for (int[] c : new int[][] {{x0, y0}, {x1 - 1, y0}, {x0, y1 - 1}, {x1 - 1, y1 - 1}}) {
			diamond(graphics, c[0], c[1], 2, BLACK);
			diamond(graphics, c[0], c[1], 1, rim);
		}
	}

	public static void outline(GuiGraphics graphics, int x0, int y0, int x1, int y1, int color) {
		graphics.fill(x0, y0, x1, y0 + 1, color);
		graphics.fill(x0, y1 - 1, x1, y1, color);
		graphics.fill(x0, y0, x0 + 1, y1, color);
		graphics.fill(x1 - 1, y0, x1, y1, color);
	}

	public static void diamond(GuiGraphics graphics, int x, int y, int r, int color) {
		for (int dy = -r; dy <= r; dy++) {
			int half = r - Math.abs(dy);
			graphics.fill(x - half, y + dy, x + half + 1, y + dy + 1, color);
		}
	}

	/** A thin line fading out at both ends, with a gem in the middle. */
	public static void divider(GuiGraphics graphics, int x0, int x1, int y, int gem) {
		int mid = (x0 + x1) / 2;
		int half = (x1 - x0) / 2;
		int steps = 12;
		for (int i = 0; i < steps; i++) {
			int a = half * i / steps;
			int b = half * (i + 1) / steps;
			int color = alpha(RIM, (i + 1) / (float) steps);
			graphics.fill(x0 + a, y, x0 + b, y + 1, color);
			graphics.fill(x1 - b, y, x1 - a, y + 1, color);
		}
		diamond(graphics, mid, y, 3, BLACK);
		diamond(graphics, mid, y, 2, gem);
	}

	/** A heading between two short fading lines, in gold or the given colour. */
	public static void heading(GuiGraphics graphics, Component text, int cx, int y, int color) {
		Font font = Minecraft.getInstance().font;
		int tw = font.width(text);
		graphics.drawString(font, text, cx - tw / 2, y, color);
		int len = 28;
		for (int i = 0; i < len; i++) {
			int c = alpha(color, 0.8f * (1 - i / (float) len));
			graphics.fill(cx - tw / 2 - 6 - i, y + 4, cx - tw / 2 - 5 - i, y + 5, c);
			graphics.fill(cx + tw / 2 + 5 + i, y + 4, cx + tw / 2 + 6 + i, y + 5, c);
		}
	}

	/**
	 * A stone button with a gold rim that takes on the accent colour under the mouse. The primary one is filled with
	 * the accent and breathes while it can be pressed.
	 */
	public static void button(GuiGraphics graphics, int x, int y, int w, int h, boolean active, boolean hovered, boolean primary, int accent) {
		int x1 = x + w;
		int y1 = y + h;
		graphics.fill(x, y, x1, y1, BLACK);
		if (!active) {
			graphics.fill(x + 1, y + 1, x1 - 1, y1 - 1, 0xFF110E12);
			outline(graphics, x + 1, y + 1, x1 - 1, y1 - 1, 0xFF2A2328);
			return;
		}
		if (primary) {
			float pulse = hovered ? 1 : 0.5f + 0.5f * (float) Math.sin(time() * 3);
			int top = mix(0xFF2A2026, accent, hovered ? 0.62f : 0.45f);
			int bottom = mix(0xFF120E12, accent, hovered ? 0.3f : 0.2f);
			graphics.fillGradient(x + 1, y + 1, x1 - 1, y1 - 1, top, bottom);
			outline(graphics, x + 1, y + 1, x1 - 1, y1 - 1, mix(RIM, GOLD, pulse));
		} else {
			graphics.fillGradient(x + 1, y + 1, x1 - 1, y1 - 1, hovered ? 0xFF342A30 : 0xFF241C22, hovered ? 0xFF1C161A : 0xFF141014);
			outline(graphics, x + 1, y + 1, x1 - 1, y1 - 1, hovered ? accent : RIM);
		}
		graphics.fill(x + 2, y + 2, x1 - 2, y + 3, 0x22FFFFFF);
	}

	/** An emblem (a 64 px Codex texture) drawn at the given size, tinted and faded. */
	public static void emblem(GuiGraphics graphics, Race race, int x, int y, int size, float shade, float alpha) {
		RenderSystem.enableBlend();
		RenderSystem.setShaderColor(shade, shade, shade, alpha);
		graphics.blit(emblem(race), x, y, size, size, 0, 0, 64, 64, 64, 64);
		RenderSystem.setShaderColor(1f, 1f, 1f, 1f);
	}

	/** An emblem at full colour, or dimmed for a race the player can't take. */
	public static void emblem(GuiGraphics graphics, Race race, int x, int y, int size, boolean bright) {
		emblem(graphics, race, x, y, size, bright ? 1f : 0.42f, 1f);
	}

	/** A soft glow behind something round: rings of fading colour. */
	public static void glow(GuiGraphics graphics, int cx, int cy, int radius, int color, float strength) {
		for (int r = radius; r > 0; r -= 2) {
			float a = strength * (1 - r / (float) radius) * 0.35f;
			int c = alpha(color, a);
			for (int dy = -r + (r & 1); dy <= r; dy += 2) {
				int half = (int) Math.sqrt(r * r - dy * dy);
				graphics.fill(cx - half, cy + dy, cx + half, cy + dy + 2, c);
			}
		}
	}

	/** A small padlock: the shackle and the body with a keyhole. */
	public static void lock(GuiGraphics graphics, int cx, int cy, int color) {
		outline(graphics, cx - 3, cy - 6, cx + 4, cy, color);
		graphics.fill(cx - 5, cy - 1, cx + 6, cy + 7, BLACK);
		graphics.fill(cx - 4, cy, cx + 5, cy + 6, color);
		graphics.fill(cx, cy + 2, cx + 1, cy + 4, BLACK);
	}
}
