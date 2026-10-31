package euf.mageslegacy.bloodkin;

import euf.mageslegacy.fx.Fx;
import euf.mageslegacy.mycelid.Allies;
import euf.mageslegacy.race.Abilities;
import euf.mageslegacy.race.Ability;
import euf.mageslegacy.race.Awakenings;
import euf.mageslegacy.race.Deeds;
import euf.mageslegacy.race.Mastery;
import euf.mageslegacy.race.Progression;
import euf.mageslegacy.race.Race;
import euf.mageslegacy.race.RaceState;
import euf.mageslegacy.race.Talent;
import euf.mageslegacy.race.Talents;
import euf.mageslegacy.voidborn.Voidborn;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.tags.EntityTypeTags;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.core.Holder;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.OwnableEntity;
import net.minecraft.world.entity.animal.AbstractGolem;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.entity.monster.Blaze;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.monster.Shulker;
import net.minecraft.world.entity.monster.Slime;
import net.minecraft.world.entity.monster.Vex;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.food.FoodProperties;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.common.Tags;
import net.neoforged.neoforge.event.entity.living.LivingDamageEvent;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.entity.living.LivingEntityUseItemEvent;
import net.neoforged.neoforge.event.entity.living.LivingHealEvent;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;
import net.neoforged.neoforge.event.entity.player.AttackEntityEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

/**
 * The Bloodkin: blood as the resource (0 to 100), which only the hunt fills and which slowly drains away; vampirism on
 * direct blows; Thirst under 20 blood (harder blows and speed, but healing halved); the sun dries their blood and
 * burns a bare head; ordinary food feeds them a third. The Blood Dash (R), the Harvest (V), the Sacrifice or, looking
 * at a friend, a Transfusion (H); the Awakenings: the Feast, the Blood Storm and the Prince of the Night, whose Bat Swarm
 * the key calls and whose Last Breath saves its body at the edge of death by itself (two cooldowns of their own).
 * Everything here is worked out by the server; the clients only draw it.
 */
public final class Bloodkin {
	public static final int THIRST_BELOW = 20;
	private static final float DRAIN = 0.3f;
	/** No creature gives more than this much blood in all (a farm of one mob, a dummy that never dies). */
	private static final float VICTIM_CAP = 20;
	private static final int DASH_TICKS = 5;
	private static final int DOUBLE_WINDOW = 60;
	private static final float DASH_DAMAGE = 6;
	private static final int TRANSFUSE_COST = 25;
	private static final int TRANSFUSE_COOLDOWN = 10 * 20;
	private static final int OFFERING_TICKS = 5 * 20;
	private static final int MARK_TICKS = 6 * 20;
	private static final int FEAST_TICKS = 10 * 20;
	private static final int FEAST_LONG = 14 * 20;
	private static final int STORM_TICKS = 6 * 20;
	private static final int STORM_LONG = 9 * 20;
	/** The Bat Swarm: 3 s (5 with Long Flight); the Last Breath's own is always 3 s. */
	private static final int SWARM_TICKS = 3 * 20;
	private static final int SWARM_LONG = 5 * 20;
	private static final double SWARM_SPEED = 0.6;
	/** The Last Breath: at or under a tenth of health, up to 30% (50% with a Second Heart), once in 20 minutes. */
	public static final int RESCUE_COOLDOWN = 20 * 60 * 20;
	private static final float RESCUE_AT = 0.1f;
	private static final int SHADOW_TICKS = 3 * 20;
	/** Trial counters, in the progress tag. */
	public static final String DASH_MULTI = "blood_dash_multi";
	public static final String HARVEST_GROUP = "blood_harvest_group";
	public static final String VAMP_HEAL = "blood_vamp_heal";
	public static final String SACRIFICES = "blood_sacrifices";
	public static final int FEAST_TRIAL = 15;
	public static final int STORM_TRIAL = 20;
	public static final int PRINCE_HEAL = 100;
	public static final int PRINCE_SACRIFICES = 10;

	/** A Blood Dash under way: its path, whom it has cut, whether it was the free second one, if it began in the air. */
	private record Dash(List<Vec3> path, Set<UUID> hit, boolean free, boolean dive) {
	}

	/** A bleeding wound: in which world, until when, how much a second, whose. */
	private record Bleed(ResourceKey<Level> level, long until, float perSecond, UUID owner) {
	}

	/** The Bat Swarm a body is scattered into: until when, whether it is the Last Breath's, whom it has bitten. */
	private static final class Swarm {
		final long until;
		final boolean rescue;
		final Set<UUID> bitten = new HashSet<>();
		float drunk;

		Swarm(long until, boolean rescue) {
			this.until = until;
			this.rescue = rescue;
		}
	}

	/** A Bloodkin's live state, one per player, never shared. */
	private static final class State {
		Dash dash;
		int dashStep;
		long secondDash;
		long offering;
		long feast;
		float feastHealed;
		long storm;
		final Set<UUID> stormCaught = new HashSet<>();
		Swarm swarm;
		int blows;
		float drunkXp;
		float healed;
		long thirstAt;
		boolean bitOnce;
	}

	private static final Map<UUID, State> STATES = new HashMap<>();
	private static final Map<UUID, Bleed> BLEEDING = new HashMap<>();
	private static final Map<UUID, Long> MARKED = new HashMap<>();
	/** How much blood each creature has given (by creature), and when last. */
	private static final Map<UUID, float[]> GIVEN = new HashMap<>();
	/** Set while a Bloodkin's own healing runs, so Thirst and the sun don't halve it. */
	private static boolean ownHeal;

	private Bloodkin() {
	}

	public static void register() {
		NeoForge.EVENT_BUS.addListener(Bloodkin::onPlayerTick);
		NeoForge.EVENT_BUS.addListener(Bloodkin::onServerTick);
		NeoForge.EVENT_BUS.addListener(Bloodkin::onIncoming);
		NeoForge.EVENT_BUS.addListener(EventPriority.LOW, Bloodkin::onDamagePre);
		NeoForge.EVENT_BUS.addListener(Bloodkin::onDamaged);
		NeoForge.EVENT_BUS.addListener(Bloodkin::onDeath);
		NeoForge.EVENT_BUS.addListener(Bloodkin::onHeal);
		NeoForge.EVENT_BUS.addListener(Bloodkin::onEat);
		NeoForge.EVENT_BUS.addListener(Bloodkin::onAttack);
		NeoForge.EVENT_BUS.addListener(Bloodkin::onUseItem);
		NeoForge.EVENT_BUS.addListener(Bloodkin::onUseBlock);
		NeoForge.EVENT_BUS.addListener(Bloodkin::onInteract);
		NeoForge.EVENT_BUS.addListener((PlayerEvent.PlayerLoggedOutEvent e) -> {
			if (e.getEntity() instanceof ServerPlayer player) {
				endSwarm(player, false);
				STATES.remove(player.getUUID());
			}
		});
		NeoForge.EVENT_BUS.addListener((PlayerEvent.PlayerChangedDimensionEvent e) -> {
			if (e.getEntity() instanceof ServerPlayer player) {
				endSwarm(player, true);
				state(player).dash = null;
			}
		});
	}

