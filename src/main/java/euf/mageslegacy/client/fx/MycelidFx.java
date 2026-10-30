package euf.mageslegacy.client.fx;

import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * The Mycelid's effects, built to be seen from every side and to cost almost nothing: a handful of painted quads
 * shaped into volumes (a dome of haze, a funnel of strands, a low-poly mushroom), no bloom on anything large, and a
 * hard cap on motes. Budget per effect: the cloud ~40 quads, a bond ~20, the storm ~70, the bloom ~70.
 */
final class MycelidFx {
	private static final int[] MOTES = {0xFF8CE07A, 0xFF7FE8C8, 0xFFB0F5C8, 0xFFE8D27A};
	/** No Mycelid effect adds motes while this many particles are already alive. */
	private static final int MOTE_CAP = 500;

	private MycelidFx() {
	}

	private static int mote() {
		return MOTES[VoidRender.RANDOM.nextInt(MOTES.length)];
	}

	private static float fade(float life, int ticks, float in, float out) {
		return Math.max(0, Math.min(1, life / in) * Math.min(1, (ticks - life) / out));
	}

	/** A mote: small, short-lived, and dropped if the particle budget is spent. */
	private static void moteAt(Vec3 at, Vec3 vel, int life, double size) {
		if (VoidRender.PARTICLES.size() < MOTE_CAP) {
			VoidRender.add(new VoidRender.Particle(at, vel, life, (float) size, mote(), true, 0.98f, 0.5f));
		}
	}

	/** Motes thrown out round a point, as far as this radius: the moment of the cast, seen from every side. */
	private static void burst(Vec3 at, double radius, int count) {
		for (int i = 0; i < count; i++) {
			double a = VoidRender.rand(0, Math.PI * 2);
			double v = radius / 15 * VoidRender.rand(0.5, 1);
			moteAt(at.add(0, VoidRender.rand(0.2, 1.2), 0), new Vec3(Math.cos(a) * v, VoidRender.rand(0.02, 0.08), Math.sin(a) * v), 18, VoidRender.rand(0.1, 0.2));
		}
	}

	/** A painted decal lying on the ground: centred, this half-size, turned by an angle (radians). No glow. */
	private static void decal(FxDraw draw, String tex, Vec3 at, float half, double angle, float a) {
		Vec3 u = new Vec3(Math.cos(angle) * half, 0, Math.sin(angle) * half);
		Vec3 v = new Vec3(-Math.sin(angle) * half, 0, Math.cos(angle) * half);
		draw.paint(tex, at, u, v, 0.72f, 0.85f, 0.75f, a, 0);
	}

	/** A camera-facing band of a painted texture from a to b, this wide; the texture runs along it. */
	private static void band(FxDraw draw, String tex, Vec3 a, Vec3 b, float width, float alpha) {
		Vec3 along = b.subtract(a);
		Vec3 side = along.cross(draw.cam.subtract(a.add(b).scale(0.5)));
		if (side.lengthSqr() < 1.0E-6) {
			return;
		}
		side = side.normalize().scale(width / 2);
		draw.paintStrip(tex, new Vec3[] {a.subtract(side), b.subtract(side), b.add(side), a.add(side)}, new float[] {0, 1, 1, 0}, new float[] {0, 0, 1, 1},
				1, 1, 1, alpha, 0);
	}

	/**
	 * A painted puff facing the camera, turned in its own plane. It fades out near the eyes, so standing inside a
	 * cloud never whites out the view.
	 */
	private static void puff(FxDraw draw, String tex, Vec3 at, float half, double angle, float a) {
		Vec3 toCam = draw.cam.subtract(at);
		float near = (float) Math.max(0, Math.min(1, (toCam.length() - 1.3) / 2.2));
		Vec3 right = toCam.cross(new Vec3(0, 1, 0));
		if (near <= 0 || right.lengthSqr() < 1.0E-6) {
			return;
		}
		right = right.normalize();
		Vec3 up = right.cross(toCam).normalize();
		double cos = Math.cos(angle) * half;
		double sin = Math.sin(angle) * half;
		draw.paint(tex, at, right.scale(cos).add(up.scale(sin)), right.scale(-sin).add(up.scale(cos)), 0.72f, 0.85f, 0.75f, a * near, 0);
	}

