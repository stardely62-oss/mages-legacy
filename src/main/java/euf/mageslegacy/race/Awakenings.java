package euf.mageslegacy.race;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * The two-of-three rule: a race has three Awakenings; the first learnt sits on Z, the second (superlate) on left
 * Alt, the third stays closed. Each keeps its own cost and cooldown and none blocks the other.
 */
public final class Awakenings {
	public static final int FIRST_SLOT = 2;
	public static final int SECOND_SLOT = 4;
	/** The second Awakening needs this race level and the superlate trial. */
	public static final int SUPERLATE_LEVEL = 45;

	private Awakenings() {
	}

	public static Ability abilityOf(Talent awakening) {
		return switch (awakening) {
			case AWAKENING_RIFT -> Ability.RIFT_ARENA;
			case AWAKENING_WORLDS -> Ability.PASSAGE;
			case AWAKENING_HOLE -> Ability.BLACK_HOLE;
			case AWAKENING_BLOOM -> Ability.GREAT_BLOOM;
			case AWAKENING_STORM -> Ability.SPORE_STORM;
			case AWAKENING_REBIRTH -> Ability.MYCELIAL_REBIRTH;
			default -> null;
		};
	}

	public static Talent talentOf(Ability ability) {
		return switch (ability) {
			case RIFT_ARENA -> Talent.AWAKENING_RIFT;
			case PASSAGE -> Talent.AWAKENING_WORLDS;
			case BLACK_HOLE -> Talent.AWAKENING_HOLE;
			case GREAT_BLOOM -> Talent.AWAKENING_BLOOM;
			case SPORE_STORM -> Talent.AWAKENING_STORM;
			case MYCELIAL_REBIRTH -> Talent.AWAKENING_REBIRTH;
			default -> null;
		};
	}

	/** The Awakenings in the order they were learnt (talent ids keep that order). */
	public static List<Talent> owned(List<String> talents) {
		List<Talent> list = new ArrayList<>();
		for (String id : talents) {
			Talent.byId(id).filter(t -> t.kind == Talent.Kind.AWAKENING).ifPresent(list::add);
		}
		return list;
	}

	/** Which Awakening sits on a key slot (Z or Alt) for these talents. */
	public static Optional<Ability> at(List<String> talents, int slot) {
		List<Talent> owned = owned(talents);
		int index = slot == FIRST_SLOT ? 0 : slot == SECOND_SLOT ? 1 : -1;
		if (index < 0 || index >= owned.size()) {
			return Optional.empty();
		}
		return Optional.ofNullable(abilityOf(owned.get(index)));
	}

	/** The key slot an Awakening sits on, or -1. */
	public static int slotOf(List<String> talents, Ability ability) {
		List<Talent> owned = owned(talents);
		Talent talent = talentOf(ability);
		int index = owned.indexOf(talent);
		return index == 0 ? FIRST_SLOT : index == 1 ? SECOND_SLOT : -1;
	}
}
