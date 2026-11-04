package euf.mageslegacy.client.fx;

import euf.mageslegacy.fx.Fx;
import euf.mageslegacy.net.FxPayload;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.Vec3;

/**
 * The Mercenaries' effects: gold, not light. Everything is a solid object of pixel-art metal (Codex sprites through
 * tools/import_merc_art.py): coins that spin, fall and ring off the ground; shards of broken gold; golden swords; seals
 * and chains of engraved gold; the guild's banner of crimson cloth; a door of oak and copper. They catch the light with a
 * faint glint at most, never glow like lamps. Seals over creatures are kept by the creature (a new word from the server
 * replaces the old); the harpoon's chain is drawn link by link. Everything near a creature stays small enough that the
 * creature stays seen.
 */
public final class MercFx {
	static final String SEAL = "fx/merc_seal";
	static final String SEAL_PART = "fx/merc_seal_part";
	static final String SEAL_QUARTER = "fx/merc_seal_quarter";
	static final String SWORD = "fx/merc_blade";
	static final String SHARD = "fx/merc_shard";
	static final String LINK = "fx/merc_link";
	static final String HOOK = "fx/merc_hook";
	static final String COIN = "fx/merc_coin_face";
	static final String COIN_SPRITE = "fx/sprite_merc_coin";
	static final String DUST = "fx/sprite_merc_spark";
	static final String BANNER = "fx/merc_banner";
	static final String POLE = "fx/merc_pole";
	static final String SIGIL = "fx/merc_sigil";
	static final String DOOR = "fx/merc_door";
	static final String WARM = "fx/merc_flare";
	static final String CONTRACT = "fx/merc_contract";
	static final String WARRANT = "fx/merc_warrant";
	/** Gold dust tints: dark gold, gold, pale gold (never white). */
	private static final int[] GOLDS = {0xFFB07A28, 0xFFD9A441, 0xFFF0D080};
	private static final int CAP = 900;
	/** The faint glint a polished metal gives the bloom: enough to catch the eye, never a lamp. */
	private static final float GLINT = 0.08f;

	private static final Map<Integer, BrandSeal> SEALS = new HashMap<>();
	private static final Map<Integer, Hook> HOOKS = new HashMap<>();
	private static final Map<Integer, VerdictSeal> VERDICTS = new HashMap<>();
	private static final Map<Integer, DebtSeal> DEBTS = new HashMap<>();
	private static final Map<Integer, Banner> BANNERS = new HashMap<>();
	private static final List<Door> DOORS = new ArrayList<>();

	/** A Nose for Profit: the last glint's direction (a world point) and until when (client ticks). */
	public static Vec3 pingAt;
	public static int pingLeft;

	private MercFx() {
	}

	static void play(FxPayload payload, Entity follow, Level level) {
		switch (payload.kind()) {
			case Fx.BRAND_BOLT -> VoidRender.add(new Bolt(payload.from(), payload.to(), payload.ticks()));
			case Fx.BRAND_SEAL -> {
				if (follow == null) {
					return;
				}
				BrandSeal old = SEALS.get(follow.getId());
				if (payload.ticks() <= 0) {
					if (old != null) {
						old.fading = payload.a() < 0 ? 1 : 6;
						SEALS.remove(follow.getId());
					}
					return;
				}
				if (old != null && old.fading < 0) {
					old.update((int) payload.a(), (int) payload.b(), payload.ticks());
				} else {
					BrandSeal seal = new BrandSeal(follow, (int) payload.a(), (int) payload.b(), payload.ticks());
					SEALS.put(follow.getId(), seal);
					VoidRender.add(seal);
				}
			}
			case Fx.BRAND_BLAST -> blast(payload.from(), payload.b() > 0);
			case Fx.HOOK -> {
				Hook old = HOOKS.get(payload.entity());
				if (payload.ticks() <= 0) {
					if (old != null) {
						old.letGo();
					}
					return;
				}
				if (follow != null) {
					Entity target = payload.b() >= 0 ? level.getEntity((int) payload.b()) : null;
					Hook hook = new Hook(follow, payload.to(), target, (int) payload.a(), payload.ticks());
					if (old != null) {
						old.letGo();
					}
					HOOKS.put(follow.getId(), hook);
					VoidRender.add(hook);
				}
			}
			case Fx.VERDICT_SEAL -> {
				if (follow == null) {
					return;
				}
				VerdictSeal old = VERDICTS.remove(follow.getId());
				if (old != null) {
					old.release();
				}
				if (payload.ticks() > 0) {
					VerdictSeal seal = new VerdictSeal(follow, payload.ticks(), Math.max(1, (int) payload.a()));
					VERDICTS.put(follow.getId(), seal);
					VoidRender.add(seal);
				}
			}
			case Fx.VERDICT_BLADE -> {
				if (follow != null) {
					VerdictSeal seal = VERDICTS.get(follow.getId());
					if (seal != null) {
						seal.lunge();
					} else {
						VoidRender.add(new Swing(follow, VoidRender.rand(0, Math.PI * 2), 1.0f, 9));
					}
				}
			}
			case Fx.VERDICT_EXECUTE -> {
				if (follow != null) {
					VerdictSeal seal = VERDICTS.get(follow.getId());
					VoidRender.add(new Executioner(follow, seal));
				}
			}
			case Fx.MARK -> VoidRender.add(new Pillar(payload.from(), payload.b() > 0, payload.ticks()));
			case Fx.DEBT_SEAL -> {
				if (follow == null) {
					return;
				}
				DebtSeal old = DEBTS.remove(follow.getId());
				if (old != null) {
					old.fading = 8;
				}
				if (payload.ticks() > 0) {
					DebtSeal seal = new DebtSeal(follow, payload.ticks());
					DEBTS.put(follow.getId(), seal);
					VoidRender.add(seal);
				}
			}
			case Fx.DEBT_WAVE -> debtWave(payload.from(), payload.a());
			case Fx.DEBT_DUE -> {
				if (follow != null) {
					due(follow);
				}
			}
			case Fx.RANSOM -> {
				if (follow != null) {
					ransom(follow);
				}
			}
			case Fx.BANNER -> {
				Banner old = BANNERS.get(payload.entity());
				if (payload.ticks() <= 0) {
					if (old != null) {
						old.fading = 14;
					}
					BANNERS.remove(payload.entity());
					return;
				}
				if (old != null && old.fading < 0) {
					old.extend(payload.ticks());
				} else {
					Banner banner = new Banner(payload.from(), payload.a(), payload.ticks());
					BANNERS.put(payload.entity(), banner);
					VoidRender.add(banner);
				}
			}
			case Fx.BANNER_BLADE -> {
				if (follow != null) {
					Vec3 d = follow.position().subtract(payload.from());
					VoidRender.add(new Swing(follow, Math.atan2(d.z, d.x) + Math.PI, 0.85f, 8));
				}
			}
			case Fx.DOOR -> {
				if (payload.ticks() > 0) {
					Door door = new Door(payload.from(), payload.a(), payload.ticks());
					DOORS.add(door);
					VoidRender.add(door);
				} else if (follow != null) {
					Door nearest = null;
					double best = 64;
					for (Door d : DOORS) {
						double dist = d.at.distanceToSqr(follow.position());
						if (d.closingAt < 0 && dist < best) {
							best = dist;
							nearest = d;
						}
					}
					if (nearest != null) {
						nearest.close();
					}
				}
			}
			case Fx.PROFIT_PING -> {
				pingAt = payload.to();
				pingLeft = payload.ticks();
				Minecraft.getInstance().getSoundManager().play(new SimpleSoundInstance(SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.PLAYERS, 0.25f, 1.9f,
						RandomSource.create(), pingAt.x, pingAt.y, pingAt.z));
			}
			case Fx.CONTRACT_DONE -> {
				if (follow != null) {
					VoidRender.add(new Stamp(follow));
					VoidRender.add(new CoinBurst(follow.position().add(0, 1.3, 0), 22, 0.22, 0.2));
				}
			}
			default -> {
			}
		}
	}

