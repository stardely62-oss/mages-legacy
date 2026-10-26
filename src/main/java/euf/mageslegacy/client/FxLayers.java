package euf.mageslegacy.client;

import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.VertexFormat;
import net.minecraft.client.render.VertexFormats;

/**
 * Our own render layer: plain see-through colour, the "ink" under the glow. Light added on top of a bright day only
 * turns red into pink, and a dark colour added is nothing at all; blood needs real colour laid over the world, and
 * the glow after it. It writes no depth, so it never hides what is drawn later, and shows both faces.
 */
public final class FxLayers extends RenderLayer {
	public static final RenderLayer INK = of("mages_legacy_ink", VertexFormats.POSITION_COLOR, VertexFormat.DrawMode.QUADS, 1 << 16, false, true,
			MultiPhaseParameters.builder()
					.program(COLOR_PROGRAM)
					.transparency(TRANSLUCENT_TRANSPARENCY)
					.cull(DISABLE_CULLING)
					.writeMaskState(COLOR_MASK)
					.build(false));

	private FxLayers(String name, VertexFormat format, VertexFormat.DrawMode mode, int size, boolean crumbling, boolean translucent, Runnable start,
			Runnable end) {
		super(name, format, mode, size, crumbling, translucent, start, end);
	}
}
