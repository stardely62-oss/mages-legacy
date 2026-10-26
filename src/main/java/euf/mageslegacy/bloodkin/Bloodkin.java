package euf.mageslegacy.bloodkin;

import euf.mageslegacy.fx.Fx;
import euf.mageslegacy.fx.ModParticles;
import euf.mageslegacy.race.AbilityResult;
import euf.mageslegacy.race.AbilitySlots;
import euf.mageslegacy.race.Progression;
import euf.mageslegacy.race.Race;
import euf.mageslegacy.race.RaceData;
import euf.mageslegacy.race.RaceState;
import euf.mageslegacy.race.Talents;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.damage.DamageTypes;
import net.minecraft.entity.decoration.ArmorStandEntity;
import net.minecraft.entity.effect.StatusEffect;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.entity.mob.Monster;
import net.minecraft.entity.passive.BatEntity;
import net.minecraft.entity.passive.GolemEntity;
import net.minecraft.entity.passive.TameableEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.FoodComponent;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;

/**
 * Everything the Bloodkin do: blood as their resource, which only hunting fills and which slowly drains away;
 * vampirism; Thirst when the blood runs low (faster and harder-hitting, but no natural healing); the Blood Dash, the
 * Harvest and the Sacrifice; and their weaknesses (sunlight burns a bare head, ordinary food barely feeds them).
 * Hearing heartbeats through walls is drawn by the client (see the outline mixin).
 */
public final class Bloodkin {
	public static final int DASH_COST = 20;
	public static final int HARVEST_COST = 40;
	public static final int SWARM_COST = 30;
	public static final int FEAST_COST = 30;
	public static final int THIRST_BELOW = 20;
	public static final int SENSE = 12;
	public static final int SENSE_WIDE = 24;
	private static final int DASH_COOLDOWN = 120;
	private static final int DASH_TICKS = 4;
	private static final int DOUBLE_WINDOW = 60;
	private static final int HARVEST_COOLDOWN = 400;
	private static final int SACRIFICE_COOLDOWN = 200;
	private static final int SWARM_SLOT = AbilitySlots.cooldownSlot(Race.BLOODKIN, 4);
	private static final int SWARM_COOLDOWN = 600;
	private static final int SWARM_TICKS = 60;
	private static final int FEAST_SLOT = AbilitySlots.cooldownSlot(Race.BLOODKIN, 3);
	private static final int FEAST_TICKS = 160;
	private static final int PRINCE_SLOT = 6;
	private static final int LONG_COOLDOWN = 24000;
	private static final int FEAST_COOLDOWN = 6000;
	private static final int OFFERING_TICKS = 100;
	private static final int MARK_TICKS = 120;

	/** Colours of the glowing effects: fresh blood, and the dark wine of old blood. */
	private static final int BLOOD_RGB = 0xE0283C;
	private static final int WINE_RGB = 0x6A0F1E;

	private record Dash(ServerPlayerEntity player, List<Vec3d> path, Set<UUID> hit, boolean free) {}

	private record Bleed(ServerWorld world, long until, float perSecond, UUID owner) {}

	private record Swarm(ServerPlayerEntity player, long until, List<BatEntity> bats) {}

	private static final List<Dash> DASHES = new ArrayList<>();
	private static final Map<UUID, Integer> DASH_STEP = new HashMap<>();
	private static final Map<UUID, Long> DOUBLE = new HashMap<>();
	private static final Map<UUID, Bleed> BLEEDING = new HashMap<>();
	private static final List<Swarm> SWARMS = new ArrayList<>();
	/** Until when: the Sacrifice's doubled vampirism, the Feast, a Harvest mark (by victim), the Storm's next pulses. */
	private static final Map<UUID, Long> OFFERING = new HashMap<>();
	private static final Map<UUID, Long> FEAST = new HashMap<>();
	private static final Map<UUID, Long> MARKED = new HashMap<>();
	private static final Map<UUID, List<Long>> STORM = new HashMap<>();
	/** Blows landed (for the Bite on every fourth), and blood drunk towards race experience. */
	private static final Map<UUID, Integer> BLOWS = new HashMap<>();
	private static final Map<UUID, Float> DRUNK = new HashMap<>();

	private Bloodkin() {
	}

	public static void reset() {
		DASHES.clear();
		DASH_STEP.clear();
		DOUBLE.clear();
		BLEEDING.clear();
		SWARMS.forEach(s -> s.bats().forEach(Entity::discard));
		SWARMS.clear();
		OFFERING.clear();
		FEAST.clear();
		MARKED.clear();
		STORM.clear();
		BLOWS.clear();
		DRUNK.clear();
	}

