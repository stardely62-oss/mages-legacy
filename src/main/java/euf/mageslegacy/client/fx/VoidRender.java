package euf.mageslegacy.client.fx;

import euf.mageslegacy.MagesLegacy;
import foundry.veil.api.client.render.VeilRenderSystem;
import foundry.veil.api.client.render.post.PostPipeline;
import foundry.veil.api.client.render.post.PostProcessingManager;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Random;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import net.neoforged.neoforge.common.NeoForge;
import org.joml.Matrix4f;
import org.joml.Vector4f;

/**
 * Plays the effects: shader-drawn shapes (rifts, streaks, marks on the ground, the black hole), particles with the
 * first version's sprites, GeckoLib models, and the black hole's lensing of the picture through a Veil post
 * pipeline. Effects tick with the client and draw after the particles.
 */
public final class VoidRender {
	/** One playing effect: its age in ticks, how long it lasts, what it follows. */
	abstract static class Effect {
		final int ticks;
		int age;

		Effect(int ticks) {
			this.ticks = Math.max(1, ticks);
		}

		float life(float partial) {
			return age + partial;
		}

		float progress(float partial) {
			return Math.max(0, Math.min(1, life(partial) / ticks));
		}

		/** Once a client tick (spawning particles); false when it has ended. */
		boolean tick() {
			return ++age < ticks;
		}

		abstract void draw(FxDraw draw, float partial);
	}

	/** A particle: a sprite moving, slowing, perhaps falling in towards a point, fading out. */
	static final class Particle {
		Vec3 pos;
		Vec3 vel;
		final int life;
		int age;
		final float size;
		final int argb;
		final boolean star;
		final float drag;
		final float glow;
		Vec3 attract;
		float pull;
		Vec3 gravity = Vec3.ZERO;
		Vec3 prev;
		/** Its own sprite (an FxDraw name) instead of the mote or the star. */
		String tex;
		/** Wheels round this point's vertical axis, pushed along by swirl each tick. */
		Vec3 swirlAt;
		float swirl;
		/** A random nudge each tick, this strong on each axis: drifting, churning. */
		float jitter;

		Particle(Vec3 pos, Vec3 vel, int life, float size, int argb, boolean star, float drag, float glow) {
			this.pos = pos;
			this.prev = pos;
			this.vel = vel;
			this.life = Math.max(1, life);
			this.size = size;
			this.argb = argb;
			this.star = star;
			this.drag = drag;
			this.glow = glow;
		}

		boolean tick() {
			prev = pos;
			if (attract != null) {
				Vec3 in = attract.subtract(pos);
				double d = in.length();
				if (d < 0.25) {
					return false;
				}
				vel = vel.add(in.scale(pull / Math.max(0.3, d)));
			}
			if (swirlAt != null) {
				double dx = pos.x - swirlAt.x;
				double dz = pos.z - swirlAt.z;
				double d = Math.sqrt(dx * dx + dz * dz);
				if (d > 0.05) {
					vel = vel.add(-dz / d * swirl, 0, dx / d * swirl);
				}
			}
			if (jitter > 0) {
				vel = vel.add((RANDOM.nextFloat() - 0.5f) * 2 * jitter, (RANDOM.nextFloat() - 0.5f) * 2 * jitter, (RANDOM.nextFloat() - 0.5f) * 2 * jitter);
			}
			vel = vel.scale(drag).add(gravity);
			pos = pos.add(vel);
			return ++age < life;
		}
	}

	static final List<Effect> EFFECTS = new ArrayList<>();
	static final List<Particle> PARTICLES = new ArrayList<>();
	static final Random RANDOM = new Random();
	private static final ResourceLocation LENS = MagesLegacy.id("lens");
	/** The black hole bending the picture now, if any. */
	static Entity lensHeart;
	static float lensStrength;
	private static boolean lensOn;

	private VoidRender() {
	}

	/** Bodies not drawn now (entity id → ticks left). */
	private static final java.util.Map<Integer, Integer> HIDDEN = new java.util.HashMap<>();

	public static void register() {
		NeoForge.EVENT_BUS.addListener(VoidRender::onTick);
		NeoForge.EVENT_BUS.addListener(VoidRender::onRender);
		NeoForge.EVENT_BUS.addListener(VoidRender::onRenderLiving);
		NeoForge.EVENT_BUS.addListener(MycelidFx::onCameraDistance);
	}

	static void hide(int entity, int ticks) {
		if (entity >= 0 && ticks <= 0) {
			HIDDEN.remove(entity);
		} else if (entity >= 0) {
			HIDDEN.merge(entity, ticks, Math::max);
		}
	}

	/** A hidden body is not drawn at all: no skin, no armour, nothing in its hands. */
	private static void onRenderLiving(net.neoforged.neoforge.client.event.RenderLivingEvent.Pre<?, ?> event) {
		if (!HIDDEN.isEmpty() && HIDDEN.containsKey(event.getEntity().getId())) {
			event.setCanceled(true);
		}
	}

	static void add(Effect effect) {
		EFFECTS.add(effect);
	}

	static void add(Particle particle) {
		if (PARTICLES.size() < 4000) {
			PARTICLES.add(particle);
		}
	}