	/**
	 * A beaded strand winding up from base: this high, its radius widening from r0 to r1, turning this many times
	 * (a funnel for the storm, a spiral round a body for the symbiosis). Eight segments.
	 */
	private static void strand(FxDraw draw, Vec3 base, float height, float r0, float r1, double turns, double phase, float width, float alpha) {
		int segs = 8;
		Vec3 prev = null;
		for (int i = 0; i <= segs; i++) {
			float t = i / (float) segs;
			double ang = phase + turns * Math.PI * 2 * t;
			float r = r0 + (r1 - r0) * t;
			Vec3 p = base.add(Math.cos(ang) * r, height * t, Math.sin(ang) * r);
			if (prev != null) {
				band(draw, FxDraw.THREAD, prev, p, width, alpha * (1 - 0.6f * t));
			}
			prev = p;
		}
	}

	private static boolean firstPersonOn(Entity e) {
		Minecraft mc = Minecraft.getInstance();
		return e == mc.player && mc.options.getCameraType().isFirstPerson();
	}

	// ---- Neurospores ------------------------------------------------------------------------------------------

	/**
	 * A cloud of neurospores as a volume: a dome of haze standing over its whole reach (it swells out at the cast, then
	 * turns slowly), soft puffs rising inside, spores streaming up its edge, and a dizzy halo of motes over the head of
	 * every enemy inside, which is what the spores do to them.
	 */
	static final class Cloud extends VoidRender.Effect {
		private static final int PUFFS = 6;
		private static final int AROUND = 10;
		private static final int RINGS = 3;
		Vec3 at;
		final Entity follow;
		final float radius;
		final double spin = VoidRender.rand(0, Math.PI * 2);
		final float[] px = new float[PUFFS];
		final float[] pz = new float[PUFFS];
		final float[] phase = new float[PUFFS];
		final float[] size = new float[PUFFS];

		Cloud(Vec3 at, Entity follow, float radius, int ticks) {
			super(ticks);
			this.at = at;
			this.follow = follow;
			this.radius = radius;
			for (int i = 0; i < PUFFS; i++) {
				double a = VoidRender.rand(0, Math.PI * 2);
				double r = radius * 0.7 * Math.sqrt(VoidRender.rand(0.1, 1));
				px[i] = (float) (Math.cos(a) * r);
				pz[i] = (float) (Math.sin(a) * r);
				phase[i] = (float) VoidRender.rand(0, 1);
				size[i] = (float) VoidRender.rand(1.1, 1.7);
			}
			VoidRender.add(new Ring(at, 0.3f, radius, 14));
			burst(at, radius, 30);
		}

