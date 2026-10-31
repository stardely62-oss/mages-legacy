package euf.mageslegacy.client.fx;

import euf.mageslegacy.fx.Fx;
import euf.mageslegacy.net.FxPayload;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * The Bloodkin's effects, all drawn by the clients: pixel drops of blood and puffs of dark red mist (one quad each, a
 * hard cap on how many live at once), painted quads (tools/gen_blood_fx.py: a streak, a sigil, rings, cuts, stains,
 * flashes) glowing into the bloom, and the Prince of the Night's bats: a flock of flapping sprites wheeling round
 * the body it was (no entities: the server only says where and how long), bursting out of it and gathering back.
 */
public final class BloodFx {
	static final String DROP = "fx/sprite_blood";
	static final String MIST = "fx/sprite_blood_mist";
	/** Painted textures (tools/gen_blood_fx.py). */
	static final String BLOOD_STREAK = "fx/blood_streak";
	static final String BLOOD_SIGIL = "fx/blood_sigil";
	static final String BLOOD_WAVE = "fx/blood_wave";
	static final String BLOOD_SLASH_TEX = "fx/blood_slash";
	static final String BLOOD_SPLAT = "fx/blood_splat";
	static final String BLOOD_FLARE = "fx/blood_flare";
	static final String[] BAT = {"fx/sprite_bat_0", "fx/sprite_bat_1", "fx/sprite_bat_2", "fx/sprite_bat_1"};
	/** Shades laid over the drops and the mist: fresh blood, dark wine, nearly black. */
	private static final int[] BLOODS = {0xFFFFFFFF, 0xFFD0C0C0, 0xFFB09090, 0xF0E8D0D0};
	private static final int[] MISTS = {0xB0FFFFFF, 0x90D0B0B0, 0x80A08080};
	/** No Bloodkin effect adds drops while this many particles are alive already. */
	private static final int CAP = 800;
	/** The flocks flying now, by the entity they are. */
	private static final Map<Integer, Swarm> SWARMS = new HashMap<>();

	private BloodFx() {
	}

	static void play(FxPayload payload, Entity follow, Level level) {
		switch (payload.kind()) {
			case Fx.BLOOD -> splash(payload.from(), payload.a(), (int) payload.b());
			case Fx.THREAD -> {
				Entity to = level.getEntity((int) payload.b());
				if (follow != null && to != null) {
					VoidRender.add(new BloodThread(follow, to, payload.ticks()));
				}
			}
			case Fx.BLOOD_TRAIL -> trail(follow, payload.from(), payload.to(), payload.ticks());
			case Fx.BLOOD_RING -> ring(payload.from(), payload.a(), payload.ticks());
			case Fx.BLOOD_SLASH -> {
				// Across the dash's way, tilted a little either side
				VoidRender.add(new Slash(payload.from(), r(-0.5, 0.5) + (VoidRender.RANDOM.nextBoolean() ? 0 : Math.PI)));
				Vec3 g = payload.from();
				VoidRender.add(new Stain(new Vec3(g.x, groundY(g), g.z), (float) r(0.7, 1.1), 100));
			}
			case Fx.SACRIFICE -> {
				if (follow != null) {
					VoidRender.add(new Helix(follow, payload.ticks()));
				}
			}
			case Fx.BLOOD_AURA -> {
				if (follow != null) {
					VoidRender.add(new Aura(follow, payload.ticks()));
				}
			}
			case Fx.BLOOD_VORTEX -> {
				if (follow != null) {
					VoidRender.add(new Vortex(follow, payload.a(), payload.ticks()));
				}
			}
			case Fx.BAT_SWARM -> swarm(follow, payload.entity(), payload.ticks(), payload.b() > 0, payload.from());
			default -> {
			}
		}
	}

	private static double r(double min, double max) {
		return VoidRender.rand(min, max);
	}

	private static void add(VoidRender.Particle p) {
		if (VoidRender.PARTICLES.size() < CAP) {
			VoidRender.add(p);
		}
	}

	/** A drop of blood: falls, slows, fades. */
	private static VoidRender.Particle drop(Vec3 at, Vec3 vel, int life, double size) {
		VoidRender.Particle p = new VoidRender.Particle(at, vel, life, (float) size, BLOODS[VoidRender.RANDOM.nextInt(BLOODS.length)], false, 0.94f, 0);
		p.tex = DROP;
		p.gravity = new Vec3(0, -0.03, 0);
		return p;
	}