	public static void tick() {
		if (pingLeft > 0) {
			pingLeft--;
		}
	}

	// ---- helpers ---------------------------------------------------------------------------------------------------

	private static double r(double min, double max) {
		return VoidRender.rand(min, max);
	}

	private static void add(VoidRender.Particle p) {
		if (VoidRender.PARTICLES.size() < CAP) {
			VoidRender.add(p);
		}
	}

	/** A mote of gold dust: drifts, settles, fades; it barely glints. */
	private static VoidRender.Particle dust(Vec3 at, Vec3 vel, int life, double size, float drag) {
		VoidRender.Particle p = new VoidRender.Particle(at, vel, life, (float) size, GOLDS[VoidRender.RANDOM.nextInt(GOLDS.length)], false, drag, 0.25f);
		p.tex = DUST;
		return p;
	}

	private static void dustPuff(Vec3 at, int count, double speed) {
		for (int i = 0; i < count; i++) {
			VoidRender.Particle p = dust(at, VoidRender.randomDir().scale(r(speed * 0.3, speed)), (int) r(14, 26), r(0.04, 0.08), 0.88f);
			p.gravity = new Vec3(0, -0.006, 0);
			add(p);
		}
	}

	/** Gold dust settling in a widening ring on the ground (in place of a ring of light). */
	private static void dustRing(Vec3 at, float radius, int count) {
		double y = BloodFx.groundY(at) + 0.05;
		for (int i = 0; i < count; i++) {
			double a = Math.PI * 2 * i / count + r(-0.1, 0.1);
			Vec3 out = new Vec3(Math.cos(a), 0, Math.sin(a));
			Vec3 p0 = new Vec3(at.x, y, at.z).add(out.scale(0.3));
			VoidRender.Particle p = dust(p0, out.scale(radius * r(0.09, 0.12)).add(0, r(0.02, 0.06), 0), (int) r(18, 30), r(0.05, 0.09), 0.86f);
			p.gravity = new Vec3(0, -0.01, 0);
			add(p);
		}
	}

	private static Vec3[] facing() {
		Camera camera = Minecraft.getInstance().gameRenderer.getMainCamera();
		return new Vec3[] {new Vec3(camera.getLeftVector().x(), camera.getLeftVector().y(), camera.getLeftVector().z()),
				new Vec3(camera.getUpVector().x(), camera.getUpVector().y(), camera.getUpVector().z())};
	}

	/** A sprite facing the camera, turned by roll; shade darkens it (an unlit part of a seal), a its fade. */
	static void billboard(FxDraw draw, String tex, Vec3 at, float half, double roll, float shade, float a, float glow) {
		Vec3[] f = facing();
		Vec3 u = f[0].scale(Math.cos(roll)).add(f[1].scale(Math.sin(roll))).scale(half);
		Vec3 v = f[1].scale(Math.cos(roll)).subtract(f[0].scale(Math.sin(roll))).scale(-half);
		draw.paint(tex, at, u, v, shade, shade, shade, a, glow);
	}

	static void decal(FxDraw draw, String tex, Vec3 at, float half, double angle, float shade, float a, float glow) {
		Vec3 u = new Vec3(Math.cos(angle) * half, 0, Math.sin(angle) * half);
		Vec3 v = new Vec3(-Math.sin(angle) * half, 0, Math.cos(angle) * half);
		draw.paint(tex, at, u, v, shade, shade, shade, a, glow);
	}

	/** A band from a to b facing the camera, the texture running along it (u) and across it (v). */
	static void band(FxDraw draw, String tex, Vec3 a, Vec3 b, float width, float alpha, float glow) {
		Vec3 along = b.subtract(a);
		Vec3 side = along.cross(draw.cam.subtract(a.add(b).scale(0.5)));
		if (side.lengthSqr() < 1.0E-6) {
			return;
		}
		side = side.normalize().scale(width / 2);
		draw.paintStrip(tex, new Vec3[] {a.subtract(side), b.subtract(side), b.add(side), a.add(side)}, new float[] {0, 1, 1, 0},
				new float[] {0, 0, 1, 1}, 1, 1, 1, alpha, glow);
	}

	/**
	 * A golden sword (the sprite stands point up in its square) laid with its point at {@code tip} and its pommel
	 * {@code length} back along {@code -dir}; turned to the eye round its own axis.
	 */
	static void sword(FxDraw draw, Vec3 tip, Vec3 dir, float length, float shade, float alpha, float glow) {
		Vec3 centre = tip.subtract(dir.scale(length / 2));
		Vec3 side = dir.cross(draw.cam.subtract(centre));
		side = side.lengthSqr() < 1.0E-6 ? new Vec3(1, 0, 0) : side.normalize();
		// The square sprite: its width is its height; the point at v = 0
		draw.paint(SWORD, centre, side.scale(length / 2), dir.scale(-length / 2), shade, shade, shade, alpha, glow);
	}

	private static float smooth(float t) {
		t = Math.max(0, Math.min(1, t));
		return t * t * (3 - 2 * t);
	}

	private static Vec3 top(Entity e, float partial) {
		return e.getPosition(partial).add(0, e.getBbHeight() + 0.5, 0);
	}

	private static Vec3 middle(Entity e, float partial) {
		return e.getPosition(partial).add(0, e.getBbHeight() * 0.55, 0);
	}

	static Vec3 hand(Entity e, float partial) {
		Vec3 look = e.getViewVector(partial);
		Vec3 side = new Vec3(-look.z, 0, look.x);
		side = side.lengthSqr() < 1.0E-6 ? Vec3.ZERO : side.normalize();
		Minecraft mc = Minecraft.getInstance();
		boolean own = e == mc.player && mc.options.getCameraType().isFirstPerson();
		return e.getEyePosition(partial).add(look.scale(own ? 0.5 : 0.3)).add(side.scale(own ? 0.3 : 0.38)).add(0, own ? -0.3 : -0.55, 0);
	}

