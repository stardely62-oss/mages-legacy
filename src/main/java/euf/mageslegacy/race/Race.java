package euf.mageslegacy.race;

import java.util.Locale;
import java.util.Optional;

/** The peoples the nameless mage split humankind into. Only races marked playable can be picked yet. */
public enum Race {
	MYCELID(0x7FB069, true),
	SHEPHERD(0xD9B26F, true),
	FROZEN(0x8FB8DE, true),
	BLOODKIN(0xB23A48, true),
	VOIDBORN(0x9B7FD4, true),
	MERCENARY(0xC8823C, true),
	TEMPTER(0xD46A8E, true),
	/** A sleeping race: its legend is told on the choice screen, its powers are still to come. */
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
}
