package euf.mageslegacy.item;

import euf.mageslegacy.fx.Fx;
import euf.mageslegacy.mycelid.Mycelid;
import euf.mageslegacy.net.RacePackets;
import euf.mageslegacy.race.RaceData;
import euf.mageslegacy.race.RaceState;
import java.util.List;
import net.minecraft.client.item.TooltipContext;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.Hand;
import net.minecraft.util.TypedActionResult;
import net.minecraft.world.World;
import org.jetbrains.annotations.Nullable;

/**
 * The Scroll of Oblivion: read, it gives back to the nameless mage everything the race has learned, and every talent
 * point returns to be spent anew. The race, its level and its experience stay.
 */
public class OblivionScrollItem extends Item {
	private static final int GOLD_RGB = 0xE8C27A;

	public OblivionScrollItem(Settings settings) {
		super(settings);
	}

	@Override
	public TypedActionResult<ItemStack> use(World world, PlayerEntity user, Hand hand) {
		ItemStack stack = user.getStackInHand(hand);
		if (!(user instanceof ServerPlayerEntity player)) {
			return TypedActionResult.success(stack);
		}
		RaceData data = RaceState.of(player);
		if (data == null || data.race == null || data.talents.isEmpty()) {
			player.sendMessage(Text.translatable("mages_legacy.scroll.nothing"), true);
			return TypedActionResult.fail(stack);
		}
		int points = forget(player, data);
		if (!player.getAbilities().creativeMode) {
			stack.decrement(1);
		}
		ServerWorld here = player.getServerWorld();
		Fx.burst(here, player.getPos().add(0, 1, 0), 1.6f, 10, GOLD_RGB);
		here.spawnParticles(ParticleTypes.ENCHANT, player.getX(), player.getY() + 1.2, player.getZ(), 60, 0.6, 0.8, 0.6, 0.6);
		here.playSound(null, player.getBlockPos(), SoundEvents.ITEM_BOOK_PAGE_TURN, SoundCategory.PLAYERS, 1.0f, 0.7f);
		here.playSound(null, player.getBlockPos(), SoundEvents.BLOCK_ENCHANTMENT_TABLE_USE, SoundCategory.PLAYERS, 1.0f, 0.6f);
		player.sendMessage(Text.translatable("mages_legacy.scroll.done", points).formatted(Formatting.GOLD), false);
		return TypedActionResult.success(stack);
	}

	/** Forgets every talent the race has learned; returns how many points came back. */
	public static int forget(ServerPlayerEntity player, RaceData data) {
		int points = data.spentPoints();
		data.talents.clear();
		Mycelid.removeCapArmor(player);
		data.dirty = true;
		RacePackets.sync(player, data);
		return points;
	}

	@Override
	public void appendTooltip(ItemStack stack, @Nullable World world, List<Text> tooltip, TooltipContext context) {
		tooltip.add(Text.translatable("mages_legacy.scroll.lore").formatted(Formatting.GRAY, Formatting.ITALIC));
		tooltip.add(Text.translatable("mages_legacy.scroll.desc").formatted(Formatting.GOLD));
	}
}
