package euf.mageslegacy.client.fx;

import euf.mageslegacy.MagesLegacy;
import euf.mageslegacy.client.ScreenFx;
import euf.mageslegacy.fx.Fx;
import euf.mageslegacy.net.FxPayload;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import org.joml.Quaternionf;
import org.joml.Vector3f;

/**
 * The Voidborn's effects from the first version of the mod, rebuilt on Photon: the same shapes (a tear in space
 * opening with a spring and sealing to a white seam, a streak along the walk, cracks onto the void, a spreading wave,
 * a swelling shell, motes falling in), laid out of particles along the old geometry. The void is drawn twice: real
 * dark ink that eats the light, and violet glow at its edges.
 */
public final class VoidFx {
	private static final ResourceLocation STAR = MagesLegacy.id("textures/fx/star.png");
	private static final ResourceLocation MOTE = MagesLegacy.id("textures/fx/void_mote.png");
	private static final int VOID = 0xFF000000 | Fx.VOID_RGB;
	private static final int STARLIGHT = 0xFF000000 | Fx.STAR_RGB;
	private static final int DEEP = 0xFF2A1446;
	private static final int INK = 0xF80A0418;
	private static final int CRACK = 0xFF05020A;

	private VoidFx() {
	}

	public static void play(FxPayload payload) {
		Minecraft mc = Minecraft.getInstance();
		Level level = mc.level;
		if (level == null) {
			return;
		}
		Entity follow = payload.entity() >= 0 ? level.getEntity(payload.entity()) : null;
		try {
			switch (payload.kind()) {
				case Fx.RIFT -> rift(level, payload.from(), payload.a(), payload.b(), payload.ticks());
				case Fx.RIBBON -> ribbon(level, payload.from(), payload.to(), payload.ticks(), payload.a());
				case Fx.BURST -> burst(level, payload.from(), payload.a(), payload.ticks(), payload.b() > 0);
				case Fx.RING -> ring(level, payload.from(), payload.a(), payload.b(), payload.ticks());
				case Fx.CRACKS -> cracks(level, payload.from(), follow, payload.a(), payload.ticks());
				case Fx.MOTES -> {
					if (follow != null) {
						motes(level, follow, payload.a(), payload.ticks());
					}
				}
				case Fx.VOID_SCREEN, Fx.SHAKE, Fx.FOV -> ScreenFx.play(payload);
				default -> {
				}
			}
		} catch (RuntimeException e) {
			// An effect must never take the game down with it
			MagesLegacy.LOGGER.error("Photon effect {} failed", payload.kind(), e);
		}
	}

	// ---- The rift ----------------------------------------------------------------------------------------------

