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
	/** A pixel spore, the Mycelid's particle. */
	static final String SPORE = "fx/sprite_spore";
	/** Pixel badges floating over heads: confused (Neurospores), weakened and quickened (the Spore Storm). */
	static final String ICON_CONFUSED = "fx/icon_confused";
	static final String ICON_WEAKENED = "fx/icon_weakened";
	static final String ICON_QUICKENED = "fx/icon_quickened";
	/** The Mycelid's painted textures: the mushroom's surfaces and the glowing decals. */
	/** The node block's own pixel stem, for the reserve body. */
	static final String NODE_STEM = "fx/node_stem";
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

	// ---- solid pieces: sprites extruded into metal, as Minecraft makes items of their pictures ----------------------

	/** One side wall of an extruded sprite, in texels: from (x0, y0) to (x1, y1), facing (nx, ny); its texture runs u0..u1, v0..v1. */
	private record Wall(float x0, float y0, float x1, float y1, int nx, int ny, float u0, float v0, float u1, float v1) {
	}

	private record Shape(int w, int h, java.util.List<Wall> walls) {
	}

	private static final java.util.Map<String, Shape> SHAPES = new java.util.HashMap<>();
	/** Where the light comes from (above, a little to one side): what makes a solid piece read as solid. */
	private static final Vec3 LIGHT = new Vec3(0.35, 0.85, 0.4).normalize();

	/** The walls round every opaque run of a texture's pixels (read once, from the mod's own resources). */
	private static Shape shape(String name) {
		Shape cached = SHAPES.get(name);
		if (cached != null) {
			return cached;
		}
		Shape made = new Shape(1, 1, java.util.List.of());
		try (java.io.InputStream in = net.minecraft.client.Minecraft.getInstance().getResourceManager()
				.open(MagesLegacy.id("textures/" + name + ".png")); com.mojang.blaze3d.platform.NativeImage img = com.mojang.blaze3d.platform.NativeImage.read(in)) {
			int w = img.getWidth();
			int h = img.getHeight();
			boolean[][] solid = new boolean[w][h];
			for (int x = 0; x < w; x++) {
				for (int y = 0; y < h; y++) {
					// NativeImage keeps ABGR: alpha is the top byte either way
					solid[x][y] = (img.getPixelRGBA(x, y) >>> 24) >= 128;
				}
			}
			java.util.List<Wall> walls = new java.util.ArrayList<>();
			for (int y = 0; y < h; y++) {
				for (int dir = -1; dir <= 1; dir += 2) {
					int x = 0;
					while (x < w) {
						int ny = y + dir;
						if (solid[x][y] && (ny < 0 || ny >= h || !solid[x][ny])) {
							int start = x;
							while (x < w && solid[x][y] && (ny < 0 || ny >= h || !solid[x][ny])) {
								x++;
							}
							float line = dir < 0 ? y : y + 1;
							float v = (y + 0.5f) / h;
							walls.add(new Wall(start, line, x, line, 0, dir, start / (float) w, v, x / (float) w, v));
						} else {
							x++;
						}
					}
				}
			}
			for (int x = 0; x < w; x++) {
				for (int dir = -1; dir <= 1; dir += 2) {
					int y = 0;
					while (y < h) {
						int nx = x + dir;
						if (solid[x][y] && (nx < 0 || nx >= w || !solid[nx][y])) {
							int start = y;
							while (y < h && solid[x][y] && (nx < 0 || nx >= w || !solid[nx][y])) {
								y++;
							}
							float line = dir < 0 ? x : x + 1;
							float u = (x + 0.5f) / w;
							walls.add(new Wall(line, start, line, y, dir, 0, u, start / (float) h, u, y / (float) h));
						} else {
							y++;
						}
					}
				}
			}
			made = new Shape(w, h, walls);
		} catch (java.io.IOException | RuntimeException e) {
			MagesLegacy.LOGGER.warn("No solid shape for {}", name, e);
		}
		SHAPES.put(name, made);
		return made;
	}

	/** How a face facing this way catches the light: never black, brightest turned up to it. */
	static float lit(Vec3 normal) {
		double d = normal.lengthSqr() < 1.0E-9 ? 0 : normal.normalize().dot(LIGHT);
		return (float) (0.58 + 0.42 * Math.max(0, d) - 0.1 * Math.max(0, -d));
	}

	/**
	 * A solid piece: the texture extruded {@code thick} deep, as Minecraft builds an item from its picture. Its two faces
	 * and every wall round its pixels lit by their own direction; laid like {@link #paint} (v = 0 at centre - halfV).
	 * Depth-tested and written, so it hides what is behind it and itself.
	 */
	void solid(String name, Vec3 centre, Vec3 halfU, Vec3 halfV, double thick, float shade, float a, float glow) {
		solid(name, centre, halfU, halfV, thick, shade, shade, shade, a, glow);
	}

	/** A solid piece tinted r, g, b (a chain darkening to rust as its Debt comes due). */
	void solid(String name, Vec3 centre, Vec3 halfU, Vec3 halfV, double thick, float r, float g, float b, float a, float glow) {
		float shade = 1;
		if (a <= 0.004f) {
			return;
		}
		RenderType type = type(name + "_solid", false);
		if (type == null) {
			paint(name, centre, halfU, halfV, r, g, b, a, glow);
			return;
		}
		Shape shape = shape(name);
		Vec3 normal = halfU.cross(halfV);
		if (normal.lengthSqr() < 1.0E-12) {
			return;
		}
		Vec3 n = normal.normalize().scale(thick / 2);
		Vec3 c = centre.subtract(cam);
		VertexConsumer vc = buffers.getBuffer(type);
		for (int side = -1; side <= 1; side += 2) {
			Vec3 o = c.add(n.scale(side));
			float s = shade * lit(n.scale(side));
			vertex(vc, o.subtract(halfU).subtract(halfV), 0, 0, s * r, s * g, s * b, a);
			vertex(vc, o.add(halfU).subtract(halfV), 1, 0, s * r, s * g, s * b, a);
			vertex(vc, o.add(halfU).add(halfV), 1, 1, s * r, s * g, s * b, a);
			vertex(vc, o.subtract(halfU).add(halfV), 0, 1, s * r, s * g, s * b, a);
		}
		Vec3 un = halfU.normalize();
		Vec3 vn = halfV.normalize();
		float[] sh = {shade * lit(un.scale(-1)), shade * lit(un), shade * lit(vn.scale(-1)), shade * lit(vn)};
		for (Wall wall : shape.walls()) {
			Vec3 p0 = c.add(halfU.scale(2 * wall.x0() / shape.w() - 1)).add(halfV.scale(2 * wall.y0() / shape.h() - 1));
			Vec3 p1 = c.add(halfU.scale(2 * wall.x1() / shape.w() - 1)).add(halfV.scale(2 * wall.y1() / shape.h() - 1));
			float s = wall.nx() < 0 ? sh[0] : wall.nx() > 0 ? sh[1] : wall.ny() < 0 ? sh[2] : sh[3];
			vertex(vc, p0.add(n), wall.u0(), wall.v0(), s * r, s * g, s * b, a);
			vertex(vc, p1.add(n), wall.u1(), wall.v1(), s * r, s * g, s * b, a);
			vertex(vc, p1.subtract(n), wall.u1(), wall.v1(), s * r, s * g, s * b, a);
			vertex(vc, p0.subtract(n), wall.u0(), wall.v0(), s * r, s * g, s * b, a);
		}
		if (glow > 0) {
			RenderType bloom = type(name, true);
			if (bloom != null) {
				VertexConsumer vg = buffers.getBuffer(bloom);
				Vec3 o = c.add(n);
				vertex(vg, o.subtract(halfU).subtract(halfV), 0, 0, 1, 1, 1, a * glow);
				vertex(vg, o.add(halfU).subtract(halfV), 1, 0, 1, 1, 1, a * glow);
				vertex(vg, o.add(halfU).add(halfV), 1, 1, 1, 1, 1, a * glow);
				vertex(vg, o.subtract(halfU).add(halfV), 0, 1, 1, 1, 1, a * glow);
			}
		}
	}

	/**
	 * A strip of cloth or paper with two faces (a grid of rows x columns corners, world positions), its texture over it
	 * as u across, v down; the back a hair behind the front. Each cell lit by its own slope, so folds read as folds.
	 */
	void sheet(String name, Vec3[][] grid, double thick, float shade, float a) {
		RenderType type = type(name + "_solid", false);
		if (type == null || a <= 0.004f) {
			return;
		}
		VertexConsumer vc = buffers.getBuffer(type);
		int rows = grid.length;
		int cols = grid[0].length;
		for (int i = 0; i + 1 < rows; i++) {
			for (int j = 0; j + 1 < cols; j++) {
				Vec3 p00 = grid[i][j].subtract(cam);
				Vec3 p01 = grid[i][j + 1].subtract(cam);
				Vec3 p11 = grid[i + 1][j + 1].subtract(cam);
				Vec3 p10 = grid[i + 1][j].subtract(cam);
				Vec3 normal = p01.subtract(p00).add(p11.subtract(p10)).cross(p10.subtract(p00).add(p11.subtract(p01)));
				if (normal.lengthSqr() < 1.0E-12) {
					continue;
				}
				Vec3 n = normal.normalize().scale(thick / 2);
				float u0 = j / (float) (cols - 1);
				float u1 = (j + 1) / (float) (cols - 1);
				float v0 = i / (float) (rows - 1);
				float v1 = (i + 1) / (float) (rows - 1);
				for (int side = -1; side <= 1; side += 2) {
					Vec3 o = n.scale(side);
					float s = shade * lit(o);
					vertex(vc, p00.add(o), u0, v0, s, s, s, a);
					vertex(vc, p01.add(o), u1, v0, s, s, s, a);
					vertex(vc, p11.add(o), u1, v1, s, s, s, a);
					vertex(vc, p10.add(o), u0, v1, s, s, s, a);
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
