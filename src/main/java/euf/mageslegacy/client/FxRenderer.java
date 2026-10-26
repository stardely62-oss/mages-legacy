package euf.mageslegacy.client;

import euf.mageslegacy.fx.Fx;
import euf.mageslegacy.fx.ModParticles;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderContext;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.particle.Particle;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.entity.Entity;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.RotationAxis;
import net.minecraft.util.math.Vec3d;
import org.joml.Matrix4f;

/**
 * Draws the ability effects the server announces (see {@link Fx}) as glowing additive geometry: spreading rings,
 * domes with a bright rim and turning bands, ribbons of light along a path, rising pillars; and a tint over the edges
 * of the screen for the player's own big moments.
 */
public final class FxRenderer {
	private static final List<Effect> EFFECTS = new ArrayList<>();
	private static final List<Effect> SCREENS = new ArrayList<>();
	/** The player's own camera: shakes and rushes of the field of view, each fading out over its ticks. */
	private static final List<float[]> SHAKES = new ArrayList<>();
	private static final List<float[]> FOVS = new ArrayList<>();
	/** Until when (client ticks) the server is carrying this player along a path. */
	private static long flightUntil;
	/** Whether the shapes now being drawn are the ink under the glow, and how thick the current effect lays it. */
	private static boolean inkPass;
	private static float inkStrength;
	/** The camera this frame: nothing is drawn right at the eye (see {@link #vertex}). */
	private static Vec3d eye = Vec3d.ZERO;

	private FxRenderer() {
	}

	private abstract static class Effect {
		final int ticks;
		final int rgb;
		/** Drawn twice: as real colour first, then as glow (see {@link Fx#INK}). */
		final boolean ink;
		int age;

		Effect(int ticks, int rgb) {
			this.ticks = Math.max(1, ticks);
			this.rgb = rgb & 0xFFFFFF;
			this.ink = (rgb & Fx.INK) != 0;
		}

		/** 0 at the start, 1 at the end, smooth between ticks. */
		float progress(float delta) {
			return MathHelper.clamp((age + delta) / ticks, 0, 1);
		}

		/** Once a client tick, for effects that make particles of their own. */
		void tick() {
		}

		/** How thick its ink is: a flash or a ghost only tints, a stream of blood is solid. */
		float inkStrength() {
			return 0.85f;
		}

		abstract void draw(Matrix4f m, VertexConsumer vc, Vec3d cam, float delta);
	}

	public static void read(PacketByteBuf buf) {
		int kind = buf.readVarInt();
		int ticks = buf.readVarInt();
		int rgb = buf.readInt();
		switch (kind) {
			case Fx.RING -> EFFECTS.add(new Ring(ticks, rgb, readVec(buf), buf.readFloat(), buf.readFloat()));
			case Fx.SPHERE -> EFFECTS.add(new Sphere(ticks, rgb, readVec(buf), buf.readFloat(), buf.readVarInt(), buf.readBoolean()));
			case Fx.RIBBON -> {
				int rgb2 = buf.readInt();
				float height = buf.readFloat();
				int count = buf.readVarInt();
				List<Vec3d> points = new ArrayList<>();
				for (int i = 0; i < count; i++) {
					points.add(readVec(buf));
				}
				EFFECTS.add(new Ribbon(ticks, rgb, rgb2, height, points));
			}
			case Fx.PILLAR -> EFFECTS.add(new Pillar(ticks, rgb, readVec(buf), buf.readFloat(), buf.readFloat()));
			case Fx.FLIGHT -> flightUntil = ClientRaceState.clientTicks() + ticks + 3;
			case Fx.SCREEN -> SCREENS.add(new Effect(ticks, rgb) {
				@Override
				void draw(Matrix4f m, VertexConsumer vc, Vec3d cam, float delta) {
				}
			});
			case Fx.SLASH -> EFFECTS.add(new Slash(ticks, rgb, readVec(buf), readVec(buf), buf.readFloat(), buf.readFloat()));
			case Fx.TENDRIL -> EFFECTS.add(new Tendril(ticks, rgb, buf.readInt(), buf.readVarInt(), buf.readVarInt()));
			case Fx.SIGIL -> EFFECTS.add(new Sigil(ticks, rgb, readVec(buf), buf.readVarInt(), buf.readFloat()));
			case Fx.HELIX -> EFFECTS.add(new Helix(ticks, rgb, buf.readVarInt(), buf.readFloat(), buf.readFloat(), buf.readVarInt(), buf.readBoolean()));
			case Fx.GHOSTS -> {
				float yaw = buf.readFloat();
				int count = buf.readVarInt();
				List<Vec3d> spots = new ArrayList<>();
				for (int i = 0; i < count; i++) {
					spots.add(readVec(buf));
				}
				EFFECTS.add(new Ghosts(ticks, rgb, yaw, spots));
			}
			case Fx.BURST -> EFFECTS.add(new Burst(ticks, rgb, readVec(buf), buf.readFloat()));
			case Fx.AURA -> EFFECTS.add(new Aura(ticks, rgb, buf.readVarInt()));
			case Fx.SWARM -> EFFECTS.add(new Swarm(ticks, buf.readVarInt(), buf.readVarInt()));
			case Fx.SHAKE -> SHAKES.add(new float[] {ticks, 0, buf.readFloat()});
			case Fx.FOV -> FOVS.add(new float[] {ticks, 0, buf.readFloat()});
			case Fx.PULSE -> SCREENS.add(new Pulse(ticks, rgb, buf.readVarInt()));
			default -> {
			}
		}
	}

	private static Vec3d readVec(PacketByteBuf buf) {
		return new Vec3d(buf.readDouble(), buf.readDouble(), buf.readDouble());
	}

	public static void tick() {
		for (List<Effect> list : List.of(EFFECTS, SCREENS)) {
			for (Iterator<Effect> it = list.iterator(); it.hasNext(); ) {
				Effect e = it.next();
				e.tick();
				if (++e.age >= e.ticks) {
					it.remove();
				}
			}
		}
		for (List<float[]> list : List.of(SHAKES, FOVS)) {
			list.removeIf(f -> ++f[1] >= f[0]);
		}
	}