	/** A puff of dark red mist: drifts, swells a little, fades. */
	private static VoidRender.Particle mist(Vec3 at, Vec3 vel, int life, double size) {
		VoidRender.Particle p = new VoidRender.Particle(at, vel, life, (float) size, MISTS[VoidRender.RANDOM.nextInt(MISTS.length)], false, 0.9f, 0);
		p.tex = MIST;
		p.jitter = 0.004f;
		return p;
	}

	private static void splash(Vec3 at, float spread, int count) {
		for (int i = 0; i < count; i++) {
			Vec3 d = VoidRender.randomDir();
			add(drop(at.add(d.scale(spread * 0.4)), new Vec3(d.x * 0.12, Math.abs(d.y) * 0.15 + 0.05, d.z * 0.12), (int) r(14, 26), r(0.08, 0.16)));
		}
		for (int i = 0; i < count / 4; i++) {
			add(mist(at.add(VoidRender.randomDir().scale(spread * 0.5)), new Vec3(0, 0.01, 0), (int) r(16, 28), r(0.25, 0.45)));
		}
	}

	/** The Blood Dash: its wake follows the body from where it began, and a stain is left on the ground behind it. */
	private static void trail(Entity body, Vec3 from, Vec3 to, int dashTicks) {
		VoidRender.add(new Wake(body, from, to, dashTicks));
		// It tears away from where it stood: a flash, a ring thrown out on the ground, a puff of mist
		VoidRender.add(new Flare(null, from.add(0, 1, 0), 1.4f, 8));
		VoidRender.add(new Wave(from, 0.4f, 2.6f, 10));
		for (int i = 0; i < 10; i++) {
			add(mist(from.add(r(-0.3, 0.3), r(0.2, 1.6), r(-0.3, 0.3)), VoidRender.randomDir().scale(0.06), (int) r(18, 30), r(0.4, 0.7)));
		}
	}

	/** The Harvest's circle (a Transfusion's small one under a friend): drops all round its edge drawn in to its heart. */
	private static void ring(Vec3 at, float radius, int ticks) {
		VoidRender.add(new Sigil(at, radius, ticks));
		int n = (int) (radius * 10);
		Vec3 heart = at.add(0, 1, 0);
		for (int i = 0; i < n; i++) {
			double a = Math.PI * 2 * i / n;
			Vec3 p = at.add(Math.cos(a) * radius, r(0.1, 0.6), Math.sin(a) * radius);
			VoidRender.Particle d = drop(p, new Vec3(0, r(0.05, 0.12), 0), 40, r(0.1, 0.16));
			d.gravity = Vec3.ZERO;
			d.attract = heart;
			d.pull = 0.035f;
			add(d);
			if (i % 3 == 0) {
				add(mist(p, Vec3.ZERO, 24, r(0.35, 0.6)));
			}
		}
	}

	/** The camera's left and up, for quads turned to face it. */
	private static Vec3[] facing() {
		Camera camera = Minecraft.getInstance().gameRenderer.getMainCamera();
		return new Vec3[] {new Vec3(camera.getLeftVector().x(), camera.getLeftVector().y(), camera.getLeftVector().z()),
				new Vec3(camera.getUpVector().x(), camera.getUpVector().y(), camera.getUpVector().z())};
	}

	/** A painted quad facing the camera, turned by roll (radians), this half-size. */
	private static void billboard(FxDraw draw, String tex, Vec3 at, float half, double roll, float bright, float a, float glow) {
		Vec3[] f = facing();
		Vec3 u = f[0].scale(Math.cos(roll)).add(f[1].scale(Math.sin(roll))).scale(half);
		Vec3 v = f[1].scale(Math.cos(roll)).subtract(f[0].scale(Math.sin(roll))).scale(half);
		draw.paint(tex, at, u, v, bright, bright, bright, a, glow);
	}