		@Override
		boolean tick() {
			if (follow != null) {
				at = follow.position();
			}
			if (age % 3 == 0) {
				double e = VoidRender.rand(0, Math.PI * 2);
				// Along the edge: spores streaming up, the cloud's reach
				moteAt(at.add(Math.cos(e) * radius, VoidRender.rand(0.1, 0.5), Math.sin(e) * radius), new Vec3(0, VoidRender.rand(0.04, 0.07), 0), 28,
						VoidRender.rand(0.1, 0.16));
			}
			var level = Minecraft.getInstance().level;
			if (level != null && age % 6 == 0) {
				int shown = 0;
				for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class, new AABB(at, at).inflate(radius, 4, radius), x -> x instanceof Enemy && x.isAlive())) {
					if (shown++ >= 6 || e.position().distanceToSqr(at) > radius * radius) {
						continue;
					}
					double ang = (age + e.getId() * 7) * 0.5;
					Vec3 head = e.position().add(0, e.getBbHeight() + 0.25, 0);
					for (int k = 0; k < 2; k++) {
						double aa = ang + k * Math.PI;
						moteAt(head.add(Math.cos(aa) * 0.4, 0, Math.sin(aa) * 0.4), new Vec3(-Math.sin(aa) * 0.1, 0.01, Math.cos(aa) * 0.1), 10, 0.1);
					}
				}
			}
			return super.tick();
		}

		@Override
		void draw(FxDraw draw, float partial) {
			float life = life(partial);
			float a = fade(life, ticks, 6, 12);
			Vec3 c = (follow != null ? follow.getPosition(partial) : at).add(0, 0.06, 0);
			decal(draw, FxDraw.CLOUD, c, radius, spin + life * 0.012, a * 0.28f);
			// The dome: it swells out in the first ticks, then breathes
			float swell = VoidRender.easeOut(Math.min(1, life / 10f)) * (1 + 0.03f * (float) Math.sin(life * 0.15));
			float height = Math.min(radius * 0.6f, 3.5f) * swell;
			double turn = spin + life * 0.015;
			Vec3[][] grid = new Vec3[RINGS + 1][AROUND + 1];
			for (int j = 0; j <= RINGS; j++) {
				double phi = 1.35 * j / RINGS;
				double r = radius * swell * Math.sin(phi);
				double y = height * Math.cos(phi);
				for (int i = 0; i <= AROUND; i++) {
					double t = turn + Math.PI * 2 * i / AROUND;
					grid[j][i] = c.add(Math.cos(t) * r, y, Math.sin(t) * r);
				}
			}
			for (int j = 0; j < RINGS; j++) {
				float edge = j == 0 ? 0.55f : 1f;
				for (int i = 0; i < AROUND; i++) {
					draw.paintStrip(FxDraw.CLOUD, new Vec3[] {grid[j][i], grid[j][i + 1], grid[j + 1][i + 1], grid[j + 1][i]}, new float[] {0, 1, 1, 0},
							new float[] {0, 0, 1, 1}, 0.72f, 0.85f, 0.75f, a * 0.15f * edge, 0);
				}
			}
			// Puffs rising through the volume, each swelling and dissolving on its way up
			double cos = Math.cos(life * 0.008);
			double sin = Math.sin(life * 0.008);
			for (int i = 0; i < PUFFS; i++) {
				float t = (life * 0.012f + phase[i]) % 1;
				Vec3 p = c.add(px[i] * cos - pz[i] * sin, 0.3 + t * height * 0.8, px[i] * sin + pz[i] * cos);
				puff(draw, FxDraw.CLOUD, p, size[i] * (0.7f + 0.6f * t), spin + i + life * 0.01, a * 0.3f * (float) Math.sin(Math.PI * t));
			}
		}
	}

	/** A ring running out over the ground. */
	static final class Ring extends VoidRender.Effect {
		final Vec3 at;
		final float from;
		final float to;

		Ring(Vec3 at, float from, float to, int ticks) {
			super(ticks);
			this.at = at.add(0, 0.08, 0);
			this.from = from;
			this.to = to;
		}

		@Override
		void draw(FxDraw draw, float partial) {
			float t = progress(partial);
			float r = from + (to - from) * VoidRender.easeOut(t);
			decal(draw, FxDraw.FIELD, at, r, 0, (float) Math.pow(1 - t, 1.3) * 0.7f);
		}
	}

	// ---- Neurosymbiosis ---------------------------------------------------------------------------------------

	/**
	 * What one sharing the symbiosis wears: a pair of beaded strands spiralling up its body, spores rising off it, a
	 * turning aura underfoot and a glowing thread to the Mycelid. The caster's own bond also sends a wave out to the
	 * reach of the spell, so it is plain who it touched. (In first person the caster's own strands are left out: they
	 * would hang in front of the eyes.)
	 */
	static final class Bond extends VoidRender.Effect {
		final Entity caster;
		final Entity ally;

		Bond(Entity caster, Entity ally, float radius, int ticks) {
			super(ticks);
			this.caster = caster;
			this.ally = ally;
			if (radius > 0) {
				VoidRender.add(new Ring(ally.position(), 0.5f, radius, 18));
				burst(ally.position(), radius * 0.6, 20);
			}
		}

		@Override
		boolean tick() {
			if (ally.isRemoved()) {
				return false;
			}
			if (age % 4 == 0 && !firstPersonOn(ally)) {
				double a = VoidRender.rand(0, Math.PI * 2);
				double r = ally.getBbWidth() * 0.6;
				moteAt(ally.position().add(Math.cos(a) * r, VoidRender.rand(0, ally.getBbHeight()), Math.sin(a) * r), new Vec3(0, VoidRender.rand(0.03, 0.06), 0), 18,
						VoidRender.rand(0.07, 0.12));
			}
			return super.tick();
		}

		@Override
		void draw(FxDraw draw, float partial) {
			float life = life(partial);
			float a = fade(life, ticks, 5, 10);
			Vec3 to = ally.getPosition(partial);
			decal(draw, FxDraw.AURA, to.add(0, 0.05, 0), Math.max(0.8f, ally.getBbWidth() * 1.3f), life * 0.05, a * 0.6f);
			if (!firstPersonOn(ally)) {
				float h = ally.getBbHeight() + 0.2f;
				float r = ally.getBbWidth() * 0.7f + 0.2f;
				float width = Math.max(0.2f, r * 0.45f);
				double spin = life * 0.14;
				strand(draw, to, h, r, r, 1.5, spin, width, a * 0.85f);
				strand(draw, to, h, r, r, 1.5, spin + Math.PI, width, a * 0.85f);
			}
			if (caster != null && caster != ally && !caster.isRemoved()) {
				Vec3 from = caster.getPosition(partial).add(0, caster.getBbHeight() * 0.6, 0);
				float pulse = 0.75f + 0.25f * (float) Math.sin(life * 0.4);
				band(draw, FxDraw.THREAD, from, to.add(0, ally.getBbHeight() * 0.6, 0), 0.35f, a * pulse);
			}
		}
	}

	// ---- Spore Storm ------------------------------------------------------------------------------------------

	/**
	 * The Spore Storm: a funnel of beaded strands winding up from the heart, a wall of pillars standing at the edge of
	 * the area it rules, a few spores spun round it, and a vortex turning on the ground. About 70 quads and 50 motes.
	 */
	static final class Storm extends VoidRender.Effect {
		private static final int PILLARS = 12;
		final Entity heart;
		final float radius;

		Storm(Entity heart, float radius, int ticks) {
			super(ticks);
			this.heart = heart;
			this.radius = radius;
			VoidRender.add(new Ring(heart.position(), 0.5f, radius, 16));
			burst(heart.position(), Math.min(radius, 12), 30);
		}

		@Override
		boolean tick() {
			Vec3 c = heart.position();
			for (int i = 0; i < 2; i++) {
				double a = VoidRender.rand(0, Math.PI * 2);
				double r = Math.min(radius, 10) * Math.sqrt(VoidRender.rand(0.05, 1));
				Vec3 around = new Vec3(-Math.sin(a), 0, Math.cos(a)).scale(VoidRender.rand(0.2, 0.4));
				VoidRender.Particle p = new VoidRender.Particle(c.add(Math.cos(a) * r, VoidRender.rand(0.2, 3.5), Math.sin(a) * r), around.add(0, 0.03, 0), 26,
						(float) VoidRender.rand(0.12, 0.24), mote(), true, 0.97f, 0.5f);
				p.attract = c.add(0, 2, 0);
				p.pull = 0.02f;
				if (VoidRender.PARTICLES.size() < MOTE_CAP) {
					VoidRender.add(p);
				}
			}
			return super.tick();
		}

		@Override
		void draw(FxDraw draw, float partial) {
			float life = life(partial);
			float a = fade(life, ticks, 10, 15);
			Vec3 c = heart.getPosition(partial);
			// The funnel: four strands winding up and widening, turning fast
			float grow = Math.min(1, life / 14f);
			for (int i = 0; i < 4; i++) {
				strand(draw, c, 9 * grow, 0.8f, 5.2f, 1.2, life * 0.22 + i * Math.PI / 2, 0.9f, a * 0.7f);
			}
			// The wall at the edge of the storm: pillars of spores, turning slowly
			for (int i = 0; i < PILLARS; i++) {
				double ang = life * 0.01 + i * Math.PI * 2 / PILLARS;
				Vec3 foot = c.add(Math.cos(ang) * radius, 0, Math.sin(ang) * radius);
				float rise = 4 + 2 * (float) Math.sin(life * 0.08 + i);
				band(draw, FxDraw.THREAD, foot, foot.add(0, rise, 0), 0.8f, a * 0.28f);
			}
			decal(draw, FxDraw.SPIRAL, c.add(0, 0.07, 0), radius, -life * 0.06, a * 0.35f);
			decal(draw, FxDraw.SPIRAL, c.add(0, 0.09, 0), radius * 0.5f, -life * 0.11 + 1.0, a * 0.25f);
		}
	}

	// ---- The Great Bloom --------------------------------------------------------------------------------------

	private static final java.util.List<Bloom> BLOOMS = new java.util.ArrayList<>();

	/** A Bloom on this body (its id) for so long, with its healing field's radius; 0 ticks withers it now. */
	static void bloom(Entity body, int id, int ticks, float field) {
		for (Bloom b : BLOOMS) {
			if (b.id == id && b.withering < 0) {
				if (ticks <= 0) {
					b.withering = 0;
				}
				return;
			}
		}
		if (ticks > 0 && body != null) {
			Bloom b = new Bloom(body, id, ticks, field);
			BLOOMS.add(b);
			VoidRender.add(b);
		}
	}

	/** The giant mushroom: springs up out of the ground, sways, breathes out motes of light, withers when it ends. */
	static final class Bloom extends VoidRender.Effect {
		private static final int GROW = 30;
		private static final int WITHER = 16;
		final Entity body;
		final int id;
		final float field;
		int withering = -1;

		Bloom(Entity body, int id, int ticks, float field) {
			super(ticks + WITHER + 1);
			this.body = body;
			this.id = id;
			this.field = field;
			VoidRender.add(new Ring(body.position(), 0.5f, field, 24));
		}

		@Override
		boolean tick() {
			if (withering < 0 && (age >= ticks - WITHER - 1 || body.isRemoved())) {
				withering = 0;
			}
			if (withering >= 0 && ++withering > WITHER) {
				BLOOMS.remove(this);
				return false;
			}
			if (age > GROW && withering < 0 && age % 3 == 0) {
				double a = VoidRender.rand(0, Math.PI * 2);
				double r = VoidRender.rand(0.8, 3.2);
				moteAt(body.position().add(Math.cos(a) * r, VoidRender.rand(3.4, 4.0), Math.sin(a) * r),
						new Vec3(Math.cos(a) * 0.02, VoidRender.rand(0.02, 0.05), Math.sin(a) * 0.02), 40, VoidRender.rand(0.08, 0.15));
			}
			return super.tick();
		}

		/** How grown it is: springing up with a little overshoot, then sinking back as it withers. */
		float size(float partial) {
			float grow = Math.min(1, life(partial) / GROW);
			float s = grow < 1 ? VoidRender.easeOutBack(grow) : 1;
			if (withering >= 0) {
				s *= Math.max(0, 1 - (withering + partial) / WITHER);
			}
			return Math.max(0.001f, s);
		}

		@Override
		void draw(FxDraw draw, float partial) {
			Vec3 base = body.getPosition(partial);
			float s = size(partial);
			float life = life(partial);
			float fieldA = Math.min(1, life / 20f) * (withering < 0 ? 1 : s);
			decal(draw, FxDraw.FIELD, base.add(0, 0.05, 0), field, life * 0.004, fieldA * 0.6f);
			float time = life / 20f;
			Vec3 sway = new Vec3(Math.sin(time * 1.3) * 0.1, 0, Math.cos(time * 1.1) * 0.1);
			float breathe = 0.88f + 0.12f * (float) Math.sin(time * 1.6);
			mushroom(draw, base, s * 1.15f, sway, Math.min(1, s * 3), breathe);
		}
	}

	/**
	 * A low-poly mushroom wearing the painted textures: a ten-sided domed cap (30 quads), the gills under it (10) and
	 * a tapering stem (24). No bloom: the cap breathes by its own brightness.
	 */
	private static void mushroom(FxDraw draw, Vec3 base, float s, Vec3 sway, float alpha, float breathe) {
		int around = 10;
		int rings = 3;
		float capY = 3.6f;
		float capR = 3.0f;
		float capH = 1.8f;
		double sun = 0.8;
		double rimPhi = 1.85;
		Vec3[][] cap = new Vec3[rings + 1][around + 1];
		float[][] cu = new float[rings + 1][around + 1];
		float[][] cv = new float[rings + 1][around + 1];
		for (int j = 0; j <= rings; j++) {
			double phi = rimPhi * j / rings;
			double rad = capR * Math.sin(Math.min(phi, Math.PI / 2)) * (phi > Math.PI / 2 ? 1 - (phi - Math.PI / 2) * 0.35 : 1);
			double y = capY + capH * Math.cos(phi);
			// The painted cap is a view from above: each point takes the texture where it would be seen from there
			double texR = 0.5 * Math.min(1, phi / (Math.PI / 2)) * (phi > Math.PI / 2 ? 1 : 0.97);
			for (int i = 0; i <= around; i++) {
				double t = Math.PI * 2 * i / around;
				cap[j][i] = base.add(sway.scale(y / (capY + capH))).add(Math.cos(t) * rad * s, y * s, Math.sin(t) * rad * s);
				cu[j][i] = (float) (0.5 + Math.cos(t) * texR);
				cv[j][i] = (float) (0.5 + Math.sin(t) * texR);
			}
		}
		for (int j = 0; j < rings; j++) {
			double phi = rimPhi * (j + 0.5) / rings;
			for (int i = 0; i < around; i++) {
				double t = Math.PI * 2 * (i + 0.5) / around;
				float light = breathe * (float) Math.max(0.3, Math.min(1, 0.5 * Math.cos(Math.min(phi, 1.5)) + 0.45 + 0.25 * Math.cos(t - sun) * Math.sin(phi)));
				draw.paintStrip(FxDraw.CAP, new Vec3[] {cap[j][i], cap[j][i + 1], cap[j + 1][i + 1], cap[j + 1][i]},
						new float[] {cu[j][i], cu[j][i + 1], cu[j + 1][i + 1], cu[j + 1][i]}, new float[] {cv[j][i], cv[j][i + 1], cv[j + 1][i + 1], cv[j + 1][i]},
						light, light, light, alpha, 0);
			}
		}
		// The gills: the painted underside, from the stem out to the rim
		float stemTop = 0.42f;
		for (int i = 0; i < around; i++) {
			double t0 = Math.PI * 2 * i / around;
			double t1 = Math.PI * 2 * (i + 1) / around;
			Vec3 in0 = base.add(sway.scale(0.8)).add(Math.cos(t0) * stemTop * s, (capY + 0.35) * s, Math.sin(t0) * stemTop * s);
			Vec3 in1 = base.add(sway.scale(0.8)).add(Math.cos(t1) * stemTop * s, (capY + 0.35) * s, Math.sin(t1) * stemTop * s);
			float ri = 0.07f;
			draw.paintStrip(FxDraw.GILLS, new Vec3[] {in0, in1, cap[rings][i + 1], cap[rings][i]},
					new float[] {(float) (0.5 + Math.cos(t0) * ri), (float) (0.5 + Math.cos(t1) * ri), (float) (0.5 + Math.cos(t1) * 0.49), (float) (0.5 + Math.cos(t0) * 0.49)},
					new float[] {(float) (0.5 + Math.sin(t0) * ri), (float) (0.5 + Math.sin(t1) * ri), (float) (0.5 + Math.sin(t1) * 0.49), (float) (0.5 + Math.sin(t0) * 0.49)},
					0.75f, 0.75f, 0.75f, alpha, 0);
		}
		// The stem: a bulb at the foot, tapering up to the cap
		int segs = 8;
		int bands = 3;
		float stemH = capY + 0.4f;
		Vec3[][] stem = new Vec3[bands + 1][segs + 1];
		for (int j = 0; j <= bands; j++) {
			double y = stemH * j / bands;
			double rad = 0.62 * (1.15 - 0.35 * y / stemH) + 0.3 * Math.exp(-y * 2.5);
			for (int i = 0; i <= segs; i++) {
				double t = Math.PI * 2 * i / segs;
				stem[j][i] = base.add(sway.scale(y / (capY + capH))).add(Math.cos(t) * rad * s, y * s, Math.sin(t) * rad * s);
			}
		}
		for (int j = 0; j < bands; j++) {
			for (int i = 0; i < segs; i++) {
				double t = Math.PI * 2 * (i + 0.5) / segs;
				float light = (float) (0.6 + 0.4 * Math.cos(t - sun));
				float u0 = 2f * i / segs;
				float u1 = 2f * (i + 1) / segs;
				float v0 = 1 - j / (float) bands;
				float v1 = 1 - (j + 1) / (float) bands;
				draw.paintStrip(FxDraw.STEM, new Vec3[] {stem[j][i], stem[j][i + 1], stem[j + 1][i + 1], stem[j + 1][i]}, new float[] {u0, u1, u1, u0},
						new float[] {v0, v0, v1, v1}, light, light, light * 0.97f, alpha, 0);
			}
		}
	}

	/**
	 * The view from outside while the player is the mushroom: drawn back far enough to see the whole of it, and eased
	 * back in as it withers.
	 */
	static void onCameraDistance(net.neoforged.neoforge.client.event.CalculateDetachedCameraDistanceEvent event) {
		var player = Minecraft.getInstance().player;
		if (player == null) {
			return;
		}
		for (Bloom b : BLOOMS) {
			if (b.id == player.getId()) {
				event.setDistance(event.getDistance() + (11 - event.getDistance()) * Math.min(1, b.size(0)));
			}
		}
	}
}
