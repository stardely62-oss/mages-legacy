package euf.mageslegacy.mercenary;

import euf.mageslegacy.fx.Fx;
import euf.mageslegacy.race.AbilityResult;
import euf.mageslegacy.race.Progression;
import euf.mageslegacy.race.Race;
import euf.mageslegacy.race.RaceData;
import euf.mageslegacy.race.RaceState;
import euf.mageslegacy.race.Talents;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.attribute.EntityAttribute;
import net.minecraft.entity.attribute.EntityAttributeInstance;
import net.minecraft.entity.attribute.EntityAttributeModifier;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.damage.DamageTypes;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.entity.mob.Monster;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.projectile.ProjectileUtil;
import net.minecraft.item.ArmorItem;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.Registries;
import net.minecraft.registry.tag.DamageTypeTags;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.hit.EntityHitResult;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;

/**
 * The Mercenaries: they served the nameless mage for pay, and he paid them in power on his own terms. Nothing comes
 * free: their abilities cost blood, hunger and the armour on their backs, and they grow only by the contracts of the
 * Mage's Shop (H). Strike on Credit (R) pays health for a doubled blow, the Exchange (V) trades effects and life with
 * an enemy, the Pledge (Z) pawns the armour for safety, the Double Stake (Alt, crown) takes power now and pays later.
 */
public final class Mercenary {
	/** Copper and the gold of coins; copper laid on as real colour under the glow (see {@link Fx#INK}). */
	public static final int COPPER_RGB = 0xC8823C;
	public static final int GOLD_RGB = 0xF2C94C;
	private static final int COPPER_INK = Fx.INK | 0x8A4E22;

	public static final int STRIKE_HEALTH = 3;
	public static final int STRIKE_HEALTH_CHEAP = 2;
	private static final int STRIKE_COOLDOWN = 160;
	private static final int STRIKE_WINDOW = 120;
	/** Strike on Credit doubles a blow, but never adds more than this. */
	private static final float STRIKE_MAX_BONUS = 10;
	public static final int EXCHANGE_HUNGER = 3;
	private static final int EXCHANGE_COOLDOWN = 400;
	private static final double EXCHANGE_RANGE = 12;
	private static final int PLEDGE_COOLDOWN = 600;
	private static final int PLEDGE_TICKS = 160;
	private static final int PLEDGE_TICKS_LONG = 240;
	private static final float PLEDGE_WEAR = 0.04f;
	private static final int STAKE_COOLDOWN = 6000;
	private static final int STAKE_TICKS = 300;
	private static final int SECOND_WIND_SLOT = 8;
	private static final int SECOND_WIND_COOLDOWN = 1200;
	private static final int RANSOM_SLOT = 9;
	private static final int RANSOM_COOLDOWN = 24000;
	public static final int RANSOM_TOKENS = 30;

	private static final UUID HIDE_ARMOR = UUID.fromString("8a51c3e2-4b7d-4f19-a2c6-5e0d9b3f1a01");
	private static final UUID VETERAN_ARMOR = UUID.fromString("8a51c3e2-4b7d-4f19-a2c6-5e0d9b3f1a02");
	private static final UUID VETERAN_TOUGHNESS = UUID.fromString("8a51c3e2-4b7d-4f19-a2c6-5e0d9b3f1a03");
	private static final UUID MARCH_SPEED = UUID.fromString("8a51c3e2-4b7d-4f19-a2c6-5e0d9b3f1a04");
	private static final UUID IRON_KNOCKBACK = UUID.fromString("8a51c3e2-4b7d-4f19-a2c6-5e0d9b3f1a05");

	/** Strike on Credit: blows left in the charge, and until when it holds. */
	private record Strike(int blows, long until) {}

	private static final Map<UUID, Strike> STRIKES = new HashMap<>();
	private static final Map<UUID, Long> PLEDGES = new HashMap<>();
	private static final Map<UUID, Long> STAKES = new HashMap<>();
	/** The last creature hit by a Strike on Credit (Greed: killing it with that blow pays the cooldown back). */
	private static final Map<UUID, UUID> STRUCK = new HashMap<>();

	private Mercenary() {
	}

	public static void reset() {
		STRIKES.clear();
		PLEDGES.clear();
		STAKES.clear();
		STRUCK.clear();
	}

	public static boolean is(PlayerEntity player) {
		return player instanceof ServerPlayerEntity sp && RaceState.raceOf(sp).orElse(null) == Race.MERCENARY;
	}

