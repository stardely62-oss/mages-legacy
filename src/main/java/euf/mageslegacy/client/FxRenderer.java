package euf.mageslegacy.client;

import euf.mageslegacy.fx.Fx;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderContext;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.entity.Entity;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.util.math.MathHelper;
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

	private FxRenderer() {
	}

	private abstract static class Effect {
		final int ticks;
		final int rgb;
		int age;

		Effect(int ticks, int rgb) {
			this.ticks = Math.max(1, ticks);
			this.rgb = rgb;
		}

		/** 0 at the start, 1 at the end, smooth between ticks. */
		float progress(float delta) {
			return MathHelper.clamp((age + delta) / ticks, 0, 1);
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
			case Fx.SCREEN -> SCREENS.add(new Effect(ticks, rgb) {
				@Override
				void draw(Matrix4f m, VertexConsumer vc, Vec3d cam, float delta) {
				}
			});
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
				if (++e.age >= e.ticks) {
					it.remove();
				}
			}
		}
	}

	public static void clear() {
		EFFECTS.clear();
		SCREENS.clear();
	}

	public static void render(WorldRenderContext context) {
		if (EFFECTS.isEmpty()) {
			return;
		}
		VertexConsumerProvider.Immediate immediate = MinecraftClient.getInstance().getBufferBuilders().getEntityVertexConsumers();
		VertexConsumer vc = immediate.getBuffer(RenderLayer.getLightning());
		Vec3d cam = context.camera().getPos();
		context.matrixStack().push();
		context.matrixStack().translate(-cam.x, -cam.y, -cam.z);
		Matrix4f m = context.matrixStack().peek().getPositionMatrix();
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
			if (t < 0.15f) {
				ctx.fill(0, 0, w, h, argb(e.rgb, 0.35f * (1 - t / 0.15f)));
			}
			float a = 0.55f * (1 - t);
			int band = Math.max(8, Math.min(w, h) / 6);
			ctx.fillGradient(0, 0, w, band, argb(e.rgb, a), argb(e.rgb, 0));
			ctx.fillGradient(0, h - band, w, h, argb(e.rgb, 0), argb(e.rgb, a));
			// Sideways gradients aren't built in: thin strips fading towards the middle
			for (int i = 0; i < band; i += 2) {
				int c = argb(e.rgb, a * (1 - (float) i / band));
				ctx.fill(i, 0, i + 2, h, c);
				ctx.fill(w - i - 2, 0, w - i, h, c);
			}
		}
	}

	private static int argb(int rgb, float alpha) {
		return ((int) (MathHelper.clamp(alpha, 0, 1) * 255) << 24) | (rgb & 0xFFFFFF);
	}

	private static void vertex(VertexConsumer vc, Matrix4f m, double x, double y, double z, int rgb, float alpha) {
		vc.vertex(m, (float) x, (float) y, (float) z).color((rgb >> 16) & 0xFF, (rgb >> 8) & 0xFF, rgb & 0xFF,
				(int) (MathHelper.clamp(alpha, 0, 1) * 255)).next();
	}

	/** A quad seen from both sides (the glow layer culls back faces). */
	private static void quad(VertexConsumer vc, Matrix4f m, Vec3d a, Vec3d b, Vec3d c, Vec3d d, int rgb, float aa, float ab, float ac, float ad) {
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
						a[k] = (0.06f + 0.55f * edge * edge + band * 0.4f) * fade;
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
}
