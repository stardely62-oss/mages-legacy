package euf.mageslegacy.net;

import euf.mageslegacy.MagesLegacy;
import euf.mageslegacy.race.Ability;
import euf.mageslegacy.race.Progression;
import euf.mageslegacy.race.RaceState;
import euf.mageslegacy.race.Talents;
import java.util.List;
import euf.mageslegacy.voidborn.Pocket;
import euf.mageslegacy.voidborn.Voidborn;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerPlayer;

/**
 * Server to client: everything the HUD and the screens show about the player's race. Cooldowns are ticks left and
 * the full length, per key slot; {@code stepping} and {@code gravity} are ticks left of the running effects.
 */
public record SyncPayload(String race, int level, int xp, int xpNext, float resource, int[] cooldowns, int[] cooldownTotals, int stepping,
		int gravity, boolean inPocket, List<String> talents, int[] progress) implements CustomPacketPayload {
	public static final Type<SyncPayload> TYPE = new Type<>(MagesLegacy.id("sync"));
	public static final StreamCodec<FriendlyByteBuf, SyncPayload> STREAM_CODEC = CustomPacketPayload.codec(SyncPayload::write, SyncPayload::read);

	public static SyncPayload of(ServerPlayer player) {
		int level = RaceState.level(player);
		Voidborn.State state = Voidborn.state(player);
		int[] cooldowns = new int[Ability.SLOTS];
		int[] totals = new int[Ability.SLOTS];
		for (int i = 0; i < Ability.SLOTS; i++) {
			cooldowns[i] = state.cooldown[i];
			totals[i] = state.cooldownTotal[i];
		}
		return new SyncPayload(player.getData(RaceState.RACE), level, player.getData(RaceState.XP), Progression.xpForNext(level), RaceState.resource(player),
				cooldowns, totals, state.step == null ? 0 : state.step.left(), state.gravity, Pocket.isPocket(player.level()), Talents.owned(player),
				new int[] {Talents.progress(player, Talents.POCKET_TICKS), Talents.progress(player, Talents.STEP_BLOCKS),
						Talents.progress(player, Talents.GRAVITY_KILLS)});
	}

	private void write(FriendlyByteBuf buf) {
		buf.writeUtf(race);
		buf.writeVarInt(level);
		buf.writeVarInt(xp);
		buf.writeVarInt(xpNext);
		buf.writeFloat(resource);
		buf.writeVarIntArray(cooldowns);
		buf.writeVarIntArray(cooldownTotals);
		buf.writeVarInt(stepping);
		buf.writeVarInt(gravity);
		buf.writeBoolean(inPocket);
		buf.writeCollection(talents, FriendlyByteBuf::writeUtf);
		buf.writeVarIntArray(progress);
	}

	private static SyncPayload read(FriendlyByteBuf buf) {
		return new SyncPayload(buf.readUtf(), buf.readVarInt(), buf.readVarInt(), buf.readVarInt(), buf.readFloat(), buf.readVarIntArray(),
				buf.readVarIntArray(), buf.readVarInt(), buf.readVarInt(), buf.readBoolean(), buf.readList(FriendlyByteBuf::readUtf),
				buf.readVarIntArray());
	}

	@Override
	public Type<SyncPayload> type() {
		return TYPE;
	}
}
