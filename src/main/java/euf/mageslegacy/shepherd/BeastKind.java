package euf.mageslegacy.shepherd;

import java.util.List;
import java.util.Optional;
import java.util.function.Predicate;
import net.minecraft.entity.EntityType;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.registry.tag.ItemTags;

/**
 * Beasts a Shepherd can bond with. Every one starts with the Song of Bonds, then wins its trust its own way
 * ({@link Taming}) in {@code steps} (feedings, songs, headbutts, kills, seconds); {@code food} is what that way needs
 * in hand, {@code capacity} the bond slots it takes, {@code treat} what it likes to eat once it's yours (feeding
 * heals it and keeps it fed). Texts: mages_legacy.beast.<id>, .skill (short), .skill_desc, .taming and .treat.
 */
public record BeastKind(String id, EntityType<?> type, Taming taming, int steps, Predicate<ItemStack> food, int capacity, Item icon,
		Predicate<ItemStack> treat) {
	/** How a beast is won over after the Song. */
	public enum Taming {
		/** Feed it, a minute apart, the last time by a campfire (the wolf). */
		FEED_BY_FIRE,
		/** Crouch beside it with its food in hand for some seconds (the cat). */
		PATIENCE,
		/** Sing to it while it sleeps, then feed it once (the fox). */
		ASLEEP,
		/** Bring it different kinds of its food (the bee: flowers). */
		KINDS,
		/** Butt heads with it bare-handed (no harm done), then feed it (the goat). */
		HEADBUTTS,
		/** Kill monsters near it: it respects strength (the polar bear). */
		HUNT,
		/** Sing to it, a minute apart (the parrot: by a playing jukebox; the bat: underground). */
		SONGS,
		/** Feed it, a minute apart (the frog, the turtle, the rabbit). */
		FEED,
		/** Walk it on a lead for a while (the llama). */
		LEAD,
		/** Feed it while you swim beside it, a minute apart (the axolotl, the dolphin). */
		WATER_GIFT
	}

	public static final List<BeastKind> ALL = List.of(
			new BeastKind("wolf", EntityType.WOLF, Taming.FEED_BY_FIRE, 4, s -> s.isFood() && s.getItem().getFoodComponent().isMeat(), 2, Items.WOLF_SPAWN_EGG,
					s -> s.isFood() && s.getItem().getFoodComponent().isMeat()),
			new BeastKind("cat", EntityType.CAT, Taming.PATIENCE, 20, s -> s.isOf(Items.COD) || s.isOf(Items.SALMON), 1, Items.CAT_SPAWN_EGG,
					s -> s.isOf(Items.COD) || s.isOf(Items.SALMON)),
			new BeastKind("fox", EntityType.FOX, Taming.ASLEEP, 1, s -> s.isOf(Items.GLOW_BERRIES), 1, Items.FOX_SPAWN_EGG,
					s -> s.isOf(Items.SWEET_BERRIES) || s.isOf(Items.GLOW_BERRIES)),
			new BeastKind("bee", EntityType.BEE, Taming.KINDS, 3, s -> s.isIn(ItemTags.FLOWERS), 1, Items.BEE_SPAWN_EGG,
					s -> s.isIn(ItemTags.FLOWERS)),
			new BeastKind("axolotl", EntityType.AXOLOTL, Taming.WATER_GIFT, 2, s -> s.isOf(Items.TROPICAL_FISH_BUCKET), 1, Items.AXOLOTL_SPAWN_EGG,
					s -> s.isOf(Items.TROPICAL_FISH) || s.isOf(Items.TROPICAL_FISH_BUCKET)),
			new BeastKind("goat", EntityType.GOAT, Taming.HEADBUTTS, 5, s -> s.isOf(Items.WHEAT), 2, Items.GOAT_SPAWN_EGG,
					s -> s.isOf(Items.WHEAT)),
			new BeastKind("polar_bear", EntityType.POLAR_BEAR, Taming.HUNT, 5, s -> false, 3, Items.POLAR_BEAR_SPAWN_EGG,
					s -> s.isOf(Items.COD) || s.isOf(Items.SALMON)),
			new BeastKind("parrot", EntityType.PARROT, Taming.SONGS, 3, s -> false, 1, Items.PARROT_SPAWN_EGG,
					s -> s.isOf(Items.WHEAT_SEEDS) || s.isOf(Items.MELON_SEEDS) || s.isOf(Items.PUMPKIN_SEEDS) || s.isOf(Items.BEETROOT_SEEDS)),
			new BeastKind("frog", EntityType.FROG, Taming.FEED, 3, s -> s.isOf(Items.SLIME_BALL), 1, Items.FROG_SPAWN_EGG,
					s -> s.isOf(Items.SLIME_BALL)),
			new BeastKind("llama", EntityType.LLAMA, Taming.LEAD, 120, s -> false, 2, Items.LLAMA_SPAWN_EGG,
					s -> s.isOf(Items.WHEAT) || s.isOf(Items.HAY_BLOCK)),
			new BeastKind("rabbit", EntityType.RABBIT, Taming.FEED, 2, s -> s.isOf(Items.GOLDEN_CARROT), 1, Items.RABBIT_SPAWN_EGG,
					s -> s.isOf(Items.CARROT) || s.isOf(Items.GOLDEN_CARROT) || s.isOf(Items.DANDELION)),
			new BeastKind("turtle", EntityType.TURTLE, Taming.FEED, 3, s -> s.isOf(Items.SEAGRASS), 1, Items.TURTLE_SPAWN_EGG,
					s -> s.isOf(Items.SEAGRASS)),
			new BeastKind("dolphin", EntityType.DOLPHIN, Taming.WATER_GIFT, 3, s -> s.isOf(Items.COD) || s.isOf(Items.SALMON), 2, Items.DOLPHIN_SPAWN_EGG,
					s -> s.isOf(Items.COD) || s.isOf(Items.SALMON)),
			new BeastKind("bat", EntityType.BAT, Taming.SONGS, 3, s -> false, 1, Items.BAT_SPAWN_EGG,
					s -> s.isOf(Items.SWEET_BERRIES) || s.isOf(Items.GLOW_BERRIES)));

	public static Optional<BeastKind> of(EntityType<?> type) {
		return ALL.stream().filter(k -> k.type() == type).findFirst();
	}

	public static Optional<BeastKind> byId(String id) {
		return ALL.stream().filter(k -> k.id().equals(id)).findFirst();
	}
}
