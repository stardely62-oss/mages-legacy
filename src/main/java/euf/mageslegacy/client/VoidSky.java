package euf.mageslegacy.client;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.BufferUploader;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexFormat;
import java.util.Random;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.resources.ResourceLocation;
import org.joml.Matrix4f;
import org.joml.Matrix4fStack;
import org.joml.Vector3f;

/**
 * The sky of the void between worlds: the End's sky, tinted a little violet, full of slowly twinkling stars and a few
 * lavender ones. The Pocket wears it as its sky; a Void Step shows it all around the walker, over the whole world.
 */
public final class VoidSky {
	private static final ResourceLocation END_SKY = ResourceLocation.withDefaultNamespace("textures/environment/end_sky.png");
	private static final int STARS = 1400;
	private static final float[][] STAR = new float[STARS][];
	/** Stars near enough to fly past during a step, in a box of {@link #BOX} blocks around the eye. */
	private static final int NEAR = 320;
	private static final float BOX = 64;
	private static final float[][] PASSING = new float[NEAR][];

	static {
		Random random = new Random(0x5EEDL);
		for (int i = 0; i < STARS; i++) {
			Vector3f dir = new Vector3f(random.nextFloat() * 2 - 1, random.nextFloat() * 2 - 1, random.nextFloat() * 2 - 1);
			if (dir.lengthSquared() < 0.01f || dir.lengthSquared() > 1) {
				i--;
				continue;
			}
			dir.normalize();
			float size = 0.12f + random.nextFloat() * random.nextFloat() * 0.45f;
			boolean lavender = random.nextInt(6) == 0;
			STAR[i] = new float[] {dir.x, dir.y, dir.z, size, random.nextFloat() * 6.28f, lavender ? 1 : 0, 0.6f + random.nextFloat() * 0.4f};
		}
		for (int i = 0; i < NEAR; i++) {
			PASSING[i] = new float[] {(random.nextFloat() - 0.5f) * BOX, (random.nextFloat() - 0.5f) * BOX, (random.nextFloat() - 0.5f) * BOX,
					0.03f + random.nextFloat() * 0.05f, random.nextInt(4) == 0 ? 1 : 0};
		}
	}

	/** Drawing the inside of the Mycelium Network instead: deep green-black, spores glowing teal and gold. */
	static boolean fungal;

	private VoidSky() {
	}

	/**
	 * Draws the sky around the camera with this view rotation; {@code alpha} below 1 lets the world show through
	 * (fading in and out of a step). With {@code over} it is drawn over everything already on the screen.
	 */
	public static void draw(Matrix4f viewRotation, float alpha, float time, boolean over) {
		draw(viewRotation, alpha, time, over, null, 0, 0);
	}

	/**
	 * With a flight: stars close by stream past the eye along {@code dir} (a unit vector in the world), {@code travel}
	 * blocks flown so far at {@code speed} blocks a tick now, drawn as streaks as long as the speed.
	 */
	public static void draw(Matrix4f viewRotation, float alpha, float time, boolean over, Vector3f dir, float travel, float speed) {
		if (alpha <= 0.002f) {
			return;
		}
		Matrix4fStack stack = RenderSystem.getModelViewStack();
		stack.pushMatrix();
		stack.identity();
		stack.mul(viewRotation);
		RenderSystem.applyModelViewMatrix();
		RenderSystem.enableBlend();
		RenderSystem.defaultBlendFunc();
		RenderSystem.depthMask(false);
		if (over) {
			RenderSystem.disableDepthTest();
		}
		RenderSystem.disableCull();
		try {
			dome(alpha);
			stars(alpha, time);
			if (dir != null) {
				passing(alpha, dir, travel, speed);
			}
		} catch (RuntimeException e) {
			// The sky is only a picture: it must never take the game down
			euf.mageslegacy.MagesLegacy.LOGGER.error("The void's sky failed to draw", e);
		} finally {
			RenderSystem.defaultBlendFunc();
		}
		RenderSystem.enableCull();
		RenderSystem.enableDepthTest();
		RenderSystem.depthMask(true);
		RenderSystem.disableBlend();
		stack.popMatrix();
		RenderSystem.applyModelViewMatrix();
	}

