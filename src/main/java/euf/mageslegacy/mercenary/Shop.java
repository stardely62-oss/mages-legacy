package euf.mageslegacy.mercenary;

import euf.mageslegacy.item.OblivionScrollItem;
import euf.mageslegacy.race.Progression;
import euf.mageslegacy.race.RaceState;
import euf.mageslegacy.race.Talent;
import euf.mageslegacy.race.Talents;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.function.Function;
import net.minecraft.ChatFormatting;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.item.EnchantedBookItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.alchemy.Potion;
import net.minecraft.world.item.alchemy.PotionContents;
import net.minecraft.world.item.alchemy.Potions;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.EnchantmentInstance;
import net.minecraft.world.item.enchantment.Enchantments;

/**
 * The Mage's Shop: what tokens buy. Every ware has a kind (supplies, materials, potions, books, gear; the services, the
 * hired boosts, are a kind of their own) and a rarity, from common to legendary. Each day the shelves get one or two
 * wares of every kind and three more of any, the counter four services; each one's rarity is rolled on odds that the
 * race level moves towards the rare end (a level-1 Mercenary may still find a legendary ware, a level-50 one still
 * finds plain bread). Every price, stock and balance is the server's: a purchase is checked again here, paid and handed
 * over in one call.
 */
public final class Shop {
	public enum Kind { SERVICE, GOODS }

	public enum Category {
		BOOSTS, SUPPLIES, MATERIALS, POTIONS, BOOKS, GEAR;

		public String id() {
			return name().toLowerCase(Locale.ROOT);
		}
	}

	/** How rare and how strong: each a share of the rolls at level 1 and at level 50 (in between, a straight line), and how many of one listing a day. */
	public enum Rarity {
		COMMON(55, 18, 3), UNCOMMON(27, 30, 2), RARE(12, 28, 1), EPIC(5, 17, 1), LEGENDARY(1, 7, 1);

		public final float low;
		public final float high;
		public final int stock;

		Rarity(float low, float high, int stock) {
			this.low = low;
			this.high = high;
			this.stock = stock;
		}

		public String id() {
			return name().toLowerCase(Locale.ROOT);
		}

		/** This rarity's share of a roll for a race level (percent). */
		public float weight(int level) {
			float t = Math.max(0, Math.min(1, (level - 1) / (float) (Progression.MAX_LEVEL - 1)));
			return low + (high - low) * t;
		}
	}

	/** One ware: what it is, what it costs, and what buying it does (gives items, or works on the buyer). */
	public record Ware(String id, Kind kind, Category category, Rarity rarity, int price, Function<ServerPlayer, List<ItemStack>> goods,
			Consumer<ServerPlayer> service, String icon) {
		/** What the shelf shows. */
		public ItemStack shown(ServerPlayer player) {
			if (goods != null) {
				List<ItemStack> list = goods.apply(player);
				if (!list.isEmpty()) {
					return list.get(0);
				}
			}
			var item = net.minecraft.core.registries.BuiltInRegistries.ITEM.get(net.minecraft.resources.ResourceLocation.parse(icon));
			return new ItemStack(item);
		}
	}

	public static final int PER_KIND_MIN = 1;
	public static final int PER_KIND_MAX = 2;
	public static final int FREE_PICKS = 3;
	public static final int SERVICES = 4;
	private static final float DEAL = 0.7f;
	public static final List<Ware> CATALOG = new ArrayList<>();
	private static final Category[] SHELVES = {Category.SUPPLIES, Category.MATERIALS, Category.POTIONS, Category.BOOKS, Category.GEAR};

	private static void goods(String id, Category category, Rarity rarity, int price, Function<ServerPlayer, List<ItemStack>> goods) {
		CATALOG.add(new Ware(id, Kind.GOODS, category, rarity, price, goods, null, ""));
	}