	/** A spinning gold piece flying on its own: a coin or a shard, its face turning to and from the eye. */
	static void spinning(FxDraw draw, String tex, Vec3 at, float size, double phase, double roll, float alpha) {
		Vec3[] f = facing();
		double w = Math.max(0.12, Math.abs(Math.cos(phase)));
		Vec3 u = f[0].scale(Math.cos(roll)).add(f[1].scale(Math.sin(roll))).scale(size * w);
		Vec3 v = f[1].scale(Math.cos(roll)).subtract(f[0].scale(Math.sin(roll))).scale(-size);
		draw.paint(tex, at, u, v, 1, 1, 1, alpha, GLINT);
	}

	// ---- loose gold: coins and shards --------------------------------------------------------------------------------

	/**
	 * Pieces of gold flung from a point: each spins, falls, bounces off the ground with a puff of dust, lies a moment
	 * and fades. Coins, or (shards) jagged bits of a broken seal or sword.
	 */
	static final class CoinBurst extends VoidRender.Effect {
		private static final class Piece {
			Vec3 pos;
			Vec3 prev;
			Vec3 vel;
			double spin;
			double phase;
			double roll;
			int resting;
			float size;
			boolean shard;
		}

		final List<Piece> pieces = new ArrayList<>();
		final double ground;

		CoinBurst(Vec3 at, int coins, double speed, double up) {
			this(at, coins, 0, speed, up);
		}

		CoinBurst(Vec3 at, int coins, int shards, double speed, double up) {
			super(70);
			this.ground = BloodFx.groundY(at) + 0.04;
			for (int i = 0; i < coins + shards; i++) {
				Piece c = new Piece();
				Vec3 d = VoidRender.randomDir();
				c.pos = at;
				c.prev = at;
				c.shard = i >= coins;
				c.vel = new Vec3(d.x * speed, Math.abs(d.y) * speed * 0.9 + up, d.z * speed);
				c.spin = r(0.35, 0.8) * (VoidRender.RANDOM.nextBoolean() ? 1 : -1);
				c.phase = r(0, Math.PI);
				c.roll = r(0, Math.PI * 2);
				c.size = c.shard ? (float) r(0.07, 0.13) : (float) r(0.08, 0.11);
				pieces.add(c);
			}
		}

		@Override
		boolean tick() {
			for (Piece c : pieces) {
				c.prev = c.pos;
				if (c.resting > 0) {
					c.resting++;
					continue;
				}
				c.vel = c.vel.scale(0.97).add(0, -0.04, 0);
				c.pos = c.pos.add(c.vel);
				c.phase += c.spin;
				c.roll += c.spin * 0.3;
				if (c.pos.y < ground) {
					c.pos = new Vec3(c.pos.x, ground, c.pos.z);
					if (Math.abs(c.vel.y) > 0.08) {
						c.vel = new Vec3(c.vel.x * 0.6, -c.vel.y * 0.42, c.vel.z * 0.6);
						if (VoidRender.RANDOM.nextFloat() < 0.35f) {
							add(dust(c.pos.add(0, 0.04, 0), new Vec3(0, 0.02, 0), 8, 0.05, 0.85f));
						}
					} else {
						c.resting = 1;
					}
				}
			}
			return super.tick();
		}

		@Override
		void draw(FxDraw draw, float partial) {
			for (Piece c : pieces) {
				Vec3 at = c.prev.lerp(c.pos, partial);
				float fade = c.resting > 0 ? Math.max(0, 1 - Math.max(0, c.resting - 20) / 20f) : 1;
				if (fade <= 0) {
					continue;
				}
				String tex = c.shard ? SHARD : COIN;
				if (c.resting > 0) {
					// Lying flat on the ground
					Vec3 u = new Vec3(Math.cos(c.roll), 0, Math.sin(c.roll)).scale(c.size);
					Vec3 v = new Vec3(-Math.sin(c.roll), 0, Math.cos(c.roll)).scale(c.size);
					draw.paint(tex, at, u, v, 0.85f, 0.85f, 0.85f, fade, 0);
				} else {
					spinning(draw, tex, at, c.size, c.phase, c.shard ? c.roll : 0, fade);
				}
			}
		}
	}

	// ---- Hunter's Brand ---------------------------------------------------------------------------------------------

	/** The bolt: a gold coin spun off the hand, flying flat and fast, shedding gold dust; it rings off what it hits. */
	static final class Bolt extends VoidRender.Effect {
		final Vec3 from;
		final Vec3 to;
		final int fly;

		Bolt(Vec3 from, Vec3 to, int fly) {
			super(fly + 2);
			this.from = from;
			this.to = to;
			this.fly = Math.max(1, fly);
		}

		Vec3 head(float partial) {
			float t = Math.min(1, life(partial) / fly);
			// A slight arc, as a flipped coin flies
			return from.lerp(to, t).add(0, Math.sin(Math.PI * t) * 0.15 * from.distanceTo(to) / 10, 0);
		}

		@Override
		boolean tick() {
			if (age < fly) {
				Vec3 h = head(1);
				for (int i = 0; i < 4; i++) {
					VoidRender.Particle p = dust(h.add(r(-0.05, 0.05), r(-0.05, 0.05), r(-0.05, 0.05)), new Vec3(0, -0.01, 0), (int) r(10, 18), r(0.04, 0.07),
							0.9f);
					p.gravity = new Vec3(0, -0.004, 0);
					add(p);
				}
			} else if (age == fly) {
				dustPuff(to, 12, 0.12);
				VoidRender.add(new CoinBurst(to, 2, 3, 0.12, 0.08));
			}
			return super.tick();
		}

		@Override
		void draw(FxDraw draw, float partial) {
			float life = life(partial);
			if (life <= fly) {
				spinning(draw, COIN, head(partial), 0.16f, life * 1.6, 0, 1);
			}
		}
	}

	/**
	 * The Brand's seal over its creature's head: a ring of engraved gold, its parts dark bronze until a blow lights them
	 * polished gold; a new part rings in with a little hop and a puff of dust. Small, slowly turning.
	 */
	static final class BrandSeal extends VoidRender.Effect {
		final Entity body;
		int charges;
		int max;
		int end;
		int popAt = -100;
		int fading = -1;

		BrandSeal(Entity body, int charges, int max, int ticks) {
			super(20 * 60 * 5);
			this.body = body;
			this.charges = charges;
			this.max = Math.max(1, max);
			this.end = ticks;
			this.popAt = 0;
		}

		void update(int charges, int max, int ticks) {
			if (charges > this.charges) {
				popAt = age;
				dustPuff(top(body, 1), 8, 0.08);
			}
			this.charges = charges;
			this.max = Math.max(1, max);
			this.end = age + ticks;
		}

		@Override
		boolean tick() {
			if (fading >= 0 && --fading <= 0 || !body.isAlive() || age > end + 2) {
				if (SEALS.get(body.getId()) == this) {
					SEALS.remove(body.getId());
				}
				return false;
			}
			return super.tick();
		}

