package euf.mageslegacy.race;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

/**
 * A node of a race's talent tree. The Voidborn tree is a grid: three columns (Through Worlds, Rift of Space,
 * Gravity), the tiers as rows from the roots down to the Awakenings, cross talents on the borders between columns.
 * {@code col} and {@code row} place the node; the row is its tier.
 * A node needs any one of {@code requires}; the core and the basic abilities open by themselves at their level.
 */
public enum Talent {
	// The core and the basic abilities, free and automatic
	POCKET(Race.VOIDBORN, Kind.CORE, Branch.CORE, 9, 0, 0, 1, "ability_pocket"),
	VOID_STEP(Race.VOIDBORN, Kind.BASIC, Branch.WORLDS, 2.5f, 0, 0, 3, "ability_void_step", "pocket"),
	GRAVITY(Race.VOIDBORN, Kind.BASIC, Branch.GRAVITY, 14.5f, 0, 0, 5, "ability_gravity", "pocket"),

	// Rift of Space: the Pocket grows, its door gets cheaper and wider
	POCKET_WIDE_1(Race.VOIDBORN, Kind.PASSIVE, Branch.RIFT, 9, 1, 1, 0, "pocket_wide", "pocket"),
	POCKET_WIDE_2(Race.VOIDBORN, Kind.PASSIVE, Branch.RIFT, 9, 2, 2, 0, "pocket_wide", "pocket_wide_1"),
	POCKET_WIDE_3(Race.VOIDBORN, Kind.PASSIVE, Branch.RIFT, 9, 3, 3, 0, "pocket_wide", "pocket_wide_2"),
	POCKET_CHEAP(Race.VOIDBORN, Kind.PASSIVE, Branch.RIFT, 10, 2, 1, 0, "pocket_cheap", "pocket_wide_1"),
	POCKET_GUESTS(Race.VOIDBORN, Kind.TRANSFORM, Branch.RIFT, 8, 2, 1, 0, "pocket_guests", "pocket_wide_1"),
	POCKET_VAULT(Race.VOIDBORN, Kind.PASSIVE, Branch.RIFT, 8, 3, 2, 0, "pocket_home", "pocket_guests"),
	POCKET_CRADLE(Race.VOIDBORN, Kind.PASSIVE, Branch.RIFT, 10, 3, 1, 0, "pocket_rest", "pocket_cheap"),
	AWAKENING_RIFT(Race.VOIDBORN, Kind.AWAKENING, Branch.RIFT, 8.5f, 5, 5, 0, "pocket_storm", "pocket_wide_3", "pocket_vault"),
	POCKET_SANCTUARY(Race.VOIDBORN, Kind.PASSIVE, Branch.RIFT, 7.5f, 1, 1, 0, "pocket_ward", "pocket"),
	POCKET_HEARTH(Race.VOIDBORN, Kind.PASSIVE, Branch.RIFT, 10.5f, 1, 1, 0, "pocket_hearth", "pocket"),
	POCKET_GARDEN(Race.VOIDBORN, Kind.PASSIVE, Branch.RIFT, 11, 3, 2, 0, "pocket_garden", "pocket_hearth"),
	POCKET_TWO_DOORS(Race.VOIDBORN, Kind.TRANSFORM, Branch.RIFT, 7, 3, 3, 0, "pocket_quick", "pocket_sanctuary"),
	POCKET_SHELTER(Race.VOIDBORN, Kind.TRANSFORM, Branch.RIFT, 7.5f, 4, 3, 0, "pocket_home", "pocket_two_doors", "pocket_vault"),
	VOID_EYES(Race.VOIDBORN, Kind.PASSIVE, Branch.RIFT, 10.5f, 4, 2, 0, "abyss_gaze", "pocket_garden", "pocket_cradle"),
	ARENA_REGEN(Race.VOIDBORN, Kind.PASSIVE, Branch.RIFT, 7, 6, 2, 0, "pocket_garden", "awakening_rift"),
	ARENA_MIGHT(Race.VOIDBORN, Kind.PASSIVE, Branch.RIFT, 8, 6, 2, 0, "pocket_hearth", "awakening_rift"),
	ARENA_WARD(Race.VOIDBORN, Kind.PASSIVE, Branch.RIFT, 9, 6, 2, 0, "pocket_ward", "awakening_rift"),
	ARENA_LONG(Race.VOIDBORN, Kind.TRANSFORM, Branch.RIFT, 10, 6, 2, 0, "pocket_quick", "awakening_rift"),

