package euf.mageslegacy.race;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * A race's active ability: which key it sits on, the race level it opens at (for free), what it costs and how long it
 * recovers. Keys: 0 R, 1 V, 2 Z, 3 H, 4 left Alt; Z and Alt are kept for the Awakenings.
 */
public enum Ability {
	VOID_STEP(Race.VOIDBORN, 0, 3, 20, 6 * 20),
	GRAVITY(Race.VOIDBORN, 1, 5, 25, 18 * 20),
	POCKET(Race.VOIDBORN, 3, 1, 20, 10 * 20),
	// The Awakenings: no fixed key; the first one learnt sits on Z, the second on left Alt (see Awakenings)
	RIFT_ARENA(Race.VOIDBORN, -1, Talent.AWAKENING_LEVEL, 60, 5 * 60 * 20),
	PASSAGE(Race.VOIDBORN, -1, Talent.AWAKENING_LEVEL, 40, 2 * 60 * 20),
	BLACK_HOLE(Race.VOIDBORN, -1, Talent.AWAKENING_LEVEL, 50, 5 * 60 * 20);

	public static final int SLOTS = 5;
	/** Key names as they stand by default, for the HUD and the choice screen. */
	public static final String[] KEY_NAMES = {"R", "V", "Z", "H", "Alt"};

	public final Race race;
	public final int slot;
	public final int level;
	public final int cost;
	public final int cooldown;

	Ability(Race race, int slot, int level, int cost, int cooldown) {
		this.race = race;
		this.slot = slot;
		this.level = level;
		this.cost = cost;
		this.cooldown = cooldown;
	}

	public String id() {
		return name().toLowerCase(java.util.Locale.ROOT);
	}

	/** Translation key prefix: mages_legacy.ability.<id>. */
	public String key() {
		return "mages_legacy.ability." + id();
	}

	public boolean awakening() {
		return slot < 0;
	}

	public static Optional<Ability> of(Race race, int slot) {
		for (Ability ability : values()) {
			if (ability.race == race && ability.slot == slot) {
				return Optional.of(ability);
			}
		}
		return Optional.empty();
	}

	/** A race's abilities in key order (R, V, Z, H, Alt), as the HUD shows them. */
	public static List<Ability> bySlot(Race race) {
		List<Ability> list = of(race);
		list.sort(java.util.Comparator.comparingInt(a -> a.slot));
		return list;
	}

	/** A race's abilities in the order they open. */
	public static List<Ability> of(Race race) {
		List<Ability> list = new ArrayList<>();
		for (Ability ability : values()) {
			if (ability.race == race && !ability.awakening()) {
				list.add(ability);
			}
		}
		list.sort(java.util.Comparator.comparingInt(a -> a.level));
		return list;
	}
}