		@Override
		void draw(FxDraw draw, float partial) {
			float life = life(partial);
			float open = VoidRender.easeOutBack(Math.min(1, life / 6f));
			float fade = fading >= 0 ? fading / 6f : Math.min(1, (end - life) / 20f + 0.3f);
			float half = (0.3f + Math.min(0.2f, body.getBbWidth() * 0.15f)) * open;
			Vec3 at = top(body, partial);
			double spin = life * 0.03;
			// The whole ring dark first, then the lit parts over it in full gold
			billboard(draw, SEAL, at, half, spin, 0.38f, 0.95f * fade, 0);
			String part = max == 4 ? SEAL_QUARTER : SEAL_PART;
			double step = Math.PI * 2 / max;
			for (int k = 0; k < Math.min(charges, max); k++) {
				float pop = k == charges - 1 ? Math.max(0, 1 - (life - popAt) / 6f) : 0;
				billboard(draw, part, at.add(0, 0.06 * pop, 0), half * (1 + 0.12f * pop), spin + k * step, 1, fade, GLINT + 0.2f * pop);
			}
		}
	}

	/** The brand bursts: its seal cracks apart into gold shards, coins shower every way, dust settles in a ring. */
	private static void blast(Vec3 at, boolean wide) {
		VoidRender.add(new Crack(at));
		VoidRender.add(new CoinBurst(at, wide ? 30 : 22, wide ? 10 : 6, wide ? 0.4 : 0.32, 0.12));
		dustPuff(at, 14, 0.18);
		dustRing(at, wide ? 3.2f : 1.8f, wide ? 28 : 18);
	}

	/** The seal cracking: its ring jerks, splits into pieces that fly out spinning and fall. */
	static final class Crack extends VoidRender.Effect {
		final Vec3 at;
		final double[] angles = new double[5];

		Crack(Vec3 at) {
			super(18);
			this.at = at;
			for (int i = 0; i < angles.length; i++) {
				angles[i] = i * Math.PI * 2 / angles.length + r(-0.25, 0.25);
			}
		}

		@Override
		void draw(FxDraw draw, float partial) {
			float life = life(partial);
			if (life < 3) {
				// A jolt: the ring shudders, whole, for a moment
				billboard(draw, SEAL, at.add(r(-0.02, 0.02), r(-0.02, 0.02), 0), 0.42f, life, 1, 1, GLINT);
				return;
			}
			float t = (life - 3) / 15f;
			for (int i = 0; i < angles.length; i++) {
				double a = angles[i];
				double d = 0.2 + 1.4 * VoidRender.easeOut(t);
				Vec3[] f = facing();
				Vec3 pos = at.add(f[0].scale(Math.cos(a) * d)).add(0, Math.sin(a) * d * 0.6 - t * t * 1.2, 0);
				spinning(draw, SHARD, pos, 0.14f, life * 0.5 + i, a + life * 0.3, 1 - t * t);
			}
		}
	}

	// ---- Guild Harpoon ----------------------------------------------------------------------------------------------

	/**
	 * The harpoon: its hook of copper and gold flies from the hand to where it bites, the chain paying out link by link
	 * behind it (slack while it flies, taut once it bites, its links turned in turn so it reads as metal), holds while the
	 * pull lasts, and snaps back when let go.
	 */
	static final class Hook extends VoidRender.Effect {
		final Entity owner;
		final Vec3 to;
		final Entity target;
		final int fly;
		int letGoAt = -1;
		Vec3 lastHead;

		Hook(Entity owner, Vec3 to, Entity target, int fly, int ticks) {
			super(ticks + 8);
			this.owner = owner;
			this.to = to;
			this.target = target;
			this.fly = Math.max(1, fly);
		}

		void letGo() {
			if (letGoAt < 0) {
				letGoAt = age;
			}
		}

		Vec3 bite(float partial) {
			return target != null && target.isAlive() ? target.getPosition(partial).add(0, target.getBbHeight() * 0.55, 0) : to;
		}

		Vec3 head(float partial) {
			float life = life(partial);
			Vec3 hand = hand(owner, partial);
			if (letGoAt >= 0) {
				float t = Math.min(1, (life - letGoAt) / 4f);
				Vec3 from = lastHead != null ? lastHead : bite(partial);
				return from.lerp(hand, t * t);
			}
			if (life < fly) {
				return hand.lerp(to, smooth(life / fly) * 0.85f + life / fly * 0.15f);
			}
			return bite(partial);
		}

		@Override
		boolean tick() {
			if (!owner.isAlive()) {
				return false;
			}
			if (age == fly && letGoAt < 0) {
				Vec3 b = bite(1);
				dustPuff(b, 10, 0.16);
				VoidRender.add(new CoinBurst(b, 0, 5, 0.15, 0.1));
			}
			if (letGoAt >= 0 && age - letGoAt > 4) {
				if (HOOKS.get(owner.getId()) == this) {
					HOOKS.remove(owner.getId());
				}
				return false;
			}
			if (letGoAt < 0) {
				lastHead = head(1);
			}
			return super.tick();
		}

		@Override
		void draw(FxDraw draw, float partial) {
			float life = life(partial);
			Vec3 hand = hand(owner, partial);
			Vec3 head = head(partial);
			Vec3 along = head.subtract(hand);
			double length = along.length();
			if (length < 0.2) {
				return;
			}
			Vec3 dir = along.scale(1 / length);
			float slack = letGoAt >= 0 ? 0.25f : life < fly ? 0.35f * (1 - life / fly) + 0.08f : 0.02f;
			Vec3 side = dir.cross(draw.cam.subtract(hand.add(head).scale(0.5)));
			side = side.lengthSqr() < 1.0E-6 ? new Vec3(1, 0, 0) : side.normalize();
			Vec3 other = dir.cross(side).normalize();
			int links = (int) Math.min(90, length / 0.2);
			for (int i = 0; i < links; i++) {
				double s0 = i / (double) links;
				double s1 = (i + 1) / (double) links;
				Vec3 p0 = hand.add(along.scale(s0)).add(0, -Math.sin(Math.PI * s0) * slack * length * 0.25, 0);
				Vec3 p1 = hand.add(along.scale(s1)).add(0, -Math.sin(Math.PI * s1) * slack * length * 0.25, 0);
				Vec3 c = p0.lerp(p1, 0.5);
				Vec3 d = p1.subtract(p0).scale(0.5 * 1.45);
				Vec3 across = (i % 2 == 0 ? side : other).scale(0.09);
				// Every other link edge-on, as a real chain hangs
				float shade = i % 2 == 0 ? 1 : 0.72f;
				draw.paint(LINK, c, d, across, shade, shade, shade, 1, i % 2 == 0 ? GLINT : 0);
			}
			Vec3 width = side.scale(0.24);
			draw.paint(HOOK, head.add(dir.scale(0.1)), width, dir.scale(0.3), 1, 1, 1, 1, GLINT);
		}
	}

	// ---- the Guild's Verdict ----------------------------------------------------------------------------------------

	/**
	 * The Verdict on its creature: the guild's warrant hanging over its head; two loops of gold chain closing round it and
	 * holding it; golden swords circling it point inwards, one for every blade still owed. A blade owed: one sword draws
	 * back, turns and drives in, and breaks into shards. The execution (see {@link Executioner}) takes what swords are
	 * left up into one great sword.
	 */
	static final class VerdictSeal extends VoidRender.Effect {
		final Entity body;
		final int blades;
		int fading = -1;
		int used;
		final int[] thrown;
		/** The age the execution took the swords (-1: not yet). */
		int executedAt = -1;