	private static long now(MinecraftServer server) {
		return server.getTicks();
	}

	private static boolean ready(ServerPlayerEntity player, RaceData data, int slot) {
		return data.readyAt.getOrDefault(slot, 0L) <= now(player.getServer());
	}

	/** Out of work, out of nerve: with no contract in hand the abilities take half as long again to come back. */
	private static void cooldown(ServerPlayerEntity player, RaceData data, int slot, int ticks) {
		if (data.merc.active.isEmpty()) {
			ticks = ticks * 3 / 2;
		}
		data.readyAt.put(slot, now(player.getServer()) + ticks);
		data.dirty = true;
	}

	// ---- once a second ----

	public static void tickSecond(ServerPlayerEntity player, RaceData data) {
		Contracts.tickSecond(player, data);
		// The race bar shows the work: how far along the first contract in hand is
		float shown = 0;
		if (!data.merc.active.isEmpty()) {
			Contract c = data.merc.active.get(0);
			shown = 100f * c.progress / c.amount;
		}
		if ((int) shown != (int) data.resource) {
			data.resource = shown;
			data.dirty = true;
		}
		modifier(player, EntityAttributes.GENERIC_ARMOR, HIDE_ARMOR, "merc_hide", 2, data.has(Talents.CRAFT_HIDE));
		modifier(player, EntityAttributes.GENERIC_ARMOR, VETERAN_ARMOR, "merc_veteran", 2, data.has(Talents.CRAFT_VETERAN));
		modifier(player, EntityAttributes.GENERIC_ARMOR_TOUGHNESS, VETERAN_TOUGHNESS, "merc_veteran_toughness", 2, data.has(Talents.CRAFT_VETERAN));
		modifier(player, EntityAttributes.GENERIC_KNOCKBACK_RESISTANCE, IRON_KNOCKBACK, "merc_iron", 0.5, data.has(Talents.CRAFT_IRON));
		EntityAttributeInstance speed = player.getAttributeInstance(EntityAttributes.GENERIC_MOVEMENT_SPEED);
		if (speed != null && data.has(Talents.CRAFT_MARCH) != (speed.getModifier(MARCH_SPEED) != null)) {
			if (data.has(Talents.CRAFT_MARCH)) {
				speed.addTemporaryModifier(new EntityAttributeModifier(MARCH_SPEED, "merc_march", 0.1, EntityAttributeModifier.Operation.MULTIPLY_BASE));
			} else {
				speed.removeModifier(MARCH_SPEED);
			}
		}
		// Tip-off: the creatures a hunt is after glow for the Mercenary
		if (data.has(Talents.CON_SCOUT)) {
			ServerWorld world = player.getServerWorld();
			for (LivingEntity mob : world.getEntitiesByClass(LivingEntity.class, player.getBoundingBox().expand(32),
					e -> e.isAlive() && hunted(data, e))) {
				mob.addStatusEffect(new StatusEffectInstance(StatusEffects.GLOWING, 40, 0, true, false));
			}
		}
		if (data.has(Talents.CRAFT_LEGEND) && !data.merc.active.isEmpty()) {
			refresh(player, StatusEffects.RESISTANCE, 0);
		}
	}

	private static void refresh(ServerPlayerEntity player, net.minecraft.entity.effect.StatusEffect effect, int amplifier) {
		StatusEffectInstance current = player.getStatusEffect(effect);
		if (current == null || current.getDuration() <= 30) {
			player.addStatusEffect(new StatusEffectInstance(effect, 60, amplifier, true, false, true));
		}
	}

	/** Adds or removes a talent's attribute bonus so it matches whether the talent is learnt. */
	private static void modifier(ServerPlayerEntity player, EntityAttribute attribute, UUID id, String name, double amount, boolean want) {
		EntityAttributeInstance instance = player.getAttributeInstance(attribute);
		if (instance == null || want == (instance.getModifier(id) != null)) {
			return;
		}
		if (want) {
			instance.addTemporaryModifier(new EntityAttributeModifier(id, name, amount, EntityAttributeModifier.Operation.ADDITION));
		} else {
			instance.removeModifier(id);
		}
	}

