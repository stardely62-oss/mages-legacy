package euf.mageslegacy.mercenary;

import euf.mageslegacy.MagesLegacy;
import euf.mageslegacy.race.Progression;
import euf.mageslegacy.race.RaceData;
import euf.mageslegacy.race.Talents;
import java.util.List;
import java.util.Optional;
import java.util.function.Supplier;
import net.minecraft.entity.effect.StatusEffect;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;

/**
 * The Mage's Shop: what tokens buy. Services work on the Mercenary alone (race experience, a hired strength); goods
 * are real items anyone can use, so a Mercenary can equip a whole party. Some goods wait for a milestone tier.
 * Names and descriptions live under mages_legacy.shop.<id> and .desc.
 */
public final class Shop {
	public enum Kind { SERVICE, GOODS }

	/**
	 * One ware: its price in tokens, the tier it waits for, how many a day (0: no limit) and how much dearer each one
	 * bought today makes the next.
	 */
	public record Ware(String id, Kind kind, int price, int tier, int dailyMax, int step, Supplier<ItemStack> icon) {}

	/** Hired strengths last 5 minutes. */
	private static final int HIRE_TICKS = 6000;
	/** Race experience the Mage's Learning gives: dearer than a contract's, so the work stays the way to grow. */
	public static final int LEARNING_XP = 60;

	public static final List<Ware> CATALOG = List.of(
			new Ware("learning", Kind.SERVICE, 12, 1, 3, 6, () -> new ItemStack(Items.EXPERIENCE_BOTTLE)),
			new Ware("hire_strength", Kind.SERVICE, 10, 1, 0, 0, () -> new ItemStack(Items.BLAZE_POWDER)),
			new Ware("hire_guard", Kind.SERVICE, 12, 1, 0, 0, () -> new ItemStack(Items.SHIELD)),
			new Ware("hire_haste", Kind.SERVICE, 8, 1, 0, 0, () -> new ItemStack(Items.GOLDEN_PICKAXE)),
			new Ware("arrows", Kind.GOODS, 3, 1, 0, 0, () -> new ItemStack(Items.ARROW, 32)),
			new Ware("name_tag", Kind.GOODS, 6, 1, 0, 0, () -> new ItemStack(Items.NAME_TAG)),
			new Ware("saddle", Kind.GOODS, 6, 1, 0, 0, () -> new ItemStack(Items.SADDLE)),
			new Ware("pearls", Kind.GOODS, 10, 1, 0, 0, () -> new ItemStack(Items.ENDER_PEARL, 4)),
			new Ware("bottles", Kind.GOODS, 8, 1, 0, 0, () -> new ItemStack(Items.EXPERIENCE_BOTTLE, 8)),
			new Ware("golden_apple", Kind.GOODS, 15, 1, 0, 0, () -> new ItemStack(Items.GOLDEN_APPLE)),
			new Ware("oblivion", Kind.GOODS, 40, 1, 0, 0, () -> new ItemStack(MagesLegacy.OBLIVION_SCROLL)),
			new Ware("blaze_rods", Kind.GOODS, 10, 2, 0, 0, () -> new ItemStack(Items.BLAZE_ROD, 4)),
			new Ware("netherite_scrap", Kind.GOODS, 60, 4, 1, 0, () -> new ItemStack(Items.NETHERITE_SCRAP)));

	private Shop() {
	}

	public static Optional<Ware> ware(String id) {
		return CATALOG.stream().filter(w -> w.id().equals(id)).findFirst();
	}

	/** Trusted Man takes 15% off everything. */
	public static int discounted(RaceData data, int price) {
		return data.has(Talents.CON_DISCOUNT) ? (int) Math.ceil(price * 0.85f) : price;
	}

	/** What the ware costs this Mercenary now, with today's purchases and the discount. */
	public static int price(RaceData data, Ware ware) {
		return discounted(data, ware.price() + ware.step() * data.merc.bought.getOrDefault(ware.id(), 0));
	}

	public static boolean soldOut(RaceData data, Ware ware) {
		return ware.dailyMax() > 0 && data.merc.bought.getOrDefault(ware.id(), 0) >= ware.dailyMax();
	}

	public static boolean buy(ServerPlayerEntity player, RaceData data, String id) {
		Ware ware = ware(id).orElse(null);
		if (ware == null) {
			return false;
		}
		if (!Progression.tierOpen(player, ware.tier())) {
			player.sendMessage(Text.translatable("mages_legacy.merc.locked_ware"), true);
			return false;
		}
		if (soldOut(data, ware)) {
			player.sendMessage(Text.translatable("mages_legacy.merc.sold_out"), true);
			return false;
		}
		int price = price(data, ware);
		if (data.merc.tokens < price) {
			player.sendMessage(Text.translatable("mages_legacy.merc.no_tokens"), true);
			return false;
		}
		data.merc.tokens -= price;
		data.merc.bought.merge(id, 1, Integer::sum);
		data.dirty = true;
		switch (id) {
			case "learning" -> Progression.addXp(player, data, LEARNING_XP);
			case "hire_strength" -> hire(player, StatusEffects.STRENGTH, 0);
			case "hire_guard" -> hire(player, StatusEffects.RESISTANCE, 0);
			case "hire_haste" -> hire(player, StatusEffects.HASTE, 1);
			default -> player.getInventory().offerOrDrop(ware.icon().get());
		}
		player.playSound(SoundEvents.ENTITY_VILLAGER_TRADE, SoundCategory.PLAYERS, 0.7f, 0.9f);
		player.playSound(SoundEvents.BLOCK_AMETHYST_BLOCK_CHIME, SoundCategory.PLAYERS, 1.0f, 1.4f);
		return true;
	}

	private static void hire(ServerPlayerEntity player, StatusEffect effect, int amplifier) {
		player.addStatusEffect(new StatusEffectInstance(effect, HIRE_TICKS, amplifier, true, false, true));
	}
}
