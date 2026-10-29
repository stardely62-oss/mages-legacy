package euf.mageslegacy.client.fx;

import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;

/**
 * The Mycelid's effects, as the big spell mods draw theirs: painted textures (textures/fx/myc_*) on a few quads, turned,
 * scaled and faded over time, and a handful of short-lived motes. Each costs a texture read per pixel, nothing
 * computed: the cloud of neurospores, the threads and auras of the symbiosis, the Spore Storm's turning vortex, and
 * the Great Bloom, a smooth mushroom wearing painted cap, gills and stem, with its healing field.
 */
final class MycelidFx {
	private static final int[] MOTES = {0xFF8CE07A, 0xFF7FE8C8, 0xFFB0F5C8, 0xFFE8D27A};

	private MycelidFx() {
	}

	private static int mote() {
		return MOTES[VoidRender.RANDOM.nextInt(MOTES.length)];
	}

	private static float fade(float life, int ticks, float in, float out) {
		return Math.max(0, Math.min(1, life / in) * Math.min(1, (ticks - life) / out));
	}

	/** A painted decal lying on the ground: centred, this half-size, turned by an angle (radians). */
	private static void decal(FxDraw draw, String tex, Vec3 at, float half, double angle, float a, float glow) {
		Vec3 u = new Vec3(Math.cos(angle) * half, 0, Math.sin(angle) * half);
		Vec3 v = new Vec3(-Math.sin(angle) * half, 0, Math.cos(angle) * half);
		draw.paint(tex, at, u, v, 1, 1, 1, a, glow);
	}

	/** A camera-facing band of a painted texture from a to b, this wide; the texture runs along it. */
	private static void band(FxDraw draw, String tex, Vec3 a, Vec3 b, float width, float alpha, float glow) {
		Vec3 along = b.subtract(a);
		Vec3 side = along.cross(draw.cam.subtract(a.add(b).scale(0.5)));
		if (side.lengthSqr() < 1.0E-6) {
			return;
		}
		side = side.normalize().scale(width / 2);
		draw.paintStrip(tex, new Vec3[] {a.subtract(side), b.subtract(side), b.add(side), a.add(side)}, new float[] {0, 1, 1, 0}, new float[] {0, 0, 1, 1},
				1, 1, 1, alpha, glow);
	}

	private static void moteAt(Vec3 at, Vec3 vel, int life, double size) {
		VoidRender.add(new VoidRender.Particle(at, vel, life, (float) size, mote(), true, 0.98f, 1f));
	}

	// ---- Neurospores ------------------------------------------------------------------------------------------

	/** A cloud of neurospores on the ground: two painted layers turning against each other, breathing; a few motes. */
	static final class Cloud extends VoidRender.Effect {
		Vec3 at;
		final Entity follow;
		final float radius;
		final double spin = VoidRender.rand(0, Math.PI * 2);

		Cloud(Vec3 at, Entity follow, float radius, int ticks) {
			super(ticks);
			this.at = at;
			this.follow = follow;
			this.radius = radius;
			VoidRender.add(new Ring(at, 0.3f, radius, 14));
		}

		@Override
		boolean tick() {
			if (follow != null) {
				at = follow.position();
			}
			if (age % 2 == 0) {
				double a = VoidRender.rand(0, Math.PI * 2);
				double r = radius * Math.sqrt(VoidRender.rand(0, 1));
				moteAt(at.add(Math.cos(a) * r, VoidRender.rand(0.1, 1.0), Math.sin(a) * r), new Vec3(0, VoidRender.rand(0.01, 0.03), 0), 24,
						VoidRender.rand(0.07, 0.13));
			}
			return super.tick();
		}