	/** A painted quad lying on the ground: centred, this half-size, turned by an angle (radians). */
	private static void decal(FxDraw draw, String tex, Vec3 at, float half, double angle, float bright, float a, float glow) {
		Vec3 u = new Vec3(Math.cos(angle) * half, 0, Math.sin(angle) * half);
		Vec3 v = new Vec3(-Math.sin(angle) * half, 0, Math.cos(angle) * half);
		draw.paint(tex, at, u, v, bright, bright, bright, a, glow);
	}

	/** A band from a to b, this wide, facing the camera; the texture runs along it from u0 to u1. */
	private static void band(FxDraw draw, String tex, Vec3 a, Vec3 b, float width, float u0, float u1, float alpha, float glow) {
		Vec3 along = b.subtract(a);
		Vec3 side = along.cross(draw.cam.subtract(a.add(b).scale(0.5)));
		if (side.lengthSqr() < 1.0E-6) {
			return;
		}
		side = side.normalize().scale(width / 2);
		draw.paintStrip(tex, new Vec3[] {a.subtract(side), b.subtract(side), b.add(side), a.add(side)}, new float[] {u0, u1, u1, u0},
				new float[] {0, 0, 1, 1}, 1, 1, 1, alpha, glow);
	}

	/** The same band lying flat (its side across it on the ground), for a wake seen from behind or above. */
	private static void flatBand(FxDraw draw, String tex, Vec3 a, Vec3 b, float width, float alpha, float glow) {
		Vec3 along = b.subtract(a);
		Vec3 side = new Vec3(-along.z, 0, along.x);
		if (side.lengthSqr() < 1.0E-6) {
			return;
		}
		side = side.normalize().scale(width / 2);
		draw.paintStrip(tex, new Vec3[] {a.subtract(side), b.subtract(side), b.add(side), a.add(side)}, new float[] {0, 1, 1, 0},
				new float[] {0, 0, 1, 1}, 1, 1, 1, alpha, glow);
	}

	/** The top of the ground at or a few blocks under a point (just above it, for decals). */
	static double groundY(Vec3 at) {
		Level level = Minecraft.getInstance().level;
		if (level == null) {
			return at.y + 0.03;
		}
		BlockPos.MutableBlockPos pos = BlockPos.containing(at.x, at.y + 0.5, at.z).mutable();
		for (int i = 0; i < 5; i++) {
			VoxelShape shape = level.getBlockState(pos).getCollisionShape(level, pos);
			if (!shape.isEmpty()) {
				double top = pos.getY() + shape.max(Direction.Axis.Y);
				if (top <= at.y + 0.6) {
					return top + 0.03;
				}
			}
			pos.move(Direction.DOWN);
		}
		return at.y + 0.03;
	}

	private static float smooth(float t) {
		t = Math.max(0, Math.min(1, t));
		return t * t * (3 - 2 * t);
	}

	/**
	 * The Blood Dash's wake: a streak of blood stretched from where it began to the body as it rushes (its head on the
	 * body), standing and lying on the ground, then drawn after it and gone; mist and drops torn off the body as it goes,
	 * a stain left on the ground every step.
	 */
	static final class Wake extends VoidRender.Effect {
		static final int LINGER = 20;
		final Entity body;
		final Vec3 from;
		final Vec3 to;
		final int dash;
		Vec3 last;

		Wake(Entity body, Vec3 from, Vec3 to, int dash) {
			super(dash + LINGER);
			this.body = body;
			this.from = from;
			this.to = to;
			this.dash = dash;
			this.last = from;
		}

		/** Where the head is: on the body while it rushes, then where it stopped. */
		Vec3 head(float partial) {
			if (life(partial) <= dash && body != null && body.isAlive()) {
				return body.getPosition(partial);
			}
			return to;
		}