	public static void clear() {
		EFFECTS.clear();
		SCREENS.clear();
		SHAKES.clear();
		FOVS.clear();
		flightUntil = 0;
	}

	/** Shakes the camera (called as the view is set up, where a hurt tilts it): strong at first, dying away. */
	public static void shake(MatrixStack matrices, float delta) {
		if (SHAKES.isEmpty()) {
			return;
		}
		float pitch = 0;
		float roll = 0;
		float time = ClientRaceState.clientTicks() + delta;
		for (float[] f : SHAKES) {
			float left = 1 - MathHelper.clamp((f[1] + delta) / f[0], 0, 1);
			float s = f[2] * left * left;
			// Uneven sines, so it judders rather than sways
			pitch += s * (MathHelper.sin(time * 2.9f) * 0.6f + MathHelper.sin(time * 5.3f) * 0.4f);
			roll += s * (MathHelper.sin(time * 3.7f + 1) * 0.6f + MathHelper.sin(time * 6.1f) * 0.4f);
		}
		matrices.multiply(RotationAxis.POSITIVE_X.rotationDegrees(pitch));
		matrices.multiply(RotationAxis.POSITIVE_Z.rotationDegrees(roll));
	}

	/** How much wider the view is now: a rush springs out in a couple of ticks and eases back. */
	public static double fovScale(float delta) {
		double scale = 1;
		for (float[] f : FOVS) {
			float t = MathHelper.clamp((f[1] + delta) / f[0], 0, 1);
			float envelope = t < 0.12f ? t / 0.12f : (1 - (t - 0.12f) / 0.88f) * (1 - (t - 0.12f) / 0.88f);
			scale += f[2] * envelope;
		}
		return scale;
	}

	/** The effects being drawn now, by kind, for the test harness's log. */
	public static String describe() {
		StringBuilder sb = new StringBuilder();
		for (Effect e : EFFECTS) {
			sb.append(e.getClass().getSimpleName()).append('@').append(e.age).append('/').append(e.ticks).append(' ');
		}
		for (Effect e : SCREENS) {
			sb.append(e instanceof Pulse ? "Pulse" : "Screen").append('@').append(e.age).append('/').append(e.ticks).append(' ');
		}
		return sb.toString().trim();
	}

	/** Whether the server is carrying this player along a path right now (its steps are then smoothed). */
	public static boolean inFlight() {
		return ClientRaceState.clientTicks() <= flightUntil;
	}

	public static void render(WorldRenderContext context) {
		if (EFFECTS.isEmpty()) {
			return;
		}
		VertexConsumerProvider.Immediate immediate = MinecraftClient.getInstance().getBufferBuilders().getEntityVertexConsumers();
		Vec3d cam = context.camera().getPos();
		eye = cam;
		context.matrixStack().push();
		context.matrixStack().translate(-cam.x, -cam.y, -cam.z);
		Matrix4f m = context.matrixStack().peek().getPositionMatrix();
		// The ink first, so the glow lands on top of it
		inkPass = true;
		VertexConsumer ink = immediate.getBuffer(FxLayers.INK);
		for (Effect e : EFFECTS) {
			if (e.ink) {
				inkStrength = e.inkStrength();
				e.draw(m, ink, cam, context.tickDelta());
			}
		}
		immediate.draw(FxLayers.INK);
		inkPass = false;
		VertexConsumer vc = immediate.getBuffer(RenderLayer.getLightning());
		for (Effect e : EFFECTS) {
			e.draw(m, vc, cam, context.tickDelta());
		}
		context.matrixStack().pop();
		immediate.draw(RenderLayer.getLightning());
	}

	/** The player's own moments: a flash, then the screen's edges glow in the ability's colour and fade. */
	public static void renderScreen(DrawContext ctx, float delta) {
		if (SCREENS.isEmpty()) {
			return;
		}
		int w = ctx.getScaledWindowWidth();
		int h = ctx.getScaledWindowHeight();
		for (Effect e : SCREENS) {
			float t = e.progress(delta);
			if (e instanceof Pulse pulse) {
				edges(ctx, w, h, e.rgb, 0.4f * pulse.beat(delta) * Math.min(1, (1 - t) * 6), 7);
				continue;
			}
			if (t < 0.15f) {
				ctx.fill(0, 0, w, h, argb(e.rgb, 0.35f * (1 - t / 0.15f)));
			}
			edges(ctx, w, h, e.rgb, 0.55f * (1 - t), 6);
		}
	}

	/** The screen's edges glowing in a colour, {@code 1/depth} of the screen deep. */
	private static void edges(DrawContext ctx, int w, int h, int rgb, float a, int depth) {
		if (a <= 0.005f) {
			return;
		}
		int band = Math.max(8, Math.min(w, h) / depth);
		ctx.fillGradient(0, 0, w, band, argb(rgb, a), argb(rgb, 0));
		ctx.fillGradient(0, h - band, w, h, argb(rgb, 0), argb(rgb, a));
		// Sideways gradients aren't built in: thin strips fading towards the middle
		for (int i = 0; i < band; i += 2) {
			int c = argb(rgb, a * (1 - (float) i / band) * (1 - (float) i / band));
			ctx.fill(i, 0, i + 2, h, c);
			ctx.fill(w - i - 2, 0, w - i, h, c);
		}
	}

	private static int argb(int rgb, float alpha) {
		return ((int) (MathHelper.clamp(alpha, 0, 1) * 255) << 24) | (rgb & 0xFFFFFF);
	}

