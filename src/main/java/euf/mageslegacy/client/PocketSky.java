package euf.mageslegacy.client;

import net.minecraft.client.renderer.DimensionSpecialEffects;
import net.minecraft.world.phys.Vec3;

/**
 * The Pocket's sky: the night sky of the world at its deepest, full of stars, without clouds or fog, and the void
 * below the island dark.
 */
final class PocketSky extends DimensionSpecialEffects {
	PocketSky() {
		super(Float.NaN, true, SkyType.NORMAL, false, false);
	}

	@Override
	public Vec3 getBrightnessDependentFogColor(Vec3 color, float brightness) {
		return color.multiply(brightness * 0.94 + 0.06, brightness * 0.94 + 0.06, brightness * 0.91 + 0.09);
	}

	@Override
	public boolean isFoggyAt(int x, int z) {
		return false;
	}
}
