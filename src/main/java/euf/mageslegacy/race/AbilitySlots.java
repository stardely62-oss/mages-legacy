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

	/**
	 * The race level at which the ability on key {@code slot} opens: none is there from the start. The first comes
	 * almost at once, the others as the race grows; abilities a talent opens need only that talent.
	 */
	public static int unlockLevel(Race race, int slot) {
		// The Shepherds start from the Song (beasts first), then the Call, the Whistle and the horn
		if (race == Race.SHEPHERD) {
			return switch (slot) {
				case 0 -> 3;
				case 1 -> 10;
				case 2 -> 5;
				case 3 -> 1;
				default -> 0;
			};
		}
		// The Voidborn start from their pocket: home first, the rest of the void after
		if (race == Race.VOIDBORN) {
			return switch (slot) {
				case 0 -> 3;
				case 1 -> 6;
				case 2 -> 10;
				case 3 -> 1;
				default -> 0;
			};
		}
		return switch (slot) {
			case 0 -> 1;
			case 1 -> 5;
			case 2 -> 10;
			case 3 -> 0;
			default -> 0;
		};
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