	private static void vertex(VertexConsumer vc, Matrix4f m, double x, double y, double z, int rgb, float alpha) {
		// Right at the eye any shape would fill the view: it thins away there
		double d = Math.sqrt(eye.squaredDistanceTo(x, y, z));
		if (d < 1.0) {
			alpha *= (float) MathHelper.clamp((d - 0.35) / 0.65, 0, 1);
		}
		vc.vertex(m, (float) x, (float) y, (float) z).color((rgb >> 16) & 0xFF, (rgb >> 8) & 0xFF, rgb & 0xFF,
				(int) (MathHelper.clamp(alpha, 0, 1) * 255)).next();
	}

	/**
	 * A quad seen from both sides (the glow layer culls back faces). As ink it is the colour a shade deeper and once
	 * only (the ink layer shows both faces), and white-hot edges stay glow alone.
	 */
	private static void quad(VertexConsumer vc, Matrix4f m, Vec3d a, Vec3d b, Vec3d c, Vec3d d, int rgb, float aa, float ab, float ac, float ad) {
		if (inkPass) {
			if ((rgb & 0xFFFFFF) == 0xFFFFFF) {
				return;
			}
			int deep = lerpColor(rgb, 0x000000, 0.3f);
			float k = inkStrength;
			vertex(vc, m, a.x, a.y, a.z, deep, aa * k);
			vertex(vc, m, b.x, b.y, b.z, deep, ab * k);
			vertex(vc, m, c.x, c.y, c.z, deep, ac * k);
			vertex(vc, m, d.x, d.y, d.z, deep, ad * k);
			return;
		}
		vertex(vc, m, a.x, a.y, a.z, rgb, aa);
		vertex(vc, m, b.x, b.y, b.z, rgb, ab);
		vertex(vc, m, c.x, c.y, c.z, rgb, ac);
		vertex(vc, m, d.x, d.y, d.z, rgb, ad);
		vertex(vc, m, d.x, d.y, d.z, rgb, ad);
		vertex(vc, m, c.x, c.y, c.z, rgb, ac);
		vertex(vc, m, b.x, b.y, b.z, rgb, ab);
		vertex(vc, m, a.x, a.y, a.z, rgb, aa);
	}

	private static float easeOut(float t) {
		return 1 - (1 - t) * (1 - t) * (1 - t);
	}

	/** A band on the ground that spreads outwards, bright at its edge, fading as it goes. */
	private static final class Ring extends Effect {
		final Vec3d at;
		final float r0;
		final float r1;

		Ring(int ticks, int rgb, Vec3d at, float r0, float r1) {
			super(ticks, rgb);
			this.at = at.add(0, 0.06, 0);
			this.r0 = r0;
			this.r1 = r1;
		}

		@Override
		float inkStrength() {
			return 0.7f;
		}

		@Override
		void draw(Matrix4f m, VertexConsumer vc, Vec3d cam, float delta) {
			float t = progress(delta);
			float r = r0 + (r1 - r0) * easeOut(t);
			float inner = Math.max(0, r - 0.5f - r * 0.08f);
			float alpha = (float) Math.pow(1 - t, 1.5) * 0.9f;
			int segments = 48;
			for (int i = 0; i < segments; i++) {
				double a0 = Math.PI * 2 * i / segments;
				double a1 = Math.PI * 2 * (i + 1) / segments;
				Vec3d o0 = at.add(Math.cos(a0) * r, 0, Math.sin(a0) * r);
				Vec3d o1 = at.add(Math.cos(a1) * r, 0, Math.sin(a1) * r);
				Vec3d i0 = at.add(Math.cos(a0) * inner, 0, Math.sin(a0) * inner);
				Vec3d i1 = at.add(Math.cos(a1) * inner, 0, Math.sin(a1) * inner);
				quad(vc, m, i0, o0, o1, i1, rgb, 0, alpha, alpha, 0);
				// A low wall of light on the edge, so the wave reads from the side as well
				quad(vc, m, o0, o0.add(0, 0.6, 0), o1.add(0, 0.6, 0), o1, rgb, alpha * 0.8f, 0, 0, alpha * 0.8f);
			}
		}
	}

	/**
	 * A dome (or a whole sphere) that springs up, holds with slowly turning bright bands and a brighter rim where it
	 * is seen edge-on, then fades. It may ride along with an entity.
	 */
	private static final class Sphere extends Effect {
		final Vec3d at;
		final float radius;
		final int follow;
		final boolean full;

		Sphere(int ticks, int rgb, Vec3d at, float radius, int follow, boolean full) {
			super(ticks, rgb);
			this.at = at;
			this.radius = radius;
			this.follow = follow;
			this.full = full;
		}

		@Override
		float inkStrength() {
			return 0.4f;
		}

		@Override
		void draw(Matrix4f m, VertexConsumer vc, Vec3d cam, float delta) {
			Vec3d center = at;
			if (follow >= 0 && MinecraftClient.getInstance().world != null) {
				Entity e = MinecraftClient.getInstance().world.getEntityById(follow);
				if (e != null) {
					center = e.getLerpedPos(delta);
				}
			}
			float life = age + delta;
			// Springs in over 6 ticks with a small overshoot, fades over the last 8
			float grow = Math.min(1, life / 6f);
			float scale = grow < 1 ? 1 + 2.2f * (float) Math.pow(grow - 1, 3) + 1.2f * (float) Math.pow(grow - 1, 2) : 1;
			float fade = Math.min(1, (ticks - life) / 8f);
			float r = radius * Math.max(0.05f, scale);
			// Seen from inside, the shell only hints at itself: it must not wash the whole view in its colour
			if (cam.squaredDistanceTo(center) < r * r) {
				fade *= 0.3f;
			}
			int lat = 12;
			int lon = 32;
			double spin = life * 0.015;
			for (int i = full ? -lat : 0; i < lat; i++) {
				double p0 = Math.PI / 2 * i / lat;
				double p1 = Math.PI / 2 * (i + 1) / lat;
				for (int j = 0; j < lon; j++) {
					double t0 = Math.PI * 2 * j / lon + spin;
					double t1 = Math.PI * 2 * (j + 1) / lon + spin;
					Vec3d[] v = {point(center, r, p0, t0), point(center, r, p0, t1), point(center, r, p1, t1), point(center, r, p1, t0)};
					// Every fourth band glows brighter, a lattice slowly turning
					float band = j % 4 == 0 ? 0.35f : 0;
					float[] a = new float[4];
					for (int k = 0; k < 4; k++) {
						Vec3d normal = v[k].subtract(center).normalize();
						Vec3d view = v[k].subtract(cam).normalize();
						float edge = 1 - (float) Math.abs(normal.dotProduct(view));
						a[k] = (0.05f + 0.42f * edge * edge + band * 0.25f) * fade;
					}
					quad(vc, m, v[0], v[1], v[2], v[3], rgb, a[0], a[1], a[2], a[3]);
				}
			}
			if (!full) {
				// A bright rim where the dome meets the ground
				drawRim(m, vc, center, r, fade);
			}
		}

