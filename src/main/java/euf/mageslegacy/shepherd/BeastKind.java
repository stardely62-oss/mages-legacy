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
 * Beasts a Shepherd can bond with: the food they love (fed during the ritual), the bond slots they take and the skill
 * a bonded one brings. Texts: mages_legacy.beast.<id> and .skill.
 */
public record BeastKind(String id, EntityType<?> type, Predicate<ItemStack> food, int capacity, Item icon) {
	public static final List<BeastKind> ALL = List.of(
			new BeastKind("wolf", EntityType.WOLF, s -> s.isFood() && s.getItem().getFoodComponent().isMeat(), 2, Items.WOLF_SPAWN_EGG),
			new BeastKind("cat", EntityType.CAT, s -> s.isOf(Items.COD) || s.isOf(Items.SALMON), 1, Items.CAT_SPAWN_EGG),
			new BeastKind("fox", EntityType.FOX, s -> s.isOf(Items.SWEET_BERRIES) || s.isOf(Items.GLOW_BERRIES), 1, Items.FOX_SPAWN_EGG),
			new BeastKind("bee", EntityType.BEE, s -> s.isIn(ItemTags.FLOWERS), 1, Items.BEE_SPAWN_EGG),
			new BeastKind("axolotl", EntityType.AXOLOTL, s -> s.isOf(Items.TROPICAL_FISH_BUCKET), 1, Items.AXOLOTL_SPAWN_EGG));

	public static Optional<BeastKind> of(EntityType<?> type) {
		return ALL.stream().filter(k -> k.type() == type).findFirst();
	}

	public static Optional<BeastKind> byId(String id) {
		return ALL.stream().filter(k -> k.id().equals(id)).findFirst();
	}
}
