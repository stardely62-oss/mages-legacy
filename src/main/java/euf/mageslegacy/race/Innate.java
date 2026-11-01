package euf.mageslegacy.race;

import euf.mageslegacy.MagesLegacy;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.TagKey;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.EntityTeleportEvent;
import net.neoforged.neoforge.event.entity.living.MobEffectEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;

/**
 * The innate traits: what a race is by nature, from its first level, never bought and never forgotten (they live in
 * no talent list; the active race alone decides them). Each race shows them as one node beside its core
 * ({@link Talent.Kind#INNATE}).
 * <ul>
 * <li>Voidborn, Kinship with the Void: +1 block of block interaction range (breaking, placing, containers,
 * mechanisms; not attacks or creatures), and half the damage of their own ender pearl's landing.</li>
 * <li>Mycelid, Fungal Nature: raw mushrooms and fungi are food ({@link euf.mageslegacy.mycelid.FungalDiet}).</li>
 * <li>Bloodkin, Predator's Instincts: the wounded heard as a heartbeat (client side, in
 * {@code client.BloodInstinct}), and raw meat feeds well and never brings Hunger
 * ({@link euf.mageslegacy.bloodkin.Bloodkin}).</li>
 * </ul>
 */
public final class Innate {
	/** The Voidborn's reach: one stable id, added once (a transient modifier: never saved, so a login can't double it). */
	public static final ResourceLocation VOID_REACH = MagesLegacy.id("innate_void_reach");
	public static final double VOID_REACH_BONUS = 1;
	/** The share of their own pearl's landing damage a Voidborn still takes. */
	public static final float PEARL_SHARE = 0.5f;
	/** Ordinary raw meat (other mods' through c:foods/raw_meat): a Bloodkin's food. */
	public static final TagKey<Item> RAW_MEAT = TagKey.create(Registries.ITEM, MagesLegacy.id("raw_meat"));
	/** Raw meat gives a Bloodkin this much more food than it says (beef 3 → 5, mutton 2 → 4) and isn't cut to a third. */
	public static final int RAW_MEAT_BONUS = 2;

	private Innate() {
	}

	public static void register() {
		NeoForge.EVENT_BUS.addListener(Innate::onPearl);
		NeoForge.EVENT_BUS.addListener(Innate::onEffect);
		NeoForge.EVENT_BUS.addListener((PlayerTickEvent.Post e) -> {
			if (e.getEntity() instanceof ServerPlayer player && player.tickCount % 20 == 0) {
				refresh(player);
			}
		});
		NeoForge.EVENT_BUS.addListener((PlayerEvent.PlayerLoggedInEvent e) -> refreshIf(e.getEntity()));
		NeoForge.EVENT_BUS.addListener((PlayerEvent.PlayerRespawnEvent e) -> refreshIf(e.getEntity()));
		NeoForge.EVENT_BUS.addListener((PlayerEvent.PlayerChangedDimensionEvent e) -> refreshIf(e.getEntity()));
	}

	private static void refreshIf(Player player) {
		if (player instanceof ServerPlayer sp) {
			refresh(sp);
		}
	}

	/**
	 * Puts the attribute bonuses of the active race's innate traits on the player and takes off any other race's: run
	 * at once on a new race, at login, respawn and a change of world, and every second besides. The attribute itself
	 * reaches the client with the vanilla attribute sync, so the client's aim and the server's check agree.
	 */
	public static void refresh(ServerPlayer player) {
		AttributeInstance reach = player.getAttribute(Attributes.BLOCK_INTERACTION_RANGE);
		if (reach == null) {
			return;
		}
		boolean want = RaceState.is(player, Race.VOIDBORN);
		boolean has = reach.hasModifier(VOID_REACH);
		if (want && !has) {
			reach.addTransientModifier(new AttributeModifier(VOID_REACH, VOID_REACH_BONUS, AttributeModifier.Operation.ADD_VALUE));
		} else if (!want && has) {
			reach.removeModifier(VOID_REACH);
		}
	}

	/** Kinship with the Void: only the landing of one's own pearl (NeoForge's own pearl event, so no fall is touched). */
	private static void onPearl(EntityTeleportEvent.EnderPearl event) {
		ServerPlayer player = event.getPlayer();
		if (player != null && RaceState.is(player, Race.VOIDBORN) && event.getPearlEntity().getOwner() == player) {
			event.setAttackDamage(event.getAttackDamage() * PEARL_SHARE);
		}
	}

	public static boolean rawMeat(ItemStack stack) {
		return !stack.isEmpty() && stack.is(RAW_MEAT);
	}

	/**
	 * Hunter's Flesh: the Hunger raw chicken gives is never laid on a Bloodkin eating it (only while that very meal is
	 * eaten: Hunger from anything else still takes).
	 */
	private static void onEffect(MobEffectEvent.Applicable event) {
		if (!(event.getEntity() instanceof ServerPlayer player) || !event.getEffectInstance().is(MobEffects.HUNGER) || !RaceState.is(player, Race.BLOODKIN)) {
			return;
		}
		if (player.isUsingItem() && player.getUseItem().is(Items.CHICKEN)) {
			event.setResult(MobEffectEvent.Applicable.Result.DO_NOT_APPLY);
		}
	}
}