	public static boolean is(PlayerEntity player) {
		return player instanceof ServerPlayerEntity sp && RaceState.raceOf(sp).orElse(null) == Race.BLOODKIN;
	}

	private static long now(MinecraftServer server) {
		return server.getTicks();
	}

	private static boolean ready(ServerPlayerEntity player, RaceData data, int slot) {
		return data.readyAt.getOrDefault(slot, 0L) <= now(player.getServer());
	}

	private static void cooldown(ServerPlayerEntity player, RaceData data, int slot, int ticks) {
		data.readyAt.put(slot, now(player.getServer()) + ticks);
		data.dirty = true;
	}

	/** Whether a creature has blood at all: not the undead, golems or armour stands. */
	public static boolean bloodful(Entity entity) {
		return entity instanceof LivingEntity living && !living.isUndead() && !(entity instanceof GolemEntity) && !(entity instanceof ArmorStandEntity);
	}

	/** A living creature with blood to drink right now. */
	/** A bat of some Bloodkin's swarm: part of a body, not prey, so heartbeat sense skips it. */
	public static boolean swarmBat(Entity entity) {
		return entity instanceof BatEntity && entity.isSilent();
	}

	public static boolean hasBlood(Entity entity) {
		return bloodful(entity) && entity.isAlive();
	}

	/**
	 * An enemy for the Dash and the Harvest: a monster, or a mob hunting a player. Villagers, livestock, pets and
	 * bonded beasts are never caught in them (a bite still drinks from anything with blood).
	 */
	private static boolean prey(Entity e, ServerPlayerEntity player) {
		return e instanceof LivingEntity living && living.isAlive() && e != player && !(e instanceof PlayerEntity)
				&& !(e instanceof TameableEntity tame && tame.isTamed()) && !e.getCommandTags().contains("mages_legacy_bond")
				&& (e instanceof Monster || (e instanceof MobEntity m && m.getTarget() instanceof PlayerEntity));
	}

	public static boolean isNight(ServerWorld world) {
		return world.getDimension().hasSkyLight() && world.isNight();
	}

	public static boolean thirsty(RaceData data) {
		return data.resource < THIRST_BELOW;
	}

	/** Blood drunk: into the resource, and every 10 drops a point of race experience. */
	private static void drink(ServerPlayerEntity player, RaceData data, float blood) {
		if (blood <= 0) {
			return;
		}
		data.addResource(blood);
		float total = DRUNK.getOrDefault(player.getUuid(), 0f) + blood;
		int xp = (int) (total / 10);
		DRUNK.put(player.getUuid(), total - xp * 10);
		if (xp > 0) {
			Progression.addXp(player, data, xp);
		}
	}

	// ---- once a second ----

	public static void tickSecond(ServerPlayerEntity player, RaceData data) {
		ServerWorld world = player.getServerWorld();
		boolean night = isNight(world);
		// Blood drains away by itself; Restraint slows it, Night Blood stops it at night
		if (!(night && data.has(Talents.NIGHT_BLOOD))) {
			data.addResource(data.has(Talents.THIRST_SLOW) ? -0.15f : -0.3f);
		}
		if (night && data.has(Talents.NIGHT_BLOOD)) {
			refresh(player, StatusEffects.REGENERATION, 0, 60, 20);
		}
		if (thirsty(data)) {
			refresh(player, StatusEffects.SPEED, 0, 40, 20);
		}
		if ((night || world.getLightLevel(BlockPos.ofFloored(player.getEyePos())) <= 7) && data.has(Talents.NIGHT_EYES)) {
			refresh(player, StatusEffects.NIGHT_VISION, 0, 300, 220);
		}
		if (night && (data.has(Talents.NIGHT_HUNTER) || data.has(Talents.NIGHT_PRINCE))) {
			refresh(player, StatusEffects.SPEED, 0, 40, 20);
		}
		// Quiet Step: at night monsters lose a Bloodkin they hunt from afar
		if (night && data.has(Talents.NIGHT_HUSH)) {
			for (MobEntity m : world.getEntitiesByClass(MobEntity.class, player.getBoundingBox().expand(24),
					e -> e.getTarget() == player && e.squaredDistanceTo(player) > 64)) {
				m.setTarget(null);
			}
		}
		sun(player, data, world);
	}