	private static void service(String id, Rarity rarity, int price, String icon, Consumer<ServerPlayer> effect) {
		CATALOG.add(new Ware(id, Kind.SERVICE, Category.BOOSTS, rarity, price, null, effect, icon));
	}

	private static Function<ServerPlayer, List<ItemStack>> item(Item item, int count) {
		return p -> List.of(new ItemStack(item, count));
	}

	private static Function<ServerPlayer, List<ItemStack>> potion(Item kind, Holder<Potion> potion, int count) {
		return p -> {
			ItemStack stack = PotionContents.createItemStack(kind, potion);
			stack.setCount(count);
			return List.of(stack);
		};
	}

	private static Function<ServerPlayer, List<ItemStack>> enchanted(Item item, Object... enchantments) {
		return p -> {
			ItemStack stack = new ItemStack(item);
			for (int i = 0; i < enchantments.length; i += 2) {
				@SuppressWarnings("unchecked")
				ResourceKey<Enchantment> key = (ResourceKey<Enchantment>) enchantments[i];
				stack.enchant(enchantment(p, key), (Integer) enchantments[i + 1]);
			}
			return List.of(stack);
		};
	}

	/** A book of one enchantment drawn from these (each "key, level"), drawn anew for every purchase. */
	private static Function<ServerPlayer, List<ItemStack>> book(Object... pool) {
		return p -> {
			int pick = p.getRandom().nextInt(pool.length / 2) * 2;
			@SuppressWarnings("unchecked")
			ResourceKey<Enchantment> key = (ResourceKey<Enchantment>) pool[pick];
			return List.of(EnchantedBookItem.createForEnchantment(new EnchantmentInstance(enchantment(p, key), (Integer) pool[pick + 1])));
		};
	}

	private static Holder<Enchantment> enchantment(ServerPlayer player, ResourceKey<Enchantment> key) {
		return player.registryAccess().registryOrThrow(Registries.ENCHANTMENT).getHolderOrThrow(key);
	}

	private static Consumer<ServerPlayer> effect(Holder<MobEffect> effect, int seconds, int amplifier) {
		return p -> p.addEffect(new MobEffectInstance(effect, seconds * 20, amplifier, true, true, true));
	}