		@Override
		void draw(FxDraw draw, float partial) {
			float life = life(partial);
			float a = fade(life, ticks, 6, 12);
			Vec3 c = (follow != null ? follow.getPosition(partial) : at).add(0, 0.06, 0);
			float breathe = 1 + 0.04f * (float) Math.sin(life * 0.15);
			decal(draw, FxDraw.CLOUD, c, radius * breathe, spin + life * 0.012, a * 0.5f, 0.06f);
			decal(draw, FxDraw.CLOUD, c.add(0, 0.02, 0), radius * 0.75f / breathe, spin * 2 - life * 0.02, a * 0.35f, 0.04f);
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
			decal(draw, FxDraw.FIELD, at, r, 0, (float) Math.pow(1 - t, 1.3) * 0.8f, 0.35f);
		}
	}

	// ---- Neurosymbiosis ---------------------------------------------------------------------------------------

	/** A glowing thread from the Mycelid to one sharing the symbiosis, its light pulsing; a turning aura under them. */
	static final class Bond extends VoidRender.Effect {
		final Entity caster;
		final Entity ally;

		Bond(Entity caster, Entity ally, int ticks) {
			super(ticks);
			this.caster = caster;
			this.ally = ally;
		}

		@Override
		boolean tick() {
			return !ally.isRemoved() && super.tick();
		}

		@Override
		void draw(FxDraw draw, float partial) {
			float life = life(partial);
			float a = fade(life, ticks, 5, 10);
			Vec3 to = ally.getPosition(partial);
			decal(draw, FxDraw.AURA, to.add(0, 0.05, 0), Math.max(0.8f, ally.getBbWidth() * 1.3f), life * 0.05, a * 0.85f, 0.3f);
			if (caster != null && caster != ally && !caster.isRemoved()) {
				Vec3 from = caster.getPosition(partial).add(0, caster.getBbHeight() * 0.6, 0);
				float pulse = 0.75f + 0.25f * (float) Math.sin(life * 0.4);
				band(draw, FxDraw.THREAD, from, to.add(0, ally.getBbHeight() * 0.6, 0), 0.35f, a * pulse, 0.4f);
			}
		}
	}

	// ---- Spore Storm ------------------------------------------------------------------------------------------

	/** The Spore Storm: painted vortices turning on the ground and above it, each layer smaller, faster and fainter. */
	static final class Storm extends VoidRender.Effect {
		final Entity heart;
		final float radius;

		Storm(Entity heart, float radius, int ticks) {
			super(ticks);
			this.heart = heart;
			this.radius = radius;
			VoidRender.add(new Ring(heart.position(), 0.5f, radius, 16));
		}

		@Override
		boolean tick() {
			Vec3 c = heart.position();
			for (int i = 0; i < 3; i++) {
				double a = VoidRender.rand(0, Math.PI * 2);
				double r = radius * Math.sqrt(VoidRender.rand(0.05, 1));
				Vec3 around = new Vec3(-Math.sin(a), 0, Math.cos(a)).scale(VoidRender.rand(0.2, 0.4));
				VoidRender.Particle p = new VoidRender.Particle(c.add(Math.cos(a) * r, VoidRender.rand(0.2, 3.5), Math.sin(a) * r), around.add(0, 0.03, 0), 26,
						(float) VoidRender.rand(0.12, 0.24), mote(), true, 0.97f, 1.1f);
				p.attract = c.add(0, 2, 0);
				p.pull = 0.02f;
				VoidRender.add(p);
			}
			return super.tick();
		}

