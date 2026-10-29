package euf.mageslegacy;

import euf.mageslegacy.net.Network;
import euf.mageslegacy.race.RaceState;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Mage's Legacy: the nameless mage split people into races. For now only the legend and the choice of race exist;
 * every race still sleeps.
 */
@Mod(MagesLegacy.MOD_ID)
public class MagesLegacy {
	public static final String MOD_ID = "mages_legacy";
	public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

	public MagesLegacy(IEventBus modBus) {
		RaceState.register(modBus);
		modBus.addListener(Network::register);
	}

	public static ResourceLocation id(String path) {
		return ResourceLocation.fromNamespaceAndPath(MOD_ID, path);
	}
}
