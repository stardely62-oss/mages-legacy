package euf.mageslegacy.shepherd;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtElement;
import net.minecraft.nbt.NbtList;

/**
 * What a Shepherd keeps: kills per mob type (the bestiary of echoes), the echo picked for the Call, the bonded beasts,
 * and bond rituals still in progress. Saved with the rest of the race data.
 */
public class ShepherdData {
	/** Entity type id -> how many of them this Shepherd (or its creatures) killed. */
	public final Map<String, Integer> kills = new HashMap<>();
	public String selectedEcho = "";
	/**
	 * Three squads of echoes the Call brings at once (echo type id -> how many); {@code activePreset} is the one
	 * the Call uses now.
	 */
	public final List<Map<String, Integer>> presets = List.of(new LinkedHashMap<>(), new LinkedHashMap<>(), new LinkedHashMap<>());
	public int activePreset;
	public final List<Bond> bonds = new ArrayList<>();
	/** Animal uuid -> a bond ritual under way. */
	public final Map<UUID, Ritual> rituals = new HashMap<>();

	/**
	 * A bonded beast. While it rests after falling, {@code restingUntil} is the world time it comes back and
	 * {@code body} holds the entity, to bring it back as it was.
	 */
	public static class Bond {
		public UUID entity;
		public String kind;
		public String name = "";
		public int level = 1;
		public int xp;
		public long restingUntil;
		public NbtCompound body;
		/** Chosen in the bestiary to lead the pack (the Alpha crown). */
		public boolean leader;
		/** World time its last meal wears off (fed beasts heal and hit harder), when it was last petted. */
		public long fedUntil;
		public long pettedAt;
		/** World time Not Today last saved it from a killing blow. */
		public long savedAt;
		/** Told to stay put (sneak + right click): it doesn't follow until called again. */
		public boolean staying;

		public boolean resting() {
			return body != null;
		}

		NbtCompound toNbt() {
			NbtCompound nbt = new NbtCompound();
			nbt.putUuid("entity", entity);
			nbt.putString("kind", kind);
			nbt.putString("name", name);
			nbt.putInt("level", level);
			nbt.putInt("xp", xp);
			nbt.putLong("restingUntil", restingUntil);
			nbt.putBoolean("leader", leader);
			nbt.putLong("fedUntil", fedUntil);
			nbt.putLong("pettedAt", pettedAt);
			nbt.putLong("savedAt", savedAt);
			nbt.putBoolean("staying", staying);
			if (body != null) {
				nbt.put("body", body);
			}
			return nbt;
		}

		static Bond fromNbt(NbtCompound nbt) {
			Bond bond = new Bond();
			bond.entity = nbt.getUuid("entity");
			bond.kind = nbt.getString("kind");
			bond.name = nbt.getString("name");
			bond.level = Math.max(1, nbt.getInt("level"));
			bond.xp = nbt.getInt("xp");
			bond.restingUntil = nbt.getLong("restingUntil");
			bond.leader = nbt.getBoolean("leader");
			bond.fedUntil = nbt.getLong("fedUntil");
			bond.pettedAt = nbt.getLong("pettedAt");
			bond.savedAt = nbt.getLong("savedAt");
			bond.staying = nbt.getBoolean("staying");
			bond.body = nbt.contains("body") ? nbt.getCompound("body") : null;
			return bond;
		}
	}

	/**
	 * A bond ritual: the Song was sung until {@code listenUntil}; {@code feedings} so far (or songs, headbutts, kills,
	 * seconds of patience: whatever this beast's way counts), the last at {@code lastFeed}; {@code note} remembers
	 * e.g. which flower came first.
	 */
	public static class Ritual {
		public long listenUntil;
		public int feedings;
		public long lastFeed;
		public String note = "";
		/** Which beast kind listens (for the bestiary). */
		public String kind = "";
	}

	public Optional<Bond> bond(UUID entity) {
		return bonds.stream().filter(b -> b.entity.equals(entity)).findFirst();
	}

	public int kills(String type) {
		return kills.getOrDefault(type, 0);
	}

	public void clear() {
		kills.clear();
		selectedEcho = "";
		presets.forEach(Map::clear);
		activePreset = 0;
		bonds.clear();
		rituals.clear();
	}

	public NbtCompound toNbt() {
		NbtCompound nbt = new NbtCompound();
		NbtCompound killNbt = new NbtCompound();
		kills.forEach(killNbt::putInt);
		nbt.put("kills", killNbt);
		nbt.putString("selected", selectedEcho);
		NbtList presetList = new NbtList();
		for (Map<String, Integer> preset : presets) {
			NbtCompound p = new NbtCompound();
			preset.forEach(p::putInt);
			presetList.add(p);
		}
		nbt.put("presets", presetList);
		nbt.putInt("activePreset", activePreset);
		NbtList bondList = new NbtList();
		bonds.forEach(b -> bondList.add(b.toNbt()));
		nbt.put("bonds", bondList);
		NbtList ritualList = new NbtList();
		rituals.forEach((id, r) -> {
			NbtCompound e = new NbtCompound();
			e.putUuid("id", id);
			e.putLong("listenUntil", r.listenUntil);
			e.putInt("feedings", r.feedings);
			e.putLong("lastFeed", r.lastFeed);
			e.putString("note", r.note);
			e.putString("kind", r.kind);
			ritualList.add(e);
		});
		nbt.put("rituals", ritualList);
		return nbt;
	}

	public void readNbt(NbtCompound nbt) {
		clear();
		NbtCompound killNbt = nbt.getCompound("kills");
		for (String key : killNbt.getKeys()) {
			kills.put(key, killNbt.getInt(key));
		}
		selectedEcho = nbt.getString("selected");
		NbtList presetList = nbt.getList("presets", NbtElement.COMPOUND_TYPE);
		for (int i = 0; i < presets.size() && i < presetList.size(); i++) {
			NbtCompound p = presetList.getCompound(i);
			for (String key : p.getKeys()) {
				presets.get(i).put(key, p.getInt(key));
			}
		}
		activePreset = Math.floorMod(nbt.getInt("activePreset"), presets.size());
		for (NbtElement e : nbt.getList("bonds", NbtElement.COMPOUND_TYPE)) {
			bonds.add(Bond.fromNbt((NbtCompound) e));
		}
		for (NbtElement e : nbt.getList("rituals", NbtElement.COMPOUND_TYPE)) {
			NbtCompound c = (NbtCompound) e;
			Ritual r = new Ritual();
			r.listenUntil = c.getLong("listenUntil");
			r.feedings = c.getInt("feedings");
			r.lastFeed = c.getLong("lastFeed");
			r.note = c.getString("note");
			r.kind = c.getString("kind");
			rituals.put(c.getUuid("id"), r);
		}
	}
}
