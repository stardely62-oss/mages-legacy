package euf.mageslegacy.race;

import java.util.Locale;
import java.util.Optional;

/** The peoples the nameless mage split humankind into. Only races marked playable can be picked; none is yet. */
public enum Race {
	MYCELID(0x7FB069, false),
	SHEPHERD(0xD9B26F, false),
	FROZEN(0x8FB8DE, false),
	BLOODKIN(0xB23A48, false),
	VOIDBORN(0x9B7FD4, false),
	MERCENARY(0xC8823C, false),
	TEMPTER(0xD46A8E, false),
	MASKED(0xBFB6A3, false);

	public final int color;
	public final boolean playable;

	Race(int color, boolean playable) {
		this.color = color;
		this.playable = playable;
	}

	public String id() {
		return name().toLowerCase(Locale.ROOT);
	}

	/** Translation key prefix: mages_legacy.race.<id>. */
	public String key() {
		return "mages_legacy.race." + id();
	}

	public static Optional<Race> byId(String id) {
		for (Race race : values()) {
			if (race.id().equals(id)) {
				return Optional.of(race);
			}
		}
		return Optional.empty();
	}

	/** Whether any race can be picked yet; while none can, the choice can be put off. */
	public static boolean anyPlayable() {
		for (Race race : values()) {
			if (race.playable) {
				return true;
			}
		}
		return false;
	}
}