	private static State state(Player player) {
		return STATES.computeIfAbsent(player.getUUID(), id -> new State());
	}

	public static boolean is(Player player) {
		return player instanceof ServerPlayer sp && RaceState.is(sp, Race.BLOODKIN);
	}

	private static long now(ServerPlayer player) {
		return player.serverLevel().getGameTime();
	}

	// ---- Who has blood, who is prey -----------------------------------------------------------------------------

	/**
	 * Whether a creature has blood at all: not the undead, golems, slimes, blazes, vexes, shulkers or armour stands.
	 * (Alive or not: a creature just killed still had blood.)
	 */
	public static boolean bloodful(Entity e) {
		return e instanceof LivingEntity && !e.getType().is(EntityTypeTags.UNDEAD) && !(e instanceof AbstractGolem)
				&& !(e instanceof ArmorStand) && !(e instanceof Slime) && !(e instanceof Blaze) && !(e instanceof Vex) && !(e instanceof Shulker);
	}

	/**
	 * Prey for the Dash, the Harvest, the Storm and the Swarm: a monster, a mob hunting a player, or a player who is no
	 * ally (PvP, see {@link Allies}); never one's own pets, other tamed creatures or allies.
	 */
	static boolean prey(LivingEntity e, ServerPlayer player) {
		if (e == player || !e.isAlive() || e instanceof ArmorStand) {
			return false;
		}
		if (e instanceof Player p) {
			return !p.isSpectator() && !p.isCreative() && !Allies.ally(player, p) && player.server.isPvpAllowed();
		}
		if (e instanceof OwnableEntity owned && owned.getOwnerUUID() != null) {
			return false;
		}
		return e instanceof Enemy || e instanceof Mob mob && mob.getTarget() instanceof Player;
	}

	public static boolean isNight(Level level) {
		return level.dimensionType().hasSkyLight() && !level.isDay();
	}

	public static boolean thirsty(ServerPlayer player) {
		return RaceState.resource(player) < THIRST_BELOW;
	}

	/** In direct sunlight: day, no rain, the open sky above the eyes. */
	public static boolean sunlit(ServerPlayer player) {
		Level level = player.level();
		return level.dimensionType().hasSkyLight() && level.isDay() && !level.isRaining() && !player.isInWaterOrRain()
				&& level.canSeeSky(BlockPos.containing(player.getEyePosition()));
	}

	private static void heal(LivingEntity e, float amount) {
		if (amount <= 0) {
			return;
		}
		ownHeal = true;
		try {
			e.heal(amount);
		} finally {
			ownHeal = false;
		}
	}

	/**
	 * Blood drunk from a creature: into the resource, never more than {@link #VICTIM_CAP} from one creature in all (a
	 * farm of one mob gives little), half from what isn't a monster; every 10 drops of a monster's blood a point of race
	 * experience.
	 */
	private static float drink(ServerPlayer player, LivingEntity from, float blood) {
		if (blood <= 0) {
			return 0;
		}
		boolean monster = from instanceof Enemy || from instanceof Player;
		if (!monster) {
			blood *= 0.5f;
		}
		float[] given = GIVEN.computeIfAbsent(from.getUUID(), id -> new float[2]);
		blood = Math.min(blood, VICTIM_CAP - given[0]);
		if (blood <= 0) {
			return 0;
		}
		given[0] += blood;
		given[1] = now(player);
		RaceState.setResource(player, RaceState.resource(player) + blood);
		if (monster) {
			State s = state(player);
			s.drunkXp += blood;
			int xp = (int) (s.drunkXp / 10);
			s.drunkXp -= xp * 10;
			if (xp > 0) {
				Progression.addXp(player, "blood_drink", xp, 40);
			}
		}
		return blood;
	}

	// ---- Every second ---------------------------------------------------------------------------------------------

	private static void onPlayerTick(PlayerTickEvent.Post event) {
		if (!(event.getEntity() instanceof ServerPlayer player)) {
			return;
		}
		State s = STATES.get(player.getUUID());
		if (!RaceState.is(player, Race.BLOODKIN)) {
			if (s != null) {
				endSwarm(player, true);
				STATES.remove(player.getUUID());
			}
			return;
		}
		s = state(player);
		if (s.dash != null) {
			tickDash(player, s);
		}
		if (s.swarm != null) {
			tickSwarm(player, s);
		}
		if (s.storm > 0) {
			tickStorm(player, s);
		}
		if (player.tickCount % 20 == 0) {
			tickSecond(player, s);
		}
	}