		private void drawRim(Matrix4f m, VertexConsumer vc, Vec3d center, float r, float fade) {
			int segments = 48;
			for (int i = 0; i < segments; i++) {
				double a0 = Math.PI * 2 * i / segments;
				double a1 = Math.PI * 2 * (i + 1) / segments;
				Vec3d o0 = center.add(Math.cos(a0) * r, 0.05, Math.sin(a0) * r);
				Vec3d o1 = center.add(Math.cos(a1) * r, 0.05, Math.sin(a1) * r);
				Vec3d i0 = center.add(Math.cos(a0) * (r - 0.4), 0.05, Math.sin(a0) * (r - 0.4));
				Vec3d i1 = center.add(Math.cos(a1) * (r - 0.4), 0.05, Math.sin(a1) * (r - 0.4));
				quad(vc, m, i0, o0, o1, i1, rgb, 0, 0.8f * fade, 0.8f * fade, 0);
			}
		}

		private static Vec3d point(Vec3d c, double r, double pitch, double yaw) {
			return c.add(Math.cos(pitch) * Math.cos(yaw) * r, Math.sin(pitch) * r, Math.cos(pitch) * Math.sin(yaw) * r);
		}
	}

	/**
	 * A streak of light along a path: two crossed ribbons, shading from one colour to the other, its tail drawing in
	 * after the head as it fades.
	 */
	private static final class Ribbon extends Effect {
		final int rgb2;
		final float height;
		final List<Vec3d> points;

		Ribbon(int ticks, int rgb, int rgb2, float height, List<Vec3d> points) {
			super(ticks, rgb);
			this.rgb2 = rgb2;
			this.height = height;
			this.points = points;
		}

		@Override
		float inkStrength() {
			return 0.45f;
		}

		@Override
		void draw(Matrix4f m, VertexConsumer vc, Vec3d cam, float delta) {
			float t = progress(delta);
			int n = points.size();
			// The tail draws in towards the head over the effect's life
			int from = (int) (t * t * (n - 1));
			float alpha = 0.85f * (1 - t * 0.6f);
			for (int i = Math.max(0, from); i < n - 1; i++) {
				Vec3d a = points.get(i).add(0, 1.0, 0);
				Vec3d b = points.get(i + 1).add(0, 1.0, 0);
				Vec3d dir = b.subtract(a);
				if (dir.lengthSquared() < 1.0E-6) {
					continue;
				}
				Vec3d side = dir.crossProduct(new Vec3d(0, 1, 0)).normalize().multiply(height * 0.35);
				Vec3d up = new Vec3d(0, height / 2, 0);
				float s0 = (float) (i - from) / Math.max(1, n - 1 - from);
				float s1 = (float) (i + 1 - from) / Math.max(1, n - 1 - from);
				int c = lerpColor(rgb, rgb2, (float) i / (n - 1));
				float a0 = alpha * s0;
				float a1 = alpha * s1;
				// Upright ribbon, brightest in its middle line
				quad(vc, m, a.subtract(up), b.subtract(up), b, a, c, 0, 0, a1, a0);
				quad(vc, m, a, b, b.add(up), a.add(up), c, a0, a1, 0, 0);
				// And a flat one across it, so the streak reads from above too
				quad(vc, m, a.subtract(side), b.subtract(side), b, a, c, 0, 0, a1, a0);
				quad(vc, m, a, b, b.add(side), a.add(side), c, a0, a1, 0, 0);
			}
		}
	}

	/** A pillar of light that shoots up from the ground and fades, brightest at its foot. */
	private static final class Pillar extends Effect {
		final Vec3d at;
		final float radius;
		final float height;

		Pillar(int ticks, int rgb, Vec3d at, float radius, float height) {
			super(ticks, rgb);
			this.at = at;
			this.radius = radius;
			this.height = height;
		}

		@Override
		float inkStrength() {
			return 0.5f;
		}

		@Override
		void draw(Matrix4f m, VertexConsumer vc, Vec3d cam, float delta) {
			float t = progress(delta);
			float h = height * easeOut(Math.min(1, t * 3));
			float alpha = 0.8f * (1 - t);
			int segments = 12;
			for (int i = 0; i < segments; i++) {
				double a0 = Math.PI * 2 * i / segments;
				double a1 = Math.PI * 2 * (i + 1) / segments;
				Vec3d b0 = at.add(Math.cos(a0) * radius, 0, Math.sin(a0) * radius);
				Vec3d b1 = at.add(Math.cos(a1) * radius, 0, Math.sin(a1) * radius);
				quad(vc, m, b0, b1, b1.add(0, h, 0), b0.add(0, h, 0), rgb, alpha, alpha, 0, 0);
			}
		}
	}

	private static int lerpColor(int a, int b, float t) {
		int r = (int) MathHelper.lerp(t, (a >> 16) & 0xFF, (b >> 16) & 0xFF);
		int g = (int) MathHelper.lerp(t, (a >> 8) & 0xFF, (b >> 8) & 0xFF);
		int bl = (int) MathHelper.lerp(t, a & 0xFF, b & 0xFF);
		return (r << 16) | (g << 8) | bl;
	}

