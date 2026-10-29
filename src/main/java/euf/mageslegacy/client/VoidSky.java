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
	}

	private VoidSky() {
	}

	/**
	 * Draws the sky around the camera with this view rotation; {@code alpha} below 1 lets the world show through
	 * (fading in and out of a step). With {@code over} it is drawn over everything already on the screen.
	 */
	public static void draw(Matrix4f viewRotation, float alpha, float time, boolean over) {
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
		dome(alpha);
		stars(alpha, time);
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
		float r = 0.30f;
		float g = 0.21f;
		float b = 0.42f;
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
		BufferUploader.drawWithShader(bb.buildOrThrow());
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
			float r = s[5] > 0 ? 0.80f : 1f;
			float g = s[5] > 0 ? 0.70f : 1f;
			bb.addVertex(centre.x - side.x - up.x, centre.y - side.y - up.y, centre.z - side.z - up.z).setColor(r, g, 1f, a);
			bb.addVertex(centre.x + side.x - up.x, centre.y + side.y - up.y, centre.z + side.z - up.z).setColor(r, g, 1f, a);
			bb.addVertex(centre.x + side.x + up.x, centre.y + side.y + up.y, centre.z + side.z + up.z).setColor(r, g, 1f, a);
			bb.addVertex(centre.x - side.x + up.x, centre.y - side.y + up.y, centre.z - side.z + up.z).setColor(r, g, 1f, a);
		}
		BufferUploader.drawWithShader(bb.buildOrThrow());
		RenderSystem.defaultBlendFunc();
	}
}