	private static void tickSecond(ServerPlayer player, State s) {
		ServerLevel level = player.serverLevel();
		boolean night = isNight(level);
		boolean dark = night || level.getMaxLocalRawBrightness(BlockPos.containing(player.getEyePosition())) <= 7;
		// Blood drains away by itself: Restraint halves it, Night Blood stops it at night
		float drain = Talents.has(player, Talent.THIRST_SLOW) ? DRAIN / 2 : DRAIN;
		if (night && Talents.has(player, Talent.NIGHT_BLOOD)) {
			drain = 0;
			refresh(player, MobEffects.REGENERATION, 0, 60, 20);
		}
		boolean sun = sunlit(player);
		if (sun) {
			// The sun dries the blood twice as fast (a Twilight Cloak only half again) and burns a bare head
			drain += Talents.has(player, Talent.NIGHT_CLOAK) ? DRAIN / 2 : DRAIN;
			if (player.getItemBySlot(EquipmentSlot.HEAD).isEmpty()) {
				if (Talents.has(player, Talent.NIGHT_CLOAK)) {
					refresh(player, MobEffects.WEAKNESS, 0, 60, 30);
				} else if (!Talents.has(player, Talent.NIGHT_SHADE) || player.tickCount / 20 % 2 == 0) {
					player.igniteForSeconds(3);
				}
			}
		}
		RaceState.setResource(player, RaceState.resource(player) - drain);
		boolean thirst = thirsty(player);
		if (thirst) {
			refresh(player, MobEffects.MOVEMENT_SPEED, 0, 40, 20);
			if (s.thirstAt == 0) {
				s.thirstAt = now(player);
			}
		} else if (s.thirstAt > 0 && RaceState.resource(player) >= 80) {
			// Blood as a weapon: from Thirst back to a full vein within 30 seconds
			if (now(player) - s.thirstAt <= 30 * 20) {
				Mastery.add(player, Mastery.Goal.BLOOD_TIDE);
			}
			s.thirstAt = 0;
		} else if (s.thirstAt > 0 && now(player) - s.thirstAt > 30 * 20 && !thirst) {
			s.thirstAt = 0;
		}
		if (dark && Talents.has(player, Talent.NIGHT_EYES)) {
			refresh(player, MobEffects.NIGHT_VISION, 0, 300, 220);
		}
		if (night && (Talents.has(player, Talent.NIGHT_HUNTER) || Talents.has(player, Talent.NIGHT_LORD))) {
			refresh(player, MobEffects.MOVEMENT_SPEED, 0, 40, 20);
		}
		if (night && Talents.has(player, Talent.NIGHT_JUMP)) {
			refresh(player, MobEffects.JUMP, 0, 40, 20);
		}
		// Quiet Step: at night monsters lose a Bloodkin they hunt from farther than 8 blocks
		if (night && Talents.has(player, Talent.NIGHT_HUSH)) {
			for (Mob m : level.getEntitiesOfClass(Mob.class, player.getBoundingBox().inflate(24), m -> m.getTarget() == player && m.distanceToSqr(player) > 64)) {
				m.setTarget(null);
			}
		}
		// Blood Sense: the hearts of what hunts around beat through the walls
		if (player.tickCount % 40 == 0 && Talents.has(player, Talent.NIGHT_SENSE)) {
			double reach = Talents.has(player, Talent.NIGHT_WIDE_SENSE) ? 24 : 12;
			for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class, player.getBoundingBox().inflate(reach),
					e -> e != player && bloodful(e) && e instanceof Enemy && e.distanceTo(player) <= reach)) {
				e.addEffect(new MobEffectInstance(MobEffects.GLOWING, 45, 0, false, false));
			}
		}
	}

	private static void refresh(ServerPlayer player, Holder<MobEffect> effect, int amplifier, int duration, int below) {
		MobEffectInstance current = player.getEffect(effect);
		if (current == null || current.getAmplifier() < amplifier || current.getDuration() <= below) {
			player.addEffect(new MobEffectInstance(effect, duration, amplifier, true, false, true));
		}
	}

	private static void onServerTick(ServerTickEvent.Post event) {
		MinecraftServer server = event.getServer();
		if (server.getTickCount() % 20 != 0) {
			return;
		}
		long now = server.overworld().getGameTime();
		MARKED.values().removeIf(until -> until < now);
		GIVEN.values().removeIf(g -> now - g[1] > 5 * 60 * 20);
		for (Iterator<Map.Entry<UUID, Bleed>> it = BLEEDING.entrySet().iterator(); it.hasNext(); ) {
			Map.Entry<UUID, Bleed> e = it.next();
			Bleed b = e.getValue();
			ServerLevel level = server.getLevel(b.level());
			Entity entity = level == null ? null : level.getEntity(e.getKey());
			if (!(entity instanceof LivingEntity living) || !living.isAlive() || b.until() < now) {
				it.remove();
				continue;
			}
			ServerPlayer owner = server.getPlayerList().getPlayer(b.owner());
			living.hurt(owner != null ? level.damageSources().indirectMagic(owner, owner) : level.damageSources().magic(), b.perSecond());
			Fx.blood(level, living.position().add(0, living.getBbHeight() * 0.6, 0), 0.3f, 4);
			// Open Vein: the bleeding feeds its Bloodkin
			if (owner != null && Talents.has(owner, Talent.FANG_OPEN)) {
				drink(owner, living, b.perSecond());
			}
		}
	}

	// ---- Blows ----------------------------------------------------------------------------------------------------

	private static boolean melee(DamageSource source, ServerPlayer player) {
		return source.is(DamageTypes.PLAYER_ATTACK) && source.getDirectEntity() == player;
	}

	private static boolean behind(LivingEntity victim, ServerPlayer player) {
		Vec3 facing = victim.getLookAngle().multiply(1, 0, 1);
		Vec3 toPlayer = player.position().subtract(victim.position()).multiply(1, 0, 1);
		return facing.lengthSqr() > 0 && toPlayer.lengthSqr() > 0 && facing.normalize().dot(toPlayer.normalize()) < -0.3;
	}

	/**
	 * Before armour: a Bloodkin's blow grows (Thirst, the night, Execution, a Harvest mark, an Ambush from behind; all
	 * together no more than twice the blow), and the Bat Swarm can't be touched.
	 */
	private static void onIncoming(LivingIncomingDamageEvent event) {
		LivingEntity victim = event.getEntity();
		if (victim instanceof ServerPlayer target && swarming(target) && !event.getSource().is(DamageTypeTags.BYPASSES_INVULNERABILITY)) {
			event.setCanceled(true);
			return;
		}
		if (!(event.getSource().getEntity() instanceof ServerPlayer player) || victim == player || !is(player) || event.getAmount() <= 0) {
			return;
		}
		float bonus = 0;
		if (thirsty(player)) {
			bonus += Talents.has(player, Talent.THIRST_HUNGER) ? 0.35f : 0.2f;
		}
		boolean night = isNight(player.level());
		if (night && Talents.has(player, Talent.NIGHT_LORD)) {
			bonus += 0.25f;
		} else if (night && Talents.has(player, Talent.NIGHT_HUNTER)) {
			bonus += 0.15f;
		}
		if (Talents.has(player, Talent.FANG_EXECUTE) && victim.getHealth() < victim.getMaxHealth() * 0.25f) {
			bonus += 0.5f;
		}
		Long mark = MARKED.get(victim.getUUID());
		if (mark != null && mark > now(player)) {
			bonus += 0.25f;
		}
		if (melee(event.getSource(), player) && Talents.has(player, Talent.NIGHT_AMBUSH) && behind(victim, player)) {
			bonus += 0.3f;
		}
		event.setAmount(event.getAmount() * (1 + Math.min(1, bonus)));
	}

	/** The share of a blow that heals: 10% (Greedy Fang 15%, +5% at night with Night Feast), doubled after an Offering; the Feast 50% (65%). */
	private static float lifesteal(ServerPlayer player, State s) {
		long now = now(player);
		if (s.feast > now) {
			return Talents.has(player, Talent.FEAST_DEEP) ? 0.65f : 0.5f;
		}
		float rate = Talents.has(player, Talent.FANG_LEECH) ? 0.15f : 0.1f;
		if (isNight(player.level()) && Talents.has(player, Talent.NIGHT_FEED)) {
			rate += 0.05f;
		}
		if (s.offering > now) {
			rate *= 2;
		}
		return rate;
	}

	/**
	 * After the blow landed (armour and all): a direct blow on a creature with blood heals the Bloodkin and gives blood;
	 * the Bite on every fourth, bleeding with a Torn Wound, the Feast's share for allies near.
	 */
	private static void onDamaged(LivingDamageEvent.Post event) {
		LivingEntity victim = event.getEntity();
		if (!(event.getSource().getEntity() instanceof ServerPlayer player) || victim == player || !is(player) || event.getNewDamage() <= 0
				|| !melee(event.getSource(), player) || !bloodful(victim) || Allies.ally(player, victim)) {
			return;
		}
		State s = state(player);
		ServerLevel level = player.serverLevel();
		float amount = event.getNewDamage();
		float heal = 0;
		if (Talents.has(player, Talent.FANG_BITE) && ++s.blows >= 4) {
			s.blows = 0;
			victim.invulnerableTime = 0;
			victim.hurt(level.damageSources().indirectMagic(player, player), 4);
			heal += 4;
			level.playSound(null, victim.blockPosition(), SoundEvents.PLAYER_ATTACK_CRIT, SoundSource.PLAYERS, 1, 0.6f);
			Fx.blood(level, victim.position().add(0, victim.getBbHeight() * 0.6, 0), 0.6f, 14);
		}
		heal += amount * lifesteal(player, s);
		heal(player, heal);
		s.healed += heal;
		int whole = (int) s.healed;
		if (whole > 0) {
			Talents.addProgress(player, VAMP_HEAL, whole);
			s.healed -= whole;
		}
		Deeds.grant(player, "blood_first_bite");
		long now = now(player);
		boolean feasting = s.feast > now;
		if (feasting) {
			s.feastHealed += heal;
			if (s.feastHealed >= 20) {
				Deeds.grant(player, "blood_feast");
			}
			Fx.thread(level, victim, player, 12);
			if (Talents.has(player, Talent.FEAST_SHARE)) {
				for (LivingEntity ally : level.getEntitiesOfClass(LivingEntity.class, player.getBoundingBox().inflate(8),
						e -> e != player && e.isAlive() && Allies.ally(player, e))) {
					heal(ally, heal * 0.25f);
				}
			}
			if (Talents.has(player, Talent.FEAST_FEAR) && !victim.getType().is(Tags.EntityTypes.BOSSES)) {
				victim.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 40, 0), player);
			}
		}
		float blood = Math.min(amount * 0.6f, 5) * (feasting ? 2 : 1);
		// Strike from the Shadow: a blow from behind drinks twice
		if (Talents.has(player, Talent.NIGHT_DRINK) && behind(victim, player)) {
			blood *= 2;
		}
		drink(player, victim, blood);
		if (Talents.has(player, Talent.FANG_BLEED)) {
			bleed(victim, player);
		}
		Fx.blood(level, victim.position().add(0, victim.getBbHeight() * 0.6, 0), 0.4f, 5);
	}

	private static void bleed(LivingEntity victim, ServerPlayer owner) {
		boolean deep = Talents.has(owner, Talent.FANG_DEEP);
		BLEEDING.put(victim.getUUID(), new Bleed(victim.level().dimension(), now(owner) + (deep ? 120 : 60), deep ? 2 : 1, owner.getUUID()));
		if (deep && !victim.getType().is(Tags.EntityTypes.BOSSES)) {
			victim.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 60, 0), owner);
		}
	}

	/** A creature with blood killed by a Bloodkin: a little hunger stilled, Frenzy, Rage, the Feast's fill, the Night's Terror. */
	private static void onDeath(LivingDeathEvent event) {
		LivingEntity victim = event.getEntity();
		BLEEDING.remove(victim.getUUID());
		MARKED.remove(victim.getUUID());
		GIVEN.remove(victim.getUUID());
		if (victim instanceof ServerPlayer dead) {
			endSwarm(dead, true);
		}
		if (!(event.getSource().getEntity() instanceof ServerPlayer player) || !is(player) || !bloodful(victim) || victim == player) {
			return;
		}
		State s = state(player);
		long now = now(player);
		boolean thirst = thirsty(player);
		player.getFoodData().eat(2, 0.2f);
		if (Talents.has(player, Talent.FANG_FRENZY)) {
			int ticks = thirst && Talents.has(player, Talent.THIRST_FRENZY) ? 200 : 100;
			player.addEffect(new MobEffectInstance(MobEffects.DAMAGE_BOOST, ticks, 0, true, true, true));
			player.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SPEED, ticks, 0, true, true, true));
			if (thirst && Talents.has(player, Talent.THIRST_FRENZY)) {
				RaceState.setResource(player, RaceState.resource(player) + 10);
			}
		}
		if (thirst && victim instanceof Enemy) {
			Deeds.grant(player, "blood_thirst");
			if (Talents.has(player, Talent.THIRST_RAGE)) {
				RaceState.setResource(player, RaceState.resource(player) + 15);
			}
		}
		if (s.feast > now && Talents.has(player, Talent.FEAST_SATED)) {
			RaceState.setResource(player, RaceState.resource(player) + 10);
		}
		if (s.feast > now && s.storm > now) {
			Deeds.grant(player, "blood_scarlet");
		}
		// The Night's Terror: at night a kill sends the monsters near reeling for 3 seconds
		if (isNight(player.level()) && Talents.has(player, Talent.NIGHT_TERROR)) {
			for (Mob m : player.serverLevel().getEntitiesOfClass(Mob.class, player.getBoundingBox().inflate(8),
					m -> m instanceof Enemy && m.isAlive() && !m.getType().is(Tags.EntityTypes.BOSSES))) {
				m.setTarget(null);
				m.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, 60, 0), player);
			}
		}
		RaceState.sync(player);
	}

	/** Thirst halves healing from anything but the Bloodkin's own blood (On the Edge lifts it); so does the sun (a Twilight Cloak: three quarters). */
	private static void onHeal(LivingHealEvent event) {
		if (ownHeal || !(event.getEntity() instanceof ServerPlayer player) || !is(player)) {
			return;
		}
		float factor = 1;
		if (thirsty(player) && !Talents.has(player, Talent.THIRST_EDGE)) {
			factor *= 0.5f;
		}
		if (sunlit(player)) {
			factor *= Talents.has(player, Talent.NIGHT_CLOAK) ? 0.75f : 0.5f;
		}
		event.setAmount(event.getAmount() * factor);
	}

	/** Ordinary food feeds a Bloodkin only a third. */
	private static void onEat(LivingEntityUseItemEvent.Finish event) {
		if (!(event.getEntity() instanceof ServerPlayer player) || !is(player)) {
			return;
		}
		FoodProperties food = event.getItem().getFoodProperties(player);
		if (food == null) {
			return;
		}
		var data = player.getFoodData();
		data.setFoodLevel(Math.max(0, data.getFoodLevel() - food.nutrition() * 2 / 3));
		data.setSaturation(Math.min(data.getSaturationLevel(), data.getFoodLevel()) / 3);
	}

	// ---- The Blood Dash (R) ---------------------------------------------------------------------------------------

	public static double dashRange(ServerPlayer player) {
		return Talents.has(player, Talent.FANG_REACH) ? 14 : 10;
	}

	/** Whether the Dash key now starts Double Rush's free second dash. */
	public static boolean secondDash(ServerPlayer player) {
		State s = STATES.get(player.getUUID());
		return s != null && s.dash == null && s.secondDash >= now(player);
	}

	public static boolean dashing(Player player) {
		State s = STATES.get(player.getUUID());
		return s != null && s.dash != null;
	}

	/**
	 * The Blood Dash: a rush along the look, stopped only by walls, through everything in the way; each creature it
	 * passes is cut once (6), and bleeds with a Torn Wound. With Double Rush a second one follows free within 3 s.
	 */
	public static Abilities.Refusal dash(ServerPlayer player) {
		State s = state(player);
		if (s.dash != null) {
			return new Abilities.Refusal("mages_legacy.refuse.busy");
		}
		boolean free = s.secondDash >= now(player);
		s.secondDash = 0;
		List<Vec3> path = dashPath(player, dashRange(player));
		if (path.size() < 2) {
			return new Abilities.Refusal("mages_legacy.refuse.no_exit");
		}
		player.stopRiding();
		s.dash = new Dash(path, new HashSet<>(), free, !player.onGround() && Talents.has(player, Talent.NIGHT_DIVE));
		s.dashStep = 0;
		ServerLevel level = player.serverLevel();
		Fx.bloodTrail(level, player, path.get(0), path.get(path.size() - 1), DASH_TICKS);
		Fx.fov(player, 0.2f, 12);
		Fx.shake(player, 0.8f, 5);
		level.playSound(null, player.blockPosition(), SoundEvents.PHANTOM_FLAP, SoundSource.PLAYERS, 1, 0.6f);
		level.playSound(null, player.blockPosition(), SoundEvents.TRIDENT_RIPTIDE_1.value(), SoundSource.PLAYERS, 0.8f, 1.4f);
		return null;
	}

	/** Points along the look, a quarter block apart, as far as the body fits (up a step as walking would). */
	private static List<Vec3> dashPath(ServerPlayer player, double range) {
		List<Vec3> path = new ArrayList<>();
		Vec3 look = player.getLookAngle();
		double rise = Math.abs(look.y) < 0.5 ? 0 : Math.max(-0.5, Math.min(0.5, look.y));
		Vec3 dir = new Vec3(look.x, rise, look.z);
		if (dir.lengthSqr() < 1.0E-4) {
			return path;
		}
		dir = dir.normalize();
		Vec3 start = player.position();
		path.add(start);
		Vec3 at = start;
		ServerLevel level = player.serverLevel();
		for (double d = 0.25; d <= range; d += 0.25) {
			Vec3 next = at.add(dir.scale(0.25));
			if (!level.noCollision(player, player.getBoundingBox().move(next.subtract(player.position())))) {
				Vec3 stepped = next.add(0, 0.6, 0);
				if (!level.noCollision(player, player.getBoundingBox().move(stepped.subtract(player.position())))) {
					break;
				}
				next = stepped;
			}
			at = next;
			path.add(at);
		}
		return path;
	}

	private static void tickDash(ServerPlayer player, State s) {
		Dash d = s.dash;
		if (!player.isAlive()) {
			s.dash = null;
			return;
		}
		ServerLevel level = player.serverLevel();
		int step = ++s.dashStep;
		int last = d.path().size() - 1;
		Vec3 a = d.path().get(Math.min(last, Math.round((float) (step - 1) * last / DASH_TICKS)));
		Vec3 b = d.path().get(Math.min(last, Math.round((float) step * last / DASH_TICKS)));
		AABB swept = player.getBoundingBox().move(a.subtract(player.position())).minmax(player.getBoundingBox().move(b.subtract(player.position())))
				.inflate(0.4);
		float damage = d.dive() ? DASH_DAMAGE * 1.5f : DASH_DAMAGE;
		for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class, swept, e -> prey(e, player) && !d.hit().contains(e.getUUID()))) {
			d.hit().add(e.getUUID());
			e.invulnerableTime = 0;
			e.hurt(level.damageSources().playerAttack(player), damage);
			Fx.blood(level, e.position().add(0, e.getBbHeight() * 0.6, 0), 0.6f, 12);
			Fx.bloodSlash(level, e.position().add(0, e.getBbHeight() * 0.6, 0), player.getYRot());
			Fx.shake(player, 2, 6);
			if (bloodful(e) && Talents.has(player, Talent.FANG_BLEED)) {
				bleed(e, player);
			}
			if (Talents.has(player, Talent.FANG_TRAIL)) {
				heal(player, 3);
			}
			Progression.addXp(player, "blood_dash", 1, 30);
		}
		player.setDeltaMovement(Vec3.ZERO);
		player.fallDistance = 0;
		player.connection.teleport(b.x, b.y, b.z, player.getYRot(), player.getXRot());
		if (Math.round((float) step * last / DASH_TICKS) >= last) {
			s.dash = null;
			int hits = d.hit().size();
			if (hits >= 2) {
				Talents.addProgress(player, DASH_MULTI, 1);
			}
			if (hits >= 3) {
				Deeds.grant(player, "blood_dash_three");
				Mastery.add(player, Mastery.Goal.DASH_CHAIN);
			}
			if (Talents.has(player, Talent.FANG_DOUBLE) && !d.free()) {
				s.secondDash = now(player) + DOUBLE_WINDOW;
			}
			Fx.blood(level, b.add(0, 0.6, 0), 0.6f, 14);
			RaceState.sync(player);
		}
	}

	// ---- The Harvest (V) ------------------------------------------------------------------------------------------

	public static double harvestRadius(ServerPlayer player) {
		return Talents.has(player, Talent.THIRST_HARVEST) ? 12 : 9;
	}

	/**
	 * The Harvest: blood drawn out of every enemy with blood within 9 blocks: each takes 4; the Bloodkin heals 2 from
	 * each (4 with Greedy Harvest), 10 at most, and drinks 5 from each, 30 at most.
	 */
	public static Abilities.Refusal harvest(ServerPlayer player) {
		ServerLevel level = player.serverLevel();
		double radius = harvestRadius(player);
		float perHeal = Talents.has(player, Talent.THIRST_DRAIN) ? 4 : 2;
		float healed = 0;
		float drunk = 0;
		int drawn = 0;
		for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class, player.getBoundingBox().inflate(radius),
				e -> prey(e, player) && bloodful(e) && e.distanceToSqr(player) < radius * radius)) {
			boolean bleeding = BLEEDING.containsKey(e.getUUID());
			e.invulnerableTime = 0;
			e.hurt(level.damageSources().indirectMagic(player, player), 4);
			if (healed < 10) {
				float h = Math.min(perHeal, 10 - healed);
				heal(player, h);
				healed += h;
			}
			if (drunk < 30) {
				// Red Harvest: a bleeding wound gives twice
				float want = Math.min(Talents.has(player, Talent.FANG_HARVEST) && bleeding ? 10 : 5, 30 - drunk);
				drunk += drink(player, e, want);
			}
			if (Talents.has(player, Talent.THIRST_MARK)) {
				MARKED.put(e.getUUID(), now(player) + MARK_TICKS);
			}
			Vec3 yank = player.position().subtract(e.position());
			if (yank.lengthSqr() > 4 && !e.getType().is(Tags.EntityTypes.BOSSES)) {
				e.setDeltaMovement(yank.normalize().scale(0.5).add(0, 0.2, 0));
				e.hurtMarked = true;
			}
			Fx.thread(level, e, player, 18);
			Progression.addXp(player, "blood_harvest", 2, 30);
			drawn++;
		}
		Fx.bloodRing(level, player.position(), (float) radius, 40);
		level.playSound(null, player.blockPosition(), SoundEvents.WARDEN_HEARTBEAT, SoundSource.PLAYERS, 1.5f, 0.8f);
		if (drawn > 0) {
			Fx.shake(player, 1, 10);
			level.playSound(null, player.blockPosition(), SoundEvents.GENERIC_DRINK, SoundSource.PLAYERS, 1, 0.6f);
		}
		if (drawn >= 3) {
			Talents.addProgress(player, HARVEST_GROUP, 1);
		}
		if (drawn >= 5) {
			Deeds.grant(player, "blood_harvest_five");
			Mastery.add(player, Mastery.Goal.HARVEST_HORDE);
		}
		return null;
	}

	// ---- The Sacrifice and the Transfusion (H) --------------------------------------------------------------------

	/** A friend in sight within 8 blocks: an ally player, or one's own pet. */
	private static LivingEntity lookedAtFriend(ServerPlayer player) {
		double range = 8;
		Vec3 eye = player.getEyePosition();
		Vec3 look = player.getLookAngle();
		HitResult wall = player.level().clip(new ClipContext(eye, eye.add(look.scale(range)), ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, player));
		double reach = wall.getType() == HitResult.Type.MISS ? range : wall.getLocation().distanceTo(eye);
		Vec3 end = eye.add(look.scale(reach));
		AABB box = player.getBoundingBox().expandTowards(look.scale(reach)).inflate(1);
		EntityHitResult hit = net.minecraft.world.entity.projectile.ProjectileUtil.getEntityHitResult(player, eye, end, box,
				e -> e instanceof LivingEntity l && l.isAlive() && e != player && !(e instanceof Enemy) && Allies.ally(player, l), reach * reach);
		return hit != null && hit.getEntity() instanceof LivingEntity living ? living : null;
	}

	/** H: looking at a friend, a Transfusion; otherwise the Sacrifice. Their cooldowns are set here. */
	public static Abilities.Refusal sacrifice(ServerPlayer player) {
		LivingEntity friend = lookedAtFriend(player);
		return friend != null ? transfuse(player, friend) : offer(player);
	}

	/**
	 * The Sacrifice: 4 of one's own health (2 with a Small Sacrifice) for 40 blood (50 with a Full Vessel, whose
	 * cooldown is halved). It never kills: with too little health it is refused. An Offering doubles vampirism for 5 s.
	 */
	private static Abilities.Refusal offer(ServerPlayer player) {
		float price = Talents.has(player, Talent.THIRST_SACRIFICE) ? 2 : 4;
		if (player.getHealth() <= price + 1) {
			return new Abilities.Refusal("mages_legacy.refuse.too_weak");
		}
		boolean vessel = Talents.has(player, Talent.THIRST_VESSEL);
		player.setHealth(player.getHealth() - price);
		RaceState.setResource(player, RaceState.resource(player) + (vessel ? 50 : 40));
		Voidborn.state(player).startCooldown(Ability.SACRIFICE.slot, vessel ? Ability.SACRIFICE.cooldown / 2 : Ability.SACRIFICE.cooldown);
		if (Talents.has(player, Talent.THIRST_OFFERING)) {
			state(player).offering = now(player) + OFFERING_TICKS;
		}
		Talents.addProgress(player, SACRIFICES, 1);
		ServerLevel level = player.serverLevel();
		Fx.sacrifice(level, player);
		Fx.bloodScreen(player, 14);
		Fx.shake(player, 1.6f, 8);
		level.playSound(null, player.blockPosition(), SoundEvents.PLAYER_HURT_SWEET_BERRY_BUSH, SoundSource.PLAYERS, 1, 0.6f);
		level.playSound(null, player.blockPosition(), SoundEvents.BREWING_STAND_BREW, SoundSource.PLAYERS, 0.8f, 0.5f);
		return null;
	}

	/**
	 * A Transfusion: 25 blood and 2 of one's own health for 6 health (10 with Generous Blood), Regeneration I for 5 s
	 * and, for a player, Strength I for 8 s. Once in 10 seconds.
	 */
	private static Abilities.Refusal transfuse(ServerPlayer player, LivingEntity friend) {
		if (RaceState.resource(player) < TRANSFUSE_COST) {
			return new Abilities.Refusal("mages_legacy.refuse.resource.bloodkin");
		}
		if (player.getHealth() <= 3) {
			return new Abilities.Refusal("mages_legacy.refuse.too_weak");
		}
		RaceState.setResource(player, RaceState.resource(player) - TRANSFUSE_COST);
		Voidborn.state(player).startCooldown(Ability.SACRIFICE.slot, TRANSFUSE_COOLDOWN);
		player.setHealth(player.getHealth() - 2);
		heal(friend, Talents.has(player, Talent.THIRST_TRANSFUSE) ? 10 : 6);
		friend.addEffect(new MobEffectInstance(MobEffects.REGENERATION, 100, 0, true, true, true));
		if (friend instanceof Player) {
			friend.addEffect(new MobEffectInstance(MobEffects.DAMAGE_BOOST, 160, 0, true, true, true));
		}
		ServerLevel level = player.serverLevel();
		Fx.thread(level, player, friend, 18);
		Fx.blood(level, friend.position().add(0, friend.getBbHeight() * 0.6, 0), 0.6f, 10);
		Fx.bloodRing(level, friend.position(), 1.6f, 24);
		level.playSound(null, friend.blockPosition(), SoundEvents.GENERIC_DRINK, SoundSource.PLAYERS, 1, 1.2f);
		level.playSound(null, friend.blockPosition(), SoundEvents.WARDEN_HEARTBEAT, SoundSource.PLAYERS, 1, 1.2f);
		Progression.addXp(player, "blood_transfuse", 3, 15);
		Deeds.grant(player, "blood_transfusion");
		return null;
	}

	// ---- The Feast (an Awakening) ---------------------------------------------------------------------------------

	/** The Feast: for 10 s (14 with a Long Feast) every blow heals half of it (65% with a Deep Feast) and drinks twice. */
	public static Abilities.Refusal feast(ServerPlayer player) {
		State s = state(player);
		int ticks = Talents.has(player, Talent.FEAST_LONG) ? FEAST_LONG : FEAST_TICKS;
		s.feast = now(player) + ticks;
		s.feastHealed = 0;
		ServerLevel level = player.serverLevel();
		Fx.bloodAura(level, player, ticks);
		Fx.bloodScreen(player, 24);
		Fx.shake(player, 2.5f, 12);
		level.playSound(null, player.blockPosition(), SoundEvents.WITHER_SPAWN, SoundSource.PLAYERS, 0.4f, 1.8f);
		return null;
	}

	public static boolean feasting(Player player) {
		State s = STATES.get(player.getUUID());
		return s != null && s.feast > player.level().getGameTime();
	}

	// ---- The Blood Storm (an Awakening) ---------------------------------------------------------------------------

	/**
	 * The Blood Storm: for 6 s (9 with a Long Storm), every second blood is torn out of every enemy with blood within 10
	 * blocks (14 with a Wide Storm): 3 each; the Bloodkin drinks 3 from each (15 a pulse at most) and heals 1 from each (2
	 * with Thirsting Storm; 6, or 10, a pulse at most). A Pulling Storm drags them in.
	 */
	public static Abilities.Refusal storm(ServerPlayer player) {
		State s = state(player);
		int ticks = Talents.has(player, Talent.BSTORM_LONG) ? STORM_LONG : STORM_TICKS;
		s.storm = now(player) + ticks;
		s.stormCaught.clear();
		ServerLevel level = player.serverLevel();
		Fx.bloodVortex(level, player, (float) stormRadius(player), ticks);
		Fx.shake(player, 1.2f, 10);
		level.playSound(null, player.blockPosition(), SoundEvents.WARDEN_HEARTBEAT, SoundSource.PLAYERS, 2, 0.6f);
		return null;
	}

	private static double stormRadius(ServerPlayer player) {
		return Talents.has(player, Talent.BSTORM_WIDE) ? 14 : 10;
	}

	public static boolean storming(Player player) {
		State s = STATES.get(player.getUUID());
		return s != null && s.storm > player.level().getGameTime();
	}

	private static void tickStorm(ServerPlayer player, State s) {
		long now = now(player);
		if (now >= s.storm || !player.isAlive()) {
			s.storm = 0;
			return;
		}
		if ((s.storm - now) % 20 != 0) {
			return;
		}
		ServerLevel level = player.serverLevel();
		double radius = stormRadius(player);
		boolean drain = Talents.has(player, Talent.BSTORM_DRAIN);
		float healCap = drain ? 10 : 6;
		float healed = 0;
		float drunk = 0;
		for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class, player.getBoundingBox().inflate(radius),
				e -> prey(e, player) && bloodful(e) && e.distanceToSqr(player) < radius * radius)) {
			e.invulnerableTime = 0;
			e.hurt(level.damageSources().indirectMagic(player, player), 3);
			if (healed < healCap) {
				float h = Math.min(drain ? 2 : 1, healCap - healed);
				heal(player, h);
				healed += h;
			}
			if (drunk < 15) {
				drunk += drink(player, e, Math.min(3, 15 - drunk));
			}
			if (Talents.has(player, Talent.BSTORM_PULL) && !e.getType().is(Tags.EntityTypes.BOSSES)) {
				Vec3 in = player.position().subtract(e.position());
				if (in.lengthSqr() > 4) {
					e.setDeltaMovement(in.normalize().scale(0.45).add(0, 0.15, 0));
					e.hurtMarked = true;
				}
			}
			Fx.thread(level, e, player, 14);
			if (s.stormCaught.add(e.getUUID()) && s.stormCaught.size() == 8) {
				Deeds.grant(player, "blood_storm");
			}
		}
		RaceState.sync(player);
	}

	// ---- The Prince of the Night (an Awakening): the Bat Swarm and the Last Breath --------------------------------

	public static boolean swarming(Player player) {
		State s = STATES.get(player.getUUID());
		return s != null && s.swarm != null;
	}

	/** The Prince's key: the Bat Swarm, 30 blood, 3 s (5 with Long Flight), its own 25-second cooldown. */
	public static Abilities.Refusal swarm(ServerPlayer player) {
		if (swarming(player)) {
			return new Abilities.Refusal("mages_legacy.refuse.busy");
		}
		startSwarm(player, false);
		Deeds.grant(player, "blood_swarm");
		return null;
	}

	/** The Last Breath's readiness: the game time it can save again (kept on the player with the cooldowns). */
	public static long rescueReady(ServerPlayer player) {
		return Voidborn.state(player).princeReady;
	}

	private static boolean prince(ServerPlayer player) {
		return Awakenings.slotOf(Talents.owned(player), Ability.NIGHT_PRINCE) >= 0;
	}

	/**
	 * The Last Breath: a blow that would leave a Prince of the Night at or under a tenth of its health (a killing one
	 * too: this runs before the health is taken, on the damage that is really left after armour, absorption and the
	 * rest) is undone: the body scatters into bats, its health set to 30% (50% with a Second Heart). Once in 20 minutes,
	 * day or night, no blood needed; never during a swarm. The void and /kill, which pass through every protection, are
	 * not stopped (the body would only fall again).
	 */
	private static void onDamagePre(LivingDamageEvent.Pre event) {
		if (!(event.getEntity() instanceof ServerPlayer player) || !is(player) || event.getNewDamage() <= 0 || swarming(player) || !prince(player)
				|| event.getSource().is(DamageTypeTags.BYPASSES_INVULNERABILITY)) {
			return;
		}
		float max = player.getMaxHealth();
		if (player.getHealth() - event.getNewDamage() > max * RESCUE_AT || now(player) < rescueReady(player)) {
			return;
		}
		event.setNewDamage(0);
		Voidborn.state(player).princeReady = now(player) + RESCUE_COOLDOWN;
		player.setHealth(Math.min(max, max * (Talents.has(player, Talent.PRINCE_HEART) ? 0.5f : 0.3f)));
		startSwarm(player, true);
		Fx.bloodScreen(player, 20);
		Fx.shake(player, 2.5f, 14);
		Fx.fov(player, 0.25f, 16);
		player.displayClientMessage(Component.translatable("mages_legacy.message.last_breath").withStyle(ChatFormatting.DARK_RED), true);
		Deeds.grant(player, "blood_last_breath");
		RaceState.sync(player);
	}

	private static void startSwarm(ServerPlayer player, boolean rescue) {
		State s = state(player);
		int ticks = !rescue && Talents.has(player, Talent.PRINCE_LONG) ? SWARM_LONG : SWARM_TICKS;
		s.swarm = new Swarm(now(player) + ticks, rescue);
		s.dash = null;
		player.stopRiding();
		player.setNoGravity(true);
		player.addEffect(new MobEffectInstance(MobEffects.INVISIBILITY, ticks + 4, 0, true, false, false));
		ServerLevel level = player.serverLevel();
		Fx.hide(level, player, ticks + 2);
		Fx.batSwarm(level, player, ticks, rescue);
		// The view steps out behind the swarm, so the whole cloud of bats is seen; it comes back with the body
		Fx.thirdPerson(player, ticks + 10);
		Fx.fov(player, 0.16f, ticks);
		level.playSound(null, player.blockPosition(), SoundEvents.BAT_TAKEOFF, SoundSource.PLAYERS, 1.4f, 0.7f);
		level.playSound(null, player.blockPosition(), SoundEvents.PHANTOM_FLAP, SoundSource.PLAYERS, 1, 0.5f);
	}

	private static void tickSwarm(ServerPlayer player, State s) {
		Swarm swarm = s.swarm;
		if (now(player) >= swarm.until || !player.isAlive()) {
			endSwarm(player, true);
			return;
		}
		// The swarm flies where the Bloodkin looks (a fifth faster at night); walls stop it, creatures don't
		double speed = SWARM_SPEED * (isNight(player.level()) ? 1.2 : 1);
		player.setDeltaMovement(player.getLookAngle().scale(speed));
		player.hurtMarked = true;
		player.fallDistance = 0;
		// A Bloodthirsty Swarm bites what it flies through: bleeding, and a little blood (20 a flight at most)
		if (!swarm.rescue && Talents.has(player, Talent.PRINCE_SWARM)) {
			ServerLevel level = player.serverLevel();
			for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class, player.getBoundingBox().inflate(1.5),
					e -> prey(e, player) && bloodful(e) && !swarm.bitten.contains(e.getUUID()))) {
				swarm.bitten.add(e.getUUID());
				bleed(e, player);
				if (swarm.drunk < 20) {
					swarm.drunk += drink(player, e, Math.min(4, 20 - swarm.drunk));
				}
				Fx.blood(level, e.position().add(0, e.getBbHeight() * 0.6, 0), 0.4f, 8);
			}
		}
	}

	/** The bats gather back into a body (quietly: no effects, on leaving or dying). Safe to call twice. */
	public static void endSwarm(ServerPlayer player, boolean show) {
		State s = STATES.get(player.getUUID());
		if (s == null || s.swarm == null) {
			return;
		}
		Swarm swarm = s.swarm;
		s.swarm = null;
		Fx.thirdPerson(player, 0);
		player.setNoGravity(false);
		player.removeEffect(MobEffects.INVISIBILITY);
		player.fallDistance = 0;
		player.setDeltaMovement(player.getDeltaMovement().scale(0.2));
		player.hurtMarked = true;
		if (!show) {
			return;
		}
		ServerLevel level = player.serverLevel();
		Fx.hide(level, player, 0);
		Fx.batSwarm(level, player, 0, swarm.rescue);
		level.playSound(null, player.blockPosition(), SoundEvents.BAT_TAKEOFF, SoundSource.PLAYERS, 1, 1.4f);
		// The Shadow of Death: back in a body after the Last Breath, Resistance I for 3 s
		if (swarm.rescue && Talents.has(player, Talent.PRINCE_SHADOW)) {
			player.addEffect(new MobEffectInstance(MobEffects.DAMAGE_RESISTANCE, SHADOW_TICKS, 0, true, true, true));
		}
	}

	// ---- Nothing else while the body is bats ----------------------------------------------------------------------

	private static void onAttack(AttackEntityEvent event) {
		if (swarming(event.getEntity())) {
			event.setCanceled(true);
		}
	}

	private static void onUseItem(PlayerInteractEvent.RightClickItem event) {
		if (swarming(event.getEntity())) {
			event.setCanceled(true);
		}
	}

	private static void onUseBlock(PlayerInteractEvent.RightClickBlock event) {
		if (swarming(event.getEntity())) {
			event.setCanceled(true);
		}
	}

	private static void onInteract(PlayerInteractEvent.EntityInteract event) {
		if (swarming(event.getEntity())) {
			event.setCanceled(true);
		}
	}

	/** Forgets a player's live state (a new race). */
	public static void reset(ServerPlayer player) {
		endSwarm(player, false);
		STATES.remove(player.getUUID());
	}

	/** For the harness: how much blood a creature has given so far. */
	public static float given(LivingEntity e) {
		float[] g = GIVEN.get(e.getUUID());
		return g == null ? 0 : g[0];
	}
}
