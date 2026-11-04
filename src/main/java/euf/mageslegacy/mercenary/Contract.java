package euf.mageslegacy.mercenary;

import java.util.Locale;
import net.minecraft.nbt.CompoundTag;

/**
 * One job of the Mage's Shop: hunt so many of a creature, deliver so many of an item, or reach a land. Its pay (race
 * experience and tokens, elite already counted) is fixed when it is offered; the talents that raise pay are counted
 * when it is completed.
 */
public final class Contract {
	public enum Kind {
		HUNT, DELIVER, EXPLORE;

		public String id() {
			return name().toLowerCase(Locale.ROOT);
		}
	}

	/** A number unique within the account, so the client can name a contract without its place in a list. */
	public final int id;
	public final Kind kind;
	/** An entity type id (hunt), an item id (deliver) or a biome id (explore). */
	public final String target;
	public final int amount;
	public int progress;
	public final int tier;
	public final boolean elite;
	public final int xp;
	public final int tokens;
	/** Explore: the Mercenary has been outside the land since taking the job (only then does reaching it count). */
	public boolean armed;
	/** Explore with the Guide: where the nearest such land was found when the job was taken (no y), if it was. */
	public boolean located;
	public int locX;
	public int locZ;

	public Contract(int id, Kind kind, String target, int amount, int tier, boolean elite, int xp, int tokens) {
		this.id = id;
		this.kind = kind;
		this.target = target;
		this.amount = Math.max(1, amount);
		this.tier = tier;
		this.elite = elite;
		this.xp = xp;
		this.tokens = tokens;
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
		tag.putBoolean("armed", armed);
		if (located) {
			tag.putIntArray("loc", new int[] {locX, locZ});
		}
		return tag;
	}

	public static Contract load(CompoundTag tag) {
		Kind kind;
		try {
			kind = Kind.valueOf(tag.getString("kind").toUpperCase(Locale.ROOT));
		} catch (IllegalArgumentException e) {
			kind = Kind.HUNT;
		}
		Contract c = new Contract(tag.getInt("id"), kind, tag.getString("target"), tag.getInt("amount"), Math.max(1, tag.getInt("tier")),
				tag.getBoolean("elite"), tag.getInt("xp"), tag.getInt("tokens"));
		c.progress = Math.min(c.amount, tag.getInt("progress"));
		c.armed = tag.getBoolean("armed");
		int[] loc = tag.getIntArray("loc");
		if (loc.length == 2) {
			c.located = true;
			c.locX = loc[0];
			c.locZ = loc[1];
		}
		return c;
	}
}
