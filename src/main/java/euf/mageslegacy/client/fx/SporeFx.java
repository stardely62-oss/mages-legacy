package euf.mageslegacy.client.fx;

import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;

/**
 * The Mycelid's glowing spores, drawn with the effects' own particles: the Spore Storm's whirl round its heart, and
 * the spores the Great Bloom's cap breathes out.
 */
final class SporeFx {
	private static final int[] COLOURS = {0xFF8CE07A, 0xFF7FE8C8, 0xFF9CF0B0, 0xFFE8D27A};

	private SporeFx() {
	}

	private static int colour() {
		return COLOURS[VoidRender.RANDOM.nextInt(COLOURS.length)];
	}

	/** The Spore Storm: thick arms of glowing spores whirling round the Mycelid, drawn in towards them. */
	static final class Storm extends VoidRender.Effect {
		final Entity heart;
		final float radius;

		Storm(Entity heart, float radius, int ticks) {
			super(ticks);
			this.heart = heart;
			this.radius = radius;
		}

		@Override
		boolean tick() {
			Vec3 c = heart.position();
			float fade = Math.min(1, age / 10f) * Math.min(1, (ticks - age) / 15f);
			int count = (int) (55 * fade);
			for (int i = 0; i < count; i++) {
				double a = VoidRender.rand(0, Math.PI * 2);
				double r = radius * Math.sqrt(VoidRender.rand(0.02, 1));
				double y = VoidRender.rand(0.1, 4.5);
				Vec3 at = c.add(Math.cos(a) * r, y, Math.sin(a) * r);
				// Round the heart (anticlockwise), a little up, falling in as it goes
				Vec3 around = new Vec3(-Math.sin(a), 0, Math.cos(a)).scale(VoidRender.rand(0.25, 0.5));
				VoidRender.Particle p = new VoidRender.Particle(at, around.add(0, VoidRender.rand(0.01, 0.05), 0), 34,
						(float) VoidRender.rand(0.14, 0.32), colour(), true, 0.97f, 1.1f);
				p.attract = c.add(0, y, 0);
				p.pull = 0.025f;
				VoidRender.add(p);
			}
			return super.tick();
		}

		@Override
		void draw(FxDraw draw, float partial) {
		}
	}

	/** The Great Bloom's breath: spores drifting up and out from under the cap. */
	static void breathe(Vec3 base, float scale) {
		for (int i = 0; i < 5; i++) {
			double a = VoidRender.rand(0, Math.PI * 2);
			double r = VoidRender.rand(0.5, 3.0) * scale;
			Vec3 at = base.add(Math.cos(a) * r, VoidRender.rand(2.6, 4.2) * scale, Math.sin(a) * r);
			Vec3 vel = new Vec3(Math.cos(a) * 0.03, VoidRender.rand(0.02, 0.06), Math.sin(a) * 0.03);
			VoidRender.add(new VoidRender.Particle(at, vel, 60, (float) VoidRender.rand(0.05, 0.12), colour(), true, 0.99f, 1.1f));
		}
	}
}
