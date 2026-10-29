package euf.mageslegacy.race;

import com.mojang.serialization.Codec;
import euf.mageslegacy.MagesLegacy;
import euf.mageslegacy.net.OpenChoicePayload;
import java.util.Optional;
import java.util.function.Supplier;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.attachment.AttachmentType;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.NeoForgeRegistries;

/** A player's race, kept on the player (an empty id = not chosen yet) and through death. */
public final class RaceState {
	private static final DeferredRegister<AttachmentType<?>> ATTACHMENTS = DeferredRegister.create(NeoForgeRegistries.ATTACHMENT_TYPES,
			MagesLegacy.MOD_ID);
	public static final Supplier<AttachmentType<String>> RACE = ATTACHMENTS.register("race",
			() -> AttachmentType.builder(() -> "").serialize(Codec.STRING).copyOnDeath().build());

	private RaceState() {
	}

	public static void register(IEventBus modBus) {
		ATTACHMENTS.register(modBus);
		NeoForge.EVENT_BUS.addListener(RaceState::onLogin);
	}

	public static Optional<Race> get(ServerPlayer player) {
		return Race.byId(player.getData(RACE));
	}

	/** A player without a race is shown the legend and the choice on every join. */
	private static void onLogin(PlayerEvent.PlayerLoggedInEvent event) {
		if (event.getEntity() instanceof ServerPlayer player && get(player).isEmpty()) {
			PacketDistributor.sendToPlayer(player, new OpenChoicePayload());
		}
	}

	/** The client's choice: only a playable race, and only once. */
	public static void choose(ServerPlayer player, String id) {
		Optional<Race> race = Race.byId(id);
		if (race.isEmpty() || !race.get().playable || get(player).isPresent()) {
			return;
		}
		player.setData(RACE, race.get().id());
		MagesLegacy.LOGGER.info("{} became {}", player.getGameProfile().getName(), race.get().id());
	}
}