	/** Takes the talents' attribute bonuses off (the race was reset or changed). */
	public static void cleanup(ServerPlayerEntity player) {
		modifier(player, EntityAttributes.GENERIC_ARMOR, HIDE_ARMOR, "merc_hide", 2, false);
		modifier(player, EntityAttributes.GENERIC_ARMOR, VETERAN_ARMOR, "merc_veteran", 2, false);
		modifier(player, EntityAttributes.GENERIC_ARMOR_TOUGHNESS, VETERAN_TOUGHNESS, "merc_veteran_toughness", 2, false);
		modifier(player, EntityAttributes.GENERIC_KNOCKBACK_RESISTANCE, IRON_KNOCKBACK, "merc_iron", 0.5, false);
		EntityAttributeInstance speed = player.getAttributeInstance(EntityAttributes.GENERIC_MOVEMENT_SPEED);
		if (speed != null) {
			speed.removeModifier(MARCH_SPEED);
		}
		STRIKES.remove(player.getUuid());
		PLEDGES.remove(player.getUuid());
		STAKES.remove(player.getUuid());
	}

	/** Whether a creature is what one of the Mercenary's hunts is after. */
	public static boolean hunted(RaceData data, LivingEntity e) {
		String id = Registries.ENTITY_TYPE.getId(e.getType()).toString();
		return data.merc.active.stream().anyMatch(c -> c.kind == Contract.Kind.HUNT && c.target.equals(id));
	}

	// ---- every tick ----

	public static void tick(MinecraftServer server) {
		long now = now(server);
		STRIKES.values().removeIf(s -> s.until() < now);
		PLEDGES.values().removeIf(until -> until < now);
		// The Double Stake comes due: a fifth of the health left, never the last of it
		STAKES.entrySet().removeIf(e -> {
			if (e.getValue() > now) {
				return false;
			}
			ServerPlayerEntity player = server.getPlayerManager().getPlayer(e.getKey());
			if (player != null && player.isAlive()) {
				player.setHealth(Math.max(1, player.getHealth() * 0.8f));
				ServerWorld world = player.getServerWorld();
				Fx.burst(world, player.getPos().add(0, 1, 0), 1.6f, 8, COPPER_INK);
				Fx.pulse(player, 1, 16, COPPER_RGB);
				world.playSound(null, player.getBlockPos(), SoundEvents.BLOCK_CHAIN_BREAK, SoundCategory.PLAYERS, 1.0f, 0.6f);
				player.sendMessage(Text.translatable("mages_legacy.merc.stake_due").formatted(Formatting.GOLD), true);
			}
			return true;
		});
	}

	// ---- abilities ----

	public static AbilityResult use(ServerPlayerEntity player, RaceData data, int slot) {
		return switch (slot) {
			case 0 -> strike(player, data);
			case 1 -> exchange(player, data);
			case 2 -> pledge(player, data);
			case 4 -> data.has(Talents.DEAL_STAKE) ? stake(player, data) : RaceData.talentLocked(player, Talents.DEAL_STAKE);
			// The Shop opens on the client; a press that reaches the server does nothing
			default -> AbilityResult.HINTED;
		};
	}

	public static int strikeHealth(RaceData data) {
		return data.has(Talents.DEAL_CHEAP) ? STRIKE_HEALTH_CHEAP : STRIKE_HEALTH;
	}

	/** Strike on Credit (R): pay health now, and the next blow within 6 seconds lands twice as hard. */
	public static AbilityResult strike(ServerPlayerEntity player, RaceData data) {
		if (!ready(player, data, 0)) {
			return AbilityResult.COOLDOWN;
		}
		int price = strikeHealth(data);
		// The price may never be the last of one's blood
		if (player.getHealth() <= price + 2) {
			return AbilityResult.NO_RESOURCE;
		}
		player.setHealth(player.getHealth() - price);
		cooldown(player, data, 0, STRIKE_COOLDOWN);
		STRIKES.put(player.getUuid(), new Strike(data.has(Talents.DEAL_DOUBLE) ? 2 : 1, now(player.getServer()) + STRIKE_WINDOW));
		ServerWorld world = player.getServerWorld();
		// Blood bought on credit: a few coins tossed up and the seal of the debt flickering at the hand
		Fx.coins(world, player.getPos().add(0, 1.2, 0), 4, 0.6f, 24);
		Fx.aura(world, player, STRIKE_WINDOW, GOLD_RGB, Fx.MOTES);
		Fx.pulse(player, 1, 14, 0xB01226);
		world.playSound(null, player.getBlockPos(), SoundEvents.ITEM_ARMOR_EQUIP_CHAIN, SoundCategory.PLAYERS, 1.0f, 0.7f);
		world.playSound(null, player.getBlockPos(), SoundEvents.BLOCK_AMETHYST_BLOCK_CHIME, SoundCategory.PLAYERS, 1.0f, 0.6f);
		return AbilityResult.OK;
	}

