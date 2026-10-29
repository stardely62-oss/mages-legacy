package euf.mageslegacy.client;

import euf.mageslegacy.MagesLegacy;
import euf.mageslegacy.client.screen.LoreScreen;
import euf.mageslegacy.net.ChooseRacePayload;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.ReceivingLevelScreen;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.network.PacketDistributor;

@Mod(value = MagesLegacy.MOD_ID, dist = Dist.CLIENT)
public class MagesLegacyClient {
	/** The server asked for the choice; it opens once the world has finished loading. */
	private static boolean choicePending;

	public MagesLegacyClient(IEventBus modBus) {
		NeoForge.EVENT_BUS.addListener(MagesLegacyClient::onTick);
		if (Boolean.getBoolean("mages_legacy.harness")) {
			Harness.start();
		}
	}

	public static void openChoice() {
		choicePending = true;
	}

	public static void sendChoice(String race) {
		PacketDistributor.sendToServer(new ChooseRacePayload(race));
	}

	/** The loading screen closes itself when the terrain arrives, so the legend waits for it instead of being closed with it. */
	private static void onTick(ClientTickEvent.Post event) {
		Minecraft client = Minecraft.getInstance();
		if (choicePending && client.player != null && !(client.screen instanceof ReceivingLevelScreen) && client.getOverlay() == null) {
			choicePending = false;
			client.setScreen(new LoreScreen());
		}
	}
}
