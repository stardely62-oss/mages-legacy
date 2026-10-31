package euf.mageslegacy.client;

import euf.mageslegacy.fx.Fx;
import euf.mageslegacy.net.FxPayload;
import java.util.ArrayList;
import java.util.List;
import com.mojang.blaze3d.audio.Listener;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.sounds.SoundEngine;
import net.minecraft.client.sounds.SoundManager;
import net.minecraft.sounds.SoundSource;
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
	/** The Bloodkin's: the view edged in red (the Sacrifice, the Feast, the Last Breath). */
	private static final List<float[]> BLOODS = new ArrayList<>();
	/** Ticks left of a view from behind (the Black Hole), and the view to return to. */
	private static int outside;
	private static net.minecraft.client.CameraType before;
	/** Ticks left of muffled hearing (the in-between, a singularity nearby). */
	private static int muffled;
	private static float gain = -1;
	private static int clientTicks;

	private ScreenFx() {
	}

	public static void play(FxPayload payload) {
		switch (payload.kind()) {
			case Fx.VOID_SCREEN -> VOIDS.add(new float[] {payload.ticks(), 0, 1});
			case Fx.SHAKE -> SHAKES.add(new float[] {payload.ticks(), 0, payload.a()});
			case Fx.FOV -> FOVS.add(new float[] {payload.ticks(), 0, payload.a()});
			case Fx.BLOOD_SCREEN -> BLOODS.add(new float[] {payload.ticks(), 0, 1});
			case Fx.THIRD_PERSON -> {
				var options = Minecraft.getInstance().options;
				if (payload.ticks() <= 0) {
					// Over early: back to one's own eyes on the next tick
					outside = Math.min(outside, 1);
					return;
				}
				if (outside <= 0) {
					before = options.getCameraType();
				}
				outside = Math.max(outside, payload.ticks());
				options.setCameraType(net.minecraft.client.CameraType.THIRD_PERSON_BACK);
			}
			case Fx.MUFFLE -> {
				var player = Minecraft.getInstance().player;
				if (player != null && (payload.entity() == player.getId() || player.position().distanceTo(payload.from()) <= payload.a())) {
					muffled = Math.max(muffled, payload.ticks());
				}
			}
			default -> {
			}
		}
	}

	static void tick() {
		clientTicks++;
		// The view comes back to the eyes it left (unless the player has changed it themselves meanwhile)
		if (outside > 0 && --outside == 0 && before != null) {
			var options = Minecraft.getInstance().options;
			if (options.getCameraType() == net.minecraft.client.CameraType.THIRD_PERSON_BACK) {
				options.setCameraType(before);
			}
			before = null;
		}
		hearing();
		for (List<float[]> list : List.of(VOIDS, SHAKES, FOVS, BLOODS)) {
			list.removeIf(f -> ++f[1] >= f[0]);
		}
	}

	/**
	 * Muffled hearing: the listener's gain sinks while it lasts and comes back after. The sound engine has no hook for
	 * this, so its listener is reached by reflection (mojmap names at runtime on NeoForge); if that fails, nothing changes.
	 */
	private static void hearing() {
		Minecraft mc = Minecraft.getInstance();
		float master = mc.options.getSoundSourceVolume(SoundSource.MASTER);
		float target = muffled > 0 ? master * 0.3f : master;
		if (muffled > 0) {
			muffled--;
		}
		if (broken || gain < 0 && muffled == 0 || Math.abs(target - gain) < 0.001f) {
			if (gain < 0) {
				gain = master;
			}
			return;
		}
		// Ease towards it, so the world fades away and back rather than switching
		gain = gain < 0 ? master : gain + (target - gain) * 0.35f;
		if (Math.abs(target - gain) < 0.01f) {
			gain = target;
		}
		try {
			if (engineField == null) {
				engineField = SoundManager.class.getDeclaredField("soundEngine");
				engineField.setAccessible(true);
				listenerField = SoundEngine.class.getDeclaredField("listener");
				listenerField.setAccessible(true);
			}
			Object engine = engineField.get(mc.getSoundManager());
			((Listener) listenerField.get(engine)).setGain(gain);
		} catch (Throwable e) {
			// No sound device, or a different engine: hearing simply stays as it is
			broken = true;
			muffled = 0;
			gain = master;
		}
	}

	private static boolean broken;
	private static java.lang.reflect.Field engineField;
	private static java.lang.reflect.Field listenerField;

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
		float delta = deltaTracker.getGameTimeDeltaPartialTick(false);
		int w = graphics.guiWidth();
		int h = graphics.guiHeight();
		// Blood: a dark red veil that swells in and drains away, the edges deep crimson
		for (float[] f : BLOODS) {
			float life = f[1] + delta;
			float a = Math.min(1, life / 2f) * Math.min(1, (f[0] - life) / 8f);
			if (a > 0) {
				graphics.fill(0, 0, w, h, argb(0x3A0008, 0.08f * a));
				edges(graphics, w, h, 0x8E0E1E, 0.45f * a, 5);
			}
		}
		if (VOIDS.isEmpty()) {
			return;
		}
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
