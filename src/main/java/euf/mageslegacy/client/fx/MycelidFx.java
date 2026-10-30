package euf.mageslegacy.client.fx;

import euf.mageslegacy.mycelid.MyceliumNodeBlock;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * The Mycelid's effects, built to be seen from every side and to cost almost nothing: a handful of painted quads
 * where shapes are needed (spiral strands, a blocky body, the node block itself grown huge), pixel spores with drift and swirl for everything airborne, no bloom on anything large, and a
 * hard cap on motes. The spores, the storm and the waking are pixel spores only (one quad each); a bond ~20 quads, a body ~40, the bloom ~16.
 */
final class MycelidFx {
	private static final int[] MOTES = {0xFF8CE07A, 0xFF7FE8C8, 0xFFB0F5C8, 0xFFE8D27A};
	/** Neurospores (and the network's own: bodies, the waking): cool moss, mint and teal, a few darker. */
	private static final int[] SPORES = {0xD8A6E07A, 0xD87FD8B8, 0xD8B8F0A0, 0xD860C8A8, 0xC86FA05A};
	/** The Spore Storm: warm dust of amber, rust, ochre and brown, so it never reads as the neurospores' cloud. */
	private static final int[] STORM_SPORES = {0xE0E8A040, 0xE0C8703A, 0xD8F0C860, 0xD0A0603A, 0xE0E0B070};
	/** No Mycelid effect adds motes or spores while this many particles are already alive. */
	private static final int MOTE_CAP = 900;

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

	/** A pixel spore: one small quad, no glow. */
	private static VoidRender.Particle spore(Vec3 at, Vec3 vel, int life, double size, float drag) {
		return spore(SPORES, at, vel, life, size, drag);
	}

	private static VoidRender.Particle spore(int[] palette, Vec3 at, Vec3 vel, int life, double size, float drag) {
		VoidRender.Particle p = new VoidRender.Particle(at, vel, life, (float) size, palette[VoidRender.RANDOM.nextInt(palette.length)], false, drag, 0);
		p.tex = FxDraw.SPORE;
		return p;
	}

