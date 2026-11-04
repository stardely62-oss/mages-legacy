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
import net.minecraft.world.phys.Vec3;

/**
 * The Mercenaries' effects, all drawn by the clients from the server's word: painted quads in copper and dark gold
 * (tools/gen_merc_fx.py: seals, a warrant, a blade, chain links, a hook, a banner, a door) glowing into the bloom,
 * pixel coins and sparks. Seals over creatures are kept by the creature (a new word from the server replaces the old),
 * so a brand's charges light up part by part; the harpoon's chain is drawn link by link, sagging while the hook flies
 * and pulled taut once it bites. Everything stays small and see-through near a creature so the creature stays seen.
 */
public final class MercFx {
	static final String SEAL = "fx/merc_seal";
	static final String SEAL_PART = "fx/merc_seal_part";
	static final String VERDICT = "fx/merc_verdict";
	static final String BLADE = "fx/merc_blade";
	static final String LINK = "fx/merc_link";
	static final String HOOK = "fx/merc_hook";
	static final String COIN = "fx/sprite_merc_coin";
	static final String SPARK = "fx/sprite_merc_spark";
	static final String BANNER = "fx/merc_banner";
	static final String SIGIL = "fx/merc_sigil";
	static final String WAVE = "fx/merc_wave";
	static final String FLARE = "fx/merc_flare";
	static final String DOOR = "fx/merc_door";
	static final String CONTRACT = "fx/merc_contract";
	static final String WARRANT = "fx/merc_warrant";
	/** Tints laid over the sparks: pale gold, dark gold, copper. */
	private static final int[] GOLDS = {0xFFFFF2C4, 0xFFE2B04A, 0xFFC8823C};
	private static final int CAP = 900;

