package euf.mageslegacy.race;

/**
 * The ability keys: R, V, Z for every race's three main abilities, then H and left Alt for the ones a race has beyond them,
 * most of which a talent opens (the Bloodkin's Feast and Swarm, say). Each has a key of its own: sneaking is never what
 * picks an ability, since the server can't always tell a crouch from a keypress.
 */
public final class AbilitySlots {
	/** Keys the client sends: 0..2 the main three, 3 is H, 4 is left Alt. */
	public static final int KEYS = 5;
	/** Cooldown slots the sync carries (abilities keep theirs at fixed numbers in RaceData.readyAt). */
	public static final int COOLDOWNS = 10;
	/** Marks a key with an ability open from the start, no talent needed. */
	public static final String OPEN = "";

	private AbilitySlots() {
	}

	/** The talent that opens the ability on key {@code slot}, {@link #OPEN}, or null when the race has nothing there. */
	public static String talent(Race race, int slot) {
		if (slot < 3) {
			return OPEN;
		}
		return switch (race) {
			case SHEPHERD -> OPEN;
			case VOIDBORN -> slot == 3 ? OPEN : Talents.ABYSS_GAZE;
			case BLOODKIN -> slot == 3 ? Talents.FANG_FEAST : Talents.NIGHT_BATS;
			case FROZEN -> slot == 3 ? Talents.FLOW_CURRENT : Talents.STASIS_FREEZE;
			default -> null;
		};
	}

	/** Whether this player has an ability on key {@code slot} right now. */
	public static boolean has(Race race, int slot, java.util.Set<String> talents) {
		String talent = talent(race, slot);
		return talent != null && (talent.isEmpty() || talents.contains(talent));
	}

	/** Whether a cooldown slot belongs to one of the race's keys (the client shows those; talent timers it doesn't). */
	public static boolean onKey(Race race, int cooldownSlot) {
		for (int slot = 0; slot < KEYS; slot++) {
			if (talent(race, slot) != null && cooldownSlot(race, slot) == cooldownSlot) {
				return true;
			}
		}
		return false;
	}

	/** Where the ability on key {@code slot} keeps its cooldown. */
	public static int cooldownSlot(Race race, int slot) {
		if (slot < 3 || race == Race.SHEPHERD && slot == 3) {
			return slot;
		}
		return switch (race) {
			case SHEPHERD -> 5;
			case BLOODKIN -> slot == 3 ? 5 : 4;
			case FROZEN -> slot == 3 ? 8 : 4;
			default -> slot;
		};
	}
}
