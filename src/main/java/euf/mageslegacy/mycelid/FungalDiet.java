package euf.mageslegacy.mycelid;

import euf.mageslegacy.MagesLegacy;
import euf.mageslegacy.race.Race;
import euf.mageslegacy.race.RaceState;
import euf.mageslegacy.race.Talent;
import euf.mageslegacy.race.Talents;
import java.util.function.Predicate;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.stats.Stats;
import net.minecraft.tags.TagKey;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.living.LivingEntityUseItemEvent;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;

/**
 * What a Mycelid eats. Raw mushrooms and fungi (the tag {@code mages_legacy:edible_mushrooms}, other mods' through
 * {@code c:mushrooms}) are food for a Mycelid alone, from level 1: eaten the vanilla way (the eating animation, sounds
 * and length come from {@code ItemMixin}, which only describes them; only a Mycelid ever starts eating one), 5 food
 * and 3 saturation each, not when full. Everyone else still places them, and a click on a node still feeds the
 * colony (the node takes the click first). The fungal diet's talents act once per meal, here alone: Fungal Diet +3
 * food, Spore Digestion +15 Spores, Living Recovery 4 health once in 30 seconds (kept on the player).
 */
public final class FungalDiet {
	public static final TagKey<Item> EDIBLE = TagKey.create(Registries.ITEM, MagesLegacy.id("edible_mushrooms"));
	public static final int EAT_TICKS = 32;
	private static final int RAW_FOOD = 5;
	/** Saturation = food x modifier x 2: 3 for a raw mushroom. */
	private static final float RAW_SATURATION = 0.3f;
	private static final int RECOVERY = 30 * 20;
	private static final String RECOVERED = "diet_heal_at";
	/** On the client: whether this is the local player and a Mycelid (set by the client, which knows its race). */
	public static Predicate<Player> clientMycelid = p -> false;

	private FungalDiet() {
	}

	public static void register() {
		NeoForge.EVENT_BUS.addListener(FungalDiet::onUse);
		NeoForge.EVENT_BUS.addListener(FungalDiet::onNode);
		NeoForge.EVENT_BUS.addListener(FungalDiet::onEat);
	}

	/** A raw mushroom or fungus that isn't food for everyone already. */
	public static boolean raw(ItemStack stack) {
		return !stack.isEmpty() && stack.is(EDIBLE) && stack.get(DataComponents.FOOD) == null;
	}

	private static boolean mycelid(Player player) {
		return player.level().isClientSide ? clientMycelid.test(player) : player instanceof ServerPlayer sp && RaceState.is(sp, Race.MYCELID);
	}

	/**
	 * Shift + right click on a node with something organic gives it the whole stack: vanilla skips a block's own use
	 * while sneaking with an item in hand, so the node is let use it anyway.
	 */
	private static void onNode(PlayerInteractEvent.RightClickBlock event) {
		if (event.getLevel().getBlockState(event.getPos()).is(MyceliumNodeBlock.NODE.get()) && MyceliumNetwork.nutritionOf(event.getItemStack()) > 0) {
			event.setUseBlock(net.neoforged.neoforge.common.util.TriState.TRUE);
		}
	}

	/** A right click in the air (or where it can't be placed) with a raw mushroom: a hungry Mycelid starts eating it. */
	private static void onUse(PlayerInteractEvent.RightClickItem event) {
		Player player = event.getEntity();
		ItemStack stack = event.getItemStack();
		if (!raw(stack) || !mycelid(player) || !player.canEat(false)) {
			return;
		}
		player.startUsingItem(event.getHand());
		event.setCanceled(true);
		event.setCancellationResult(InteractionResult.CONSUME);
	}

	/** A meal finished: a raw mushroom eaten; then the diet's talents for anything fungal, once. */
	private static void onEat(LivingEntityUseItemEvent.Finish event) {
		if (!(event.getEntity() instanceof ServerPlayer player) || !RaceState.is(player, Race.MYCELID)) {
			return;
		}
		ItemStack eaten = event.getItem();
		boolean raw = raw(eaten);
		if (!raw && !eaten.is(MyceliumNetwork.FUNGAL_FOOD)) {
			return;
		}
		if (raw) {
			ItemStack left = event.getResultStack();
			player.getFoodData().eat(RAW_FOOD, RAW_SATURATION);
			player.awardStat(Stats.ITEM_USED.get(eaten.getItem()));
			left.consume(1, player);
			event.setResultStack(left);
			player.level().playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.PLAYER_BURP, SoundSource.PLAYERS, 0.5f,
					player.getRandom().nextFloat() * 0.1f + 0.9f);
		}
		if (Talents.has(player, Talent.DIET_1)) {
			player.getFoodData().eat(3, 0.6f);
		}
		if (Talents.has(player, Talent.DIET_2)) {
			RaceState.setResource(player, RaceState.resource(player) + 15);
		}
		// Living Recovery: 4 health, once in 30 seconds
		if (Talents.has(player, Talent.DIET_3)) {
			long now = player.level().getGameTime();
			CompoundTag progress = player.getData(RaceState.PROGRESS);
			long last = progress.getLong(RECOVERED);
			if (last <= 0 || now - last >= RECOVERY || now < last) {
				player.heal(4);
				CompoundTag tag = progress.copy();
				tag.putLong(RECOVERED, now);
				player.setData(RaceState.PROGRESS, tag);
			}
		}
		player.serverLevel().sendParticles(ParticleTypes.HAPPY_VILLAGER, player.getX(), player.getY() + 1.2, player.getZ(), 8, 0.3, 0.3, 0.3, 0);
		RaceState.sync(player);
	}
}