	/** In direct sunlight a bare head burns (Shade halves it, Twilight Cloak turns it into mere weakness). */
	private static void sun(ServerPlayerEntity player, RaceData data, ServerWorld world) {
		boolean sunlit = world.getDimension().hasSkyLight() && world.isDay() && !world.isRaining() && !player.isTouchingWaterOrRain()
				&& world.isSkyVisible(BlockPos.ofFloored(player.getEyePos()));
		if (!sunlit || !player.getEquippedStack(EquipmentSlot.HEAD).isEmpty()) {
			return;
		}
		if (data.has(Talents.NIGHT_CLOAK)) {
			refresh(player, StatusEffects.WEAKNESS, 0, 60, 30);
			return;
		}
		if (data.has(Talents.NIGHT_SHADE) && world.getTime() / 20 % 2 == 0) {
			return;
		}
		player.setOnFireFor(3);
	}

	private static void refresh(ServerPlayerEntity player, StatusEffect effect, int amplifier, int duration, int below) {
		StatusEffectInstance current = player.getStatusEffect(effect);
		if (current == null || current.getAmplifier() < amplifier || current.getDuration() <= below) {
			player.addStatusEffect(new StatusEffectInstance(effect, duration, amplifier, true, false, true));
		}
	}

	// ---- weaknesses ----

	/** Natural healing stops while thirsty (unless On the Edge). */
	public static float naturalHealing(PlayerEntity player) {
		if (!is(player)) {
			return 1;
		}
		RaceData data = RaceState.of((ServerPlayerEntity) player);
		return thirsty(data) && !data.has(Talents.THIRST_EDGE) ? 0 : 1;
	}

	/** Ordinary food feeds a Bloodkin only a third. */
	public static void afterEating(ServerPlayerEntity player, FoodComponent food) {
		int lost = food.getHunger() * 2 / 3;
		player.getHungerManager().setFoodLevel(Math.max(0, player.getHungerManager().getFoodLevel() - lost));
		player.getHungerManager().setSaturationLevel(Math.min(player.getHungerManager().getSaturationLevel(),
				player.getHungerManager().getFoodLevel()) / 3);
	}

	// ---- blows ----

	/**
	 * A Bloodkin's own blow on a living creature: Thirst, the night, Execution, the Bite, a Harvest mark and an Ambush
	 * from behind make it harder; then it heals (vampirism) and gives blood; with Torn Wound it bleeds.
	 */
	public static float onHit(LivingEntity victim, DamageSource source, float amount) {
		if (!(source.getAttacker() instanceof ServerPlayerEntity player) || victim == player || !is(player) || amount <= 0) {
			return amount;
		}
		RaceData data = RaceState.of(player);
		ServerWorld world = player.getServerWorld();
		long now = now(player.getServer());
		// A real blow (a weapon, a fist, the Dash), not the Harvest's pull or a bleeding wound: only those drink
		boolean melee = source.isOf(DamageTypes.PLAYER_ATTACK) && source.getSource() == player;
		boolean night = isNight(world);
		if (thirsty(data)) {
			amount *= data.has(Talents.THIRST_HUNGER) ? 1.35f : 1.2f;
		}
		if (night && data.has(Talents.NIGHT_PRINCE)) {
			amount *= 1.25f;
		} else if (night && data.has(Talents.NIGHT_HUNTER)) {
			amount *= 1.15f;
		}
		if (data.has(Talents.FANG_EXECUTE) && victim.getHealth() < victim.getMaxHealth() * 0.25f) {
			amount *= 1.5f;
		}
		Long mark = MARKED.get(victim.getUuid());
		if (mark != null && mark > now) {
			amount *= 1.25f;
		}
		// Ambush: a blow at a back that doesn't see you
		if (melee && data.has(Talents.NIGHT_AMBUSH)) {
			Vec3d facing = victim.getRotationVec(1.0f).multiply(1, 0, 1);
			Vec3d toPlayer = player.getPos().subtract(victim.getPos()).multiply(1, 0, 1);
			if (facing.lengthSquared() > 0 && toPlayer.lengthSquared() > 0 && facing.normalize().dotProduct(toPlayer.normalize()) < -0.3) {
				amount *= 1.3f;
			}
		}
		if (!melee || !hasBlood(victim)) {
			return amount;
		}
		float heal = 0;
		if (data.has(Talents.FANG_BITE) && BLOWS.merge(player.getUuid(), 1, Integer::sum) >= 4) {
			BLOWS.remove(player.getUuid());
			amount += 4;
			heal += 4;
			world.playSound(null, victim.getBlockPos(), SoundEvents.ENTITY_PLAYER_ATTACK_CRIT, SoundCategory.PLAYERS, 1.0f, 0.6f);
			splash(world, victim, 10);
		}
		heal += amount * lifesteal(player, data, now);
		player.heal(heal);
		drink(player, data, Math.min(amount, 8));
		if (data.has(Talents.FANG_BLEED)) {
			bleed(world, victim, player, data);
		}
		splash(world, victim, 4);
		return amount;
	}

