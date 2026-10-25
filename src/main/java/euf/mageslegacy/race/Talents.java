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

	// Mycelids: Swarm (clouds and poison), Network (jumps and nodes), Flesh (diet and endurance). Per branch three
	// talents on tier I, four on II, three on III and a crown on IV: 75 points for all, 60 at level 50, so a build
	// has to choose. Within a tier the order here is the order on screen.
	public static final String SWARM_WIDE = add("swarm_wide", Race.MYCELID, 0, 1, 1, null, Items.SPORE_BLOSSOM);
	public static final String SWARM_TRAIL = add("swarm_trail", Race.MYCELID, 0, 1, 1, null, Items.POISONOUS_POTATO);
	public static final String SWARM_QUICK = add("swarm_quick", Race.MYCELID, 0, 1, 1, null, Items.CLOCK);
	public static final String SWARM_POTENT = add("swarm_potent", Race.MYCELID, 0, 2, 2, SWARM_WIDE, Items.SPIDER_EYE);
	public static final String SWARM_INFECTION = add("swarm_infection", Race.MYCELID, 0, 2, 2, SWARM_POTENT, Items.BONE);
	public static final String SWARM_WEAKNESS = add("swarm_weakness", Race.MYCELID, 0, 2, 2, SWARM_TRAIL, Items.STONE_SWORD);
	public static final String SWARM_MENDING = add("swarm_mending", Race.MYCELID, 0, 2, 2, SWARM_QUICK, Items.GLISTERING_MELON_SLICE);
	public static final String SWARM_LINGERING = add("swarm_lingering", Race.MYCELID, 0, 3, 3, SWARM_INFECTION, Items.FERMENTED_SPIDER_EYE);
	public static final String SWARM_BURST = add("swarm_burst", Race.MYCELID, 0, 3, 3, SWARM_WEAKNESS, Items.FIRE_CHARGE);
	public static final String SWARM_FOLLOW = add("swarm_follow", Race.MYCELID, 0, 3, 3, SWARM_MENDING, Items.LEAD);
	public static final String SWARM_BLOOM = add("swarm_bloom", Race.MYCELID, 0, 4, 5, SWARM_LINGERING, Items.WITHER_ROSE);

	public static final String NET_CHAIN = add("net_chain", Race.MYCELID, 1, 1, 1, null, Items.MYCELIUM);
	public static final String NET_REACH = add("net_reach", Race.MYCELID, 1, 1, 1, null, Items.CHORUS_FRUIT);
	public static final String NET_LIGHT_STEP = add("net_light_step", Race.MYCELID, 1, 1, 1, null, Items.FEATHER);
	public static final String NET_NODES = add("net_nodes", Race.MYCELID, 1, 2, 2, NET_CHAIN, Items.ROOTED_DIRT);
	public static final String NET_FAR = add("net_far", Race.MYCELID, 1, 2, 2, NET_NODES, Items.WARPED_ROOTS);
	public static final String NET_CHEAP = add("net_cheap", Race.MYCELID, 1, 2, 2, NET_REACH, Items.SUGAR);
	public static final String NET_HASTE = add("net_haste", Race.MYCELID, 1, 2, 2, NET_LIGHT_STEP, Items.RABBIT_FOOT);
	public static final String NET_SHARED = add("net_shared", Race.MYCELID, 1, 3, 3, NET_FAR, Items.GLOW_LICHEN);
	public static final String NET_ROOTS = add("net_roots", Race.MYCELID, 1, 3, 3, NET_REACH, Items.HANGING_ROOTS);
	public static final String NET_CLEANSE = add("net_cleanse", Race.MYCELID, 1, 3, 3, NET_CHEAP, Items.MILK_BUCKET);
	public static final String NET_MIND = add("net_mind", Race.MYCELID, 1, 4, 5, NET_SHARED, Items.ENDER_EYE);

	public static final String FLESH_TWO_TASTES = add("flesh_two_tastes", Race.MYCELID, 2, 1, 1, null, Items.BROWN_MUSHROOM);
	public static final String FLESH_RICH = add("flesh_rich", Race.MYCELID, 2, 1, 1, null, Items.RED_MUSHROOM);
	public static final String FLESH_FIREPROOF = add("flesh_fireproof", Race.MYCELID, 2, 1, 1, null, Items.WATER_BUCKET);
	public static final String FLESH_DARK_REGEN = add("flesh_dark_regen", Race.MYCELID, 2, 2, 2, FLESH_TWO_TASTES, Items.GLOW_BERRIES);
	public static final String FLESH_CAP = add("flesh_cap", Race.MYCELID, 2, 2, 2, FLESH_DARK_REGEN, Items.RED_MUSHROOM_BLOCK);
	public static final String FLESH_MEAT = add("flesh_meat", Race.MYCELID, 2, 2, 2, FLESH_RICH, Items.COOKED_BEEF);
	public static final String FLESH_SPORE_BLOOD = add("flesh_spore_blood", Race.MYCELID, 2, 2, 2, FLESH_FIREPROOF, Items.REDSTONE);
	public static final String FLESH_SUNPROOF = add("flesh_sunproof", Race.MYCELID, 2, 3, 3, FLESH_CAP, Items.SHROOMLIGHT);
	public static final String FLESH_FEAST = add("flesh_feast", Race.MYCELID, 2, 3, 3, FLESH_MEAT, Items.MUSHROOM_STEW);
	public static final String FLESH_HEARTY = add("flesh_hearty", Race.MYCELID, 2, 3, 3, FLESH_SPORE_BLOOD, Items.GOLDEN_APPLE);
	public static final String FLESH_REBIRTH = add("flesh_rebirth", Race.MYCELID, 2, 4, 5, FLESH_SUNPROOF, Items.TOTEM_OF_UNDYING);

	// Shepherds: Legion (echoes of the fallen), Pack (bonded beasts), Voice (the Shepherd itself). Same shape and
	// prices as the Mycelid tree.
	public static final String LEGION_CHEAP = add("legion_cheap", Race.SHEPHERD, 0, 1, 1, null, Items.BONE);
	public static final String LEGION_THRESHOLDS = add("legion_thresholds", Race.SHEPHERD, 0, 1, 1, null, Items.SKELETON_SKULL);
	public static final String LEGION_STURDY = add("legion_sturdy", Race.SHEPHERD, 0, 1, 1, null, Items.SHIELD);
	public static final String LEGION_CAPACITY = add("legion_capacity", Race.SHEPHERD, 0, 2, 2, LEGION_CHEAP, Items.ZOMBIE_HEAD);
	public static final String LEGION_SLEEPLESS = add("legion_sleepless", Race.SHEPHERD, 0, 2, 2, LEGION_CAPACITY, Items.CLOCK);
	public static final String LEGION_RETURN = add("legion_return", Race.SHEPHERD, 0, 2, 2, LEGION_THRESHOLDS, Items.SOUL_LANTERN);
	public static final String LEGION_WEAPONS = add("legion_weapons", Race.SHEPHERD, 0, 2, 2, LEGION_STURDY, Items.IRON_SWORD);
	public static final String LEGION_MORE = add("legion_more", Race.SHEPHERD, 0, 3, 3, LEGION_SLEEPLESS, Items.WITHER_SKELETON_SKULL);
	public static final String LEGION_LEECH = add("legion_leech", Race.SHEPHERD, 0, 3, 3, LEGION_RETURN, Items.SOUL_SAND);
	public static final String LEGION_BURNING = add("legion_burning", Race.SHEPHERD, 0, 3, 3, LEGION_WEAPONS, Items.SOUL_CAMPFIRE);
	public static final String LEGION_ARMY = add("legion_army", Race.SHEPHERD, 0, 4, 5, LEGION_MORE, Items.WHITE_BANNER);

	public static final String PACK_FRIENDLY = add("pack_friendly", Race.SHEPHERD, 1, 1, 1, null, Items.BONE_MEAL);
	public static final String PACK_CAMPLESS = add("pack_campless", Race.SHEPHERD, 1, 1, 1, null, Items.CAMPFIRE);
	public static final String PACK_THICK = add("pack_thick", Race.SHEPHERD, 1, 1, 1, null, Items.RABBIT_HIDE);
	public static final String PACK_LEVELS = add("pack_levels", Race.SHEPHERD, 1, 2, 2, PACK_FRIENDLY, Items.EXPERIENCE_BOTTLE);
	public static final String PACK_SKILL = add("pack_skill", Race.SHEPHERD, 1, 2, 2, PACK_LEVELS, Items.NETHER_STAR);
	public static final String PACK_QUICK_REST = add("pack_quick_rest", Race.SHEPHERD, 1, 2, 2, PACK_CAMPLESS, Items.RED_BED);
	public static final String PACK_ARMOR = add("pack_armor", Race.SHEPHERD, 1, 2, 2, PACK_THICK, Items.LEATHER_HORSE_ARMOR);
	public static final String PACK_HEAL = add("pack_heal", Race.SHEPHERD, 1, 3, 3, PACK_SKILL, Items.GLISTERING_MELON_SLICE);
	public static final String PACK_INSTANT = add("pack_instant", Race.SHEPHERD, 1, 3, 3, PACK_QUICK_REST, Items.FEATHER);
	public static final String PACK_FURY = add("pack_fury", Race.SHEPHERD, 1, 3, 3, PACK_ARMOR, Items.BLAZE_POWDER);
	public static final String PACK_ALPHA = add("pack_alpha", Race.SHEPHERD, 1, 4, 5, PACK_HEAL, Items.GOLDEN_HELMET);

	public static final String VOICE_DEEP = add("voice_deep", Race.SHEPHERD, 2, 1, 1, null, Items.NOTE_BLOCK);
	public static final String VOICE_CAPACITY = add("voice_capacity", Race.SHEPHERD, 2, 1, 1, null, Items.LEAD);
	public static final String VOICE_ARMOR = add("voice_armor", Race.SHEPHERD, 2, 1, 1, null, Items.IRON_CHESTPLATE);
	public static final String VOICE_RALLY = add("voice_rally", Race.SHEPHERD, 2, 2, 2, VOICE_DEEP, Items.GOAT_HORN);
	public static final String VOICE_SWAP = add("voice_swap", Race.SHEPHERD, 2, 2, 2, VOICE_CAPACITY, Items.ENDER_PEARL);
	public static final String VOICE_STRENGTH = add("voice_strength", Race.SHEPHERD, 2, 2, 2, VOICE_ARMOR, Items.IRON_AXE);
	public static final String VOICE_LIFE = add("voice_life", Race.SHEPHERD, 2, 2, 2, VOICE_STRENGTH, Items.GOLDEN_APPLE);
	public static final String VOICE_AURA = add("voice_aura", Race.SHEPHERD, 2, 3, 3, VOICE_RALLY, Items.BEACON);
	public static final String VOICE_COMMAND = add("voice_command", Race.SHEPHERD, 2, 3, 3, VOICE_SWAP, Items.SPYGLASS);
	public static final String VOICE_MARK = add("voice_mark", Race.SHEPHERD, 2, 3, 3, VOICE_LIFE, Items.TARGET);
	public static final String VOICE_HEART = add("voice_heart", Race.SHEPHERD, 2, 4, 5, VOICE_AURA, Items.HEART_OF_THE_SEA);

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
