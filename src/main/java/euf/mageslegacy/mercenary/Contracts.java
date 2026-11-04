package euf.mageslegacy.mercenary;

import euf.mageslegacy.fx.Fx;
import euf.mageslegacy.race.Deeds;
import euf.mageslegacy.race.Mastery;
import euf.mageslegacy.race.Progression;
import euf.mageslegacy.race.Talent;
import euf.mageslegacy.race.Talents;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.ChatFormatting;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.biome.Biome;

/**
 * The board of the Mage's Shop. Offers come from the tiers the Mercenary's race level has opened (the tree's: 1, 10,
 * 20, 30); a job done puts a fresh offer on the board at once, and every {@link #DAY} ticks of the overworld's own
 * clock (beds don't hurry it) the board and the Shop's stock are laid out anew. Contracts pay race experience (the
 * Mercenary's main way to grow) and tokens.
 */
public final class Contracts {
	public static final int[] XP = {120, 240, 400, 620};
	public static final int[] TOKENS = {10, 16, 24, 34};
	public static final float ELITE_WORK = 2;
	public static final float ELITE_PAY = 2.5f;
	public static final long DAY = 24000;
	/** How many recent targets stay off the board. */
	private static final int RECENT = 6;
	/** The Streak: a job within 20 minutes of the last adds 10%, up to 30%. */
	private static final int STREAK_TICKS = 20 * 60 * 20;

	public record Template(Contract.Kind kind, String target, int amount, int tier) {
	}

	public static final List<Template> TEMPLATES = new ArrayList<>();

	static {
		// Tier I: the overworld's own
		hunts(1, "zombie:10", "skeleton:8", "spider:8", "creeper:5", "drowned:6", "husk:6", "stray:5", "slime:5", "witch:2", "pillager:4");
		delivers(1, "coal:32", "iron_ingot:12", "copper_ingot:24", "leather:8", "string:12", "bone:16", "gunpowder:8", "wheat:32", "redstone:24",
				"lapis_lazuli:16", "oak_log:32", "rotten_flesh:24");
		explores(1, "desert", "jungle", "swamp", "snowy_plains", "badlands", "savanna", "taiga", "dark_forest", "cherry_grove", "birch_forest",
				"flower_forest", "sunflower_plains");
		// Tier II: deeper and further, the Nether opens
		hunts(2, "enderman:5", "witch:4", "pillager:8", "cave_spider:10", "guardian:4", "blaze:6", "magma_cube:6", "wither_skeleton:4", "ghast:2",
				"hoglin:3", "zombified_piglin:10");
		delivers(2, "gold_ingot:12", "quartz:32", "glowstone_dust:16", "blaze_rod:4", "obsidian:8", "nether_wart:16", "ender_pearl:4", "emerald:6",
				"amethyst_shard:16", "honeycomb:6", "glow_ink_sac:6");
		explores(2, "crimson_forest", "warped_forest", "basalt_deltas", "soul_sand_valley", "nether_wastes", "lush_caves", "dripstone_caves",
				"ice_spikes", "bamboo_jungle", "mangrove_swamp");
		// Tier III: raids, ruins, the deep
		hunts(3, "vindicator:6", "evoker:1", "phantom:4", "ravager:1", "piglin_brute:2", "guardian:8", "wither_skeleton:8", "blaze:12", "breeze:2",
				"bogged:6", "enderman:10");
		delivers(3, "diamond:4", "emerald:12", "prismarine_shard:16", "ghast_tear:2", "slime_ball:12", "rabbit_foot:2", "crying_obsidian:6",
				"magma_cream:8", "nautilus_shell:2");
		explores(3, "deep_dark", "mushroom_fields", "warm_ocean", "frozen_peaks", "jagged_peaks", "eroded_badlands", "wooded_badlands");
		// Tier IV: the End, and the hardest of the rest (an overworld player always has work here too)
		hunts(4, "shulker:4", "enderman:15", "elder_guardian:1", "evoker:3", "ravager:2", "wither_skeleton:12", "piglin_brute:4", "breeze:4",
				"phantom:8");
		delivers(4, "chorus_fruit:32", "shulker_shell:2", "phantom_membrane:4", "netherite_scrap:1", "diamond:10", "echo_shard:2", "ghast_tear:6",
				"popped_chorus_fruit:16");
		explores(4, "end_highlands", "end_midlands", "end_barrens", "small_end_islands", "deep_dark", "mushroom_fields", "ice_spikes");
	}

	private Contracts() {
	}

	private static void hunts(int tier, String... entries) {
		for (String e : entries) {
			String[] p = e.split(":");
			TEMPLATES.add(new Template(Contract.Kind.HUNT, "minecraft:" + p[0], Integer.parseInt(p[1]), tier));
		}
	}