	/** The share of a blow that heals: 10%, 15% with the Greedy Fang, doubled after a Sacrifice, all of it in the Feast. */
	private static float lifesteal(ServerPlayerEntity player, RaceData data, long now) {
		Long feast = FEAST.get(player.getUuid());
		if (feast != null && feast > now) {
			return 1.0f;
		}
		float rate = data.has(Talents.FANG_LEECH) ? 0.15f : 0.1f;
		Long offering = OFFERING.get(player.getUuid());
		if (offering != null && offering > now) {
			rate *= 2;
		}
		return rate;
	}

	private static void bleed(ServerWorld world, LivingEntity victim, ServerPlayerEntity owner, RaceData data) {
		boolean deep = data.has(Talents.FANG_DEEP);
		BLEEDING.put(victim.getUuid(), new Bleed(world, now(world.getServer()) + (deep ? 120 : 60), deep ? 2 : 1, owner.getUuid()));
		if (deep) {
			victim.addStatusEffect(new StatusEffectInstance(StatusEffects.SLOWNESS, 60, 0));
		}
	}

	/** Drops of blood off a creature. */
	private static void splash(ServerWorld world, Entity e, int count) {
		world.spawnParticles(ModParticles.BLOOD, e.getX(), e.getY() + e.getHeight() * 0.6, e.getZ(), count, e.getWidth() * 0.3, e.getHeight() * 0.2,
				e.getWidth() * 0.3, 0.05);
	}

	/** A living creature died at a Bloodkin's hand: a little hunger stilled, and Frenzy, Rage and experience. */
	public static void onKill(ServerPlayerEntity player, LivingEntity victim) {
		if (!is(player) || !bloodful(victim)) {
			return;
		}
		RaceData data = RaceState.of(player);
		player.getHungerManager().add(2, 0.2f);
		if (data.has(Talents.FANG_FRENZY)) {
			player.addStatusEffect(new StatusEffectInstance(StatusEffects.STRENGTH, 100, 0, true, true, true));
			player.addStatusEffect(new StatusEffectInstance(StatusEffects.SPEED, 100, 0, true, true, true));
		}
		if (thirsty(data)) {
			Progression.addXp(player, data, 4);
			if (data.has(Talents.THIRST_RAGE)) {
				data.addResource(15);
			}
		}
	}

	/** The Prince of the Night: at night a killing blow scatters the Bloodkin into bats instead, once in 20 minutes. */
	public static boolean allowDeath(ServerPlayerEntity player) {
		RaceData data = RaceState.of(player);
		if (data == null || data.race != Race.BLOODKIN || !data.has(Talents.NIGHT_PRINCE) || !isNight(player.getServerWorld())
				|| !ready(player, data, PRINCE_SLOT)) {
			return true;
		}
		player.setHealth(player.getMaxHealth() * 0.3f);
		cooldown(player, data, PRINCE_SLOT, LONG_COOLDOWN);
		startSwarm(player, data);
		player.sendMessage(Text.translatable("mages_legacy.bloodkin.prince").formatted(Formatting.DARK_RED), false);
		return false;
	}

	/** Nothing touches a Bloodkin scattered into bats. */
	public static boolean avoids(LivingEntity victim) {
		for (Swarm s : SWARMS) {
			if (s.player() == victim) {
				return true;
			}
		}
		return false;
	}

	// ---- abilities ----

	public static AbilityResult use(ServerPlayerEntity player, RaceData data, int slot) {
		return switch (slot) {
			case 0 -> dash(player, data);
			case 1 -> harvest(player, data);
			case 2 -> sacrifice(player, data);
			case 3 -> data.has(Talents.FANG_FEAST) ? feast(player, data) : RaceData.talentLocked(player, Talents.FANG_FEAST);
			case 4 -> data.has(Talents.NIGHT_BATS) ? swarm(player, data) : RaceData.talentLocked(player, Talents.NIGHT_BATS);
			default -> AbilityResult.LOCKED;
		};
	}

