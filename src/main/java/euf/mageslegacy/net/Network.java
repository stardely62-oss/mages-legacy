package euf.mageslegacy.net;

import euf.mageslegacy.client.MagesLegacyClient;
import euf.mageslegacy.race.RaceState;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;

public final class Network {
	private Network() {
	}

	public static void register(RegisterPayloadHandlersEvent event) {
		PayloadRegistrar registrar = event.registrar("1");
		// The lambda's body only runs on a client, so a dedicated server never loads the client class
		registrar.playToClient(OpenChoicePayload.TYPE, OpenChoicePayload.STREAM_CODEC, (payload, context) -> MagesLegacyClient.openChoice());
		registrar.playToServer(ChooseRacePayload.TYPE, ChooseRacePayload.STREAM_CODEC, (payload, context) -> {
			if (context.player() instanceof ServerPlayer player) {
				RaceState.choose(player, payload.race());
			}
		});
	}
}