	/**
	 * A tear in space, always turned to the viewer: a lens of pure dark with a soft violet halo, a violet edge and a
	 * white-hot seam along its lips, stars far away inside. It springs open in 5 ticks, and in its last 6 seals until
	 * only a bright seam is left.
	 */
	private static void rift(Level level, Vec3 at, float height, float width, int ticks) {
		long seed = Double.doubleToLongBits(at.x * 13 + at.y * 7 + at.z * 3 + ticks);
		int n = 22;
		Vector3f[] mid = new Vector3f[n + 1];
		Vector3f[] left = new Vector3f[n + 1];
		Vector3f[] right = new Vector3f[n + 1];
		for (int k = 0; k <= n; k++) {
			float s = (float) k / n;
			float y = (s - 0.5f) * height;
			float profile = (float) Math.pow(Math.sin(Math.PI * s), 0.75);
			float l = width / 2 * profile * (0.85f + 0.3f * hash(seed, k));
			float r = width / 2 * profile * (0.85f + 0.3f * hash(seed, k + 100));
			float cx = width * 0.12f * (hash(seed, k + 200) - 0.5f) * profile;
			mid[k] = new Vector3f(cx, y, 0);
			left[k] = new Vector3f(cx - l, y, 0);
			right[k] = new Vector3f(cx + r, y, 0);
		}
		// The dark inside, filled across the lens; the lips, traced densely along both edges
		List<Vector3f> fill = new ArrayList<>();
		List<Vector3f> lips = new ArrayList<>();
		List<Vector3f> seam = new ArrayList<>();
		for (int k = 0; k < n; k++) {
			for (float f = 0; f < 1; f += 0.34f) {
				Vector3f m = new Vector3f(mid[k]).lerp(mid[k + 1], f);
				Vector3f a = new Vector3f(left[k]).lerp(left[k + 1], f);
				Vector3f b = new Vector3f(right[k]).lerp(right[k + 1], f);
				for (float u = -0.7f; u <= 0.71f; u += 0.35f) {
					fill.add(new Vector3f(m).lerp(u < 0 ? a : b, Math.abs(u)));
				}
				lips.add(a);
				lips.add(b);
				seam.add(m);
			}
		}
		List<Vector3f> stars = new ArrayList<>();
		for (int i = 0; i < 16; i++) {
			int k = 2 + (int) (hash(seed, i + 300) * (n - 4));
			float u = hash(seed, i + 400) * 1.6f - 0.8f;
			stars.add(new Vector3f(mid[k]).lerp(u < 0 ? left[k] : right[k], Math.abs(u)).add(0, 0, 0.02f));
		}
		int life = Math.max(2, ticks);
		// Sealing: the seam lights up white only in the last 6 ticks
		int seamFrom = Math.max(0, Math.round(20 * (1 - 6f / life)));
		PhotonFx.play(level, at, null, 0, (root, age) -> {
			float open = age < 5 ? easeOutBack(age / 5f) : 1;
			float shut = Math.min(1, (life - age) / 6f);
			float wide = Math.max(0.03f, open * shut);
			root.updateScale(new Vector3f(wide, 0.6f + 0.4f * Math.min(1, open), 1));
			root.updateRotation(facing(at));
		},
				new PhotonFx.Emitter(life).points(fill, null).burst(fill.size()).life(life, life).speed(0, 0).size(0.32f, 0.4f)
						.colors(INK, INK).ink(),
				new PhotonFx.Emitter(life).points(lips, null).burst(lips.size()).life(life, life).speed(0, 0).size(0.55f, 0.6f)
						.colors(alpha(VOID, 0.16f), alpha(VOID, 0.16f)).glow(),
				new PhotonFx.Emitter(life).points(lips, null).burst(lips.size()).life(life, life).speed(0, 0).size(0.16f, 0.18f)
						.colors(alpha(VOID, 0.9f), alpha(VOID, 0.9f)).glow(),
				new PhotonFx.Emitter(life).points(lips, null).burst(lips.size()).life(life, life).speed(0, 0).size(0.05f, 0.06f)
						.colors(0xB3FFFFFF, 0xB3FFFFFF).glow(),
				new PhotonFx.Emitter(life).points(seam, null).burst(seam.size()).life(life, life).speed(0, 0).size(0.12f, 0.13f)
						.colors(stops(20, seamFrom, 0x00FFFFFF, 0xE6FFFFFF)).glow(),
				new PhotonFx.Emitter(life).points(stars, null).burst(stars.size()).life(life, life).speed(0, 0).size(0.07f, 0.13f)
						.colors(0x00FFFFFF, 0xFFFFFFFF, 0x66FFFFFF, 0xFFFFFFFF, 0x55FFFFFF, 0xEEFFFFFF, 0x00FFFFFF).texture(STAR).glow());
	}

	/** Turns a flat effect laid out on X/Y to face the camera, standing upright. */
	private static Quaternionf facing(Vec3 at) {
		Vec3 cam = Minecraft.getInstance().gameRenderer.getMainCamera().getPosition();
		Vec3 toCam = cam.subtract(at).multiply(1, 0, 1);
		Vec3 dir = toCam.lengthSqr() < 1.0E-4 ? new Vec3(0, 0, 1) : toCam.normalize();
		Vec3 right = dir.cross(new Vec3(0, 1, 0)).normalize();
		return new Quaternionf().rotateY((float) Math.atan2(-right.z, right.x));
	}

	// ---- The walk ---------------------------------------------------------------------------------------------

	/**
	 * A streak of the void along the walk, dark at its start and violet towards the end; its tail draws in towards
	 * the head over its life, as the walker passes.
	 */
	private static void ribbon(Level level, Vec3 from, Vec3 to, int ticks, float height) {
		Vec3 a = from.add(0, 1, 0);
		Vec3 b = to.add(0, 1, 0);
		double length = a.distanceTo(b);
		int count = Math.max(4, (int) Math.ceil(length / 0.18));
		int buckets = 6;
		List<PhotonFx.Emitter> emitters = new ArrayList<>();
		for (int bucket = 0; bucket < buckets; bucket++) {
			List<Vector3f> points = new ArrayList<>();
			for (int i = bucket; i < count; i += buckets) {
				Vec3 p = a.lerp(b, i / (double) (count - 1)).subtract(a);
				points.add(new Vector3f((float) p.x, (float) p.y, (float) p.z));
			}
			// The tail goes first: a point fades when t² passes its place along the path
			float place = (bucket + 0.5f) / buckets;
			int life = Math.max(3, Math.round(ticks * (float) Math.sqrt(place)));
			int color = lerpColor(DEEP, VOID, place);
			emitters.add(new PhotonFx.Emitter(life).points(points, null).burst(points.size()).life(life, life).speed(0, 0)
					.size(height * 0.5f, height * 0.6f).sizeCurve(1f, 1f, 0.6f).colors(alpha(color, 0.5f), alpha(color, 0.35f), alpha(color, 0f)).glow());
			emitters.add(new PhotonFx.Emitter(life).points(points, null).burst(points.size()).life(life, life).speed(0, 0)
					.size(height * 0.3f, height * 0.36f).colors(alpha(DEEP, 0.45f), alpha(DEEP, 0.3f), alpha(DEEP, 0f)).ink());
		}
		PhotonFx.play(level, a, null, 0, emitters.toArray(PhotonFx.Emitter[]::new));
	}

