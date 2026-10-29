package euf.mageslegacy.client.fx;

import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;

/**
 * The Mycelid's effects, drawn with their own shaders (pinwheel fx/spore and fx/mushroom) rather than with crowds of
 * particles: a few quads each, so they cost next to nothing. The cloud of neurospores, the threads of the symbiosis,
 * the Spore Storm's spiral and wall, and the Great Bloom: a smooth glowing mushroom with its healing field.
 */
final class MycelidFx {
	/** The spore shader's shapes, in its blue channel. */
	static final float CLOUD = 0.07f;
	static final float RING = 0.22f;
	static final float THREAD = 0.37f;
	static final float AURA = 0.52f;
	static final float SPIRAL = 0.67f;
	static final float WALL = 0.82f;
	static final float FIELD = 0.95f;
	/** The mushroom shader's parts. */
	private static final float CAP = 0.15f;
	private static final float GILLS = 0.45f;
	private static final float STEM = 0.8f;
	private static final int[] MOTES = {0xFF8CE07A, 0xFF7FE8C8, 0xFFB0F5C8, 0xFFE8D27A};

	private MycelidFx() {
	}

	private static int mote() {
		return MOTES[VoidRender.RANDOM.nextInt(MOTES.length)];
	}

	private static float fade(float life, int ticks, float in, float out) {
		return Math.max(0, Math.min(1, life / in) * Math.min(1, (ticks - life) / out));
	}

	/** A flat quad lying on the ground, centred, with this half-size. */
	private static void ground(FxDraw draw, Vec3 at, float half, float r, float g, float shape, float a) {
		draw.quad(FxDraw.SPORE, at, new Vec3(half, 0, 0), new Vec3(0, 0, half), r, g, shape, a);
	}

	/** A camera-facing band from a to b, this wide; uv.x runs along it. */
	private static void band(FxDraw draw, Vec3 a, Vec3 b, float width, float r, float g, float shape, float alpha) {
		Vec3 along = b.subtract(a);
		Vec3 toCam = draw.cam.subtract(a.add(b).scale(0.5));
		Vec3 side = along.cross(toCam);
		if (side.lengthSqr() < 1.0E-6) {
			return;
		}
		side = side.normalize().scale(width / 2);
		draw.strip(FxDraw.SPORE, new Vec3[] {a.subtract(side), b.subtract(side), b.add(side), a.add(side)}, new float[] {0, 1, 1, 0},
				new float[] {0, 0, 1, 1}, r, g, shape, alpha);
	}

	// ---- Neurospores ------------------------------------------------------------------------------------------

	/** A cloud of neurospores on the ground (riding with its Mycelid if it follows one), and a few motes rising. */
	static final class Cloud extends VoidRender.Effect {
		Vec3 at;
		final Entity follow;
		final float radius;
		final float seed = VoidRender.RANDOM.nextFloat();

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
			for (int i = 0; i < 2; i++) {
				double a = VoidRender.rand(0, Math.PI * 2);
				double r = radius * Math.sqrt(VoidRender.rand(0, 1));
				VoidRender.add(new VoidRender.Particle(at.add(Math.cos(a) * r, VoidRender.rand(0.1, 1.2), Math.sin(a) * r),
						new Vec3(0, VoidRender.rand(0.01, 0.03), 0), 26, (float) VoidRender.rand(0.06, 0.12), mote(), true, 0.98f, 1f));
			}
			return super.tick();
		}

