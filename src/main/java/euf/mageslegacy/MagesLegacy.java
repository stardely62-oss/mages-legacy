package euf.mageslegacy;

import euf.mageslegacy.net.Network;
import euf.mageslegacy.race.Progression;
import euf.mageslegacy.race.RaceCommand;
import euf.mageslegacy.race.RaceState;
import euf.mageslegacy.voidborn.Arena;
import euf.mageslegacy.voidborn.BlackHole;
import euf.mageslegacy.voidborn.Passage;
import euf.mageslegacy.voidborn.Pocket;
import euf.mageslegacy.voidborn.Voidborn;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Mage's Legacy: the nameless mage split people into races. The legend, the choice of race and the awakened races,
 * the Voidborn and the Mycelids; the others still sleep.
 */
@Mod(MagesLegacy.MOD_ID)
public class MagesLegacy {
	public static final String MOD_ID = "mages_legacy";
	public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

	public MagesLegacy(IEventBus modBus) {
		RaceState.register(modBus);
		euf.mageslegacy.race.Deeds.register(modBus);
		euf.mageslegacy.item.OblivionScrollItem.register(modBus);
		euf.mageslegacy.item.LegacyIcon.register(modBus);
		euf.mageslegacy.mycelid.MyceliumNodeBlock.register(modBus);
		modBus.addListener(Network::register);
		Progression.register();
		Voidborn.register();
		Pocket.register();
		Arena.register();
		Passage.register();
		BlackHole.register();
		RaceCommand.register();
		euf.mageslegacy.mycelid.MyceliumNetwork.register();
		euf.mageslegacy.mycelid.Mycelid.register();
		euf.mageslegacy.mycelid.Awaken.register();
		euf.mageslegacy.mycelid.Allies.register();
	}

	public static ResourceLocation id(String path) {
		return ResourceLocation.fromNamespaceAndPath(MOD_ID, path);
	}
}