	private static void delivers(int tier, String... entries) {
		for (String e : entries) {
			String[] p = e.split(":");
			TEMPLATES.add(new Template(Contract.Kind.DELIVER, "minecraft:" + p[0], Integer.parseInt(p[1]), tier));
		}
	}

	private static void explores(int tier, String... biomes) {
		for (String b : biomes) {
			TEMPLATES.add(new Template(Contract.Kind.EXPLORE, "minecraft:" + b, 1, tier));
		}
	}

	public static long today(ServerPlayer player) {
		return player.server.overworld().getGameTime() / DAY;
	}

	/** Ticks until the next rotation of board and stock. */
	public static long untilNextDay(ServerPlayer player) {
		long now = player.server.overworld().getGameTime();
		return (now / DAY + 1) * DAY - now;
	}

	public static int offerCount(ServerPlayer player) {
		return Talents.has(player, Talent.CON_BOARD) ? 4 : 3;
	}

	public static int handSlots(ServerPlayer player) {
		return Talents.has(player, Talent.CON_HANDS) ? 3 : 2;
	}

	/** A new day: a fresh board, a fresh stock, today's purchases forgotten. Returns whether it rotated. */
	public static boolean rotate(ServerPlayer player, Account a) {
		long today = today(player);
		if (a.day == today) {
			return false;
		}
		a.day = today;
		a.rerolls = 0;
		a.bought.clear();
		a.offers.clear();
		fill(player, a);
		Shop.restock(player, a);
		return true;
	}

	/**
	 * Fills the board up to its size: the first offer always from the highest open tier, the second from tier I (so the
	 * work never stalls on a level's gap), the rest mostly from the highest; never a job already on the board or in
	 * hand, never one done lately; at least two kinds of work on a board of three or more.
	 */
	public static void fill(ServerPlayer player, Account a) {
		RandomSource random = player.getRandom();
		int maxTier = Progression.openTiers(player);
		int want = offerCount(player);
		int guard = 0;
		while (a.offers.size() < want && guard++ < 40) {
			int tier;
			if (!hasTier(a, maxTier)) {
				tier = maxTier;
			} else if (!hasTier(a, 1)) {
				tier = 1;
			} else {
				tier = random.nextFloat() < 0.6f ? maxTier : 1 + random.nextInt(maxTier);
			}
			Contract.Kind avoid = null;
			if (a.offers.size() == want - 1 && a.offers.size() >= 2 && a.offers.stream().map(c -> c.kind).distinct().count() == 1) {
				avoid = a.offers.get(0).kind;
			}
			Contract c = roll(player, a, tier, avoid);
			if (c != null) {
				a.offers.add(c);
			}
		}
	}

	private static boolean hasTier(Account a, int tier) {
		return a.offers.stream().anyMatch(c -> c.tier == tier);
	}

	/** One offer of a tier (another kind than {@code avoid}, if given). */
	private static Contract roll(ServerPlayer player, Account a, int tier, Contract.Kind avoid) {
		RandomSource random = player.getRandom();
		List<Template> pool = new ArrayList<>();
		for (Template t : TEMPLATES) {
			if (t.tier() == tier && t.kind() != avoid && !taken(a, t.target()) && !a.recent.contains(t.target()) && exists(t)) {
				pool.add(t);
			}
		}
		if (pool.isEmpty()) {
			// Everything recent: then anything not on the board or in hand
			for (Template t : TEMPLATES) {
				if (t.tier() == tier && !taken(a, t.target()) && exists(t)) {
					pool.add(t);
				}
			}
		}
		if (pool.isEmpty()) {
			return null;
		}
		Template t = pool.get(random.nextInt(pool.size()));
		boolean elite = Talents.has(player, Talent.CON_ELITE) && t.kind() != Contract.Kind.EXPLORE && a.offers.stream().noneMatch(c -> c.elite)
				&& random.nextFloat() < 0.25f;
		float pay = elite ? ELITE_PAY : 1;
		int amount = elite ? Math.round(t.amount() * ELITE_WORK) : t.amount();
		return new Contract(a.nextId++, t.kind(), t.target(), amount, t.tier(), elite, Math.round(XP[tier - 1] * pay),
				Math.round(TOKENS[tier - 1] * pay));
	}

	private static boolean taken(Account a, String target) {
		return a.offers.stream().anyMatch(c -> c.target.equals(target)) || a.active.stream().anyMatch(c -> c.target.equals(target));
	}

	/** Whether a template names a real thing in this game (an id from a later version, or a removed item, is skipped). */
	private static boolean exists(Template t) {
		ResourceLocation id = ResourceLocation.tryParse(t.target());
		if (id == null) {
			return false;
		}
		return switch (t.kind()) {
			case HUNT -> BuiltInRegistries.ENTITY_TYPE.containsKey(id);
			case DELIVER -> BuiltInRegistries.ITEM.containsKey(id);
			case EXPLORE -> true;
		};
	}