		VerdictSeal(Entity body, int ticks, int blades) {
			super(ticks + 4);
			this.body = body;
			this.blades = Math.min(7, blades);
			this.thrown = new int[this.blades];
			java.util.Arrays.fill(thrown, -1);
		}

		void lunge() {
			if (used < blades) {
				thrown[used++] = age;
			}
		}

		void release() {
			fading = 10;
		}

		double radius() {
			return 1.0 + body.getBbWidth() * 0.7;
		}

		@Override
		boolean tick() {
			if (fading >= 0 && --fading <= 0 || !body.isAlive()) {
				if (VERDICTS.get(body.getId()) == this) {
					VERDICTS.remove(body.getId());
				}
				if (!body.isAlive()) {
					// The chains fall away in links
					VoidRender.add(new CoinBurst(middle(body, 1), 0, 8, 0.2, 0.1));
				}
				return false;
			}
			for (int k = 0; k < blades; k++) {
				if (thrown[k] >= 0 && age - thrown[k] == 6) {
					Vec3 hit = middle(body, 1);
					dustPuff(hit, 12, 0.18);
					VoidRender.add(new CoinBurst(hit, 0, 6, 0.2, 0.08));
				}
			}
			return super.tick();
		}

		/** Where a circling sword stands: round the body at chest height, slowly turning, bobbing. */
		Vec3 swordAt(int k, float life, Vec3 heart) {
			double a = life * 0.045 + k * Math.PI * 2 / blades;
			return heart.add(Math.cos(a) * radius(), 0.2 + 0.08 * Math.sin(life * 0.15 + k), Math.sin(a) * radius());
		}

		boolean circling(int k) {
			return thrown[k] < 0;
		}

		@Override
		void draw(FxDraw draw, float partial) {
			float life = life(partial);
			float open = VoidRender.easeOutBack(Math.min(1, life / 8f));
			float fade = fading >= 0 ? fading / 10f : Math.min(1, (ticks - life) / 10f);
			Vec3 heart = middle(body, partial);
			Vec3 feet = body.getPosition(partial);
			// The warrant over the head, swaying, until the execution tears it
			if (executedAt < 0) {
				Vec3 over = top(body, partial).add(0, 0.25, 0);
				billboard(draw, WARRANT, over, 0.3f * open, Math.sin(life * 0.12) * 0.12, 1, fade, 0);
			}
			// Two loops of chain closing in on the body and holding it, turning against each other
			float close = smooth(Math.min(1, life / 8f));
			for (int loop = 0; loop < 2; loop++) {
				double h = body.getBbHeight() * (loop == 0 ? 0.35 : 0.7);
				double rad = (body.getBbWidth() * 0.6 + 0.15) + (1 - close) * 1.6;
				int links = (int) Math.max(10, rad * 2 * Math.PI / 0.18);
				double turn = life * (loop == 0 ? 0.02 : -0.025);
				Vec3 c = feet.add(0, h, 0);
				for (int i = 0; i < links; i++) {
					double a0 = turn + Math.PI * 2 * i / links;
					double a1 = turn + Math.PI * 2 * (i + 1) / links;
					Vec3 p0 = c.add(Math.cos(a0) * rad, 0, Math.sin(a0) * rad);
					Vec3 p1 = c.add(Math.cos(a1) * rad, 0, Math.sin(a1) * rad);
					Vec3 mid = p0.lerp(p1, 0.5);
					Vec3 d = p1.subtract(p0).scale(0.5 * 1.45);
					Vec3 out = new Vec3(Math.cos((a0 + a1) / 2), 0, Math.sin((a0 + a1) / 2));
					Vec3 across = (i % 2 == 0 ? new Vec3(0, 1, 0) : out).scale(0.08);
					float shade = i % 2 == 0 ? 1 : 0.7f;
					draw.paint(LINK, mid, d, across, shade, shade, shade, fade * close, 0);
				}
			}
			// The swords
			for (int k = 0; k < blades; k++) {
				Vec3 at = swordAt(k, life, heart);
				Vec3 dir = heart.subtract(at).normalize();
				float alpha = fade * open;
				float len = 1.35f + body.getBbHeight() * 0.15f;
				if (thrown[k] >= 0) {
					float t = (life - thrown[k]) / 6f;
					if (t > 1.35f) {
						continue;
					}
					// Drawn back and raised a little, a beat, then driven in point first; it breaks as it lands
					Vec3 back = at.subtract(dir.scale(0.45)).add(0, 0.3, 0);
					Vec3 pos = t < 0.4f ? at.lerp(back, smooth(t / 0.4f)) : back.lerp(heart.subtract(dir.scale(len * 0.35)), Math.min(1, (t - 0.4f) / 0.6f));
					at = pos;
					dir = heart.subtract(at).normalize();
					alpha *= t < 1 ? 1 : Math.max(0, 1 - (t - 1) / 0.35f);
				} else if (executedAt >= 0) {
					// Taken up to make the great sword
					float t = Math.min(1, (life - executedAt) / 8f);
					Vec3 up = top(body, partial).add(0, 1.6, 0);
					at = at.lerp(up, smooth(t));
					dir = new Vec3(0, -1, 0).lerp(dir, 1 - t).normalize();
					alpha *= 1 - t * t;
					len *= 1 - 0.4f * t;
				}
				sword(draw, at.add(dir.scale(len * 0.5)), dir, len, 1, alpha, GLINT);
			}
		}
	}

	/** A sword's stroke at a creature from one side (the banner's blades; a Verdict without its seal). */
	static final class Swing extends VoidRender.Effect {
		final Entity body;
		final double angle;
		final float scale;
		final int stroke;

		Swing(Entity body, double angle, float scale, int stroke) {
			super(stroke + 6);
			this.body = body;
			this.angle = angle;
			this.scale = scale;
			this.stroke = stroke;
		}

		@Override
		boolean tick() {
			if (age == stroke - 1 && body.isAlive()) {
				Vec3 hit = middle(body, 1);
				dustPuff(hit, 8, 0.16);
				VoidRender.add(new CoinBurst(hit, 0, 3, 0.16, 0.06));
			}
			return super.tick();
		}

		@Override
		void draw(FxDraw draw, float partial) {
			if (!body.isAlive() && age < stroke) {
				return;
			}
			float life = life(partial);
			Vec3 heart = middle(body, partial);
			Vec3 radial = new Vec3(Math.cos(angle), 0, Math.sin(angle));
			Vec3 hilt = heart.add(radial.scale(0.8 + body.getBbWidth() * 0.6)).add(0, 0.3, 0);
			float len = (0.9f + body.getBbHeight() * 0.15f) * scale;
			float t = smooth(Math.min(1, life / stroke));
			// From raised high to cutting down through the body
			double theta = -0.2 + t * 2.1;
			Vec3 dir = new Vec3(0, Math.cos(theta), 0).subtract(radial.scale(Math.sin(theta))).normalize();
			float alpha = life < stroke ? Math.min(1, life / 2f) : Math.max(0, 1 - (life - stroke) / 6f);
			sword(draw, hilt.add(dir.scale(len)), dir, len, 1, alpha, GLINT);
		}
	}

