package euf.mageslegacy.client.fx;

import euf.mageslegacy.MagesLegacy;
import euf.mageslegacy.client.ScreenFx;
import euf.mageslegacy.fx.Fx;
import euf.mageslegacy.net.FxPayload;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

/**
 * The Voidborn's effects on Veil and GeckoLib. Shapes are drawn by shaders (see assets/mages_legacy/pinwheel): the
 * rift is a jagged lens of the void with stars and nebula deep inside and burning lips, the walk a streak of flowing
 * void, the marks on the ground cracks, waves and the gravity lens, the black hole a core with its ring of light, a
 * spiralling disk and the picture bent around it. Particles carry the first version's motes and stars; the large
 * rifts are ringed with GeckoLib shards of obsidian.
 */
public final class VoidFx {
	private static final int MOTE_TINT = 0xFFE8DEFF;
	private static final int STAR_TINT = 0xFFFFFFFF;

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
				case Fx.RIFT -> rift(payload.from(), payload.a(), payload.b(), payload.ticks());
				case Fx.RIBBON -> VoidRender.add(new Ribbon(payload.from(), payload.to(), payload.ticks(), payload.a()));
				case Fx.BURST -> VoidRender.add(new Burst(payload.from(), payload.a(), payload.ticks()));
				case Fx.RING -> VoidRender.add(new Ring(payload.from(), payload.a(), payload.b(), payload.ticks()));
				case Fx.CRACKS -> VoidRender.add(new Cracks(payload.from(), follow, payload.a(), payload.ticks()));
				case Fx.MOTES -> {
					if (follow != null) {
						VoidRender.add(new Motes(follow, payload.a(), payload.ticks()));
					}
				}
				case Fx.STEP_TRAIL -> {
					if (follow != null) {
						VoidRender.add(new Trail(follow, payload.ticks()));
					}
				}
				case Fx.SPARKLE -> sparkle(payload.from(), (int) payload.a(), payload.b());
				case Fx.GRAVITY_FIELD -> {
					if (follow != null) {
						VoidRender.add(new Lens(follow, payload.a(), payload.ticks()));
					}
				}
				case Fx.PRESS -> {
					if (follow != null) {
						VoidRender.add(new Press(follow, payload.a(), payload.b()));
					}
				}
				case Fx.VORTEX -> {
					if (follow != null) {
						VoidRender.add(new Vortex(follow, payload.ticks()));
					}
				}
				case Fx.HIDE -> VoidRender.hide(payload.entity(), payload.ticks());
				case Fx.VOID_SCREEN, Fx.SHAKE, Fx.FOV, Fx.MUFFLE -> ScreenFx.play(payload);
				default -> {
				}
			}
		} catch (RuntimeException e) {
			MagesLegacy.LOGGER.error("Voidborn effect {} failed", payload.kind(), e);
		}
	}

	// ---- The rift ----------------------------------------------------------------------------------------------

	private static void rift(Vec3 at, float height, float width, int ticks) {
		VoidRender.add(new Rift(at, height, width, ticks));
		// Doors (the Pocket's, the arena's, Through Worlds') are big: shards of obsidian wheel round them
		if (height >= 2.7f) {
			VoidShards.spawn(at, height, ticks);
		}
	}

	/** A tear in space, always facing the viewer: springs open in 5 ticks, seals in its last 6 to a white seam. */
	static final class Rift extends VoidRender.Effect {
		final Vec3 at;
		final float height;
		final float width;
		final float seed;

		Rift(Vec3 at, float height, float width, int ticks) {
			super(ticks);
			this.at = at;
			this.height = height;
			this.width = width;
			this.seed = VoidRender.RANDOM.nextFloat();
		}

		@Override
		void draw(FxDraw draw, float partial) {
			float life = life(partial);
			float open = life < 5 ? VoidRender.easeOutBack(life / 5f) : 1;
			float shut = Math.max(0, Math.min(1, (ticks - life) / 6f));
			Vec3 toCam = draw.cam.subtract(at).multiply(1, 0, 1);
			Vec3 facing = toCam.lengthSqr() < 1.0E-4 ? new Vec3(0, 0, 1) : toCam.normalize();
			Vec3 right = facing.cross(new Vec3(0, 1, 0)).normalize();
			Vec3 halfU = right.scale(width / 2 * 1.6);
			Vec3 halfV = new Vec3(0, height / 2 * 1.15 * (0.6f + 0.4f * Math.min(1, open)), 0);
			draw.quad(FxDraw.RIFT, at, halfU, halfV, open * shut / 1.25f, seed, 1 - shut, 1);
		}
	}

	// ---- The walk ---------------------------------------------------------------------------------------------

	/** A streak of the void along a path, upright and lying flat at once; its tail draws in towards the head. */
	static final class Ribbon extends VoidRender.Effect {
		final Vec3 from;
		final Vec3 to;
		final float height;
		final float seed;

		Ribbon(Vec3 from, Vec3 to, int ticks, float height) {
			super(ticks);
			this.from = from.add(0, 1, 0);
			this.to = to.add(0, 1, 0);
			this.height = height;
			this.seed = VoidRender.RANDOM.nextFloat();
		}

		@Override
		void draw(FxDraw draw, float partial) {
			float t = progress(partial);
			Vec3 dir = to.subtract(from);
			if (dir.lengthSqr() < 1.0E-4) {
				return;
			}
			Vec3 flat = dir.cross(new Vec3(0, 1, 0));
			flat = flat.lengthSqr() < 1.0E-4 ? new Vec3(1, 0, 0) : flat.normalize();
			Vec3 up = flat.cross(dir).normalize();
			int segments = 24;
			for (Vec3 across : new Vec3[] {up.scale(height * 0.5), flat.scale(height * 0.35)}) {
				for (int i = 0; i < segments; i++) {
					float u0 = (float) i / segments;
					float u1 = (float) (i + 1) / segments;
					Vec3 a = from.lerp(to, u0);
					Vec3 b = from.lerp(to, u1);
					draw.strip(FxDraw.RIBBON, new Vec3[] {a.subtract(across), b.subtract(across), b.add(across), a.add(across)},
							new float[] {u0, u1, u1, u0}, new float[] {0, 0, 1, 1}, t * t, seed, 0, 1 - t * 0.6f);
				}
			}
		}
	}

	/** What a walker leaves along the way: a cloud of void motes and stars shed where the body passes. */
	static final class Trail extends VoidRender.Effect {
		final Entity walker;

		Trail(Entity walker, int ticks) {
			super(ticks);
			this.walker = walker;
		}

		@Override
		boolean tick() {
			Vec3 at = walker.position().add(0, 1, 0);
			for (int i = 0; i < 8; i++) {
				Vec3 p = at.add(VoidRender.rand(-0.4, 0.4), VoidRender.rand(-0.8, 0.8), VoidRender.rand(-0.4, 0.4));
				VoidRender.add(new VoidRender.Particle(p, VoidRender.randomDir().scale(0.03), 14 + VoidRender.RANDOM.nextInt(10), 0.14f, MOTE_TINT, false,
						0.9f, 0.6f));
			}
			for (int i = 0; i < 4; i++) {
				Vec3 p = at.add(VoidRender.rand(-0.5, 0.5), VoidRender.rand(-0.7, 0.7), VoidRender.rand(-0.5, 0.5));
				VoidRender.add(new VoidRender.Particle(p, Vec3.ZERO, 10 + VoidRender.RANDOM.nextInt(10), 0.07f, STAR_TINT, true, 0.9f, 1f));
			}
			return super.tick();
		}

		@Override
		void draw(FxDraw draw, float partial) {
		}
	}

	/** Void motes thrown out from a point, and stars glinting round it (no motes: only stars, more of them). */
	private static void sparkle(Vec3 at, int motes, float speed) {
		for (int i = 0; i < motes; i++) {
			VoidRender.add(new VoidRender.Particle(at, VoidRender.randomDir().scale(speed * VoidRender.rand(0.6, 1.4)).multiply(1, 1.3, 1),
					14 + VoidRender.RANDOM.nextInt(10), 0.16f, MOTE_TINT, false, 0.88f, 0.7f));
		}
		int stars = motes > 0 ? 12 : 20;
		for (int i = 0; i < stars; i++) {
			Vec3 p = at.add(VoidRender.rand(-0.8, 0.8), VoidRender.rand(-1, 1), VoidRender.rand(-0.8, 0.8));
			VoidRender.add(new VoidRender.Particle(p, Vec3.ZERO, 12 + VoidRender.RANDOM.nextInt(12), 0.08f, STAR_TINT, true, 0.9f, 1.2f));
		}
	}

	/** A shell of the void swelling out from a point and fading. */
	static final class Burst extends VoidRender.Effect {
		final Vec3 at;
		final float radius;

		Burst(Vec3 at, float radius, int ticks) {
			super(ticks);
			this.at = at;
			this.radius = radius;
		}

		@Override
		void draw(FxDraw draw, float partial) {
			float t = progress(partial);
			Billboard b = Billboard.facing(draw.cam, at);
			float size = radius * 1.15f;
			draw.quad(FxDraw.ORB, at, b.right.scale(size), b.up.scale(size), VoidRender.easeOut(t) * 0.87f, 0.3f, FxDraw.SHELL, (1 - t) * (1 - t));
		}
	}

	/** A wave spreading on the ground from one radius to another. */
	static final class Ring extends VoidRender.Effect {
		final Vec3 at;
		final float from;
		final float to;

		Ring(Vec3 at, float from, float to, int ticks) {
			super(ticks);
			this.at = at.add(0, 0.06, 0);
			this.from = from;
			this.to = to;
		}

		@Override
		void draw(FxDraw draw, float partial) {
			float t = progress(partial);
			float half = Math.max(from, to) + 0.5f;
			float r = (from + (to - from) * VoidRender.easeOut(t)) / half;
			draw.quad(FxDraw.GROUND, at, new Vec3(half, 0, 0), new Vec3(0, 0, half), r, 0.5f, FxDraw.RING, (float) Math.pow(1 - t, 1.5));
		}
	}

	/** The ground cracked open onto the void (riding along with a body, if it follows one). */
	static final class Cracks extends VoidRender.Effect {
		final Vec3 at;
		final Entity follow;
		final float radius;
		final float seed;

		Cracks(Vec3 at, Entity follow, float radius, int ticks) {
			super(ticks);
			this.at = at;
			this.follow = follow;
			this.radius = radius;
			this.seed = VoidRender.RANDOM.nextFloat();
		}

		@Override
		void draw(FxDraw draw, float partial) {
			float life = life(partial);
			float grow = VoidRender.easeOut(Math.min(1, life / 6f));
			float fade = Math.min(1, (ticks - life) / 10f) * Math.min(1, life / 2f);
			Vec3 c = (follow != null ? follow.getPosition(partial) : at).add(0, 0.04, 0);
			draw.quad(FxDraw.GROUND, c, new Vec3(radius, 0, 0), new Vec3(0, 0, radius), grow, seed, FxDraw.CRACKS, fade);
		}
	}

	/** Motes of the void falling in towards a body from all around. */
	static final class Motes extends VoidRender.Effect {
		final Entity around;
		final float radius;

		Motes(Entity around, float radius, int ticks) {
			super(ticks);
			this.around = around;
			this.radius = radius;
		}

		@Override
		boolean tick() {
			Vec3 heart = around.position().add(0, 1, 0);
			for (int i = 0; i < 4; i++) {
				Vec3 dir = VoidRender.randomDir();
				Vec3 p = heart.add(dir.scale(radius * VoidRender.rand(0.9, 1.3)));
				VoidRender.Particle mote = new VoidRender.Particle(p, dir.cross(new Vec3(0, 1, 0)).scale(0.05), 20, 0.15f, MOTE_TINT, false, 0.92f,
						0.7f);
				mote.attract = heart;
				mote.pull = 0.04f;
				VoidRender.add(mote);
			}
			return super.tick();
		}

		@Override
		void draw(FxDraw draw, float partial) {
		}
	}

	// ---- Heavier Gravity ----------------------------------------------------------------------------------------

	/**
	 * Heavier Gravity: space folds in on the caster (a shell imploding), then the ground around them is a lens of
	 * darkness with rings tightening across it and a rim of violet; streaks of weight drive down onto it from above.
	 */
	static final class Lens extends VoidRender.Effect {
		final Entity caster;
		final float reach;
		final float seed;

		Lens(Entity caster, float reach, int ticks) {
			super(ticks);
			this.caster = caster;
			this.reach = reach;
			this.seed = VoidRender.RANDOM.nextFloat();
		}

		@Override
		boolean tick() {
			Vec3 c = caster.position();
			for (int i = 0; i < Math.round(reach * 1.5f); i++) {
				double a = VoidRender.rand(0, Math.PI * 2);
				double r = reach * Math.sqrt(VoidRender.rand(0.1, 1));
				VoidRender.Particle streak = new VoidRender.Particle(c.add(Math.cos(a) * r, VoidRender.rand(2.6, 3.6), Math.sin(a) * r),
						new Vec3(0, -0.35, 0), 8, 0.06f, STAR_TINT, true, 1f, 1.3f);
				VoidRender.add(streak);
			}
			return super.tick();
		}

		@Override
		void draw(FxDraw draw, float partial) {
			float life = life(partial);
			float fade = Math.min(1, life / 4f) * Math.min(1, (ticks - life) / 8f);
			Vec3 c = caster.getPosition(partial).add(0, 0.05, 0);
			draw.quad(FxDraw.GROUND, c, new Vec3(reach, 0, 0), new Vec3(0, 0, reach), 1, seed, FxDraw.LENS, fade);
			// The field's edge, always there so the eye knows where the weight ends, and a ring tightening in with each beat
			float half = reach + 0.5f;
			Vec3 ex = new Vec3(half, 0, 0);
			Vec3 ez = new Vec3(0, 0, half);
			draw.quad(FxDraw.GROUND, c.add(0, 0.01, 0), ex, ez, reach / half, 0.5f, FxDraw.RING, fade * 0.85f);
			float beat = (life % 20) / 20f;
			float squeeze = reach * (1 - VoidRender.easeOut(Math.min(1, beat * 1.4f)));
			draw.quad(FxDraw.GROUND, c.add(0, 0.02, 0), ex, ez, squeeze / half, 0.5f, FxDraw.RING, fade * (1 - beat) * 0.95f);
			// The fold: for its first 8 ticks a shell of void closes in on the caster
			if (life < 8) {
				float t = life / 8f;
				Vec3 heart = c.add(0, 1, 0);
				Billboard b = Billboard.facing(draw.cam, heart);
				float size = reach + 1.4f;
				draw.quad(FxDraw.ORB, heart, b.right.scale(size), b.up.scale(size), (1 - t) * 0.95f, seed, FxDraw.FOLD, t < 0.2f ? t * 5 : 1);
			}
		}
	}

	/** The weight landing on one held creature: a ring snapping shut at its feet, streaks driving down on it. */
	static final class Press extends VoidRender.Effect {
		final Entity target;
		final float width;

		Press(Entity target, float width, float height) {
			super(10);
			this.target = target;
			this.width = width;
			Vec3 c = target.position();
			for (int i = 0; i < 6; i++) {
				VoidRender.add(new VoidRender.Particle(c.add(VoidRender.rand(-width, width), height + VoidRender.rand(1.0, 1.8), VoidRender.rand(-width, width)),
						new Vec3(0, -0.4, 0), 6, 0.06f, STAR_TINT, true, 1f, 1.3f));
			}
		}

		@Override
		void draw(FxDraw draw, float partial) {
			float t = progress(partial);
			float half = width + 0.8f;
			draw.quad(FxDraw.GROUND, target.getPosition(partial).add(0, 0.07, 0), new Vec3(half, 0, 0), new Vec3(0, 0, half),
					(1 - VoidRender.easeOut(t)) * 0.95f, 0.5f, FxDraw.RING, 1 - t);
		}
	}

	// ---- The black hole ---------------------------------------------------------------------------------------

	/**
	 * The black hole around its heart: the core of nothing with its white ring, a tilted disk of fire spiralling in,
	 * motes falling in from all around, and the picture itself bent round it (the lens pipeline).
	 */
	static final class Vortex extends VoidRender.Effect {
		final Entity heart;
		final float seed;

		Vortex(Entity heart, int ticks) {
			super(ticks);
			this.heart = heart;
			this.seed = VoidRender.RANDOM.nextFloat();
		}

		private float size(float life) {
			float grow = VoidRender.easeOutBack(Math.min(1, life / 8f));
			float shrink = Math.max(0, Math.min(1, (ticks - life) / 6f));
			return Math.max(0, grow * shrink);
		}

		@Override
		boolean tick() {
			Vec3 c = heart.position().add(0, 1.2, 0);
			for (int i = 0; i < 5; i++) {
				Vec3 dir = VoidRender.randomDir();
				VoidRender.Particle mote = new VoidRender.Particle(c.add(dir.scale(VoidRender.rand(5, 9))), dir.cross(new Vec3(0, 1, 0)).scale(0.1), 30,
						0.16f, MOTE_TINT, false, 0.93f, 0.8f);
				mote.attract = c;
				mote.pull = 0.06f;
				VoidRender.add(mote);
			}
			return super.tick();
		}

		@Override
		void draw(FxDraw draw, float partial) {
			float size = size(life(partial));
			if (size <= 0.01f) {
				return;
			}
			VoidRender.lensHeart = heart;
			VoidRender.lensStrength = size;
			Vec3 c = heart.getPosition(partial).add(0, 1.2, 0);
			// The disk, tilted a little so it reads as a disk from anywhere
			Vec3 normal = new Vec3(0.3, 1, 0.15).normalize();
			Vec3 e1 = normal.cross(new Vec3(0, 0, 1)).normalize();
			Vec3 e2 = normal.cross(e1).normalize();
			float disk = 4.2f * size;
			draw.quad(FxDraw.GROUND, c, e1.scale(disk), e2.scale(disk), 1, seed, FxDraw.DISK, 1);
			Billboard b = Billboard.facing(draw.cam, c);
			float core = 2.1f * size;
			draw.quad(FxDraw.ORB, c, b.right.scale(core), b.up.scale(core), 1, seed, FxDraw.HOLE, 1);
		}
	}

	/** Two directions across the view at a point: for quads that face the camera. */
	record Billboard(Vec3 right, Vec3 up) {
		static Billboard facing(Vec3 cam, Vec3 at) {
			Vec3 n = cam.subtract(at);
			n = n.lengthSqr() < 1.0E-4 ? new Vec3(0, 0, 1) : n.normalize();
			Vec3 right = n.cross(new Vec3(0, 1, 0));
			right = right.lengthSqr() < 1.0E-4 ? new Vec3(1, 0, 0) : right.normalize();
			Vec3 up = right.cross(n).normalize();
			return new Billboard(right, up);
		}
	}
}