	/** The End's sky box, its grey turned to a deep violet. */
	private static void dome(float alpha) {
		RenderSystem.setShader(GameRenderer::getPositionTexColorShader);
		RenderSystem.setShaderTexture(0, END_SKY);
		BufferBuilder bb = Tesselator.getInstance().begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_TEX_COLOR);
		// Nearly black, with only a breath of violet
		float r = fungal ? 0.03f : 0.085f;
		float g = fungal ? 0.09f : 0.06f;
		float b = fungal ? 0.06f : 0.13f;
		Matrix4f face = new Matrix4f();
		for (int i = 0; i < 6; i++) {
			face.identity();
			switch (i) {
				case 1 -> face.rotateX((float) Math.toRadians(90));
				case 2 -> face.rotateX((float) Math.toRadians(-90));
				case 3 -> face.rotateX((float) Math.toRadians(180));
				case 4 -> face.rotateZ((float) Math.toRadians(90));
				case 5 -> face.rotateZ((float) Math.toRadians(-90));
				default -> {
				}
			}
			bb.addVertex(face, -100, -100, -100).setUv(0, 0).setColor(r, g, b, alpha);
			bb.addVertex(face, -100, -100, 100).setUv(0, 16).setColor(r, g, b, alpha);
			bb.addVertex(face, 100, -100, 100).setUv(16, 16).setColor(r, g, b, alpha);
			bb.addVertex(face, 100, -100, -100).setUv(16, 0).setColor(r, g, b, alpha);
		}
		upload(bb);
	}

	/** The near stars of a flight: each moved back along the way by what has been flown, wrapped round the box. */
	private static void passing(float alpha, Vector3f dir, float travel, float speed) {
		RenderSystem.setShader(GameRenderer::getPositionColorShader);
		RenderSystem.blendFunc(com.mojang.blaze3d.platform.GlStateManager.SourceFactor.SRC_ALPHA, com.mojang.blaze3d.platform.GlStateManager.DestFactor.ONE);
		BufferBuilder bb = Tesselator.getInstance().begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_COLOR);
		float length = 0.3f + speed * 4f;
		Vector3f p = new Vector3f();
		Vector3f tail = new Vector3f();
		Vector3f across = new Vector3f();
		for (float[] s : PASSING) {
			p.set(wrap(s[0] - dir.x * travel), wrap(s[1] - dir.y * travel), wrap(s[2] - dir.z * travel));
			float d = p.length();
			if (d < 1.5f) {
				continue;
			}
			// The streak trails back the way the star came from (ahead of the flier)
			// Brightest close by, gone towards the box's edge so nothing pops in or out
			float a = alpha * Math.min(1, (BOX * 0.5f - d) / 10f) * Math.min(1, d / 4f);
			if (a <= 0.01f) {
				continue;
			}
			tail.set(dir).mul(length).add(p);
			across.set(dir).cross(p);
			if (across.lengthSquared() < 1.0E-4f) {
				continue;
			}
			across.normalize().mul(s[3] * (1 + d * 0.03f));
			float r = fungal ? (s[4] > 0 ? 1f : 0.45f) : s[4] > 0 ? 0.8f : 1f;
			float g = fungal ? (s[4] > 0 ? 0.85f : 1f) : s[4] > 0 ? 0.7f : 1f;
			float bl = fungal ? (s[4] > 0 ? 0.4f : 0.75f) : 1f;
			bb.addVertex(p.x - across.x, p.y - across.y, p.z - across.z).setColor(r, g, bl, a);
			bb.addVertex(p.x + across.x, p.y + across.y, p.z + across.z).setColor(r, g, bl, a);
			bb.addVertex(tail.x + across.x, tail.y + across.y, tail.z + across.z).setColor(r, g, bl, 0);
			bb.addVertex(tail.x - across.x, tail.y - across.y, tail.z - across.z).setColor(r, g, bl, 0);
		}
		upload(bb);
		RenderSystem.defaultBlendFunc();
	}

	/** Draws what was written; a frame where every star was too faint to write is simply nothing to draw. */
	private static void upload(BufferBuilder bb) {
		com.mojang.blaze3d.vertex.MeshData mesh = bb.build();
		if (mesh != null) {
			BufferUploader.drawWithShader(mesh);
		}
	}

	private static float wrap(float v) {
		float half = BOX / 2;
		return ((v + half) % BOX + BOX) % BOX - half;
	}

	/** Stars as small squares facing the middle, each twinkling at its own pace. */
	private static void stars(float alpha, float time) {
		RenderSystem.setShader(GameRenderer::getPositionColorShader);
		RenderSystem.blendFunc(com.mojang.blaze3d.platform.GlStateManager.SourceFactor.SRC_ALPHA, com.mojang.blaze3d.platform.GlStateManager.DestFactor.ONE);
		BufferBuilder bb = Tesselator.getInstance().begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_COLOR);
		Vector3f up = new Vector3f();
		Vector3f side = new Vector3f();
		for (float[] s : STAR) {
			Vector3f dir = new Vector3f(s[0], s[1], s[2]);
			Vector3f centre = new Vector3f(dir).mul(90);
			// Two axes across the direction
			side.set(Math.abs(dir.y) > 0.9f ? 1 : 0, Math.abs(dir.y) > 0.9f ? 0 : 1, 0).cross(dir).normalize().mul(s[3]);
			up.set(dir).cross(side).normalize().mul(s[3]);
			float twinkle = 0.65f + 0.35f * (float) Math.sin(time * 0.08f * s[6] + s[4]);
			float a = alpha * twinkle * s[6];
			float r = fungal ? (s[5] > 0 ? 1f : 0.55f) : s[5] > 0 ? 0.80f : 1f;
			float g = fungal ? (s[5] > 0 ? 0.85f : 1f) : s[5] > 0 ? 0.70f : 1f;
			float bl = fungal ? (s[5] > 0 ? 0.45f : 0.8f) : 1f;
			bb.addVertex(centre.x - side.x - up.x, centre.y - side.y - up.y, centre.z - side.z - up.z).setColor(r, g, bl, a);
			bb.addVertex(centre.x + side.x - up.x, centre.y + side.y - up.y, centre.z + side.z - up.z).setColor(r, g, bl, a);
			bb.addVertex(centre.x + side.x + up.x, centre.y + side.y + up.y, centre.z + side.z + up.z).setColor(r, g, bl, a);
			bb.addVertex(centre.x - side.x + up.x, centre.y - side.y + up.y, centre.z - side.z + up.z).setColor(r, g, bl, a);
		}
		upload(bb);
		RenderSystem.defaultBlendFunc();
	}
}
