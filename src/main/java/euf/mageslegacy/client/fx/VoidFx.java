package euf.mageslegacy.client.fx;

import euf.mageslegacy.MagesLegacy;
import euf.mageslegacy.fx.Fx;
import euf.mageslegacy.net.FxPayload;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

/**
 * The Voidborn's effects, built on Photon. The void is drawn twice over: real dark "ink" that eats the light, and
 * violet glow at its edges, so it reads by day and by night.
 */
public final class VoidFx {
	private static final int INK = 0xF0080412;
	private static final int INK_FADE = 0x00080412;
	private static final int VIOLET = 0xFFB27CFF;
	private static final int VIOLET_DEEP = 0xFF6A3AC8;
	private static final int STAR = 0xFFF2E8FF;

	private VoidFx() {
	}

	public static void play(FxPayload payload) {
		Level level = Minecraft.getInstance().level;
		if (level == null) {
			return;
		}
		Entity follow = payload.entity() >= 0 ? level.getEntity(payload.entity()) : null;
		try {
			switch (payload.kind()) {
				case Fx.RIFT -> rift(level, payload.from(), 1f, payload.ticks());
				case Fx.POCKET_RIFT -> rift(level, payload.from(), 1.6f, payload.ticks());
				case Fx.STEP_PATH -> path(level, payload.from(), payload.to(), payload.ticks());
				case Fx.STEP_VEIL -> {
					if (follow != null) {
						veil(level, follow, payload.ticks());
					}
				}
				case Fx.GRAVITY -> {
					if (follow != null) {
						gravity(level, follow, payload.ticks());
					}
				}
				default -> {
				}
			}
		} catch (RuntimeException e) {
			// An effect must never take the game down with it
			MagesLegacy.LOGGER.error("Photon effect {} failed", payload.kind(), e);
		}
	}

	/**
	 * A tear in space: a tall thin seam of darkness that draws the air into itself, violet sparks spiralling in along
	 * its edges, a ring of light snapping shut around it, and a few stars glinting from the other side.
	 */
	private static void rift(Level level, Vec3 at, float size, int ticks) {
		int open = Math.max(10, ticks);
		PhotonFx.play(level, at, null, 0,
				// The seam: dark ink drawn inwards
				new PhotonFx.Emitter(open).sphere(0.6f * size, 0.3f).scale(0.35f, 2.4f, 0.35f).flow(16 * size).burst((int) (40 * size))
						.life(10, 18).speed(0, 0.01f).size(0.22f * size, 0.38f * size).sizeCurve(0.4f, 1f, 0f)
						.colors(INK_FADE, INK, INK, INK_FADE).swirl(-4, 0).ink(),
				// Violet sparks spiralling in at the edges
				new PhotonFx.Emitter(open).sphere(0.9f * size, 0.3f).scale(0.6f, 1.9f, 0.6f).flow(12 * size).burst((int) (36 * size))
						.life(12, 22).speed(0, 0.01f).size(0.07f, 0.13f).sizeCurve(0.2f, 1f, 0f)
						.colors(0x00B27CFF, VIOLET, VIOLET_DEEP, 0x006A3AC8).swirl(-6, 8).glow(),
				// The ring closing in once, as the rift opens
				new PhotonFx.Emitter(4).sphere(1.6f * size, 0.05f).scale(1f, 1.4f, 1f).burst((int) (60 * size))
						.life(8, 12).speed(0, 0).size(0.06f, 0.1f).sizeCurve(1f, 1f, 0f)
						.colors(STAR, VIOLET, 0x006A3AC8).swirl(-14, 0).glow(),
				// Stars of the other side
				new PhotonFx.Emitter(open).sphere(0.3f * size, 1f).scale(0.3f, 2f, 0.3f).flow(1.5f * size)
						.life(8, 16).speed(0, 0).size(0.04f, 0.07f).sizeCurve(0f, 1f, 0f)
						.colors(0x00F2E8FF, STAR, 0x00F2E8FF).glow());
	}

