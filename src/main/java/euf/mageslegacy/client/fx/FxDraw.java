package euf.mageslegacy.client.fx;

import com.mojang.blaze3d.vertex.VertexConsumer;
import euf.mageslegacy.MagesLegacy;
import foundry.veil.api.client.render.rendertype.VeilRenderType;
import java.util.LinkedHashSet;
import java.util.Set;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.world.phys.Vec3;

/**
 * Writes the effects' quads into Veil render types (see assets/mages_legacy/pinwheel): every quad carries its own
 * numbers in the vertex colour for its shader to read, so one draw call holds all the effects of a kind. Positions
 * are relative to the camera. A "glow" render type writes into Veil's bloom buffer.
 */
final class FxDraw {
	static final String RIFT = "fx/rift";
	static final String RIBBON = "fx/ribbon";
	static final String GROUND = "fx/ground";
	static final String ORB = "fx/orb";
	static final String STAR = "fx/sprite_star";
	static final String MOTE = "fx/sprite_mote";
	/** The Mycelid's painted textures: the mushroom's surfaces and the glowing decals. */
	static final String STEM = "fx/myc_stem";
	static final String CLOUD = "fx/myc_cloud";
	static final String SPIRAL = "fx/myc_spiral";
	static final String AURA = "fx/myc_aura";
	static final String FIELD = "fx/myc_field";
	static final String THREAD = "fx/myc_thread";

	/** The ground shader's modes, in its blue channel. */
	static final float CRACKS = 0.1f;
	static final float RING = 0.3f;
	static final float LENS = 0.55f;
	static final float DISK = 0.85f;
	/** The orb shader's modes. */
	static final float SHELL = 0.1f;
	static final float HOLE = 0.45f;
	static final float FOLD = 0.8f;

	/** Render types by name, looked up once per draw: a lookup per quad was a cost of its own. */
	private boolean blocks;
	private final java.util.Map<String, RenderType> types = new java.util.HashMap<>();
	private final MultiBufferSource.BufferSource buffers;
	final Vec3 cam;
	private final Set<RenderType> used = new LinkedHashSet<>();

	FxDraw(MultiBufferSource.BufferSource buffers, Vec3 cam) {
		this.buffers = buffers;
		this.cam = cam;
	}

	/** The render type of this name, or its bloom twin; null if Veil hasn't loaded it (the effect is then skipped). */
	private RenderType type(String name, boolean glow) {
		String key = glow ? name + "_glow" : name;
		RenderType type = types.get(key);
		if (type == null && !types.containsKey(key)) {
			type = VeilRenderType.get(MagesLegacy.id(key));
			types.put(key, type);
		}
		if (type != null) {
			used.add(type);
		}
		return type;
	}

	/**
	 * A quad from a centre and two half-axes (world units), drawn in the plain type and in its glow twin.
	 * uv runs 0..1 over the quad.
	 */
	void quad(String name, Vec3 centre, Vec3 halfU, Vec3 halfV, float r, float g, float b, float a) {
		for (boolean glow : new boolean[] {false, true}) {
			RenderType type = type(name, glow);
			if (type == null) {
				continue;
			}
			VertexConsumer vc = buffers.getBuffer(type);
			Vec3 c = centre.subtract(cam);
			vertex(vc, c.subtract(halfU).subtract(halfV), 0, 0, r, g, b, a);
			vertex(vc, c.add(halfU).subtract(halfV), 1, 0, r, g, b, a);
			vertex(vc, c.add(halfU).add(halfV), 1, 1, r, g, b, a);
			vertex(vc, c.subtract(halfU).add(halfV), 0, 1, r, g, b, a);
		}
	}

	/** A quad with its own four corners and texture coordinates (a strip segment), plain and glow. */
	void strip(String name, Vec3[] corners, float[] us, float[] vs, float r, float g, float b, float a) {
		for (boolean glow : new boolean[] {false, true}) {
			RenderType type = type(name, glow);
			if (type == null) {
				continue;
			}
			VertexConsumer vc = buffers.getBuffer(type);
			for (int i = 0; i < 4; i++) {
				vertex(vc, corners[i].subtract(cam), us[i], vs[i], r, g, b, a);
			}
		}
	}

	/**
	 * A painted texture on a quad (the Mycelid's decals): its colour tinted by r, g, b and faded by a; the glow twin
	 * (into the bloom) as strong as {@code glow} times that.
	 */
	void paint(String name, Vec3 centre, Vec3 halfU, Vec3 halfV, float r, float g, float b, float a, float glow) {
		Vec3 c = centre.subtract(cam);
		Vec3[] corners = {c.subtract(halfU).subtract(halfV), c.add(halfU).subtract(halfV), c.add(halfU).add(halfV), c.subtract(halfU).add(halfV)};
		float[] us = {0, 1, 1, 0};
		float[] vs = {0, 0, 1, 1};
		paintRaw(name, corners, us, vs, r, g, b, a, glow);
	}

