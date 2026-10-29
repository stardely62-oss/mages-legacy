package euf.mageslegacy.client;

import euf.mageslegacy.fx.Fx;
import euf.mageslegacy.net.FxPayload;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.util.Mth;
import net.neoforged.neoforge.client.event.ViewportEvent;

/**
 * The player's own moments from the first version of the mod: the passage through the void (the view goes dark,
 * stars rush past from the middle outwards, violet at the edges), camera shakes, and rushes of the field of view.
 */
public final class ScreenFx {
	/** Each entry: ticks it lasts, age, and its strength. */
	private static final List<float[]> VOIDS = new ArrayList<>();
	private static final List<float[]> SHAKES = new ArrayList<>();
	private static final List<float[]> FOVS = new ArrayList<>();
	private static int clientTicks;

	private ScreenFx() {
	}

	public static void play(FxPayload payload) {
		switch (payload.kind()) {
			case Fx.VOID_SCREEN -> VOIDS.add(new float[] {payload.ticks(), 0, 1});
			case Fx.SHAKE -> SHAKES.add(new float[] {payload.ticks(), 0, payload.a()});
			case Fx.FOV -> FOVS.add(new float[] {payload.ticks(), 0, payload.a()});
			default -> {
			}
		}
	}

	static void tick() {
		clientTicks++;
		for (List<float[]> list : List.of(VOIDS, SHAKES, FOVS)) {
			list.removeIf(f -> ++f[1] >= f[0]);
		}
	}

	/** Whether the view is in the void right now (the wall overlay is then hidden too). */
	static boolean inVoid() {
		return !VOIDS.isEmpty();
	}

	/** Shakes the camera: strong at first, dying away, judders rather than sways. */
	static void onAngles(ViewportEvent.ComputeCameraAngles event) {
		if (SHAKES.isEmpty()) {
			return;
		}
		float delta = (float) event.getPartialTick();
		float pitch = 0;
		float roll = 0;
		float time = clientTicks + delta;
		for (float[] f : SHAKES) {
			float left = 1 - Mth.clamp((f[1] + delta) / f[0], 0, 1);
			float s = f[2] * left * left;
			pitch += s * (Mth.sin(time * 2.9f) * 0.6f + Mth.sin(time * 5.3f) * 0.4f);
			roll += s * (Mth.sin(time * 3.7f + 1) * 0.6f + Mth.sin(time * 6.1f) * 0.4f);
		}
		event.setPitch(event.getPitch() + pitch);
		event.setRoll(event.getRoll() + roll);
	}

	/** A rush of the field of view springs out in a couple of ticks and eases back. */
	static void onFov(ViewportEvent.ComputeFov event) {
		if (FOVS.isEmpty()) {
			return;
		}
		float delta = (float) event.getPartialTick();
		double scale = 1;
		for (float[] f : FOVS) {
			float t = Mth.clamp((f[1] + delta) / f[0], 0, 1);
			float envelope = t < 0.12f ? t / 0.12f : (1 - (t - 0.12f) / 0.88f) * (1 - (t - 0.12f) / 0.88f);
			scale += f[2] * envelope;
		}
		event.setFOV(event.getFOV() * scale);
	}

	/** The passage through the void: dark, violet edges, and stars streaking out from the middle. */
	static void render(GuiGraphics graphics, DeltaTracker deltaTracker) {
		if (VOIDS.isEmpty()) {
			return;
		}
		float delta = deltaTracker.getGameTimeDeltaPartialTick(false);
		int w = graphics.guiWidth();
		int h = graphics.guiHeight();
		for (float[] f : VOIDS) {
			float life = f[1] + delta;
			float a = Math.min(1, life / 2f) * Math.min(1, (f[0] - life) / 4f);
			if (a <= 0) {
				continue;
			}
			graphics.fill(0, 0, w, h, argb(0x07020F, 0.92f * a));
			edges(graphics, w, h, 0x7050C0, 0.55f * a, 4);
			int cx = w / 2;
			int cy = h / 2;
			double reach = Math.hypot(w, h) / 2;
			for (int i = 0; i < 110; i++) {
				double angle = hash(7, i) * Math.PI * 2;
				double speed = 0.05 + 0.08 * hash(11, i);
				double d = (hash(13, i) + life * speed) % 1;
				double dist = d * d * reach;
				int len = (int) (1 + d * 10);
				int size = d > 0.6 ? 2 : 1;
				float bright = (float) Math.min(1, d * 3) * a;
				int color = i % 5 == 0 ? 0xC8B4FF : 0xFFFFFF;
				// A streak pointing out from the middle: a few dots along its way
				for (int k = 0; k < len; k += 2) {
					double r = dist + k;
					int x = cx + (int) (Math.cos(angle) * r);
					int y = cy + (int) (Math.sin(angle) * r);
					graphics.fill(x, y, x + size, y + size, argb(color, bright * (1 - (float) k / (len + 1))));
				}
			}
		}
	}

	/** The screen's edges glowing in a colour, {@code 1/depth} of the screen deep. */
	private static void edges(GuiGraphics graphics, int w, int h, int rgb, float a, int depth) {
		if (a <= 0.005f) {
			return;
		}
		int band = Math.max(8, Math.min(w, h) / depth);
		graphics.fillGradient(0, 0, w, band, argb(rgb, a), argb(rgb, 0));
		graphics.fillGradient(0, h - band, w, h, argb(rgb, 0), argb(rgb, a));
		for (int i = 0; i < band; i += 2) {
			int c = argb(rgb, a * (1 - (float) i / band) * (1 - (float) i / band));
			graphics.fill(i, 0, i + 2, h, c);
			graphics.fill(w - i - 2, 0, w - i, h, c);
		}
	}

	private static int argb(int rgb, float alpha) {
		return ((int) (Mth.clamp(alpha, 0, 1) * 255) << 24) | (rgb & 0xFFFFFF);
	}

	private static float hash(long seed, int i) {
		long h = seed * 6364136223846793005L + i * 1442695040888963407L;
		h ^= h >>> 29;
		h *= 0xBF58476D1CE4E5B9L;
		h ^= h >>> 32;
		return (h & 0xFFFFFF) / (float) 0x1000000;
	}
}