		@Override
		void draw(FxDraw draw, float partial) {
			float life = life(partial);
			float a = fade(life, ticks, 10, 15);
			Vec3 c = heart.getPosition(partial);
			decal(draw, FxDraw.SPIRAL, c.add(0, 0.07, 0), radius, -life * 0.06, a * 0.55f, 0.08f);
			decal(draw, FxDraw.SPIRAL, c.add(0, 1.6, 0), radius * 0.7f, -life * 0.09 + 1.0, a * 0.3f, 0.05f);
			decal(draw, FxDraw.SPIRAL, c.add(0, 3.4, 0), radius * 0.45f, -life * 0.13 + 2.0, a * 0.2f, 0.04f);
			decal(draw, FxDraw.CLOUD, c.add(0, 0.09, 0), radius * 0.55f, life * 0.03, a * 0.3f, 0.03f);
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
			if (age > GROW && withering < 0 && age % 2 == 0) {
				double a = VoidRender.rand(0, Math.PI * 2);
				double r = VoidRender.rand(0.8, 3.2);
				moteAt(body.position().add(Math.cos(a) * r, VoidRender.rand(3.4, 4.0), Math.sin(a) * r),
						new Vec3(Math.cos(a) * 0.02, VoidRender.rand(0.02, 0.05), Math.sin(a) * 0.02), 50, VoidRender.rand(0.08, 0.15));
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
			decal(draw, FxDraw.FIELD, base.add(0, 0.05, 0), field, life * 0.004, fieldA * 0.7f, 0.25f);
			decal(draw, FxDraw.CLOUD, base.add(0, 0.06, 0), 4.5f * s, -life * 0.01, fieldA * 0.3f, 0.04f);
			float time = life / 20f;
			Vec3 sway = new Vec3(Math.sin(time * 1.3) * 0.1, 0, Math.cos(time * 1.1) * 0.1);
			// The spots breathe: the glow of the cap rises and falls
			float breathe = 0.75f + 0.25f * (float) Math.sin(time * 1.6);
			mushroom(draw, base, s * 1.15f, sway, Math.min(1, s * 3), breathe);
		}
	}

	/** The mushroom's mesh, wearing painted textures: a domed cap seen from above, gills from below, a fibrous stem. */
	private static void mushroom(FxDraw draw, Vec3 base, float s, Vec3 sway, float alpha, float breathe) {
		int around = 24;
		float capY = 3.6f;
		float capR = 3.0f;
		float capH = 1.8f;
		double sun = 0.8;
		int rings = 6;
		double rimPhi = 1.85;
		Vec3[][] cap = new Vec3[rings + 1][around + 1];
		float[][] cu = new float[rings + 1][around + 1];
		float[][] cv = new float[rings + 1][around + 1];
		for (int j = 0; j <= rings; j++) {
			double phi = rimPhi * j / rings;
			double rad = capR * Math.sin(Math.min(phi, Math.PI / 2)) * (phi > Math.PI / 2 ? 1 - (phi - Math.PI / 2) * 0.35 : 1);
			double y = capY + capH * Math.cos(phi);
			// The painted cap is a view from above: each point takes the texture where it would be seen from there, the
			// rim (curling under) at the very edge of the painting
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
				float light = (float) Math.max(0.3, Math.min(1, 0.5 * Math.cos(Math.min(phi, 1.5)) + 0.45 + 0.25 * Math.cos(t - sun) * Math.sin(phi)));
				draw.paintStrip(FxDraw.CAP, new Vec3[] {cap[j][i], cap[j][i + 1], cap[j + 1][i + 1], cap[j + 1][i]},
						new float[] {cu[j][i], cu[j][i + 1], cu[j + 1][i + 1], cu[j + 1][i]}, new float[] {cv[j][i], cv[j][i + 1], cv[j + 1][i + 1], cv[j + 1][i]},
						light, light, light, alpha, breathe * alpha);
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
					0.75f, 0.75f, 0.75f, alpha, breathe * alpha);
		}
		// The stem: a bulb at the foot, tapering up to the cap; its fibres wrapped round twice
		int segs = 16;
		int bands = 5;
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
						new float[] {v0, v0, v1, v1}, light, light, light * 0.97f, alpha, 0.5f * alpha);
			}
		}
		// Threads of light hanging from the rim
		for (int k = 0; k < 8; k++) {
			Vec3 top = cap[rings][(k * around / 8 + 1) % around];
			float len = (0.9f + 0.6f * ((k * 37) % 5) / 5f) * s;
			band(draw, FxDraw.THREAD, top.add(0, 0.1, 0), top.add(0, -len, 0), 0.25f * s, alpha * 0.9f, 0.4f);
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
