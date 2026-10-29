package euf.mageslegacy.client;

import net.minecraft.client.Camera;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.DimensionSpecialEffects;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;

/**
 * The Pocket's sky: the void between worlds ({@link VoidSky}), the same the Void Step walks through — the End's sky
 * gone a little violet and full of stars, without clouds or fog.
 */
final class PocketSky extends DimensionSpecialEffects {
	PocketSky() {
		super(Float.NaN, false, SkyType.NONE, false, false);
	}

	@Override
	public Vec3 getBrightnessDependentFogColor(Vec3 color, float brightness) {
		return new Vec3(0.02, 0.015, 0.035);
	}

	@Override
	public boolean isFoggyAt(int x, int z) {
		return false;
	}

	@Override
	public boolean renderSky(ClientLevel level, int ticks, float partialTick, Matrix4f modelViewMatrix, Camera camera, Matrix4f projectionMatrix,
			boolean isFoggy, Runnable setupFog) {
		VoidSky.draw(modelViewMatrix, 1, ticks + partialTick, false);
		return true;
	}

	@Override
	public boolean renderClouds(ClientLevel level, int ticks, float partialTick, com.mojang.blaze3d.vertex.PoseStack poseStack, double camX, double camY,
			double camZ, Matrix4f modelViewMatrix, Matrix4f projectionMatrix) {
		return true;
	}
}