		@Override
		boolean tick() {
			if (age <= dash + 1) {
				Vec3 now = head(1);
				Vec3 step = now.subtract(last);
				int n = (int) Math.max(2, step.length() * 4);
				Vec3 dir = step.lengthSqr() > 1.0E-6 ? step.normalize() : Vec3.ZERO;
				for (int i = 0; i < n; i++) {
					Vec3 p = last.add(step.scale(i / (double) n));
					if (i % 2 == 0) {
						add(mist(p.add(r(-0.25, 0.25), r(0.2, 1.7), r(-0.25, 0.25)), dir.scale(0.04).add(0, 0.01, 0), (int) r(26, 44), r(0.3, 0.5)));
					}
					add(drop(p.add(r(-0.2, 0.2), r(0.3, 1.6), r(-0.2, 0.2)), dir.scale(r(0.05, 0.2)).add(r(-0.08, 0.08), r(0.04, 0.16),
							r(-0.08, 0.08)), (int) r(16, 28), r(0.1, 0.18)));
					if (i % 2 == 0) {
						Vec3 side = new Vec3(-dir.z, 0, dir.x).scale(r(-0.6, 0.6));
						Vec3 g = p.add(side);
						VoidRender.add(new Stain(new Vec3(g.x, groundY(g), g.z), (float) r(0.35, 0.8), 90));
					}
				}
				last = now;
			}
			return super.tick();
		}

		@Override
		void draw(FxDraw draw, float partial) {
			float life = life(partial);
			Vec3 head = head(partial);
			// After the rush the tail is drawn in after the body, thinning and fading
			float k = smooth((life - dash) / LINGER);
			Vec3 tail = from.lerp(head, VoidRender.easeOut(k) * 0.9);
			if (tail.distanceToSqr(head) < 0.04) {
				return;
			}
			float alpha = (1 - k * k) * Math.min(1, life / 1.5f);
			float width = 1.1f * (1 - 0.5f * k);
			band(draw, BLOOD_STREAK, tail.add(0, 1.05, 0), head.add(0, 1.05, 0), width, 0, 1, alpha * 0.9f, 0.5f);
			band(draw, BLOOD_STREAK, tail.add(0, 0.45, 0), head.add(0, 0.45, 0), width * 0.55f, 0, 1, alpha * 0.4f, 0.25f);
			double y = groundY(head);
			flatBand(draw, BLOOD_STREAK, new Vec3(tail.x, y + 0.01, tail.z), new Vec3(head.x, y + 0.01, head.z), width, alpha * 0.55f, 0.35f);
		}
	}

	/** A stain of blood on the ground: spreads at once, stays a few seconds, fades. No glow. */
	static final class Stain extends VoidRender.Effect {
		final Vec3 at;
		final float half;
		final double angle = r(0, Math.PI * 2);

		Stain(Vec3 at, float half, int ticks) {
			super(ticks);
			this.at = at;
			this.half = half;
		}

		@Override
		void draw(FxDraw draw, float partial) {
			float life = life(partial);
			float spread = 0.5f + 0.5f * VoidRender.easeOut(Math.min(1, life / 4f));
			float alpha = Math.min(1, (ticks - life) / 30f) * 0.9f;
			decal(draw, BLOOD_SPLAT, at, half * spread, angle, 1, alpha, 0);
		}
	}

	/** A cut: a crescent of blood across a creature, a second crossing it a moment later, flashing and gone. */
	static final class Slash extends VoidRender.Effect {
		final Vec3 at;
		final double roll;

		Slash(Vec3 at, double roll) {
			super(10);
			this.at = at;
			this.roll = roll;
		}

		@Override
		void draw(FxDraw draw, float partial) {
			float life = life(partial);
			for (int k = 0; k < 2; k++) {
				float t = life - k * 2;
				if (t < 0) {
					continue;
				}
				float size = (k == 0 ? 1.1f : 0.85f) * (0.4f + 0.6f * VoidRender.easeOutBack(Math.min(1, t / 3f)));
				float alpha = Math.min(1, (ticks - k * 2 - t) / 5f);
				billboard(draw, BLOOD_SLASH_TEX, at, size, roll + k * 1.9, 1, alpha, 0.9f);
			}
			if (life < 5) {
				billboard(draw, BLOOD_FLARE, at, 0.7f + life * 0.1f, 0, 1, (1 - life / 5) * 0.6f, 0.8f);
			}
		}
	}

	/** A soft red flash at a point (on a body, if given), swelling and gone. */
	static final class Flare extends VoidRender.Effect {
		final Entity body;
		final Vec3 at;
		final float size;

		Flare(Entity body, Vec3 at, float size, int ticks) {
			super(ticks);
			this.body = body;
			this.at = at;
			this.size = size;
		}