	/**
	 * The Exchange (V): with the creature you look at (12 blocks) you trade what you carry: your harmful effects go to
	 * it, its good ones come to you, and some of its life becomes your absorption. It costs hunger.
	 */
	public static AbilityResult exchange(ServerPlayerEntity player, RaceData data) {
		if (!ready(player, data, 1)) {
			return AbilityResult.COOLDOWN;
		}
		boolean free = data.has(Talents.DEAL_FREE);
		if (!free && player.getHungerManager().getFoodLevel() < EXCHANGE_HUNGER + 3) {
			return AbilityResult.NO_RESOURCE;
		}
		LivingEntity target = lookedAt(player, EXCHANGE_RANGE);
		if (target == null) {
			return AbilityResult.NO_SIGHT;
		}
		if (!free) {
			player.getHungerManager().setFoodLevel(player.getHungerManager().getFoodLevel() - EXCHANGE_HUNGER);
		}
		cooldown(player, data, 1, EXCHANGE_COOLDOWN);
		List<StatusEffectInstance> mine = new ArrayList<>();
		for (StatusEffectInstance e : player.getStatusEffects()) {
			if (!e.getEffectType().isBeneficial() && !e.isAmbient()) {
				mine.add(e);
			}
		}
		List<StatusEffectInstance> theirs = new ArrayList<>();
		for (StatusEffectInstance e : target.getStatusEffects()) {
			if (e.getEffectType().isBeneficial()) {
				theirs.add(e);
			}
		}
		for (StatusEffectInstance e : mine) {
			player.removeStatusEffect(e.getEffectType());
			target.addStatusEffect(new StatusEffectInstance(e));
		}
		for (StatusEffectInstance e : theirs) {
			target.removeStatusEffect(e.getEffectType());
			player.addStatusEffect(new StatusEffectInstance(e));
		}
		float drain = data.has(Talents.DEAL_SHARP) ? 6 : 4;
		ServerWorld world = player.getServerWorld();
		target.damage(world.getDamageSources().indirectMagic(player, player), drain);
		player.setAbsorptionAmount(Math.min(8, player.getAbsorptionAmount() + drain));
		if (data.has(Talents.DEAL_CURSE)) {
			target.addStatusEffect(new StatusEffectInstance(StatusEffects.WEAKNESS, 120, 0));
			target.addStatusEffect(new StatusEffectInstance(StatusEffects.SLOWNESS, 120, 0));
		}
		// The trade: what was hers goes out along one arc, what was the creature's comes back along the other
		Fx.exchange(world, player, target, 16, GOLD_RGB, 0xC0303A);
		world.playSound(null, player.getBlockPos(), SoundEvents.ENTITY_VILLAGER_TRADE, SoundCategory.PLAYERS, 1.0f, 0.6f);
		world.playSound(null, target.getBlockPos(), SoundEvents.BLOCK_AMETHYST_BLOCK_RESONATE, SoundCategory.PLAYERS, 1.0f, 0.8f);
		return AbilityResult.OK;
	}