	static {
		Rarity c = Rarity.COMMON;
		Rarity u = Rarity.UNCOMMON;
		Rarity r = Rarity.RARE;
		Rarity e = Rarity.EPIC;
		Rarity l = Rarity.LEGENDARY;
		// Supplies
		goods("arrows", Category.SUPPLIES, c, 4, item(Items.ARROW, 32));
		goods("torches", Category.SUPPLIES, c, 3, item(Items.TORCH, 32));
		goods("bread", Category.SUPPLIES, c, 4, item(Items.BREAD, 16));
		goods("steak", Category.SUPPLIES, c, 5, item(Items.COOKED_BEEF, 12));
		goods("golden_carrots", Category.SUPPLIES, u, 12, item(Items.GOLDEN_CARROT, 12));
		goods("spectral_arrows", Category.SUPPLIES, u, 10, item(Items.SPECTRAL_ARROW, 24));
		goods("pearls", Category.SUPPLIES, u, 12, item(Items.ENDER_PEARL, 4));
		goods("rockets", Category.SUPPLIES, u, 10, item(Items.FIREWORK_ROCKET, 16));
		goods("golden_apples", Category.SUPPLIES, r, 25, item(Items.GOLDEN_APPLE, 3));
		goods("xp_bottles", Category.SUPPLIES, r, 22, item(Items.EXPERIENCE_BOTTLE, 16));
		goods("oblivion", Category.SUPPLIES, e, 45, p -> List.of(new ItemStack(OblivionScrollItem.SCROLL.get())));
		goods("harming_arrows", Category.SUPPLIES, e, 45, potion(Items.TIPPED_ARROW, Potions.STRONG_HARMING, 16));
		goods("totem", Category.SUPPLIES, l, 100, item(Items.TOTEM_OF_UNDYING, 1));
		goods("notch_apple", Category.SUPPLIES, l, 110, item(Items.ENCHANTED_GOLDEN_APPLE, 1));
		// Materials
		goods("iron", Category.MATERIALS, c, 6, item(Items.IRON_INGOT, 8));
		goods("coal", Category.MATERIALS, c, 4, item(Items.COAL, 32));
		goods("copper", Category.MATERIALS, c, 4, item(Items.COPPER_INGOT, 16));
		goods("gold", Category.MATERIALS, u, 12, item(Items.GOLD_INGOT, 8));
		goods("lapis", Category.MATERIALS, u, 10, item(Items.LAPIS_LAZULI, 16));
		goods("redstone", Category.MATERIALS, u, 10, item(Items.REDSTONE, 32));
		goods("diamonds", Category.MATERIALS, r, 26, item(Items.DIAMOND, 2));
		goods("emeralds", Category.MATERIALS, r, 22, item(Items.EMERALD, 8));
		goods("blaze_rods", Category.MATERIALS, r, 20, item(Items.BLAZE_ROD, 6));
		goods("diamond_pile", Category.MATERIALS, e, 55, item(Items.DIAMOND, 5));
		goods("debris", Category.MATERIALS, e, 60, item(Items.ANCIENT_DEBRIS, 1));
		goods("netherite", Category.MATERIALS, l, 120, item(Items.NETHERITE_INGOT, 1));
		// Potions
		goods("healing", Category.POTIONS, c, 6, potion(Items.POTION, Potions.HEALING, 1));
		goods("swiftness", Category.POTIONS, c, 6, potion(Items.POTION, Potions.SWIFTNESS, 1));
		goods("strong_healing", Category.POTIONS, u, 12, potion(Items.POTION, Potions.STRONG_HEALING, 1));
		goods("regeneration", Category.POTIONS, u, 14, potion(Items.POTION, Potions.REGENERATION, 1));
		goods("fire_resistance", Category.POTIONS, u, 12, potion(Items.POTION, Potions.LONG_FIRE_RESISTANCE, 1));
		goods("strength", Category.POTIONS, r, 26, potion(Items.POTION, Potions.STRONG_STRENGTH, 1));
		goods("water_breathing", Category.POTIONS, r, 22, potion(Items.POTION, Potions.LONG_WATER_BREATHING, 1));
		goods("slow_falling", Category.POTIONS, r, 22, potion(Items.POTION, Potions.LONG_SLOW_FALLING, 1));
		goods("turtle_master", Category.POTIONS, e, 50, potion(Items.POTION, Potions.STRONG_TURTLE_MASTER, 1));
		goods("splash_healing", Category.POTIONS, e, 48, potion(Items.SPLASH_POTION, Potions.STRONG_HEALING, 3));
		goods("elixir_set", Category.POTIONS, l, 95, p -> List.of(PotionContents.createItemStack(Items.POTION, Potions.STRONG_STRENGTH),
				PotionContents.createItemStack(Items.POTION, Potions.STRONG_REGENERATION), PotionContents.createItemStack(Items.POTION, Potions.STRONG_SWIFTNESS)));
		// Books
		goods("book_common", Category.BOOKS, c, 10, book(Enchantments.UNBREAKING, 2, Enchantments.EFFICIENCY, 2, Enchantments.PROTECTION, 2,
				Enchantments.POWER, 2, Enchantments.SHARPNESS, 2));
		goods("book_uncommon", Category.BOOKS, u, 18, book(Enchantments.UNBREAKING, 3, Enchantments.SHARPNESS, 3, Enchantments.PROTECTION, 3,
				Enchantments.POWER, 3, Enchantments.EFFICIENCY, 3, Enchantments.FEATHER_FALLING, 3));
		goods("book_rare", Category.BOOKS, r, 30, book(Enchantments.SHARPNESS, 4, Enchantments.FORTUNE, 2, Enchantments.LOOTING, 2,
				Enchantments.FEATHER_FALLING, 4, Enchantments.POWER, 4, Enchantments.RESPIRATION, 3));
		goods("book_epic", Category.BOOKS, e, 55, book(Enchantments.FORTUNE, 3, Enchantments.LOOTING, 3, Enchantments.PROTECTION, 4,
				Enchantments.SHARPNESS, 5, Enchantments.POWER, 5, Enchantments.EFFICIENCY, 5));
		goods("book_legendary", Category.BOOKS, l, 90, book(Enchantments.MENDING, 1, Enchantments.SILK_TOUCH, 1, Enchantments.INFINITY, 1));
		// Gear
		goods("iron_sword", Category.GEAR, c, 8, item(Items.IRON_SWORD, 1));
		goods("shield", Category.GEAR, c, 6, item(Items.SHIELD, 1));
		goods("iron_helmet", Category.GEAR, c, 8, item(Items.IRON_HELMET, 1));
		goods("guard_chestplate", Category.GEAR, u, 16, enchanted(Items.IRON_CHESTPLATE, Enchantments.PROTECTION, 1));
		goods("quick_crossbow", Category.GEAR, u, 14, enchanted(Items.CROSSBOW, Enchantments.QUICK_CHARGE, 1));
		goods("miner_pickaxe", Category.GEAR, u, 14, enchanted(Items.IRON_PICKAXE, Enchantments.EFFICIENCY, 2));
		goods("diamond_sword", Category.GEAR, r, 30, item(Items.DIAMOND_SWORD, 1));
		goods("diamond_pickaxe", Category.GEAR, r, 30, item(Items.DIAMOND_PICKAXE, 1));
		goods("hunter_bow", Category.GEAR, r, 26, enchanted(Items.BOW, Enchantments.POWER, 3));
		goods("captain_chestplate", Category.GEAR, e, 60, enchanted(Items.DIAMOND_CHESTPLATE, Enchantments.PROTECTION, 3));
		goods("captain_sword", Category.GEAR, e, 58, enchanted(Items.DIAMOND_SWORD, Enchantments.SHARPNESS, 4, Enchantments.UNBREAKING, 2));
		goods("elytra", Category.GEAR, l, 120, item(Items.ELYTRA, 1));
		goods("guild_blade", Category.GEAR, l, 110, enchanted(Items.DIAMOND_SWORD, Enchantments.SHARPNESS, 5, Enchantments.UNBREAKING, 3,
				Enchantments.LOOTING, 3));
		// The services: the hired boosts, on the buyer alone
		service("hire_swift", c, 6, "minecraft:sugar", effect(MobEffects.MOVEMENT_SPEED, 300, 0));
		service("hire_haste", c, 6, "minecraft:golden_pickaxe", effect(MobEffects.DIG_SPEED, 300, 0));
		service("hire_night", c, 5, "minecraft:golden_carrot", effect(MobEffects.NIGHT_VISION, 480, 0));
		service("hire_meal", c, 5, "minecraft:cooked_beef", p -> {
			p.getFoodData().setFoodLevel(20);
			p.getFoodData().setSaturation(Math.max(p.getFoodData().getSaturationLevel(), 12));
		});
		service("hire_strength", u, 12, "minecraft:blaze_powder", effect(MobEffects.DAMAGE_BOOST, 300, 0));
		service("hire_guard", u, 12, "minecraft:shield", effect(MobEffects.DAMAGE_RESISTANCE, 300, 0));
		service("hire_regen", u, 12, "minecraft:ghast_tear", effect(MobEffects.REGENERATION, 60, 0));
		service("hire_fireward", u, 10, "minecraft:magma_cream", effect(MobEffects.FIRE_RESISTANCE, 480, 0));
		service("learning", u, 14, "minecraft:experience_bottle", p -> Progression.addXp(p, 60));
		service("hire_strength_2", r, 26, "minecraft:blaze_rod", effect(MobEffects.DAMAGE_BOOST, 180, 1));
		service("hire_guard_2", r, 26, "minecraft:iron_chestplate", effect(MobEffects.DAMAGE_RESISTANCE, 180, 1));
		service("hire_vigor", r, 24, "minecraft:glistering_melon_slice", effect(MobEffects.HEALTH_BOOST, 600, 1));
		service("learning_2", r, 30, "minecraft:enchanted_book", p -> Progression.addXp(p, 150));
		service("fortune", e, 45, "minecraft:gold_ingot", p -> luck(p, 3, false));
		service("aegis", e, 45, "minecraft:totem_of_undying", p -> {
			p.addEffect(new MobEffectInstance(MobEffects.ABSORPTION, 120 * 20, 3, true, true, true));
			p.addEffect(new MobEffectInstance(MobEffects.DAMAGE_RESISTANCE, 120 * 20, 0, true, true, true));
		});
		service("learning_3", e, 60, "minecraft:nether_star", p -> Progression.addXp(p, 400));
		service("blessing", l, 90, "minecraft:beacon", p -> {
			for (Holder<MobEffect> eff : List.of(MobEffects.DAMAGE_BOOST, MobEffects.DAMAGE_RESISTANCE, MobEffects.REGENERATION, MobEffects.MOVEMENT_SPEED)) {
				p.addEffect(new MobEffectInstance(eff, 120 * 20, 1, true, true, true));
			}
		});
		service("lucky_day", l, 100, "minecraft:emerald_block", p -> luck(p, 3, true));
	}