		@Override
		void draw(FxDraw draw, float partial) {
			float t = progress(partial);
			Vec3 c = body != null && body.isAlive() ? body.getPosition(partial).add(0, body.getBbHeight() * 0.6, 0) : at;
			billboard(draw, BLOOD_FLARE, c, size * (0.6f + 0.6f * VoidRender.easeOut(t)), 0, 1, (1 - t) * (1 - t) * 0.7f, 0.9f);
		}
	}

	/** A thin ring on the ground going from one radius to another (closing in when the second is smaller). */
	static final class Wave extends VoidRender.Effect {
		final Vec3 at;
		final float r0;
		final float r1;

		Wave(Vec3 at, float r0, float r1, int ticks) {
			super(ticks);
			this.at = new Vec3(at.x, groundY(at) + 0.02, at.z);
			this.r0 = r0;
			this.r1 = r1;
		}

		@Override
		void draw(FxDraw draw, float partial) {
			float t = progress(partial);
			float e = r1 > r0 ? VoidRender.easeOut(t) : t * t;
			float alpha = (float) Math.sqrt(Math.sin(Math.PI * t));
			decal(draw, BLOOD_WAVE, at, r0 + (r1 - r0) * e, 0, 0.9f, alpha * 0.85f, 0.6f);
		}
	}

	/**
	 * The Harvest's circle on the ground: a sigil thrown open to the full reach, turning, beating like a heart (its
	 * smaller twin turning the other way inside it); two rings closing in on its heart, a flash where they meet.
	 */
	static final class Sigil extends VoidRender.Effect {
		final Vec3 at;
		final float radius;

		Sigil(Vec3 at, float radius, int ticks) {
			super(Math.max(24, ticks));
			this.at = new Vec3(at.x, groundY(at), at.z);
			this.radius = radius;
			float close = Math.min(14, radius * 1.6f);
			VoidRender.add(new Wave(at, radius, 0.3f, (int) close));
			VoidRender.add(new Delayed(6, () -> VoidRender.add(new Wave(at, radius * 0.8f, 0.3f, (int) close))));
			VoidRender.add(new Delayed((int) close, () -> VoidRender.add(new Flare(null, at.add(0, 1, 0), Math.min(1.5f, 0.7f + radius * 0.1f), 10))));
		}

		@Override
		void draw(FxDraw draw, float partial) {
			float life = life(partial);
			float open = VoidRender.easeOutBack(Math.min(1, life / 8f));
			float half = radius * (0.3f + 0.7f * open);
			// Lub-dub every second
			float beat = (float) (Math.exp(-Math.pow((life % 20) / 2.0, 2)) + 0.7 * Math.exp(-Math.pow((life % 20 - 7) / 2.0, 2)));
			float alpha = Math.min(1, life / 3f) * (1 - smooth((life - (ticks - 14)) / 14f));
			float bright = 0.7f + 0.3f * beat;
			decal(draw, BLOOD_SIGIL, at, half, life * 0.03, bright, alpha * 0.9f, 0.25f + 0.5f * beat);
			decal(draw, BLOOD_SIGIL, at.add(0, 0.01, 0), half * 0.42f, -life * 0.07, bright, alpha * 0.6f, 0.2f + 0.5f * beat);
		}
	}

	/** Runs something a few ticks from now. */
	static final class Delayed extends VoidRender.Effect {
		final Runnable then;

		Delayed(int ticks, Runnable then) {
			super(ticks);
			this.then = then;
		}

		@Override
		boolean tick() {
			if (age + 1 >= ticks) {
				then.run();
			}
			return super.tick();
		}

		@Override
		void draw(FxDraw draw, float partial) {
		}
	}

	/**
	 * A thread of blood from one body into another: a glowing strand arcing between them, pulses running along it
	 * to the drinker, drops pouring along it while it lasts; a gush where it is torn out.
	 */
	static final class BloodThread extends VoidRender.Effect {
		final Entity from;
		final Entity to;

		BloodThread(Entity from, Entity to, int ticks) {
			super(ticks);
			this.from = from;
			this.to = to;
		}

