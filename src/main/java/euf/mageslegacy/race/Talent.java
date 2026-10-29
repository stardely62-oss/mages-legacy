package euf.mageslegacy.race;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

/**
 * A node of a race's talent tree. The Voidborn tree is one radial web around the Pocket: Rift of Space up, Through
 * Worlds down-left, Gravity down-right, cross talents between them, an Awakening at the far end of each direction.
 * {@code ring} and {@code angle} place the node (rings out from the core, degrees clockwise from the right).
 * A node needs any one of {@code requires}; the core and the basic abilities open by themselves at their level.
 */
public enum Talent {
	// The core and the basic abilities, free and automatic
	POCKET(Race.VOIDBORN, Kind.CORE, Branch.CORE, 0, 0, 0, 1, "ability_pocket"),
	VOID_STEP(Race.VOIDBORN, Kind.BASIC, Branch.WORLDS, 1, 150, 0, 3, "ability_void_step", "pocket"),
	GRAVITY(Race.VOIDBORN, Kind.BASIC, Branch.GRAVITY, 1, 30, 0, 5, "ability_gravity", "pocket"),

	// Rift of Space: the Pocket grows, its door gets cheaper and wider
	POCKET_WIDE_1(Race.VOIDBORN, Kind.PASSIVE, Branch.RIFT, 1, -90, 1, 0, "pocket_wide", "pocket"),
	POCKET_WIDE_2(Race.VOIDBORN, Kind.PASSIVE, Branch.RIFT, 2, -90, 2, 0, "pocket_wide", "pocket_wide_1"),
	POCKET_WIDE_3(Race.VOIDBORN, Kind.PASSIVE, Branch.RIFT, 3, -90, 3, 0, "pocket_wide", "pocket_wide_2"),
	POCKET_CHEAP(Race.VOIDBORN, Kind.PASSIVE, Branch.RIFT, 2, -70, 1, 0, "pocket_cheap", "pocket_wide_1"),
	POCKET_GUESTS(Race.VOIDBORN, Kind.TRANSFORM, Branch.RIFT, 2, -110, 1, 0, "pocket_guests", "pocket_wide_1"),
	POCKET_VAULT(Race.VOIDBORN, Kind.PASSIVE, Branch.RIFT, 3, -110, 2, 0, "pocket_home", "pocket_guests"),
	POCKET_CRADLE(Race.VOIDBORN, Kind.PASSIVE, Branch.RIFT, 3, -70, 1, 0, "pocket_rest", "pocket_cheap"),
	AWAKENING_RIFT(Race.VOIDBORN, Kind.AWAKENING, Branch.RIFT, 4.2f, -90, 5, 0, "pocket_storm", "pocket_wide_3", "pocket_vault"),
	POCKET_SANCTUARY(Race.VOIDBORN, Kind.PASSIVE, Branch.RIFT, 2, -130, 1, 0, "pocket_ward", "pocket_wide_1"),
	POCKET_HEARTH(Race.VOIDBORN, Kind.PASSIVE, Branch.RIFT, 2, -50, 1, 0, "pocket_hearth", "pocket_wide_1"),
	POCKET_GARDEN(Race.VOIDBORN, Kind.PASSIVE, Branch.RIFT, 3, -50, 2, 0, "pocket_garden", "pocket_hearth"),
	POCKET_TWO_DOORS(Race.VOIDBORN, Kind.TRANSFORM, Branch.RIFT, 3, -130, 3, 0, "pocket_quick", "pocket_sanctuary"),
	POCKET_SHELTER(Race.VOIDBORN, Kind.TRANSFORM, Branch.RIFT, 3.8f, -122, 3, 0, "pocket_home", "pocket_two_doors", "pocket_vault"),
	VOID_EYES(Race.VOIDBORN, Kind.PASSIVE, Branch.RIFT, 3.8f, -58, 2, 0, "abyss_gaze", "pocket_garden", "pocket_cradle"),
	ARENA_REGEN(Race.VOIDBORN, Kind.PASSIVE, Branch.RIFT, 5.3f, -114, 2, 0, "pocket_garden", "awakening_rift"),
	ARENA_MIGHT(Race.VOIDBORN, Kind.PASSIVE, Branch.RIFT, 5.3f, -98, 2, 0, "pocket_hearth", "awakening_rift"),
	ARENA_WARD(Race.VOIDBORN, Kind.PASSIVE, Branch.RIFT, 5.3f, -82, 2, 0, "pocket_ward", "awakening_rift"),
	ARENA_LONG(Race.VOIDBORN, Kind.TRANSFORM, Branch.RIFT, 5.3f, -66, 2, 0, "pocket_quick", "awakening_rift"),