	/**
	 * The execution: what swords are left rise over the condemned and fuse into a great golden sword (it swells and turns
	 * point down), it hangs a beat and falls through the body; the chains snap into flying links, gold shards and coins
	 * burst out, dust rolls out along the ground, the warrant is torn to scraps; the sword stands a moment and crumbles.
	 */
	static final class Executioner extends VoidRender.Effect {
		final Entity body;
		final VerdictSeal seal;
		final Vec3 rest;
		final double[] scraps = new double[6];
		static final int GATHER = 8;
		static final int HANG = 5;
		static final int FALL = 3;
		static final int STAND = 14;

		Executioner(Entity body, VerdictSeal seal) {
			super(GATHER + HANG + FALL + STAND + 8);
			this.body = body;
			this.seal = seal;
			this.rest = middle(body, 1);
			if (seal != null) {
				seal.executedAt = seal.age;
			}
			for (int i = 0; i < scraps.length; i++) {
				scraps[i] = i * Math.PI * 2 / scraps.length + r(-0.3, 0.3);
			}
		}

		private Vec3 heart(float partial) {
			return body.isAlive() ? middle(body, partial) : rest;
		}

		@Override
		boolean tick() {
			int strike = GATHER + HANG + FALL;
			if (age == strike) {
				Vec3 hit = heart(1);
				VoidRender.add(new CoinBurst(hit, 16, 14, 0.36, 0.14));
				dustPuff(hit, 20, 0.25);
				dustRing(hit, 3f, 30);
				if (seal != null) {
					seal.release();
				}
				Minecraft.getInstance().getSoundManager().play(new SimpleSoundInstance(SoundEvents.CHAIN_BREAK, SoundSource.PLAYERS, 1, 0.7f,
						RandomSource.create(), hit.x, hit.y, hit.z));
			}
			if (age == strike + STAND) {
				dustPuff(heart(1).add(0, 0.8, 0), 24, 0.12);
			}
			return super.tick();
		}

		@Override
		void draw(FxDraw draw, float partial) {
			float life = life(partial);
			Vec3 heart = heart(partial);
			Vec3 above = top(body, partial).add(0, 2.2, 0);
			float full = 2.6f + body.getBbHeight() * 0.3f;
			if (life < GATHER + HANG) {
				// Fusing: it swells from the swords drawn up into it, turning point down
				float t = Math.min(1, life / GATHER);
				float len = full * (0.35f + 0.65f * VoidRender.easeOutBack(t));
				// From point up to point down, turning in the plane across the eye
				double turn = Math.PI * smooth(t);
				Vec3[] f = facing();
				Vec3 dir = new Vec3(0, Math.cos(turn), 0).add(new Vec3(f[0].x, 0, f[0].z).normalize().scale(Math.sin(turn))).normalize();
				Vec3 tip = above.add(dir.scale(len * 0.5));
				// A shiver while it hangs
				if (life >= GATHER) {
					tip = tip.add(r(-0.02, 0.02), 0, r(-0.02, 0.02));
				}
				sword(draw, tip, dir, len, 1, Math.min(1, life / 3f), GLINT + 0.05f);
				return;
			}
			float fall = Math.min(1, (life - GATHER - HANG) / FALL);
			float after = Math.max(0, life - GATHER - HANG - FALL);
			float alpha = after < STAND ? 1 : Math.max(0, 1 - (after - STAND) / 6f);
			Vec3 tipFrom = above.add(0, -full * 0.5, 0);
			Vec3 tipTo = heart.add(0, -body.getBbHeight() * 0.35, 0);
			Vec3 tip = tipFrom.lerp(tipTo, fall * fall);
			sword(draw, tip, new Vec3(0, -1, 0), full, after > STAND ? 0.8f : 1, alpha, GLINT);
			// The warrant torn: scraps fluttering out and down
			if (after > 0 && after < 22) {
				float t = after / 22f;
				Vec3 over = top(body, partial).add(0, 0.25, 0);
				for (int i = 0; i < scraps.length; i++) {
					double a = scraps[i];
					float d = 0.3f + 1.4f * VoidRender.easeOut(t);
					Vec3 p = over.add(Math.cos(a) * d, -t * t * 1.4 + 0.2 * Math.sin(a * 3 + after * 0.4), Math.sin(a) * d);
					billboard(draw, CONTRACT, p, 0.11f, a + after * 0.3, 1, (1 - t), 0);
				}
				// Snapped chain links flying off
				for (int i = 0; i < 10; i++) {
					double a = i * Math.PI * 2 / 10 + scraps[i % scraps.length];
					float d = 0.4f + 2.2f * VoidRender.easeOut(t);
					Vec3 p = heart.add(Math.cos(a) * d, body.getBbHeight() * (i % 2 == 0 ? -0.2 : 0.15) + 0.6 * t - 1.5 * t * t, Math.sin(a) * d);
					spinning(draw, LINK, p, 0.09f, after * 0.7 + i, a, 1 - t);
				}
			}
		}
	}

	/** A pillar over a contract's mark: gold dust rising in a slow column, a faint amber haze to find it from afar. */
	static final class Pillar extends VoidRender.Effect {
		final Vec3 at;
		final boolean bounty;
		double ground = Double.NaN;

		Pillar(Vec3 at, boolean bounty, int ticks) {
			super(ticks);
			this.at = at;
			this.bounty = bounty;
		}

		private Vec3 foot() {
			if (Double.isNaN(ground)) {
				Minecraft mc = Minecraft.getInstance();
				ground = mc.level == null ? at.y
						: mc.level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, (int) Math.floor(at.x), (int) Math.floor(at.z));
			}
			return new Vec3(at.x, ground, at.z);
		}

		@Override
		boolean tick() {
			Vec3 foot = foot();
			for (int i = 0; i < 3; i++) {
				double a = r(0, Math.PI * 2);
				double d = r(0, 0.7);
				VoidRender.Particle p = dust(foot.add(Math.cos(a) * d, r(0, 6), Math.sin(a) * d), new Vec3(0, r(0.04, 0.09), 0), (int) r(30, 50), r(0.08, 0.14),
						0.995f);
				add(p);
			}
			return super.tick();
		}

