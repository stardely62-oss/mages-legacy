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
	/** No creature in sight where one was needed (the Excision, the Stopping Gaze). */
	NO_SIGHT,
	/** No enemy near enough for an ability that takes them all. */
	NO_ENEMIES,
	/** It didn't happen, and the player was already told why. */
	HINTED
}
