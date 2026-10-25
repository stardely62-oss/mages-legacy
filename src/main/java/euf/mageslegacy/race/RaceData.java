package euf.mageslegacy.race;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtElement;
import net.minecraft.nbt.NbtList;
import net.minecraft.nbt.NbtString;

/** One player's race, resource, experience and talents. Cooldowns and chains are not saved. */
public class RaceData {
	public static final int MAX_LEVEL = 50;
	public static final float MAX_RESOURCE = 100;

	public Race race;
	public float resource;
	public int xp;
	public int level;
	public final Set<String> talents = new LinkedHashSet<>();
	/** Effect ids of the mushroom tastes now active, oldest first. */
	public final List<String> tastes = new ArrayList<>();

	// Not saved: server ticks at which each ability slot is ready again, and the spore jump chain
	public final Map<Integer, Long> readyAt = new HashMap<>();
	public int chainLeft;
	public long chainUntil;
	public boolean dirty = true;
	/** The legend and the choice of race were sent to the client this session. */
	public boolean prompted;

	public boolean has(String talent) {
		return talents.contains(talent);
	}

	public void addResource(float amount) {
		float before = resource;
		resource = Math.max(0, Math.min(MAX_RESOURCE, resource + amount));
		dirty |= (int) before != (int) resource;
	}

	/** Experience needed to go from {@code level} to the next one. */
	public static int xpForNext(int level) {
		return 40 + 20 * level;
	}

	/** Adds race experience; returns how many levels were gained. */
	public int addXp(int amount) {
		if (amount <= 0 || level >= MAX_LEVEL) {
			return 0;
		}
		xp += amount;
		int gained = 0;
		while (level < MAX_LEVEL && xp >= xpForNext(level)) {
			xp -= xpForNext(level);
			level++;
			gained++;
		}
		if (level >= MAX_LEVEL) {
			xp = 0;
		}
		dirty = true;
		return gained;
	}

	/** One point per level, two more at every tenth level: 60 at level 50. */
	public int totalPoints() {
		return level + 2 * (level / 10);
	}

	public int spentPoints() {
		return talents.stream().mapToInt(id -> Talents.byId(id).map(t -> t.cost()).orElse(0)).sum();
	}

	public int freePoints() {
		return totalPoints() - spentPoints();
	}

	public void reset(Race newRace) {
		race = newRace;
		resource = 0;
		xp = 0;
		level = 0;
		talents.clear();
		tastes.clear();
		readyAt.clear();
		chainLeft = 0;
		prompted = false;
		dirty = true;
	}

	public NbtCompound toNbt() {
		NbtCompound nbt = new NbtCompound();
		if (race != null) {
			nbt.putString("race", race.id());
		}
		nbt.putFloat("resource", resource);
		nbt.putInt("xp", xp);
		nbt.putInt("level", level);
		NbtList list = new NbtList();
		talents.forEach(t -> list.add(NbtString.of(t)));
		nbt.put("talents", list);
		NbtList tasteList = new NbtList();
		tastes.forEach(t -> tasteList.add(NbtString.of(t)));
		nbt.put("tastes", tasteList);
		return nbt;
	}

	public static RaceData fromNbt(NbtCompound nbt) {
		RaceData data = new RaceData();
		data.race = Race.byId(nbt.getString("race")).orElse(null);
		data.resource = nbt.getFloat("resource");
		data.xp = nbt.getInt("xp");
		data.level = nbt.getInt("level");
		for (NbtElement e : nbt.getList("talents", NbtElement.STRING_TYPE)) {
			data.talents.add(e.asString());
		}
		for (NbtElement e : nbt.getList("tastes", NbtElement.STRING_TYPE)) {
			data.tastes.add(e.asString());
		}
		return data;
	}
}