	// Through Worlds: farther, faster, together, and marks in space
	STEP_FAR(Race.VOIDBORN, Kind.PASSIVE, Branch.WORLDS, 2, 145, 1, 0, "step_far", "void_step"),
	STEP_FAR_2(Race.VOIDBORN, Kind.PASSIVE, Branch.WORLDS, 3, 150, 2, 0, "step_sever", "step_far"),
	STEP_QUICK(Race.VOIDBORN, Kind.TRANSFORM, Branch.WORLDS, 2.5f, 168, 2, 0, "step_quick", "void_step"),
	STEP_TRACE(Race.VOIDBORN, Kind.PASSIVE, Branch.WORLDS, 2.5f, 125, 1, 0, "step_veil", "void_step"),
	STEP_ALLY(Race.VOIDBORN, Kind.TRANSFORM, Branch.WORLDS, 3, 133, 2, 0, "step_ally", "step_far", "step_trace"),
	STEP_SOFT(Race.VOIDBORN, Kind.PASSIVE, Branch.WORLDS, 2, 108, 1, 0, "step_veil", "void_step"),
	STEP_SENSE(Race.VOIDBORN, Kind.PASSIVE, Branch.WORLDS, 2, 198, 1, 0, "step_sever", "void_step"),
	STEP_ECHO(Race.VOIDBORN, Kind.TRANSFORM, Branch.WORLDS, 3, 187, 2, 0, "step_rift", "step_sense"),
	STEP_AMBUSH(Race.VOIDBORN, Kind.PASSIVE, Branch.WORLDS, 3.2f, 108, 2, 0, "step_cut", "step_soft"),
	STEP_STONE(Race.VOIDBORN, Kind.PASSIVE, Branch.WORLDS, 3.8f, 178, 3, 0, "step_far", "step_echo", "step_quick"),
	AWAKENING_WORLDS(Race.VOIDBORN, Kind.AWAKENING, Branch.WORLDS, 4.2f, 150, 5, 0, "abyss_gaze", "step_far_2", "step_stone"),
	WORLDS_LONG(Race.VOIDBORN, Kind.PASSIVE, Branch.WORLDS, 5.3f, 134, 2, 0, "excise_long", "awakening_worlds"),
	WORLDS_CHARGE_2(Race.VOIDBORN, Kind.PASSIVE, Branch.WORLDS, 5.3f, 150, 2, 0, "step_cut", "awakening_worlds"),
	WORLDS_CHARGE_3(Race.VOIDBORN, Kind.PASSIVE, Branch.WORLDS, 6.2f, 150, 3, 0, "step_cut", "worlds_charge_2"),
	WORLDS_TRAIN(Race.VOIDBORN, Kind.TRANSFORM, Branch.WORLDS, 5.3f, 166, 2, 0, "excise_drop", "awakening_worlds"),
	WORLDS_CLEANSE(Race.VOIDBORN, Kind.PASSIVE, Branch.WORLDS, 5.3f, 118, 2, 0, "step_mend", "awakening_worlds"),

