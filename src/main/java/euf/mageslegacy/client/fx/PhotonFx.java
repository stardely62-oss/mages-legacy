package euf.mageslegacy.client.fx;

import com.lowdragmc.lowdraglib2.math.GradientColor;
import com.lowdragmc.photon.client.fx.FXData;
import com.lowdragmc.photon.client.fx.FXRuntime;
import com.lowdragmc.photon.client.fx.IEffectExecutor;
import com.lowdragmc.photon.client.gameobject.IFXObject;
import com.lowdragmc.photon.client.gameobject.emitter.data.EmissionSetting;
import com.lowdragmc.photon.client.gameobject.emitter.data.MaterialSetting;
import com.lowdragmc.photon.client.gameobject.emitter.data.material.BlendMode;
import com.lowdragmc.photon.client.gameobject.emitter.data.material.TextureMaterial;
import com.lowdragmc.photon.client.gameobject.emitter.data.number.NumberFunction;
import com.lowdragmc.photon.client.gameobject.emitter.data.number.NumberFunction3;
import com.lowdragmc.photon.client.gameobject.emitter.data.number.RandomConstant;
import com.lowdragmc.photon.client.gameobject.emitter.data.number.color.Gradient;
import com.lowdragmc.photon.client.gameobject.emitter.data.number.curve.Curve;
import com.lowdragmc.photon.client.gameobject.emitter.data.number.curve.ECBCurves;
import com.lowdragmc.photon.client.gameobject.emitter.data.shape.Circle;
import com.lowdragmc.photon.client.gameobject.emitter.data.shape.IShape;
import com.lowdragmc.photon.client.gameobject.emitter.data.shape.Sphere;
import com.lowdragmc.photon.client.gameobject.emitter.particle.ParticleConfig;
import com.lowdragmc.photon.client.gameobject.emitter.particle.ParticleEmitter;
import com.lowdragmc.photon.client.gameobject.emitter.particle.ParticleRendererSetting;
import net.minecraft.resources.ResourceLocation;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3f;

/**
 * A small builder over Photon 2's particle emitters, so the race effects are written in code: an emitter is a shape
 * that spawns particles (a burst, a steady flow or both) whose colour, size and motion change over their life. An
 * effect is a handful of emitters played together at a point or following an entity.
 */
final class PhotonFx {
	private PhotonFx() {
	}

	/** One emitter. Speeds are in blocks per tick; {@link #radial} pulls in (negative) or pushes out. */
	static final class Emitter {
		final ParticleEmitter emitter = new ParticleEmitter();
		final ParticleConfig config = emitter.config;

		Emitter(int duration) {
			config.setDuration(Math.max(1, duration));
			config.setLooping(false);
			config.emission.setEmissionRate(NumberFunction.constant(0));
		}

		Emitter shape(IShape shape) {
			config.shape.setShape(shape);
			return this;
		}

		Emitter sphere(float radius, float thickness) {
			Sphere sphere = new Sphere();
			sphere.setRadius(radius);
			sphere.setRadiusThickness(thickness);
			return shape(sphere);
		}

		/** A flat ring lying on the ground (the circle shape stands upright until turned). */
		Emitter ring(float radius, float thickness) {
			Circle circle = new Circle();
			circle.setRadius(radius);
			circle.setRadiusThickness(thickness);
			config.shape.setRotation(new NumberFunction3(90, 0, 0));
			return shape(circle);
		}

		/** Stretches the shape (a tall thin rift from a sphere). */
		Emitter scale(float x, float y, float z) {
			config.shape.setScale(new NumberFunction3(x, y, z));
			return this;
		}

		/** Particles laid on these points (relative to the effect), in order, with these starting velocities (or none). */
		Emitter points(List<Vector3f> points, List<Vector3f> velocities) {
			return shape(new PointsShape(points, velocities));
		}

		/** Particles lie flat, facing up (marks on the ground), or stand upright. */
		Emitter flat() {
			config.renderer.setRenderMode(ParticleRendererSetting.Mode.Horizontal);
			return this;
		}

		/** Waits this many ticks before the first particle. */
		Emitter delay(int ticks) {
			config.setStartDelay(NumberFunction.constant(ticks));
			return this;
		}

		/** Draws the particles with this sprite instead of Photon's soft circle. */
		Emitter texture(ResourceLocation texture) {
			MaterialSetting old = config.renderer.getMaterials().isEmpty() ? null : config.renderer.getMaterials().get(0);
			MaterialSetting material = new MaterialSetting(new TextureMaterial(texture));
			if (old != null) {
				material.setRenderStateSource(old.getRenderStateSource());
				material.setBlend(old.getBlendMode());
			}
			config.renderer.getMaterials().clear();
			config.renderer.getMaterials().add(material);
			return this;
		}

		Emitter offset(float x, float y, float z) {
			emitter.updatePos(new Vector3f(x, y, z));
			return this;
		}

		Emitter burst(int count) {
			EmissionSetting.Burst burst = new EmissionSetting.Burst();
			burst.setCount(NumberFunction.constant(count));
			config.emission.getBursts().add(burst);
			return this;
		}

		/** Particles per tick for the emitter's whole duration. */
		Emitter flow(float perTick) {
			config.emission.setEmissionRate(NumberFunction.constant(perTick));
			return this;
		}