	/** Brand seals by the creature they hang over. */
	private static final Map<Integer, BrandSeal> SEALS = new HashMap<>();
	private static final Map<Integer, Hook> HOOKS = new HashMap<>();
	private static final Map<Integer, VerdictSeal> VERDICTS = new HashMap<>();
	private static final Map<Integer, DebtSeal> DEBTS = new HashMap<>();
	private static final Map<Integer, Banner> BANNERS = new HashMap<>();
	private static final java.util.List<Door> DOORS = new java.util.ArrayList<>();

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
						// Burst (a < 0): gone at once, the burst draws its own fold; run out: a short fade
						old.fading = payload.a() < 0 ? 1 : 6;
						SEALS.remove(follow.getId());
					}
					return;
				}
				if (old != null && old.age < old.ticks && old.fading < 0) {
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
					old.fading = 10;
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
						VoidRender.add(new Swing(follow, null, VoidRender.rand(0, Math.PI * 2), 1.0f, 9));
					}
				}
			}
			case Fx.VERDICT_EXECUTE -> {
				if (follow != null) {
					VerdictSeal seal = VERDICTS.get(follow.getId());
					if (seal != null) {
						seal.executed = true;
					}
					execute(follow);
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
					double a = Math.atan2(d.z, d.x) + Math.PI;
					VoidRender.add(new Swing(follow, payload.from(), a, 0.8f, 8));
				}
			}
			case Fx.DOOR -> {
				if (payload.ticks() > 0) {
					Door door = new Door(payload.from(), payload.a(), payload.ticks());
					DOORS.add(door);
					VoidRender.add(door);
				} else if (follow != null) {
					Door nearest = null;
					double best = 49;
					for (Door d : DOORS) {
						double dist = d.at.distanceToSqr(follow.position());
						if (d.closing < 0 && dist < best) {
							best = dist;
							nearest = d;
						}
					}
					if (nearest != null) {
						nearest.closing = 0;
					}
				}
			}
			case Fx.PROFIT_PING -> {
				pingAt = payload.to();
				pingLeft = payload.ticks();
				Minecraft mc = Minecraft.getInstance();
				mc.getSoundManager().play(new SimpleSoundInstance(SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.PLAYERS, 0.25f, 1.9f,
						RandomSource.create(), pingAt.x, pingAt.y, pingAt.z));
			}
			case Fx.CONTRACT_DONE -> {
				if (follow != null) {
					VoidRender.add(new Stamp(follow));
					coins(follow.position().add(0, 1.2, 0), 18, 0.18);
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

	/** A coin flung out: tumbles (its sprite is round), falls, glints. */
	private static VoidRender.Particle coin(Vec3 at, Vec3 vel, int life) {
		VoidRender.Particle p = new VoidRender.Particle(at, vel, life, (float) r(0.07, 0.11), 0xFFFFFFFF, false, 0.96f, 0.6f);
		p.tex = COIN;
		p.gravity = new Vec3(0, -0.035, 0);
		return p;
	}

	/** A spark of gold or copper: quick, bright, gone. */
	private static VoidRender.Particle spark(Vec3 at, Vec3 vel, int life, double size) {
		return spark(at, vel, life, size, 0.86f);
	}

	private static VoidRender.Particle spark(Vec3 at, Vec3 vel, int life, double size, float drag) {
		VoidRender.Particle p = new VoidRender.Particle(at, vel, life, (float) size, GOLDS[VoidRender.RANDOM.nextInt(GOLDS.length)], false, drag, 1.2f);
		p.tex = SPARK;
		return p;
	}

	static void coins(Vec3 at, int count, double speed) {
		for (int i = 0; i < count; i++) {
			Vec3 d = VoidRender.randomDir();
			add(coin(at, new Vec3(d.x * speed, Math.abs(d.y) * speed + 0.12, d.z * speed), (int) r(24, 40)));
		}
	}

	private static void sparks(Vec3 at, int count, double speed) {
		for (int i = 0; i < count; i++) {
			add(spark(at, VoidRender.randomDir().scale(r(speed * 0.4, speed)), (int) r(8, 16), r(0.05, 0.1)));
		}
	}

	private static Vec3[] facing() {
		Camera camera = Minecraft.getInstance().gameRenderer.getMainCamera();
		return new Vec3[] {new Vec3(camera.getLeftVector().x(), camera.getLeftVector().y(), camera.getLeftVector().z()),
				new Vec3(camera.getUpVector().x(), camera.getUpVector().y(), camera.getUpVector().z())};
	}

	/** A painted quad facing the camera, turned by roll. */
	static void billboard(FxDraw draw, String tex, Vec3 at, float half, double roll, float bright, float a, float glow) {
		Vec3[] f = facing();
		Vec3 u = f[0].scale(Math.cos(roll)).add(f[1].scale(Math.sin(roll))).scale(half);
		Vec3 v = f[1].scale(Math.cos(roll)).subtract(f[0].scale(Math.sin(roll))).scale(half);
		draw.paint(tex, at, u, v, bright, bright, bright, a, glow);
	}

	/** The same, tinted. */
	static void billboard(FxDraw draw, String tex, Vec3 at, float half, double roll, float r, float g, float b, float a, float glow) {
		Vec3[] f = facing();
		Vec3 u = f[0].scale(Math.cos(roll)).add(f[1].scale(Math.sin(roll))).scale(half);
		Vec3 v = f[1].scale(Math.cos(roll)).subtract(f[0].scale(Math.sin(roll))).scale(half);
		draw.paint(tex, at, u, v, r, g, b, a, glow);
	}

	/** A painted quad lying flat at a height, turned by an angle. */
	static void decal(FxDraw draw, String tex, Vec3 at, float half, double angle, float bright, float a, float glow) {
		Vec3 u = new Vec3(Math.cos(angle) * half, 0, Math.sin(angle) * half);
		Vec3 v = new Vec3(-Math.sin(angle) * half, 0, Math.cos(angle) * half);
		draw.paint(tex, at, u, v, bright, bright, bright, a, glow);
	}

	/** A band from a to b facing the camera, the texture along it. */
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

	private static float smooth(float t) {
		t = Math.max(0, Math.min(1, t));
		return t * t * (3 - 2 * t);
	}

	private static Vec3 top(Entity e, float partial) {
		return e.getPosition(partial).add(0, e.getBbHeight() + 0.55, 0);
	}

	private static Vec3 middle(Entity e, float partial) {
		return e.getPosition(partial).add(0, e.getBbHeight() * 0.55, 0);
	}

	/** Where a player's right hand is, near enough (the harpoon's chain starts here). */
	static Vec3 hand(Entity e, float partial) {
		Vec3 look = e.getViewVector(partial);
		Vec3 side = new Vec3(-look.z, 0, look.x);
		side = side.lengthSqr() < 1.0E-6 ? Vec3.ZERO : side.normalize();
		Minecraft mc = Minecraft.getInstance();
		boolean own = e == mc.player && mc.options.getCameraType().isFirstPerson();
		return e.getEyePosition(partial).add(look.scale(own ? 0.5 : 0.3)).add(side.scale(own ? 0.3 : 0.38)).add(0, own ? -0.3 : -0.55, 0);
	}

	// ---- Hunter's Brand ---------------------------------------------------------------------------------------------

	/** The bolt: a hot head trailing a streak of gold that lingers a moment along its whole way, sparks shed behind it. */
	static final class Bolt extends VoidRender.Effect {
		final Vec3 from;
		final Vec3 to;
		final int fly;

		Bolt(Vec3 from, Vec3 to, int fly) {
			super(fly + 10);
			this.from = from;
			this.to = to;
			this.fly = Math.max(1, fly);
		}

		Vec3 head(float partial) {
			return from.lerp(to, Math.min(1, life(partial) / fly));
		}

		@Override
		boolean tick() {
			if (age < fly) {
				Vec3 h = head(1);
				for (int i = 0; i < 4; i++) {
					add(spark(h.add(r(-0.08, 0.08), r(-0.08, 0.08), r(-0.08, 0.08)), VoidRender.randomDir().scale(0.03), (int) r(8, 16), r(0.04, 0.08)));
				}
			} else if (age == fly) {
				sparks(to, 16, 0.2);
				coins(to, 3, 0.1);
			}
			return super.tick();
		}

		@Override
		void draw(FxDraw draw, float partial) {
			float life = life(partial);
			Vec3 h = head(partial);
			// The streak stays where the bolt went, thinning and fading after it lands
			float linger = life <= fly ? 1 : Math.max(0, 1 - (life - fly) / 10f);
			if (h.distanceToSqr(from) > 0.01) {
				band(draw, FLARE, from, h, 0.12f * linger + 0.02f, 0.55f * linger, 0.5f);
				band(draw, FLARE, from.lerp(h, 0.6), h, 0.22f * linger, 0.8f * linger, 0.8f);
			}
			if (life <= fly) {
				billboard(draw, FLARE, h, 0.25f, 0, 1, 1, 1.0f);
				billboard(draw, SEAL_PART, h, 0.2f, life * 0.9, 1, 0.9f, 0.6f);
			} else {
				float t = (life - fly) / 10f;
				billboard(draw, FLARE, to, 0.35f + t * 0.3f, 0, 1, (1 - t) * 0.7f, 0.6f);
			}
		}
	}

	/**
	 * The brand's seal over the creature's head: a ring and its parts, one lit for each charge of Debt; a new charge
	 * pops its part in bright; it slowly turns, small and half see-through.
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
				Vec3 at = top(body, 1);
				sparks(at, 8, 0.1);
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
			float fade = fading >= 0 ? fading / 6f : Math.min(1, (end - life) / 20f + 0.25f);
			float half = (0.32f + Math.min(0.25f, body.getBbWidth() * 0.18f)) * open;
			Vec3 at = top(body, partial);
			double spin = life * 0.03;
			billboard(draw, SEAL, at, half, spin, 1, 0.55f * fade, 0.12f);
			double step = Math.PI * 2 / max;
			for (int k = 0; k < max; k++) {
				boolean lit = k < charges;
				float pop = lit && k == charges - 1 ? Math.max(0, 1 - (life - popAt) / 6f) : 0;
				// The part's texture covers a quarter from the top: turn it to its place
				double roll = -(spin + k * step) - (max == 3 ? Math.PI / 12 : 0);
				billboard(draw, SEAL_PART, at, half * (1 + 0.25f * pop), roll, lit ? 1 : 0.5f, (lit ? 0.95f : 0.25f) * fade, lit ? 0.35f + 0.6f * pop : 0);
			}
		}
	}

	/** The brand blows: the seal folds in, a short flash, and coins burst out every way, ringing off the ground. */
	private static void blast(Vec3 at, boolean wide) {
		VoidRender.add(new Fold(at));
		VoidRender.add(new CoinBurst(at, wide ? 34 : 24, wide ? 0.42 : 0.34));
		for (int i = 0; i < 16; i++) {
			add(spark(at, VoidRender.randomDir().scale(r(0.12, 0.3)), (int) r(8, 14), r(0.06, 0.1)));
		}
		if (wide) {
			VoidRender.add(new Ring(new Vec3(at.x, BloodFx.groundY(at) + 0.02, at.z), 0.4f, 3.2f, 12));
		}
	}

	/**
	 * Coins flung every way from a point: each spins (its face narrowing and widening), falls, bounces off the ground
	 * with a small chink of light, then lies a moment and fades.
	 */
	static final class CoinBurst extends VoidRender.Effect {
		private static final class Coin {
			Vec3 pos;
			Vec3 prev;
			Vec3 vel;
			double spin;
			double phase;
			int resting;
			float size;
		}

		final List<Coin> coins = new ArrayList<>();
		final double ground;

		CoinBurst(Vec3 at, int count, double speed) {
			super(60);
			this.ground = BloodFx.groundY(at) + 0.04;
			for (int i = 0; i < count; i++) {
				Coin c = new Coin();
				Vec3 d = VoidRender.randomDir();
				c.pos = at;
				c.prev = at;
				c.vel = new Vec3(d.x * speed, Math.abs(d.y) * speed * 0.9 + 0.12, d.z * speed);
				c.spin = r(0.35, 0.8) * (VoidRender.RANDOM.nextBoolean() ? 1 : -1);
				c.phase = r(0, Math.PI);
				c.size = (float) r(0.07, 0.11);
				coins.add(c);
			}
		}

		@Override
		boolean tick() {
			for (Coin c : coins) {
				c.prev = c.pos;
				if (c.resting > 0) {
					c.resting++;
					continue;
				}
				c.vel = c.vel.scale(0.97).add(0, -0.04, 0);
				c.pos = c.pos.add(c.vel);
				c.phase += c.spin;
				if (c.pos.y < ground) {
					c.pos = new Vec3(c.pos.x, ground, c.pos.z);
					if (Math.abs(c.vel.y) > 0.08) {
						c.vel = new Vec3(c.vel.x * 0.6, -c.vel.y * 0.45, c.vel.z * 0.6);
						if (VoidRender.RANDOM.nextFloat() < 0.3f) {
							add(spark(c.pos.add(0, 0.05, 0), new Vec3(0, 0.03, 0), 6, 0.05));
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
			Vec3[] f = facing();
			for (Coin c : coins) {
				Vec3 at = c.prev.lerp(c.pos, partial);
				float fade = c.resting > 0 ? Math.max(0, 1 - Math.max(0, c.resting - 15) / 20f) : 1;
				if (fade <= 0) {
					continue;
				}
				// Lying on the ground: flat; flying: a face turning to and from the eye
				if (c.resting > 0) {
					draw.paint(COIN, at, new Vec3(c.size, 0, 0), new Vec3(0, 0, c.size), 1, 1, 1, fade, 0.3f);
				} else {
					double w = Math.max(0.15, Math.abs(Math.cos(c.phase)));
					draw.paint(COIN, at, f[0].scale(c.size * w), f[1].scale(c.size), 1, 1, 1, fade, 0.7f);
				}
			}
		}
	}

	static final class Fold extends VoidRender.Effect {
		final Vec3 at;

		Fold(Vec3 at) {
			super(14);
			this.at = at;
		}

		@Override
		void draw(FxDraw draw, float partial) {
			float life = life(partial);
			if (life < 5) {
				float t = life / 5f;
				float half = 0.7f * (1 - t * t);
				billboard(draw, SEAL, at, half, life * 0.6, 1, 0.9f, 0.4f);
				for (int k = 0; k < 3; k++) {
					billboard(draw, SEAL_PART, at, half, -life * 0.6 - k * 2.094, 1, 0.9f, 0.6f);
				}
			}
			float t = Math.max(0, (life - 4) / 10f);
			if (life >= 4) {
				billboard(draw, FLARE, at, 0.3f + 0.5f * VoidRender.easeOut(t), 0, 1, (1 - t) * (1 - t) * 0.7f, 0.6f);
			}
		}
	}

	/** A thin ring on the ground going out (or in). */
	static final class Ring extends VoidRender.Effect {
		final Vec3 at;
		final float r0;
		final float r1;

		Ring(Vec3 at, float r0, float r1, int ticks) {
			super(ticks);
			this.at = at;
			this.r0 = r0;
			this.r1 = r1;
		}

		@Override
		void draw(FxDraw draw, float partial) {
			float t = progress(partial);
			float alpha = (float) Math.sqrt(Math.sin(Math.PI * t));
			float r = r0 + (r1 - r0) * VoidRender.easeOut(t);
			// A wide ring keeps its band thin: the bigger, the fainter
			decal(draw, WAVE, at, r, 0, 1, alpha * Math.min(0.5f, 1.2f / Math.max(1, r)), 0.06f);
		}
	}

	// ---- Guild Harpoon ----------------------------------------------------------------------------------------------

	/**
	 * The harpoon: its hook flies from the hand to where it bites, its chain paying out behind it link by link (a
	 * little slack while it flies, taut once it bites, links turned in turn so the chain reads as metal), then it holds
	 * whatever it bit while the pull lasts and snaps back when let go.
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
				sparks(bite(1), 12, 0.2);
				VoidRender.add(new Flash(bite(1), 0.6f, 6));
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
			// Slack while flying and coming back, taut while it holds
			float slack = letGoAt >= 0 ? 0.25f : life < fly ? 0.35f * (1 - life / fly) + 0.08f : 0.02f;
			Vec3 view = draw.cam.subtract(hand.add(head).scale(0.5));
			Vec3 side = dir.cross(view);
			side = side.lengthSqr() < 1.0E-6 ? new Vec3(1, 0, 0) : side.normalize();
			Vec3 other = dir.cross(side).normalize();
			int links = (int) Math.min(80, length / 0.26);
			float strain = letGoAt < 0 && life >= fly ? 1 : 0;
			for (int i = 0; i < links; i++) {
				double s0 = i / (double) links;
				double s1 = (i + 1) / (double) links;
				Vec3 p0 = hand.add(along.scale(s0)).add(0, -Math.sin(Math.PI * s0) * slack * length * 0.25, 0);
				Vec3 p1 = hand.add(along.scale(s1)).add(0, -Math.sin(Math.PI * s1) * slack * length * 0.25, 0);
				Vec3 c = p0.lerp(p1, 0.5);
				Vec3 d = p1.subtract(p0).scale(0.5 * 1.3);
				Vec3 across = (i % 2 == 0 ? side : other).scale(0.1);
				// A shimmer runs along a taut chain: the metal edges catch the light in turn
				float shine = 0.75f + 0.25f * (float) Math.sin(life * 0.9 - i * 0.6) * strain;
				draw.paint(LINK, c, d, across, shine, shine * 0.92f, shine * 0.8f, 0.95f, 0.45f + 0.35f * strain);
			}
			// The hook's head, along the chain, point first
			Vec3 up = dir.scale(0.28);
			Vec3 width = side.scale(0.2);
			draw.paint(HOOK, head, width, up.scale(-1), 1, 1, 1, 1, 0.7f);
			if (life < fly) {
				billboard(draw, FLARE, head, 0.25f, 0, 1, 0.6f, 1.0f);
			}
		}
	}

	/** A quick soft flash at a point. */
	static final class Flash extends VoidRender.Effect {
		final Vec3 at;
		final float size;

		Flash(Vec3 at, float size, int ticks) {
			super(ticks);
			this.at = at;
			this.size = size;
		}

		@Override
		void draw(FxDraw draw, float partial) {
			float t = progress(partial);
			billboard(draw, FLARE, at, size * (0.6f + 0.7f * VoidRender.easeOut(t)), 0, 1, (1 - t) * (1 - t), 1.3f);
		}
	}

	// ---- the Guild's Verdict ----------------------------------------------------------------------------------------

	/**
	 * The Verdict on its creature: the guild's warrant hanging over its head (small, turned to the eye, swaying), and
	 * spectral swords circling it point inwards, one for every blade still owed. A blade owed is one sword driven into the
	 * body; the execution brings down a great sword from above (see {@link Executioner}) and tears the warrant apart.
	 */
	static final class VerdictSeal extends VoidRender.Effect {
		final Entity body;
		final int blades;
		int fading = -1;
		int used;
		/** The age each driven sword was let go at (-1: still circling). */
		final int[] thrown;
		boolean executed;

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

		@Override
		boolean tick() {
			if (fading >= 0 && --fading <= 0 || !body.isAlive()) {
				if (VERDICTS.get(body.getId()) == this) {
					VERDICTS.remove(body.getId());
				}
				return false;
			}
			for (int k = 0; k < blades; k++) {
				if (thrown[k] >= 0 && age - thrown[k] == 4) {
					Vec3 hit = middle(body, 1);
					sparks(hit, 10, 0.2);
					VoidRender.add(new Flash(hit, 0.45f, 6));
				}
			}
			return super.tick();
		}

		/** Where a circling sword stands and points: round the body at chest height, slowly turning, bobbing. */
		private Vec3[] sword(int k, float life, Vec3 heart) {
			double radius = 0.9 + body.getBbWidth() * 0.7;
			double a = life * 0.05 + k * Math.PI * 2 / blades;
			Vec3 at = heart.add(Math.cos(a) * radius, 0.25 + 0.1 * Math.sin(life * 0.15 + k), Math.sin(a) * radius);
			return new Vec3[] {at, heart.subtract(at).normalize()};
		}

		@Override
		void draw(FxDraw draw, float partial) {
			float life = life(partial);
			float open = VoidRender.easeOutBack(Math.min(1, life / 8f));
			float fade = fading >= 0 ? fading / 10f : Math.min(1, (ticks - life) / 10f);
			Vec3 heart = middle(body, partial);
			// The warrant, swaying a little over the head (torn away at the execution)
			if (!executed) {
				Vec3 over = top(body, partial).add(0, 0.35, 0);
				double sway = Math.sin(life * 0.12) * 0.12;
				Vec3[] f = facing();
				Vec3 u = f[0].scale(Math.cos(sway)).add(f[1].scale(Math.sin(sway))).scale(0.26 * open);
				Vec3 v = f[1].scale(Math.cos(sway)).subtract(f[0].scale(Math.sin(sway))).scale(0.33 * open);
				draw.paint(WARRANT, over, u, v.scale(-1), 1, 1, 1, 0.92f * fade, 0.15f);
			}
			for (int k = 0; k < blades; k++) {
				Vec3[] s = sword(k, life, heart);
				Vec3 at = s[0];
				Vec3 dir = s[1];
				float alpha = 0.85f * fade * open;
				if (thrown[k] >= 0) {
					float t = (life - thrown[k]) / 4f;
					if (t > 1.6f) {
						continue;
					}
					// Drawn back a little, then driven in, then gone in a flash
					float drive = t < 0.25f ? -0.3f * (t / 0.25f) : -0.3f + 1.3f * Math.min(1, (t - 0.25f) / 0.75f);
					at = at.add(dir.scale(drive * at.distanceTo(heart)));
					alpha *= t < 1 ? 1 : Math.max(0, 1 - (t - 1) / 0.6f);
				}
				float len = 0.42f + body.getBbHeight() * 0.08f;
				Vec3 side = dir.cross(draw.cam.subtract(at));
				side = side.lengthSqr() < 1.0E-6 ? new Vec3(0, 1, 0) : side.normalize();
				// Point towards the body (the texture's tip is at v = 0)
				draw.paint(BLADE, at, side.scale(0.09), dir.scale(-len), 1, 0.95f, 0.85f, alpha, 0.55f);
			}
			// A faint chain of light on the ground, the circle the swords keep
			Vec3 feet = body.getPosition(partial);
			decal(draw, SIGIL, new Vec3(feet.x, BloodFx.groundY(feet) + 0.03, feet.z), (float) (0.9 + body.getBbWidth() * 0.7) * open, -life * 0.03, 1,
					0.3f * fade, 0.1f);
		}
	}

	/**
	 * A spectral blade's stroke at a creature: it appears beside it (from a side, or from the banner's way), raised, then
	 * swings down through the body with two fading after-images and is gone in a flash.
	 */
	static final class Swing extends VoidRender.Effect {
		final Entity body;
		final double angle;
		final float scale;
		final int stroke;

		Swing(Entity body, Vec3 from, double angle, float scale, int stroke) {
			super(stroke + 6);
			this.body = body;
			this.angle = angle;
			this.scale = scale;
			this.stroke = stroke;
		}

		@Override
		boolean tick() {
			if (age == stroke - 2 && body.isAlive()) {
				sparks(middle(body, 1), 12, 0.22);
			}
			return super.tick();
		}

		private void blade(FxDraw draw, Vec3 pivot, Vec3 radial, Vec3 tangent, double theta, float alpha, float glow) {
			Vec3 up = new Vec3(0, 1, 0);
			Vec3 d = up.scale(Math.cos(theta)).subtract(radial.scale(Math.sin(theta)));
			float len = 0.75f * scale + body.getBbHeight() * 0.25f;
			Vec3 hilt = pivot.add(radial.scale(0.9 + body.getBbWidth() * 0.6)).add(0, 0.2, 0);
			Vec3 centre = hilt.add(d.scale(len));
			draw.paint(BLADE, centre, tangent.scale(0.16 * scale), d.scale(-len), 1, 0.95f, 0.85f, alpha, glow);
		}

		@Override
		void draw(FxDraw draw, float partial) {
			if (!body.isAlive() && age < stroke) {
				return;
			}
			float life = life(partial);
			Vec3 pivot = middle(body, partial);
			Vec3 radial = new Vec3(Math.cos(angle), 0, Math.sin(angle));
			Vec3 tangent = new Vec3(-radial.z, 0, radial.x);
			float appear = Math.min(1, life / 3f);
			if (life < stroke) {
				float t = smooth(Math.max(0, (life - 2) / (stroke - 2)));
				double theta = -0.3 + t * 2.0;
				for (int k = 2; k >= 0; k--) {
					double back = theta - k * 0.22 * t;
					blade(draw, pivot, radial, tangent, back, appear * (k == 0 ? 0.95f : 0.3f / k), k == 0 ? 1.2f : 0.5f);
				}
			} else {
				float t = (life - stroke) / 6f;
				blade(draw, pivot, radial, tangent, 1.7, (1 - t) * 0.8f, 1.0f);
				billboard(draw, FLARE, pivot, 0.5f + t * 0.6f, 0, 1, (1 - t) * 0.8f, 1.3f);
			}
		}
	}

	/** The execution: a great sword forms over the condemned, turns point down, falls; a thin ring, shards, coins, the warrant torn. */
	private static void execute(Entity body) {
		VoidRender.add(new Executioner(body));
	}

	static final class Executioner extends VoidRender.Effect {
		final Entity body;
		final Vec3 rest;
		final double[] scraps = new double[6];
		static final int FORM = 10;
		static final int FALL = 3;

		Executioner(Entity body) {
			super(FORM + FALL + 22);
			this.body = body;
			this.rest = middle(body, 1);
			for (int i = 0; i < scraps.length; i++) {
				scraps[i] = i * Math.PI * 2 / scraps.length + r(-0.3, 0.3);
			}
		}

		private Vec3 heart(float partial) {
			return body.isAlive() ? middle(body, partial) : rest;
		}

		@Override
		boolean tick() {
			if (age == FORM + FALL) {
				Vec3 hit = heart(1);
				Vec3 g = new Vec3(hit.x, BloodFx.groundY(hit) + 0.02, hit.z);
				VoidRender.add(new Ring(g, 0.4f, 2.6f, 12));
				VoidRender.add(new Flash(hit, 0.6f, 6));
				VoidRender.add(new CoinBurst(hit, 18, 0.3));
				for (int i = 0; i < 30; i++) {
					add(spark(hit, VoidRender.randomDir().scale(r(0.15, 0.4)), (int) r(10, 20), r(0.07, 0.12)));
				}
			}
			return super.tick();
		}

		@Override
		void draw(FxDraw draw, float partial) {
			float life = life(partial);
			Vec3 heart = heart(partial);
			float len = 1.1f + body.getBbHeight() * 0.35f;
			Vec3[] f = facing();
			Vec3 side = new Vec3(f[0].x, 0, f[0].z);
			side = side.lengthSqr() < 1.0E-6 ? new Vec3(1, 0, 0) : side.normalize();
			Vec3 above = heart.add(0, body.getBbHeight() * 0.5 + 2.4, 0);
			if (life < FORM) {
				// Forming: it fades in point up, turns over and steadies, point down
				float t = life / FORM;
				double turn = Math.PI * VoidRender.easeOut(t);
				Vec3 tipDir = new Vec3(0, Math.cos(turn), 0).add(side.scale(Math.sin(turn)));
				Vec3 width = tipDir.cross(draw.cam.subtract(above));
				width = width.lengthSqr() < 1.0E-6 ? side : width.normalize();
				draw.paint(BLADE, above, width.scale(0.32), tipDir.scale(-len), 1, 0.92f, 0.8f, t, 0.15f * t);
				billboard(draw, FLARE, above, 0.4f + 0.3f * t, 0, 1, 0.25f * t, 0.15f);
				return;
			}
			float fall = Math.min(1, (life - FORM) / FALL);
			float after = Math.max(0, life - FORM - FALL);
			float alpha = after < 10 ? 1 : Math.max(0, 1 - (after - 10) / 12f);
			// Falling: its tip goes from over the head into the heart; then it stands there, fading
			Vec3 tip = above.subtract(0, len, 0).lerp(heart, fall * fall);
			Vec3 centre = tip.add(0, len, 0);
			draw.paint(BLADE, centre, side.scale(0.32), new Vec3(0, len, 0), 1, 0.92f, 0.8f, alpha, 0.18f);
			// The warrant torn: scraps fluttering out and down
			if (after > 0 && after < 22) {
				float t = after / 22f;
				Vec3 over = top(body, partial).add(0, 0.35, 0);
				for (int i = 0; i < scraps.length; i++) {
					double a = scraps[i];
					float d = 0.3f + 1.6f * VoidRender.easeOut(t);
					Vec3 p = over.add(Math.cos(a) * d, -t * t * 1.4 + 0.2 * Math.sin(a * 3 + after * 0.4), Math.sin(a) * d);
					billboard(draw, CONTRACT, p, 0.12f, a + after * 0.3, 1, (1 - t) * 0.9f, 0.1f);
				}
			}
		}
	}

	/** A pillar of light over a contract's mark (gold for a place to scout, red-gold for a bounty's lair), seen from afar. */
	static final class Pillar extends VoidRender.Effect {
		final Vec3 at;
		final boolean bounty;

		Pillar(Vec3 at, boolean bounty, int ticks) {
			super(ticks);
			this.at = at;
			this.bounty = bounty;
		}

		@Override
		void draw(FxDraw draw, float partial) {
			float t = progress(partial);
			float alpha = (float) Math.sin(Math.PI * t) * 0.7f;
			Minecraft mc = Minecraft.getInstance();
			double ground = at.y;
			if (mc.level != null) {
				ground = mc.level.getHeight(net.minecraft.world.level.levelgen.Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, (int) Math.floor(at.x),
						(int) Math.floor(at.z));
			}
			Vec3 foot = new Vec3(at.x, ground, at.z);
			float r = bounty ? 1 : 1;
			float gr = bounty ? 0.55f : 0.85f;
			float b = bounty ? 0.35f : 0.5f;
			Vec3 top = foot.add(0, 48, 0);
			Vec3 along = top.subtract(foot);
			Vec3 side = along.cross(draw.cam.subtract(foot.lerp(top, 0.5)));
			if (side.lengthSqr() < 1.0E-6) {
				return;
			}
			side = side.normalize().scale(0.6);
			draw.paintStrip(FLARE, new Vec3[] {foot.subtract(side), top.subtract(side), top.add(side), foot.add(side)}, new float[] {0.5f, 0.5f, 0.5f, 0.5f},
					new float[] {0.5f, 0.5f, 0.5f, 0.5f}, r, gr, b, alpha * 0.6f, 0.4f);
			decal(draw, SIGIL, foot.add(0, 0.05, 0), 2.2f, t * 2, 1, alpha, 0.3f);
		}
	}

	// ---- the Ransom -------------------------------------------------------------------------------------------------

	/** The Life Debt: a ring of sealed contracts turning round the ward's waist, a faint seal under its feet. */
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
			int n = 6;
			for (int i = 0; i < n; i++) {
				double a = life * 0.06 + i * Math.PI * 2 / n;
				Vec3 out = new Vec3(Math.cos(a), 0, Math.sin(a));
				Vec3 tangent = new Vec3(-out.z, 0, out.x);
				Vec3 c = waist.add(out.scale(radius)).add(0, Math.sin(life * 0.15 + i) * 0.06, 0);
				draw.paint(CONTRACT, c, tangent.scale(0.2), new Vec3(0, 0.1, 0), 1, 1, 1, 0.8f * fade, 0.6f);
			}
			Vec3 feet = body.getPosition(partial);
			decal(draw, SIGIL, new Vec3(feet.x, BloodFx.groundY(feet) + 0.03, feet.z), (float) radius + 0.4f, life * 0.03, 1, 0.35f * fade, 0.4f);
		}
	}

	private static void debtWave(Vec3 at, float radius) {
		Vec3 g = new Vec3(at.x, BloodFx.groundY(at) + 0.02, at.z);
		VoidRender.add(new Ring(g, 0.4f, radius, 14));
		VoidRender.add(new Sigil(g, radius * 0.8f, 18));
		VoidRender.add(new Flash(at.add(0, 1, 0), 1.0f, 10));
		coins(at.add(0, 1, 0), 16, 0.2);
		sparks(at.add(0, 1, 0), 20, 0.3);
	}

	/** The Debt come due: its contracts torn off the body, a dark red flash. */
	private static void due(Entity body) {
		Vec3 at = middle(body, 1);
		VoidRender.add(new Tinted(at, 0.9f, 10, 1, 0.35f, 0.25f));
		for (int i = 0; i < 18; i++) {
			VoidRender.Particle p = new VoidRender.Particle(at, VoidRender.randomDir().scale(r(0.1, 0.25)), (int) r(10, 18), (float) r(0.05, 0.09),
					0xFFE04A30, false, 0.88f, 0.9f);
			p.tex = SPARK;
			p.gravity = new Vec3(0, -0.02, 0);
			add(p);
		}
	}

	static final class Tinted extends VoidRender.Effect {
		final Vec3 at;
		final float size;
		final float r;
		final float g;
		final float b;

		Tinted(Vec3 at, float size, int ticks, float r, float g, float b) {
			super(ticks);
			this.at = at;
			this.size = size;
			this.r = r;
			this.g = g;
			this.b = b;
		}

		@Override
		void draw(FxDraw draw, float partial) {
			float t = progress(partial);
			billboard(draw, FLARE, at, size * (0.6f + 0.7f * t), 0, r, g, b, (1 - t) * 0.9f, 1.1f);
		}
	}

	/** A turning circle on the ground that opens and fades. */
	static final class Sigil extends VoidRender.Effect {
		final Vec3 at;
		final float half;

		Sigil(Vec3 at, float half, int ticks) {
			super(ticks);
			this.at = at;
			this.half = half;
		}

		@Override
		void draw(FxDraw draw, float partial) {
			float t = progress(partial);
			decal(draw, SIGIL, at, half * (0.4f + 0.6f * VoidRender.easeOutBack(Math.min(1, t * 2))), t * 1.2, 1, (1 - t) * 0.9f, 1.0f);
		}
	}

	/** The Last Ransom: a great seal flares up behind the body and turns, coins burst from it, a column of sparks. */
	private static void ransom(Entity body) {
		VoidRender.add(new Warrant(body));
		Vec3 at = middle(body, 1);
		coins(at, 40, 0.3);
		for (int i = 0; i < 30; i++) {
			add(spark(body.position().add(r(-0.4, 0.4), r(0, 0.3), r(-0.4, 0.4)), new Vec3(0, r(0.15, 0.4), 0), (int) r(14, 26), r(0.06, 0.12), 0.93f));
		}
		Vec3 g = body.position();
		VoidRender.add(new Ring(new Vec3(g.x, BloodFx.groundY(g) + 0.02, g.z), 0.5f, 4, 16));
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
			float open = VoidRender.easeOutBack(Math.min(1, life / 6f));
			billboard(draw, VERDICT, at, 1.6f * open, life * 0.08, 1, (1 - t * t) * 0.9f, 1.3f);
			billboard(draw, FLARE, at, 1.4f + t * 1.5f, 0, 1, (1 - t) * 0.8f, 1.4f);
		}
	}

	// ---- the Guild Legend -------------------------------------------------------------------------------------------

	/**
	 * The banner: a pole of light, a translucent golden cloth with the guild's coin and blade waving from it (turned to
	 * whoever looks), a slow seal on the ground and its circle's edge, light shards rising round it.
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
			VoidRender.add(new Ring(this.at.add(0, 0.02, 0), 0.5f, radius, 16));
			coins(at.add(0, 3, 0), 12, 0.15);
		}

		void extend(int ticks) {
			end = age + ticks;
			sparks(at.add(0, 3.2, 0), 16, 0.25);
		}

		@Override
		boolean tick() {
			if (fading >= 0 && --fading <= 0 || fading < 0 && age > end + 20) {
				return false;
			}
			if (age % 3 == 0 && fading < 0) {
				double a = r(0, Math.PI * 2);
				double d = Math.sqrt(VoidRender.RANDOM.nextDouble()) * radius;
				add(spark(at.add(Math.cos(a) * d, 0.1, Math.sin(a) * d), new Vec3(0, r(0.03, 0.07), 0), (int) r(26, 44), r(0.05, 0.09), 0.99f));
			}
			return super.tick();
		}

		@Override
		void draw(FxDraw draw, float partial) {
			float life = life(partial);
			float rise = VoidRender.easeOutBack(Math.min(1, life / 10f));
			float fade = fading >= 0 ? fading / 14f : Math.min(1, (end + 20 - life) / 20f);
			// The pole
			Vec3 foot = at;
			Vec3 head = at.add(0, 3.6 * rise, 0);
			band(draw, FLARE, foot, head, 0.12f, 0.9f * fade, 1.0f);
			billboard(draw, FLARE, head, 0.35f, 0, 1, 0.9f * fade, 1.3f);
			// The cloth, turned round the pole towards the eye, waving in segments
			Vec3 toCam = draw.cam.subtract(at);
			Vec3 flat = new Vec3(toCam.x, 0, toCam.z);
			flat = flat.lengthSqr() < 1.0E-6 ? new Vec3(0, 0, 1) : flat.normalize();
			Vec3 across = new Vec3(-flat.z, 0, flat.x);
			float width = 1.3f;
			float height = 2.4f * rise;
			Vec3 topLeft = head.add(0, -0.15, 0);
			int segments = 6;
			for (int i = 0; i < segments; i++) {
				float u0 = i / (float) segments;
				float u1 = (i + 1) / (float) segments;
				double w0 = Math.sin(life * 0.18 - u0 * 3.5) * 0.12 * u0;
				double w1 = Math.sin(life * 0.18 - u1 * 3.5) * 0.12 * u1;
				Vec3 a0 = topLeft.add(across.scale(u0 * width)).add(flat.scale(w0));
				Vec3 a1 = topLeft.add(across.scale(u1 * width)).add(flat.scale(w1));
				Vec3 b0 = a0.add(0, -height, 0);
				Vec3 b1 = a1.add(0, -height, 0);
				draw.paintStrip(BANNER, new Vec3[] {a0, a1, b1, b0}, new float[] {u0, u1, u1, u0}, new float[] {0, 0, 1, 1}, 1, 1, 1, 0.8f * fade, 0.55f);
			}
			// The ground: a slow seal at the foot, the circle's edge breathing
			decal(draw, SIGIL, foot.add(0, 0.03, 0), 1.6f * rise, life * 0.02, 1, 0.55f * fade, 0.6f);
			// The circle's edge: thin lines of the seal, breathing faintly (a wide band of light at this size blinds)
			float pulse = 0.5f + 0.5f * (float) Math.sin(life * 0.12);
			decal(draw, SIGIL, foot.add(0, 0.02, 0), radius, -life * 0.006, 1, (0.14f + 0.08f * pulse) * fade, 0.05f);
		}
	}

	// ---- the Mage's Shop and the contracts ---------------------------------------------------------------------------

	/** The Shop's door of warm light standing before its Mercenary: it grows open, glows, and swings shut when left. */
	static final class Door extends VoidRender.Effect {
		final Vec3 at;
		final float yaw;
		int closing = -1;

		Door(Vec3 at, float yaw, int ticks) {
			super(ticks);
			this.at = at;
			this.yaw = yaw;
		}

		@Override
		boolean tick() {
			if (closing >= 0 && ++closing > 10) {
				DOORS.remove(this);
				return false;
			}
			if (age % 4 == 0 && closing < 0) {
				add(coin(at.add(r(-0.5, 0.5), r(0.2, 2.4), r(-0.5, 0.5)), new Vec3(0, 0.01, 0), 30));
			}
			boolean alive = super.tick();
			if (!alive) {
				DOORS.remove(this);
			}
			return alive;
		}

		@Override
		void draw(FxDraw draw, float partial) {
			float life = life(partial);
			float open = closing >= 0 ? 1 - smooth((closing + partial) / 10f) : smooth(life / 10f);
			double rad = Math.toRadians(yaw);
			Vec3 side = new Vec3(Math.cos(rad), 0, Math.sin(rad));
			float h = 1.4f * open;
			Vec3 centre = new Vec3(at.x, BloodFx.groundY(at) + h, at.z);
			draw.paint(DOOR, centre, side.scale(0.75f), new Vec3(0, -h, 0), 1, 0.9f, 0.75f, 0.7f * open, 0.3f);
			decal(draw, SIGIL, new Vec3(at.x, BloodFx.groundY(at) + 0.03, at.z), 1.0f * open, life * 0.02, 1, 0.5f * open, 0.4f);
		}
	}

	/** A contract done: a seal stamped over the head and a circle at the feet. */
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
			billboard(draw, SEAL, over, 0.45f * press, 0, 1, (1 - t) * 0.95f, 1.1f);
			for (int k = 0; k < 3; k++) {
				billboard(draw, SEAL_PART, over, 0.45f * press, -k * 2.094 - Math.PI / 12, 1, (1 - t) * 0.95f, 1.2f);
			}
			Vec3 feet = body.getPosition(partial);
			decal(draw, SIGIL, new Vec3(feet.x, BloodFx.groundY(feet) + 0.03, feet.z), 0.6f + t * 1.4f, t, 1, (1 - t) * 0.8f, 0.8f);
		}
	}
}