	private static void onTick(ClientTickEvent.Post event) {
		if (Minecraft.getInstance().level == null) {
			HIDDEN.clear();
			EFFECTS.clear();
			PARTICLES.clear();
			lensHeart = null;
			return;
		}
		lensHeart = null;
		HIDDEN.replaceAll((id, left) -> left - 1);
		HIDDEN.values().removeIf(left -> left <= 0);
		EFFECTS.removeIf(e -> !e.tick());
		for (Iterator<Particle> it = PARTICLES.iterator(); it.hasNext(); ) {
			if (!it.next().tick()) {
				it.remove();
			}
		}
		VoidShards.tick();
	}

	private static void onRender(RenderLevelStageEvent event) {
		if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_PARTICLES) {
			return;
		}
		Minecraft mc = Minecraft.getInstance();
		Camera camera = event.getCamera();
		float partial = event.getPartialTick().getGameTimeDeltaPartialTick(false);
		try {
			VoidShards.render(event.getPoseStack(), camera, partial);
			// In the void (a Void Step) the world's effects stay behind with the world, glow and all
			if ((!EFFECTS.isEmpty() || !PARTICLES.isEmpty()) && !euf.mageslegacy.client.ClientStep.active()) {
				FxDraw draw = new FxDraw(mc.renderBuffers().bufferSource(), camera.getPosition());
				for (Effect e : EFFECTS) {
					e.draw(draw, partial);
				}
				Vec3 left = new Vec3(camera.getLeftVector().x(), camera.getLeftVector().y(), camera.getLeftVector().z());
				Vec3 up = new Vec3(camera.getUpVector().x(), camera.getUpVector().y(), camera.getUpVector().z());
				for (Particle p : PARTICLES) {
					float t = (p.age + partial) / p.life;
					float fade = Math.min(1, (1 - t) * 3) * Math.min(1, (p.age + partial) / 2f);
					int a = (int) (((p.argb >>> 24) & 0xFF) * Math.max(0, fade));
					Vec3 at = p.prev.lerp(p.pos, partial);
					draw.sprite(p.tex != null ? p.tex : p.star ? FxDraw.STAR : FxDraw.MOTE, at, left, up, p.size, (a << 24) | (p.argb & 0xFFFFFF), p.glow);
				}
				draw.finish();
			}
			updateLens(event, partial);
		} catch (RuntimeException e) {
			// An effect must never take the game down with it
			MagesLegacy.LOGGER.error("Voidborn effects failed to draw", e);
			EFFECTS.clear();
			PARTICLES.clear();
		}
	}

	/** Turns the lensing pipeline on while a black hole is seen, and points it at its heart on the screen. */
	private static void updateLens(RenderLevelStageEvent event, float partial) {
		PostProcessingManager post = VeilRenderSystem.renderer().getPostProcessingManager();
		Entity heart = lensHeart;
		if (heart == null || lensStrength <= 0.001f) {
			if (lensOn) {
				post.remove(LENS);
				lensOn = false;
			}
			return;
		}
		Vec3 at = heart.getPosition(partial).add(0, 1.2, 0).subtract(event.getCamera().getPosition());
		Matrix4f view = new Matrix4f(event.getProjectionMatrix()).mul(event.getModelViewMatrix());
		Vector4f clip = view.transform(new Vector4f((float) at.x, (float) at.y, (float) at.z, 1));
		if (clip.w <= 0.1f) {
			if (lensOn) {
				post.remove(LENS);
				lensOn = false;
			}
			return;
		}
		if (!lensOn) {
			lensOn = post.add(LENS);
		}
		PostPipeline pipeline = post.getPipeline(LENS);
		if (pipeline == null) {
			return;
		}
		float x = (clip.x / clip.w) * 0.5f + 0.5f;
		float y = (clip.y / clip.w) * 0.5f + 0.5f;
		var window = Minecraft.getInstance().getWindow();
		float aspect = window.getWidth() / (float) Math.max(1, window.getHeight());
		// About eight blocks of radius around the heart, as seen from here
		float radius = (float) (8.4 * event.getProjectionMatrix().m11() / Math.max(0.5, clip.w)) * 0.5f;
		pipeline.getUniformSafe("LensCenter").setVector(x, y);
		pipeline.getUniformSafe("LensRadius").setFloat(Math.min(0.6f, radius));
		pipeline.getUniformSafe("LensStrength").setFloat(lensStrength * 0.25f);
		pipeline.getUniformSafe("LensAspect").setFloat(aspect);
	}

	static float easeOut(float t) {
		return 1 - (1 - t) * (1 - t) * (1 - t);
	}

	static float easeOutBack(float t) {
		float c = 1.7f;
		return 1 + (c + 1) * (float) Math.pow(t - 1, 3) + c * (float) Math.pow(t - 1, 2);
	}

	static double rand(double min, double max) {
		return min + RANDOM.nextDouble() * (max - min);
	}

	/** A random direction, evenly over the sphere. */
	static Vec3 randomDir() {
		double z = rand(-1, 1);
		double a = rand(0, Math.PI * 2);
		double r = Math.sqrt(1 - z * z);
		return new Vec3(r * Math.cos(a), z, r * Math.sin(a));
	}
}