	/** Fortune: the next jobs pay 30% more tokens; a Lucky Day: twice the tokens and the experience. */
	private static void luck(ServerPlayer player, int jobs, boolean lucky) {
		Account a = Mercenary.account(player);
		if (lucky) {
			a.luckyJobs += jobs;
		} else {
			a.fortuneJobs += jobs;
		}
		Mercenary.save(player, a);
	}

	private Shop() {
	}

	public static Optional<Ware> ware(String id) {
		return CATALOG.stream().filter(w -> w.id().equals(id)).findFirst();
	}

	/** A Bargain takes 15% off everything. */
	public static int discounted(ServerPlayer player, int price) {
		return Talents.has(player, Talent.CON_BARGAIN) && price > 0 ? Math.max(1, (int) Math.ceil(price * 0.85f)) : price;
	}

	public static int price(ServerPlayer player, Account a, Ware ware) {
		int price = ware.price();
		if (a.deals.contains(ware.id())) {
			price = Math.max(1, Math.round(price * DEAL));
		}
		return discounted(player, price);
	}

	/** A rarity rolled on the odds of this race level. */
	public static Rarity rollRarity(RandomSource random, int level) {
		float total = 0;
		for (Rarity r : Rarity.values()) {
			total += r.weight(level);
		}
		float pick = random.nextFloat() * total;
		for (Rarity r : Rarity.values()) {
			pick -= r.weight(level);
			if (pick <= 0) {
				return r;
			}
		}
		return Rarity.COMMON;
	}