	/** A shell of the void swelling out and fading, dark ink under it when asked. */
	private static void burst(Level level, Vec3 at, float radius, int ticks, boolean ink) {
		int life = Math.max(2, ticks);
		// Radial speed is in hundredths of a block a tick: reach the radius as the shell fades
		float push = radius / life * 100;
		List<PhotonFx.Emitter> emitters = new ArrayList<>();
		emitters.add(new PhotonFx.Emitter(life).sphere(0.05f, 0).burst(90).life(life, life).speed(0, 0).size(0.3f, 0.4f)
				.colors(alpha(VOID, 0.75f), alpha(VOID, 0.35f), alpha(VOID, 0.08f), alpha(VOID, 0)).swirl(push, 0).glow());
		if (ink) {
			emitters.add(new PhotonFx.Emitter(life).sphere(0.05f, 0).burst(60).life(life, life).speed(0, 0).size(0.35f, 0.45f)
					.colors(alpha(DEEP, 0.3f), alpha(DEEP, 0.15f), alpha(DEEP, 0)).swirl(push, 0).ink());
		}
		PhotonFx.play(level, at, null, 0, emitters.toArray(PhotonFx.Emitter[]::new));
	}

	/** A wave spreading on the ground from one radius to another, with a low wall of light on its edge. */
	private static void ring(Level level, Vec3 at, float from, float to, int ticks) {
		int life = Math.max(2, ticks);
		float push = (to - from) / life * 100;
		PhotonFx.play(level, at.add(0, 0.06, 0), null, 0,
				new PhotonFx.Emitter(life).ring(Math.max(0.05f, from), 0).burst(64).life(life, life).speed(0, 0).size(0.5f, 0.55f)
						.colors(alpha(VOID, 0.9f), alpha(VOID, 0.45f), alpha(VOID, 0.12f), alpha(VOID, 0)).swirl(push, 0).flat().glow(),
				new PhotonFx.Emitter(life).ring(Math.max(0.05f, from), 0).offset(0, 0.3f, 0).burst(48).life(life, life).speed(0, 0).size(0.3f, 0.35f)
						.colors(alpha(VOID, 0.7f), alpha(VOID, 0.3f), alpha(VOID, 0)).swirl(push, 0).glow(),
				new PhotonFx.Emitter(life).ring(Math.max(0.05f, from), 0).burst(40).life(life, life).speed(0, 0).size(0.4f, 0.45f)
						.colors(alpha(DEEP, 0.6f), alpha(DEEP, 0.25f), alpha(DEEP, 0)).swirl(push, 0).flat().ink());
	}