	public static double dashRange(RaceData data) {
		return data.has(Talents.FANG_REACH) ? 9 : 6;
	}

	/**
	 * Blood Dash: a rush along your look, stopped only by walls, through everything in the way: each one hit takes 6
	 * and bleeds. With Double Rush a second dash follows free within 3 seconds.
	 */
	public static AbilityResult dash(ServerPlayerEntity player, RaceData data) {
		if (DASHES.stream().anyMatch(d -> d.player() == player)) {
			return AbilityResult.HINTED;
		}
		long now = now(player.getServer());
		Long free = DOUBLE.remove(player.getUuid());
		boolean second = free != null && free >= now;
		if (!second && !ready(player, data, 0)) {
			return AbilityResult.COOLDOWN;
		}
		if (!second && data.resource < DASH_COST) {
			return AbilityResult.NO_RESOURCE;
		}
		List<Vec3d> path = dashPath(player, dashRange(data));
		if (path.size() < 2) {
			return AbilityResult.NO_SPACE;
		}
		if (!second) {
			data.addResource(-DASH_COST);
			cooldown(player, data, 0, DASH_COOLDOWN);
		}
		player.stopRiding();
		DASHES.add(new Dash(player, path, new HashSet<>(), second));
		DASH_STEP.put(player.getUuid(), 0);
		ServerWorld world = player.getServerWorld();
		Fx.flight(player, DASH_TICKS);
		Fx.ribbon(world, path, 18, WINE_RGB, BLOOD_RGB, 0.9f);
		world.playSound(null, player.getBlockPos(), SoundEvents.ENTITY_PHANTOM_FLAP, SoundCategory.PLAYERS, 1.0f, 0.6f);
		world.playSound(null, player.getBlockPos(), SoundEvents.ITEM_TRIDENT_RIPTIDE_1, SoundCategory.PLAYERS, 0.8f, 1.4f);
		return AbilityResult.OK;
	}

	/** Points along the look, a quarter block apart, as far as the body fits. */
	private static List<Vec3d> dashPath(ServerPlayerEntity player, double range) {
		List<Vec3d> path = new ArrayList<>();
		Vec3d look = player.getRotationVec(1.0f);
		// Looking about level the dash runs along the ground; only a clear look up or down makes it climb or dive
		double rise = Math.abs(look.y) < 0.5 ? 0 : Math.max(-0.5, Math.min(0.5, look.y));
		Vec3d dir = new Vec3d(look.x, rise, look.z);
		if (dir.lengthSquared() < 1.0E-4) {
			return path;
		}
		dir = dir.normalize();
		Vec3d start = player.getPos();
		path.add(start);
		Vec3d at = start;
		ServerWorld world = player.getServerWorld();
		for (double d = 0.25; d <= range; d += 0.25) {
			Vec3d next = at.add(dir.multiply(0.25));
			if (!world.isSpaceEmpty(player, player.getBoundingBox().offset(next.subtract(player.getPos())))) {
				// Up a step, as walking would
				Vec3d stepped = next.add(0, 0.6, 0);
				if (!world.isSpaceEmpty(player, player.getBoundingBox().offset(stepped.subtract(player.getPos())))) {
					break;
				}
				next = stepped;
			}
			at = next;
			path.add(at);
		}
		return path;
	}

