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
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.damage.DamageTypes;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.entity.mob.Monster;
import net.minecraft.entity.passive.IronGolemEntity;
import net.minecraft.entity.passive.TameableEntity;
import net.minecraft.entity.passive.VillagerEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.projectile.ProjectileUtil;
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
 * The Tempters: the nameless mage's longing to be loved. They live on attention (Adoration) and are the tank of the
 * races: every monster after a Tempter is tied to it by a heart thread, and every blow on the Tempter is shared out
 * along the threads, a tenth to each, half at most. The Gaze (R) draws monsters on, the Kiss (V) drinks one, the Vow
 * (Z) ties an ally so their wounds come to the Tempter, Jealousy (H) sets the threaded on one another, and the Broken
 * Heart (Alt, crown) snaps every thread at once. The undead have no heart: no thread holds them.
 */
public final class Tempter {
	/** Rose of the threads, and dark wine laid on as real colour under the glow (see {@link Fx#INK}). */
	public static final int ROSE_RGB = 0xFF6FA8;
	private static final int WINE_INK = Fx.INK | 0x6A1838;

	public static final int GAZE_COST = 15;
	public static final int GAZE_COST_CHEAP = 10;
	public static final int KISS_COST = 25;
	public static final int KISS_COST_CHEAP = 15;
	public static final int VOW_COST = 30;
	public static final int VOW_COST_CHEAP = 15;
	public static final int JEALOUSY_COST = 40;
	public static final int BREAK_COST = 50;
	private static final int GAZE_COOLDOWN = 160;
	private static final int KISS_COOLDOWN = 240;
	private static final int KISS_COOLDOWN_QUICK = 160;
	private static final int VOW_COOLDOWN = 600;
	private static final int JEALOUSY_COOLDOWN = 500;
	private static final int BREAK_COOLDOWN = 6000;
	private static final int UNDYING_SLOT = 9;
	private static final int UNDYING_COOLDOWN = 24000;
	/** Each thread takes a tenth of a blow; five threads (six with Wide Heart) take half (three fifths). */
	public static final float THREAD_SHARE = 0.1f;
	private static final int VOW_TICKS = 400;
	private static final int VOW_TICKS_LONG = 600;
	private static final int JEALOUSY_TICKS = 120;
	private static final int JEALOUSY_TICKS_LONG = 180;
	/** A minute without a living soul near (three with Company) brings the longing. */
	private static final int LONELY_AFTER = 1200;
	private static final int LONELY_AFTER_LONG = 3600;

	/** A Vow: whose wounds go to which Tempter, until when. */
	private record Vow(UUID tempter, long until) {}

	/** The monsters tied to each Tempter, nearest first. */
	private static final Map<UUID, List<UUID>> THREADS = new HashMap<>();
	private static final Map<UUID, Vow> VOWS = new HashMap<>();
	/** Monsters set on each other by Jealousy (or a charming Kiss): until when, and for whom. */
	private record Jealousy(UUID tempter, long until) {}

	private static final Map<UUID, Jealousy> JEALOUS = new HashMap<>();
	/** Damage shared out along each Tempter's threads lately, fading by a tenth a second (the Broken Heart pays it back). */
	private static final Map<UUID, Float> SHARED = new HashMap<>();
	private static final Map<UUID, Long> LAST_COMPANY = new HashMap<>();
	/** Set while a Vow passes a wound on, so two Tempters vowed to each other don't pass it back and forth. */
	private static boolean passing;

	private Tempter() {
	}