	/** A ware of this kind (any kind of goods for null) and the rolled rarity, or the nearest rarity there is; never one already out. */
	private static Ware pick(RandomSource random, Kind kind, Category category, Rarity rarity, List<String> out) {
		for (int step = 0; step < Rarity.values().length; step++) {
			for (int sign : new int[] {-1, 1}) {
				int index = rarity.ordinal() + sign * step;
				if (index < 0 || index >= Rarity.values().length) {
					continue;
				}
				Rarity r = Rarity.values()[index];
				List<Ware> pool = new ArrayList<>();
				for (Ware w : CATALOG) {
					if (w.kind() == kind && (category == null || w.category() == category) && w.rarity() == r && !out.contains(w.id())) {
						pool.add(w);
					}
				}
				if (!pool.isEmpty()) {
					return pool.get(random.nextInt(pool.size()));
				}
			}
		}
		return null;
	}

	/**
	 * Today's wares: one or two of every kind of goods and three more of any (two more for a Regular), four services;
	 * each one's rarity rolled on the race level's odds; one deal of the day (two for a Regular).
	 */
	public static void restock(ServerPlayer player, Account a) {
		RandomSource random = player.getRandom();
		int level = RaceState.level(player);
		boolean regular = Talents.has(player, Talent.CON_REGULAR);
		a.stock.clear();
		a.deals.clear();
		for (Category shelf : SHELVES) {
			int count = PER_KIND_MIN + random.nextInt(PER_KIND_MAX - PER_KIND_MIN + 1);
			for (int i = 0; i < count; i++) {
				Ware w = pick(random, Kind.GOODS, shelf, rollRarity(random, level), a.stock);
				if (w != null) {
					a.stock.add(w.id());
				}
			}
		}
		for (int i = 0; i < FREE_PICKS + (regular ? 2 : 0); i++) {
			Ware w = pick(random, Kind.GOODS, null, rollRarity(random, level), a.stock);
			if (w != null) {
				a.stock.add(w.id());
			}
		}
		List<String> services = new ArrayList<>();
		for (int i = 0; i < SERVICES; i++) {
			Ware w = pick(random, Kind.SERVICE, null, rollRarity(random, level), services);
			if (w != null) {
				services.add(w.id());
			}
		}
		a.stock.addAll(services);
		List<String> shuffled = new ArrayList<>(a.stock);
		java.util.Collections.shuffle(shuffled, new java.util.Random(random.nextLong()));
		shuffled.stream().limit(regular ? 2 : 1).forEach(a.deals::add);
	}

