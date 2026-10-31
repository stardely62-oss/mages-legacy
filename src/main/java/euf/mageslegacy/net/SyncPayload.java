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
		int gravity, boolean inPocket, List<String> talents, int[] progress, int charges, int chargesMax) implements CustomPacketPayload {
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
		// Through Worlds shows its charges: the slot recovers only while none is ready, towards the nearest one
		int passageSlot = euf.mageslegacy.race.Awakenings.slotOf(Talents.owned(player), Ability.PASSAGE);
		int charges = 0;
		int chargesMax = 0;
		if (passageSlot >= 0) {
			charges = euf.mageslegacy.voidborn.Passage.ready(player);
			chargesMax = euf.mageslegacy.voidborn.Passage.charges(player);
			cooldowns[passageSlot] = euf.mageslegacy.voidborn.Passage.nextIn(player);
			totals[passageSlot] = euf.mageslegacy.race.Abilities.cooldown(player, Ability.PASSAGE);
		}
		return new SyncPayload(player.getData(RaceState.RACE), level, player.getData(RaceState.XP), Progression.xpForNext(level), RaceState.resource(player),
				cooldowns, totals, state.step == null ? 0 : state.step.left(), state.gravity, Pocket.isPocket(player.level()), Talents.owned(player),
				trialProgress(player),
				charges, chargesMax);
	}

	/**
	 * What the screen shows of the trials: 0 nights in the Pocket, 1 steps through matter, 2 kills under the weight,
	 * 3 superlate passed, 4 open tiers, 5 of the steps in the Nether, 6 fliers killed, 7..9 whether each first trial is
	 * met (Rift of Space, Through Worlds, Black Hole), counting the old trials too.
	 */
	private static int[] trialProgress(ServerPlayer player) {
		var tag = player.getData(euf.mageslegacy.race.RaceState.PROGRESS);
		var owned = Talents.owned(player);
		// 17 the mastery trial begun, 18..20 its three deeds for the player's race
		var goals = RaceState.get(player).map(euf.mageslegacy.race.Mastery::goals).orElse(java.util.List.of());
		int[] mastery = new int[4];
		mastery[0] = euf.mageslegacy.race.Mastery.started(tag) ? 1 : 0;
		for (int i = 0; i < Math.min(3, goals.size()); i++) {
			mastery[i + 1] = euf.mageslegacy.race.Mastery.count(tag, goals.get(i));
		}
		return new int[] {tag.getInt(Talents.POCKET_NIGHTS), tag.getInt(Talents.STEP_THROUGH), tag.getInt(Talents.GRAVITY_KILLS),
				tag.getInt(Talents.SUPERLATE), Progression.openTiers(player), tag.getInt(Talents.STEP_THROUGH_NETHER), tag.getInt(Talents.GRAVITY_FLIERS),
				Talents.trialDone(euf.mageslegacy.race.Talent.AWAKENING_RIFT, owned, tag) ? 1 : 0,
				Talents.trialDone(euf.mageslegacy.race.Talent.AWAKENING_WORLDS, owned, tag) ? 1 : 0,
				Talents.trialDone(euf.mageslegacy.race.Talent.AWAKENING_HOLE, owned, tag) ? 1 : 0,
				// 10..16 the Mycelid's: nodes, journeys, infected kills, shared symbioses, then Bloom, Storm, Rebirth met
				tag.getInt(euf.mageslegacy.mycelid.Mycelid.NODES), tag.getInt(euf.mageslegacy.mycelid.Mycelid.JOURNEYS),
				tag.getInt(euf.mageslegacy.mycelid.Mycelid.INFECTED_KILLS), tag.getInt(euf.mageslegacy.mycelid.Mycelid.SHARED),
				Talents.trialDone(euf.mageslegacy.race.Talent.AWAKENING_BLOOM, owned, tag) ? 1 : 0,
				Talents.trialDone(euf.mageslegacy.race.Talent.AWAKENING_STORM, owned, tag) ? 1 : 0,
				Talents.trialDone(euf.mageslegacy.race.Talent.AWAKENING_REBIRTH, owned, tag) ? 1 : 0, mastery[0], mastery[1], mastery[2], mastery[3]};
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
		buf.writeVarInt(charges);
		buf.writeVarInt(chargesMax);
	}

	private static SyncPayload read(FriendlyByteBuf buf) {
		return new SyncPayload(buf.readUtf(), buf.readVarInt(), buf.readVarInt(), buf.readVarInt(), buf.readFloat(), buf.readVarIntArray(),
				buf.readVarIntArray(), buf.readVarInt(), buf.readVarInt(), buf.readBoolean(), buf.readList(FriendlyByteBuf::readUtf),
				buf.readVarIntArray(), buf.readVarInt(), buf.readVarInt());
	}

	@Override
	public Type<SyncPayload> type() {
		return TYPE;
	}
}
