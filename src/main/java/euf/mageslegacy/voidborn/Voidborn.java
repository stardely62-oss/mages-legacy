package euf.mageslegacy.voidborn;

import euf.mageslegacy.fx.Fx;
import euf.mageslegacy.fx.ModParticles;
import euf.mageslegacy.race.AbilityResult;
import euf.mageslegacy.race.Progression;
import euf.mageslegacy.race.Race;
import euf.mageslegacy.race.RaceData;
import euf.mageslegacy.race.RaceState;
import euf.mageslegacy.race.Talents;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import net.minecraft.entity.Entity;
import net.minecraft.entity.ItemEntity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.boss.WitherEntity;
import net.minecraft.entity.boss.dragon.EnderDragonEntity;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.effect.StatusEffect;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.entity.mob.ElderGuardianEntity;
import net.minecraft.entity.mob.EndermanEntity;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.entity.mob.Monster;
import net.minecraft.entity.mob.WardenEntity;
import net.minecraft.entity.passive.TameableEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.projectile.ProjectileEntity;
import net.minecraft.entity.projectile.ProjectileUtil;
import net.minecraft.registry.tag.DamageTypeTags;
import net.minecraft.registry.tag.FluidTags;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.EntityHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.RaycastContext;
import net.minecraft.world.World;

/**
 * Everything the Voidborn do. Part of each of them stayed in the void, so space gives way to them: the Void Step
 * carries them through solid rock, the Well pulls enemies into one point, the Excision tears a creature out of the
 * world into their pocket for a while, and the Pocket is a world of their own. Water burns them, rain weakens them,
 * and the world's beds won't let them sleep: only in the pocket.
 */
public final class Voidborn {
	public static final int STEP_COST = 20;
	public static final int WELL_COST = 40;
	public static final int EXCISE_COST = 35;
	public static final int POCKET_COST = 20;
	public static final double STEP_RANGE = 24;
	private static final int STEP_TICKS = 10;
	private static final int STEP_COOLDOWN = 160;
	private static final int WELL_TICKS = 80;
	private static final int WELL_COOLDOWN = 500;
	private static final double WELL_RADIUS = 8;
	private static final int EXCISE_COOLDOWN = 300;
	private static final double EXCISE_RANGE = 24;
	private static final int POCKET_COOLDOWN = 200;
	private static final int GAZE_SLOT = 4;
	public static final int GAZE_COST = 50;
	private static final int GAZE_COOLDOWN = 6000;
	private static final int HEARTH_SLOT = 6;
	private static final int LONG_COOLDOWN = 24000;
	/** Ticks between the rift opening and the step through it. */
	private static final int POCKET_DELAY = 8;

	/** The void's colours: violet light, the near-black of the void itself laid on as real colour, starlight. */
	public static final int VOID_RGB = 0x9B7FD4;
	public static final int VOID_INK = Fx.INK | 0x2A1446;
	public static final int STAR_RGB = 0xE8E0FF;

	private record Step(ServerPlayerEntity player, List<Vec3d> path, Set<UUID> hit, boolean through, List<ServerPlayerEntity> companions) {}

	private record Well(ServerWorld world, Vec3d center, double radius, long until, ServerPlayerEntity owner, Set<UUID> pulled) {}

	/** An open way left by a Rift Step: whoever walks into its start comes out at its end. */
	private record Door(ServerWorld world, Vec3d from, Vec3d to, long until) {}

	/**
	 * A creature held in its puller's pocket: where it came from, and when it goes back. One taken by the Excision
	 * stays until its Voidborn presses the key again ({@code until} is then far off); the Gaze's come back by themselves.
	 */
	private record Held(Entity entity, UUID owner, ServerWorld origin, Vec3d pos, float yaw, long until, boolean ally, boolean excised) {}

	/** A step into (or out of) the pocket, made a moment after the rift opens; {@code home}: out to one's spawn. */
	private record Passage(ServerPlayerEntity player, long at, boolean in, boolean home) {}

	private static final List<Step> STEPS = new ArrayList<>();
	private static final java.util.Map<UUID, Integer> STEP_AT = new java.util.HashMap<>();
	private static final List<Well> WELLS = new ArrayList<>();
	private static final List<Held> HELD = new ArrayList<>();
	private static final List<Passage> PASSAGES = new ArrayList<>();
	private static final List<Door> DOORS = new ArrayList<>();
	/** When each Voidborn was last hurt (server ticks): the pocket doesn't open for one still under blows. */
	private static final java.util.Map<UUID, Long> LAST_HURT = new java.util.HashMap<>();
	private static final int POCKET_CALM = 100;
	/** Until when (client world time) this client's own player phases through blocks; set by the effect packet. */
	public static long clientPhaseUntil;

	private Voidborn() {
	}

	public static void reset() {
		STEPS.clear();
		STEP_AT.clear();
		WELLS.clear();
		HELD.clear();
		PASSAGES.clear();
		DOORS.clear();
		LAST_HURT.clear();
	}

	/** A Voidborn took a blow: for a few seconds its pocket won't open. */
	public static void onHurt(LivingEntity self, float amount) {
		if (amount > 0 && self instanceof ServerPlayerEntity player && is(player)) {
			LAST_HURT.put(player.getUuid(), now(player.getServer()));
		}
	}

	// ---- what the talents change ----

	public static int stepCost(boolean cheap) {
		return cheap ? 14 : STEP_COST;
	}

	public static double stepRange(RaceData data) {
		return data.has(Talents.STEP_FAR) ? 32 : STEP_RANGE;
	}

	public static int exciseCost(boolean cheap) {
		return cheap ? 25 : EXCISE_COST;
	}

	public static int pocketCost(boolean cheap) {
		return cheap ? 10 : POCKET_COST;
	}

	public static double wellRadius(RaceData data) {
		return data.has(Talents.WELL_WIDE) ? 11 : WELL_RADIUS;
	}