	/** The Pledge (Z): the armour is pawned for a while: Resistance II, and every piece worn pays with a little of its wear. */
	public static AbilityResult pledge(ServerPlayerEntity player, RaceData data) {
		if (!ready(player, data, 2)) {
			return AbilityResult.COOLDOWN;
		}
		List<EquipmentSlot> worn = new ArrayList<>();
		for (EquipmentSlot slot : List.of(EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET)) {
			if (player.getEquippedStack(slot).getItem() instanceof ArmorItem) {
				worn.add(slot);
			}
		}
		if (worn.isEmpty()) {
			player.sendMessage(Text.translatable("mages_legacy.merc.no_armor"), true);
			return AbilityResult.HINTED;
		}
		cooldown(player, data, 2, PLEDGE_COOLDOWN);
		if (!data.has(Talents.DEAL_BUYOUT)) {
			for (EquipmentSlot slot : worn) {
				ItemStack stack = player.getEquippedStack(slot);
				if (stack.isDamageable()) {
					stack.damage(Math.max(1, Math.round(stack.getMaxDamage() * PLEDGE_WEAR)), player, p -> p.sendEquipmentBreakStatus(slot));
				}
			}
		}
		int ticks = data.has(Talents.DEAL_LONG) ? PLEDGE_TICKS_LONG : PLEDGE_TICKS;
		player.addStatusEffect(new StatusEffectInstance(StatusEffects.RESISTANCE, ticks, 1, true, false, true));
		PLEDGES.put(player.getUuid(), now(player.getServer()) + ticks);
		ServerWorld world = player.getServerWorld();
		// The pawnbroker's seal comes down on the body, and its rim turns at the waist for as long as the pledge holds
		Fx.seal(world, player, 1.1f, ticks, GOLD_RGB);
		Fx.shake(player, 1.0f, 6);
		world.playSound(null, player.getBlockPos(), SoundEvents.BLOCK_ANVIL_PLACE, SoundCategory.PLAYERS, 0.5f, 1.4f);
		world.playSound(null, player.getBlockPos(), SoundEvents.ITEM_ARMOR_EQUIP_NETHERITE, SoundCategory.PLAYERS, 1.0f, 0.8f);
		return AbilityResult.OK;
	}

	/** The Double Stake (Alt, the Deals' crown): 15 seconds of blows half again as hard that heal a quarter; then a fifth of the health is paid. */
	public static AbilityResult stake(ServerPlayerEntity player, RaceData data) {
		if (!ready(player, data, 4)) {
			return AbilityResult.COOLDOWN;
		}
		cooldown(player, data, 4, STAKE_COOLDOWN);
		STAKES.put(player.getUuid(), now(player.getServer()) + STAKE_TICKS);
		ServerWorld world = player.getServerWorld();
		// All on one toss: a great coin flies up spinning over the head, comes down, and gold spills everywhere
		Fx.flip(world, player, 28, GOLD_RGB);
		Fx.coins(world, player.getPos().add(0, 1.6, 0), 16, 1.2f, 40);
		Fx.aura(world, player, STAKE_TICKS, GOLD_RGB, Fx.MOTES);
		Fx.fov(player, 0.15f, 14);
		Fx.shake(player, 1.5f, 10);
		world.playSound(null, player.getBlockPos(), SoundEvents.BLOCK_BELL_USE, SoundCategory.PLAYERS, 1.0f, 0.7f);
		world.playSound(null, player.getBlockPos(), SoundEvents.ENTITY_PLAYER_LEVELUP, SoundCategory.PLAYERS, 0.7f, 0.6f);
		return AbilityResult.OK;
	}

	private static LivingEntity lookedAt(ServerPlayerEntity player, double range) {
		Vec3d eye = player.getEyePos();
		Vec3d look = player.getRotationVec(1.0f);
		Vec3d end = eye.add(look.multiply(range));
		Box box = player.getBoundingBox().stretch(look.multiply(range)).expand(1);
		EntityHitResult hit = ProjectileUtil.raycast(player, eye, end, box,
				e -> e instanceof LivingEntity living && living.isAlive() && e != player && !(e instanceof PlayerEntity), range * range);
		return hit != null && hit.getEntity() instanceof LivingEntity living ? living : null;
	}

	// ---- damage ----