	// Through Worlds: farther, faster, together, and marks in space
	STEP_FAR(Race.VOIDBORN, Kind.PASSIVE, Branch.WORLDS, 2, 1, 1, 0, "step_far", "void_step"),
	STEP_FAR_2(Race.VOIDBORN, Kind.PASSIVE, Branch.WORLDS, 1.75f, 3, 2, 0, "step_sever", "step_far"),
	STEP_QUICK(Race.VOIDBORN, Kind.TRANSFORM, Branch.WORLDS, 1, 1, 2, 0, "step_quick", "void_step"),
	STEP_TRACE(Race.VOIDBORN, Kind.PASSIVE, Branch.WORLDS, 3.5f, 1, 1, 0, "step_veil", "void_step"),
	STEP_ALLY(Race.VOIDBORN, Kind.TRANSFORM, Branch.WORLDS, 2.75f, 2, 2, 0, "step_ally", "step_far", "step_trace"),
	STEP_SOFT(Race.VOIDBORN, Kind.PASSIVE, Branch.WORLDS, 5, 1, 1, 0, "step_veil", "void_step"),
	STEP_SENSE(Race.VOIDBORN, Kind.PASSIVE, Branch.WORLDS, 0, 1, 1, 0, "step_sever", "void_step"),
	STEP_ECHO(Race.VOIDBORN, Kind.TRANSFORM, Branch.WORLDS, 0, 2, 2, 0, "step_rift", "step_sense"),
	STEP_AMBUSH(Race.VOIDBORN, Kind.PASSIVE, Branch.WORLDS, 5, 2, 2, 0, "step_cut", "step_soft"),
	STEP_STONE(Race.VOIDBORN, Kind.PASSIVE, Branch.WORLDS, 0.5f, 3, 3, 0, "step_far", "step_echo", "step_quick"),
	AWAKENING_WORLDS(Race.VOIDBORN, Kind.AWAKENING, Branch.WORLDS, 1.25f, 5, 5, 0, "abyss_gaze", "step_far_2", "step_stone"),
	WORLDS_LONG(Race.VOIDBORN, Kind.PASSIVE, Branch.WORLDS, 0, 6, 2, 0, "excise_long", "awakening_worlds"),
	WORLDS_CHARGE_2(Race.VOIDBORN, Kind.PASSIVE, Branch.WORLDS, 1, 6, 2, 0, "step_cut", "awakening_worlds"),
	WORLDS_CHARGE_3(Race.VOIDBORN, Kind.PASSIVE, Branch.WORLDS, 1, 7, 3, 0, "step_cut", "worlds_charge_2"),
	WORLDS_TRAIN(Race.VOIDBORN, Kind.TRANSFORM, Branch.WORLDS, 2, 6, 2, 0, "excise_drop", "awakening_worlds"),
	WORLDS_CLEANSE(Race.VOIDBORN, Kind.PASSIVE, Branch.WORLDS, 3, 6, 2, 0, "step_mend", "awakening_worlds"),

	// Gravity: wider, heavier, pulling, bending what flies
	GRAV_WIDE(Race.VOIDBORN, Kind.PASSIVE, Branch.GRAVITY, 13, 1, 1, 0, "well_wide", "gravity"),
	GRAV_SLOW(Race.VOIDBORN, Kind.PASSIVE, Branch.GRAVITY, 14, 1, 1, 0, "well_hold", "gravity"),
	GRAV_PULL(Race.VOIDBORN, Kind.TRANSFORM, Branch.GRAVITY, 13, 3, 2, 0, "well_hunger", "grav_wide"),
	GRAV_FLIERS(Race.VOIDBORN, Kind.PASSIVE, Branch.GRAVITY, 13.5f, 2, 1, 0, "well_long", "grav_wide", "grav_slow"),
	GRAV_ARROWS(Race.VOIDBORN, Kind.TRANSFORM, Branch.GRAVITY, 14, 3, 2, 0, "well_eat", "grav_slow"),
	GRAV_ANCHOR(Race.VOIDBORN, Kind.PASSIVE, Branch.GRAVITY, 15, 1, 1, 0, "well_heart", "gravity"),
	GRAV_DRAIN(Race.VOIDBORN, Kind.PASSIVE, Branch.GRAVITY, 16, 1, 1, 0, "well_eat", "gravity"),
	GRAV_FUSE(Race.VOIDBORN, Kind.PASSIVE, Branch.GRAVITY, 15, 2, 2, 0, "well_hold", "grav_anchor"),
	GRAV_CHAIN(Race.VOIDBORN, Kind.TRANSFORM, Branch.GRAVITY, 16, 2, 2, 0, "well_long", "grav_drain"),
	GRAV_LINGER(Race.VOIDBORN, Kind.TRANSFORM, Branch.GRAVITY, 15, 4, 3, 0, "well_wide", "grav_fuse", "grav_pull"),
	GRAV_FEAST(Race.VOIDBORN, Kind.PASSIVE, Branch.GRAVITY, 16, 4, 3, 0, "well_hunger", "grav_chain", "grav_arrows"),
	AWAKENING_HOLE(Race.VOIDBORN, Kind.AWAKENING, Branch.GRAVITY, 13.5f, 5, 5, 0, "well_collapse", "grav_pull", "grav_arrows"),
	HOLE_WIDE(Race.VOIDBORN, Kind.PASSIVE, Branch.GRAVITY, 13, 6, 2, 0, "well_heart", "awakening_hole"),
	HOLE_DEEP(Race.VOIDBORN, Kind.PASSIVE, Branch.GRAVITY, 14, 6, 2, 0, "step_rend", "awakening_hole"),
	HOLE_QUICK(Race.VOIDBORN, Kind.TRANSFORM, Branch.GRAVITY, 15, 6, 2, 0, "excise_cheap", "awakening_hole"),
	HOLE_SATED(Race.VOIDBORN, Kind.PASSIVE, Branch.GRAVITY, 16, 6, 2, 0, "well_collapse", "awakening_hole"),

