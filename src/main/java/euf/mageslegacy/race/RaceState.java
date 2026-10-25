package euf.mageslegacy.race;

import euf.mageslegacy.MagesLegacy;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtElement;
import net.minecraft.nbt.NbtList;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.GlobalPos;
import net.minecraft.world.PersistentState;
import net.minecraft.world.World;

/** Every player's race data and every mycelium node, saved with the overworld. */
public class RaceState extends PersistentState {
	private final Map<UUID, RaceData> players = new HashMap<>();
	/** Mycelium nodes per owner, oldest first. */
	private final Map<UUID, List<GlobalPos>> nodes = new HashMap<>();

	public static RaceState get(MinecraftServer server) {
		return server.getOverworld().getPersistentStateManager().getOrCreate(RaceState::fromNbt, RaceState::new, MagesLegacy.MOD_ID);
	}

	/** The player's data, or null on the client or before the server is up. */
	public static RaceData of(PlayerEntity player) {
		MinecraftServer server = player.getServer();
		return server == null || player.getWorld().isClient ? null : get(server).data(player.getUuid());
	}

	public static Optional<Race> raceOf(PlayerEntity player) {
		RaceData data = of(player);
		return data == null ? Optional.empty() : Optional.ofNullable(data.race);
	}

	public RaceData data(UUID player) {
		// Data changes in place all the time; saving it on every world save is cheap
		markDirty();
		return players.computeIfAbsent(player, id -> new RaceData());
	}

	public List<GlobalPos> nodes(UUID owner) {
		return nodes.computeIfAbsent(owner, id -> new ArrayList<>());
	}

	public Optional<UUID> nodeOwner(RegistryKey<World> dimension, BlockPos pos) {
		GlobalPos key = GlobalPos.create(dimension, pos);
		return nodes.entrySet().stream().filter(e -> e.getValue().contains(key)).map(Map.Entry::getKey).findFirst();
	}

	public void removeNode(RegistryKey<World> dimension, BlockPos pos) {
		GlobalPos key = GlobalPos.create(dimension, pos);
		nodes.values().forEach(list -> list.remove(key));
		markDirty();
	}

	@Override
	public NbtCompound writeNbt(NbtCompound nbt) {
		NbtCompound playerTag = new NbtCompound();
		players.forEach((id, data) -> playerTag.put(id.toString(), data.toNbt()));
		nbt.put("players", playerTag);
		NbtCompound nodeTag = new NbtCompound();
		nodes.forEach((id, list) -> {
			NbtList entries = new NbtList();
			for (GlobalPos pos : list) {
				NbtCompound entry = new NbtCompound();
				entry.putString("dim", pos.getDimension().getValue().toString());
				entry.putLong("pos", pos.getPos().asLong());
				entries.add(entry);
			}
			nodeTag.put(id.toString(), entries);
		});
		nbt.put("nodes", nodeTag);
		return nbt;
	}

	private static RaceState fromNbt(NbtCompound nbt) {
		RaceState state = new RaceState();
		NbtCompound playerTag = nbt.getCompound("players");
		for (String key : playerTag.getKeys()) {
			state.players.put(UUID.fromString(key), RaceData.fromNbt(playerTag.getCompound(key)));
		}
		NbtCompound nodeTag = nbt.getCompound("nodes");
		for (String key : nodeTag.getKeys()) {
			List<GlobalPos> list = new ArrayList<>();
			for (NbtElement e : nodeTag.getList(key, NbtElement.COMPOUND_TYPE)) {
				NbtCompound entry = (NbtCompound) e;
				RegistryKey<World> dim = RegistryKey.of(RegistryKeys.WORLD, new Identifier(entry.getString("dim")));
				list.add(GlobalPos.create(dim, BlockPos.fromLong(entry.getLong("pos"))));
			}
			state.nodes.put(UUID.fromString(key), list);
		}
		return state;
	}
}