	// Gravity: wider, heavier, pulling, bending what flies
	GRAV_WIDE(Race.VOIDBORN, Kind.PASSIVE, Branch.GRAVITY, 2, 15, 1, 0, "well_wide", "gravity"),
	GRAV_SLOW(Race.VOIDBORN, Kind.PASSIVE, Branch.GRAVITY, 2, 45, 1, 0, "well_hold", "gravity"),
	GRAV_PULL(Race.VOIDBORN, Kind.TRANSFORM, Branch.GRAVITY, 3, 15, 2, 0, "well_hunger", "grav_wide"),
	GRAV_FLIERS(Race.VOIDBORN, Kind.PASSIVE, Branch.GRAVITY, 3.3f, 34, 1, 0, "well_long", "grav_wide", "grav_slow"),
	GRAV_ARROWS(Race.VOIDBORN, Kind.TRANSFORM, Branch.GRAVITY, 3, 45, 2, 0, "well_eat", "grav_slow"),
	GRAV_ANCHOR(Race.VOIDBORN, Kind.PASSIVE, Branch.GRAVITY, 2, -15, 1, 0, "well_heart", "gravity"),
	GRAV_DRAIN(Race.VOIDBORN, Kind.PASSIVE, Branch.GRAVITY, 2, 65, 1, 0, "well_eat", "gravity"),
	GRAV_FUSE(Race.VOIDBORN, Kind.PASSIVE, Branch.GRAVITY, 3, -10, 2, 0, "well_hold", "grav_anchor"),
	GRAV_CHAIN(Race.VOIDBORN, Kind.TRANSFORM, Branch.GRAVITY, 3, 62, 2, 0, "well_long", "grav_drain"),
	GRAV_LINGER(Race.VOIDBORN, Kind.TRANSFORM, Branch.GRAVITY, 3.8f, 2, 3, 0, "well_wide", "grav_fuse", "grav_pull"),
	GRAV_FEAST(Race.VOIDBORN, Kind.PASSIVE, Branch.GRAVITY, 3.8f, 58, 3, 0, "well_hunger", "grav_chain", "grav_arrows"),
	AWAKENING_HOLE(Race.VOIDBORN, Kind.AWAKENING, Branch.GRAVITY, 4.2f, 30, 5, 0, "well_collapse", "grav_pull", "grav_arrows"),
	HOLE_WIDE(Race.VOIDBORN, Kind.PASSIVE, Branch.GRAVITY, 5.3f, 14, 2, 0, "well_heart", "awakening_hole"),
	HOLE_DEEP(Race.VOIDBORN, Kind.PASSIVE, Branch.GRAVITY, 5.3f, 30, 2, 0, "step_rend", "awakening_hole"),
	HOLE_QUICK(Race.VOIDBORN, Kind.TRANSFORM, Branch.GRAVITY, 5.3f, 46, 2, 0, "excise_cheap", "awakening_hole"),
	HOLE_SATED(Race.VOIDBORN, Kind.PASSIVE, Branch.GRAVITY, 5.3f, -2, 2, 0, "well_collapse", "awakening_hole"),

