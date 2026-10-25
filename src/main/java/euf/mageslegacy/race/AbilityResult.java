package euf.mageslegacy.race;

/** What became of a race ability; anything but OK is shown to the player as mages_legacy.ability.<result>. */
public enum AbilityResult {
	OK, COOLDOWN, NO_RESOURCE, NO_TARGET, LOCKED, NO_SPACE,
	/** Shepherds: every bond slot is taken. */
	NO_CAPACITY,
	/** Shepherds: no echo picked in the bestiary yet. */
	NO_ECHO,
	/** Shepherds: not looking at one of your own creatures. */
	NO_CREATURE
}
