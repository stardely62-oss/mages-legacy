package euf.mageslegacy.client.fx;

import euf.mageslegacy.fx.Fx;
import euf.mageslegacy.net.FxPayload;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

/**
 * The Bloodkin's effects, all drawn by the clients: pixel drops of blood and puffs of dark red mist (one quad each, a
 * hard cap on how many live at once), and the Prince of the Night's bats: a flock of flapping sprites wheeling round
 * the body it was (no entities: the server only says where and how long), bursting out of it and gathering back.
 */
public final class BloodFx {
	static final String DROP = "fx/sprite_blood";
	static final String MIST = "fx/sprite_blood_mist";
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
			case Fx.BLOOD_TRAIL -> trail(payload.from(), payload.to());
			case Fx.BLOOD_RING -> ring(payload.from(), payload.a());
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

	/** The Dash's way: a wake of mist along it and drops shaken off. */
	private static void trail(Vec3 from, Vec3 to) {
		Vec3 along = to.subtract(from);
		// A dense wake, body-high, that lingers a second and a half: mist thickest along the middle, drops flung off it
		int n = (int) Math.max(8, along.length() * 7);
		Vec3 dir = along.lengthSqr() > 1.0E-6 ? along.normalize() : Vec3.ZERO;
		for (int i = 0; i <= n; i++) {
			Vec3 p = from.add(along.scale(i / (double) n)).add(r(-0.2, 0.2), r(0.2, 1.7), r(-0.2, 0.2));
			add(mist(p, dir.scale(0.03), (int) r(24, 40), r(0.45, 0.8)));
			add(drop(p, dir.scale(r(0.05, 0.15)).add(r(-0.06, 0.06), r(0.03, 0.12), r(-0.06, 0.06)), (int) r(16, 28), r(0.09, 0.16)));
		}
	}

	/** The Harvest's circle: drops all round its edge drawn in to the Bloodkin. */
	private static void ring(Vec3 at, float radius) {
		int n = (int) (radius * 7);
		Vec3 heart = at.add(0, 1, 0);
		for (int i = 0; i < n; i++) {
			double a = Math.PI * 2 * i / n;
			Vec3 p = at.add(Math.cos(a) * radius, r(0.2, 0.8), Math.sin(a) * radius);
			VoidRender.Particle d = drop(p, Vec3.ZERO, 40, r(0.09, 0.14));
			d.gravity = Vec3.ZERO;
			d.attract = heart;
			d.pull = 0.03f;
			add(d);
			if (i % 3 == 0) {
				add(mist(p, Vec3.ZERO, 24, r(0.3, 0.5)));
			}
		}
	}

	/** A thread of blood from one body into another: drops pouring along it while it lasts. */
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
			for (int i = 0; i < 3; i++) {
				VoidRender.Particle d = drop(a.add(r(-0.2, 0.2), r(-0.2, 0.2), r(-0.2, 0.2)), b.subtract(a).normalize().scale(0.25), 30, r(0.08, 0.13));
				d.gravity = Vec3.ZERO;
				d.attract = b;
				d.pull = 0.06f;
				add(d);
			}
			return super.tick();
		}

		@Override
		void draw(FxDraw draw, float partial) {
		}
	}

	/** The Sacrifice: blood wound up round the body in a rising spiral, then drunk back into the chest. */
	static final class Helix extends VoidRender.Effect {
		final Entity body;

		Helix(Entity body, int ticks) {
			super(ticks);
			this.body = body;
		}

		@Override
		boolean tick() {
			Vec3 c = body.position();
			for (int k = 0; k < 3; k++) {
				double a = age * 0.6 + k * Math.PI * 2 / 3;
				double h = age / (double) ticks * 2.2;
				VoidRender.Particle d = drop(c.add(Math.cos(a) * 0.9, h, Math.sin(a) * 0.9), Vec3.ZERO, 16, 0.11);
				d.gravity = Vec3.ZERO;
				d.attract = c.add(0, 1.2, 0);
				d.pull = 0.02f;
				add(d);
			}
			return super.tick();
		}

		@Override
		void draw(FxDraw draw, float partial) {
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