	public static boolean is(PlayerEntity player) {
		return player instanceof ServerPlayerEntity sp && RaceState.raceOf(sp).orElse(null) == Race.VOIDBORN;
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

	/**
	 * Whether this player is passing through solid matter right now: then blocks don't hold or smother it. On the
	 * server that is a Void Step under way; on a client, its own player while the server says so.
	 */
	public static boolean phasing(PlayerEntity player) {
		if (player.getWorld().isClient()) {
			return player.isMainPlayer() && player.getWorld().getTime() <= clientPhaseUntil;
		}
		for (Step s : STEPS) {
			if (s.player() == player) {
				return true;
			}
		}
		return false;
	}

	/** An enemy for the Well and the Step: a monster, or a mob hunting a player. Pets and other players never. */
	private static boolean enemy(Entity e, ServerPlayerEntity player) {
		return e instanceof LivingEntity living && living.isAlive() && e != player && !(e instanceof PlayerEntity)
				&& !(e instanceof TameableEntity tame && tame.isTamed()) && !e.getCommandTags().contains("mages_legacy_bond")
				&& (e instanceof Monster || (e instanceof MobEntity m && m.getTarget() instanceof PlayerEntity));
	}

	/** Too big for any pocket: the dragon, the Wither, the Warden, the elder guardian. */
	private static boolean tooBig(Entity e) {
		return e instanceof EnderDragonEntity || e instanceof WitherEntity || e instanceof WardenEntity || e instanceof ElderGuardianEntity;
	}

	// ---- once a second ----

	public static void tickSecond(ServerPlayerEntity player, RaceData data) {
		ServerWorld world = player.getServerWorld();
		boolean home = Pocket.in(player) || world.getRegistryKey() == World.END;
		boolean dark = world.getLightLevel(BlockPos.ofFloored(player.getEyePos())) <= 7;
		data.addResource(home ? 1.8f : dark ? 1.2f : 0.6f);
		// Water burns them as it burns the endermen; rain saps them
		if (world.getFluidState(player.getBlockPos()).isIn(FluidTags.WATER) || world.getFluidState(BlockPos.ofFloored(player.getEyePos())).isIn(FluidTags.WATER)) {
			player.damage(world.getDamageSources().magic(), data.has(Talents.POCKET_WATER) ? 1 : 2);
			world.spawnParticles(ModParticles.VOID_MOTE, player.getX(), player.getY() + 1, player.getZ(), 8, 0.3, 0.5, 0.3, 0.02);
			world.playSound(null, player.getBlockPos(), SoundEvents.BLOCK_FIRE_EXTINGUISH, SoundCategory.PLAYERS, 0.5f, 1.4f);
		} else if (world.hasRain(BlockPos.ofFloored(player.getEyePos()))) {
			refresh(player, StatusEffects.WEAKNESS, 60);
			if (!data.has(Talents.POCKET_STORM)) {
				refresh(player, StatusEffects.SLOWNESS, 60);
			}
		}
		if (data.has(Talents.POCKET_REST) && Pocket.in(player)) {
			refresh(player, StatusEffects.REGENERATION, 60);
		}
	}

	private static void refresh(ServerPlayerEntity player, StatusEffect effect, int duration) {
		StatusEffectInstance current = player.getStatusEffect(effect);
		if (current == null || current.getDuration() <= 30) {
			player.addStatusEffect(new StatusEffectInstance(effect, duration, 0, true, false, true));
		}
	}

	/** Nothing touches a Voidborn inside the rock; and one arrow or fireball in four passes through the rest of them. */
	public static boolean avoids(LivingEntity self, DamageSource source) {
		if (!(self instanceof ServerPlayerEntity player) || !is(player)) {
			return false;
		}
		if (phasing(player)) {
			return true;
		}
		if (source.isIn(DamageTypeTags.IS_PROJECTILE) && player.getRandom().nextFloat() < 0.25f) {
			ServerWorld world = player.getServerWorld();
			world.spawnParticles(ModParticles.VOID_MOTE, player.getX(), player.getY() + 1.2, player.getZ(), 10, 0.25, 0.4, 0.25, 0.03);
			world.playSound(null, player.getBlockPos(), SoundEvents.ENTITY_ENDERMAN_TELEPORT, SoundCategory.PLAYERS, 0.4f, 1.8f);
			return true;
		}
		return false;
	}

	/** Endermen take a Voidborn's look for one of their own. */
	public static boolean kin(PlayerEntity player) {
		return is(player);
	}

	/** A creature died at a Voidborn's hand: a little void flows back; an enderman is worth a lot of experience. */
	public static void onKill(ServerPlayerEntity player, LivingEntity victim) {
		if (!is(player) || !(victim instanceof Monster)) {
			return;
		}
		RaceData data = RaceState.of(player);
		data.addResource(3);
		// The dark is the void's own: a kill in it teaches more
		if (player.getServerWorld().getLightLevel(BlockPos.ofFloored(player.getEyePos())) <= 7) {
			Progression.addXp(player, data, Progression.HUNT, 2, Progression.HUNT_CAP);
		}
		if (data.has(Talents.WELL_HUNGER) && WELLS.stream().anyMatch(w -> w.owner() == player && w.pulled().contains(victim.getUuid()))) {
			data.addResource(10);
		}
		// Kin of the void teach the most, though an enderman farm is no faster a road than any other hunt
		if (victim instanceof EndermanEntity) {
			Progression.addXp(player, data, Progression.HUNT, 8, Progression.HUNT_CAP);
		}
	}

	/** The world's beds won't hold a Voidborn: they sleep only in their pocket. */
	public static boolean mayNotSleep(ServerPlayerEntity player) {
		if (!is(player) || Pocket.in(player)) {
			return false;
		}
		player.sendMessage(Text.translatable("mages_legacy.voidborn.no_sleep"), true);
		return true;
	}

	/**
	 * Sleeping in the pocket passes the night out in the world too: once a Voidborn has slept there long enough and
	 * nobody in the overworld is left awake, morning comes.
	 */
	public static void tickSleep(MinecraftServer server) {
		ServerWorld pocket = Pocket.world(server);
		if (pocket == null) {
			return;
		}
		boolean rested = pocket.getPlayers().stream().anyMatch(p -> p.isSleeping() && p.getSleepTimer() >= 99);
		ServerWorld overworld = server.getOverworld();
		if (!rested || !overworld.isNight() || overworld.getPlayers().stream().anyMatch(p -> !p.isSpectator() && !p.isSleeping())) {
			return;
		}
		long time = overworld.getTimeOfDay() + 24000;
		overworld.setTimeOfDay(time - time % 24000);
		// A night slept at home in the void
		for (ServerPlayerEntity p : pocket.getPlayers()) {
			if (p.isSleeping() && is(p)) {
				Progression.addXp(p, RaceState.of(p), 10);
			}
		}
		for (ServerWorld w : List.of(pocket, overworld)) {
			for (ServerPlayerEntity p : w.getPlayers()) {
				if (p.isSleeping()) {
					p.wakeUp(false, true);
				}
			}
		}
	}

	// ---- abilities ----

	public static AbilityResult use(ServerPlayerEntity player, RaceData data, int slot) {
		return switch (slot) {
			case 0 -> step(player, data);
			case 1 -> well(player, data);
			case 2 -> excise(player, data);
			case 3 -> pocket(player, data);
			case 4 -> data.has(Talents.ABYSS_GAZE) ? gaze(player, data) : RaceData.talentLocked(player, Talents.ABYSS_GAZE);
			default -> AbilityResult.LOCKED;
		};
	}

	/**
	 * Void Step: half a second's passage through the void, 24 blocks where you look, through walls, floors and
	 * ceilings alike, coming out where the body fits (never inside the rock, never in lava). Bedrock and other
	 * unbreakable things stop it. What it passes through on the way takes 4 and is slowed.
	 */
	public static AbilityResult step(ServerPlayerEntity player, RaceData data) {
		if (STEPS.stream().anyMatch(s -> s.player() == player)) {
			return AbilityResult.HINTED;
		}
		long now = now(player.getServer());
		int cost = stepCost(data.has(Talents.STEP_CHEAP));
		if (!ready(player, data, 0)) {
			return AbilityResult.COOLDOWN;
		}
		if (data.resource < cost) {
			return AbilityResult.NO_RESOURCE;
		}
		List<Vec3d> path = stepPath(player, stepRange(data));
		if (path.size() < 2) {
			return AbilityResult.NO_SPACE;
		}
		data.addResource(-cost);
		cooldown(player, data, 0, data.has(Talents.STEP_QUICK) ? 100 : STEP_COOLDOWN);
		ServerWorld world = player.getServerWorld();
		boolean through = path.stream().anyMatch(p -> !fits(player, p));
		player.stopRiding();
		// Taken Along: one player standing right beside goes through too
		List<ServerPlayerEntity> companions = !data.has(Talents.STEP_ALLY) ? List.of()
				: world.getPlayers(p -> p != player && p.isAlive() && !p.isSpectator() && p.squaredDistanceTo(player) < 4).stream().limit(1).toList();
		STEPS.add(new Step(player, path, new HashSet<>(), through, companions));
		STEP_AT.put(player.getUuid(), 0);
		player.addStatusEffect(new StatusEffectInstance(StatusEffects.INVISIBILITY, STEP_TICKS + 4, 0, true, false, false));
		Vec3d start = path.get(0);
		Vec3d end = path.get(path.size() - 1);
		// The world tears where the body goes in and where it will come out; between, a dark streak through the rock
		boolean door = data.has(Talents.STEP_RIFT);
		Fx.rift(world, start.add(0, 1, 0), 2.4f, 1.3f, door ? 100 : 16, VOID_RGB);
		if (door) {
			DOORS.add(new Door(world, start, path.get(path.size() - 1), now + 100));
		}
		Fx.rift(world, end.add(0, 1, 0), 2.4f, 1.3f, STEP_TICKS + 14, VOID_RGB);
		Fx.ribbon(world, path, STEP_TICKS + 12, VOID_INK, VOID_RGB, 1.1f);
		Fx.burst(world, start.add(0, 1, 0), 1.3f, 6, VOID_INK);
		Fx.phase(player, STEP_TICKS);
		Fx.voidScreen(player, STEP_TICKS + 4);
		Fx.fov(player, 0.3f, STEP_TICKS + 6);
		world.spawnParticles(ModParticles.VOID_MOTE, start.x, start.y + 1, start.z, 24, 0.4, 0.7, 0.4, 0.06);
		world.playSound(null, player.getBlockPos(), SoundEvents.ENTITY_ENDERMAN_TELEPORT, SoundCategory.PLAYERS, 1.0f, 0.5f);
		world.playSound(null, player.getBlockPos(), SoundEvents.BLOCK_RESPAWN_ANCHOR_DEPLETE.value(), SoundCategory.PLAYERS, 0.6f, 1.6f);
		return AbilityResult.OK;
	}

	/**
	 * The way through: points a quarter block apart along the look, through anything breakable, as far as the body
	 * can come out. From the far end back, the first spot where the body fits (or fits a little higher or lower)
	 * ends it; the path then runs straight to that spot. Empty if there is none.
	 */
	static List<Vec3d> stepPath(ServerPlayerEntity player, double range) {
		ServerWorld world = player.getServerWorld();
		Vec3d look = player.getRotationVec(1.0f).normalize();
		Vec3d start = player.getPos();
		List<Vec3d> line = new ArrayList<>();
		line.add(start);
		for (double d = 0.25; d <= range; d += 0.25) {
			Vec3d p = start.add(look.multiply(d));
			if (unbreakable(player, p) || !world.getWorldBorder().contains(BlockPos.ofFloored(p))) {
				break;
			}
			line.add(p);
		}
		double[] lifts = {0, 0.5, 1, -0.5, -1, 1.5, -1.5};
		for (int i = line.size() - 1; i >= 6; i--) {
			for (double lift : lifts) {
				Vec3d spot = line.get(i).add(0, lift, 0);
				if (fits(player, spot)) {
					List<Vec3d> path = new ArrayList<>(line.subList(0, i + 1));
					path.set(path.size() - 1, spot);
					return path;
				}
			}
		}
		return List.of();
	}

	/** Whether the body would stand free at this spot: nothing solid, no lava. */
	private static boolean fits(ServerPlayerEntity player, Vec3d spot) {
		Box box = player.getBoundingBox().offset(spot.subtract(player.getPos()));
		ServerWorld world = player.getServerWorld();
		return world.isSpaceEmpty(player, box) && world.getStatesInBoxIfLoaded(box).noneMatch(s -> s.getFluidState().isIn(FluidTags.LAVA));
	}

	/** Whether the body at this spot would overlap something nothing can pass: bedrock, barriers, portal frames. */
	private static boolean unbreakable(ServerPlayerEntity player, Vec3d spot) {
		ServerWorld world = player.getServerWorld();
		Box box = player.getBoundingBox().offset(spot.subtract(player.getPos()));
		for (BlockPos pos : BlockPos.iterate(BlockPos.ofFloored(box.minX, box.minY, box.minZ), BlockPos.ofFloored(box.maxX, box.maxY, box.maxZ))) {
			if (world.getBlockState(pos).getHardness(world, pos) < 0) {
				return true;
			}
		}
		return false;
	}

	private static void tickSteps(long now) {
		for (Iterator<Step> it = STEPS.iterator(); it.hasNext(); ) {
			Step s = it.next();
			ServerPlayerEntity player = s.player();
			if (player.isRemoved() || !player.isAlive()) {
				it.remove();
				STEP_AT.remove(player.getUuid());
				continue;
			}
			ServerWorld world = player.getServerWorld();
			int step = STEP_AT.merge(player.getUuid(), 1, Integer::sum);
			int last = s.path().size() - 1;
			// Eased: slow out of the rift, fast through the rock, slow into the light again
			float t0 = ease((float) (step - 1) / STEP_TICKS);
			float t1 = ease((float) step / STEP_TICKS);
			Vec3d a = s.path().get(Math.min(last, Math.round(t0 * last)));
			Vec3d b = s.path().get(Math.min(last, Math.round(t1 * last)));
			Box swept = player.getBoundingBox().offset(a.subtract(player.getPos())).union(player.getBoundingBox().offset(b.subtract(player.getPos())));
			for (LivingEntity e : world.getEntitiesByClass(LivingEntity.class, swept, e -> enemy(e, player) && !s.hit().contains(e.getUuid()))) {
				s.hit().add(e.getUuid());
				RaceData data = RaceState.of(player);
				boolean sever = data.has(Talents.STEP_SEVER);
				e.damage(world.getDamageSources().indirectMagic(player, player), sever ? 9 : data.has(Talents.STEP_CUT) ? 6 : 4);
				e.addStatusEffect(new StatusEffectInstance(StatusEffects.SLOWNESS, 60, sever ? 2 : 1));
				if (data.has(Talents.STEP_REND)) {
					e.addStatusEffect(new StatusEffectInstance(StatusEffects.WEAKNESS, 80, 1));
				}
				if (data.has(Talents.STEP_MEND)) {
					player.heal(2);
				}
				if (sever) {
					Fx.slash(world, e.getPos().add(0, e.getHeight() / 2, 0), b.subtract(a), 1.2f + e.getWidth(), 90, 9, VOID_RGB);
				}
				Fx.burst(world, e.getPos().add(0, e.getHeight() / 2, 0), 0.8f + e.getWidth(), 6, VOID_RGB);
			}
			for (int i = 0; i <= 3; i++) {
				Vec3d p = a.lerp(b, i / 3.0);
				world.spawnParticles(ModParticles.VOID_MOTE, p.x, p.y + 1, p.z, 2, 0.25, 0.5, 0.25, 0.02);
				world.spawnParticles(ModParticles.STAR, p.x, p.y + 1, p.z, 1, 0.4, 0.6, 0.4, 0.0);
			}
			player.setVelocity(Vec3d.ZERO);
			player.fallDistance = 0;
			player.networkHandler.requestTeleport(b.x, b.y, b.z, player.getYaw(), player.getPitch());
			if (step >= STEP_TICKS) {
				it.remove();
				STEP_AT.remove(player.getUuid());
				emerge(player, b, s.through());
				for (ServerPlayerEntity companion : s.companions()) {
					if (companion.isAlive() && companion.getServerWorld() == world) {
						companion.teleport(world, b.x + 0.6, b.y, b.z, companion.getYaw(), companion.getPitch());
						companion.fallDistance = 0;
						Fx.rift(world, b.add(0.6, 1, 0), 2.2f, 1.2f, 12, VOID_RGB);
					}
				}
			}
		}
	}

	private static float ease(float t) {
		t = Math.max(0, Math.min(1, t));
		return t * t * (3 - 2 * t);
	}

	/** Out of the void again: a flash, cracks in the ground where the body landed, the world shaking a little. */
	private static void emerge(ServerPlayerEntity player, Vec3d at, boolean through) {
		ServerWorld world = player.getServerWorld();
		player.removeStatusEffect(StatusEffects.INVISIBILITY);
		// Veil: monsters that hunted the Voidborn lose it in the void
		if (RaceState.of(player).has(Talents.STEP_VEIL)) {
			for (MobEntity m : world.getEntitiesByClass(MobEntity.class, player.getBoundingBox().expand(32), m -> m.getTarget() == player)) {
				m.setTarget(null);
			}
		}
		player.setVelocity(Vec3d.ZERO);
		player.velocityModified = true;
		player.fallDistance = 0;
		Fx.burst(world, at.add(0, 1, 0), 1.7f, 7, VOID_RGB);
		Fx.ring(world, at, 0.3f, 2.8f, 10, VOID_INK);
		Fx.mark(world, at, null, 1.6f, 40, VOID_INK, Fx.CRACKS);
		Fx.shake(player, 1.2f, 7);
		// Exit Funnel: the void closing behind the body drags the enemies around to where it came out
		if (RaceState.of(player).has(Talents.STEP_PULL)) {
			for (LivingEntity e : world.getEntitiesByClass(LivingEntity.class, player.getBoundingBox().expand(5), e -> enemy(e, player))) {
				Vec3d to = at.subtract(e.getPos());
				e.setVelocity(to.multiply(0.25).add(0, 0.25, 0));
				e.velocityModified = true;
				Fx.tendril(world, e, player, 8, VOID_RGB, STAR_RGB);
			}
			Fx.vortex(world, at.add(0, 1, 0), 1.4f, 16, VOID_RGB);
		}
		world.spawnParticles(ModParticles.VOID_MOTE, at.x, at.y + 1, at.z, 30, 0.2, 0.4, 0.2, 0.12);
		world.spawnParticles(ModParticles.STAR, at.x, at.y + 1, at.z, 12, 0.6, 0.8, 0.6, 0.0);
		world.playSound(null, player.getBlockPos(), SoundEvents.ENTITY_ENDERMAN_TELEPORT, SoundCategory.PLAYERS, 1.0f, 1.2f);
		if (through) {
			Progression.addXp(player, RaceState.of(player), "voidborn_step", 1, 20);
		}
	}

	/** Where the look meets a block within {@code range}, or {@code range} along it. */
	private static Vec3d aim(ServerPlayerEntity player, double range) {
		Vec3d eye = player.getEyePos();
		Vec3d end = eye.add(player.getRotationVec(1.0f).multiply(range));
		BlockHitResult hit = player.getWorld().raycast(new RaycastContext(eye, end, RaycastContext.ShapeType.COLLIDER, RaycastContext.FluidHandling.NONE, player));
		return hit.getType() == HitResult.Type.MISS ? end : hit.getPos().add(hit.getSide().getVector().getX() * 1.5, hit.getSide().getVector().getY() * 1.5,
				hit.getSide().getVector().getZ() * 1.5);
	}

	/**
	 * The Well: a black hole where you look (up to 20 blocks) for 4 seconds. Enemies within 8 blocks, their arrows
	 * and loose items are dragged into it; at its heart it hurts; at the end it collapses with a blow.
	 */
	public static AbilityResult well(ServerPlayerEntity player, RaceData data) {
		if (!ready(player, data, 1)) {
			return AbilityResult.COOLDOWN;
		}
		if (data.resource < WELL_COST) {
			return AbilityResult.NO_RESOURCE;
		}
		data.addResource(-WELL_COST);
		cooldown(player, data, 1, WELL_COOLDOWN);
		openWell(player, data, aim(player, 20));
		return AbilityResult.OK;
	}

	/** A Well of this player's opens at {@code center}. */
	public static void openWell(ServerPlayerEntity player, RaceData data, Vec3d center) {
		ServerWorld world = player.getServerWorld();
		int ticks = data.has(Talents.WELL_LONG) ? 120 : WELL_TICKS;
		double radius = wellRadius(data);
		WELLS.add(new Well(world, center, radius, now(player.getServer()) + ticks, player, new HashSet<>()));
		Fx.vortex(world, center, (float) radius * 0.45f, ticks, VOID_RGB);
		Fx.mark(world, ground(world, center), null, (float) radius * 0.45f, ticks + 10, VOID_INK, Fx.CRACKS);
		Fx.shake(player, 0.8f, 8);
		world.playSound(null, BlockPos.ofFloored(center), SoundEvents.BLOCK_BEACON_ACTIVATE, SoundCategory.PLAYERS, 1.2f, 0.5f);
		world.playSound(null, BlockPos.ofFloored(center), SoundEvents.BLOCK_PORTAL_TRIGGER, SoundCategory.PLAYERS, 0.5f, 1.6f);
	}

	/** The ground under a point (up to 12 blocks down), or the point itself over a drop. */
	private static Vec3d ground(ServerWorld world, Vec3d p) {
		BlockPos.Mutable pos = BlockPos.ofFloored(p).mutableCopy();
		for (int i = 0; i < 12; i++) {
			net.minecraft.util.shape.VoxelShape shape = world.getBlockState(pos).getCollisionShape(world, pos);
			if (!shape.isEmpty() && pos.getY() + shape.getMax(net.minecraft.util.math.Direction.Axis.Y) <= p.y) {
				return new Vec3d(p.x, pos.getY() + shape.getMax(net.minecraft.util.math.Direction.Axis.Y), p.z);
			}
			pos.move(net.minecraft.util.math.Direction.DOWN);
		}
		return p;
	}

	private static void tickWells(MinecraftServer server, long now) {
		for (Iterator<Well> it = WELLS.iterator(); it.hasNext(); ) {
			Well w = it.next();
			ServerPlayerEntity owner = w.owner().isRemoved() ? null : w.owner();
			ServerWorld world = w.world();
			Box reach = new Box(w.center(), w.center()).expand(w.radius());
			RaceData data = owner == null ? null : RaceState.of(owner);
			if (owner != null) {
				for (LivingEntity e : world.getEntitiesByClass(LivingEntity.class, reach, e -> enemy(e, owner))) {
					pull(e, w.center(), 0.16, w.radius());
					w.pulled().add(e.getUuid());
					if (now % 20 == 0 && e.getBoundingBox().getCenter().distanceTo(w.center()) < 1.8) {
						e.damage(world.getDamageSources().indirectMagic(owner, owner), data.has(Talents.WELL_HEART) ? 3 : 2);
					}
				}
			}
			// Arrows not of their own, and loose things; with Swallowing, arrows reaching the heart are gone
			for (Entity e : world.getOtherEntities(null, reach, e -> e instanceof ItemEntity
					|| e instanceof ProjectileEntity p && (owner == null || p.getOwner() != owner))) {
				pull(e, w.center(), 0.12, w.radius());
				if (e instanceof ProjectileEntity && data != null && data.has(Talents.WELL_EAT) && e.getPos().distanceTo(w.center()) < 1.2) {
					e.discard();
					data.addResource(2);
				}
			}
			if (now % 4 == 0) {
				world.spawnParticles(ModParticles.STAR, w.center().x, w.center().y, w.center().z, 2, 1.5, 1.5, 1.5, 0.0);
			}
			if (w.until() <= now) {
				it.remove();
				collapse(w, owner);
			}
		}
	}

	/** Drags an entity towards a point, harder the nearer it is, never past it. */
	private static void pull(Entity e, Vec3d center, double strength, double radius) {
		Vec3d to = center.subtract(e.getBoundingBox().getCenter());
		double d = to.length();
		if (d < 0.3) {
			e.setVelocity(e.getVelocity().multiply(0.3));
		} else {
			double force = strength * (0.4 + 0.6 * (1 - Math.min(1, d / radius)));
			e.setVelocity(e.getVelocity().multiply(0.6).add(to.normalize().multiply(force * Math.min(1, d))));
		}
		e.velocityModified = true;
		e.fallDistance = 0;
	}

	/** The Well collapses: a blow at its heart for whatever it holds, a flash, and the world drawing breath. */
	private static void collapse(Well w, ServerPlayerEntity owner) {
		ServerWorld world = w.world();
		Vec3d c = w.center();
		if (owner != null) {
			RaceData data = RaceState.of(owner);
			for (LivingEntity e : world.getEntitiesByClass(LivingEntity.class, new Box(c, c).expand(4), e -> enemy(e, owner))) {
				e.damage(world.getDamageSources().indirectMagic(owner, owner), data.has(Talents.WELL_COLLAPSE) ? 8 : 6);
			}
			if (data.has(Talents.WELL_HOLD)) {
				for (LivingEntity e : world.getEntitiesByClass(LivingEntity.class, new Box(c, c).expand(w.radius()), e -> enemy(e, owner))) {
					e.addStatusEffect(new StatusEffectInstance(StatusEffects.SLOWNESS, 60, 1));
				}
			}
			if (data != null && !w.pulled().isEmpty()) {
				Progression.addXp(owner, data, "voidborn_well", Math.min(8, w.pulled().size()), 40);
			}
			Fx.shake(owner, 1.6f, 10);
		}
		Fx.burst(world, c, 4.0f, 9, VOID_RGB);
		Fx.ring(world, ground(world, c), 6, 0.3f, 10, VOID_INK);
		world.spawnParticles(ModParticles.VOID_MOTE, c.x, c.y, c.z, 40, 0.3, 0.3, 0.3, 0.25);
		world.spawnParticles(ModParticles.STAR, c.x, c.y, c.z, 20, 1.2, 1.2, 1.2, 0.0);
		world.playSound(null, BlockPos.ofFloored(c), SoundEvents.ENTITY_GENERIC_EXPLODE, SoundCategory.PLAYERS, 0.7f, 0.5f);
		world.playSound(null, BlockPos.ofFloored(c), SoundEvents.BLOCK_BEACON_DEACTIVATE, SoundCategory.PLAYERS, 1.0f, 0.6f);
	}

	/**
	 * Excision: the creature you look at (up to 24 blocks, in plain sight) is torn into your pocket for 6 seconds and
	 * then put back where it was. An enemy comes back dazed; a friend comes back mended. Bosses are too big for it.
	 */
	public static AbilityResult excise(ServerPlayerEntity player, RaceData data) {
		return excise(player, data, lookedAt(player, EXCISE_RANGE));
	}

	/** The Excision of a given creature (the one looked at, or one a test picks). */
	public static AbilityResult excise(ServerPlayerEntity player, RaceData data, LivingEntity target) {
		// Pressed at nothing while holding someone: everyone held comes back now
		if (target == null || HELD.stream().anyMatch(h -> h.entity() == target)) {
			if (holding(player)) {
				release(player);
				return AbilityResult.OK;
			}
			return AbilityResult.NO_SIGHT;
		}
		if (tooBig(target)) {
			return AbilityResult.NO_SIGHT;
		}
		boolean ally = !enemy(target, player);
		// Only one enemy at a time; friends, as many as you like
		if (!ally && HELD.stream().anyMatch(h -> h.excised() && !h.ally() && h.owner().equals(player.getUuid()))) {
			player.sendMessage(Text.translatable("mages_legacy.voidborn.one_enemy"), true);
			return AbilityResult.HINTED;
		}
		if (!ready(player, data, 2)) {
			return AbilityResult.COOLDOWN;
		}
		int cost = exciseCost(data.has(Talents.EXCISE_CHEAP));
		if (data.resource < cost) {
			return AbilityResult.NO_RESOURCE;
		}
		ServerWorld pocket = Pocket.world(player.getServer());
		if (pocket == null) {
			return AbilityResult.LOCKED;
		}
		data.addResource(-cost);
		data.dirty = true;
		take(player, data, pocket, target, ally);
		// A player taken brings the players standing right beside them along
		if (target instanceof PlayerEntity) {
			for (ServerPlayerEntity other : player.getServerWorld().getPlayers(p -> p != player && p != target && p.isAlive() && !p.isSpectator()
					&& p.squaredDistanceTo(target) < 9 && HELD.stream().noneMatch(h -> h.entity() == p))) {
				take(player, data, pocket, other, true);
			}
		}
		if (!ally) {
			Progression.addXp(player, data, "voidborn_excise", 3, 30);
		}
		return AbilityResult.OK;
	}

	/** Lets go of everyone this Voidborn's Excision holds: they come back where they were. */
	private static void release(ServerPlayerEntity player) {
		long now = now(player.getServer());
		HELD.replaceAll(h -> h.excised() && h.owner().equals(player.getUuid())
				? new Held(h.entity(), h.owner(), h.origin(), h.pos(), h.yaw(), now, h.ally(), true) : h);
	}

	/** One creature torn into the pocket: an enemy into the sealed cell, a friend onto the island. */
	private static void take(ServerPlayerEntity player, RaceData data, ServerWorld pocket, LivingEntity target, boolean ally) {
		ServerWorld world = player.getServerWorld();
		int room = Pocket.room(player, data);
		Pocket.build(pocket, room, data.voidborn, Pocket.half(data));
		Vec3d from = target.getPos();
		float yaw = target.getYaw();
		// A thread of void to it, a rift behind it, and it is drawn in
		Fx.tendril(world, player, target, 10, VOID_RGB, STAR_RGB);
		Fx.rift(world, from.add(0, target.getHeight() / 2, 0), target.getHeight() + 1.2f, target.getWidth() + 1.0f, 18, VOID_RGB);
		Fx.burst(world, from.add(0, target.getHeight() / 2, 0), 1.2f + target.getWidth(), 7, VOID_INK);
		implode(world, from.add(0, target.getHeight() / 2, 0));
		world.playSound(null, target.getBlockPos(), SoundEvents.ITEM_CHORUS_FRUIT_TELEPORT, SoundCategory.PLAYERS, 1.2f, 0.5f);
		target.stopRiding();
		target.removeAllPassengers();
		Vec3d into = ally ? Pocket.center(room).add(world.getRandom().nextDouble() * 2 - 1, 0, world.getRandom().nextDouble() * 2 - 1) : Pocket.cell(room);
		pocket.setChunkForced((int) Math.floor(into.x) >> 4, (int) Math.floor(into.z) >> 4, true);
		Entity moved = Pocket.move(target, pocket, into, yaw);
		// Dropped from High: an enemy comes back out of the sky, if there is sky above its spot
		Vec3d back = from;
		if (!ally && data.has(Talents.EXCISE_DROP)) {
			Vec3d up = from.add(0, 10, 0);
			if (world.isSpaceEmpty(target.getBoundingBox().offset(0, 10, 0))) {
				back = up;
			}
		}
		HELD.add(new Held(moved, player.getUuid(), world, back, yaw, Long.MAX_VALUE, ally, true));
	}


	/** Motes of void drawn into a point from all around. */
	private static void implode(ServerWorld world, Vec3d c) {
		for (int i = 0; i < 24; i++) {
			double a = world.getRandom().nextDouble() * Math.PI * 2;
			double y = (world.getRandom().nextDouble() - 0.5) * 2;
			Vec3d out = new Vec3d(Math.cos(a) * 1.6, y, Math.sin(a) * 1.6);
			Vec3d at = c.add(out);
			world.spawnParticles(ModParticles.VOID_MOTE, at.x, at.y, at.z, 0, -out.x * 0.12, -out.y * 0.12, -out.z * 0.12, 1);
		}
	}

	/** The living creature the eye rests on within {@code range}, if no wall stands between. */
	private static LivingEntity lookedAt(ServerPlayerEntity player, double range) {
		Vec3d eye = player.getEyePos();
		Vec3d look = player.getRotationVec(1.0f);
		BlockHitResult wall = player.getWorld().raycast(new RaycastContext(eye, eye.add(look.multiply(range)), RaycastContext.ShapeType.COLLIDER,
				RaycastContext.FluidHandling.NONE, player));
		double reach = wall.getType() == HitResult.Type.MISS ? range : wall.getPos().distanceTo(eye);
		Vec3d end = eye.add(look.multiply(reach));
		Box box = player.getBoundingBox().stretch(look.multiply(reach)).expand(1);
		EntityHitResult hit = ProjectileUtil.raycast(player, eye, end, box, e -> e instanceof LivingEntity l && l.isAlive() && e != player, reach * reach);
		return hit != null && hit.getEntity() instanceof LivingEntity living ? living : null;
	}

	/**
	 * Someone joins the game standing in a pocket that isn't theirs (taken in by a friend's Excision, then gone
	 * offline): nobody holds them any more, so they come out at their spawn point.
	 */
	public static void onJoin(ServerPlayerEntity player) {
		RaceData data = RaceState.of(player);
		if (!Pocket.in(player) || (data != null && data.race == Race.VOIDBORN && Pocket.inOwnRoom(player, data))
				|| HELD.stream().anyMatch(h -> h.entity() == player)) {
			return;
		}
		ServerWorld to = player.getServer().getWorld(player.getSpawnPointDimension());
		BlockPos spawn = player.getSpawnPointPosition();
		Vec3d at = to != null && spawn != null
				? PlayerEntity.findRespawnPosition(to, spawn, player.getSpawnAngle(), player.isSpawnForced(), true).orElse(null) : null;
		if (at == null) {
			to = player.getServer().getOverworld();
			at = Vec3d.ofBottomCenter(to.getSpawnPos());
		}
		player.teleport(to, at.x, at.y, at.z, player.getYaw(), player.getPitch());
	}

	/** Whether this Voidborn holds someone in its pocket by the Excision now. */
	public static boolean holding(ServerPlayerEntity player) {
		return HELD.stream().anyMatch(h -> h.excised() && h.owner().equals(player.getUuid()));
	}

	/** What the client shows of a Voidborn beyond the shared data: whether its Excision holds someone. */
	public static net.minecraft.nbt.NbtCompound clientInfo(ServerPlayerEntity player) {
		net.minecraft.nbt.NbtCompound nbt = new net.minecraft.nbt.NbtCompound();
		nbt.putBoolean("holding", holding(player));
		return nbt;
	}

	private static void tickHeld(MinecraftServer server, long now) {
		for (Iterator<Held> it = HELD.iterator(); it.hasNext(); ) {
			Held h = it.next();
			ServerPlayerEntity owner = server.getPlayerManager().getPlayer(h.owner());
			// A held one is let go when its Voidborn lets go, leaves or dies
			boolean ownerGone = owner == null || !owner.isAlive();
			// An enemy held costs its Voidborn 1 void a second; when the void runs out, it falls back out
			boolean dry = false;
			if (h.excised() && !h.ally() && !ownerGone && h.until() > now && now % 20 == 0) {
				RaceData upkeep = RaceState.of(owner);
				if (upkeep.resource < 1) {
					dry = true;
				} else {
					upkeep.addResource(-1);
				}
			}
			if (h.until() > now && !dry && !ownerGone && !h.entity().isRemoved()) {
				// Gnawing Void: an enemy held by the Excision wears away, down to a third of its health
				if (h.excised() && !h.ally() && now % 20 == 0 && h.entity() instanceof LivingEntity living
						&& RaceState.of(owner).has(Talents.EXCISE_LONG) && living.getHealth() > living.getMaxHealth() / 3) {
					living.damage(living.getDamageSources().magic(), 2);
				}
				continue;
			}
			it.remove();
			if (owner != null && h.excised()) {
				// However it ended (let go, or gone inside), the Excision now starts to recharge
				RaceData data = RaceState.of(owner);
				if (ready(owner, data, 2)) {
					cooldown(owner, data, 2, EXCISE_COOLDOWN);
				}
				data.dirty = true;
			}
			Entity e = h.entity();
			ServerWorld pocket = Pocket.world(server);
			if (pocket != null && HELD.stream().noneMatch(o -> o.owner().equals(h.owner()))) {
				Vec3d cell = e.getPos();
				pocket.setChunkForced((int) Math.floor(cell.x) >> 4, (int) Math.floor(cell.z) >> 4, false);
			}
			if (e.isRemoved() || (e instanceof LivingEntity l && !l.isAlive())) {
				continue;
			}
			Entity back = Pocket.move(e, h.origin(), h.pos(), h.yaw());
			ServerWorld world = h.origin();
			Vec3d c = h.pos().add(0, back.getHeight() / 2, 0);
			Fx.rift(world, c, back.getHeight() + 1.2f, back.getWidth() + 1.0f, 16, VOID_RGB);
			Fx.burst(world, c, 1.2f + back.getWidth(), 7, VOID_RGB);
			world.spawnParticles(ModParticles.VOID_MOTE, c.x, c.y, c.z, 24, 0.2, 0.3, 0.2, 0.14);
			world.playSound(null, BlockPos.ofFloored(h.pos()), SoundEvents.ITEM_CHORUS_FRUIT_TELEPORT, SoundCategory.PLAYERS, 1.2f, 0.8f);
			if (back instanceof LivingEntity living) {
				if (h.ally()) {
					living.heal(6);
				} else {
					living.addStatusEffect(new StatusEffectInstance(StatusEffects.SLOWNESS, 60, 1));
					living.addStatusEffect(new StatusEffectInstance(StatusEffects.WEAKNESS, 60, 0));
				}
			}
		}
	}

	/**
	 * The Pocket: a rift opens before you and a moment later you are through it, in your own room of the pocket
	 * world; pressed there, the same way back to the very spot you left. Going in costs void; coming out is free.
	 */
	public static AbilityResult pocket(ServerPlayerEntity player, RaceData data) {
		if (PASSAGES.stream().anyMatch(p -> p.player() == player)) {
			return AbilityResult.HINTED;
		}
		boolean in = !Pocket.inOwnRoom(player, data);
		Long hurt = LAST_HURT.get(player.getUuid());
		if (in && hurt != null && now(player.getServer()) - hurt < POCKET_CALM) {
			player.sendMessage(Text.translatable("mages_legacy.voidborn.pocket_calm"), true);
			return AbilityResult.HINTED;
		}
		if (in) {
			if (!ready(player, data, 3)) {
				return AbilityResult.COOLDOWN;
			}
			int cost = pocketCost(data.has(Talents.POCKET_CHEAP));
			if (data.resource < cost) {
				return AbilityResult.NO_RESOURCE;
			}
			data.addResource(-cost);
			cooldown(player, data, 3, data.has(Talents.POCKET_QUICK) ? 80 : POCKET_COOLDOWN);
		}
		ServerWorld world = player.getServerWorld();
		Vec3d ahead = player.getPos().add(player.getRotationVec(1.0f).multiply(1, 0, 1).normalize().multiply(1.2)).add(0, 1.3, 0);
		Fx.rift(world, ahead, 2.8f, 1.7f, POCKET_DELAY + 18, VOID_RGB);
		Fx.voidScreen(player, POCKET_DELAY * 2 + 6);
		Fx.fov(player, -0.15f, POCKET_DELAY + 4);
		world.playSound(null, player.getBlockPos(), SoundEvents.BLOCK_PORTAL_TRIGGER, SoundCategory.PLAYERS, 0.4f, 1.8f);
		world.playSound(null, player.getBlockPos(), SoundEvents.ENTITY_ENDERMAN_TELEPORT, SoundCategory.PLAYERS, 0.8f, 0.6f);
		PASSAGES.add(new Passage(player, now(player.getServer()) + POCKET_DELAY, in, !in && data.alt && data.has(Talents.POCKET_HOME)));
		return AbilityResult.OK;
	}

	private static void tickPassages(long now) {
		for (Iterator<Passage> it = PASSAGES.iterator(); it.hasNext(); ) {
			Passage p = it.next();
			if (p.at() > now) {
				continue;
			}
			it.remove();
			ServerPlayerEntity player = p.player();
			if (player.isRemoved() || !player.isAlive()) {
				continue;
			}
			RaceData data = RaceState.of(player);
			if (p.in()) {
				Pocket.enter(player, data);
			} else {
				Pocket.leave(player, data, p.home());
				if (data.has(Talents.POCKET_WARD)) {
					player.addStatusEffect(new StatusEffectInstance(StatusEffects.RESISTANCE, 60, 1, true, true, true));
				}
			}
			// The rift closing behind on the far side
			ServerWorld world = player.getServerWorld();
			Vec3d at = player.getPos().add(0, 1.3, 0);
			Fx.rift(world, at, 2.8f, 1.7f, 14, VOID_RGB);
			world.spawnParticles(ModParticles.STAR, at.x, at.y, at.z, 20, 0.8, 1.0, 0.8, 0.0);
			world.playSound(null, player.getBlockPos(), SoundEvents.ENTITY_ENDERMAN_TELEPORT, SoundCategory.PLAYERS, 0.8f, 1.2f);
		}
	}

	/** Rift Step doors: whoever steps into the start of the way comes out at its end. */
	private static void tickDoors(MinecraftServer server, long now) {
		DOORS.removeIf(d -> d.until() < now);
		for (Door d : DOORS) {
			for (ServerPlayerEntity p : d.world().getPlayers(p -> p.isAlive() && !p.isSpectator() && p.getPos().distanceTo(d.from()) < 1.0
					&& !phasing(p))) {
				p.teleport(d.world(), d.to().x, d.to().y, d.to().z, p.getYaw(), p.getPitch());
				p.fallDistance = 0;
				Fx.burst(d.world(), d.to().add(0, 1, 0), 1.2f, 6, VOID_RGB);
				d.world().playSound(null, p.getBlockPos(), SoundEvents.ENTITY_ENDERMAN_TELEPORT, SoundCategory.PLAYERS, 0.8f, 1.3f);
			}
		}
	}

	/** Pocket Garden: in a Voidborn's room, whatever grows there grows three times as fast. */
	private static void tickGardens(MinecraftServer server) {
		ServerWorld pocket = Pocket.world(server);
		if (pocket == null) {
			return;
		}
		for (ServerPlayerEntity player : pocket.getPlayers()) {
			RaceData data = RaceState.of(player);
			if (data == null || !data.has(Talents.POCKET_GARDEN) || !Pocket.inOwnRoom(player, data)) {
				continue;
			}
			BlockPos c = BlockPos.ofFloored(Pocket.center(data.voidborn.room));
			int half = Pocket.half(data);
			for (BlockPos pos : BlockPos.iterate(c.add(-half, -1, -half), c.add(half, 2, half))) {
				net.minecraft.block.BlockState state = pocket.getBlockState(pos);
				if (state.hasRandomTicks() && (state.getBlock() instanceof net.minecraft.block.Fertilizable || state.isOf(net.minecraft.block.Blocks.SUGAR_CANE)
						|| state.isOf(net.minecraft.block.Blocks.CACTUS) || state.isOf(net.minecraft.block.Blocks.NETHER_WART))) {
					for (int i = 0; i < 2; i++) {
						state = pocket.getBlockState(pos);
						if (state.hasRandomTicks()) {
							state.randomTick(pocket, pos.toImmutable(), pocket.getRandom());
						}
					}
				}
			}
		}
	}

	/**
	 * The Mage's Gaze crown (Alt): every enemy within 16 blocks falls into the void at once for 4 seconds, and comes
	 * back dazed. Once in 5 minutes.
	 */
	public static AbilityResult gaze(ServerPlayerEntity player, RaceData data) {
		if (!ready(player, data, GAZE_SLOT)) {
			return AbilityResult.COOLDOWN;
		}
		if (data.resource < GAZE_COST) {
			return AbilityResult.NO_RESOURCE;
		}
		ServerWorld pocket = Pocket.world(player.getServer());
		ServerWorld world = player.getServerWorld();
		List<LivingEntity> enemies = world.getEntitiesByClass(LivingEntity.class, player.getBoundingBox().expand(16),
				e -> enemy(e, player) && !tooBig(e) && e.squaredDistanceTo(player) < 256).stream().limit(12).toList();
		if (pocket == null) {
			return AbilityResult.LOCKED;
		}
		if (enemies.isEmpty()) {
			return AbilityResult.NO_ENEMIES;
		}
		data.addResource(-GAZE_COST);
		cooldown(player, data, GAZE_SLOT, GAZE_COOLDOWN);
		int room = Pocket.room(player, data);
		Pocket.build(pocket, room, data.voidborn, Pocket.half(data));
		Vec3d cell = Pocket.cell(room);
		pocket.setChunkForced((int) Math.floor(cell.x) >> 4, (int) Math.floor(cell.z) >> 4, true);
		long until = now(player.getServer()) + 80;
		for (LivingEntity e : enemies) {
			Vec3d from = e.getPos();
			Fx.rift(world, from.add(0, e.getHeight() / 2, 0), e.getHeight() + 1.2f, e.getWidth() + 1.0f, 16, VOID_RGB);
			implode(world, from.add(0, e.getHeight() / 2, 0));
			e.stopRiding();
			e.removeAllPassengers();
			Entity moved = Pocket.move(e, pocket, cell, e.getYaw());
			HELD.add(new Held(moved, player.getUuid(), world, from, e.getYaw(), until, false, false));
		}
		// The mage's eye opens over the Voidborn for a moment
		Fx.vortex(world, player.getPos().add(0, 4, 0), 1.6f, 30, VOID_RGB);
		Fx.mark(world, player.getPos(), null, 6, 60, VOID_INK, Fx.CRACKS);
		Fx.voidScreen(player, 12);
		Fx.shake(player, 2.5f, 14);
		world.playSound(null, player.getBlockPos(), SoundEvents.ENTITY_WARDEN_SONIC_BOOM, SoundCategory.PLAYERS, 0.8f, 0.5f);
		return AbilityResult.OK;
	}

	/**
	 * Hearth of the Mage crown: a killing blow sends a Voidborn into its Pocket instead, with half its health and
	 * everything it carries, once in 20 minutes. It comes out (H) at its spawn point.
	 */
	public static boolean allowDeath(ServerPlayerEntity player) {
		RaceData data = RaceState.of(player);
		if (data == null || data.race != Race.VOIDBORN || !data.has(Talents.POCKET_HEARTH) || !ready(player, data, HEARTH_SLOT)
				|| Pocket.world(player.getServer()) == null) {
			return true;
		}
		cooldown(player, data, HEARTH_SLOT, LONG_COOLDOWN);
		player.setHealth(player.getMaxHealth() / 2);
		player.extinguish();
		player.clearStatusEffects();
		Fx.rift(player.getServerWorld(), player.getPos().add(0, 1.3, 0), 2.8f, 1.7f, 20, VOID_RGB);
		Fx.voidScreen(player, 24);
		boolean inPocket = Pocket.inOwnRoom(player, data);
		if (!inPocket) {
			Pocket.enter(player, data);
			// Out again to safety, not back into whatever killed it
			data.voidborn.returnWorld = player.getSpawnPointDimension();
			BlockPos spawn = player.getSpawnPointPosition() != null ? player.getSpawnPointPosition() : player.getServer().getOverworld().getSpawnPos();
			data.voidborn.returnPos = Vec3d.ofBottomCenter(spawn.up());
		}
		player.sendMessage(Text.translatable("mages_legacy.voidborn.hearth").formatted(net.minecraft.util.Formatting.LIGHT_PURPLE), false);
		return false;
	}

	// ---- every tick ----

	public static void tick(MinecraftServer server) {
		long now = now(server);
		tickSteps(now);
		tickWells(server, now);
		tickHeld(server, now);
		tickPassages(now);
		tickDoors(server, now);
		Pocket.catchFallers(server);
		if (now % 20 == 0) {
			tickGardens(server);
		}
	}

	/** The race changed: whatever it held goes back, its wells close. */
	public static void cleanup(ServerPlayerEntity player) {
		WELLS.removeIf(w -> w.owner() == player);
		STEPS.removeIf(s -> s.player() == player);
		HELD.replaceAll(h -> h.owner().equals(player.getUuid()) ? new Held(h.entity(), h.owner(), h.origin(), h.pos(), h.yaw(), 0, h.ally(), h.excised()) : h);
	}
}
