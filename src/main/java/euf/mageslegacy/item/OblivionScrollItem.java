package euf.mageslegacy.item;

import euf.mageslegacy.MagesLegacy;
import euf.mageslegacy.fx.Fx;
import euf.mageslegacy.race.RaceState;
import euf.mageslegacy.race.Talents;
import java.util.List;
import net.minecraft.ChatFormatting;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.event.BuildCreativeModeTabContentsEvent;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;

/**
 * The Scroll of Oblivion: read, it gives back to the nameless mage everything the race has learnt, and every talent
 * point returns to be spent anew. The race, its level and its Awakenings stay.
 */
public class OblivionScrollItem extends Item {
	private static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(MagesLegacy.MOD_ID);
	public static final DeferredItem<OblivionScrollItem> SCROLL = ITEMS.register("oblivion_scroll",
			() -> new OblivionScrollItem(new Item.Properties().stacksTo(16)));

	public OblivionScrollItem(Properties properties) {
		super(properties);
	}

	public static void register(IEventBus modBus) {
		ITEMS.register(modBus);
		modBus.addListener((BuildCreativeModeTabContentsEvent event) -> {
			if (event.getTabKey() == CreativeModeTabs.TOOLS_AND_UTILITIES) {
				event.accept(SCROLL.get());
			}
		});
	}

	@Override
	public InteractionResultHolder<ItemStack> use(Level level, Player user, InteractionHand hand) {
		ItemStack stack = user.getItemInHand(hand);
		if (!(user instanceof ServerPlayer player)) {
			return InteractionResultHolder.success(stack);
		}
		int points = Talents.spent(Talents.owned(player)) - Talents.spent(euf.mageslegacy.race.Awakenings.owned(Talents.owned(player)).stream()
				.map(t -> t.id()).toList());
		if (RaceState.get(player).isEmpty() || points <= 0) {
			player.displayClientMessage(Component.translatable("mages_legacy.scroll.nothing"), true);
			return InteractionResultHolder.fail(stack);
		}
		Talents.reset(player);
		stack.consume(1, player);
		ServerLevel here = player.serverLevel();
		Fx.burst(here, player.position().add(0, 1, 0), 1.6f, 10, false);
		here.sendParticles(ParticleTypes.ENCHANT, player.getX(), player.getY() + 1.2, player.getZ(), 60, 0.6, 0.8, 0.6, 0.6);
		here.playSound(null, player.blockPosition(), SoundEvents.BOOK_PAGE_TURN, SoundSource.PLAYERS, 1.0f, 0.7f);
		here.playSound(null, player.blockPosition(), SoundEvents.ENCHANTMENT_TABLE_USE, SoundSource.PLAYERS, 1.0f, 0.6f);
		player.displayClientMessage(Component.translatable("mages_legacy.scroll.done", points).withStyle(ChatFormatting.GOLD), false);
		return InteractionResultHolder.success(stack);
	}

	@Override
	public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
		tooltip.add(Component.translatable("mages_legacy.scroll.lore").withStyle(ChatFormatting.GRAY, ChatFormatting.ITALIC));
		tooltip.add(Component.translatable("mages_legacy.scroll.desc").withStyle(ChatFormatting.GOLD));
	}
}
