package euf.mageslegacy.tempter;

import euf.mageslegacy.fx.Fx;
import euf.mageslegacy.race.AbilityResult;
import euf.mageslegacy.race.Progression;
import euf.mageslegacy.race.Race;
import euf.mageslegacy.race.RaceData;
import euf.mageslegacy.race.RaceState;
import euf.mageslegacy.race.Talents;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.attribute.EntityAttributeInstance;
import net.minecraft.entity.attribute.EntityAttributeModifier;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.damage.DamageTypes;
import net.minecraft.entity.effect.StatusEffect;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.entity.mob.Monster;
import net.minecraft.entity.passive.IronGolemEntity;
import net.minecraft.entity.passive.TameableEntity;
import net.minecraft.entity.passive.VillagerEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.projectile.ProjectileUtil;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.particle.ParticleTypes;
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
 * The Tempters: the nameless mage's longing to be loved, a hunter that lives by the kiss. The Kiss (R) rushes in,
 * drinks a creature's life and leaves it infatuated: it won't raise a hand against her and takes her blows harder. The
 * Wings (V) carry her through the air, the Charm (Z) makes a monster her thrall for a while, the Lullaby (H) puts the
 * monsters around to sleep (a sleeper's kiss is twice as sweet), and the Broken Heart (Alt, crown) breaks every heart
 * she has won. The undead have no heart: no kiss, charm or song takes them.
 */
public final class Tempter {
	/** Rose of the kiss, and dark wine laid on as real colour under the glow (see {@link Fx#INK}). */
	public static final int ROSE_RGB = 0xFF6FA8;
	private static final int WINE_INK = Fx.INK | 0x6A1838;
	private static final int DREAM_RGB = 0xC8A8FF;

	public static final int KISS_COST = 20;
	public static final int KISS_COST_CHEAP = 12;
	public static final int WINGS_COST = 15;
	public static final int WINGS_COST_CHEAP = 8;
	public static final int CHARM_COST = 30;
	public static final int CHARM_COST_CHEAP = 20;
	public static final int LULLABY_COST = 40;
	public static final int BREAK_COST = 50;
	private static final int KISS_COOLDOWN = 120;
	private static final int KISS_COOLDOWN_QUICK = 80;
	private static final int WINGS_COOLDOWN = 160;
	private static final int CHARM_COOLDOWN = 400;
	private static final int LULLABY_COOLDOWN = 600;
	private static final int BREAK_COOLDOWN = 6000;
	private static final int UNDYING_SLOT = 9;
	private static final int UNDYING_COOLDOWN = 24000;
	private static final int INFATUATION_TICKS = 120;
	private static final int INFATUATION_TICKS_LONG = 200;
	private static final int THRALL_TICKS = 200;
	private static final int THRALL_TICKS_LONG = 300;
	private static final int SLEEP_TICKS = 80;
	private static final int SLEEP_TICKS_LONG = 120;
	/** After the Wings, how long the second flight, the dive and the free kiss wait. */
	private static final int AFTER_WINGS = 60;
	/** A minute without a living soul near brings the longing. */
	private static final int LONELY_AFTER = 1200;
	private static final UUID FRAIL_HEALTH = UUID.fromString("2b6c3f1e-7d4a-4f0b-8e21-5a9c0d3e6f11");
	private static final UUID NIGHT_SPEED = UUID.fromString("2b6c3f1e-7d4a-4f0b-8e21-5a9c0d3e6f12");

	/** A creature won over by a Tempter (infatuated, a thrall or asleep): for whom, until when. */
	private record Spell(UUID tempter, long until) {}

	/** Infatuated: won't strike its Tempter and takes her blows a quarter harder. */
	private static final Map<UUID, Spell> INFATUATED = new HashMap<>();
	/** Thralls: fight the other monsters for their Tempter and never touch a player. */
	private static final Map<UUID, Spell> THRALLS = new HashMap<>();
	/** Asleep: no target, no step, until a blow (other than a kiss) wakes them. */
	private static final Map<UUID, Spell> ASLEEP = new HashMap<>();
	/** When each Tempter last spread her Wings (for the second flight, the dive and the free kiss). */
	private static final Map<UUID, Long> WINGS = new HashMap<>();
	private static final Map<UUID, Boolean> SECOND_FLIGHT = new HashMap<>();
	private static final Map<UUID, Long> LAST_COMPANY = new HashMap<>();

	private Tempter() {
	}

	public static void reset() {
		INFATUATED.clear();
		THRALLS.clear();
		ASLEEP.clear();
		WINGS.clear();
		SECOND_FLIGHT.clear();
		LAST_COMPANY.clear();
	}

	public static boolean is(PlayerEntity player) {
		return player instanceof ServerPlayerEntity sp && RaceState.raceOf(sp).orElse(null) == Race.TEMPTER;
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

	/** Whether a creature has a heart to win: no undead, no golems, no players. */
	public static boolean hearted(LivingEntity e) {
		return !e.isUndead() && !(e instanceof IronGolemEntity) && !(e instanceof PlayerEntity);
	}

	private static boolean held(Map<UUID, Spell> spells, Entity mob, long now) {
		Spell spell = spells.get(mob.getUuid());
		return spell != null && spell.until() > now;
	}

	private static boolean heldBy(Map<UUID, Spell> spells, Entity mob, PlayerEntity tempter, long now) {
		Spell spell = spells.get(mob.getUuid());
		return spell != null && spell.until() > now && spell.tempter().equals(tempter.getUuid());
	}

	public static boolean infatuated(Entity mob, PlayerEntity tempter) {
		return mob.getServer() != null && heldBy(INFATUATED, mob, tempter, now(mob.getServer()));
	}

	public static boolean asleep(Entity mob) {
		return mob.getServer() != null && held(ASLEEP, mob, now(mob.getServer()));
	}

	public static boolean thrall(Entity mob) {
		return mob.getServer() != null && held(THRALLS, mob, now(mob.getServer()));
	}

	/** Whether a creature leaves this player be: a sleeper and a thrall touch no one, the infatuated not their Tempter. */
	public static boolean blocksTarget(MobEntity mob, PlayerEntity target) {
		if (mob.getServer() == null) {
			return false;
		}
		long now = now(mob.getServer());
		return held(ASLEEP, mob, now) || held(THRALLS, mob, now) || heldBy(INFATUATED, mob, target, now);
	}

	private static boolean night(ServerWorld world) {
		return world.isNight() || world.getDimension().hasFixedTime();
	}

	// ---- once a second ----

	public static void tickSecond(ServerPlayerEntity player, RaceData data) {
		ServerWorld world = player.getServerWorld();
		long now = now(player.getServer());
		// Adoration: a slow trickle of her own, more from every heart she holds and every friendly face near
		int won = 0;
		for (MobEntity m : world.getEntitiesByClass(MobEntity.class, player.getBoundingBox().expand(16), m -> m.isAlive())) {
			if (heldBy(INFATUATED, m, player, now) || heldBy(THRALLS, m, player, now)) {
				won++;
			}
			if (heldBy(ASLEEP, m, player, now) && data.has(Talents.SLEEP_DREAM)) {
				// Sweet Dreams: the sleepers pay for their dreams
				m.damage(world.getDamageSources().indirectMagic(player, player), 1);
				won++;
			}
		}
		int friends = world.getEntitiesByClass(LivingEntity.class, player.getBoundingBox().expand(16),
				e -> e != player && e.isAlive() && (e instanceof PlayerEntity || e instanceof VillagerEntity)).size();
		boolean company = won > 0 || friends > 0 || !world.getEntitiesByClass(LivingEntity.class, player.getBoundingBox().expand(16),
				e -> e != player && e.isAlive() && !(e instanceof net.minecraft.entity.decoration.ArmorStandEntity)).isEmpty();
		if (company) {
			LAST_COMPANY.put(player.getUuid(), now);
		}
		long alone = now - LAST_COMPANY.computeIfAbsent(player.getUuid(), id -> now);
		float gain = 0.4f + won * 0.4f + friends * 0.3f;
		if (alone > LONELY_AFTER) {
			gain = -0.5f;
			refresh(player, StatusEffects.WEAKNESS, 0);
		}
		data.addResource(gain);

		// Frail: two hearts short of a plain human
		EntityAttributeInstance health = player.getAttributeInstance(EntityAttributes.GENERIC_MAX_HEALTH);
		if (health != null && health.getModifier(FRAIL_HEALTH) == null) {
			health.addTemporaryModifier(new EntityAttributeModifier(FRAIL_HEALTH, "tempter_frail", -4, EntityAttributeModifier.Operation.ADDITION));
			player.setHealth(Math.min(player.getHealth(), player.getMaxHealth()));
		}
		// Night Huntress: quicker under the moon
		EntityAttributeInstance speed = player.getAttributeInstance(EntityAttributes.GENERIC_MOVEMENT_SPEED);
		boolean nightSpeed = data.has(Talents.WING_NIGHT) && night(world);
		if (speed != null && nightSpeed != (speed.getModifier(NIGHT_SPEED) != null)) {
			if (nightSpeed) {
				speed.addTemporaryModifier(new EntityAttributeModifier(NIGHT_SPEED, "tempter_night", 0.1, EntityAttributeModifier.Operation.MULTIPLY_BASE));
			} else {
				speed.removeModifier(NIGHT_SPEED);
			}
		}
		// Village golems can't stand a Tempter
		for (IronGolemEntity golem : world.getEntitiesByClass(IronGolemEntity.class, player.getBoundingBox().expand(16),
				g -> g.isAlive() && !g.isPlayerCreated() && g.getTarget() == null)) {
			golem.setTarget(player);
		}
	}

	private static void refresh(ServerPlayerEntity player, StatusEffect effect, int amplifier) {
		StatusEffectInstance current = player.getStatusEffect(effect);
		if (current == null || current.getDuration() <= 30) {
			player.addStatusEffect(new StatusEffectInstance(effect, 60, amplifier, true, false, true));
		}
	}

	// ---- every tick ----

	public static void tick(MinecraftServer server) {
		long now = now(server);
		INFATUATED.values().removeIf(s -> s.until() < now);
		ASLEEP.values().removeIf(s -> s.until() < now);
		// A thrall whose charm ends stays infatuated a while (Lingering Heart)
		THRALLS.entrySet().removeIf(e -> {
			if (e.getValue().until() >= now) {
				return false;
			}
			ServerPlayerEntity tempter = server.getPlayerManager().getPlayer(e.getValue().tempter());
			if (tempter != null && RaceState.of(tempter).has(Talents.CHARM_HEART)) {
				INFATUATED.put(e.getKey(), new Spell(tempter.getUuid(), now + 200));
				RaceState.of(tempter).addResource(10);
			}
			return true;
		});
		for (ServerPlayerEntity player : server.getPlayerManager().getPlayerList()) {
			// Gliding Wings: held crouching in the air, she glides
			if (player.isSneaking() && !player.isOnGround() && player.getVelocity().y < 0 && is(player) && RaceState.of(player).has(Talents.WING_GLIDE)) {
				player.addStatusEffect(new StatusEffectInstance(StatusEffects.SLOW_FALLING, 10, 0, true, false, false));
			}
			Long flew = WINGS.get(player.getUuid());
			// Swooping Dive: the first landing after the Wings shakes the ground
			if (flew != null && now - flew > 4 && now - flew < 80 && player.isOnGround() && is(player)) {
				WINGS.remove(player.getUuid());
				if (RaceState.of(player).has(Talents.WING_DIVE)) {
					dive(player, RaceState.of(player));
				}
			}
		}
		if (now % 5 == 0) {
			holdSleepers(server, now);
		}
		if (now % 10 == 0) {
			leadThralls(server, now);
		}
	}

	private static void holdSleepers(MinecraftServer server, long now) {
		for (ServerWorld world : server.getWorlds()) {
			for (Map.Entry<UUID, Spell> e : ASLEEP.entrySet()) {
				if (world.getEntity(e.getKey()) instanceof MobEntity mob && mob.isAlive()) {
					mob.setTarget(null);
					mob.getNavigation().stop();
					mob.addStatusEffect(new StatusEffectInstance(StatusEffects.SLOWNESS, 10, 9, true, false));
					if (now % 20 == 0) {
						world.spawnParticles(ParticleTypes.NOTE, mob.getX(), mob.getY() + mob.getHeight() + 0.3, mob.getZ(), 1, 0.2, 0, 0.2, 0.5);
					}
				}
			}
		}
	}

	/** Thralls turn on the nearest monster that isn't another of hers, and keep near her otherwise. */
	private static void leadThralls(MinecraftServer server, long now) {
		for (ServerWorld world : server.getWorlds()) {
			for (Map.Entry<UUID, Spell> e : THRALLS.entrySet()) {
				if (!(world.getEntity(e.getKey()) instanceof MobEntity mob) || !mob.isAlive()) {
					continue;
				}
				ServerPlayerEntity tempter = server.getPlayerManager().getPlayer(e.getValue().tempter());
				if (tempter == null || tempter.getWorld() != world) {
					continue;
				}
				LivingEntity target = mob.getTarget();
				if (target == null || !target.isAlive() || target instanceof PlayerEntity || held(THRALLS, target, now)) {
					LivingEntity rival = world.getEntitiesByClass(MobEntity.class, mob.getBoundingBox().expand(12),
							o -> o != mob && o.isAlive() && o instanceof Monster && !held(THRALLS, o, now) && !held(ASLEEP, o, now)).stream()
							.min(Comparator.comparingDouble(o -> o.squaredDistanceTo(mob))).orElse(null);
					mob.setTarget(rival);
					if (rival == null && mob.squaredDistanceTo(tempter) > 64) {
						mob.getNavigation().startMovingTo(tempter, 1.2);
					}
				}
				// Bait: the monsters around the thrall go for it, not for her
				if (RaceState.of(tempter).has(Talents.CHARM_LURE)) {
					for (MobEntity other : world.getEntitiesByClass(MobEntity.class, mob.getBoundingBox().expand(8),
							o -> o != mob && o.isAlive() && o instanceof Monster && o.getTarget() == tempter && !held(THRALLS, o, now))) {
						other.setTarget(mob);
					}
				}
			}
		}
	}

	// ---- abilities ----

	public static AbilityResult use(ServerPlayerEntity player, RaceData data, int slot) {
		return switch (slot) {
			case 0 -> kiss(player, data);
			case 1 -> wings(player, data);
			case 2 -> charm(player, data);
			case 3 -> lullaby(player, data);
			case 4 -> data.has(Talents.THREAD_BREAK) ? breakHeart(player, data) : RaceData.talentLocked(player, Talents.THREAD_BREAK);
			default -> AbilityResult.LOCKED;
		};
	}

	public static int kissCost(RaceData data) {
		return data.has(Talents.KISS_CHEAP) ? KISS_COST_CHEAP : KISS_COST;
	}

	public static int wingsCost(RaceData data) {
		return data.has(Talents.WING_CHEAP) ? WINGS_COST_CHEAP : WINGS_COST;
	}

	public static int charmCost(RaceData data) {
		return data.has(Talents.CHARM_CHEAP) ? CHARM_COST_CHEAP : CHARM_COST;
	}

	public static double kissReach(RaceData data) {
		return data.has(Talents.KISS_REACH) ? 12 : 8;
	}

	private static boolean justFlew(ServerPlayerEntity player) {
		Long flew = WINGS.get(player.getUuid());
		return flew != null && now(player.getServer()) - flew <= AFTER_WINGS;
	}

	/**
	 * The Kiss (R): a rush to the creature you look at and a kiss that drinks 6 of its life into yours (twice as much
	 * from a sleeper, half again by night with the Succubus crown); it stays infatuated for 6 seconds. No heart, no kiss.
	 * With Kiss of Life, R at a player heals them instead.
	 */
	public static AbilityResult kiss(ServerPlayerEntity player, RaceData data) {
		if (!ready(player, data, 0)) {
			return AbilityResult.COOLDOWN;
		}
		boolean free = data.has(Talents.WING_KISS) && justFlew(player);
		int cost = free ? 0 : kissCost(data);
		if (data.resource < cost) {
			return AbilityResult.NO_RESOURCE;
		}
		LivingEntity target = lookedAt(player, kissReach(data), e -> !(e instanceof TameableEntity tame && tame.isTamed())
				&& (data.has(Talents.KISS_ALLY) || !(e instanceof PlayerEntity)));
		if (target == null) {
			return AbilityResult.NO_SIGHT;
		}
		ServerWorld world = player.getServerWorld();
		boolean moon = data.has(Talents.WING_SUCCUBUS) && night(world);
		int cd = data.has(Talents.KISS_QUICK) ? KISS_COOLDOWN_QUICK : KISS_COOLDOWN;
		if (target instanceof PlayerEntity friend) {
			data.addResource(-cost);
			cooldown(player, data, 0, moon ? cd * 3 / 4 : cd);
			friend.heal(6);
			friend.addStatusEffect(new StatusEffectInstance(StatusEffects.REGENERATION, 100, 0, true, true, true));
			Fx.hearts(world, player, friend, 6, 24, ROSE_RGB);
			world.spawnParticles(ParticleTypes.HEART, friend.getX(), friend.getY() + friend.getHeight() + 0.2, friend.getZ(), 4, 0.3, 0.2, 0.3, 0);
			world.playSound(null, player.getBlockPos(), SoundEvents.ENTITY_ALLAY_ITEM_GIVEN, SoundCategory.PLAYERS, 1.0f, 1.2f);
			return AbilityResult.OK;
		}
		if (!hearted(target)) {
			player.sendMessage(Text.translatable("mages_legacy.tempter.no_heart"), true);
			return AbilityResult.HINTED;
		}
		data.addResource(-cost);
		cooldown(player, data, 0, moon ? cd * 3 / 4 : cd);
		long now = now(player.getServer());
		boolean sleeper = held(ASLEEP, target, now);
		// The rush: up to the creature's side
		Vec3d side = target.getPos().add(player.getPos().subtract(target.getPos()).normalize().multiply(target.getWidth() / 2 + 0.8));
		Vec3d from = player.getPos();
		player.requestTeleport(side.x, target.getY(), side.z);
		float drain = (data.has(Talents.KISS_DEEP) ? 9 : 6) * (sleeper ? 2 : 1) * (moon ? 1.5f : 1);
		target.damage(world.getDamageSources().indirectMagic(player, player), drain);
		player.heal(drain);
		// The kiss doesn't wake a sleeper: it deepens the dream into love
		INFATUATED.put(target.getUuid(), new Spell(player.getUuid(), now + (data.has(Talents.KISS_CHARM) ? INFATUATION_TICKS_LONG : INFATUATION_TICKS)));
		if (target instanceof MobEntity mob && mob.getTarget() == player) {
			mob.setTarget(null);
		}
		if (data.has(Talents.KISS_POISON)) {
			target.addStatusEffect(new StatusEffectInstance(StatusEffects.POISON, 80, 1));
		}
		if (data.has(Talents.KISS_SHIELD)) {
			player.setAbsorptionAmount(Math.min(8, player.getAbsorptionAmount() + 4));
		}
		if (data.has(Talents.KISS_SHARE)) {
			for (PlayerEntity friend : world.getEntitiesByClass(PlayerEntity.class, player.getBoundingBox().expand(6), p -> p != player && p.isAlive())) {
				friend.heal(drain / 2);
			}
		}
		if (data.has(Talents.WING_RUSH)) {
			player.addStatusEffect(new StatusEffectInstance(StatusEffects.SPEED, 60, 1, true, true, true));
		}
		Progression.addXp(player, data, "tempter_kiss", 1, 30);
		// A rose streak where she went; over the kissed a heart beats once, and its life flows to her as little hearts
		Fx.ribbon(world, List.of(from, player.getPos()), 12, WINE_INK, ROSE_RGB, 1.0f);
		Fx.hearts(world, target, player, sleeper ? 10 : 6, 24, ROSE_RGB);
		Fx.pulse(player, 2, 20, ROSE_RGB);
		world.spawnParticles(ParticleTypes.HEART, target.getX(), target.getY() + target.getHeight() + 0.2, target.getZ(), sleeper ? 6 : 3, 0.3, 0.2, 0.3, 0);
		world.playSound(null, player.getBlockPos(), SoundEvents.ENTITY_GENERIC_DRINK, SoundCategory.PLAYERS, 0.8f, 1.5f);
		world.playSound(null, player.getBlockPos(), SoundEvents.ENTITY_ALLAY_AMBIENT_WITH_ITEM, SoundCategory.PLAYERS, 1.0f, 0.7f);
		return AbilityResult.OK;
	}

	/**
	 * The Wings (V): a leap through the air the way she looks, and a slow fall after. With Second Flight the next one
	 * within 3 seconds is free.
	 */
	public static AbilityResult wings(ServerPlayerEntity player, RaceData data) {
		long now = now(player.getServer());
		boolean second = data.has(Talents.WING_TWICE) && justFlew(player) && SECOND_FLIGHT.getOrDefault(player.getUuid(), false);
		if (!second && !ready(player, data, 1)) {
			return AbilityResult.COOLDOWN;
		}
		int cost = second ? 0 : wingsCost(data);
		if (data.resource < cost) {
			return AbilityResult.NO_RESOURCE;
		}
		ServerWorld world = player.getServerWorld();
		data.addResource(-cost);
		if (!second) {
			int cd = data.has(Talents.WING_NIGHT) && night(world) ? WINGS_COOLDOWN / 2 : WINGS_COOLDOWN;
			cooldown(player, data, 1, cd);
		}
		SECOND_FLIGHT.put(player.getUuid(), !second);
		WINGS.put(player.getUuid(), now);
		Vec3d look = player.getRotationVec(1.0f);
		double power = data.has(Talents.WING_FAR) ? 1.6 : 1.2;
		Vec3d flat = new Vec3d(look.x, 0, look.z);
		flat = flat.lengthSquared() < 1.0E-4 ? Vec3d.ZERO : flat.normalize();
		player.setVelocity(flat.x * power, 0.55 + Math.max(0, look.y) * 0.5, flat.z * power);
		player.velocityModified = true;
		player.fallDistance = 0;
		player.addStatusEffect(new StatusEffectInstance(StatusEffects.SLOW_FALLING, 50, 0, true, false, true));
		// Dark wings unfold from her back and beat once; the air rings where she sprang
		Fx.wings(world, player, 1.5f, 36, ROSE_RGB);
		Fx.ring(world, player.getPos(), 0.4f, 3.0f, 10, ROSE_RGB);
		Fx.fov(player, 0.15f, 10);
		world.playSound(null, player.getBlockPos(), SoundEvents.ENTITY_ENDER_DRAGON_FLAP, SoundCategory.PLAYERS, 0.6f, 1.6f);
		return AbilityResult.OK;
	}

	/** Swooping Dive: landing after the Wings knocks the creatures around back for 5, and with Swoon infatuates them. */
	private static void dive(ServerPlayerEntity player, RaceData data) {
		ServerWorld world = player.getServerWorld();
		long now = now(player.getServer());
		for (LivingEntity e : world.getEntitiesByClass(LivingEntity.class, player.getBoundingBox().expand(3),
				e -> e != player && e.isAlive() && !(e instanceof PlayerEntity) && !held(THRALLS, e, now) && !(e instanceof TameableEntity t && t.isTamed()))) {
			e.damage(world.getDamageSources().indirectMagic(player, player), 5);
			Vec3d push = e.getPos().subtract(player.getPos()).normalize();
			e.addVelocity(push.x * 0.8, 0.3, push.z * 0.8);
			e.velocityModified = true;
			if (data.has(Talents.WING_SWOON) && hearted(e)) {
				INFATUATED.put(e.getUuid(), new Spell(player.getUuid(), now + INFATUATION_TICKS));
				if (e instanceof MobEntity mob && mob.getTarget() == player) {
					mob.setTarget(null);
				}
			}
		}
		Fx.ring(world, player.getPos(), 0.5f, 4, 12, ROSE_RGB);
		Fx.shake(player, 1.2f, 8);
		world.playSound(null, player.getBlockPos(), SoundEvents.ENTITY_GENERIC_SMALL_FALL, SoundCategory.PLAYERS, 1.2f, 0.6f);
	}

	/** The Charm (Z): the creature you look at (16 blocks) becomes a thrall for 10 seconds and fights the other monsters. */
	public static AbilityResult charm(ServerPlayerEntity player, RaceData data) {
		if (!ready(player, data, 2)) {
			return AbilityResult.COOLDOWN;
		}
		int cost = charmCost(data);
		if (data.resource < cost) {
			return AbilityResult.NO_RESOURCE;
		}
		LivingEntity target = lookedAt(player, 16, e -> e instanceof MobEntity && !(e instanceof TameableEntity tame && tame.isTamed()));
		if (!(target instanceof MobEntity mob)) {
			return AbilityResult.NO_SIGHT;
		}
		if (!hearted(mob)) {
			player.sendMessage(Text.translatable("mages_legacy.tempter.no_heart"), true);
			return AbilityResult.HINTED;
		}
		data.addResource(-cost);
		cooldown(player, data, 2, CHARM_COOLDOWN);
		long now = now(player.getServer());
		// One thrall at a time (two with Two Hearts): a new one lets the oldest go
		int allowed = data.has(Talents.CHARM_MORE) ? 2 : 1;
		List<UUID> mine = new ArrayList<>(THRALLS.entrySet().stream().filter(e -> e.getValue().tempter().equals(player.getUuid()))
				.sorted(Comparator.comparingLong(e -> e.getValue().until())).map(Map.Entry::getKey).toList());
		while (mine.size() >= allowed) {
			THRALLS.remove(mine.remove(0));
		}
		int ticks = data.has(Talents.CHARM_LONG) ? THRALL_TICKS_LONG : THRALL_TICKS;
		THRALLS.put(mob.getUuid(), new Spell(player.getUuid(), now + ticks));
		ASLEEP.remove(mob.getUuid());
		mob.setTarget(null);
		if (data.has(Talents.CHARM_STRONG)) {
			mob.addStatusEffect(new StatusEffectInstance(StatusEffects.STRENGTH, ticks, 0));
			mob.addStatusEffect(new StatusEffectInstance(StatusEffects.RESISTANCE, ticks, 0));
		}
		ServerWorld world = player.getServerWorld();
		// Her eyes on it, and a heart that turns over its head for as long as it is hers
		Fx.hearts(world, player, mob, 4, 16, ROSE_RGB);
		Fx.sigil(world, mob, Fx.SIGIL_HEART, ticks, ROSE_RGB);
		Fx.aura(world, mob, ticks, ROSE_RGB, Fx.MOTES);
		world.spawnParticles(ParticleTypes.HEART, mob.getX(), mob.getY() + mob.getHeight() + 0.2, mob.getZ(), 5, 0.3, 0.2, 0.3, 0);
		world.playSound(null, mob.getBlockPos(), SoundEvents.BLOCK_AMETHYST_BLOCK_CHIME, SoundCategory.PLAYERS, 1.2f, 0.8f);
		world.playSound(null, player.getBlockPos(), SoundEvents.ENTITY_EVOKER_PREPARE_WOLOLO, SoundCategory.PLAYERS, 0.7f, 1.6f);
		return AbilityResult.OK;
	}

	/** The Lullaby (H): every monster with a heart within 8 blocks falls asleep for 4 seconds; any blow but a kiss wakes it. */
	public static AbilityResult lullaby(ServerPlayerEntity player, RaceData data) {
		if (!ready(player, data, 3)) {
			return AbilityResult.COOLDOWN;
		}
		if (data.resource < LULLABY_COST) {
			return AbilityResult.NO_RESOURCE;
		}
		ServerWorld world = player.getServerWorld();
		long now = now(player.getServer());
		double radius = data.has(Talents.SLEEP_WIDE) ? 12 : 8;
		List<MobEntity> sleepers = world.getEntitiesByClass(MobEntity.class, player.getBoundingBox().expand(radius),
				m -> m.isAlive() && m instanceof Monster && hearted(m) && !held(THRALLS, m, now) && m.squaredDistanceTo(player) <= radius * radius);
		if (sleepers.isEmpty()) {
			return AbilityResult.NO_ENEMIES;
		}
		data.addResource(-LULLABY_COST);
		cooldown(player, data, 3, LULLABY_COOLDOWN);
		long until = now + (data.has(Talents.SLEEP_LONG) ? SLEEP_TICKS_LONG : SLEEP_TICKS);
		for (MobEntity m : sleepers) {
			ASLEEP.put(m.getUuid(), new Spell(player.getUuid(), until));
			m.setTarget(null);
			m.getNavigation().stop();
			Fx.sigil(world, m, Fx.SIGIL_MOON, (int) (until - now), DREAM_RGB);
		}
		Fx.lullaby(world, player.getPos(), (float) radius, 50, DREAM_RGB);
		world.playSound(null, player.getBlockPos(), SoundEvents.BLOCK_NOTE_BLOCK_CHIME.value(), SoundCategory.PLAYERS, 1.2f, 0.7f);
		world.playSound(null, player.getBlockPos(), SoundEvents.BLOCK_AMETHYST_BLOCK_RESONATE, SoundCategory.PLAYERS, 1.0f, 0.6f);
		return AbilityResult.OK;
	}

	/**
	 * The Broken Heart (Alt, the Charm's crown): every heart she has won within 16 blocks (infatuated, a thrall or asleep
	 * under her song) breaks: 8 damage and a quarter of its life, 30 at most, and it staggers.
	 */
	public static AbilityResult breakHeart(ServerPlayerEntity player, RaceData data) {
		if (!ready(player, data, 4)) {
			return AbilityResult.COOLDOWN;
		}
		if (data.resource < BREAK_COST) {
			return AbilityResult.NO_RESOURCE;
		}
		ServerWorld world = player.getServerWorld();
		long now = now(player.getServer());
		List<MobEntity> won = world.getEntitiesByClass(MobEntity.class, player.getBoundingBox().expand(16),
				m -> m.isAlive() && (heldBy(INFATUATED, m, player, now) || heldBy(THRALLS, m, player, now) || heldBy(ASLEEP, m, player, now)));
		if (won.isEmpty()) {
			player.sendMessage(Text.translatable("mages_legacy.tempter.no_hearts"), true);
			return AbilityResult.HINTED;
		}
		data.addResource(-BREAK_COST);
		cooldown(player, data, 4, BREAK_COOLDOWN);
		for (MobEntity m : won) {
			INFATUATED.remove(m.getUuid());
			THRALLS.remove(m.getUuid());
			ASLEEP.remove(m.getUuid());
			m.damage(world.getDamageSources().indirectMagic(player, player), Math.min(30, 8 + m.getMaxHealth() * 0.25f));
			m.addStatusEffect(new StatusEffectInstance(StatusEffects.SLOWNESS, 40, 3));
			Fx.heartbreak(world, m, 30, ROSE_RGB);
		}
		Fx.ring(world, player.getPos(), 0.5f, 16, 18, ROSE_RGB);
		Fx.burst(world, player.getPos().add(0, 1, 0), 3.0f, 10, WINE_INK);
		Fx.screen(player, 18, ROSE_RGB);
		Fx.shake(player, 2.0f, 12);
		world.playSound(null, player.getBlockPos(), SoundEvents.BLOCK_GLASS_BREAK, SoundCategory.PLAYERS, 1.2f, 0.5f);
		world.playSound(null, player.getBlockPos(), SoundEvents.ENTITY_WARDEN_HEARTBEAT, SoundCategory.PLAYERS, 1.5f, 0.6f);
		return AbilityResult.OK;
	}

	private static LivingEntity lookedAt(ServerPlayerEntity player, double range, java.util.function.Predicate<LivingEntity> filter) {
		Vec3d eye = player.getEyePos();
		Vec3d look = player.getRotationVec(1.0f);
		Vec3d end = eye.add(look.multiply(range));
		Box box = player.getBoundingBox().stretch(look.multiply(range)).expand(1);
		EntityHitResult hit = ProjectileUtil.raycast(player, eye, end, box,
				e -> e instanceof LivingEntity living && living.isAlive() && e != player && filter.test(living), range * range);
		return hit != null && hit.getEntity() instanceof LivingEntity living ? living : null;
	}

	// ---- damage ----

	/** A fall on a Tempter: Soft Wings halve it. */
	public static float modifyDamage(ServerPlayerEntity player, DamageSource source, float amount) {
		RaceData data = RaceState.of(player);
		if (data != null && data.has(Talents.WING_SOFT) && source.isIn(DamageTypeTags.IS_FALL)) {
			return amount * 0.5f;
		}
		return amount;
	}

	/**
	 * Any creature's blow, for the Tempters' sake: a blow wakes a sleeper (her kiss doesn't); her own blows strike the
	 * infatuated a quarter harder (and heal her a little with the Lover's Bite).
	 */
	public static float onHit(LivingEntity victim, DamageSource source, float amount) {
		if (amount <= 0 || victim.getServer() == null) {
			return amount;
		}
		Entity attacker = source.getAttacker();
		boolean kiss = attacker instanceof ServerPlayerEntity p && is(p) && source.isOf(DamageTypes.INDIRECT_MAGIC);
		if (!kiss) {
			ASLEEP.remove(victim.getUuid());
		}
		if (attacker instanceof ServerPlayerEntity player && is(player) && infatuated(victim, player)) {
			amount *= 1.25f;
			RaceData data = RaceState.of(player);
			if (source.isOf(DamageTypes.PLAYER_ATTACK) && source.getSource() == player) {
				data.addResource(3);
				if (data.has(Talents.KISS_LIFE)) {
					player.heal(amount * 0.15f);
				}
			}
		}
		return amount;
	}

	/** A monster died: by a thrall's hand (Grateful Thrall pays), or won by her (the hunt pays). */
	public static void onDeath(LivingEntity victim, DamageSource source) {
		if (!(victim instanceof Monster) || victim.getServer() == null) {
			return;
		}
		long now = now(victim.getServer());
		Spell spell = THRALLS.get(victim.getUuid());
		if (spell == null) {
			spell = INFATUATED.get(victim.getUuid());
		}
		INFATUATED.remove(victim.getUuid());
		THRALLS.remove(victim.getUuid());
		ASLEEP.remove(victim.getUuid());
		if (spell != null && spell.until() > now) {
			ServerPlayerEntity tempter = victim.getServer().getPlayerManager().getPlayer(spell.tempter());
			if (tempter != null) {
				Progression.addXp(tempter, RaceState.of(tempter), Progression.HUNT, 2, Progression.HUNT_CAP);
				if (RaceState.of(tempter).has(Talents.WING_SUCCUBUS)) {
					// The Succubus: every heart won and taken lets her fly again at once
					RaceState.of(tempter).readyAt.put(1, 0L);
					RaceState.of(tempter).dirty = true;
				}
			}
		}
		Spell master = source.getAttacker() == null ? null : THRALLS.get(source.getAttacker().getUuid());
		ServerPlayerEntity tempter = master == null ? null : victim.getServer().getPlayerManager().getPlayer(master.tempter());
		if (tempter != null) {
			RaceData data = RaceState.of(tempter);
			Progression.addXp(tempter, data, Progression.HUNT, 2, Progression.HUNT_CAP);
			if (data.has(Talents.CHARM_RETURN)) {
				data.addResource(5);
				tempter.heal(2);
			}
		}
	}

	/** The Undying (the Kiss's crown): a killing blow takes the life of the nearest creature with a heart instead, once in 20 minutes. */
	public static boolean allowDeath(ServerPlayerEntity player) {
		RaceData data = RaceState.of(player);
		if (data == null || data.race != Race.TEMPTER || !data.has(Talents.KISS_UNDYING) || !ready(player, data, UNDYING_SLOT)) {
			return true;
		}
		ServerWorld world = player.getServerWorld();
		LivingEntity instead = world.getEntitiesByClass(MobEntity.class, player.getBoundingBox().expand(8), m -> m.isAlive() && hearted(m)).stream()
				.min(Comparator.comparingDouble(m -> m.squaredDistanceTo(player))).orElse(null);
		if (instead == null) {
			return true;
		}
		instead.damage(world.getDamageSources().indirectMagic(player, player), Float.MAX_VALUE);
		player.setHealth(player.getMaxHealth() * 0.3f);
		data.readyAt.put(UNDYING_SLOT, now(player.getServer()) + UNDYING_COOLDOWN);
		data.dirty = true;
		Fx.hearts(world, instead, player, 12, 30, ROSE_RGB);
		Fx.heartbreak(world, instead, 30, ROSE_RGB);
		Fx.screen(player, 24, ROSE_RGB);
		world.playSound(null, player.getBlockPos(), SoundEvents.ITEM_TOTEM_USE, SoundCategory.PLAYERS, 0.6f, 1.5f);
		player.sendMessage(Text.translatable("mages_legacy.tempter.undying").formatted(Formatting.LIGHT_PURPLE), false);
		return false;
	}

	/** Takes the Tempter's spells and changes away (the race was reset or changed). */
	public static void cleanup(ServerPlayerEntity player) {
		UUID id = player.getUuid();
		INFATUATED.values().removeIf(s -> s.tempter().equals(id));
		THRALLS.values().removeIf(s -> s.tempter().equals(id));
		ASLEEP.values().removeIf(s -> s.tempter().equals(id));
		WINGS.remove(id);
		SECOND_FLIGHT.remove(id);
		EntityAttributeInstance health = player.getAttributeInstance(EntityAttributes.GENERIC_MAX_HEALTH);
		if (health != null) {
			health.removeModifier(FRAIL_HEALTH);
		}
		EntityAttributeInstance speed = player.getAttributeInstance(EntityAttributes.GENERIC_MOVEMENT_SPEED);
		if (speed != null) {
			speed.removeModifier(NIGHT_SPEED);
		}
	}

	/** What the HUD needs: how many hearts she holds now. */
	public static NbtCompound clientInfo(ServerPlayerEntity player) {
		NbtCompound nbt = new NbtCompound();
		long now = now(player.getServer());
		int hearts = 0;
		for (Map<UUID, Spell> spells : List.of(INFATUATED, THRALLS)) {
			for (Spell s : spells.values()) {
				if (s.tempter().equals(player.getUuid()) && s.until() > now) {
					hearts++;
				}
			}
		}
		nbt.putInt("hearts", hearts);
		return nbt;
	}
}