	public static void reset() {
		THREADS.clear();
		VOWS.clear();
		JEALOUS.clear();
		SHARED.clear();
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

	/** Whether a creature has a heart a thread can hold: no undead, no golems. */
	public static boolean hearted(LivingEntity e) {
		return !e.isUndead() && !(e instanceof IronGolemEntity);
	}

	public static int maxThreads(RaceData data) {
		return data.has(Talents.THREAD_MORE) ? 6 : 5;
	}

	public static double threadRange(RaceData data) {
		return data.has(Talents.THREAD_FAR) ? 24 : 16;
	}

	/** The monsters threaded to this Tempter right now. */
	public static List<LivingEntity> threaded(ServerPlayerEntity player) {
		List<LivingEntity> list = new ArrayList<>();
		for (UUID id : THREADS.getOrDefault(player.getUuid(), List.of())) {
			if (player.getServerWorld().getEntity(id) instanceof LivingEntity living && living.isAlive()) {
				list.add(living);
			}
		}
		return list;
	}

	// ---- once a second ----

	public static void tickSecond(ServerPlayerEntity player, RaceData data) {
		ServerWorld world = player.getServerWorld();
		long now = now(player.getServer());
		double range = threadRange(data);
		// The threads: monsters with a heart that are after the Tempter (or quarrel for it), nearest first
		List<LivingEntity> tied = new ArrayList<>(world.getEntitiesByClass(MobEntity.class, player.getBoundingBox().expand(range),
				m -> m.isAlive() && m instanceof Monster && hearted(m) && (m.getTarget() == player || jealousFor(m, player, now))));
		tied.sort(Comparator.comparingDouble(m -> m.squaredDistanceTo(player)));
		List<UUID> ids = new ArrayList<>();
		for (LivingEntity m : tied.subList(0, Math.min(tied.size(), maxThreads(data)))) {
			ids.add(m.getUuid());
			// A rose thread from the heart of each one to the Tempter, held as long as the thread holds
			Fx.tendril(world, m, player, 24, WINE_INK, ROSE_RGB);
			if (data.has(Talents.THREAD_HOLD)) {
				m.addStatusEffect(new StatusEffectInstance(StatusEffects.SLOWNESS, 30, 0, true, false));
			}
		}
		THREADS.put(player.getUuid(), ids);
		SHARED.computeIfPresent(player.getUuid(), (id, v) -> v * 0.9f);

		// Adoration: the eyes on the Tempter feed it; company a little; loneliness drains it
		boolean company = !world.getEntitiesByClass(LivingEntity.class, player.getBoundingBox().expand(16),
				e -> e != player && e.isAlive() && !(e instanceof net.minecraft.entity.decoration.ArmorStandEntity)).isEmpty();
		if (company) {
			LAST_COMPANY.put(player.getUuid(), now);
		}
		long alone = now - LAST_COMPANY.computeIfAbsent(player.getUuid(), id -> now);
		int friends = world.getEntitiesByClass(LivingEntity.class, player.getBoundingBox().expand(16),
				e -> e != player && e.isAlive() && (e instanceof PlayerEntity || e instanceof VillagerEntity)).size();
		float gain = 0.25f + ids.size() * (data.has(Talents.THREAD_FEED) ? 0.75f : 0.5f) + friends * (data.has(Talents.COURT_CROWD) ? 0.6f : 0.3f);
		if (alone > (data.has(Talents.COURT_COMPANY) ? LONELY_AFTER_LONG : LONELY_AFTER)) {
			gain = -0.5f;
			refresh(player, StatusEffects.WEAKNESS, 0);
		}
		data.addResource(gain);

		if (data.has(Talents.THREAD_WARD) && ids.size() >= 3) {
			refresh(player, StatusEffects.RESISTANCE, 0);
		}
		if (data.has(Talents.THREAD_MEND) && ids.size() >= 2) {
			refresh(player, StatusEffects.REGENERATION, 0);
		}
		// They can't help being drawn: a monster near that is after another player turns to the Tempter
		double pull = data.has(Talents.THREAD_GUARD) ? 12 : 8;
		float chance = data.has(Talents.THREAD_GUARD) ? 0.6f : 0.3f;
		for (MobEntity m : world.getEntitiesByClass(MobEntity.class, player.getBoundingBox().expand(pull),
				m -> m.isAlive() && m instanceof Monster && hearted(m) && m.getTarget() instanceof PlayerEntity other && other != player)) {
			if (player.getRandom().nextFloat() < chance) {
				m.setTarget(player);
			}
		}
		// Village golems can't stand a Tempter (Peace with Golems softens them)
		if (!data.has(Talents.COURT_GOLEM)) {
			for (IronGolemEntity golem : world.getEntitiesByClass(IronGolemEntity.class, player.getBoundingBox().expand(16),
					g -> g.isAlive() && !g.isPlayerCreated() && g.getTarget() == null)) {
				golem.setTarget(player);
			}
		}
	}

	private static void refresh(ServerPlayerEntity player, net.minecraft.entity.effect.StatusEffect effect, int amplifier) {
		StatusEffectInstance current = player.getStatusEffect(effect);
		if (current == null || current.getDuration() <= 30) {
			player.addStatusEffect(new StatusEffectInstance(effect, 60, amplifier, true, false, true));
		}
	}

	private static boolean jealousFor(LivingEntity mob, ServerPlayerEntity player, long now) {
		Jealousy j = JEALOUS.get(mob.getUuid());
		return j != null && j.until() > now && j.tempter().equals(player.getUuid());
	}

	// ---- every tick ----

	public static void tick(MinecraftServer server) {
		long now = now(server);
		VOWS.values().removeIf(v -> v.until() < now);
		JEALOUS.values().removeIf(j -> j.until() < now);
		if (now % 10 != 0) {
			return;
		}
		// The jealous turn on the nearest other monster, never on the one they quarrel over
		for (ServerWorld world : server.getWorlds()) {
			for (Map.Entry<UUID, Jealousy> e : JEALOUS.entrySet()) {
				if (!(world.getEntity(e.getKey()) instanceof MobEntity mob) || !mob.isAlive()) {
					continue;
				}
				LivingEntity target = mob.getTarget();
				if (target == null || !target.isAlive() || target instanceof PlayerEntity || !(target instanceof Monster)) {
					LivingEntity rival = world.getEntitiesByClass(LivingEntity.class, mob.getBoundingBox().expand(12),
							o -> o != mob && o.isAlive() && o instanceof Monster).stream()
							.min(Comparator.comparingDouble(o -> o.squaredDistanceTo(mob))).orElse(null);
					mob.setTarget(rival);
				}
			}
		}
	}

	// ---- abilities ----

	public static AbilityResult use(ServerPlayerEntity player, RaceData data, int slot) {
		return switch (slot) {
			case 0 -> gaze(player, data);
			case 1 -> kiss(player, data);
			case 2 -> vow(player, data);
			case 3 -> jealousy(player, data);
			case 4 -> data.has(Talents.THREAD_BREAK) ? breakHeart(player, data) : RaceData.talentLocked(player, Talents.THREAD_BREAK);
			default -> AbilityResult.LOCKED;
		};
	}

	public static int gazeCost(RaceData data) {
		return data.has(Talents.THREAD_GAZE) ? GAZE_COST_CHEAP : GAZE_COST;
	}

	public static int kissCost(RaceData data) {
		return data.has(Talents.KISS_CHEAP) ? KISS_COST_CHEAP : KISS_COST;
	}

	public static int vowCost(RaceData data) {
		return data.has(Talents.COURT_CHEAP_VOW) ? VOW_COST_CHEAP : VOW_COST;
	}

	/** The Gaze (R): every monster in front within 12 blocks turns to the Tempter and is tied at once. */
	public static AbilityResult gaze(ServerPlayerEntity player, RaceData data) {
		if (!ready(player, data, 0)) {
			return AbilityResult.COOLDOWN;
		}
		int cost = gazeCost(data);
		if (data.resource < cost) {
			return AbilityResult.NO_RESOURCE;
		}
		ServerWorld world = player.getServerWorld();
		Vec3d eye = player.getEyePos();
		Vec3d look = player.getRotationVec(1.0f).normalize();
		double cos = data.has(Talents.THREAD_GAZE) ? 0.707 : 0.866;
		List<MobEntity> drawn = world.getEntitiesByClass(MobEntity.class, player.getBoundingBox().expand(12), m -> {
			Vec3d to = m.getPos().add(0, m.getHeight() / 2, 0).subtract(eye);
			return m.isAlive() && m instanceof Monster && to.lengthSquared() < 144 && to.normalize().dotProduct(look) > cos;
		});
		if (drawn.isEmpty()) {
			return AbilityResult.NO_ENEMIES;
		}
		data.addResource(-cost);
		cooldown(player, data, 0, GAZE_COOLDOWN);
		List<UUID> ids = THREADS.computeIfAbsent(player.getUuid(), id -> new ArrayList<>());
		for (MobEntity m : drawn) {
			m.setTarget(player);
			if (hearted(m) && ids.size() < maxThreads(data) && !ids.contains(m.getUuid())) {
				ids.add(m.getUuid());
			}
			if (data.has(Talents.THREAD_PULL)) {
				Vec3d toward = player.getPos().subtract(m.getPos()).normalize().multiply(0.8);
				m.addVelocity(toward.x, 0.25, toward.z);
				m.velocityModified = true;
			}
			Fx.tendril(world, player, m, 16, WINE_INK, ROSE_RGB);
		}
		if (data.has(Talents.COURT_RALLY)) {
			for (PlayerEntity friend : world.getEntitiesByClass(PlayerEntity.class, player.getBoundingBox().expand(8), p -> p != player && p.isAlive())) {
				friend.addStatusEffect(new StatusEffectInstance(StatusEffects.RESISTANCE, 100, 0, true, true, true));
			}
		}
		// The look that turns heads: a rose wave out in front, a beat of the heart
		Fx.ring(world, player.getPos(), 0.4f, 12, 14, ROSE_RGB);
		Fx.burst(world, eye, 1.2f, 7, ROSE_RGB);
		Fx.pulse(player, 1, 14, ROSE_RGB);
		world.playSound(null, player.getBlockPos(), SoundEvents.ENTITY_ALLAY_ITEM_THROWN, SoundCategory.PLAYERS, 1.0f, 0.6f);
		world.playSound(null, player.getBlockPos(), SoundEvents.BLOCK_AMETHYST_BLOCK_RESONATE, SoundCategory.PLAYERS, 1.0f, 1.4f);
		return AbilityResult.OK;
	}

	public static double kissReach(RaceData data) {
		return data.has(Talents.KISS_REACH) ? 12 : 8;
	}

	/**
	 * The Kiss (V): a rush to the creature you look at and a kiss that drinks 6 of its life into yours, leaving it weak.
	 * No heart, no kiss: the undead give nothing.
	 */
	public static AbilityResult kiss(ServerPlayerEntity player, RaceData data) {
		if (!ready(player, data, 1)) {
			return AbilityResult.COOLDOWN;
		}
		int cost = kissCost(data);
		if (data.resource < cost) {
			return AbilityResult.NO_RESOURCE;
		}
		LivingEntity target = lookedAt(player, kissReach(data), e -> !(e instanceof PlayerEntity) && !(e instanceof TameableEntity tame && tame.isTamed()));
		if (target == null) {
			return AbilityResult.NO_SIGHT;
		}
		if (!hearted(target)) {
			player.sendMessage(Text.translatable("mages_legacy.tempter.no_heart"), true);
			return AbilityResult.HINTED;
		}
		data.addResource(-cost);
		cooldown(player, data, 1, data.has(Talents.KISS_QUICK) ? KISS_COOLDOWN_QUICK : KISS_COOLDOWN);
		ServerWorld world = player.getServerWorld();
		// The rush: up to the creature's side
		Vec3d side = target.getPos().add(player.getPos().subtract(target.getPos()).normalize().multiply(target.getWidth() / 2 + 0.8));
		Vec3d from = player.getPos();
		player.requestTeleport(side.x, target.getY(), side.z);
		float drain = data.has(Talents.KISS_DEEP) ? 9 : 6;
		target.damage(world.getDamageSources().indirectMagic(player, player), drain);
		player.heal(drain);
		target.addStatusEffect(new StatusEffectInstance(StatusEffects.WEAKNESS, 100, 0));
		if (data.has(Talents.KISS_POISON)) {
			target.addStatusEffect(new StatusEffectInstance(StatusEffects.POISON, 80, 1));
		}
		if (data.has(Talents.KISS_CHARM) && target instanceof MobEntity mob) {
			JEALOUS.put(mob.getUuid(), new Jealousy(player.getUuid(), now(player.getServer()) + 80));
			mob.setTarget(null);
		}
		if (data.has(Talents.KISS_SHIELD)) {
			player.setAbsorptionAmount(Math.min(8, player.getAbsorptionAmount() + 4));
		}
		if (data.has(Talents.KISS_SHARE)) {
			for (PlayerEntity friend : world.getEntitiesByClass(PlayerEntity.class, player.getBoundingBox().expand(6), p -> p != player && p.isAlive())) {
				friend.heal(drain / 2);
			}
		}
		Progression.addXp(player, data, "tempter_kiss", 1, 20);
		// A rose streak where she went, the life drawn out in a thread, a heartbeat
		Fx.ribbon(world, List.of(from, player.getPos()), 12, WINE_INK, ROSE_RGB, 1.0f);
		Fx.tendril(world, target, player, 14, WINE_INK, ROSE_RGB);
		Fx.burst(world, target.getPos().add(0, target.getHeight() * 0.7, 0), 1.0f + target.getWidth(), 7, ROSE_RGB);
		Fx.pulse(player, 2, 20, ROSE_RGB);
		world.spawnParticles(net.minecraft.particle.ParticleTypes.HEART, target.getX(), target.getY() + target.getHeight() + 0.2, target.getZ(), 3, 0.3, 0.2, 0.3, 0);
		world.playSound(null, player.getBlockPos(), SoundEvents.ENTITY_GENERIC_DRINK, SoundCategory.PLAYERS, 0.8f, 1.5f);
		world.playSound(null, player.getBlockPos(), SoundEvents.ENTITY_ALLAY_AMBIENT_WITH_ITEM, SoundCategory.PLAYERS, 1.0f, 0.7f);
		return AbilityResult.OK;
	}

	/** The Vow (Z): a thread to an ally (a player or a tamed beast you look at): half of what hurts them, the Tempter takes. */
	public static AbilityResult vow(ServerPlayerEntity player, RaceData data) {
		if (!ready(player, data, 2)) {
			return AbilityResult.COOLDOWN;
		}
		int cost = vowCost(data);
		if (data.resource < cost) {
			return AbilityResult.NO_RESOURCE;
		}
		LivingEntity ally = lookedAt(player, 16, e -> e instanceof PlayerEntity || e instanceof TameableEntity tame && tame.isTamed());
		if (ally == null) {
			return AbilityResult.NO_SIGHT;
		}
		data.addResource(-cost);
		cooldown(player, data, 2, VOW_COOLDOWN);
		long now = now(player.getServer());
		int allowed = data.has(Talents.COURT_TWO_VOWS) ? 2 : 1;
		List<UUID> mine = VOWS.entrySet().stream().filter(e -> e.getValue().tempter().equals(player.getUuid())).map(Map.Entry::getKey).toList();
		for (int i = 0; i <= mine.size() - allowed; i++) {
			VOWS.remove(mine.get(i));
		}
		int ticks = data.has(Talents.COURT_VOW) ? VOW_TICKS_LONG : VOW_TICKS;
		VOWS.put(ally.getUuid(), new Vow(player.getUuid(), now + ticks));
		if (data.has(Talents.KISS_ALLY)) {
			ally.addStatusEffect(new StatusEffectInstance(StatusEffects.REGENERATION, ticks, 0, true, true, true));
		}
		ServerWorld world = player.getServerWorld();
		Fx.tendril(world, player, ally, 30, WINE_INK, ROSE_RGB);
		Fx.helix(world, ally, ally.getWidth() + 0.5f, ally.getHeight() + 0.3f, 2, true, 20, ROSE_RGB);
		Fx.aura(world, ally, ticks, ROSE_RGB, Fx.MOTES);
		world.playSound(null, ally.getBlockPos(), SoundEvents.BLOCK_AMETHYST_BLOCK_CHIME, SoundCategory.PLAYERS, 1.2f, 0.8f);
		world.playSound(null, player.getBlockPos(), SoundEvents.ENTITY_ALLAY_ITEM_GIVEN, SoundCategory.PLAYERS, 1.0f, 0.9f);
		return AbilityResult.OK;
	}

	/** Jealousy (H): for 6 seconds every threaded monster fights the others over the Tempter, and leaves her alone. */
	public static AbilityResult jealousy(ServerPlayerEntity player, RaceData data) {
		if (!ready(player, data, 3)) {
			return AbilityResult.COOLDOWN;
		}
		if (data.resource < JEALOUSY_COST) {
			return AbilityResult.NO_RESOURCE;
		}
		List<LivingEntity> tied = threaded(player);
		if (tied.size() < 2) {
			player.sendMessage(Text.translatable("mages_legacy.tempter.no_rivals"), true);
			return AbilityResult.HINTED;
		}
		data.addResource(-JEALOUSY_COST);
		cooldown(player, data, 3, JEALOUSY_COOLDOWN);
		long until = now(player.getServer()) + (data.has(Talents.COURT_JEALOUS) ? JEALOUSY_TICKS_LONG : JEALOUSY_TICKS);
		ServerWorld world = player.getServerWorld();
		for (LivingEntity m : tied) {
			JEALOUS.put(m.getUuid(), new Jealousy(player.getUuid(), until));
			if (m instanceof MobEntity mob) {
				mob.setTarget(null);
			}
			Fx.burst(world, m.getPos().add(0, m.getHeight() + 0.3, 0), 0.8f, 8, ROSE_RGB);
			world.spawnParticles(net.minecraft.particle.ParticleTypes.ANGRY_VILLAGER, m.getX(), m.getY() + m.getHeight() + 0.3, m.getZ(), 2, 0.3, 0.2, 0.3, 0);
		}
		Fx.ring(world, player.getPos(), 0.5f, 10, 16, ROSE_RGB);
		Fx.mark(world, player.getPos(), player, 2.0f, 30, ROSE_RGB, Fx.RIPPLES);
		world.playSound(null, player.getBlockPos(), SoundEvents.ENTITY_EVOKER_PREPARE_WOLOLO, SoundCategory.PLAYERS, 0.8f, 1.4f);
		return AbilityResult.OK;
	}

	/**
	 * The Broken Heart (Alt, the Threads' crown): every thread snaps at once; each monster on one takes 8 and half of
	 * what the threads carried lately (30 at most), and staggers.
	 */
	public static AbilityResult breakHeart(ServerPlayerEntity player, RaceData data) {
		if (!ready(player, data, 4)) {
			return AbilityResult.COOLDOWN;
		}
		if (data.resource < BREAK_COST) {
			return AbilityResult.NO_RESOURCE;
		}
		List<LivingEntity> tied = threaded(player);
		if (tied.isEmpty()) {
			return AbilityResult.NO_ENEMIES;
		}
		data.addResource(-BREAK_COST);
		cooldown(player, data, 4, BREAK_COOLDOWN);
		float blow = Math.min(30, 8 + SHARED.getOrDefault(player.getUuid(), 0f) * 0.5f);
		ServerWorld world = player.getServerWorld();
		for (LivingEntity m : tied) {
			m.damage(world.getDamageSources().indirectMagic(player, player), blow);
			m.addStatusEffect(new StatusEffectInstance(StatusEffects.SLOWNESS, 40, 3));
			Fx.burst(world, m.getPos().add(0, m.getHeight() / 2, 0), 1.2f + m.getWidth(), 9, ROSE_RGB);
			Fx.slash(world, m.getPos().add(0, m.getHeight() * 0.6, 0), m.getPos().subtract(player.getPos()), 0.8f + m.getWidth(), 90, 9, WINE_INK);
		}
		THREADS.remove(player.getUuid());
		SHARED.remove(player.getUuid());
		Fx.ring(world, player.getPos(), 0.5f, 14, 18, ROSE_RGB);
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

	/**
	 * A blow on a Tempter is shared out along the threads: each threaded monster takes a tenth of it (half again with
	 * Thorny Threads), and the Tempter is spared as much. Nothing that bypasses invulnerability is shared.
	 */
	public static float modifyDamage(ServerPlayerEntity player, DamageSource source, float amount) {
		RaceData data = RaceState.of(player);
		// Not while the body is still in its moment of invulnerability: those blows mostly don't land at all
		if (data == null || amount <= 0 || source.isIn(DamageTypeTags.BYPASSES_INVULNERABILITY) || player.timeUntilRegen > 10) {
			return amount;
		}
		List<LivingEntity> tied = threaded(player);
		if (tied.isEmpty()) {
			return amount;
		}
		ServerWorld world = player.getServerWorld();
		float each = amount * THREAD_SHARE;
		float onThem = each * (data.has(Talents.THREAD_STING) ? 1.5f : 1.0f);
		for (LivingEntity m : tied) {
			m.damage(world.getDamageSources().indirectMagic(player, player), onThem);
		}
		float shared = each * tied.size();
		SHARED.merge(player.getUuid(), shared, Float::sum);
		Progression.addXp(player, data, "tempter_threads", Math.max(1, Math.round(shared / 5)), 40);
		return amount - shared;
	}

	/**
	 * Any creature's blow, for the Tempters' sake: a Tempter's own hand is soft; Warm Thirst drinks from the threaded;
	 * the Court near a Tempter strikes harder; quarrelling monsters hit each other harder (Quarrel).
	 */
	public static float onHit(LivingEntity victim, DamageSource source, float amount) {
		Entity attacker = source.getAttacker();
		if (amount <= 0 || attacker == null || attacker == victim) {
			return amount;
		}
		if (attacker instanceof ServerPlayerEntity player && is(player)) {
			RaceData data = RaceState.of(player);
			if (source.isOf(DamageTypes.PLAYER_ATTACK) && source.getSource() == player) {
				amount *= 0.75f;
				if (data.has(Talents.KISS_LIFE) && THREADS.getOrDefault(player.getUuid(), List.of()).contains(victim.getUuid())) {
					player.heal(amount * 0.1f);
				}
			}
			return amount;
		}
		if (attacker instanceof PlayerEntity other && other.getServer() != null) {
			for (ServerPlayerEntity tempter : courtNear(other, 16)) {
				RaceData data = RaceState.of(tempter);
				if (data.has(Talents.COURT_AURA) && tempter.squaredDistanceTo(other) < 64) {
					amount *= 1.1f;
				}
				if (data.has(Talents.COURT_QUEEN) && threaded(tempter).size() >= 3) {
					amount *= 1.1f;
				}
			}
			return amount;
		}
		if (attacker instanceof MobEntity mob && victim instanceof Monster && attacker.getServer() != null) {
			Jealousy j = JEALOUS.get(mob.getUuid());
			ServerPlayerEntity tempter = j == null ? null : attacker.getServer().getPlayerManager().getPlayer(j.tempter());
			if (tempter != null && RaceState.of(tempter).has(Talents.COURT_QUARREL)) {
				amount *= 1.5f;
			}
		}
		return amount;
	}

	/** The Tempters within {@code range} of a player (not the player itself). */
	private static List<ServerPlayerEntity> courtNear(PlayerEntity player, double range) {
		List<ServerPlayerEntity> list = new ArrayList<>();
		for (PlayerEntity p : player.getWorld().getPlayers()) {
			if (p != player && p instanceof ServerPlayerEntity sp && is(sp) && sp.squaredDistanceTo(player) < range * range) {
				list.add(sp);
			}
		}
		return list;
	}

	/**
	 * A blow on someone a Tempter has vowed to: half of it goes to the Tempter instead (and from there along the
	 * threads); the Court's queen spares players near her a sixth.
	 */
	public static float onAllyHurt(LivingEntity victim, DamageSource source, float amount) {
		if (amount <= 0 || victim.getServer() == null || passing || source.isIn(DamageTypeTags.BYPASSES_INVULNERABILITY) || victim.timeUntilRegen > 10) {
			return amount;
		}
		if (victim instanceof PlayerEntity player && !is(player)) {
			for (ServerPlayerEntity tempter : courtNear(player, 16)) {
				if (RaceState.of(tempter).has(Talents.COURT_QUEEN) && threaded(tempter).size() >= 3) {
					amount *= 0.85f;
				}
			}
		}
		Vow vow = VOWS.get(victim.getUuid());
		if (vow == null) {
			return amount;
		}
		ServerPlayerEntity tempter = victim.getServer().getPlayerManager().getPlayer(vow.tempter());
		if (tempter == null || !tempter.isAlive() || tempter == victim || tempter.getWorld() != victim.getWorld() || tempter.squaredDistanceTo(victim) > 32 * 32) {
			return amount;
		}
		float taken = amount * 0.5f;
		passing = true;
		try {
			tempter.damage(tempter.getDamageSources().magic(), taken);
		} finally {
			passing = false;
		}
		RaceData data = RaceState.of(tempter);
		Progression.addXp(tempter, data, "tempter_vow", Math.max(1, Math.round(taken / 5)), 30);
		Fx.tendril(tempter.getServerWorld(), victim, tempter, 10, WINE_INK, ROSE_RGB);
		return amount - taken;
	}

	/** A monster killed while tied to a Tempter, or by one quarrelling over her: the courtship pays. */
	public static void onDeath(LivingEntity victim, DamageSource source) {
		if (!(victim instanceof Monster) || victim.getServer() == null) {
			return;
		}
		for (Map.Entry<UUID, List<UUID>> e : THREADS.entrySet()) {
			if (e.getValue().contains(victim.getUuid())) {
				ServerPlayerEntity tempter = victim.getServer().getPlayerManager().getPlayer(e.getKey());
				if (tempter != null) {
					Progression.addXp(tempter, RaceState.of(tempter), Progression.HUNT, 2, Progression.HUNT_CAP);
				}
				return;
			}
		}
		if (source.getAttacker() instanceof MobEntity killer) {
			Jealousy j = JEALOUS.get(killer.getUuid());
			ServerPlayerEntity tempter = j == null ? null : victim.getServer().getPlayerManager().getPlayer(j.tempter());
			if (tempter != null) {
				Progression.addXp(tempter, RaceState.of(tempter), Progression.HUNT, 3, Progression.HUNT_CAP);
			}
		}
	}

	/** The Undying (the Kiss's crown): a killing blow takes the life of a threaded monster instead, once in 20 minutes. */
	public static boolean allowDeath(ServerPlayerEntity player) {
		RaceData data = RaceState.of(player);
		if (data == null || data.race != Race.TEMPTER || !data.has(Talents.KISS_UNDYING) || !ready(player, data, UNDYING_SLOT)) {
			return true;
		}
		List<LivingEntity> tied = threaded(player);
		if (tied.isEmpty()) {
			return true;
		}
		LivingEntity instead = tied.get(0);
		ServerWorld world = player.getServerWorld();
		instead.damage(world.getDamageSources().indirectMagic(player, player), Float.MAX_VALUE);
		player.setHealth(player.getMaxHealth() * 0.3f);
		data.readyAt.put(UNDYING_SLOT, now(player.getServer()) + UNDYING_COOLDOWN);
		data.dirty = true;
		Fx.tendril(world, instead, player, 24, WINE_INK, ROSE_RGB);
		Fx.burst(world, player.getPos().add(0, 1, 0), 2.5f, 12, ROSE_RGB);
		Fx.screen(player, 24, ROSE_RGB);
		world.playSound(null, player.getBlockPos(), SoundEvents.ITEM_TOTEM_USE, SoundCategory.PLAYERS, 0.6f, 1.5f);
		player.sendMessage(Text.translatable("mages_legacy.tempter.undying").formatted(Formatting.LIGHT_PURPLE), false);
		return false;
	}

	/** Takes the Tempter's threads and vows away (the race was reset or changed). */
	public static void cleanup(ServerPlayerEntity player) {
		THREADS.remove(player.getUuid());
		SHARED.remove(player.getUuid());
		VOWS.values().removeIf(v -> v.tempter().equals(player.getUuid()));
		JEALOUS.values().removeIf(j -> j.tempter().equals(player.getUuid()));
	}

	/** What the HUD needs: how many threads hold. */
	public static net.minecraft.nbt.NbtCompound clientInfo(ServerPlayerEntity player) {
		net.minecraft.nbt.NbtCompound nbt = new net.minecraft.nbt.NbtCompound();
		nbt.putInt("threads", THREADS.getOrDefault(player.getUuid(), List.of()).size());
		return nbt;
	}
}
