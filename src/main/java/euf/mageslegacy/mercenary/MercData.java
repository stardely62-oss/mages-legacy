package euf.mageslegacy.mercenary;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtElement;
import net.minecraft.nbt.NbtList;
import net.minecraft.nbt.NbtString;

/**
 * A Mercenary's account in the Mage's Shop: tokens (they exist nowhere but here), the board of offered contracts,
 * the contracts in hand, and what was bought today.
 */
public class MercData {
	public int tokens;
	public final List<Contract> offers = new ArrayList<>();
	public final List<Contract> active = new ArrayList<>();
	/** The in-game day the board was last filled; a new day brings new offers and new prices. */
	public long day = -1;
	public int rerolls;
	/** Shop id -> how many bought today. */
	public final Map<String, Integer> bought = new HashMap<>();
	public int completed;
	/** Today's goods in the shop (ware ids), rolled with the board; one of them is the deal of the day. */
	public final List<String> stock = new ArrayList<>();
	public String deal = "";

	public void clear() {
		tokens = 0;
		offers.clear();
		active.clear();
		day = -1;
		rerolls = 0;
		bought.clear();
		completed = 0;
		stock.clear();
		deal = "";
	}

	public NbtCompound toNbt() {
		NbtCompound nbt = new NbtCompound();
		nbt.putInt("tokens", tokens);
		nbt.put("offers", list(offers));
		nbt.put("active", list(active));
		nbt.putLong("day", day);
		nbt.putInt("rerolls", rerolls);
		NbtCompound today = new NbtCompound();
		bought.forEach(today::putInt);
		nbt.put("bought", today);
		nbt.putInt("completed", completed);
		NbtList wares = new NbtList();
		stock.forEach(id -> wares.add(NbtString.of(id)));
		nbt.put("stock", wares);
		nbt.putString("deal", deal);
		return nbt;
	}

	public void readNbt(NbtCompound nbt) {
		clear();
		tokens = nbt.getInt("tokens");
		read(nbt.getList("offers", NbtElement.COMPOUND_TYPE), offers);
		read(nbt.getList("active", NbtElement.COMPOUND_TYPE), active);
		day = nbt.contains("day") ? nbt.getLong("day") : -1;
		rerolls = nbt.getInt("rerolls");
		NbtCompound today = nbt.getCompound("bought");
		for (String id : today.getKeys()) {
			bought.put(id, today.getInt(id));
		}
		completed = nbt.getInt("completed");
		for (NbtElement e : nbt.getList("stock", NbtElement.STRING_TYPE)) {
			stock.add(e.asString());
		}
		deal = nbt.getString("deal");
	}

	private static NbtList list(List<Contract> contracts) {
		NbtList list = new NbtList();
		contracts.forEach(c -> list.add(c.toNbt()));
		return list;
	}

	private static void read(NbtList list, List<Contract> into) {
		for (int i = 0; i < list.size(); i++) {
			into.add(Contract.fromNbt(list.getCompound(i)));
		}
	}
}