	/** A painted texture on a quad of its own four corners (world positions) and texture coordinates. */
	void paintStrip(String name, Vec3[] corners, float[] us, float[] vs, float r, float g, float b, float a, float glow) {
		Vec3[] rel = new Vec3[4];
		for (int i = 0; i < 4; i++) {
			rel[i] = corners[i].subtract(cam);
		}
		paintRaw(name, rel, us, vs, r, g, b, a, glow);
	}

	private void paintRaw(String name, Vec3[] rel, float[] us, float[] vs, float r, float g, float b, float a, float glow) {
		RenderType plain = type(name, false);
		if (plain != null) {
			VertexConsumer vc = buffers.getBuffer(plain);
			for (int i = 0; i < 4; i++) {
				vertex(vc, rel[i], us[i], vs[i], r, g, b, a);
			}
		}
		if (glow > 0) {
			RenderType bloom = type(name, true);
			if (bloom != null) {
				VertexConsumer vc = buffers.getBuffer(bloom);
				for (int i = 0; i < 4; i++) {
					vertex(vc, rel[i], us[i], vs[i], 1, 1, 1, a * glow);
				}
			}
		}
	}

	/** A sprite facing the camera, coloured; drawn as glow too when {@code glow} is above 0 (its colour scaled by it). */
	void sprite(String name, Vec3 at, Vec3 left, Vec3 up, float size, int argb, float glow) {
		float a = ((argb >>> 24) & 0xFF) / 255f;
		float r = ((argb >> 16) & 0xFF) / 255f;
		float g = ((argb >> 8) & 0xFF) / 255f;
		float b = (argb & 0xFF) / 255f;
		Vec3 c = at.subtract(cam);
		Vec3 l = left.scale(size);
		Vec3 u = up.scale(size);
		RenderType plain = type(name, false);
		if (plain != null) {
			VertexConsumer vc = buffers.getBuffer(plain);
			spriteQuad(vc, c, l, u, r, g, b, a);
		}
		if (glow > 0) {
			RenderType bloom = type(name, true);
			if (bloom != null) {
				VertexConsumer vc = buffers.getBuffer(bloom);
				spriteQuad(vc, c, l, u, Math.min(1, r * glow), Math.min(1, g * glow), Math.min(1, b * glow), a);
			}
		}
	}

	private static void spriteQuad(VertexConsumer vc, Vec3 c, Vec3 l, Vec3 u, float r, float g, float b, float a) {
		vertex(vc, c.add(l).subtract(u), 0, 1, r, g, b, a);
		vertex(vc, c.subtract(l).subtract(u), 1, 1, r, g, b, a);
		vertex(vc, c.subtract(l).add(u), 1, 0, r, g, b, a);
		vertex(vc, c.add(l).add(u), 0, 0, r, g, b, a);
	}

	private static void vertex(VertexConsumer vc, Vec3 p, float u, float v, float r, float g, float b, float a) {
		vc.addVertex((float) p.x, (float) p.y, (float) p.z).setUv(u, v).setColor(clamp(r), clamp(g), clamp(b), clamp(a));
	}

	private static float clamp(float v) {
		return Math.max(0, Math.min(1, v));
	}

	/** A block drawn from its own model, glowing, this many blocks tall per block, standing on at and tilted a little. */
	void block(net.minecraft.world.level.block.state.BlockState state, Vec3 at, float scale, float yaw, float tiltX, float tiltZ) {
		com.mojang.blaze3d.vertex.PoseStack pose = new com.mojang.blaze3d.vertex.PoseStack();
		Vec3 c = at.subtract(cam);
		pose.translate(c.x, c.y, c.z);
		pose.mulPose(com.mojang.math.Axis.YP.rotationDegrees(yaw));
		pose.mulPose(com.mojang.math.Axis.XP.rotationDegrees(tiltX));
		pose.mulPose(com.mojang.math.Axis.ZP.rotationDegrees(tiltZ));
		pose.scale(scale, scale, scale);
		pose.translate(-0.5, 0, -0.5);
		net.minecraft.client.Minecraft.getInstance().getBlockRenderer().renderSingleBlock(state, pose, buffers,
				net.minecraft.client.renderer.LightTexture.FULL_BRIGHT, net.minecraft.client.renderer.texture.OverlayTexture.NO_OVERLAY);
		blocks = true;
	}

	/** Draws everything written this frame. */
	void finish() {
		if (blocks) {
			// A single block goes into the entity block sheets, not the chunk layers: everything is flushed
			buffers.endBatch();
		}
		for (RenderType type : used) {
			buffers.endBatch(type);
		}
	}
}
