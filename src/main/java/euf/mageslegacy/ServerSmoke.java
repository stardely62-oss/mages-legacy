package euf.mageslegacy;

import euf.mageslegacy.race.Innate;
import euf.mageslegacy.race.Race;
import euf.mageslegacy.race.Talent;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.item.Items;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

/**
 * The CI's dedicated-server check (run with -Dmages_legacy.server_smoke=true): the mod loads on a dedicated server
 * (no client class reached), the world ticks for five seconds, the innate traits' data is there; writes
 * server-smoke.txt into the server's folder and stops the server. No players: a real multiplayer test needs people.
 */
final class ServerSmoke {
	private ServerSmoke() {
	}

	static void register() {
		NeoForge.EVENT_BUS.addListener((ServerTickEvent.Post event) -> {
			MinecraftServer server = event.getServer();
			if (server.getTickCount() != 100) {
				return;
			}
			List<String> lines = new ArrayList<>();
			lines.add((server.isDedicatedServer() ? "PASS" : "FAIL") + " a dedicated server started with the mod and ticked 100 times");
			lines.add((Items.BEEF.builtInRegistryHolder().is(Innate.RAW_MEAT) && Items.CHICKEN.builtInRegistryHolder().is(Innate.RAW_MEAT)
					&& !Items.COOKED_BEEF.builtInRegistryHolder().is(Innate.RAW_MEAT) ? "PASS" : "FAIL") + " the raw meat tag is loaded");
			boolean nodes = true;
			for (Race race : Race.values()) {
				if (race.playable) {
					nodes &= Talent.innateOf(race).isPresent() && Talent.tree(race).size() == 59;
				}
			}
			lines.add((nodes ? "PASS" : "FAIL") + " every playable race has its 59 nodes and one innate node");
			lines.add("DONE");
			try {
				Files.write(Path.of("server-smoke.txt"), lines);
			} catch (IOException e) {
				MagesLegacy.LOGGER.error("Can't write the server smoke result", e);
			}
			lines.forEach(line -> MagesLegacy.LOGGER.info("[server smoke] {}", line));
			server.halt(false);
		});
	}
}
