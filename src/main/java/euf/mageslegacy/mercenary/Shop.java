package euf.mageslegacy.mercenary;

import euf.mageslegacy.item.OblivionScrollItem;
import euf.mageslegacy.race.Progression;
import euf.mageslegacy.race.Talent;
import euf.mageslegacy.race.Talents;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.function.Supplier;
import net.minecraft.ChatFormatting;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.item.EnchantedBookItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.alchemy.PotionContents;
import net.minecraft.world.item.alchemy.Potions;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.EnchantmentInstance;
import net.minecraft.world.item.enchantment.Enchantments;

/**
 * The Mage's Shop: what tokens buy. Services (always there) work on the Mercenary alone; goods are real items anyone can
 * use. Each day the Shop puts out {@link #STOCK} goods of the open tiers, one of them the deal of the day (two for a
 * Regular). Every price, limit and balance is the server's: a purchase is checked again here, paid and handed over in
 * one call.
 */
public final class Shop {
	public enum Kind { SERVICE, GOODS }

	/** What a ware is, for the book's sorting and its "new" marks. */
	public enum Category {
		BOOSTS, SUPPLIES, MATERIALS, POTIONS, BOOKS, RARE;

		public String id() {
			return name().toLowerCase(Locale.ROOT);
		}
	}

	/** One ware: its price in tokens, the tier it waits for, how many a day (0: no limit), how much dearer each one today. */
	public record Ware(String id, Kind kind, Category category, int price, int tier, int dailyMax, int step, Supplier<ItemStack> icon) {
	}

	private static final int HIRE_TICKS = 5 * 60 * 20;
	public static final int LEARNING_XP = 60;
	public static final int STOCK = 7;
	private static final float DEAL = 0.7f;

