package euf.mageslegacy.client;

import euf.mageslegacy.net.RacePackets;
import euf.mageslegacy.race.ClientRaceView;
import euf.mageslegacy.race.Race;
import java.util.HashSet;
import java.util.Set;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.network.PacketByteBuf;

/** The client's copy of its own race data, from the sync packet; cooldowns count down locally between syncs. */
public final class ClientRaceState {
	public static Race race;
	public static float resource;
	public static int level;
	public static int xp;
	public static int xpNext = 40;
	public static int freePoints;
	public static int openTiers = 1;
	public static final Set<String> talents = new HashSet<>();
	private static final int[] cooldowns = new int[RacePackets.SLOTS];
	public static int chainLeft;
	/** Race extras from the sync: for Shepherds the capacity, bestiary kills, picked echo and bonds. */
	public static NbtCompound extra = new NbtCompound();
	private static long receivedAt;
	private static long clientTicks;

	private ClientRaceState() {
	}

	public static void read(PacketByteBuf buf) {
		String id = buf.readString();
		race = Race.byId(id).orElse(null);
		ClientRaceView.race = race;
		resource = buf.readFloat();
		level = buf.readVarInt();
		xp = buf.readVarInt();
		xpNext = buf.readVarInt();
		freePoints = buf.readVarInt();
		openTiers = buf.readVarInt();
		talents.clear();
		int count = buf.readVarInt();
		for (int i = 0; i < count; i++) {
			talents.add(buf.readString());
		}
		for (int slot = 0; slot < RacePackets.SLOTS; slot++) {
			cooldowns[slot] = buf.readVarInt();
		}
		chainLeft = buf.readVarInt();
		NbtCompound nbt = buf.readNbt();
		extra = nbt == null ? new NbtCompound() : nbt;
		receivedAt = clientTicks;
	}

	public static void tick() {
		clientTicks++;
	}

	/** Ticks until the slot is ready again. */
	public static int cooldown(int slot) {
		return (int) Math.max(0, cooldowns[slot] - (clientTicks - receivedAt));
	}

	public static void clear() {
		race = null;
		ClientRaceView.race = null;
		talents.clear();
		resource = 0;
		level = 0;
	}
}