	private static void tickDashes(long now) {
		for (Iterator<Dash> it = DASHES.iterator(); it.hasNext(); ) {
			Dash d = it.next();
			ServerPlayerEntity player = d.player();
			if (player.isRemoved() || !player.isAlive()) {
				it.remove();
				continue;
			}
			ServerWorld world = player.getServerWorld();
			RaceData data = RaceState.of(player);
			int step = DASH_STEP.merge(player.getUuid(), 1, Integer::sum);
			int last = d.path().size() - 1;
			int from = Math.min(last, Math.round((float) (step - 1) * last / DASH_TICKS));
			int to = Math.min(last, Math.round((float) step * last / DASH_TICKS));
			Vec3d a = d.path().get(from);
			Vec3d b = d.path().get(to);
			// Everything the body sweeps through on this stretch is cut
			Box swept = player.getBoundingBox().offset(a.subtract(player.getPos())).union(player.getBoundingBox().offset(b.subtract(player.getPos())))
					.expand(0.4);
			for (LivingEntity e : world.getEntitiesByClass(LivingEntity.class, swept, e -> prey(e, player) && !d.hit().contains(e.getUuid()))) {
				d.hit().add(e.getUuid());
				e.damage(world.getDamageSources().playerAttack(player), 6);
				if (hasBlood(e)) {
					bleed(world, e, player, data);
					splash(world, e, 12);
				}
				Progression.addXp(player, data, 1);
				if (data.has(Talents.FANG_TRAIL)) {
					player.heal(3);
				}
			}
			for (int i = 0; i <= 4; i++) {
				Vec3d p = a.lerp(b, i / 4.0);
				world.spawnParticles(ModParticles.BLOOD, p.x, p.y + 1, p.z, 2, 0.2, 0.4, 0.2, 0.02);
			}
			player.setVelocity(Vec3d.ZERO);
			player.fallDistance = 0;
			player.networkHandler.requestTeleport(b.x, b.y, b.z, player.getYaw(), player.getPitch());
			if (to >= last) {
				it.remove();
				DASH_STEP.remove(player.getUuid());
				if (data.has(Talents.FANG_DOUBLE) && !d.free()) {
					DOUBLE.put(player.getUuid(), now + DOUBLE_WINDOW);
				}
				world.spawnParticles(ModParticles.BLOOD, b.x, b.y + 0.5, b.z, 16, 0.4, 0.3, 0.4, 0.1);
			}
		}
	}

	public static double harvestRadius(RaceData data) {
		return data.has(Talents.THIRST_HARVEST) ? 8 : 6;
	}

	/**
	 * Harvest: blood is drawn out of every enemy around: each takes 4, and you heal 2 (4 with Greedy Harvest) and drink 5
	 * from each. Mark of the Harvest leaves them taking more from you; the Blood Storm crown pulls twice more.
	 */
	public static AbilityResult harvest(ServerPlayerEntity player, RaceData data) {
		if (!ready(player, data, 1)) {
			return AbilityResult.COOLDOWN;
		}
		if (data.resource < HARVEST_COST) {
			return AbilityResult.NO_RESOURCE;
		}
		data.addResource(-HARVEST_COST);
		cooldown(player, data, 1, HARVEST_COOLDOWN);
		pull(player, data);
		if (data.has(Talents.THIRST_STORM)) {
			long now = now(player.getServer());
			STORM.put(player.getUuid(), new ArrayList<>(List.of(now + 20, now + 40)));
		}
		return AbilityResult.OK;
	}

	/** One pull of the Harvest. */
	private static void pull(ServerPlayerEntity player, RaceData data) {
		ServerWorld world = player.getServerWorld();
		double radius = harvestRadius(data);
		long now = now(player.getServer());
		int drawn = 0;
		for (LivingEntity e : world.getEntitiesByClass(LivingEntity.class, player.getBoundingBox().expand(radius),
				e -> prey(e, player) && hasBlood(e) && e.squaredDistanceTo(player) < radius * radius)) {
			e.damage(world.getDamageSources().indirectMagic(player, player), 4);
			player.heal(data.has(Talents.THIRST_DRAIN) ? 4 : 2);
			drink(player, data, 5);
			if (data.has(Talents.THIRST_MARK)) {
				MARKED.put(e.getUuid(), now + MARK_TICKS);
			}
			// A stream of blood from the victim to the Bloodkin
			Vec3d from = e.getPos().add(0, e.getHeight() * 0.6, 0);
			Vec3d way = player.getPos().add(0, 1, 0).subtract(from);
			for (int i = 0; i < 10; i++) {
				Vec3d at = from.add(way.multiply(i / 10.0 * 0.3));
				Vec3d push = way.multiply(0.07 + world.getRandom().nextDouble() * 0.015);
				world.spawnParticles(ModParticles.BLOOD_MOTE, at.x, at.y, at.z, 0, push.x, push.y, push.z, 1);
			}
			Progression.addXp(player, data, 2);
			drawn++;
		}
		Fx.ring(world, player.getPos(), (float) radius, 0.5f, 14, BLOOD_RGB);
		world.playSound(null, player.getBlockPos(), SoundEvents.ENTITY_WARDEN_HEARTBEAT, SoundCategory.PLAYERS, 1.5f, 0.8f);
		if (drawn > 0) {
			world.playSound(null, player.getBlockPos(), SoundEvents.ENTITY_GENERIC_DRINK, SoundCategory.PLAYERS, 1.0f, 0.6f);
		}
	}