	// ---- shared shapes ----

	/** Where an entity is now, smoothly between ticks; null if this client doesn't have it. */
	private static Vec3d lerped(int id, float delta) {
		MinecraftClient client = MinecraftClient.getInstance();
		Entity e = client.world == null ? null : client.world.getEntityById(id);
		return e == null ? null : e.getLerpedPos(delta);
	}

	/** A soft line of light from a to b, {@code width} across in the plane facing {@code normal}: bright down its middle. */
	private static void glowLine(VertexConsumer vc, Matrix4f m, Vec3d a, Vec3d b, Vec3d normal, double width, int rgb, float alpha) {
		Vec3d dir = b.subtract(a);
		Vec3d side = dir.crossProduct(normal);
		if (side.lengthSquared() < 1.0E-8) {
			return;
		}
		side = side.normalize().multiply(width / 2);
		quad(vc, m, a.subtract(side), b.subtract(side), b, a, rgb, 0, 0, alpha, alpha);
		quad(vc, m, a, b, b.add(side), a.add(side), rgb, alpha, alpha, 0, 0);
	}

	/**
	 * A ribbon through {@code points} that always turns its face to the camera: {@code widths}, {@code colors} and
	 * {@code alphas} per point, bright along its spine and soft at its edges.
	 */
	private static void strip(VertexConsumer vc, Matrix4f m, Vec3d cam, List<Vec3d> points, float[] widths, int[] colors, float[] alphas) {
		for (int i = 0; i < points.size() - 1; i++) {
			Vec3d a = points.get(i);
			Vec3d b = points.get(i + 1);
			Vec3d dir = b.subtract(a);
			Vec3d sideA = dir.crossProduct(cam.subtract(a));
			Vec3d sideB = dir.crossProduct(cam.subtract(b));
			if (sideA.lengthSquared() < 1.0E-8 || sideB.lengthSquared() < 1.0E-8) {
				continue;
			}
			sideA = sideA.normalize().multiply(widths[i] / 2);
			sideB = sideB.normalize().multiply(widths[i + 1] / 2);
			quad(vc, m, a.subtract(sideA), b.subtract(sideB), b, a, colors[i], 0, 0, alphas[i + 1], alphas[i]);
			quad(vc, m, a, b, b.add(sideB), a.add(sideA), colors[i], alphas[i], alphas[i + 1], 0, 0);
		}
	}

	/** A ring of light lying flat at {@code center}. */
	private static void flatRing(VertexConsumer vc, Matrix4f m, Vec3d center, double r, double width, int rgb, float alpha, int segments) {
		Vec3d up = new Vec3d(0, 1, 0);
		for (int i = 0; i < segments; i++) {
			double a0 = Math.PI * 2 * i / segments;
			double a1 = Math.PI * 2 * (i + 1) / segments;
			glowLine(vc, m, center.add(Math.cos(a0) * r, 0, Math.sin(a0) * r), center.add(Math.cos(a1) * r, 0, Math.sin(a1) * r), up, width, rgb, alpha);
		}
	}

	/** Overshoots a little and settles: things that spring open. */
	private static float easeOutBack(float t) {
		float c = 1.7f;
		return 1 + (c + 1) * (float) Math.pow(t - 1, 3) + c * (float) Math.pow(t - 1, 2);
	}

	/** A heart's beat over a phase of 0..1: a strong lub and, just after, a softer dub. */
	static float heartbeat(float phase) {
		float lub = (float) Math.exp(-Math.pow(phase / 0.07, 2)) + (float) Math.exp(-Math.pow((phase - 1) / 0.07, 2));
		float dub = (float) Math.exp(-Math.pow((phase - 0.26) / 0.07, 2));
		return Math.min(1, lub + 0.7f * dub);
	}

	// ---- the new effects ----

	/** A crescent cut through a point: it sweeps across in a flash, white-hot along its edge, then widens and fades. */
	private static final class Slash extends Effect {
		final Vec3d center;
		final Vec3d forward;
		final Vec3d across;
		final Vec3d normal;
		final float size;

		Slash(int ticks, int rgb, Vec3d at, Vec3d dir, float size, float roll) {
			super(ticks, rgb);
			Vec3d f = dir.lengthSquared() < 1.0E-6 ? new Vec3d(0, 0, 1) : dir.normalize();
			Vec3d right = f.crossProduct(new Vec3d(0, 1, 0));
			right = right.lengthSquared() < 1.0E-6 ? new Vec3d(1, 0, 0) : right.normalize();
			Vec3d up = right.crossProduct(f).normalize();
			double r = Math.toRadians(roll);
			this.forward = f;
			this.across = right.multiply(Math.cos(r)).add(up.multiply(Math.sin(r)));
			this.normal = this.across.crossProduct(f).normalize();
			this.size = size;
			// The arc bulges forward through the point it cuts
			this.center = at.subtract(f.multiply(size * 0.55));
		}

		@Override
		float inkStrength() {
			return 0.9f;
		}

		@Override
		void draw(Matrix4f m, VertexConsumer vc, Vec3d cam, float delta) {
			float t = progress(delta);
			float head = easeOut(Math.min(1, t / 0.3f));
			float tail = t < 0.3f ? 0 : (t - 0.3f) / 0.7f;
			float alpha = 1 - tail * tail;
			double radius = size * (1 + 0.3 * t);
			int segments = 28;
			Vec3d prevO = null;
			Vec3d prevC = null;
			Vec3d prevI = null;
			for (int k = 0; k <= segments; k++) {
				float s = (float) k / segments;
				if (s < tail || s > head) {
					prevO = null;
					continue;
				}
				double angle = (s - 0.5) * Math.toRadians(150);
				Vec3d out = across.multiply(Math.sin(angle)).add(forward.multiply(Math.cos(angle)));
				// Thin at its tips, thick in its belly
				double width = size * 0.32 * Math.sin(Math.PI * s);
				Vec3d o = center.add(out.multiply(radius));
				Vec3d c = center.add(out.multiply(radius - width * 0.3));
				Vec3d i = center.add(out.multiply(radius - width));
				if (prevO != null) {
					quad(vc, m, prevI, i, c, prevC, rgb, 0, 0, alpha * 0.9f, alpha * 0.9f);
					quad(vc, m, prevC, c, o, prevO, rgb, alpha * 0.9f, alpha * 0.9f, 0, 0);
					// The white-hot edge
					Vec3d e0 = prevC.add(prevO).multiply(0.5);
					Vec3d e1 = c.add(o).multiply(0.5);
					quad(vc, m, prevC, c, e1, e0, 0xFFFFFF, alpha, alpha, alpha * 0.6f, alpha * 0.6f);
				}
				prevO = o;
				prevC = c;
				prevI = i;
			}
		}
	}

