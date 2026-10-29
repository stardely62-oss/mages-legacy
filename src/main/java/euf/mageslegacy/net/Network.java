package euf.mageslegacy.net;

import euf.mageslegacy.client.MagesLegacyClient;
import euf.mageslegacy.race.Abilities;
import euf.mageslegacy.race.RaceState;
import euf.mageslegacy.race.Talents;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;

public final class Network {
	private Network() {
	}

	public static void register(RegisterPayloadHandlersEvent event) {
		PayloadRegistrar registrar = event.registrar("4");
		// The lambdas' bodies only run on a client, so a dedicated server never loads the client class
		registrar.playToClient(OpenChoicePayload.TYPE, OpenChoicePayload.STREAM_CODEC, (payload, context) -> MagesLegacyClient.openChoice());
		registrar.playToClient(SyncPayload.TYPE, SyncPayload.STREAM_CODEC, (payload, context) -> MagesLegacyClient.onSync(payload));
		registrar.playToClient(StepPayload.TYPE, StepPayload.STREAM_CODEC, (payload, context) -> MagesLegacyClient.onStep(payload));
		registrar.playToClient(FxPayload.TYPE, FxPayload.STREAM_CODEC, (payload, context) -> MagesLegacyClient.onFx(payload));
		registrar.playToServer(ChooseRacePayload.TYPE, ChooseRacePayload.STREAM_CODEC, (payload, context) -> {
			if (context.player() instanceof ServerPlayer player) {
				RaceState.choose(player, payload.race());
			}
		});
		registrar.playToServer(LearnPayload.TYPE, LearnPayload.STREAM_CODEC, (payload, context) -> {
			if (context.player() instanceof ServerPlayer player) {
				Talents.learn(player, payload.talent());
			}
		});
		registrar.playToServer(AbilityPayload.TYPE, AbilityPayload.STREAM_CODEC, (payload, context) -> {
			if (context.player() instanceof ServerPlayer player) {
				Abilities.use(player, payload.slot());
			}
		});
	}
}
