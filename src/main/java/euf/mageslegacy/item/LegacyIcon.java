package euf.mageslegacy.item;

import euf.mageslegacy.MagesLegacy;
import net.minecraft.world.item.Item;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;

/**
 * The picture of an advancement of the Mage's Legacy tab: one item, never in a creative tab or a recipe, whose model
 * picks the advancement's painted icon by its custom model data (see tools/advancements.py).
 */
public final class LegacyIcon {
	private static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(MagesLegacy.MOD_ID);
	public static final DeferredItem<Item> ICON = ITEMS.registerSimpleItem("legacy_icon", new Item.Properties().stacksTo(1));

	private LegacyIcon() {
	}

	public static void register(IEventBus modBus) {
		ITEMS.register(modBus);
	}
}
