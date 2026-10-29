package euf.mageslegacy.voidborn;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.saveddata.SavedData;

/** Which room of the Pocket dimension belongs to which Voidborn, and how far each has grown. Saved with the world. */
public final class PocketRooms extends SavedData {
	private static final String NAME = "mages_legacy_pockets";

	private final Map<UUID, Integer> rooms = new HashMap<>();
	private final Map<UUID, Integer> sizes = new HashMap<>();
	/** Rooms with High Vaults: twice the build space above and below. */
	private final Map<UUID, Boolean> tall = new HashMap<>();
	private int next;

	public static PocketRooms get(MinecraftServer server) {
		return server.overworld().getDataStorage().computeIfAbsent(new SavedData.Factory<>(PocketRooms::new, PocketRooms::load, null), NAME);
	}

	/** The owner's room index, given out on first use. */
	public int roomOf(UUID owner) {
		Integer room = rooms.get(owner);
		if (room == null) {
			room = next++;
			rooms.put(owner, room);
			setDirty();
		}
		return room;
	}

	/** The platform's width: 5, 9, 15 or 21 blocks. */
	public int sizeOf(UUID owner) {
		return sizes.getOrDefault(owner, 5);
	}

	public void setSize(UUID owner, int size) {
		sizes.put(owner, size);
		setDirty();
	}

	public boolean isTall(UUID owner) {
		return tall.getOrDefault(owner, false);
	}

	public void setTall(UUID owner, boolean value) {
		tall.put(owner, value);
		setDirty();
	}

	/** Whose room this index is, or null. */
	public UUID ownerOf(int room) {
		for (Map.Entry<UUID, Integer> entry : rooms.entrySet()) {
			if (entry.getValue() == room) {
				return entry.getKey();
			}
		}
		return null;
	}

	@Override
	public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
		CompoundTag list = new CompoundTag();
		rooms.forEach((owner, room) -> {
			CompoundTag entry = new CompoundTag();
			entry.putInt("room", room);
			entry.putInt("size", sizeOf(owner));
			entry.putBoolean("tall", isTall(owner));
			list.put(owner.toString(), entry);
		});
		tag.put("rooms", list);
		tag.putInt("next", next);
		return tag;
	}

	private static PocketRooms load(CompoundTag tag, HolderLookup.Provider registries) {
		PocketRooms data = new PocketRooms();
		CompoundTag list = tag.getCompound("rooms");
		for (String key : list.getAllKeys()) {
			UUID owner = UUID.fromString(key);
			CompoundTag entry = list.getCompound(key);
			data.rooms.put(owner, entry.getInt("room"));
			data.sizes.put(owner, entry.getInt("size"));
			data.tall.put(owner, entry.getBoolean("tall"));
		}
		data.next = tag.getInt("next");
		return data;
	}
}
