package euf.mageslegacy.mercenary;

import java.util.Locale;
import net.minecraft.nbt.CompoundTag;

/**
 * One job of the Mage's Shop, from a client of the guild: hunt so many of a creature, deliver so many of an item, reach a
 * land, scout a marked place, or bring down a named bounty. Its pay (race experience and tokens, elite already counted) is
 * fixed when it is offered; the talents, a hard condition and a job done in good time add to it when it is completed.
 */
public final class Contract {
	public enum Kind {
		HUNT, DELIVER, EXPLORE, SCOUT, BOUNTY;

		public String id() {
			return name().toLowerCase(Locale.ROOT);
		}

		/** The three kinds the trials ask for: hunts (bounties too), deliveries, journeys (lands and scouted places). */
		public String group() {
			return switch (this) {
				case HUNT, BOUNTY -> "hunt";
				case DELIVER -> "deliver";
				case EXPLORE, SCOUT -> "explore";
			};
		}

		public int groupIndex() {
			return switch (this) {
				case HUNT, BOUNTY -> 0;
				case DELIVER -> 1;
				case EXPLORE, SCOUT -> 2;
			};
		}
	}

	/** A hard condition on a hunt: only kills at night, only with projectiles, only by a Brand's burst. */
	public enum Rule {
		NONE, NIGHT, BOW, BRAND;

		public String id() {
			return name().toLowerCase(Locale.ROOT);
		}
	}

	/** A number unique within the account, so the client can name a contract without its place in a list. */
	public final int id;
	public final Kind kind;
	/** An entity type id (hunt, bounty), an item id (deliver), a biome id (explore), "" (scout). */
	public final String target;
	public final int amount;
	public int progress;
	public final int tier;
	public final boolean elite;
	public final int xp;
	public final int tokens;
	/** Who asked (mages_legacy.merc.client.&lt;id&gt;) and the job's own line (mages_legacy.merc.job.&lt;id&gt;). */
	public final String client;
	public final String job;
	public final Rule rule;
	/** Explore: the Mercenary has been outside the land since taking the job (only then does reaching it count). */
	public boolean armed;
	/** Scout and bounty: the place (in the overworld), and for a bounty whether it stands there yet and who it is. */
	public boolean located;
	public int locX;
	public int locZ;
	public boolean spawned;
	public String bounty = "";
	/** Done before this overworld time pays half again in tokens (0: not taken yet). */
	public long quickUntil;

	public Contract(int id, Kind kind, String target, int amount, int tier, boolean elite, int xp, int tokens, String client, String job, Rule rule) {
		this.id = id;
		this.kind = kind;
		this.target = target;
		this.amount = Math.max(1, amount);
		this.tier = tier;
		this.elite = elite;
		this.xp = xp;
		this.tokens = tokens;
		this.client = client;
		this.job = job;
		this.rule = rule;
	}

	/** A plain job (tests, old saves). */
	public Contract(int id, Kind kind, String target, int amount, int tier, boolean elite, int xp, int tokens) {
		this(id, kind, target, amount, tier, elite, xp, tokens, "guild", "", Rule.NONE);
	}

	public boolean done() {
		return progress >= amount;
	}

	public float share() {
		return Math.min(1, progress / (float) amount);
	}

	public CompoundTag save() {
		CompoundTag tag = new CompoundTag();
		tag.putInt("id", id);
		tag.putString("kind", kind.id());
		tag.putString("target", target);
		tag.putInt("amount", amount);
		tag.putInt("progress", progress);
		tag.putInt("tier", tier);
		tag.putBoolean("elite", elite);
		tag.putInt("xp", xp);
		tag.putInt("tokens", tokens);
		tag.putString("client", client);
		tag.putString("job", job);
		tag.putString("rule", rule.id());
		tag.putBoolean("armed", armed);
		if (located) {
			tag.putIntArray("loc", new int[] {locX, locZ});
		}
		tag.putBoolean("spawned", spawned);
		tag.putString("bounty", bounty);
		tag.putLong("quickUntil", quickUntil);
		return tag;
	}

	public static Contract load(CompoundTag tag) {
		Kind kind;
		Rule rule;
		try {
			kind = Kind.valueOf(tag.getString("kind").toUpperCase(Locale.ROOT));
		} catch (IllegalArgumentException e) {
			kind = Kind.HUNT;
		}
		try {
			rule = tag.contains("rule") ? Rule.valueOf(tag.getString("rule").toUpperCase(Locale.ROOT)) : Rule.NONE;
		} catch (IllegalArgumentException e) {
			rule = Rule.NONE;
		}
		Contract c = new Contract(tag.getInt("id"), kind, tag.getString("target"), tag.getInt("amount"), Math.max(1, tag.getInt("tier")),
				tag.getBoolean("elite"), tag.getInt("xp"), tag.getInt("tokens"), tag.contains("client") ? tag.getString("client") : "guild",
				tag.getString("job"), rule);
		c.progress = Math.min(c.amount, tag.getInt("progress"));
		c.armed = tag.getBoolean("armed");
		int[] loc = tag.getIntArray("loc");
		if (loc.length == 2) {
			c.located = true;
			c.locX = loc[0];
			c.locZ = loc[1];
		}
		c.spawned = tag.getBoolean("spawned");
		c.bounty = tag.getString("bounty");
		c.quickUntil = tag.getLong("quickUntil");
		return c;
	}
}