	// Cross talents between the directions
	STEP_DOOR(Race.VOIDBORN, Kind.CROSS, Branch.CROSS, 2.6f, -150, 2, 0, "step_pull", "pocket_guests", "step_quick"),
	STEP_HEAVY(Race.VOIDBORN, Kind.CROSS, Branch.CROSS, 2.6f, 90, 2, 0, "step_mend", "step_trace", "grav_slow"),
	DOOR_RECOIL(Race.VOIDBORN, Kind.CROSS, Branch.CROSS, 2.6f, -30, 2, 0, "pocket_water", "grav_wide", "pocket_cheap"),
	STEP_HOMEWARD(Race.VOIDBORN, Kind.CROSS, Branch.CROSS, 3.4f, -155, 3, 0, "step_door", "pocket_two_doors", "step_echo"),
	STEP_RUPTURE(Race.VOIDBORN, Kind.CROSS, Branch.CROSS, 3.7f, 90, 3, 0, "step_rend", "step_ambush", "grav_chain"),
	POCKET_HOLD(Race.VOIDBORN, Kind.CROSS, Branch.CROSS, 3.4f, -30, 3, 0, "pocket_rest", "grav_fuse", "pocket_garden"),
	// ---- The Mycelid: the Mycelium Network at the heart, Colony up, Neurospores down-left, Symbiosis down-right ----
	MYCELIUM_NETWORK(Race.MYCELID, Kind.CORE, Branch.CORE, 0, 0, 0, 1, "ability_mycelium_network"),
	NEUROSPORES(Race.MYCELID, Kind.BASIC, Branch.SPORES, 1, 150, 0, 3, "ability_neurospores", "mycelium_network"),
	NEUROSYMBIOSIS(Race.MYCELID, Kind.BASIC, Branch.SYMBIOSIS, 1, 30, 0, 5, "ability_neurosymbiosis", "mycelium_network"),
	NET_NODES_1(Race.MYCELID, Kind.PASSIVE, Branch.COLONY, 1, -90, 1, 0, "net_nodes", "mycelium_network"),
	NET_NODES_2(Race.MYCELID, Kind.PASSIVE, Branch.COLONY, 2, -90, 1, 0, "net_nodes", "net_nodes_1"),
	NET_NODES_3(Race.MYCELID, Kind.PASSIVE, Branch.COLONY, 3, -90, 3, 0, "net_roots", "net_nodes_2"),
	NET_THRIFT(Race.MYCELID, Kind.PASSIVE, Branch.COLONY, 2, -70, 1, 0, "net_cheap", "net_nodes_1"),
	NET_REACH(Race.MYCELID, Kind.TRANSFORM, Branch.COLONY, 2, -110, 1, 0, "net_reach", "net_nodes_1"),
	NET_TRAIN(Race.MYCELID, Kind.PASSIVE, Branch.COLONY, 3, -110, 2, 0, "net_shared", "net_reach"),
	NET_GROWTH(Race.MYCELID, Kind.PASSIVE, Branch.COLONY, 3, -70, 1, 0, "net_haste", "net_thrift"),
	NET_SWIFT(Race.MYCELID, Kind.PASSIVE, Branch.COLONY, 2, -130, 1, 0, "net_light_step", "net_nodes_1"),
	NET_TOLL(Race.MYCELID, Kind.PASSIVE, Branch.COLONY, 2, -50, 1, 0, "net_far", "net_nodes_1"),
	NET_SENSE(Race.MYCELID, Kind.PASSIVE, Branch.COLONY, 3, -50, 2, 0, "net_mind", "net_toll"),
	NET_RECALL(Race.MYCELID, Kind.TRANSFORM, Branch.COLONY, 3, -130, 3, 0, "net_chain", "net_swift"),
	NET_SHARED(Race.MYCELID, Kind.TRANSFORM, Branch.COLONY, 3.8f, -122, 3, 0, "net_shared", "net_recall", "net_train"),
	NET_GLOW(Race.MYCELID, Kind.PASSIVE, Branch.COLONY, 3.8f, -58, 2, 0, "net_cleanse", "net_sense", "net_growth"),
	AWAKENING_BLOOM(Race.MYCELID, Kind.AWAKENING, Branch.COLONY, 4.2f, -90, 5, 0, "swarm_bloom", "net_nodes_3", "net_train"),
	BLOOM_HEAL(Race.MYCELID, Kind.PASSIVE, Branch.COLONY, 5.3f, -114, 2, 0, "swarm_mending", "awakening_bloom"),
	BLOOM_BARK(Race.MYCELID, Kind.PASSIVE, Branch.COLONY, 5.3f, -98, 2, 0, "flesh_cap", "awakening_bloom"),
	BLOOM_ROOTS(Race.MYCELID, Kind.PASSIVE, Branch.COLONY, 5.3f, -82, 2, 0, "net_roots", "awakening_bloom"),
	BLOOM_LONG(Race.MYCELID, Kind.TRANSFORM, Branch.COLONY, 5.3f, -66, 2, 0, "swarm_lingering", "awakening_bloom"),
	SPORE_WIDE(Race.MYCELID, Kind.PASSIVE, Branch.SPORES, 2, 145, 1, 0, "swarm_wide", "neurospores"),
	SPORE_WIDE_2(Race.MYCELID, Kind.PASSIVE, Branch.SPORES, 3, 150, 2, 0, "swarm_wide", "spore_wide"),
	SPORE_QUICK(Race.MYCELID, Kind.TRANSFORM, Branch.SPORES, 2.5f, 168, 2, 0, "swarm_quick", "neurospores"),
	SPORE_LONG(Race.MYCELID, Kind.PASSIVE, Branch.SPORES, 2.5f, 125, 1, 0, "swarm_lingering", "neurospores"),
	SPORE_SPREAD(Race.MYCELID, Kind.TRANSFORM, Branch.SPORES, 3, 133, 2, 0, "swarm_infection", "spore_wide", "spore_long"),
	SPORE_CONFUSE(Race.MYCELID, Kind.PASSIVE, Branch.SPORES, 2, 108, 1, 0, "ability_spore_cloud", "neurospores"),
	SPORE_MARK(Race.MYCELID, Kind.PASSIVE, Branch.SPORES, 2, 198, 1, 0, "night_sense", "neurospores"),
	SPORE_TRAIL(Race.MYCELID, Kind.TRANSFORM, Branch.SPORES, 3, 187, 2, 0, "swarm_trail", "spore_mark"),
	SPORE_FEEBLE(Race.MYCELID, Kind.PASSIVE, Branch.SPORES, 3.2f, 108, 2, 0, "swarm_weakness", "spore_confuse"),
	SPORE_LINGER(Race.MYCELID, Kind.PASSIVE, Branch.SPORES, 3.8f, 178, 3, 0, "swarm_potent", "spore_trail", "spore_quick"),
	AWAKENING_STORM(Race.MYCELID, Kind.AWAKENING, Branch.SPORES, 4.2f, 150, 5, 0, "swarm_burst", "spore_wide_2", "spore_linger"),
	STORM_LONG(Race.MYCELID, Kind.PASSIVE, Branch.SPORES, 5.3f, 134, 2, 0, "swarm_lingering", "awakening_storm"),
	STORM_WIDE(Race.MYCELID, Kind.PASSIVE, Branch.SPORES, 5.3f, 150, 2, 0, "swarm_wide", "awakening_storm"),
	STORM_KEEP(Race.MYCELID, Kind.PASSIVE, Branch.SPORES, 6.2f, 150, 3, 0, "swarm_potent", "storm_wide"),
	STORM_ALLY(Race.MYCELID, Kind.TRANSFORM, Branch.SPORES, 5.3f, 166, 2, 0, "swarm_follow", "awakening_storm"),
	STORM_BLIND(Race.MYCELID, Kind.PASSIVE, Branch.SPORES, 5.3f, 118, 2, 0, "night_shade", "awakening_storm"),
	SYM_PULSE(Race.MYCELID, Kind.PASSIVE, Branch.SYMBIOSIS, 2, 15, 1, 0, "pack_fast", "neurosymbiosis"),
	SYM_REFLEX(Race.MYCELID, Kind.PASSIVE, Branch.SYMBIOSIS, 2, 45, 1, 0, "pack_thick", "neurosymbiosis"),
	SYM_UNITY(Race.MYCELID, Kind.TRANSFORM, Branch.SYMBIOSIS, 3, 15, 2, 0, "pack_alpha", "sym_pulse"),
	SYM_ADAPT(Race.MYCELID, Kind.PASSIVE, Branch.SYMBIOSIS, 3.3f, 34, 1, 0, "flesh_sunproof", "sym_pulse", "sym_reflex"),
	SYM_SHARE(Race.MYCELID, Kind.TRANSFORM, Branch.SYMBIOSIS, 3, 45, 2, 0, "kin_heal", "sym_reflex"),
	DIET_1(Race.MYCELID, Kind.PASSIVE, Branch.SYMBIOSIS, 2, -15, 1, 0, "flesh_meat", "mycelium_network"),
	SYM_ENV(Race.MYCELID, Kind.PASSIVE, Branch.SYMBIOSIS, 2, 65, 1, 0, "flesh_fireproof", "neurosymbiosis"),
	DIET_2(Race.MYCELID, Kind.PASSIVE, Branch.SYMBIOSIS, 3, -10, 1, 0, "flesh_two_tastes", "diet_1"),
	SYM_NIGHT(Race.MYCELID, Kind.TRANSFORM, Branch.SYMBIOSIS, 3, 62, 2, 0, "flesh_dark_regen", "sym_env"),
	DIET_3(Race.MYCELID, Kind.TRANSFORM, Branch.SYMBIOSIS, 3.8f, 2, 2, 0, "flesh_rich", "diet_2", "sym_unity"),
	SYM_AURA(Race.MYCELID, Kind.PASSIVE, Branch.SYMBIOSIS, 3.8f, 58, 2, 0, "kin_all", "sym_night", "sym_share"),
	AWAKENING_REBIRTH(Race.MYCELID, Kind.AWAKENING, Branch.SYMBIOSIS, 4.2f, 30, 5, 0, "flesh_rebirth", "sym_unity", "sym_share"),
	REBIRTH_FAST(Race.MYCELID, Kind.PASSIVE, Branch.SYMBIOSIS, 5.3f, 14, 2, 0, "loop_quick", "awakening_rebirth"),
	REBIRTH_HEALTH(Race.MYCELID, Kind.PASSIVE, Branch.SYMBIOSIS, 5.3f, 30, 2, 0, "flesh_hearty", "awakening_rebirth"),
	REBIRTH_CHEAP(Race.MYCELID, Kind.TRANSFORM, Branch.SYMBIOSIS, 5.3f, 46, 2, 0, "loop_cheap", "awakening_rebirth"),
	REBIRTH_SOFT(Race.MYCELID, Kind.PASSIVE, Branch.SYMBIOSIS, 5.3f, -2, 2, 0, "sleep_dream", "awakening_rebirth"),
	SPORE_NODES(Race.MYCELID, Kind.CROSS, Branch.CROSS, 2.6f, -150, 2, 0, "flesh_spore_blood", "net_reach", "spore_quick"),
	SPORE_FEED(Race.MYCELID, Kind.CROSS, Branch.CROSS, 2.6f, 90, 2, 0, "thirst_drain", "spore_long", "sym_reflex"),
	HEAL_NODES(Race.MYCELID, Kind.CROSS, Branch.CROSS, 2.6f, -30, 2, 0, "kin_heal", "sym_pulse", "net_thrift"),
	SPORE_TRAVEL(Race.MYCELID, Kind.CROSS, Branch.CROSS, 3.4f, -155, 3, 0, "swarm_trail", "net_recall", "spore_trail"),
	SYM_SPORES(Race.MYCELID, Kind.CROSS, Branch.CROSS, 3.7f, 90, 3, 0, "swarm_potent", "spore_feeble", "sym_night"),
	NODE_FOOD(Race.MYCELID, Kind.CROSS, Branch.CROSS, 3.4f, -30, 3, 0, "pack_fed", "diet_2", "net_sense");