	/**
	 * A Mercenary's blow: Strike on Credit doubles it, the Double Stake adds half and drinks a quarter, and the Craft
	 * and the work add up: the contract's quarry, a firm grip, a bow, the finishing blow, a pack on one's back.
	 */
	public static float onHit(LivingEntity victim, DamageSource source, float amount) {
		if (!(source.getAttacker() instanceof ServerPlayerEntity player) || victim == player || !is(player) || amount <= 0) {
			return amount;
		}
		RaceData data = RaceState.of(player);
		long now = now(player.getServer());
		boolean melee = source.isOf(DamageTypes.PLAYER_ATTACK) && source.getSource() == player;
		boolean projectile = source.isIn(DamageTypeTags.IS_PROJECTILE);
		float bonus = 0;
		if (hunted(data, victim)) {
			bonus += data.has(Talents.CRAFT_MARK) ? 0.25f : 0.1f;
		}
		if (melee && data.has(Talents.CRAFT_GRIP)) {
			bonus += 0.1f;
		}
		if (projectile && data.has(Talents.CRAFT_RANGED)) {
			bonus += 0.2f;
		}
		if (data.has(Talents.CRAFT_EXECUTE) && victim instanceof Monster && victim.getHealth() < victim.getMaxHealth() * 0.3f) {
			bonus += 0.3f;
		}
		if (data.has(Talents.CRAFT_WOLF) && hunters(player) >= 3) {
			bonus += 0.2f;
		}
		if (data.has(Talents.CRAFT_LEGEND) && !data.merc.active.isEmpty()) {
			bonus += 0.15f;
		}
		boolean staking = STAKES.getOrDefault(player.getUuid(), 0L) > now;
		if (staking && (melee || projectile)) {
			bonus += 0.5f;
		}
		// Everything that makes a blow harder adds up, to twice the blow at most (as the Bloodkin's)
		amount *= 1 + Math.min(1.0f, bonus);
		ServerWorld world = player.getServerWorld();
		Strike strike = STRIKES.get(player.getUuid());
		if (melee && strike != null && strike.until() >= now) {
			float extra = Math.min(STRIKE_MAX_BONUS, amount);
			amount += extra;
			if (strike.blows() > 1) {
				STRIKES.put(player.getUuid(), new Strike(strike.blows() - 1, strike.until()));
			} else {
				STRIKES.remove(player.getUuid());
			}
			STRUCK.put(player.getUuid(), victim.getUuid());
			if (data.has(Talents.DEAL_INTEREST)) {
				player.heal(extra * 0.3f);
			}
			// The debt paid out in one blow: a crossed copper cut and a spray of gold
			Vec3d center = victim.getPos().add(0, victim.getHeight() * 0.6, 0);
			Fx.slash(world, center, player.getRotationVec(1.0f), 0.9f + victim.getWidth(), 35, 9, COPPER_INK);
			Fx.slash(world, center, player.getRotationVec(1.0f), 0.9f + victim.getWidth(), 145, 9, GOLD_RGB);
			Fx.coins(world, center, 10, 1.0f, 32);
			Fx.shake(player, 1.4f, 6);
			world.playSound(null, victim.getBlockPos(), SoundEvents.ENTITY_PLAYER_ATTACK_CRIT, SoundCategory.PLAYERS, 1.0f, 0.7f);
			world.playSound(null, victim.getBlockPos(), SoundEvents.BLOCK_CHAIN_BREAK, SoundCategory.PLAYERS, 0.8f, 1.2f);
		}
		if (staking && melee) {
			player.heal(amount * 0.25f);
		}
		return amount;
	}

	/** How many mobs within 16 blocks are after this player. */
	private static int hunters(ServerPlayerEntity player) {
		return player.getServerWorld().getEntitiesByClass(MobEntity.class, player.getBoundingBox().expand(16),
				m -> m.isAlive() && m.getTarget() == player).size();
	}

	/** Blows on a Mercenary: the Iron Pledge gives a part back; Second Wind catches a fall below a third of the health. */
	public static float modifyDamage(ServerPlayerEntity player, DamageSource source, float amount) {
		RaceData data = RaceState.of(player);
		if (data == null || amount <= 0) {
			return amount;
		}
		long now = now(player.getServer());
		if (data.has(Talents.DEAL_IRON) && PLEDGES.getOrDefault(player.getUuid(), 0L) > now && source.getAttacker() instanceof LivingEntity attacker
				&& attacker != player && source.getSource() == attacker) {
			attacker.damage(player.getDamageSources().thorns(player), amount * 0.3f);
		}
		if (data.has(Talents.CRAFT_SECOND) && player.getHealth() - amount < player.getMaxHealth() * 0.3f && player.getHealth() - amount > 0
				&& ready(player, data, SECOND_WIND_SLOT)) {
			data.readyAt.put(SECOND_WIND_SLOT, now + SECOND_WIND_COOLDOWN);
			player.addStatusEffect(new StatusEffectInstance(StatusEffects.SPEED, 80, 1, true, true, true));
			player.addStatusEffect(new StatusEffectInstance(StatusEffects.REGENERATION, 80, 0, true, true, true));
		}
		return amount;
	}

	/** A creature the Mercenary killed: it counts for the hunts; a kill with a Strike on Credit pays back its cooldown (Greed). */
	public static void onKill(ServerPlayerEntity player, LivingEntity victim) {
		if (!is(player)) {
			return;
		}
		RaceData data = RaceState.of(player);
		Contracts.onKill(player, data, victim);
		if (data.has(Talents.DEAL_GREED) && victim.getUuid().equals(STRUCK.remove(player.getUuid()))) {
			data.readyAt.remove(0);
			data.dirty = true;
		}
	}