	/** Blood Sacrifice: your own health for blood at once. It never kills. Offering of Blood doubles vampirism after. */
	public static AbilityResult sacrifice(ServerPlayerEntity player, RaceData data) {
		if (!ready(player, data, 2)) {
			return AbilityResult.COOLDOWN;
		}
		float price = data.has(Talents.THIRST_SACRIFICE) ? 2 : 4;
		if (player.getHealth() <= price + 1) {
			player.sendMessage(Text.translatable("mages_legacy.bloodkin.too_weak"), true);
			return AbilityResult.HINTED;
		}
		boolean vessel = data.has(Talents.THIRST_VESSEL);
		player.setHealth(player.getHealth() - price);
		data.addResource(vessel ? 50 : 40);
		cooldown(player, data, 2, vessel ? SACRIFICE_COOLDOWN / 2 : SACRIFICE_COOLDOWN);
		if (data.has(Talents.THIRST_OFFERING)) {
			OFFERING.put(player.getUuid(), now(player.getServer()) + OFFERING_TICKS);
		}
		ServerWorld world = player.getServerWorld();
		splash(world, player, 20);
		Fx.screen(player, 14, BLOOD_RGB);
		Fx.ring(world, player.getPos(), 0.3f, 2.5f, 12, WINE_RGB);
		world.playSound(null, player.getBlockPos(), SoundEvents.ENTITY_PLAYER_HURT_SWEET_BERRY_BUSH, SoundCategory.PLAYERS, 1.0f, 0.6f);
		world.playSound(null, player.getBlockPos(), SoundEvents.BLOCK_BREWING_STAND_BREW, SoundCategory.PLAYERS, 0.8f, 0.5f);
		return AbilityResult.OK;
	}

	/** The Feast crown (H): for 8 seconds every blow heals in full. Once in 5 minutes. */
	public static AbilityResult feast(ServerPlayerEntity player, RaceData data) {
		if (!ready(player, data, FEAST_SLOT)) {
			return AbilityResult.COOLDOWN;
		}
		if (data.resource < FEAST_COST) {
			return AbilityResult.NO_RESOURCE;
		}
		data.addResource(-FEAST_COST);
		cooldown(player, data, FEAST_SLOT, FEAST_COOLDOWN);
		FEAST.put(player.getUuid(), now(player.getServer()) + FEAST_TICKS);
		ServerWorld world = player.getServerWorld();
		Fx.screen(player, FEAST_TICKS, BLOOD_RGB);
		Fx.ring(world, player.getPos(), 0.5f, 6, 16, BLOOD_RGB);
		world.playSound(null, player.getBlockPos(), SoundEvents.ENTITY_WITHER_SPAWN, SoundCategory.PLAYERS, 0.4f, 1.8f);
		return AbilityResult.OK;
	}

	/**
	 * Bat Swarm (X): the Bloodkin scatters into bats for 3 seconds, flying where it looks, untouchable;
	 * with the Biting Swarm whatever it flies through bleeds.
	 */
	public static AbilityResult swarm(ServerPlayerEntity player, RaceData data) {
		if (!ready(player, data, SWARM_SLOT)) {
			return AbilityResult.COOLDOWN;
		}
		if (data.resource < SWARM_COST) {
			return AbilityResult.NO_RESOURCE;
		}
		data.addResource(-SWARM_COST);
		cooldown(player, data, SWARM_SLOT, SWARM_COOLDOWN);
		startSwarm(player, data);
		return AbilityResult.OK;
	}

	private static void startSwarm(ServerPlayerEntity player, RaceData data) {
		ServerWorld world = player.getServerWorld();
		List<BatEntity> bats = new ArrayList<>();
		for (int i = 0; i < 7; i++) {
			BatEntity bat = EntityType.BAT.create(world);
			if (bat == null) {
				continue;
			}
			// Burst out onto the ring at once, not out of the Bloodkin's own face
			double a = Math.PI * 2 * i / 7;
			bat.refreshPositionAndAngles(player.getX() + Math.cos(a) * 1.6, player.getY() + 0.4 + world.getRandom().nextDouble(),
					player.getZ() + Math.sin(a) * 1.6, world.getRandom().nextFloat() * 360, 0);
			bat.setInvulnerable(true);
			bat.addCommandTag("mages_legacy_swarm");
			// Silence is synced, so clients can tell the swarm from wild bats (see swarmBat)
			bat.setSilent(true);
			world.spawnEntity(bat);
			bats.add(bat);
		}
		SWARMS.add(new Swarm(player, now(player.getServer()) + SWARM_TICKS, bats));
		player.addStatusEffect(new StatusEffectInstance(StatusEffects.INVISIBILITY, SWARM_TICKS + 4, 0, true, false, false));
		world.spawnParticles(ParticleTypes.SMOKE, player.getX(), player.getY() + 1, player.getZ(), 30, 0.4, 0.6, 0.4, 0.05);
		world.playSound(null, player.getBlockPos(), SoundEvents.ENTITY_BAT_TAKEOFF, SoundCategory.PLAYERS, 1.2f, 0.8f);
	}