	/**
	 * The ground cracked open onto the void: black fissures zigzagging out from the middle, glowing violet at their
	 * lips, a white-hot thread along them and stars glinting deep inside. They split open over a few ticks.
	 */
	private static void cracks(Level level, Vec3 at, Entity follow, float radius, int ticks) {
		long seed = Double.doubleToLongBits(at.x * 31 + at.z * 17 + radius);
		int count = 7;
		int steps = 5;
		List<PhotonFx.Emitter> emitters = new ArrayList<>();
		List<Vector3f> stars = new ArrayList<>();
		List<List<Vector3f>> byStep = new ArrayList<>();
		for (int s = 0; s < steps; s++) {
			byStep.add(new ArrayList<>());
		}
		for (int i = 0; i < count; i++) {
			double angle = Math.PI * 2 * i / count + hash(seed, i) * 0.6;
			double length = radius * (0.55 + 0.45 * hash(seed, i + 50));
			Vector3f p = new Vector3f(0, 0.04f, 0);
			for (int s = 0; s < steps; s++) {
				angle += (hash(seed, i * 10 + s + 100) - 0.5) * 1.2;
				Vector3f next = new Vector3f(p).add((float) (Math.cos(angle) * length / steps), 0, (float) (Math.sin(angle) * length / steps));
				float seg = p.distance(next);
				for (float f = 0; f < 1; f += Math.min(1, 0.1f / Math.max(0.01f, seg))) {
					byStep.get(s).add(new Vector3f(p).lerp(next, f));
				}
				if (hash(seed, i * 10 + s + 700) < 0.5) {
					stars.add(new Vector3f(p).lerp(next, 0.5f).add(0, 0.02f, 0));
				}
				p = next;
			}
		}
		for (int s = 0; s < steps; s++) {
			List<Vector3f> points = byStep.get(s);
			float width = 0.24f * (1 - (float) s / steps) + 0.04f;
			int life = Math.max(2, ticks - s);
			emitters.add(new PhotonFx.Emitter(life).points(points, null).delay(s).burst(points.size()).life(life, life).speed(0, 0)
					.size(width * 1.3f, width * 1.4f).colors(hold(CRACK, 1f, life)).flat().ink());
			emitters.add(new PhotonFx.Emitter(life).points(points, null).delay(s).burst(points.size()).life(life, life).speed(0, 0)
					.size(width * 2.4f, width * 2.6f).colors(hold(VOID, 0.5f, life)).flat().glow());
			emitters.add(new PhotonFx.Emitter(life).points(points, null).delay(s).burst(points.size()).life(life, life).speed(0, 0)
					.size(width * 0.35f, width * 0.4f).colors(hold(STARLIGHT, 0.5f, life)).flat().glow());
		}
		emitters.add(new PhotonFx.Emitter(ticks).points(stars, null).delay(2).burst(stars.size()).life(ticks, ticks).speed(0, 0).size(0.08f, 0.1f)
				.colors(0x00FFFFFF, 0xFFFFFFFF, 0x50FFFFFF, 0xFFFFFFFF, 0x50FFFFFF, 0xFFFFFFFF, 0x00FFFFFF).texture(STAR).flat().glow());
		PhotonFx.play(level, at, follow, 0, emitters.toArray(PhotonFx.Emitter[]::new));
	}

	/** Motes of the void falling in towards a body from all around, swirling as they come, and stars glinting on a ring. */
	private static void motes(Level level, Entity around, float radius, int ticks) {
		PhotonFx.play(level, null, around, 0,
				new PhotonFx.Emitter(ticks).sphere(radius, 0.35f).offset(0, 1, 0).flow(5).life(10, 14).speed(0, 0).size(0.12f, 0.2f)
						.colors(0x00FFFFFF, 0xFFFFFFFF, 0xFFFFFFFF, 0x00FFFFFF).swirl(-22, 6).texture(MOTE).world().glow(),
				new PhotonFx.Emitter(ticks).ring(radius * 1.2f, 0).offset(0, 0.1f, 0).flow(0.5f).life(16, 24).speed(0, 0).size(0.1f, 0.14f)
						.colors(0x00FFFFFF, 0xFFFFFFFF, 0x40FFFFFF, 0xFFFFFFFF, 0x00FFFFFF).texture(STAR).world().glow());
	}

	// ---- Helpers ----------------------------------------------------------------------------------------------

	/** Full colour for most of the life, fading out over the last 10 ticks and in over the first 2. */
	private static int[] hold(int argb, float alpha, int life) {
		int stopsCount = 20;
		int[] colors = new int[stopsCount];
		for (int i = 0; i < stopsCount; i++) {
			float t = i / (float) (stopsCount - 1) * life;
			float a = Math.min(1, Math.min(t / 2f, (life - t) / 10f));
			colors[i] = alpha(argb, alpha * Math.max(0, a));
		}
		return colors;
	}

	/** {@code count} evenly spaced colours: {@code before} up to stop {@code from}, then {@code after}. */
	private static int[] stops(int count, int from, int before, int after) {
		int[] colors = new int[count];
		for (int i = 0; i < count; i++) {
			colors[i] = i < from ? before : after;
		}
		return colors;
	}

	static int alpha(int argb, float alpha) {
		return ((int) (Math.max(0, Math.min(1, alpha)) * 255) << 24) | (argb & 0xFFFFFF);
	}

	static int lerpColor(int a, int b, float t) {
		int r = (int) (((a >> 16) & 0xFF) * (1 - t) + ((b >> 16) & 0xFF) * t);
		int g = (int) (((a >> 8) & 0xFF) * (1 - t) + ((b >> 8) & 0xFF) * t);
		int bl = (int) ((a & 0xFF) * (1 - t) + (b & 0xFF) * t);
		return 0xFF000000 | (r << 16) | (g << 8) | bl;
	}

	static float hash(long seed, int i) {
		long h = seed * 6364136223846793005L + i * 1442695040888963407L;
		h ^= h >>> 29;
		h *= 0xBF58476D1CE4E5B9L;
		h ^= h >>> 32;
		return (h & 0xFFFFFF) / (float) 0x1000000;
	}

	private static float easeOutBack(float t) {
		float c = 1.7f;
		return 1 + (c + 1) * (float) Math.pow(t - 1, 3) + c * (float) Math.pow(t - 1, 2);
	}
}
