package euf.mageslegacy.net;

import euf.mageslegacy.MagesLegacy;
import euf.mageslegacy.mycelid.Mycelid;
import euf.mageslegacy.race.AbilityResult;
import euf.mageslegacy.race.Progression;
import euf.mageslegacy.race.Race;
import euf.mageslegacy.race.RaceData;
import euf.mageslegacy.race.RaceState;
import euf.mageslegacy.shepherd.Shepherd;
import java.util.Locale;
import net.fabricmc.fabric.api.networking.v1.PacketByteBufs;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.Identifier;

/** Channel ids and the server side of every packet. The server re-checks everything a client asks for. */
public final class RacePackets {
	public static final Identifier SYNC = MagesLegacy.id("sync");
	public static final Identifier OPEN_CHOICE = MagesLegacy.id("open_choice");
	public static final Identifier CHOOSE = MagesLegacy.id("choose");
	public static final Identifier ABILITY = MagesLegacy.id("ability");
	public static final Identifier UNLOCK = MagesLegacy.id("unlock");
	public static final Identifier SELECT_ECHO = MagesLegacy.id("select_echo");
	/** Server to client: entity ids of the echoes in the player's world, so they are drawn as spirits. */
	public static final Identifier ECHOES = MagesLegacy.id("echoes");
	public static final int SLOTS = 3;

	private RacePackets() {
	}

	public static void registerServerReceivers() {
		ServerPlayNetworking.registerGlobalReceiver(CHOOSE, (server, player, handler, buf, sender) -> {
			String id = buf.readString(32);
			server.execute(() -> choose(player, Race.byId(id).orElse(null)));
		});
		ServerPlayNetworking.registerGlobalReceiver(ABILITY, (server, player, handler, buf, sender) -> {
			int slot = buf.readVarInt();
			server.execute(() -> useAbility(player, slot));
		});
		ServerPlayNetworking.registerGlobalReceiver(SELECT_ECHO, (server, player, handler, buf, sender) -> {
			String id = buf.readString(64);
			server.execute(() -> {
				RaceData data = RaceState.of(player);
				if (data != null && data.race == Race.SHEPHERD && Shepherd.select(player, data, id)) {
					sync(player, data);
				}
			});
		});
		ServerPlayNetworking.registerGlobalReceiver(UNLOCK, (server, player, handler, buf, sender) -> {
			String id = buf.readString(64);
			server.execute(() -> {
				RaceData data = RaceState.of(player);
				if (data != null) {
					Progression.UnlockResult result = Progression.unlock(player, data, id);
					player.sendMessage(Text.translatable("mages_legacy.unlock." + result.name().toLowerCase(Locale.ROOT)), true);
				}
			});
		});
	}

	/** The first (and, without a command, only) choice of race. */
	public static boolean choose(ServerPlayerEntity player, Race race) {
		RaceData data = RaceState.of(player);
		if (data == null || data.race != null || race == null || !race.playable) {
			return false;
		}
		data.reset(race);
		player.sendMessage(Text.translatable("mages_legacy.message.chosen", Text.translatable(race.key())).formatted(Formatting.GOLD), false);
		if (race == Race.SHEPHERD) {
			player.sendMessage(Text.translatable("mages_legacy.message.shepherd_hint").formatted(Formatting.YELLOW), false);
		}
		sync(player, data);
		return true;
	}

	public static void useAbility(ServerPlayerEntity player, int slot) {
		RaceData data = RaceState.of(player);
		if (data == null || data.race == null || slot < 0 || slot >= SLOTS || player.isSpectator()) {
			return;
		}
		AbilityResult result = switch (data.race) {
			case MYCELID -> Mycelid.use(player, data, slot);
			case SHEPHERD -> Shepherd.use(player, data, slot);
			default -> AbilityResult.LOCKED;
		};
		if (result != AbilityResult.OK) {
			// Lacking the resource names it: spores, voice...
			String key = "mages_legacy.ability." + result.name().toLowerCase(Locale.ROOT);
			player.sendMessage(Text.translatable(result == AbilityResult.NO_RESOURCE ? key + "." + data.race.id() : key), true);
		}
		sync(player, data);
	}

	/** Shows the legend and the choice of race; false while the client has not registered our channels yet. */
	public static boolean openChoice(ServerPlayerEntity player) {
		if (!ServerPlayNetworking.canSend(player, OPEN_CHOICE)) {
			return false;
		}
		ServerPlayNetworking.send(player, OPEN_CHOICE, PacketByteBufs.empty());
		return true;
	}

	/** Everything the HUD and the tree screen show. */
	public static void sync(ServerPlayerEntity player, RaceData data) {
		// Until the client has registered our channels, the data stays dirty and goes out with a later tick
		if (!ServerPlayNetworking.canSend(player, SYNC)) {
			return;
		}
		data.dirty = false;
		PacketByteBuf buf = PacketByteBufs.create();
		buf.writeString(data.race == null ? "" : data.race.id());
		buf.writeFloat(data.resource);
		buf.writeVarInt(data.level);
		buf.writeVarInt(data.xp);
		buf.writeVarInt(RaceData.xpForNext(data.level));
		buf.writeVarInt(data.freePoints());
		buf.writeVarInt(Progression.openTiers(player));
		buf.writeVarInt(data.talents.size());
		data.talents.forEach(buf::writeString);
		long now = player.server.getTicks();
		for (int slot = 0; slot < SLOTS; slot++) {
			buf.writeVarInt((int) Math.max(0, data.readyAt.getOrDefault(slot, 0L) - now));
		}
		buf.writeVarInt(data.chainLeft > 0 && now <= data.chainUntil ? data.chainLeft : 0);
		// Race-specific extras: the Shepherd's capacity, bestiary and bonds
		buf.writeNbt(data.race == Race.SHEPHERD ? Shepherd.clientInfo(player, data) : new NbtCompound());
		ServerPlayNetworking.send(player, SYNC, buf);
	}
}
