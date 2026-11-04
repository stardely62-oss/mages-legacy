package euf.mageslegacy.mercenary;

import euf.mageslegacy.race.RaceState;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerPlayer;

/**
 * A Mercenary's account in the Mage's Shop, read from the player ({@link RaceState#MERC}) and written back whole by
 * {@link #save}: tokens exist nowhere else. Everything here is changed on the server thread only, inside one call, so
 * a payment and what it buys are never half done.
 */
public final class Account {
	public int tokens;
	public final List<Contract> offers = new ArrayList<>();
	public final List<Contract> active = new ArrayList<>();
	/** The board's day ({@link Contracts#DAY} ticks of the overworld's own clock); a new one rotates board and stock. */
	public long day = -1;
	public int rerolls;
	/** Ware id → how many bought today. */
	public final Map<String, Integer> bought = new HashMap<>();
	/** Today's goods, and the deals of the day among them (one; two for a Regular). */
	public final List<String> stock = new ArrayList<>();
	public final List<String> deals = new ArrayList<>();
	/** Targets of the last jobs done (newest last): none of them comes back on the board soon. */
	public final List<String> recent = new ArrayList<>();
	public int nextId = 1;
	/** Contracts completed, in all and of each kind (hunt, deliver, explore). */
	public int completed;
	public final int[] completedKinds = new int[3];
	/** Tokens spent in the Shop (purchases and fresh boards), for the Ransom's trial. */
	public int spent;
	/** The Streak: when the last contract was done, and how many in a row. */
	public long lastDoneAt = -1;
	public int streak;
	/** The highest tier whose wares the Mercenary has seen (newer ones are marked new in the Shop). */
	public int seenTier;
	/** The Last Ransom: the game time it can save again. */
	public long ransomReady;
	/** A Life Debt left to pay that couldn't be paid when its contract ended (the debtor had left): paid at the next login. */
	public float debtOwed;
	/** Bought luck: so many next jobs pay 30% more tokens (Fortune), or twice the tokens and experience (a Lucky Day). */
	public int fortuneJobs;
	public int luckyJobs;

	public static Account of(ServerPlayer player) {
		Account a = new Account();
		CompoundTag tag = player.getData(RaceState.MERC);
		a.tokens = Math.max(0, tag.getInt("tokens"));
		for (Tag t : tag.getList("offers", Tag.TAG_COMPOUND)) {
			a.offers.add(Contract.load((CompoundTag) t));
		}
		for (Tag t : tag.getList("active", Tag.TAG_COMPOUND)) {
			a.active.add(Contract.load((CompoundTag) t));
		}
		a.day = tag.contains("day") ? tag.getLong("day") : -1;
		a.rerolls = tag.getInt("rerolls");
		CompoundTag bought = tag.getCompound("bought");
		for (String key : bought.getAllKeys()) {
			a.bought.put(key, bought.getInt(key));
		}
		strings(tag, "stock", a.stock);
		strings(tag, "deals", a.deals);
		strings(tag, "recent", a.recent);
		a.nextId = Math.max(1, tag.getInt("nextId"));
		a.completed = tag.getInt("completed");
		int[] kinds = tag.getIntArray("kinds");
		for (int i = 0; i < Math.min(3, kinds.length); i++) {
			a.completedKinds[i] = kinds[i];
		}
		a.spent = tag.getInt("spent");
		a.lastDoneAt = tag.contains("lastDone") ? tag.getLong("lastDone") : -1;
		a.streak = tag.getInt("streak");
		a.seenTier = tag.getInt("seenTier");
		a.ransomReady = tag.getLong("ransomReady");
		a.debtOwed = tag.getFloat("debtOwed");
		a.fortuneJobs = tag.getInt("fortuneJobs");
		a.luckyJobs = tag.getInt("luckyJobs");
		return a;
	}

	private static void strings(CompoundTag tag, String key, List<String> into) {
		for (Tag t : tag.getList(key, Tag.TAG_STRING)) {
			into.add(t.getAsString());
		}
	}

	private static ListTag strings(List<String> list) {
		ListTag tag = new ListTag();
		for (String s : list) {
			tag.add(StringTag.valueOf(s));
		}
		return tag;
	}

	public void save(ServerPlayer player) {
		player.setData(RaceState.MERC, write());
	}

	public CompoundTag write() {
		CompoundTag tag = new CompoundTag();
		tag.putInt("tokens", tokens);
		ListTag offers = new ListTag();
		this.offers.forEach(c -> offers.add(c.save()));
		tag.put("offers", offers);
		ListTag active = new ListTag();
		this.active.forEach(c -> active.add(c.save()));
		tag.put("active", active);
		tag.putLong("day", day);
		tag.putInt("rerolls", rerolls);
		CompoundTag bought = new CompoundTag();
		this.bought.forEach(bought::putInt);
		tag.put("bought", bought);
		tag.put("stock", strings(stock));
		tag.put("deals", strings(deals));
		tag.put("recent", strings(recent));
		tag.putInt("nextId", nextId);
		tag.putInt("completed", completed);
		tag.putIntArray("kinds", completedKinds.clone());
		tag.putInt("spent", spent);
		tag.putLong("lastDone", lastDoneAt);
		tag.putInt("streak", streak);
		tag.putInt("seenTier", seenTier);
		tag.putLong("ransomReady", ransomReady);
		tag.putFloat("debtOwed", debtOwed);
		tag.putInt("fortuneJobs", fortuneJobs);
		tag.putInt("luckyJobs", luckyJobs);
		return tag;
	}

	public Contract activeById(int id) {
		for (Contract c : active) {
			if (c.id == id) {
				return c;
			}
		}
		return null;
	}

	public Contract offerById(int id) {
		for (Contract c : offers) {
			if (c.id == id) {
				return c;
			}
		}
		return null;
	}

	/** Whether a hunt in hand is after this entity type (id like minecraft:zombie). */
	public boolean hunting(String entityType) {
		for (Contract c : active) {
			if (c.kind == Contract.Kind.HUNT && !c.done() && c.target.equals(entityType)) {
				return true;
			}
		}
		return false;
	}

	public boolean hasHunts() {
		for (Contract c : active) {
			if (c.kind == Contract.Kind.HUNT && !c.done()) {
				return true;
			}
		}
		return false;
	}
}