	// ---- the Mercenary's actions in the book (all checked here again: the client only asks) ----

	public static boolean accept(ServerPlayer player, Account a, int id) {
		Contract c = a.offerById(id);
		if (c == null) {
			return false;
		}
		if (a.active.size() >= handSlots(player)) {
			player.displayClientMessage(Component.translatable("mages_legacy.merc.no_slot", handSlots(player)).withStyle(ChatFormatting.RED), true);
			return false;
		}
		a.offers.remove(c);
		// A land counts only once reached: whoever stands in it already must leave it first
		c.armed = false;
		if (c.kind == Contract.Kind.EXPLORE && Talents.has(player, Talent.CON_GUIDE)) {
			locate(player, c);
		}
		a.active.add(c);
		player.level().playSound(null, player.blockPosition(), SoundEvents.BOOK_PAGE_TURN, SoundSource.PLAYERS, 1, 1.1f);
		player.level().playSound(null, player.blockPosition(), SoundEvents.VILLAGER_WORK_CARTOGRAPHER, SoundSource.PLAYERS, 0.6f, 1.2f);
		return true;
	}

	/** The Guide: the nearest such land in this world (searched once, in a bounded square), kept with the job. */
	private static void locate(ServerPlayer player, Contract c) {
		ResourceLocation id = ResourceLocation.tryParse(c.target);
		if (id == null) {
			return;
		}
		ResourceKey<Biome> key = ResourceKey.create(Registries.BIOME, id);
		ServerLevel level = player.serverLevel();
		var found = level.findClosestBiome3d(h -> h.is(key), player.blockPosition(), 2400, 48, 64);
		if (found != null) {
			c.located = true;
			c.locX = found.getFirst().getX();
			c.locZ = found.getFirst().getZ();
		}
	}

	public static boolean abandon(ServerPlayer player, Account a, int id) {
		Contract c = a.activeById(id);
		if (c == null) {
			return false;
		}
		a.active.remove(c);
		player.level().playSound(null, player.blockPosition(), SoundEvents.BOOK_PUT, SoundSource.PLAYERS, 1, 0.7f);
		return true;
	}

	/**
	 * Hands over what a delivery asks for, as much as the inventory holds (counted first, then taken: nothing is taken
	 * that isn't counted, nothing counted that isn't taken).
	 */
	public static boolean deliver(ServerPlayer player, Account a, int id) {
		Contract c = a.activeById(id);
		ResourceLocation itemId = c == null ? null : ResourceLocation.tryParse(c.target);
		if (c == null || c.kind != Contract.Kind.DELIVER || itemId == null || c.done()) {
			return false;
		}
		Item item = BuiltInRegistries.ITEM.get(itemId);
		int need = c.amount - c.progress;
		Inventory inventory = player.getInventory();
		int taken = 0;
		for (int slot = 0; slot < inventory.getContainerSize() && taken < need; slot++) {
			ItemStack stack = inventory.getItem(slot);
			if (!stack.isEmpty() && stack.is(item)) {
				int take = Math.min(need - taken, stack.getCount());
				stack.shrink(take);
				taken += take;
			}
		}
		if (taken == 0) {
			player.displayClientMessage(Component.translatable("mages_legacy.merc.nothing_to_deliver").withStyle(ChatFormatting.RED), true);
			return false;
		}
		inventory.setChanged();
		c.progress += taken;
		player.level().playSound(null, player.blockPosition(), SoundEvents.BUNDLE_INSERT, SoundSource.PLAYERS, 1, 0.9f);
		if (c.done()) {
			complete(player, a, c);
		} else {
			player.displayClientMessage(Component.translatable("mages_legacy.merc.delivered", c.progress, c.amount).withStyle(ChatFormatting.GOLD), true);
		}
		return true;
	}

	/** A fresh board for tokens: 5, 10, 20, 40... a day; with Connections the first is free and the rest cost half. */
	public static int rerollPrice(ServerPlayer player, Account a) {
		boolean connected = Talents.has(player, Talent.CON_CONNECTIONS);
		if (connected && a.rerolls == 0) {
			return 0;
		}
		int price = 5 << Math.min(4, a.rerolls);
		return connected ? Math.max(1, price / 2) : price;
	}

	public static boolean reroll(ServerPlayer player, Account a) {
		int price = Shop.discounted(player, rerollPrice(player, a));
		if (a.tokens < price) {
			player.displayClientMessage(Component.translatable("mages_legacy.merc.no_tokens").withStyle(ChatFormatting.RED), true);
			return false;
		}
		a.tokens -= price;
		a.spent += price;
		a.rerolls++;
		a.offers.clear();
		fill(player, a);
		Mercenary.spent(player, a, price);
		player.level().playSound(null, player.blockPosition(), SoundEvents.BOOK_PAGE_TURN, SoundSource.PLAYERS, 1, 0.8f);
		return true;
	}

