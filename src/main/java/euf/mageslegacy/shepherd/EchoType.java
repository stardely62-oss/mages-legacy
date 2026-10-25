package euf.mageslegacy.shepherd;

import java.util.List;
import java.util.Optional;
import net.minecraft.entity.EntityType;
import net.minecraft.item.Item;
import net.minecraft.item.Items;
import net.minecraft.registry.Registries;

/**
 * The echoes a Shepherd can learn: kill enough of a kind and its echo answers the Call. {@code kills} unlock it,
 * {@code capacity} is the bond slots it takes while it walks with you, {@code voice} what the Call costs.
 */
public record EchoType(EntityType<?> type, int kills, int capacity, int voice, Item icon) {
	public static final List<EchoType> ALL = List.of(
			new EchoType(EntityType.ZOMBIE, 20, 1, 15, Items.ZOMBIE_SPAWN_EGG),
			new EchoType(EntityType.HUSK, 15, 1, 15, Items.HUSK_SPAWN_EGG),
			new EchoType(EntityType.DROWNED, 15, 1, 15, Items.DROWNED_SPAWN_EGG),
			new EchoType(EntityType.SKELETON, 25, 2, 25, Items.SKELETON_SPAWN_EGG),
			new EchoType(EntityType.STRAY, 15, 2, 25, Items.STRAY_SPAWN_EGG),
			new EchoType(EntityType.SPIDER, 20, 2, 20, Items.SPIDER_SPAWN_EGG),
			new EchoType(EntityType.CAVE_SPIDER, 15, 1, 15, Items.CAVE_SPIDER_SPAWN_EGG),
			new EchoType(EntityType.VINDICATOR, 10, 3, 35, Items.VINDICATOR_SPAWN_EGG),
			new EchoType(EntityType.BLAZE, 15, 3, 35, Items.BLAZE_SPAWN_EGG),
			new EchoType(EntityType.WITHER_SKELETON, 10, 4, 45, Items.WITHER_SKELETON_SPAWN_EGG));

	public String id() {
		return Registries.ENTITY_TYPE.getId(type).toString();
	}

	/** Kills needed; Memory of the Fallen lowers it by 40%. */
	public int killsNeeded(boolean memory) {
		return memory ? Math.max(1, Math.round(kills * 0.6f)) : kills;
	}

	/** Voice the Call costs; Quiet Call takes a quarter off. */
	public int voiceCost(boolean quiet) {
		return quiet ? Math.round(voice * 0.75f) : voice;
	}

	public static Optional<EchoType> byId(String id) {
		return ALL.stream().filter(e -> e.id().equals(id)).findFirst();
	}

	public static Optional<EchoType> of(EntityType<?> type) {
		return ALL.stream().filter(e -> e.type() == type).findFirst();
	}
}