		Emitter life(int min, int max) {
			config.setStartLifetime(new RandomConstant(min, max));
			return this;
		}

		Emitter speed(float min, float max) {
			config.setStartSpeed(new RandomConstant(min, max));
			return this;
		}

		Emitter size(float min, float max) {
			config.setStartSize(new NumberFunction3(new RandomConstant(min, max), new RandomConstant(min, max), new RandomConstant(min, max)));
			return this;
		}

		/** Colours over the particle's life, ARGB, spread evenly from birth to death. */
		Emitter colors(int... argb) {
			config.colorOverLifetime.setEnable(true);
			config.colorOverLifetime.setColor(new Gradient(new GradientColor(argb)));
			return this;
		}

		/** Size over life as a multiplier: grows from {@code from} to {@code peak} early, then to {@code to}. */
		Emitter sizeCurve(float from, float peak, float to) {
			float top = Math.max(1, Math.max(from, Math.max(peak, to)));
			Curve curve = new Curve(0, top, 0, top, "life", "size",
					new ECBCurves(0, from / top, 0.1f, peak / top, 0.2f, peak / top, 0.3f, peak / top,
							0.3f, peak / top, 0.6f, peak / top, 0.8f, to / top, 1, to / top));
			config.sizeOverLifetime.setEnable(true);
			config.sizeOverLifetime.setSize(new NumberFunction3(curve, curve, curve));
			return this;
		}

		/** Pull towards the emitter (negative) or push away, and swirl around its vertical axis. */
		Emitter swirl(float radial, float orbitY) {
			config.velocityOverLifetime.setEnable(true);
			config.velocityOverLifetime.setRadial(NumberFunction.constant(radial));
			config.velocityOverLifetime.setOrbital(new NumberFunction3(0, orbitY, 0));
			return this;
		}

		/** A steady force (gravity pressing down is negative y), in blocks per tick². */
		Emitter force(float x, float y, float z) {
			config.forceOverLifetime.setEnable(true);
			config.forceOverLifetime.setForce(new NumberFunction3(x, y, z));
			return this;
		}

		Emitter noise(float strength) {
			config.noise.setEnable(true);
			config.noise.setPosition(new NumberFunction3(strength, strength, strength));
			return this;
		}

		/** Particles stay where they were born instead of moving with the emitter. */
		Emitter world() {
			config.setSimulationSpace(ParticleConfig.Space.World);
			return this;
		}

		/** Glowing light, added onto what's behind. Dark colours vanish in it: use {@link #ink()} for those. */
		Emitter glow() {
			for (MaterialSetting material : config.renderer.getMaterials()) {
				material.setRenderStateSource(MaterialSetting.RenderStateSource.CUSTOM);
				material.setBlend(BlendMode.Preset.ADDITIVE.create());
			}
			return this;
		}

		/** Real colour over what's behind: the darkness of the void itself. */
		Emitter ink() {
			for (MaterialSetting material : config.renderer.getMaterials()) {
				material.setRenderStateSource(MaterialSetting.RenderStateSource.CUSTOM);
				material.setBlend(BlendMode.Preset.ALPHA.create());
			}
			return this;
		}
	}

	/** Moves, turns or stretches the whole effect each frame; {@code age} is in ticks since it started. */
	interface Animation {
		void frame(IFXObject root, float age);
	}

	/** Anchors a played effect: a fixed point, or an entity followed frame by frame. */
	private static final class Anchor implements IEffectExecutor {
		private final Level level;
		private final Entity follow;
		private final Animation animation;
		private final long start;
		private FXRuntime runtime;

		Anchor(Level level, Entity follow, Animation animation) {
			this.level = level;
			this.follow = follow;
			this.animation = animation;
			this.start = level.getGameTime();
		}

		@Override
		public Level getLevel() {
			return level;
		}

		@Override
		public Entity owner() {
			return follow;
		}

		@Override
		public void updateFXObjectFrame(IFXObject object, float partialTicks) {
			if (animation != null && runtime != null && object == runtime.root) {
				animation.frame(object, level.getGameTime() - start + partialTicks);
			}
			if (follow != null && runtime != null && object == runtime.root) {
				if (follow.isRemoved()) {
					runtime.destroy(false);
					return;
				}
				Vec3 at = follow.getPosition(partialTicks);
				object.updatePos(new Vector3f((float) at.x, (float) at.y, (float) at.z));
			}
		}
	}

	/** Plays the emitters together at a point, or following an entity (then {@code at} is ignored), after a delay in ticks. */
	static void play(Level level, Vec3 at, Entity follow, int delay, Emitter... emitters) {
		play(level, at, follow, delay, null, emitters);
	}

	static void play(Level level, Vec3 at, Entity follow, int delay, Animation animation, Emitter... emitters) {
		List<IFXObject> objects = new ArrayList<>();
		for (Emitter emitter : emitters) {
			objects.add(emitter.emitter);
		}
		FXRuntime runtime = new FXRuntime(new FXData(objects));
		Anchor anchor = new Anchor(level, follow, animation);
		anchor.runtime = runtime;
		Vec3 start = follow != null ? follow.position() : at;
		runtime.root.updatePos(new Vector3f((float) start.x, (float) start.y, (float) start.z));
		runtime.emit(anchor, delay);
	}
}