	private static void add(VoidRender.Particle p) {
		if (VoidRender.PARTICLES.size() < MOTE_CAP) {
			VoidRender.add(p);
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

	/** The badges asked for this frame, per creature, each with its strongest alpha; drawn together in drawBadges. */
	private static final java.util.Map<Entity, java.util.TreeMap<String, Float>> BADGES = new java.util.LinkedHashMap<>();

	/** Asks for a status badge over a creature's head this frame (two spells on one creature: two badges side by side). */
	private static void icon(Entity e, String tex, float alpha) {
		BADGES.computeIfAbsent(e, k -> new java.util.TreeMap<>()).merge(tex, alpha, Math::max);
	}

	/**
	 * Draws the frame's badges: pixel icons like the game's own effect icons, floating over the heads and facing the
	 * camera, several on one creature in a row.
	 */
	static void drawBadges(FxDraw draw, float partial) {
		float half = 0.25f;
		float step = 0.58f;
		for (var entry : BADGES.entrySet()) {
			Entity e = entry.getKey();
			Vec3 at = e.getPosition(partial).add(0, e.getBbHeight() + 0.55 + 0.05 * Math.sin((e.tickCount + partial) * 0.15), 0);
			Vec3 toCam = draw.cam.subtract(at);
			Vec3 right = new Vec3(0, 1, 0).cross(toCam);
			if (right.lengthSqr() < 1.0E-6) {
				continue;
			}
			right = right.normalize();
			Vec3 down = toCam.cross(right).normalize().scale(-1);
			int n = entry.getValue().size();
			int i = 0;
			for (var badge : entry.getValue().entrySet()) {
				Vec3 c = at.add(right.scale((i++ - (n - 1) / 2f) * step));
				draw.paint(badge.getKey(), c, right.scale(half), down.scale(half), 1, 1, 1, badge.getValue(), 0);
			}
		}
		BADGES.clear();
	}

	/** The enemies within this reach of a point (at most 12). */
	private static java.util.List<LivingEntity> enemiesNear(Vec3 c, float radius) {
		var level = Minecraft.getInstance().level;
		if (level == null) {
			return java.util.List.of();
		}
		return level.getEntitiesOfClass(LivingEntity.class, new AABB(c, c).inflate(radius, 4, radius),
				x -> x instanceof Enemy && x.isAlive() && x.position().distanceToSqr(c) <= radius * radius).stream().limit(12).toList();
	}

	/** The players and this player's own pets within this reach of a point, the player themself left out (at most 12). */
	private static java.util.List<LivingEntity> alliesNear(Entity heart, Vec3 c, float radius) {
		var level = Minecraft.getInstance().level;
		if (level == null) {
			return java.util.List.of();
		}
		return level.getEntitiesOfClass(LivingEntity.class, new AABB(c, c).inflate(radius, 4, radius),
				x -> x != heart && x.isAlive() && x.position().distanceToSqr(c) <= radius * radius
						&& (x instanceof net.minecraft.world.entity.player.Player p && !p.isSpectator()
								|| x instanceof net.minecraft.world.entity.OwnableEntity o && heart.getUUID().equals(o.getOwnerUUID())))
				.stream().limit(12).toList();
	}

	private static boolean firstPersonOn(Entity e) {
		Minecraft mc = Minecraft.getInstance();
		return e == mc.player && mc.options.getCameraType().isFirstPerson();
	}

	// ---- Neurospores ------------------------------------------------------------------------------------------

	/**
	 * A cloud of neurospores, all of it pixel spores and no pictures: a puff thrown out from the Mycelid fills the
	 * volume, then the cloud lives on its own (spores drifting, churning slowly round its centre and rising), a rim of
	 * spores creeping along the ground marks its reach, and spores circle the head of every enemy inside like stars
	 * after a blow, which is what they do to them. About 150 spores, one small quad each.
	 */
	static final class Cloud extends VoidRender.Effect {
		java.util.List<LivingEntity> enemies = java.util.List.of();
		Vec3 at;
		final Entity follow;
		final float radius;

		Cloud(Vec3 at, Entity follow, float radius, int ticks) {
			super(ticks);
			this.at = at;
			this.follow = follow;
			this.radius = radius;
			// The puff: spores thrown out every way from the chest, slowing as they spread through the volume
			Vec3 chest = at.add(0, 1.0, 0);
			for (int i = 0; i < 80; i++) {
				Vec3 dir = new Vec3(VoidRender.rand(-1, 1), VoidRender.rand(-0.3, 0.8), VoidRender.rand(-1, 1)).normalize();
				VoidRender.Particle p = spore(chest, dir.scale(radius * VoidRender.rand(0.05, 0.11)), (int) VoidRender.rand(30, 50), VoidRender.rand(0.06, 0.12), 0.88f);
				p.jitter = 0.004f;
				p.gravity = new Vec3(0, 0.0008, 0);
				add(p);
			}
		}

		@Override
		boolean tick() {
			if (follow != null) {
				at = follow.position();
			}
			// The body of the cloud: spores born all through the volume, low and wide, thinning upwards
			for (int i = 0; i < 3; i++) {
				double a = VoidRender.rand(0, Math.PI * 2);
				double d = Math.sqrt(VoidRender.rand(0, 1));
				double h = VoidRender.rand(0.1, 2.4) * (1 - d * d * 0.7);
				VoidRender.Particle p = spore(at.add(Math.cos(a) * d * radius, h, Math.sin(a) * d * radius), Vec3.ZERO, (int) VoidRender.rand(30, 50),
						VoidRender.rand(0.05, 0.11), 0.92f);
				p.jitter = 0.006f;
				p.swirlAt = at;
				p.swirl = 0.004f;
				p.gravity = new Vec3(0, 0.0006, 0);
				add(p);
			}
			// The rim: spores creeping along the ground at the edge of the reach
			double e = VoidRender.rand(0, Math.PI * 2);
			VoidRender.Particle rim = spore(at.add(Math.cos(e) * radius, VoidRender.rand(0.05, 0.3), Math.sin(e) * radius), new Vec3(0, 0.01, 0), 30,
					VoidRender.rand(0.06, 0.1), 0.95f);
			rim.swirlAt = at;
			rim.swirl = 0.003f;
			add(rim);
			if (age % 5 == 0) {
				enemies = enemiesNear(at, radius);
			}
			return super.tick();
		}

		@Override
		void draw(FxDraw draw, float partial) {
			float life = life(partial);
			float a = fade(life, ticks, 4, 8);
			for (LivingEntity e : enemies) {
				icon(e, FxDraw.ICON_CONFUSED, a);
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
	 * The Spore Storm: a wave of spores bursting out from the Mycelid every 0.7 s (the first one bigger), a weakening
	 * badge over every enemy in its reach and a quickening badge over every ally. Pixel spores only.
	 */
	static final class Storm extends VoidRender.Effect {
		private static final int PULSE = 14;
		final Entity heart;
		final float radius;
		java.util.List<LivingEntity> enemies = java.util.List.of();
		java.util.List<LivingEntity> allies = java.util.List.of();

		Storm(Entity heart, float radius, int ticks) {
			super(ticks);
			this.heart = heart;
			this.radius = radius;
			// about 15 blocks out for the first, 8 to 12 for the rest (drag 0.94 over ~30 ticks carries a spore ~13x its speed)
			wave(160, 0.8, 1.15);
		}

		/** Spores thrown out from the chest every way along the ground, this many, this fast, in the storm's warm dust. */
		private void wave(int count, double min, double max) {
			Vec3 chest = heart.position().add(0, 1.0, 0);
			for (int i = 0; i < count; i++) {
				double a = VoidRender.rand(0, Math.PI * 2);
				double v = VoidRender.rand(min, max);
				add(spore(STORM_SPORES, chest.add(0, VoidRender.rand(-0.6, 0.4), 0), new Vec3(Math.cos(a) * v, VoidRender.rand(-0.01, 0.04), Math.sin(a) * v),
						(int) VoidRender.rand(26, 34), VoidRender.rand(0.08, 0.14), 0.94f));
			}
		}

		@Override
		boolean tick() {
			if (age > 0 && age % PULSE == 0 && age < ticks - 6) {
				wave(80, 0.6, 0.9);
			}
			if (age % 5 == 0) {
				Vec3 c = heart.position();
				enemies = enemiesNear(c, radius);
				allies = alliesNear(heart, c, radius);
			}
			return !heart.isRemoved() && super.tick();
		}

		@Override
		void draw(FxDraw draw, float partial) {
			float life = life(partial);
			float a = fade(life, ticks, 4, 8);
			for (LivingEntity e : enemies) {
				icon(e, FxDraw.ICON_WEAKENED, a);
			}
			for (LivingEntity e : allies) {
				icon(e, FxDraw.ICON_QUICKENED, a);
			}
		}
	}

	// ---- The reserve body -------------------------------------------------------------------------------------

	/**
	 * A reserve body growing beside its node for the length of the growth: a blocky figure in the node's own pixel stem that forms from the feet
	 * up in the first seconds, then thickens and brightens while spores stream into it from the node, and lights up as
	 * it ripens. Six boxes (about 36 quads).
	 */
	static final class Body extends VoidRender.Effect {
		private static final int FORM = 100;
		final Vec3 at;
		final Vec3 node;
		final double yaw;

		Body(Vec3 at, Vec3 node, int ticks) {
			super(ticks);
			this.at = at;
			this.node = node;
			this.yaw = Math.atan2(node.z - at.z, node.x - at.x) - Math.PI / 2;
			burst(at, 2, 16);
		}

		@Override
		boolean tick() {
			if (age % 3 == 0) {
				// Spores flowing from the node into the body, and rising off the ground round it
				Vec3 from = node.add(VoidRender.rand(-0.2, 0.2), VoidRender.rand(0.3, 0.8), VoidRender.rand(-0.2, 0.2));
				Vec3 to = at.add(0, 1, 0);
				VoidRender.Particle p = new VoidRender.Particle(from, to.subtract(from).normalize().scale(0.08), 30, 0.1f, mote(), true, 0.97f, 0.5f);
				p.attract = to;
				p.pull = 0.04f;
				if (VoidRender.PARTICLES.size() < MOTE_CAP) {
					VoidRender.add(p);
				}
				double a = VoidRender.rand(0, Math.PI * 2);
				moteAt(at.add(Math.cos(a) * 0.8, 0.05, Math.sin(a) * 0.8), new Vec3(0, VoidRender.rand(0.03, 0.06), 0), 20, VoidRender.rand(0.07, 0.12));
			}
			if (age == ticks - 1) {
				// Ripe: a flash of spores and a wave on the ground
				burst(at, 3, 30);
				VoidRender.add(new Ring(at, 0.3f, 3, 14));
			}
			return super.tick();
		}

		private static float grown(float form, double from, double to) {
			return VoidRender.easeOut((float) Math.max(0, Math.min(1, (form - from) / (to - from))));
		}

		@Override
		void draw(FxDraw draw, float partial) {
			float life = life(partial);
			float form = Math.min(1, life / FORM);
			float solid = life / ticks;
			float ripe = Math.max(0, (life - (ticks - 20)) / 20f);
			float a = Math.min(1, life / 8f) * Math.min(1, (ticks - life) / 6f) * (0.75f + 0.25f * solid);
			float pulse = 0.85f + 0.15f * (float) Math.sin(life * 0.2) + 0.4f * ripe;
			decal(draw, FxDraw.AURA, at.add(0, 0.05, 0), 1.3f, life * 0.03, a * 0.7f);
			// legs, torso, arms, head: each from its own part of the forming
			box(draw, 0.13, 0, 0.25, 0.7, 0.25, grown(form, 0, 0.3), pulse, a);
			box(draw, -0.13, 0, 0.25, 0.7, 0.25, grown(form, 0, 0.3), pulse, a);
			box(draw, 0, 0.7, 0.5, 0.7, 0.25, grown(form, 0.25, 0.6), pulse, a);
			box(draw, 0.35, 0.7, 0.2, 0.7, 0.2, grown(form, 0.5, 0.8), pulse, a);
			box(draw, -0.35, 0.7, 0.2, 0.7, 0.2, grown(form, 0.5, 0.8), pulse, a);
			box(draw, 0, 1.4, 0.45, 0.45, 0.45, grown(form, 0.75, 1), pulse, a);
			// The thread from the node, pulsing as it feeds
			Vec3 chest = at.add(0, 1.05, 0);
			band(draw, FxDraw.THREAD, node.add(0, 0.4, 0), chest, 0.3f, a * (0.6f + 0.4f * (float) Math.sin(life * 0.35)) * Math.min(1, form * 2));
		}

		/** A box of the node's stem standing on the body's feet at this offset: grown from its base by g. */
		private void box(FxDraw draw, double ox, double oy, double w, double h, double d, float g, float light, float alpha) {
			if (g <= 0.001f) {
				return;
			}
			w *= g;
			h *= g;
			d *= g;
			double cos = Math.cos(yaw);
			double sin = Math.sin(yaw);
			Vec3[] bottom = new Vec3[4];
			Vec3[] top = new Vec3[4];
			double[][] corner = {{-1, -1}, {1, -1}, {1, 1}, {-1, 1}};
			for (int i = 0; i < 4; i++) {
				double x = ox + corner[i][0] * w / 2;
				double z = corner[i][1] * d / 2;
				Vec3 foot = at.add(x * cos - z * sin, oy, x * sin + z * cos);
				bottom[i] = foot;
				top[i] = foot.add(0, h, 0);
			}
			float[] shade = {0.9f, 0.65f, 0.8f, 0.6f};
			for (int i = 0; i < 4; i++) {
				int n = (i + 1) % 4;
				float l = light * shade[i];
				draw.paintStrip(FxDraw.NODE_STEM, new Vec3[] {bottom[i], bottom[n], top[n], top[i]}, new float[] {0, 1, 1, 0}, new float[] {1, 1, 0, 0}, 0.7f * l, l, 0.75f * l,
						alpha, 0);
			}
			draw.paintStrip(FxDraw.NODE_STEM, top, new float[] {0, 1, 1, 0}, new float[] {0, 0, 1, 1}, 0.7f * light, light, 0.75f * light, alpha, 0);
		}
	}

	// ---- Mycelial Rebirth: the waking ------------------------------------------------------------------------

	/**
	 * Where a Mycelid dies, the body comes apart into spores that drift up and away (out); where the reserve body
	 * wakes, spores gather in from every side to one point and burst out over the ground as it stands up.
	 */
	static final class Wake extends VoidRender.Effect {
		private static final int GATHER = 14;
		final Vec3 at;
		final boolean out;

		Wake(Vec3 at, boolean out) {
			super(out ? 2 : GATHER + 2);
			this.at = at;
			this.out = out;
			if (out) {
				// The body coming apart: spores all through its shape, scattering and rising
				for (int i = 0; i < 90; i++) {
					Vec3 from = at.add(VoidRender.rand(-0.35, 0.35), VoidRender.rand(0, 1.8), VoidRender.rand(-0.35, 0.35));
					Vec3 vel = new Vec3(VoidRender.rand(-0.08, 0.08), VoidRender.rand(0.02, 0.1), VoidRender.rand(-0.08, 0.08));
					VoidRender.Particle p = spore(from, vel, (int) VoidRender.rand(25, 45), VoidRender.rand(0.07, 0.13), 0.94f);
					p.jitter = 0.006f;
					p.gravity = new Vec3(0, 0.002, 0);
					add(p);
				}
				return;
			}
			// Spores from a shell round the waking place, all drawn in to it
			Vec3 heart = at.add(0, 1, 0);
			for (int i = 0; i < 110; i++) {
				Vec3 dir = new Vec3(VoidRender.rand(-1, 1), VoidRender.rand(-0.4, 1), VoidRender.rand(-1, 1)).normalize();
				Vec3 from = heart.add(dir.scale(VoidRender.rand(2.5, 4)));
				VoidRender.Particle p = spore(from, Vec3.ZERO, GATHER + 4, VoidRender.rand(0.07, 0.13), 0.9f);
				p.attract = heart;
				p.pull = 0.06f;
				add(p);
			}
		}

		@Override
		boolean tick() {
			if (!out && age == GATHER) {
				// Standing up: a wave of spores out over the ground, and a puff up the new body
				for (int i = 0; i < 90; i++) {
					double a = VoidRender.rand(0, Math.PI * 2);
					double v = VoidRender.rand(0.25, 0.5);
					add(spore(at.add(0, VoidRender.rand(0.1, 0.4), 0), new Vec3(Math.cos(a) * v, VoidRender.rand(0, 0.03), Math.sin(a) * v), (int) VoidRender.rand(16, 24),
							VoidRender.rand(0.07, 0.12), 0.88f));
				}
				for (int i = 0; i < 30; i++) {
					VoidRender.Particle p = spore(at.add(VoidRender.rand(-0.3, 0.3), VoidRender.rand(0, 1.8), VoidRender.rand(-0.3, 0.3)),
							new Vec3(0, VoidRender.rand(0.03, 0.08), 0), (int) VoidRender.rand(20, 30), 0.08, 0.95f);
					p.jitter = 0.005f;
					add(p);
				}
			}
			return super.tick();
		}

		@Override
		void draw(FxDraw draw, float partial) {
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
		/** Blocks tall per block of the node model (its cap is 13/16 high). */
		private static final float SCALE = 7f;
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
				moteAt(body.position().add(Math.cos(a) * r, VoidRender.rand(5.2, 5.8), Math.sin(a) * r),
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
			// The very mushroom that grows on the network, as big as a tree, swaying a little and breathing
			float breathe = 1 + 0.02f * (float) Math.sin(time * 1.6);
			draw.block(MyceliumNodeBlock.NODE.get().defaultBlockState(), base, SCALE * s * breathe, 0, (float) Math.sin(time * 1.3) * 1.5f,
					(float) Math.cos(time * 1.1) * 1.5f);
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