		@Override
		boolean tick() {
			if (!from.isAlive() && age > 2 || to.isRemoved()) {
				return false;
			}
			Vec3 a = from.position().add(0, from.getBbHeight() * 0.6, 0);
			Vec3 b = to.position().add(0, to.getBbHeight() * 0.6, 0);
			if (age == 0) {
				splash(a, 0.4f, 10);
			}
			for (int i = 0; i < 2; i++) {
				VoidRender.Particle d = drop(a.add(r(-0.2, 0.2), r(-0.2, 0.2), r(-0.2, 0.2)), b.subtract(a).normalize().scale(0.25).add(0, 0.12, 0), 30,
						r(0.08, 0.13));
				d.gravity = Vec3.ZERO;
				d.attract = b;
				d.pull = 0.06f;
				add(d);
			}
			return super.tick();
		}

		@Override
		void draw(FxDraw draw, float partial) {
			if (to.isRemoved()) {
				return;
			}
			float life = life(partial);
			Vec3 a = from.getPosition(partial).add(0, from.getBbHeight() * 0.6, 0);
			Vec3 b = to.getPosition(partial).add(0, to.getBbHeight() * 0.6, 0);
			double length = a.distanceTo(b);
			Vec3 lift = new Vec3(0, 0.4 + length * 0.12, 0);
			// Shot out of the source over 4 ticks, thinning away over the last 5
			float reach = VoidRender.easeOut(Math.min(1, life / 4f));
			float fade = Math.min(1, (ticks - life) / 5f);
			int segs = 12;
			Vec3 prev = a;
			for (int i = 1; i <= segs; i++) {
				float s = i / (float) segs;
				if (s > reach + 1.0E-3) {
					break;
				}
				Vec3 p = a.lerp(b, s).add(lift.scale(4 * s * (1 - s)));
				float pulse = 0.55f + 0.45f * (float) Math.pow(Math.sin(s * 9 - life * 1.1), 2);
				band(draw, BLOOD_STREAK, prev, p, 0.3f * fade + 0.06f, (i - 1) / (float) segs, s, fade * pulse, 0.7f * pulse);
				prev = p;
			}
		}
	}

	/**
	 * The Sacrifice: the cut, and a gush of blood thrown up off the body; then three strands of it winding up round
	 * the body over a turning sigil, and drunk back into the chest in a flash.
	 */
	static final class Helix extends VoidRender.Effect {
		final Entity body;

		Helix(Entity body, int ticks) {
			super(ticks);
			this.body = body;
		}

		@Override
		boolean tick() {
			Vec3 c = body.position();
			Vec3 chest = c.add(0, body.getBbHeight() * 0.6, 0);
			if (age == 0) {
				VoidRender.add(new Slash(chest, r(-0.6, 0.6)));
				for (int i = 0; i < 30; i++) {
					add(drop(chest, new Vec3(r(-0.1, 0.1), r(0.22, 0.42), r(-0.1, 0.1)), (int) r(24, 34), r(0.1, 0.17)));
				}
				for (int i = 0; i < 8; i++) {
					add(mist(chest.add(VoidRender.randomDir().scale(0.4)), VoidRender.randomDir().scale(0.04), 26, r(0.4, 0.7)));
				}
			}
			if (age == ticks - 7) {
				VoidRender.add(new Flare(body, chest, 1.1f, 10));
				splash(chest, 0.3f, 8);
			}
			if (age > 4 && age < ticks - 7) {
				for (int k = 0; k < 3; k++) {
					double a = age * 0.45 + k * Math.PI * 2 / 3;
					double h = Math.min(1, (age - 4) / 12.0) * 2.2;
					VoidRender.Particle d = drop(c.add(Math.cos(a) * 0.8, h, Math.sin(a) * 0.8), Vec3.ZERO, 14, 0.12);
					d.gravity = Vec3.ZERO;
					d.attract = chest;
					d.pull = 0.015f;
					add(d);
				}
			}
			return super.tick();
		}