	public static final List<Ware> CATALOG = List.of(
			new Ware("learning", Kind.SERVICE, Category.BOOSTS, 12, 1, 3, 6, () -> new ItemStack(Items.EXPERIENCE_BOTTLE)),
			new Ware("hire_strength", Kind.SERVICE, Category.BOOSTS, 10, 1, 0, 0, () -> new ItemStack(Items.BLAZE_POWDER)),
			new Ware("hire_guard", Kind.SERVICE, Category.BOOSTS, 12, 1, 0, 0, () -> new ItemStack(Items.SHIELD)),
			new Ware("hire_haste", Kind.SERVICE, Category.BOOSTS, 8, 1, 0, 0, () -> new ItemStack(Items.GOLDEN_PICKAXE)),
			new Ware("arrows", Kind.GOODS, Category.SUPPLIES, 3, 1, 0, 0, () -> new ItemStack(Items.ARROW, 32)),
			new Ware("spectral_arrows", Kind.GOODS, Category.SUPPLIES, 5, 1, 0, 0, () -> new ItemStack(Items.SPECTRAL_ARROW, 16)),
			new Ware("torches", Kind.GOODS, Category.SUPPLIES, 2, 1, 0, 0, () -> new ItemStack(Items.TORCH, 32)),
			new Ware("steak", Kind.GOODS, Category.SUPPLIES, 4, 1, 0, 0, () -> new ItemStack(Items.COOKED_BEEF, 16)),
			new Ware("leads", Kind.GOODS, Category.SUPPLIES, 4, 1, 0, 0, () -> new ItemStack(Items.LEAD, 2)),
			new Ware("name_tag", Kind.GOODS, Category.SUPPLIES, 6, 1, 0, 0, () -> new ItemStack(Items.NAME_TAG)),
			new Ware("saddle", Kind.GOODS, Category.SUPPLIES, 6, 1, 0, 0, () -> new ItemStack(Items.SADDLE)),
			new Ware("iron", Kind.GOODS, Category.MATERIALS, 6, 1, 0, 0, () -> new ItemStack(Items.IRON_INGOT, 8)),
			new Ware("copper", Kind.GOODS, Category.MATERIALS, 4, 1, 0, 0, () -> new ItemStack(Items.COPPER_INGOT, 16)),
			new Ware("pearls", Kind.GOODS, Category.MATERIALS, 10, 1, 0, 0, () -> new ItemStack(Items.ENDER_PEARL, 4)),
			new Ware("bottles", Kind.GOODS, Category.MATERIALS, 8, 1, 0, 0, () -> new ItemStack(Items.EXPERIENCE_BOTTLE, 8)),
			new Ware("healing", Kind.GOODS, Category.POTIONS, 8, 1, 0, 0, () -> PotionContents.createItemStack(Items.POTION, Potions.STRONG_HEALING)),
			new Ware("swiftness", Kind.GOODS, Category.POTIONS, 6, 1, 0, 0, () -> PotionContents.createItemStack(Items.POTION, Potions.LONG_SWIFTNESS)),
			new Ware("golden_apple", Kind.GOODS, Category.SUPPLIES, 15, 1, 0, 0, () -> new ItemStack(Items.GOLDEN_APPLE)),
			new Ware("oblivion", Kind.GOODS, Category.RARE, 40, 1, 1, 0, () -> new ItemStack(OblivionScrollItem.SCROLL.get())),
			new Ware("blaze_rods", Kind.GOODS, Category.MATERIALS, 10, 2, 0, 0, () -> new ItemStack(Items.BLAZE_ROD, 4)),
			new Ware("fire_resistance", Kind.GOODS, Category.POTIONS, 10, 2, 0, 0,
					() -> PotionContents.createItemStack(Items.POTION, Potions.LONG_FIRE_RESISTANCE)),
			new Ware("night_vision", Kind.GOODS, Category.POTIONS, 8, 2, 0, 0, () -> PotionContents.createItemStack(Items.POTION, Potions.LONG_NIGHT_VISION)),
			new Ware("ghast_tears", Kind.GOODS, Category.MATERIALS, 12, 2, 0, 0, () -> new ItemStack(Items.GHAST_TEAR, 2)),
			new Ware("diamonds", Kind.GOODS, Category.MATERIALS, 18, 2, 1, 0, () -> new ItemStack(Items.DIAMOND, 2)),
			new Ware("book", Kind.GOODS, Category.BOOKS, 20, 2, 1, 0, () -> new ItemStack(Items.ENCHANTED_BOOK)),
			new Ware("strength_potion", Kind.GOODS, Category.POTIONS, 14, 3, 0, 0, () -> PotionContents.createItemStack(Items.POTION, Potions.STRONG_STRENGTH)),
			new Ware("ender_eyes", Kind.GOODS, Category.MATERIALS, 14, 3, 0, 0, () -> new ItemStack(Items.ENDER_EYE, 4)),
			new Ware("rare_book", Kind.GOODS, Category.BOOKS, 32, 3, 1, 0, () -> new ItemStack(Items.ENCHANTED_BOOK)),
			new Ware("shulker_shells", Kind.GOODS, Category.RARE, 30, 3, 1, 0, () -> new ItemStack(Items.SHULKER_SHELL, 2)),
			new Ware("totem", Kind.GOODS, Category.RARE, 45, 3, 1, 0, () -> new ItemStack(Items.TOTEM_OF_UNDYING)),
			new Ware("netherite_scrap", Kind.GOODS, Category.RARE, 60, 4, 1, 0, () -> new ItemStack(Items.NETHERITE_SCRAP)),
			new Ware("enchanted_apple", Kind.GOODS, Category.RARE, 80, 4, 1, 0, () -> new ItemStack(Items.ENCHANTED_GOLDEN_APPLE)));

	/** A book of the Shop: a useful enchantment at a good level, never the best of the best. */
	private static final List<Object[]> BOOKS = List.of(new Object[] {Enchantments.UNBREAKING, 3}, new Object[] {Enchantments.SHARPNESS, 4},
			new Object[] {Enchantments.PROTECTION, 3}, new Object[] {Enchantments.EFFICIENCY, 4}, new Object[] {Enchantments.FORTUNE, 2},
			new Object[] {Enchantments.LOOTING, 2}, new Object[] {Enchantments.POWER, 4}, new Object[] {Enchantments.FEATHER_FALLING, 4},
			new Object[] {Enchantments.RESPIRATION, 2}, new Object[] {Enchantments.QUICK_CHARGE, 2});
	private static final List<Object[]> RARE_BOOKS = List.of(new Object[] {Enchantments.MENDING, 1}, new Object[] {Enchantments.SILK_TOUCH, 1},
			new Object[] {Enchantments.SHARPNESS, 5}, new Object[] {Enchantments.PROTECTION, 4}, new Object[] {Enchantments.FORTUNE, 3},
			new Object[] {Enchantments.LOOTING, 3}, new Object[] {Enchantments.INFINITY, 1});

