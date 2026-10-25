package euf.mageslegacy.race;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import net.minecraft.item.Item;
import net.minecraft.item.Items;

/** Every talent of every race. Branch names live under mages_legacy.branch.<race>.<index>. */
public final class Talents {
	private static final Map<String, Talent> BY_ID = new LinkedHashMap<>();

	// Mycelids: Swarm (clouds and poison), Network (jumps and nodes), Flesh (diet and endurance)
	public static final String SWARM_WIDE = add("swarm_wide", Race.MYCELID, 0, 1, 1, null, Items.SPORE_BLOSSOM);
	public static final String SWARM_TRAIL = add("swarm_trail", Race.MYCELID, 0, 1, 1, null, Items.POISONOUS_POTATO);
	public static final String SWARM_POTENT = add("swarm_potent", Race.MYCELID, 0, 2, 2, SWARM_WIDE, Items.SPIDER_EYE);
	public static final String SWARM_INFECTION = add("swarm_infection", Race.MYCELID, 0, 2, 2, SWARM_POTENT, Items.BONE);
	public static final String SWARM_LINGERING = add("swarm_lingering", Race.MYCELID, 0, 3, 3, SWARM_INFECTION, Items.FERMENTED_SPIDER_EYE);

	public static final String NET_CHAIN = add("net_chain", Race.MYCELID, 1, 1, 1, null, Items.MYCELIUM);
	public static final String NET_REACH = add("net_reach", Race.MYCELID, 1, 1, 1, null, Items.CHORUS_FRUIT);
	public static final String NET_NODES = add("net_nodes", Race.MYCELID, 1, 2, 2, NET_CHAIN, Items.ROOTED_DIRT);
	public static final String NET_FAR = add("net_far", Race.MYCELID, 1, 2, 2, NET_NODES, Items.WARPED_ROOTS);
	public static final String NET_ROOTS = add("net_roots", Race.MYCELID, 1, 3, 3, NET_REACH, Items.HANGING_ROOTS);

	public static final String FLESH_TWO_TASTES = add("flesh_two_tastes", Race.MYCELID, 2, 1, 1, null, Items.BROWN_MUSHROOM);
	public static final String FLESH_RICH = add("flesh_rich", Race.MYCELID, 2, 1, 1, null, Items.RED_MUSHROOM);
	public static final String FLESH_DARK_REGEN = add("flesh_dark_regen", Race.MYCELID, 2, 2, 2, FLESH_TWO_TASTES, Items.GLOW_BERRIES);
	public static final String FLESH_CAP = add("flesh_cap", Race.MYCELID, 2, 2, 2, FLESH_DARK_REGEN, Items.RED_MUSHROOM_BLOCK);
	public static final String FLESH_SUNPROOF = add("flesh_sunproof", Race.MYCELID, 2, 3, 3, FLESH_CAP, Items.SHROOMLIGHT);

	private Talents() {
	}

	private static String add(String id, Race race, int branch, int tier, int cost, String requires, Item icon) {
		BY_ID.put(id, new Talent(id, race, branch, tier, cost, requires, icon));
		return id;
	}

	public static Optional<Talent> byId(String id) {
		return Optional.ofNullable(BY_ID.get(id));
	}

	public static List<Talent> of(Race race) {
		List<Talent> list = new ArrayList<>();
		BY_ID.values().stream().filter(t -> t.race() == race).forEach(list::add);
		return Collections.unmodifiableList(list);
	}

	public static void init() {
		// Loads the class, so every talent is registered before the first lookup
	}
}
