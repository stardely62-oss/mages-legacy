package euf.mageslegacy.race;

/** What became of a race ability; anything but OK is shown to the player as mages_legacy.ability.<result>. */
public enum AbilityResult {
	OK, COOLDOWN, NO_RESOURCE, NO_TARGET, LOCKED, NO_SPACE,
	/** Shepherds: every bond slot is taken. */
	NO_CAPACITY,
	/** Shepherds: no echo picked in the bestiary yet. */
	NO_ECHO,
	/** Shepherds: not looking at one of your own creatures. */
	NO_CREATURE,
	/** Shepherds: the Song of Bonds needs a beast that can be bonded in sight. */
	NO_BEAST,
	/** It didn't happen, and the player was already told why. */
	HINTED
}
