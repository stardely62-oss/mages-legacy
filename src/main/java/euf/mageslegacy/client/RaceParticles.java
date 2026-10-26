package euf.mageslegacy.client;

import euf.mageslegacy.fx.ModParticles;
import net.fabricmc.fabric.api.client.particle.v1.ParticleFactoryRegistry;
import net.minecraft.client.particle.ParticleTextureSheet;
import net.minecraft.client.particle.SpriteBillboardParticle;
import net.minecraft.client.particle.SpriteProvider;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.util.math.MathHelper;

/** How each of the races' particles moves and looks (see {@link ModParticles}). */
public final class RaceParticles {
	private static final int FULL_BRIGHT = 0xF000F0;

	private RaceParticles() {
	}

	public static void register() {
		ParticleFactoryRegistry registry = ParticleFactoryRegistry.getInstance();
		registry.register(ModParticles.SAND, sprites -> (type, world, x, y, z, vx, vy, vz) -> new Sand(world, x, y, z, vx, vy, vz, sprites, false));
		registry.register(ModParticles.STILL_SAND, sprites -> (type, world, x, y, z, vx, vy, vz) -> new Sand(world, x, y, z, vx, vy, vz, sprites, true));
		registry.register(ModParticles.ICE_SHARD, sprites -> (type, world, x, y, z, vx, vy, vz) -> new Shard(world, x, y, z, vx, vy, vz, sprites));
		registry.register(ModParticles.SPORE, sprites -> (type, world, x, y, z, vx, vy, vz) -> new Spore(world, x, y, z, vx, vy, vz, sprites));
		registry.register(ModParticles.WISP, sprites -> (type, world, x, y, z, vx, vy, vz) -> new Wisp(world, x, y, z, vx, vy, vz, sprites));
		registry.register(ModParticles.MOTE, sprites -> (type, world, x, y, z, vx, vy, vz) -> new Mote(world, x, y, z, vx, vy, vz, sprites));
		registry.register(ModParticles.BLOOD, sprites -> (type, world, x, y, z, vx, vy, vz) -> new Drop(world, x, y, z, vx, vy, vz, sprites));
		registry.register(ModParticles.BLOOD_MOTE, sprites -> (type, world, x, y, z, vx, vy, vz) -> new Mote(world, x, y, z, vx, vy, vz, sprites));
	}

	/** Fades in over the first few ticks and out over the last third. */
	private static float lifeAlpha(int age, int maxAge, float fadeIn) {
		float in = fadeIn <= 0 ? 1 : Math.min(1, age / fadeIn);
		float out = Math.min(1, (maxAge - age) / (maxAge / 3f));
		return MathHelper.clamp(Math.min(in, out), 0, 1);
	}

	/** A grain of sand: tiny, falls and drifts; a still one hangs in the air and glints. */
	static final class Sand extends SpriteBillboardParticle {
		private final boolean still;

		Sand(ClientWorld world, double x, double y, double z, double vx, double vy, double vz, SpriteProvider sprites, boolean still) {
			super(world, x, y, z);
			this.still = still;
			setSprite(sprites.getSprite(random));
			velocityX = still ? 0 : vx + (random.nextDouble() - 0.5) * 0.02;
			velocityY = still ? 0 : vy;
			velocityZ = still ? 0 : vz + (random.nextDouble() - 0.5) * 0.02;
			gravityStrength = still ? 0 : 0.35f;
			velocityMultiplier = 0.92f;
			scale = 0.05f + random.nextFloat() * 0.04f;
			maxAge = still ? 60 + random.nextInt(40) : 20 + random.nextInt(20);
			float tint = 0.85f + random.nextFloat() * 0.15f;
			setColor(tint, tint, tint);
		}

		@Override
		public void tick() {
			super.tick();
			alpha = lifeAlpha(age, maxAge, 4);
			if (still) {
				// A slow glint, each grain on its own beat
				float glint = 0.75f + 0.25f * MathHelper.sin((age + hashCode() % 20) * 0.35f);
				setColor(glint, glint, glint);
			}
		}

		@Override
		public int getBrightness(float tint) {
			return still ? FULL_BRIGHT : super.getBrightness(tint);
		}

		@Override
		public ParticleTextureSheet getType() {
			return ParticleTextureSheet.PARTICLE_SHEET_TRANSLUCENT;
		}
	}

	/** A shard of ice: tumbles as it falls, catching the light. */
	static final class Shard extends SpriteBillboardParticle {
		private final float spin;

		Shard(ClientWorld world, double x, double y, double z, double vx, double vy, double vz, SpriteProvider sprites) {
			super(world, x, y, z, vx, vy, vz);
			setSprite(sprites.getSprite(random));
			velocityX = vx + (random.nextDouble() - 0.5) * 0.06;
			velocityY = vy + 0.02 + random.nextDouble() * 0.04;
			velocityZ = vz + (random.nextDouble() - 0.5) * 0.06;
			gravityStrength = 0.6f;
			velocityMultiplier = 0.94f;
			scale = 0.08f + random.nextFloat() * 0.07f;
			maxAge = 18 + random.nextInt(16);
			spin = (random.nextFloat() - 0.5f) * 0.5f;
			angle = random.nextFloat() * MathHelper.TAU;
			prevAngle = angle;
		}

		@Override
		public void tick() {
			super.tick();
			prevAngle = angle;
			if (!onGround) {
				angle += spin;
			}
			alpha = lifeAlpha(age, maxAge, 0);
		}

		@Override
		public int getBrightness(float tint) {
			// Ice keeps a little of its own light
			return Math.max(super.getBrightness(tint), 0xB000B0);
		}