	/** A winding stream from one entity to another, thick where it leaves and thin where it arrives, pulses running along. */
	private static final class Tendril extends Effect {
		final int rgb2;
		final int from;
		final int to;
		final float seed;

		Tendril(int ticks, int rgb, int rgb2, int from, int to) {
			super(ticks, rgb);
			this.rgb2 = rgb2;
			this.from = from;
			this.to = to;
			this.seed = (from * 31 + to) % 100 / 10f;
		}

		@Override
		float inkStrength() {
			return 1.0f;
		}

		@Override
		void draw(Matrix4f m, VertexConsumer vc, Vec3d cam, float delta) {
			Vec3d a = lerped(from, delta);
			Vec3d b = lerped(to, delta);
			if (a == null || b == null) {
				return;
			}
			MinecraftClient client = MinecraftClient.getInstance();
			Entity source = client.world.getEntityById(from);
			a = a.add(0, source.getHeight() * 0.6, 0);
			b = b.add(0, 1.0, 0);
			float life = age + delta;
			float reach = Math.min(1, life / 4f);
			float fade = Math.min(1, (ticks - life) / 6f);
			Vec3d way = b.subtract(a);
			Vec3d side = way.crossProduct(new Vec3d(0, 1, 0));
			side = side.lengthSquared() < 1.0E-6 ? new Vec3d(1, 0, 0) : side.normalize();
			double lift = 0.6 + way.length() * 0.15;
			Vec3d p1 = a.add(0, lift, 0).add(side.multiply(Math.sin(life * 0.3 + seed) * 0.9));
			Vec3d p2 = b.add(0, lift * 0.6, 0).subtract(side.multiply(Math.cos(life * 0.27 + seed) * 0.7));
			int n = 28;
			List<Vec3d> points = new ArrayList<>();
			float[] widths = new float[n + 1];
			int[] colors = new int[n + 1];
			float[] alphas = new float[n + 1];
			for (int k = 0; k <= n; k++) {
				float s = (float) k / n * reach;
				points.add(bezier(a, p1, p2, b, s));
				// Bright pulses carried from the victim to the drinker
				float pulse = 0;
				for (int j = 0; j < 3; j++) {
					float at = (life * 0.07f + j / 3f) % 1;
					pulse = Math.max(pulse, (float) Math.exp(-Math.pow((s - at) / 0.06, 2)));
				}
				widths[k] = (0.5f * (1 - s) + 0.1f) * (1 + 0.6f * pulse);
				colors[k] = lerpColor(rgb, rgb2, s);
				alphas[k] = (0.45f + 0.55f * pulse) * fade;
			}
			strip(vc, m, cam, points, widths, colors, alphas);
		}

		private static Vec3d bezier(Vec3d a, Vec3d b, Vec3d c, Vec3d d, double t) {
			double u = 1 - t;
			return a.multiply(u * u * u).add(b.multiply(3 * u * u * t)).add(c.multiply(3 * u * t * t)).add(d.multiply(t * t * t));
		}
	}

	/** A circle of runes on the ground: springs open, turns, beats with light like a heart, and fades. */
	private static final class Sigil extends Effect {
		final Vec3d at;
		final int follow;
		final float radius;

		Sigil(int ticks, int rgb, Vec3d at, int follow, float radius) {
			super(ticks, rgb);
			this.at = at;
			this.follow = follow;
			this.radius = radius;
		}

		@Override
		float inkStrength() {
			return 0.9f;
		}