	private static void tickSwarms(long now) {
		for (Iterator<Swarm> it = SWARMS.iterator(); it.hasNext(); ) {
			Swarm s = it.next();
			ServerPlayerEntity player = s.player();
			if (player.isRemoved() || !player.isAlive() || s.until() < now) {
				s.bats().forEach(Entity::discard);
				if (!player.isRemoved()) {
					player.removeStatusEffect(StatusEffects.INVISIBILITY);
					player.fallDistance = 0;
					player.getServerWorld().spawnParticles(ParticleTypes.SMOKE, player.getX(), player.getY() + 1, player.getZ(), 30, 0.4, 0.6, 0.4, 0.05);
				}
				it.remove();
				continue;
			}
			// The swarm flies where the Bloodkin looks; the bats flutter around it
			Vec3d look = player.getRotationVec(1.0f);
			player.setVelocity(look.multiply(0.7));
			player.velocityModified = true;
			player.fallDistance = 0;
			ServerWorld world = player.getServerWorld();
			for (BatEntity bat : s.bats()) {
				// A ring around the body, never in the Bloodkin's own face
				double a = world.getRandom().nextDouble() * Math.PI * 2;
				double r = 1.4 + world.getRandom().nextDouble();
				Vec3d spot = player.getPos().add(Math.cos(a) * r, 0.2 + world.getRandom().nextDouble() * 1.4, Math.sin(a) * r);
				bat.setVelocity(spot.subtract(bat.getPos()).multiply(0.4));
				bat.velocityModified = true;
			}
			RaceData data = RaceState.of(player);
			if (data.has(Talents.NIGHT_SWARM)) {
				for (LivingEntity e : world.getEntitiesByClass(LivingEntity.class, player.getBoundingBox().expand(1.5),
						e -> prey(e, player) && hasBlood(e) && !BLEEDING.containsKey(e.getUuid()))) {
					bleed(world, e, player, data);
					splash(world, e, 6);
				}
			}
		}
	}

	// ---- every tick ----

	public static void tick(MinecraftServer server) {
		long now = now(server);
		tickDashes(now);
		tickSwarms(now);
		STORM.entrySet().removeIf(e -> {
			ServerPlayerEntity player = server.getPlayerManager().getPlayer(e.getKey());
			if (player == null) {
				return true;
			}
			List<Long> pulses = e.getValue();
			if (!pulses.isEmpty() && pulses.get(0) <= now) {
				pulses.remove(0);
				pull(player, RaceState.of(player));
			}
			return pulses.isEmpty();
		});
		if (now % 20 == 0) {
			MARKED.values().removeIf(until -> until < now);
			BLEEDING.entrySet().removeIf(e -> {
				Bleed b = e.getValue();
				Entity entity = b.world().getEntity(e.getKey());
				if (!(entity instanceof LivingEntity living) || !living.isAlive() || b.until() < now) {
					return true;
				}
				ServerPlayerEntity owner = server.getPlayerManager().getPlayer(b.owner());
				living.damage(owner != null ? b.world().getDamageSources().indirectMagic(owner, owner) : b.world().getDamageSources().magic(), b.perSecond());
				splash(b.world(), living, 3);
				// Open Vein: the bleeding feeds its Bloodkin
				if (owner != null && RaceState.of(owner).has(Talents.FANG_OPEN)) {
					drink(owner, RaceState.of(owner), b.perSecond());
				}
				return false;
			});
		}
	}

	/** The race changed: its bats fly off, its marks and moments end. */
	public static void cleanup(ServerPlayerEntity player) {
		UUID id = player.getUuid();
		SWARMS.removeIf(s -> {
			if (s.player() == player) {
				s.bats().forEach(Entity::discard);
				return true;
			}
			return false;
		});
		FEAST.remove(id);
		OFFERING.remove(id);
		DOUBLE.remove(id);
	}
}