		@Override
		void draw(FxDraw draw, float partial) {
			float t = progress(partial);
			float alpha = (float) Math.sin(Math.PI * t);
			Vec3 foot = foot();
			Vec3 top = foot.add(0, 40, 0);
			Vec3 side = top.subtract(foot).cross(draw.cam.subtract(foot.lerp(top, 0.5)));
			if (side.lengthSqr() < 1.0E-6) {
				return;
			}
			side = side.normalize().scale(0.45);
			// A thin amber haze, more like dust in the air than a ray
			draw.paintStrip(WARM, new Vec3[] {foot.subtract(side), top.subtract(side), top.add(side), foot.add(side)}, new float[] {0.5f, 0.5f, 0.5f, 0.5f},
					new float[] {0.5f, 0.5f, 0.5f, 0.5f}, 0.85f, bounty ? 0.45f : 0.62f, bounty ? 0.25f : 0.3f, alpha * 0.28f, 0.05f);
			decal(draw, SIGIL, foot.add(0, 0.05, 0), 1.8f, t * 2, 0.8f, alpha * 0.8f, 0);
		}
	}

	// ---- the Ransom -------------------------------------------------------------------------------------------------

	/** The Life Debt: sealed contracts on a gold chain turning round the ward's waist. */
	static final class DebtSeal extends VoidRender.Effect {
		final Entity body;
		int fading = -1;

		DebtSeal(Entity body, int ticks) {
			super(ticks + 4);
			this.body = body;
		}

		@Override
		boolean tick() {
			if (fading >= 0 && --fading <= 0 || !body.isAlive()) {
				if (DEBTS.get(body.getId()) == this) {
					DEBTS.remove(body.getId());
				}
				return false;
			}
			return super.tick();
		}

		@Override
		void draw(FxDraw draw, float partial) {
			float life = life(partial);
			float open = VoidRender.easeOutBack(Math.min(1, life / 8f));
			float fade = fading >= 0 ? fading / 8f : Math.min(1, (ticks - life) / 8f);
			Vec3 waist = body.getPosition(partial).add(0, body.getBbHeight() * 0.5, 0);
			double radius = (0.45 + body.getBbWidth() * 0.6) * open;
			int links = (int) Math.max(12, radius * 2 * Math.PI / 0.18);
			double turn = life * 0.06;
			for (int i = 0; i < links; i++) {
				double a0 = turn + Math.PI * 2 * i / links;
				double a1 = turn + Math.PI * 2 * (i + 1) / links;
				Vec3 p0 = waist.add(Math.cos(a0) * radius, 0, Math.sin(a0) * radius);
				Vec3 p1 = waist.add(Math.cos(a1) * radius, 0, Math.sin(a1) * radius);
				Vec3 out = new Vec3(Math.cos((a0 + a1) / 2), 0, Math.sin((a0 + a1) / 2));
				Vec3 across = (i % 2 == 0 ? new Vec3(0, 1, 0) : out).scale(0.07);
				float shade = i % 2 == 0 ? 1 : 0.7f;
				draw.paint(LINK, p0.lerp(p1, 0.5), p1.subtract(p0).scale(0.72), across, shade, shade, shade, fade, 0);
			}
			for (int i = 0; i < 4; i++) {
				double a = turn + i * Math.PI / 2;
				Vec3 out = new Vec3(Math.cos(a), 0, Math.sin(a));
				Vec3 tangent = new Vec3(-out.z, 0, out.x);
				Vec3 c = waist.add(out.scale(radius + 0.02)).add(0, -0.14, 0);
				draw.paint(CONTRACT, c, tangent.scale(0.17), new Vec3(0, -0.085, 0), 1, 1, 1, fade, 0);
			}
		}
	}

	/** The Debt paid off: coins and shards burst from the body, dust rolls out over the wave's reach. */
	private static void debtWave(Vec3 at, float radius) {
		Vec3 heart = at.add(0, 1, 0);
		VoidRender.add(new CoinBurst(heart, 20, 6, 0.3, 0.16));
		dustRing(at, radius, 36);
		dustPuff(heart, 12, 0.2);
	}

	/** The Debt come due: the chain snaps, its contracts torn off, dark red dust. */
	private static void due(Entity body) {
		Vec3 at = middle(body, 1);
		VoidRender.add(new CoinBurst(at, 0, 8, 0.25, 0.1));
		for (int i = 0; i < 18; i++) {
			VoidRender.Particle p = new VoidRender.Particle(at, VoidRender.randomDir().scale(r(0.1, 0.22)), (int) r(10, 18), (float) r(0.05, 0.09), 0xFF8A2A1E,
					false, 0.88f, 0);
			p.tex = DUST;
			p.gravity = new Vec3(0, -0.02, 0);
			add(p);
		}
	}

	/** The Last Ransom: a great gold seal stamped down behind the body, a fountain of coins, dust. */
	private static void ransom(Entity body) {
		VoidRender.add(new Warrant(body));
		Vec3 at = middle(body, 1);
		VoidRender.add(new CoinBurst(at, 40, 8, 0.38, 0.22));
		dustRing(body.position(), 3.5f, 36);
	}

	static final class Warrant extends VoidRender.Effect {
		final Entity body;

		Warrant(Entity body) {
			super(30);
			this.body = body;
		}

		@Override
		void draw(FxDraw draw, float partial) {
			float life = life(partial);
			float t = progress(partial);
			Vec3 at = middle(body, partial);
			// Stamped: it comes down big and settles, then fades
			float press = life < 5 ? 1.6f - 0.6f * smooth(life / 5f) : 1;
			billboard(draw, SEAL, at, 1.1f * press, life * 0.05, 1, (1 - t * t), GLINT);
		}
	}

	// ---- the Guild Legend -------------------------------------------------------------------------------------------

	/**
	 * The banner: a pole of gold, the guild's crimson cloth with its gold coin and sword waving from it (turned round the
	 * pole to whoever looks), a seal of gold laid in the ground at its foot; gold dust lifts off the circle's edge.
	 */
	static final class Banner extends VoidRender.Effect {
		final Vec3 at;
		final float radius;
		int end;
		int fading = -1;

		Banner(Vec3 at, float radius, int ticks) {
			super(20 * 60);
			this.at = new Vec3(at.x, BloodFx.groundY(at), at.z);
			this.radius = radius;
			this.end = ticks;
			dustRing(this.at, radius, 48);
			VoidRender.add(new CoinBurst(this.at.add(0, 3, 0), 10, 0.15, 0.1));
		}

		void extend(int ticks) {
			end = age + ticks;
			dustPuff(at.add(0, 3.2, 0), 14, 0.2);
		}

		@Override
		boolean tick() {
			if (fading >= 0 && --fading <= 0 || fading < 0 && age > end + 20) {
				return false;
			}
			if (age % 2 == 0 && fading < 0) {
				double a = r(0, Math.PI * 2);
				Vec3 p = at.add(Math.cos(a) * radius, 0.1, Math.sin(a) * radius);
				add(dust(p, new Vec3(0, r(0.02, 0.05), 0), (int) r(20, 34), r(0.05, 0.09), 0.99f));
			}
			return super.tick();
		}

		@Override
		void draw(FxDraw draw, float partial) {
			float life = life(partial);
			float rise = VoidRender.easeOutBack(Math.min(1, life / 10f));
			float fade = fading >= 0 ? fading / 14f : Math.min(1, (end + 20 - life) / 20f);
			Vec3 foot = at;
			Vec3 head = at.add(0, 3.6 * rise, 0);
			band(draw, POLE, foot, head, 0.14f, fade, 0);
			Vec3 toCam = draw.cam.subtract(at);
			Vec3 flat = new Vec3(toCam.x, 0, toCam.z);
			flat = flat.lengthSqr() < 1.0E-6 ? new Vec3(0, 0, 1) : flat.normalize();
			Vec3 across = new Vec3(-flat.z, 0, flat.x);
			float width = 1.6f;
			float height = 2.35f * rise;
			Vec3 topLeft = head.add(across.scale(-0.05)).add(0, -0.05, 0);
			int segments = 6;
			for (int i = 0; i < segments; i++) {
				float u0 = i / (float) segments;
				float u1 = (i + 1) / (float) segments;
				double w0 = Math.sin(life * 0.18 - u0 * 3.5) * 0.14 * u0;
				double w1 = Math.sin(life * 0.18 - u1 * 3.5) * 0.14 * u1;
				Vec3 a0 = topLeft.add(across.scale(u0 * width)).add(flat.scale(w0));
				Vec3 a1 = topLeft.add(across.scale(u1 * width)).add(flat.scale(w1));
				Vec3 b0 = a0.add(0, -height, 0);
				Vec3 b1 = a1.add(0, -height, 0);
				// The cloth takes a little shade where it folds away
				float shade = 0.82f + 0.18f * (float) Math.cos(life * 0.18 - u0 * 3.5);
				draw.paintStrip(BANNER, new Vec3[] {a0, a1, b1, b0}, new float[] {u0, u1, u1, u0}, new float[] {0, 0, 1, 1}, shade, shade, shade, fade, 0);
			}
			decal(draw, SEAL, foot.add(0, 0.03, 0), 1.3f * rise, life * 0.01, 0.9f, 0.9f * fade, 0);
			decal(draw, SIGIL, foot.add(0, 0.02, 0), radius, -life * 0.004, 0.75f, 0.35f * fade, 0);
		}
	}

	// ---- the Mage's Shop and the contracts ---------------------------------------------------------------------------

	/**
	 * The Shop's door, as in the first version: an arch of gold traces itself in the air before the Mercenary, two leaves
	 * of oak and copper swing open on the warm inside, coins drift out; it stays open while the Mercenary is in the Shop,
	 * then swings shut and fades.
	 */
	static final class Door extends VoidRender.Effect {
		private static final int TRACE = 8;
		private static final int OPEN = 12;
		private static final int CLOSE = 14;
		final Vec3 at;
		final Vec3 forward;
		final Vec3 right;
		int closingAt = -1;

		Door(Vec3 at, float yaw, int ticks) {
			super(ticks);
			this.at = new Vec3(at.x, BloodFx.groundY(at), at.z);
			double r = Math.toRadians(yaw);
			this.forward = new Vec3(-Math.sin(r), 0, Math.cos(r));
			this.right = new Vec3(Math.cos(r), 0, Math.sin(r));
		}

		void close() {
			if (closingAt < 0) {
				closingAt = age;
			}
		}

		@Override
		boolean tick() {
			if (closingAt >= 0 && age - closingAt >= CLOSE) {
				DOORS.remove(this);
				return false;
			}
			float open = openness(1);
			if (open > 0.5f && age % 5 == 0 && closingAt < 0) {
				Vec3 p = at.add(right.scale(r(-0.6, 0.6))).add(0, r(0.4, 2.2), 0);
				VoidRender.Particle coin = new VoidRender.Particle(p, forward.scale(-0.05).add(0, 0.01, 0), 30, 0.07f, 0xFFFFFFFF, false, 0.98f, 0);
				coin.tex = COIN_SPRITE;
				add(coin);
			}
			boolean alive = super.tick();
			if (!alive) {
				DOORS.remove(this);
			}
			return alive;
		}

		float openness(float partial) {
			float life = life(partial);
			float open = VoidRender.easeOut(Math.max(0, Math.min(1, (life - TRACE) / OPEN)));
			if (closingAt >= 0) {
				open = Math.min(open, 1 - smooth((life - closingAt) / (CLOSE * 0.7f)));
			}
			return open;
		}

		@Override
		void draw(FxDraw draw, float partial) {
			float life = life(partial);
			double w = 1.8;
			double hs = 1.6;
			float fade = closingAt >= 0 ? 1 - smooth((life - closingAt - CLOSE * 0.5f) / (CLOSE * 0.5f)) : 1;
			// The arch traced in gold: two posts and a round top, drawn piece by piece
			List<Vec3> frame = new ArrayList<>();
			frame.add(at.add(right.scale(-w / 2)));
			frame.add(at.add(right.scale(-w / 2)).add(0, hs, 0));
			for (int i = 1; i < 14; i++) {
				double a = Math.PI - Math.PI * i / 14;
				frame.add(at.add(right.scale(Math.cos(a) * w / 2)).add(0, hs + Math.sin(a) * w / 2 * 0.6, 0));
			}
			frame.add(at.add(right.scale(w / 2)).add(0, hs, 0));
			frame.add(at.add(right.scale(w / 2)));
			float traced = Math.min(1, life / TRACE);
			int shown = Math.max(1, (int) Math.ceil((frame.size() - 1) * traced));
			for (int i = 0; i < shown; i++) {
				band(draw, POLE, frame.get(i), frame.get(i + 1), 0.13f, fade, 0);
			}
			float open = openness(partial);
			if (traced < 1) {
				return;
			}
			// The warm inside: a soft amber haze within the frame while it stands open
			if (open > 0) {
				Vec3 mid = at.add(0, hs * 0.55, 0);
				draw.paint(WARM, mid.subtract(forward.scale(0.02)), right.scale(w / 2 * 0.95), new Vec3(0, -hs * 0.6, 0), 0.95f, 0.6f, 0.3f,
						0.3f * open * fade, 0.02f);
			}
			// Two leaves on their hinges at the posts, swinging inwards (away from the Mercenary)
			double angle = Math.toRadians(100) * open;
			for (int side = -1; side <= 1; side += 2) {
				Vec3 hinge = at.add(right.scale(side * w / 2));
				Vec3 along = right.scale(-side * Math.cos(angle)).add(forward.scale(-Math.sin(angle)));
				Vec3 centre = hinge.add(along.scale(w / 4)).add(0, 1.05, 0);
				// The left leaf's picture as it is, the right one mirrored (u runs back from its hinge)
				float shade = 0.75f + 0.25f * (float) Math.cos(angle);
				Vec3 halfU = side < 0 ? along.scale(w / 4) : along.scale(-w / 4);
				draw.paint(DOOR, centre, halfU, new Vec3(0, -1.05, 0), shade, shade, shade, fade, 0);
			}
		}
	}

	/** A contract done: a gold seal stamped over the head. */
	static final class Stamp extends VoidRender.Effect {
		final Entity body;

		Stamp(Entity body) {
			super(24);
			this.body = body;
		}

		@Override
		void draw(FxDraw draw, float partial) {
			float life = life(partial);
			float t = progress(partial);
			float press = life < 4 ? 1.6f - 0.6f * smooth(life / 4f) : 1;
			Vec3 over = top(body, partial).add(0, 0.2, 0);
			billboard(draw, SEAL, over, 0.42f * press, 0, 1, (1 - t * t), GLINT);
			for (int k = 0; k < 3; k++) {
				billboard(draw, SEAL_PART, over, 0.42f * press, k * Math.PI * 2 / 3, 1, (1 - t * t), GLINT);
			}
		}
	}
}