	/** The Ransom (crown of the Contracts): a killing blow is bought off for 30 tokens, once in 20 minutes. */
	public static boolean allowDeath(ServerPlayerEntity player) {
		RaceData data = RaceState.of(player);
		if (data == null || data.race != Race.MERCENARY || !data.has(Talents.CON_RANSOM) || data.merc.tokens < RANSOM_TOKENS
				|| !ready(player, data, RANSOM_SLOT)) {
			return true;
		}
		data.merc.tokens -= RANSOM_TOKENS;
		data.readyAt.put(RANSOM_SLOT, now(player.getServer()) + RANSOM_COOLDOWN);
		data.dirty = true;
		player.setHealth(player.getMaxHealth() * 0.4f);
		ServerWorld world = player.getServerWorld();
		Fx.burst(world, player.getPos().add(0, 1, 0), 3.0f, 12, GOLD_RGB);
		Fx.pillar(world, player.getPos(), 0.8f, 4, 24, GOLD_RGB);
		Fx.screen(player, 24, GOLD_RGB);
		world.playSound(null, player.getBlockPos(), SoundEvents.ITEM_TOTEM_USE, SoundCategory.PLAYERS, 0.6f, 1.3f);
		world.playSound(null, player.getBlockPos(), SoundEvents.ENTITY_VILLAGER_TRADE, SoundCategory.PLAYERS, 1.0f, 0.5f);
		player.sendMessage(Text.translatable("mages_legacy.merc.ransom", RANSOM_TOKENS).formatted(Formatting.GOLD), false);
		return false;
	}

	/** The Mage's Shop is called: its door stands in the air before the Mercenary and opens on warm light, until it is left. */
	public static void openedShop(ServerPlayerEntity player) {
		ServerWorld world = player.getServerWorld();
		Vec3d look = player.getRotationVec(1.0f).multiply(1, 0, 1);
		look = look.lengthSquared() < 1.0E-4 ? new Vec3d(0, 0, 1) : look.normalize();
		Fx.door(world, player.getPos().add(look.multiply(3.0)), player.getYaw(), player, 6000, GOLD_RGB);
		world.playSound(null, player.getBlockPos(), SoundEvents.BLOCK_WOODEN_DOOR_OPEN, SoundCategory.PLAYERS, 1.0f, 0.6f);
		world.playSound(null, player.getBlockPos(), SoundEvents.BLOCK_AMETHYST_BLOCK_CHIME, SoundCategory.PLAYERS, 1.0f, 1.2f);
	}

	/** The Mercenary left the Shop: the door swings shut behind them. */
	public static void closedShop(ServerPlayerEntity player) {
		ServerWorld world = player.getServerWorld();
		Fx.doorClose(world, player);
		world.playSound(null, player.getBlockPos(), SoundEvents.BLOCK_WOODEN_DOOR_CLOSE, SoundCategory.PLAYERS, 1.0f, 0.6f);
	}

	/** What the client's Shop screen needs: the account in the Shop and today's prices. */
	public static net.minecraft.nbt.NbtCompound clientInfo(ServerPlayerEntity player, RaceData data) {
		Contracts.refresh(player, data);
		Shop.ensureStock(player, data);
		net.minecraft.nbt.NbtCompound nbt = data.merc.toNbt();
		nbt.putInt("slots", Contracts.slots(data));
		nbt.putInt("rerollPrice", Shop.discounted(data, Contracts.rerollPrice(data)));
		net.minecraft.nbt.NbtCompound prices = new net.minecraft.nbt.NbtCompound();
		for (Shop.Ware ware : Shop.CATALOG) {
			if (ware.kind() == Shop.Kind.GOODS && !data.merc.stock.contains(ware.id())) {
				continue;
			}
			prices.putInt(ware.id(), Shop.soldOut(data, ware) ? -1 : Progression.tierOpen(player, ware.tier()) ? Shop.price(data, ware) : -2);
		}
		nbt.put("prices", prices);
		nbt.putLong("nextDay", (Contracts.today(player) + 1) * Contracts.DAY - player.getServer().getOverworld().getTime());
		return nbt;
	}
}