		@Override
		void draw(Matrix4f m, VertexConsumer vc, Vec3d cam, float delta) {
			Vec3d center = at;
			if (follow >= 0) {
				Vec3d riding = lerped(follow, delta);
				if (riding != null) {
					center = riding;
				}
			}
			center = center.add(0, 0.04, 0);
			float life = age + delta;
			float open = easeOutBack(Math.min(1, life / 8f));
			float fade = Math.min(1, (ticks - life) / 10f) * Math.min(1, life / 3f);
			float beat = 0.55f + 0.45f * heartbeat((life % 20) / 20f);
			float a = fade * beat;
			double r = radius * open;
			double spin = life * 0.035;
			Vec3d up = new Vec3d(0, 1, 0);
			// A faint glow over the whole circle, under the lines
			int segments = 64;
			for (int i = 0; i < segments; i++) {
				double a0 = Math.PI * 2 * i / segments;
				double a1 = Math.PI * 2 * (i + 1) / segments;
				quad(vc, m, center, center, center.add(Math.cos(a1) * r, 0, Math.sin(a1) * r), center.add(Math.cos(a0) * r, 0, Math.sin(a0) * r),
						rgb, a * 0.18f, a * 0.18f, 0, 0);
			}
			flatRing(vc, m, center, r, 0.16, rgb, a, segments);
			flatRing(vc, m, center, r * 0.93, 0.05, 0xFFFFFF, a * 0.35f, segments);
			flatRing(vc, m, center, r * 0.72, 0.1, rgb, a * 0.85f, segments);
			flatRing(vc, m, center, r * 0.28, 0.08, rgb, a * 0.8f, 32);
			// A six-pointed star in the inner circle, turning
			for (int tri = 0; tri < 2; tri++) {
				for (int k = 0; k < 3; k++) {
					double p0 = spin + tri * Math.PI / 3 + k * Math.PI * 2 / 3;
					double p1 = spin + tri * Math.PI / 3 + (k + 1) * Math.PI * 2 / 3;
					glowLine(vc, m, center.add(Math.cos(p0) * r * 0.72, 0, Math.sin(p0) * r * 0.72),
							center.add(Math.cos(p1) * r * 0.72, 0, Math.sin(p1) * r * 0.72), up, 0.09, rgb, a * 0.9f);
				}
			}
			// Runes: marks between the rings, turning the other way
			for (int k = 0; k < 18; k++) {
				double p = -spin * 0.6 + k * Math.PI * 2 / 18;
				double inner = r * (k % 3 == 0 ? 0.76 : 0.8);
				double outer = r * (k % 3 == 0 ? 0.9 : 0.86);
				Vec3d dir = new Vec3d(Math.cos(p), 0, Math.sin(p));
				glowLine(vc, m, center.add(dir.multiply(inner)), center.add(dir.multiply(outer)), up, 0.07, rgb, a);
				if (k % 3 == 0) {
					Vec3d across = new Vec3d(-dir.z, 0, dir.x).multiply(r * 0.05);
					Vec3d mid = center.add(dir.multiply((inner + outer) / 2));
					glowLine(vc, m, mid.subtract(across), mid.add(across), up, 0.06, rgb, a * 0.9f);
				}
			}
		}
	}

	/** Strands of light winding up around an entity, rising; at the end they draw into it, or fly apart. */
	private static final class Helix extends Effect {
		final int around;
		final float radius;
		final float height;
		final int strands;
		final boolean inward;

		Helix(int ticks, int rgb, int around, float radius, float height, int strands, boolean inward) {
			super(ticks, rgb);
			this.around = around;
			this.radius = radius;
			this.height = height;
			this.strands = Math.max(1, strands);
			this.inward = inward;
		}

		@Override
		float inkStrength() {
			return 0.9f;
		}

		@Override
		void draw(Matrix4f m, VertexConsumer vc, Vec3d cam, float delta) {
			Vec3d base = lerped(around, delta);
			if (base == null) {
				return;
			}
			float t = progress(delta);
			float life = age + delta;
			float top = easeOut(Math.min(1, t / 0.55f));
			float bottom = t < 0.45f ? 0 : (t - 0.45f) / 0.55f;
			// The last part: the strands close in on the body or open out
			float close = t < 0.6f ? 1 : inward ? 1 - (t - 0.6f) / 0.4f : 1 + (t - 0.6f) * 2.5f;
			int n = 30;
			for (int j = 0; j < strands; j++) {
				List<Vec3d> points = new ArrayList<>();
				float[] widths = new float[n + 1];
				int[] colors = new int[n + 1];
				float[] alphas = new float[n + 1];
				for (int k = 0; k <= n; k++) {
					float s = bottom + (top - bottom) * k / n;
					double angle = Math.PI * 2 * j / strands + s * 7.0 + life * 0.3;
					double r = radius * (1 - 0.35 * s) * close;
					points.add(base.add(Math.cos(angle) * r, s * height, Math.sin(angle) * r));
					float head = (float) k / n;
					widths[k] = 0.05f + 0.12f * head;
					colors[k] = lerpColor(rgb, 0xFF8080, head * head * 0.5f);
					alphas[k] = (0.25f + 0.75f * head) * (1 - bottom * 0.5f);
				}
				strip(vc, m, cam, points, widths, colors, alphas);
			}
		}
	}

	/** Afterimages of a body along a path: glowing silhouettes, the oldest fading first. */
	private static final class Ghosts extends Effect {
		final float yaw;
		final List<Vec3d> spots;

		Ghosts(int ticks, int rgb, float yaw, List<Vec3d> spots) {
			super(ticks, rgb);
			this.yaw = yaw;
			this.spots = spots;
		}

		@Override
		float inkStrength() {
			return 0.45f;
		}

		@Override
		void draw(Matrix4f m, VertexConsumer vc, Vec3d cam, float delta) {
			float t = progress(delta);
			double rad = Math.toRadians(yaw);
			Vec3d fwd = new Vec3d(-Math.sin(rad), 0, Math.cos(rad));
			Vec3d right = new Vec3d(Math.cos(rad), 0, Math.sin(rad));
			for (int i = 0; i < spots.size(); i++) {
				// Older ones (nearer the start) fade sooner
				float own = MathHelper.clamp(t * 1.4f - (1 - (float) (i + 1) / spots.size()) * 0.4f, 0, 1);
				float a = (1 - own) * (0.25f + 0.35f * (i + 1) / spots.size());
				if (a <= 0.01f) {
					continue;
				}
				Vec3d feet = spots.get(i);
				// Head, body, arms, legs
				box(vc, m, feet, right, fwd, 0, 1.75, 0, 0.25, 0.25, 0.25, a);
				box(vc, m, feet, right, fwd, 0, 1.125, 0, 0.25, 0.375, 0.13, a);
				box(vc, m, feet, right, fwd, 0.375, 1.15, -0.05, 0.12, 0.36, 0.12, a * 0.8f);
				box(vc, m, feet, right, fwd, -0.375, 1.15, -0.05, 0.12, 0.36, 0.12, a * 0.8f);
				box(vc, m, feet, right, fwd, 0.125, 0.375, 0, 0.12, 0.375, 0.12, a * 0.8f);
				box(vc, m, feet, right, fwd, -0.125, 0.375, 0, 0.12, 0.375, 0.12, a * 0.8f);
			}
		}