		@Override
		void draw(FxDraw draw, float partial) {
			if (body.isRemoved()) {
				return;
			}
			float life = life(partial);
			Vec3 c = body.getPosition(partial);
			Vec3 chest = c.add(0, body.getBbHeight() * 0.6, 0);
			float in = Math.min(1, life / 4f);
			float out = Math.min(1, (ticks - life) / 6f);
			decal(draw, BLOOD_SIGIL, new Vec3(c.x, groundY(c), c.z), 1.5f * VoidRender.easeOutBack(Math.min(1, life / 6f)), life * 0.12, 1,
					in * out * 0.85f, 0.5f);
			// The strands rise over the first 12 ticks after the cut and close into the chest over the last 8
			float rise = smooth((life - 3) / 12f);
			float close = smooth((life - (ticks - 9)) / 6f);
			if (rise <= 0) {
				return;
			}
			int segs = 10;
			for (int k = 0; k < 3; k++) {
				double phase = life * 0.35 + k * Math.PI * 2 / 3;
				Vec3 prev = null;
				for (int i = 0; i <= segs; i++) {
					float s = i / (float) segs * rise;
					double ang = phase + s * Math.PI * 2 * 1.1;
					double radius = 0.85 * (1 - 0.45 * s);
					Vec3 p = c.add(Math.cos(ang) * radius, 0.1 + s * 2.1, Math.sin(ang) * radius).lerp(chest, close);
					if (prev != null) {
						band(draw, BLOOD_STREAK, prev, p, 0.24f, (i - 1) / (float) segs, i / (float) segs, out * 0.95f, 0.7f);
					}
					prev = p;
				}
			}
		}
	}

	/** The Feast: embers of blood rising off the body, a slow dark mist round the feet. */
	static final class Aura extends VoidRender.Effect {
		final Entity body;

		Aura(Entity body, int ticks) {
			super(ticks);
			this.body = body;
		}

		@Override
		boolean tick() {
			if (body.isRemoved()) {
				return false;
			}
			Vec3 c = body.position();
			for (int i = 0; i < 2; i++) {
				double a = r(0, Math.PI * 2);
				VoidRender.Particle d = drop(c.add(Math.cos(a) * r(0.3, 0.7), r(0.1, 1.6), Math.sin(a) * r(0.3, 0.7)), new Vec3(0, r(0.03, 0.07), 0), 22,
						r(0.06, 0.11));
				d.gravity = Vec3.ZERO;
				add(d);
			}
			if (age % 3 == 0) {
				add(mist(c.add(r(-0.6, 0.6), 0.1, r(-0.6, 0.6)), new Vec3(0, 0.01, 0), 30, r(0.35, 0.6)));
			}
			return super.tick();
		}

		@Override
		void draw(FxDraw draw, float partial) {
		}
	}

	/** The Blood Storm: drops and mist wheeling round the Bloodkin out to its reach, drawn slowly inward and up. */
	static final class Vortex extends VoidRender.Effect {
		final Entity heart;
		final float radius;

		Vortex(Entity heart, float radius, int ticks) {
			super(ticks);
			this.heart = heart;
			this.radius = radius;
		}

		@Override
		boolean tick() {
			if (heart.isRemoved()) {
				return false;
			}
			Vec3 c = heart.position();
			for (int i = 0; i < 10; i++) {
				double a = r(0, Math.PI * 2);
				double d = r(1.5, radius);
				VoidRender.Particle p = i % 3 == 0 ? mist(c.add(Math.cos(a) * d, r(0.2, 1.4), Math.sin(a) * d), Vec3.ZERO, 26, r(0.4, 0.7))
						: drop(c.add(Math.cos(a) * d, r(0.2, 1.8), Math.sin(a) * d), new Vec3(0, 0.02, 0), 26, r(0.09, 0.15));
				p.gravity = Vec3.ZERO;
				p.swirlAt = c;
				p.swirl = 0.05f;
				p.attract = c.add(0, 1, 0);
				p.pull = 0.006f;
				add(p);
			}
			return super.tick();
		}

		@Override
		void draw(FxDraw draw, float partial) {
		}
	}

	/** One bat of a flock: where on its orbit, how far out, how high, how fast, its wingbeat. */
	private record Bat(double angle, double radius, double height, double speed, int phase, double size) {
	}

	/**
	 * A body scattered into bats: they burst out of it, wheel round where it flies (following it smoothly), red mist
	 * trailing off them; at the end they gather back into one point and are gone. The Last Breath's flock is bigger.
	 */
	static final class Swarm extends VoidRender.Effect {
		final Entity body;
		final List<Bat> bats = new ArrayList<>();
		/** Gathering: from this tick of its life the flock closes in on this point. */
		int gatherAt = -1;
		Vec3 gatherTo;