	private Shop() {
	}

	public static Optional<Ware> ware(String id) {
		return CATALOG.stream().filter(w -> w.id().equals(id)).findFirst();
	}

	/** A Bargain takes 15% off everything. */
	public static int discounted(ServerPlayer player, int price) {
		return Talents.has(player, Talent.CON_BARGAIN) && price > 0 ? Math.max(1, (int) Math.ceil(price * 0.85f)) : price;
	}

	/** What a ware costs this Mercenary now: today's purchases, the deal of the day, the Bargain. */
	public static int price(ServerPlayer player, Account a, Ware ware) {
		int price = ware.price() + ware.step() * a.bought.getOrDefault(ware.id(), 0);
		if (a.deals.contains(ware.id())) {
			price = Math.max(1, Math.round(price * DEAL));
		}
		return discounted(player, price);
	}

	public static boolean tierOpen(ServerPlayer player, int tier) {
		return Progression.openTiers(player) >= tier;
	}

	/** Today's goods: a fresh pick from the open tiers (two more for a Regular), the deal(s) of the day among them. */
	public static void restock(ServerPlayer player, Account a) {
		List<Ware> pool = new ArrayList<>();
		for (Ware w : CATALOG) {
			if (w.kind() == Kind.GOODS && tierOpen(player, w.tier())) {
				pool.add(w);
			}
		}
		java.util.Collections.shuffle(pool, new java.util.Random(player.getRandom().nextLong()));
		boolean regular = Talents.has(player, Talent.CON_REGULAR);
		a.stock.clear();
		a.deals.clear();
		pool.stream().limit(STOCK + (regular ? 2 : 0)).forEach(w -> a.stock.add(w.id()));
		List<String> shuffled = new ArrayList<>(a.stock);
		java.util.Collections.shuffle(shuffled, new java.util.Random(player.getRandom().nextLong()));
		shuffled.stream().limit(regular ? 2 : 1).forEach(a.deals::add);
	}

	public static boolean soldOut(Account a, Ware ware) {
		return ware.dailyMax() > 0 && a.bought.getOrDefault(ware.id(), 0) >= ware.dailyMax();
	}

	/** Why a ware can't be bought now (a translation key), or null. */
	public static String refusal(ServerPlayer player, Account a, Ware ware) {
		if (ware.kind() == Kind.GOODS && !a.stock.contains(ware.id())) {
			return "mages_legacy.merc.not_today";
		}
		if (!tierOpen(player, ware.tier())) {
			return "mages_legacy.merc.locked_ware";
		}
		if (soldOut(a, ware)) {
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
		switch (id) {
			case "learning" -> Progression.addXp(player, LEARNING_XP);
			case "hire_strength" -> hire(player, MobEffects.DAMAGE_BOOST, 0);
			case "hire_guard" -> hire(player, MobEffects.DAMAGE_RESISTANCE, 0);
			case "hire_haste" -> hire(player, MobEffects.DIG_SPEED, 1);
			case "book" -> give(player, book(player, BOOKS));
			case "rare_book" -> give(player, book(player, RARE_BOOKS));
			default -> give(player, ware.icon().get());
		}
		Mercenary.spent(player, a, price);
		player.level().playSound(null, player.blockPosition(), SoundEvents.VILLAGER_TRADE, SoundSource.PLAYERS, 0.7f, 0.9f);
		player.level().playSound(null, player.blockPosition(), SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.PLAYERS, 1, 1.4f);
		return true;
	}

	@SuppressWarnings("unchecked")
	private static ItemStack book(ServerPlayer player, List<Object[]> from) {
		Object[] pick = from.get(player.getRandom().nextInt(from.size()));
		var registry = player.registryAccess().registryOrThrow(Registries.ENCHANTMENT);
		Holder<Enchantment> enchantment = registry.getHolderOrThrow((ResourceKey<Enchantment>) pick[0]);
		return EnchantedBookItem.createForEnchantment(new EnchantmentInstance(enchantment, (Integer) pick[1]));
	}

	private static void give(ServerPlayer player, ItemStack stack) {
		if (!player.getInventory().add(stack)) {
			player.drop(stack, false);
		}
	}

	private static void hire(ServerPlayer player, Holder<MobEffect> effect, int amplifier) {
		player.addEffect(new MobEffectInstance(effect, HIRE_TICKS, amplifier, true, false, true));
	}
}