	// ---- the work itself ----

	/** Kills that count: the hunter's own (twice for a Trophy), or an ally's near a Mercenary with Partners. */
	public static void onKill(ServerPlayer player, Account a, String type, boolean trophy) {
		for (Contract c : List.copyOf(a.active)) {
			if (c.kind == Contract.Kind.HUNT && !c.done() && c.target.equals(type)) {
				c.progress = Math.min(c.amount, c.progress + (trophy ? 2 : 1));
				if (c.done()) {
					complete(player, a, c);
				}
				return;
			}
		}
	}

	/** Once a second: the explorations (armed once outside the land, done once in it). */
	public static boolean tickSecond(ServerPlayer player, Account a) {
		boolean changed = false;
		Holder<Biome> here = player.level().getBiome(player.blockPosition());
		for (Contract c : List.copyOf(a.active)) {
			if (c.kind != Contract.Kind.EXPLORE || c.done()) {
				continue;
			}
			ResourceLocation id = ResourceLocation.tryParse(c.target);
			boolean inside = id != null && here.is(ResourceKey.create(Registries.BIOME, id));
			if (!inside && !c.armed) {
				c.armed = true;
				changed = true;
			} else if (inside && c.armed) {
				c.progress = c.amount;
				complete(player, a, c);
				changed = true;
			}
		}
		return changed;
	}

	/** Pay with the talents counted now: Bonus +25% tokens, Renown +20% experience, the Streak up to +30% of both. */
	public static int[] pay(ServerPlayer player, Account a, Contract c, boolean forStreak) {
		float streak = 0;
		if (Talents.has(player, Talent.CON_STREAK)) {
			int s = forStreak ? nextStreak(player, a) : a.streak;
			streak = Math.min(0.3f, 0.1f * s);
		}
		float xp = c.xp * (1 + (Talents.has(player, Talent.CON_RENOWN) ? 0.2f : 0) + streak);
		float tokens = c.tokens * (1 + (Talents.has(player, Talent.CON_BONUS) ? 0.25f : 0) + streak);
		return new int[] {Math.round(xp), Math.round(tokens)};
	}

	private static int nextStreak(ServerPlayer player, Account a) {
		long now = player.server.overworld().getGameTime();
		return a.lastDoneAt >= 0 && now - a.lastDoneAt <= STREAK_TICKS ? a.streak + 1 : 0;
	}

	/** The pay, a fresh offer in its place, and what the trials count. Only ever once: the job leaves the hand first. */
	public static void complete(ServerPlayer player, Account a, Contract c) {
		if (!a.active.remove(c)) {
			return;
		}
		int[] pay = pay(player, a, c, true);
		a.streak = nextStreak(player, a);
		a.lastDoneAt = player.server.overworld().getGameTime();
		a.tokens += pay[1];
		a.completed++;
		a.completedKinds[c.kind.ordinal()]++;
		a.recent.add(c.target);
		while (a.recent.size() > RECENT) {
			a.recent.remove(0);
		}
		// The work never runs dry: a fresh offer at once
		fill(player, a);
		player.displayClientMessage(Component.translatable("mages_legacy.merc.completed", Component.translatable("mages_legacy.merc.kind." + c.kind.id()),
				pay[1], pay[0]).withStyle(ChatFormatting.GOLD), false);
		Progression.addXp(player, pay[0]);
		ServerLevel level = player.serverLevel();
		level.playSound(null, player.blockPosition(), SoundEvents.VILLAGER_YES, SoundSource.PLAYERS, 0.6f, 0.8f);
		level.playSound(null, player.blockPosition(), SoundEvents.PLAYER_LEVELUP, SoundSource.PLAYERS, 0.5f, 1.4f);
		Fx.contractDone(level, player);
		Mercenary.contractDone(player, a, c);
		Talents.addProgress(player, Mercenary.T_CONTRACTS, 1);
		Talents.addProgress(player, Mercenary.T_KIND + c.kind.id(), 1);
		if (c.kind == Contract.Kind.HUNT) {
			Talents.addProgress(player, Mercenary.T_HUNTS, 1);
		}
		Mastery.addDistinct(player, Mastery.Goal.SIX_CONTRACTS, "c" + a.completed + ":" + c.kind.id());
		Deeds.grant(player, "merc_first_contract");
		if (c.elite) {
			Deeds.grant(player, "merc_elite");
		}
		if (a.completed >= 25) {
			Deeds.grant(player, "merc_veteran");
		}
	}
}