	// Cross talents between the directions
	STEP_DOOR(Race.VOIDBORN, Kind.CROSS, Branch.CROSS, 6, 2, 2, 0, "step_pull", "pocket_guests", "step_quick"),
	STEP_HEAVY(Race.VOIDBORN, Kind.CROSS, Branch.CROSS, 4, 2, 2, 0, "step_mend", "step_trace", "grav_slow"),
	DOOR_RECOIL(Race.VOIDBORN, Kind.CROSS, Branch.CROSS, 12, 2, 2, 0, "pocket_water", "grav_wide", "pocket_cheap"),
	STEP_HOMEWARD(Race.VOIDBORN, Kind.CROSS, Branch.CROSS, 6, 4, 3, 0, "step_door", "pocket_two_doors", "step_echo"),
	STEP_RUPTURE(Race.VOIDBORN, Kind.CROSS, Branch.CROSS, 5, 4, 3, 0, "step_rend", "step_ambush", "grav_chain"),
	POCKET_HOLD(Race.VOIDBORN, Kind.CROSS, Branch.CROSS, 12, 4, 3, 0, "pocket_rest", "grav_fuse", "pocket_garden");

	public enum Kind {
		CORE, BASIC, PASSIVE, TRANSFORM, CROSS, AWAKENING;

		public String key() {
			return "mages_legacy.talent.kind." + name().toLowerCase(Locale.ROOT);
		}
	}

	public enum Branch {
		CORE, RIFT, WORLDS, GRAVITY, CROSS
	}

	public static final int MAX_TIER = 4;
	/** Race level each tier opens at. */
	public static final int[] TIER_LEVELS = {1, 10, 20, 30};
	/** The first Awakening opens from this race level on. */
	public static final int AWAKENING_LEVEL = 30;

	public final Race race;
	public final Kind kind;
	public final Branch branch;
	/** Where the node stands in the tree's grid: a column (halves allowed) and a row (0 the roots, 1-4 tiers I-III, 5+ IV). */
	public final float col;
	public final int row;
	/** The cost written in the table; the real one comes from the tier, see {@link #cost()}. */
	private final int baseCost;
	/** For the core and basic abilities: the race level they open at by themselves. */
	public final int autoLevel;
	public final String icon;
	private final String[] requires;

	Talent(Race race, Kind kind, Branch branch, float col, int row, int cost, int autoLevel, String icon, String... requires) {
		this.race = race;
		this.kind = kind;
		this.branch = branch;
		this.col = col;
		this.row = row;
		this.baseCost = cost;
		this.autoLevel = autoLevel;
		this.icon = icon;
		this.requires = requires;
	}

	public String id() {
		return name().toLowerCase(Locale.ROOT);
	}

	public String key() {
		return "mages_legacy.talent." + id();
	}

	public boolean automatic() {
		return kind == Kind.CORE || kind == Kind.BASIC;
	}

	public List<Talent> requires() {
		List<Talent> list = new ArrayList<>();
		for (String id : requires) {
			byId(id).ifPresent(list::add);
		}
		return list;
	}

	/** The Awakening this node grows from, if it is one of an Awakening's own talents. */
	public Talent awakening() {
		for (Talent t : requires()) {
			if (t.kind == Kind.AWAKENING) {
				return t;
			}
		}
		return null;
	}

	/**
	 * The tier: I open at once, II from race level 10, III from 20, IV (the Awakenings and their talents) from 30.
	 */
	public int tier() {
		if (automatic()) {
			return 1;
		}
		// The rows are the tiers: I, II, III (two rows), and IV for the Awakenings and theirs
		return row <= 1 ? 1 : row == 2 ? 2 : row <= 4 ? 3 : 4;
	}

	/** Points: 1, 2, 3 by tier; an Awakening 5, an Awakening's own talents 3. */
	public int cost() {
		if (automatic()) {
			return 0;
		}
		if (kind == Kind.AWAKENING) {
			return 5;
		}
		return Math.min(3, tier());
	}

	public int levelNeeded() {
		if (automatic()) {
			return autoLevel;
		}
		return TIER_LEVELS[tier() - 1];
	}

	public static Optional<Talent> byId(String id) {
		for (Talent t : values()) {
			if (t.id().equals(id)) {
				return Optional.of(t);
			}
		}
		return Optional.empty();
	}

	public static List<Talent> of(Race race) {
		List<Talent> list = new ArrayList<>();
		for (Talent t : values()) {
			if (t.race == race) {
				list.add(t);
			}
		}
		return list;
	}

	/** Talent points a race level gives, as in the first version: 1 a level, 2 more every tenth (60 by level 50). */
	public static int pointsAt(int level) {
		return level + 2 * (level / 10);
	}
}