	/** The way through the in-between: dark threads along the path, lit one after another as the walker passes. */
	private static void path(Level level, Vec3 from, Vec3 to, int ticks) {
		double length = from.distanceTo(to);
		int points = Math.max(2, (int) Math.ceil(length / 1.2));
		for (int i = 0; i <= points; i++) {
			double t = i / (double) points;
			Vec3 at = from.lerp(to, t);
			int delay = (int) Math.round(ticks * (t * t * (3 - 2 * t)));
			PhotonFx.play(level, at, null, delay,
					new PhotonFx.Emitter(3).sphere(0.45f, 1f).burst(10).life(14, 24).speed(0, 0.01f).size(0.2f, 0.34f)
							.sizeCurve(0.3f, 1f, 0f).colors(INK_FADE, INK, INK_FADE).noise(0.4f).ink(),
					new PhotonFx.Emitter(3).sphere(0.6f, 0.4f).burst(6).life(10, 18).speed(0, 0.02f).size(0.05f, 0.08f)
							.sizeCurve(0.5f, 1f, 0f).colors(VIOLET, VIOLET_DEEP, 0x006A3AC8).swirl(-3, 6).glow());
		}
	}

	/** The in-between clinging to the walker: a shroud of dark motes and violet wisps around the body. */
	private static void veil(Level level, Entity follow, int ticks) {
		PhotonFx.play(level, null, follow, 0,
				new PhotonFx.Emitter(ticks).sphere(0.9f, 0.5f).offset(0, 1f, 0).scale(0.8f, 1.3f, 0.8f).flow(8).life(8, 14).speed(0, 0.01f)
						.size(0.2f, 0.3f).sizeCurve(0.3f, 1f, 0f).colors(INK_FADE, INK, INK_FADE).swirl(-3, 10).world().ink(),
				new PhotonFx.Emitter(ticks).sphere(1.1f, 0.3f).offset(0, 1f, 0).scale(0.8f, 1.3f, 0.8f).flow(5).life(8, 14).speed(0, 0.01f)
						.size(0.04f, 0.08f).sizeCurve(0.4f, 1f, 0f).colors(VIOLET, VIOLET_DEEP, 0x006A3AC8).swirl(-5, 14).world().glow());
	}

	/**
	 * Heavier gravity: motes falling hard out of the air onto a ring around the caster, a dark disc pressed into the
	 * ground and violet dust pulled inwards along it, for as long as it lasts.
	 */
	private static void gravity(Level level, Entity follow, int ticks) {
		PhotonFx.play(level, null, follow, 0,
				// Falling from above, fast
				new PhotonFx.Emitter(ticks).ring(2f, 0.15f).offset(0, 3.2f, 0).flow(6).life(8, 12).speed(0, 0).size(0.04f, 0.07f)
						.sizeCurve(1f, 1f, 0.3f).colors(0x00F2E8FF, STAR, VIOLET, 0x006A3AC8).force(0, -0.06f, 0).world().glow(),
				// The pressed ground: dark ink lying flat
				new PhotonFx.Emitter(ticks).ring(2f, 0.35f).offset(0, 0.08f, 0).flow(10).life(10, 16).speed(0, 0).size(0.25f, 0.4f)
						.sizeCurve(0.2f, 1f, 0.6f).colors(INK_FADE, 0xC0080412, INK_FADE).swirl(-2, 4).ink(),
				// Violet dust sucked inward along the ground
				new PhotonFx.Emitter(ticks).ring(2.2f, 0.2f).offset(0, 0.15f, 0).flow(5).life(14, 20).speed(0, 0).size(0.05f, 0.09f)
						.sizeCurve(0.5f, 1f, 0f).colors(VIOLET, VIOLET_DEEP, 0x006A3AC8).swirl(-10, 12).glow(),
				// One heavy pulse as it lands
				new PhotonFx.Emitter(3).ring(0.3f, 1f).offset(0, 0.1f, 0).burst(50).life(10, 14).speed(0, 0).size(0.08f, 0.12f)
						.sizeCurve(1f, 1f, 0f).colors(STAR, VIOLET, 0x006A3AC8).swirl(22, 0).glow());
	}
}