		/** A box of light: centre (x right, y up, z forward) and half-sizes, its faces glowing. */
		private void box(VertexConsumer vc, Matrix4f m, Vec3d feet, Vec3d right, Vec3d fwd, double x, double y, double z, double hx, double hy, double hz,
				float a) {
			Vec3d c = feet.add(right.multiply(x)).add(0, y, 0).add(fwd.multiply(z));
			Vec3d rx = right.multiply(hx);
			Vec3d ry = new Vec3d(0, hy, 0);
			Vec3d rz = fwd.multiply(hz);
			Vec3d[] v = new Vec3d[8];
			for (int k = 0; k < 8; k++) {
				v[k] = c.add(rx.multiply((k & 1) == 0 ? -1 : 1)).add(ry.multiply((k & 2) == 0 ? -1 : 1)).add(rz.multiply((k & 4) == 0 ? -1 : 1));
			}
			int[][] faces = {{0, 1, 3, 2}, {4, 5, 7, 6}, {0, 1, 5, 4}, {2, 3, 7, 6}, {0, 2, 6, 4}, {1, 3, 7, 5}};
			for (int[] f : faces) {
				// Brighter towards the top, as if lit from within
				quad(vc, m, v[f[0]], v[f[1]], v[f[2]], v[f[3]], rgb, a * 0.5f, a * 0.5f, a, a);
			}
		}
	}

	/** A flash: a shell bursting out to its radius in a few ticks, brightest at its rim, and gone. */
	private static final class Burst extends Effect {
		final Vec3d at;
		final float radius;

		Burst(int ticks, int rgb, Vec3d at, float radius) {
			super(ticks, rgb);
			this.at = at;
			this.radius = radius;
		}

		@Override
		float inkStrength() {
			return 0.3f;
		}

		@Override
		void draw(Matrix4f m, VertexConsumer vc, Vec3d cam, float delta) {
			float t = progress(delta);
			double r = radius * easeOut(t);
			float fade = (1 - t) * (1 - t);
			if (cam.squaredDistanceTo(at) < r * r) {
				fade *= 0.35f;
			}
			int lat = 10;
			int lon = 20;
			for (int i = -lat; i < lat; i++) {
				double p0 = Math.PI / 2 * i / lat;
				double p1 = Math.PI / 2 * (i + 1) / lat;
				for (int j = 0; j < lon; j++) {
					double t0 = Math.PI * 2 * j / lon;
					double t1 = Math.PI * 2 * (j + 1) / lon;
					Vec3d[] v = {Sphere.point(at, r, p0, t0), Sphere.point(at, r, p0, t1), Sphere.point(at, r, p1, t1), Sphere.point(at, r, p1, t0)};
					float[] a = new float[4];
					for (int k = 0; k < 4; k++) {
						Vec3d normal = v[k].subtract(at).normalize();
						float edge = 1 - (float) Math.abs(normal.dotProduct(v[k].subtract(cam).normalize()));
						a[k] = (0.08f + 0.7f * edge * edge * edge) * fade;
					}
					quad(vc, m, v[0], v[1], v[2], v[3], rgb, a[0], a[1], a[2], a[3]);
				}
			}
		}
	}

	/** Embers rising off a body for a while, and now and then a breath of blood mist: made by this client. */
	private static final class Aura extends Effect {
		final int around;

		Aura(int ticks, int rgb, int around) {
			super(ticks, rgb);
			this.around = around;
		}

		@Override
		void tick() {
			MinecraftClient client = MinecraftClient.getInstance();
			Entity e = client.world == null ? null : client.world.getEntityById(around);
			if (e == null) {
				return;
			}
			ClientWorld world = client.world;
			for (int i = 0; i < 3; i++) {
				double angle = world.random.nextDouble() * Math.PI * 2;
				double r = 0.35 + world.random.nextDouble() * 0.35;
				world.addParticle(ModParticles.EMBER, e.getX() + Math.cos(angle) * r, e.getY() + world.random.nextDouble() * e.getHeight(),
						e.getZ() + Math.sin(angle) * r, 0, 0, 0);
			}
			if (age % 4 == 0) {
				world.addParticle(ModParticles.BLOOD_MIST, e.getX(), e.getY() + 0.2, e.getZ(), (world.random.nextDouble() - 0.5) * 0.08, 0,
						(world.random.nextDouble() - 0.5) * 0.08);
			}
		}

		@Override
		void draw(Matrix4f m, VertexConsumer vc, Vec3d cam, float delta) {
		}
	}

	/** A body scattered into bats: they circle it while the swarm lasts, trailing blood mist. Made by this client. */
	private static final class Swarm extends Effect {
		final int around;
		final int count;

		Swarm(int ticks, int around, int count) {
			super(ticks, 0);
			this.around = around;
			this.count = count;
		}

		@Override
		void tick() {
			MinecraftClient client = MinecraftClient.getInstance();
			Entity e = client.world == null ? null : client.world.getEntityById(around);
			if (e == null) {
				return;
			}
			if (age == 0) {
				for (int i = 0; i < count; i++) {
					Particle bat = RaceParticles.swarmBat(client.world, e, ticks);
					if (bat != null) {
						client.particleManager.addParticle(bat);
					}
				}
			}
			// The swarm leaves a smear of mist where it passes
			if (age % 2 == 0) {
				client.world.addParticle(ModParticles.BLOOD_MIST, e.getX(), e.getY() + 0.6 + client.world.random.nextDouble(), e.getZ(), 0, 0, 0);
			}
		}

		@Override
		void draw(Matrix4f m, VertexConsumer vc, Vec3d cam, float delta) {
		}
	}

	/** The player's own heart, beating at the screen's edges. */
	private static final class Pulse extends Effect {
		final int beats;

		Pulse(int ticks, int rgb, int beats) {
			super(ticks, rgb);
			this.beats = Math.max(1, beats);
		}

		float beat(float delta) {
			float phase = (age + delta) / ticks * beats % 1;
			return heartbeat(phase);
		}

		@Override
		void draw(Matrix4f m, VertexConsumer vc, Vec3d cam, float delta) {
		}
	}
}