		@Override
		public ParticleTextureSheet getType() {
			return ParticleTextureSheet.PARTICLE_SHEET_TRANSLUCENT;
		}
	}

	/** A spore: floats up, swaying; swells as it ages and bursts into a ring at the end. Glows faintly. */
	static final class Spore extends SpriteBillboardParticle {
		private final SpriteProvider sprites;
		private final float sway;

		Spore(ClientWorld world, double x, double y, double z, double vx, double vy, double vz, SpriteProvider sprites) {
			super(world, x, y, z);
			this.sprites = sprites;
			velocityX = vx * 0.5;
			velocityY = vy * 0.5 + 0.008 + random.nextDouble() * 0.01;
			velocityZ = vz * 0.5;
			gravityStrength = -0.02f;
			velocityMultiplier = 0.96f;
			scale = 0.07f + random.nextFloat() * 0.06f;
			maxAge = 40 + random.nextInt(40);
			sway = random.nextFloat() * MathHelper.TAU;
			setSpriteForAge(sprites);
		}

		@Override
		public void tick() {
			super.tick();
			velocityX += MathHelper.sin(age * 0.15f + sway) * 0.002;
			velocityZ += MathHelper.cos(age * 0.13f + sway) * 0.002;
			setSpriteForAge(sprites);
			alpha = lifeAlpha(age, maxAge, 6) * 0.9f;
		}

		@Override
		public int getBrightness(float tint) {
			return FULL_BRIGHT;
		}

		@Override
		public ParticleTextureSheet getType() {
			return ParticleTextureSheet.PARTICLE_SHEET_TRANSLUCENT;
		}
	}

	/** A golden mote of the Song: carried along its velocity with a gentle wave, glowing warm, pulsing as it goes. */
	static final class Mote extends SpriteBillboardParticle {
		private final SpriteProvider sprites;
		private final float phase;

		Mote(ClientWorld world, double x, double y, double z, double vx, double vy, double vz, SpriteProvider sprites) {
			super(world, x, y, z);
			this.sprites = sprites;
			velocityX = vx;
			velocityY = vy;
			velocityZ = vz;
			gravityStrength = 0;
			velocityMultiplier = 0.9f;
			scale = 0.08f + random.nextFloat() * 0.05f;
			maxAge = 26 + random.nextInt(14);
			phase = random.nextFloat() * MathHelper.TAU;
			setSpriteForAge(sprites);
		}

		@Override
		public void tick() {
			super.tick();
			velocityY += MathHelper.sin(age * 0.4f + phase) * 0.004;
			setSpriteForAge(sprites);
			alpha = lifeAlpha(age, maxAge, 4);
		}

		@Override
		public int getBrightness(float tint) {
			return FULL_BRIGHT;
		}

		@Override
		public ParticleTextureSheet getType() {
			return ParticleTextureSheet.PARTICLE_SHEET_TRANSLUCENT;
		}
	}

	/** A drop of blood: flung out, falls, and lies a moment on the ground as a small stain before it fades. */
	static final class Drop extends SpriteBillboardParticle {
		private final SpriteProvider sprites;
		private boolean landed;

		Drop(ClientWorld world, double x, double y, double z, double vx, double vy, double vz, SpriteProvider sprites) {
			super(world, x, y, z);
			this.sprites = sprites;
			velocityX = vx + (random.nextDouble() - 0.5) * 0.08;
			velocityY = vy + 0.05 + random.nextDouble() * 0.08;
			velocityZ = vz + (random.nextDouble() - 0.5) * 0.08;
			gravityStrength = 1.0f;
			velocityMultiplier = 0.96f;
			scale = 0.06f + random.nextFloat() * 0.06f;
			maxAge = 30 + random.nextInt(20);
			setSprite(sprites.getSprite(random.nextInt(2), 2));
		}

		@Override
		public void tick() {
			super.tick();
			if (onGround && !landed) {
				landed = true;
				setSprite(sprites.getSprite(2, 2));
				scale *= 1.6f;
				maxAge = Math.min(maxAge, age + 25);
			}
			alpha = lifeAlpha(age, maxAge, 0);
		}

		@Override
		public ParticleTextureSheet getType() {
			return ParticleTextureSheet.PARTICLE_SHEET_TRANSLUCENT;
		}
	}

	/** A spirit wisp: rises, flickering between its frames, glowing; fades as it goes up. */
	static final class Wisp extends SpriteBillboardParticle {
		private final SpriteProvider sprites;

		Wisp(ClientWorld world, double x, double y, double z, double vx, double vy, double vz, SpriteProvider sprites) {
			super(world, x, y, z);
			this.sprites = sprites;
			velocityX = vx * 0.4 + (random.nextDouble() - 0.5) * 0.01;
			velocityY = vy * 0.4 + 0.03 + random.nextDouble() * 0.02;
			velocityZ = vz * 0.4 + (random.nextDouble() - 0.5) * 0.01;
			gravityStrength = -0.01f;
			velocityMultiplier = 0.95f;
			scale = 0.12f + random.nextFloat() * 0.08f;
			maxAge = 24 + random.nextInt(16);
			setSprite(sprites.getSprite(random));
		}

		@Override
		public void tick() {
			super.tick();
			if (age % 3 == 0) {
				setSprite(sprites.getSprite(random));
			}
			alpha = lifeAlpha(age, maxAge, 3) * 0.95f;
		}

		@Override
		public int getBrightness(float tint) {
			return FULL_BRIGHT;
		}

		@Override
		public ParticleTextureSheet getType() {
			return ParticleTextureSheet.PARTICLE_SHEET_TRANSLUCENT;
		}
	}
}