		Swarm(Entity body, int ticks, boolean rescue) {
			super(ticks + 14);
			this.body = body;
			int n = rescue ? 56 : 40;
			for (int i = 0; i < n; i++) {
				bats.add(new Bat(r(0, Math.PI * 2), r(0.6, 2.2), r(0.2, 2.2), r(0.18, 0.4) * (VoidRender.RANDOM.nextBoolean() ? 1 : -1),
						VoidRender.RANDOM.nextInt(4), r(0.14, 0.26)));
			}
		}

		void gather(Vec3 at) {
			if (gatherAt < 0) {
				gatherAt = age;
				gatherTo = at;
			}
		}

		@Override
		boolean tick() {
			if (gatherAt < 0 && age >= ticks - 14) {
				gather(body.position());
			}
			// Red mist torn off the flock as it flies
			if (gatherAt < 0 && age % 2 == 0) {
				Vec3 c = body.position().add(0, 1, 0);
				add(mist(c.add(VoidRender.randomDir().scale(1.2)), Vec3.ZERO, 22, r(0.35, 0.6)));
			}
			if (gatherAt >= 0 && age - gatherAt > 12) {
				return false;
			}
			return super.tick();
		}

		@Override
		void draw(FxDraw draw, float partial) {
			Camera camera = Minecraft.getInstance().gameRenderer.getMainCamera();
			Vec3 left = new Vec3(camera.getLeftVector().x(), camera.getLeftVector().y(), camera.getLeftVector().z());
			Vec3 up = new Vec3(camera.getUpVector().x(), camera.getUpVector().y(), camera.getUpVector().z());
			float life = life(partial);
			Vec3 centre = gatherAt >= 0 ? gatherTo : body.getPosition(partial);
			// Bursting out over the first 6 ticks, closing in over the last 12
			float out = Math.min(1, life / 6f);
			float in = gatherAt < 0 ? 1 : Math.max(0, 1 - (life - gatherAt) / 12f);
			float spread = VoidRender.easeOut(out) * in;
			for (Bat b : bats) {
				double a = b.angle() + life * b.speed();
				double radius = b.radius() * spread;
				Vec3 at = centre.add(Math.cos(a) * radius, 0.6 + (b.height() - 0.6) * spread + 0.15 * Math.sin(life * 0.5 + b.phase()),
						Math.sin(a) * radius);
				// Fade close to the eyes (the player's own swarm round their camera) and while gathering
				double near = (at.distanceTo(draw.cam) - 0.6) / 1.2;
				if (near <= 0) {
					continue;
				}
				int alpha = (int) (255 * Math.min(1, near) * Math.min(1, in * 1.5f));
				String frame = BAT[(int) ((life * 0.8 + b.phase()) % BAT.length)];
				draw.sprite(frame, at, left, up, (float) b.size(), (alpha << 24) | 0xFFFFFF, 0);
			}
		}
	}

	private static void swarm(Entity body, int id, int ticks, boolean rescue, Vec3 at) {
		Swarm flying = SWARMS.get(id);
		if (ticks <= 0) {
			// Gathered back into a body: the flock closes in, a puff of blood where it lands
			if (flying != null) {
				flying.gather(body != null ? body.position() : at);
				SWARMS.remove(id);
			}
			splash((body != null ? body.position() : at).add(0, 1, 0), 0.6f, 18);
			return;
		}
		if (body == null) {
			return;
		}
		Swarm swarm = new Swarm(body, ticks, rescue);
		SWARMS.put(id, swarm);
		SWARMS.values().removeIf(s -> s.age >= s.ticks);
		VoidRender.add(swarm);
		// The body comes apart: a burst of blood and a cloud of dark mist where it stood
		splash(body.position().add(0, 1, 0), rescue ? 1.2f : 0.8f, rescue ? 36 : 22);
		for (int i = 0; i < (rescue ? 16 : 10); i++) {
			add(mist(body.position().add(0, r(0.2, 1.8), 0).add(VoidRender.randomDir().scale(0.6)), VoidRender.randomDir().scale(0.05), 30, r(0.4, 0.8)));
		}
	}
}