		@Override
		void draw(FxDraw draw, float partial) {
			Vec3 c = follow != null ? follow.getPosition(partial) : at;
			ground(draw, c.add(0, 0.06, 0), radius, 0, seed, CLOUD, fade(life(partial), ticks, 6, 12) * 0.9f);
		}
	}

	/** A ring of spores running out over the ground. */
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
			float half = Math.max(from, to) + 0.5f;
			float r = (from + (to - from) * VoidRender.easeOut(t)) / half;
			ground(draw, at, half, r, 0, RING, (float) Math.pow(1 - t, 1.3));
		}
	}

	// ---- Neurosymbiosis ---------------------------------------------------------------------------------------

	/** A living thread from the Mycelid to one sharing the symbiosis, pulses running along it, an aura under them. */
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
			float a = fade(life(partial), ticks, 5, 10);
			Vec3 to = ally.getPosition(partial);
			ground(draw, to.add(0, 0.05, 0), Math.max(0.7f, ally.getBbWidth()), 0, 0, AURA, a);
			if (caster != null && caster != ally && !caster.isRemoved()) {
				Vec3 from = caster.getPosition(partial).add(0, caster.getBbHeight() * 0.6, 0);
				band(draw, from, to.add(0, ally.getBbHeight() * 0.6, 0), 0.14f, 0, 0, THREAD, a);
			}
		}
	}

	// ---- Spore Storm ------------------------------------------------------------------------------------------

	/** The Spore Storm: a spiral of spores turning on the ground and a wall of turning haze round its edge. */
	static final class Storm extends VoidRender.Effect {
		final Entity heart;
		final float radius;
		final float seed = VoidRender.RANDOM.nextFloat();

		Storm(Entity heart, float radius, int ticks) {
			super(ticks);
			this.heart = heart;
			this.radius = radius;
			VoidRender.add(new Ring(heart.position(), 0.5f, radius, 16));
		}

		@Override
		boolean tick() {
			Vec3 c = heart.position();
			for (int i = 0; i < 6; i++) {
				double a = VoidRender.rand(0, Math.PI * 2);
				double r = radius * Math.sqrt(VoidRender.rand(0.05, 1));
				Vec3 around = new Vec3(-Math.sin(a), 0, Math.cos(a)).scale(VoidRender.rand(0.2, 0.4));
				VoidRender.Particle p = new VoidRender.Particle(c.add(Math.cos(a) * r, VoidRender.rand(0.2, 4), Math.sin(a) * r),
						around.add(0, 0.03, 0), 28, (float) VoidRender.rand(0.12, 0.26), mote(), true, 0.97f, 1.1f);
				p.attract = c.add(0, 2, 0);
				p.pull = 0.02f;
				VoidRender.add(p);
			}
			return super.tick();
		}

		@Override
		void draw(FxDraw draw, float partial) {
			float a = fade(life(partial), ticks, 10, 15);
			Vec3 c = heart.getPosition(partial);
			ground(draw, c.add(0, 0.07, 0), radius, 0, seed, SPIRAL, a);
			// The wall: a ring of upright quads round the edge, uv.x going round, uv.y up
			int n = 36;
			float rr = radius * 0.92f;
			float h = 6;
			for (int i = 0; i < n; i++) {
				double a0 = Math.PI * 2 * i / n;
				double a1 = Math.PI * 2 * (i + 1) / n;
				Vec3 p0 = c.add(Math.cos(a0) * rr, 0, Math.sin(a0) * rr);
				Vec3 p1 = c.add(Math.cos(a1) * rr, 0, Math.sin(a1) * rr);
				draw.strip(FxDraw.SPORE, new Vec3[] {p0, p1, p1.add(0, h, 0), p0.add(0, h, 0)},
						new float[] {i / (float) n, (i + 1) / (float) n, (i + 1) / (float) n, i / (float) n}, new float[] {0, 0, 1, 1}, 0, seed, WALL, a * 0.8f);
			}
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

	/** The giant mushroom: grows out of the ground, sways, breathes out motes of light; withers when it ends. */
	static final class Bloom extends VoidRender.Effect {
		private static final int GROW = 30;
		private static final int WITHER = 16;
		final Entity body;
		final int id;
		final float field;
		final float seed = VoidRender.RANDOM.nextFloat();
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
			if (age > GROW && withering < 0) {
				double a = VoidRender.rand(0, Math.PI * 2);
				double r = VoidRender.rand(0.8, 3.5);
				VoidRender.add(new VoidRender.Particle(body.position().add(Math.cos(a) * r, VoidRender.rand(3.4, 4.2), Math.sin(a) * r),
						new Vec3(Math.cos(a) * 0.02, VoidRender.rand(0.02, 0.05), Math.sin(a) * 0.02), 50, (float) VoidRender.rand(0.08, 0.16), mote(), true,
						0.99f, 1.1f));
			}
			return super.tick();
		}

		/** How grown it is: springing up with a little overshoot, then sinking back as it withers. */
		float size(float partial) {
			float life = life(partial);
			float grow = Math.min(1, life / GROW);
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
			float a = Math.min(1, s * 3);
			ground(draw, base.add(0, 0.05, 0), field, 0, seed, FIELD, Math.min(1, life(partial) / 20f) * (withering < 0 ? 1 : s) * 0.9f);
			float time = life(partial) / 20f;
			Vec3 sway = new Vec3(Math.sin(time * 1.3) * 0.12, 0, Math.cos(time * 1.1) * 0.12);
			mushroom(draw, base, s * 1.15f, sway, a, seed);
		}
	}

	/** The mushroom's mesh: a domed cap, gills under it, a tapering stem, threads hanging from the rim. */
	private static void mushroom(FxDraw draw, Vec3 base, float s, Vec3 sway, float alpha, float seed) {
		int around = 28;
		float capY = 3.6f;
		float capR = 3.0f;
		float capH = 1.8f;
		double sun = 0.8;
		// The cap: from the crown (phi 0) to a rim curling a little under (phi ~ 1.85)
		int rings = 8;
		Vec3[][] cap = new Vec3[rings + 1][around + 1];
		for (int j = 0; j <= rings; j++) {
			double phi = 1.85 * j / rings;
			double rad = capR * Math.sin(Math.min(phi, Math.PI / 2)) * (phi > Math.PI / 2 ? 1 - (phi - Math.PI / 2) * 0.35 : 1);
			double y = capY + capH * Math.cos(phi);
			for (int i = 0; i <= around; i++) {
				double t = Math.PI * 2 * i / around;
				// A cap is never a perfect dome: a gentle lumpiness round the rim
				double lump = 1 + 0.05 * Math.sin(t * 5 + seed * 10) * j / rings;
				double lean = (y - 0.0) / (capY + capH);
				cap[j][i] = base.add(sway.scale(lean)).add(Math.cos(t) * rad * lump * s, y * s, Math.sin(t) * rad * lump * s);
			}
		}
		for (int j = 0; j < rings; j++) {
			for (int i = 0; i < around; i++) {
				double phi = 1.85 * (j + 0.5) / rings;
				double t = Math.PI * 2 * (i + 0.5) / around;
				float light = (float) Math.max(0.15, Math.min(1, 0.55 * Math.cos(Math.min(phi, 1.5)) + 0.35 + 0.25 * Math.cos(t - sun) * Math.sin(phi)));
				float u0 = i / (float) around;
				float u1 = (i + 1) / (float) around;
				float v0 = j / (float) rings;
				float v1 = (j + 1) / (float) rings;
				draw.strip(FxDraw.MUSHROOM, new Vec3[] {cap[j][i], cap[j][i + 1], cap[j + 1][i + 1], cap[j + 1][i]}, new float[] {u0, u1, u1, u0},
						new float[] {v0, v0, v1, v1}, light, seed, CAP, alpha);
			}
		}
		// The gills: from the rim in to the stem, just under the cap
		float stemTop = 0.42f;
		for (int i = 0; i < around; i++) {
			double t0 = Math.PI * 2 * i / around;
			double t1 = Math.PI * 2 * (i + 1) / around;
			Vec3 rim0 = cap[rings][i];
			Vec3 rim1 = cap[rings][i + 1];
			Vec3 in0 = base.add(sway.scale(0.8)).add(Math.cos(t0) * stemTop * s, (capY + 0.35) * s, Math.sin(t0) * stemTop * s);
			Vec3 in1 = base.add(sway.scale(0.8)).add(Math.cos(t1) * stemTop * s, (capY + 0.35) * s, Math.sin(t1) * stemTop * s);
			float u0 = i / (float) around;
			float u1 = (i + 1) / (float) around;
			draw.strip(FxDraw.MUSHROOM, new Vec3[] {in0, in1, rim1, rim0}, new float[] {u0, u1, u1, u0}, new float[] {0, 0, 1, 1}, 0.5f, seed, GILLS, alpha);
		}
		// The stem: a bulb at the foot, tapering up to the cap
		int segs = 20;
		int bands = 6;
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
				float light = (float) (0.55 + 0.4 * Math.cos(t - sun));
				float u0 = i / (float) segs;
				float u1 = (i + 1) / (float) segs;
				// uv.y 0 at the top of the stem, 1 at its foot
				float v0 = 1 - j / (float) bands;
				float v1 = 1 - (j + 1) / (float) bands;
				draw.strip(FxDraw.MUSHROOM, new Vec3[] {stem[j][i], stem[j][i + 1], stem[j + 1][i + 1], stem[j + 1][i]}, new float[] {u0, u1, u1, u0},
						new float[] {v0, v0, v1, v1}, light, seed, STEM, alpha);
			}
		}
		// Threads of light hanging from the rim
		for (int k = 0; k < 10; k++) {
			Vec3 top = cap[rings][(k * around / 10 + 1) % around];
			float len = (0.9f + 0.6f * ((k * 37) % 5) / 5f) * s;
			band(draw, top, top.add(0, -len, 0), 0.07f * s, 0, 0, THREAD, alpha * 0.9f);
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
				float k = b.size(0);
				event.setDistance(event.getDistance() + (11 - event.getDistance()) * Math.min(1, k));
			}
		}
	}
}
