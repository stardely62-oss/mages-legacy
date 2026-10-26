package euf.mageslegacy.fx;

import euf.mageslegacy.MagesLegacy;
import net.fabricmc.fabric.api.particle.v1.FabricParticleTypes;
import net.minecraft.particle.DefaultParticleType;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;

/** The races' own particles: grains of sand, ice shards, spores, spirit wisps. Sprites come from tools/gen_particles.py. */
public final class ModParticles {
	/** A grain of the sand of time: falls and drifts. */
	public static final DefaultParticleType SAND = FabricParticleTypes.simple();
	/** A grain that hangs where it is, glinting: time has stopped around it. */
	public static final DefaultParticleType STILL_SAND = FabricParticleTypes.simple();
	/** A shard of ice: tumbles as it falls. */
	public static final DefaultParticleType ICE_SHARD = FabricParticleTypes.simple();
	/** A glowing spore: floats up swaying, swells and bursts. */
	public static final DefaultParticleType SPORE = FabricParticleTypes.simple();
	/** A spirit wisp: rises flickering, glowing. */
	public static final DefaultParticleType WISP = FabricParticleTypes.simple();

	private ModParticles() {
	}

	public static void register() {
		Registry.register(Registries.PARTICLE_TYPE, MagesLegacy.id("sand"), SAND);
		Registry.register(Registries.PARTICLE_TYPE, MagesLegacy.id("still_sand"), STILL_SAND);
		Registry.register(Registries.PARTICLE_TYPE, MagesLegacy.id("ice_shard"), ICE_SHARD);
		Registry.register(Registries.PARTICLE_TYPE, MagesLegacy.id("spore"), SPORE);
		Registry.register(Registries.PARTICLE_TYPE, MagesLegacy.id("wisp"), WISP);
	}
}