	public enum Kind {
		CORE, BASIC, PASSIVE, TRANSFORM, CROSS, AWAKENING;

		public String key() {
			return "mages_legacy.talent.kind." + name().toLowerCase(Locale.ROOT);
		}
	}

	public enum Branch {
		CORE, RIFT, WORLDS, GRAVITY, COLONY, SPORES, SYMBIOSIS, CROSS
	}

	public static final int MAX_TIER = 4;
	/** Race level each tier opens at. */
	public static final int[] TIER_LEVELS = {1, 10, 20, 30};
	/** The first Awakening opens from this race level on. */
	public static final int AWAKENING_LEVEL = 30;

	public final Race race;
	public final Kind kind;
	public final Branch branch;
	public final float ring;
	public final float angle;
	/** The cost written in the table; the real one comes from the tier, see {@link #cost()}. */
	private final int baseCost;
	/** For the core and basic abilities: the race level they open at by themselves. */
	public final int autoLevel;
	public final String icon;
	private final String[] requires;

	Talent(Race race, Kind kind, Branch branch, float ring, float angle, int cost, int autoLevel, String icon, String... requires) {
		this.race = race;
		this.kind = kind;
		this.branch = branch;
		this.ring = ring;
		this.angle = angle;
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
		if (kind == Kind.AWAKENING || awakening() != null || this == WORLDS_CHARGE_3 || this == STORM_KEEP) {
			return 4;
		}
		return switch (this) {
			case POCKET_WIDE_3, POCKET_VAULT, STEP_FAR_2, GRAV_PULL, GRAV_ARROWS, POCKET_TWO_DOORS, POCKET_SHELTER, STEP_STONE,
					GRAV_LINGER, GRAV_FEAST, STEP_HOMEWARD, STEP_RUPTURE, POCKET_HOLD,
					NET_NODES_3, NET_TRAIN, SPORE_WIDE_2, SYM_UNITY, SYM_SHARE, NET_RECALL, NET_SHARED, SPORE_LINGER, DIET_3, SYM_AURA, SPORE_TRAVEL, SYM_SPORES, NODE_FOOD -> 3;
			case POCKET_CRADLE, STEP_ALLY, GRAV_FLIERS, STEP_DOOR, STEP_HEAVY, DOOR_RECOIL, POCKET_GARDEN, VOID_EYES, STEP_ECHO, STEP_AMBUSH,
					GRAV_FUSE, GRAV_CHAIN,
					NET_GROWTH, SPORE_SPREAD, SYM_ADAPT, SPORE_NODES, SPORE_FEED, HEAL_NODES, NET_SENSE, NET_GLOW, SPORE_TRAIL, SPORE_FEEBLE, DIET_2, SYM_NIGHT -> 2;
			default -> 1;
		};
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