	/** How many of a listing are left today. */
	public static int left(Account a, Ware ware) {
		return Math.max(0, ware.rarity().stock - a.bought.getOrDefault(ware.id(), 0));
	}

	/** Why a ware can't be bought now (a translation key), or null. */
	public static String refusal(ServerPlayer player, Account a, Ware ware) {
		if (!a.stock.contains(ware.id())) {
			return "mages_legacy.merc.not_today";
		}
		if (left(a, ware) <= 0) {
			return "mages_legacy.merc.sold_out";
		}
		if (a.tokens < price(player, a, ware)) {
			return "mages_legacy.merc.no_tokens";
		}
		return null;
	}

	public static boolean buy(ServerPlayer player, Account a, String id) {
		Ware ware = ware(id).orElse(null);
		if (ware == null) {
			return false;
		}
		String why = refusal(player, a, ware);
		if (why != null) {
			player.displayClientMessage(Component.translatable(why).withStyle(ChatFormatting.RED), true);
			return false;
		}
		int price = price(player, a, ware);
		a.tokens -= price;
		a.spent += price;
		a.bought.merge(id, 1, Integer::sum);
		// The account is saved before a service runs: a service may write the account itself (Fortune)
		Mercenary.save(player, a);
		if (ware.goods() != null) {
			for (ItemStack stack : ware.goods().apply(player)) {
				give(player, stack);
			}
		} else {
			ware.service().accept(player);
		}
		Mercenary.spent(player, Mercenary.account(player), price);
		player.level().playSound(null, player.blockPosition(), SoundEvents.VILLAGER_TRADE, SoundSource.PLAYERS, 0.7f, 0.9f);
		player.level().playSound(null, player.blockPosition(), ware.rarity().ordinal() >= Rarity.EPIC.ordinal() ? SoundEvents.PLAYER_LEVELUP
				: SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.PLAYERS, 1, 1.4f);
		return true;
	}

	private static void give(ServerPlayer player, ItemStack stack) {
		if (!player.getInventory().add(stack)) {
			player.drop(stack, false);
		}
	}
}
