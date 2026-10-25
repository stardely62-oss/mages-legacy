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
 * ({@link Taming}); {@code food} is what that way needs in hand, {@code capacity} the bond slots it takes. Texts:
 * mages_legacy.beast.<id>, .skill (short), .skill_desc and .taming.
 */
public record BeastKind(String id, EntityType<?> type, Taming taming, Predicate<ItemStack> food, int capacity, Item icon) {
	/** How a beast is won over after the Song. */
	public enum Taming {
		/** Feed it three times, a minute apart, the last time by a campfire (the wolf). */
		FEED_BY_FIRE,
		/** Crouch beside it with its food in hand for ten seconds (the cat). */
		PATIENCE,
		/** Sing to it while it sleeps, then feed it once (the fox). */
		ASLEEP,
		/** Bring it two different kinds of its food (the bee: two flowers). */
		TWO_KINDS,
		/** One gift is enough (the axolotl). */
		ONE_GIFT,
		/** Butt heads with it three times bare-handed (no harm done), then feed it (the goat). */
		HEADBUTTS,
		/** Kill three monsters near it: it respects strength (the polar bear). */
		HUNT,
		/** Sing to it three times, a minute apart (the parrot). */
		SONGS,
		/** Feed it twice, a minute apart (the frog, the turtle). */
		FEED_TWICE,
		/** Walk it on a lead for a minute (the llama). */
		LEAD,
		/** Give it its food while you swim beside it (the dolphin). */
		WATER_GIFT,
		/** Sing to it twice in the dark (the bat). */
		DARK_SONGS
	}

	public static final List<BeastKind> ALL = List.of(
			new BeastKind("wolf", EntityType.WOLF, Taming.FEED_BY_FIRE, s -> s.isFood() && s.getItem().getFoodComponent().isMeat(), 2, Items.WOLF_SPAWN_EGG),
			new BeastKind("cat", EntityType.CAT, Taming.PATIENCE, s -> s.isOf(Items.COD) || s.isOf(Items.SALMON), 1, Items.CAT_SPAWN_EGG),
			new BeastKind("fox", EntityType.FOX, Taming.ASLEEP, s -> s.isOf(Items.SWEET_BERRIES) || s.isOf(Items.GLOW_BERRIES), 1, Items.FOX_SPAWN_EGG),
			new BeastKind("bee", EntityType.BEE, Taming.TWO_KINDS, s -> s.isIn(ItemTags.FLOWERS), 1, Items.BEE_SPAWN_EGG),
			new BeastKind("axolotl", EntityType.AXOLOTL, Taming.ONE_GIFT, s -> s.isOf(Items.TROPICAL_FISH_BUCKET), 1, Items.AXOLOTL_SPAWN_EGG),
			new BeastKind("goat", EntityType.GOAT, Taming.HEADBUTTS, s -> s.isOf(Items.WHEAT), 2, Items.GOAT_SPAWN_EGG),
			new BeastKind("polar_bear", EntityType.POLAR_BEAR, Taming.HUNT, s -> false, 3, Items.POLAR_BEAR_SPAWN_EGG),
			new BeastKind("parrot", EntityType.PARROT, Taming.SONGS, s -> false, 1, Items.PARROT_SPAWN_EGG),
			new BeastKind("frog", EntityType.FROG, Taming.FEED_TWICE, s -> s.isOf(Items.SLIME_BALL), 1, Items.FROG_SPAWN_EGG),
			new BeastKind("llama", EntityType.LLAMA, Taming.LEAD, s -> false, 2, Items.LLAMA_SPAWN_EGG),
			new BeastKind("rabbit", EntityType.RABBIT, Taming.ONE_GIFT, s -> s.isOf(Items.GOLDEN_CARROT), 1, Items.RABBIT_SPAWN_EGG),
			new BeastKind("turtle", EntityType.TURTLE, Taming.FEED_TWICE, s -> s.isOf(Items.SEAGRASS), 1, Items.TURTLE_SPAWN_EGG),
			new BeastKind("dolphin", EntityType.DOLPHIN, Taming.WATER_GIFT, s -> s.isOf(Items.COD) || s.isOf(Items.SALMON), 2, Items.DOLPHIN_SPAWN_EGG),
			new BeastKind("bat", EntityType.BAT, Taming.DARK_SONGS, s -> false, 1, Items.BAT_SPAWN_EGG));

	public static Optional<BeastKind> of(EntityType<?> type) {
		return ALL.stream().filter(k -> k.type() == type).findFirst();
	}

	public static Optional<BeastKind> byId(String id) {
		return ALL.stream().filter(k -> k.id().equals(id)).findFirst();
	}
}
