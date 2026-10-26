package euf.mageslegacy.voidborn;

import euf.mageslegacy.fx.Fx;
import euf.mageslegacy.fx.ModParticles;
import euf.mageslegacy.race.AbilityResult;
import euf.mageslegacy.race.Progression;
import euf.mageslegacy.race.Race;
import euf.mageslegacy.race.RaceData;
import euf.mageslegacy.race.RaceState;
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
	public static final double STEP_RANGE = 14;
	private static final int STEP_TICKS = 10;
	private static final int STEP_COOLDOWN = 100;
	private static final int WELL_TICKS = 80;
	private static final int WELL_COOLDOWN = 500;
	private static final double WELL_RADIUS = 8;
	private static final int EXCISE_TICKS = 120;
	private static final int EXCISE_COOLDOWN = 300;
	private static final double EXCISE_RANGE = 24;
	private static final int POCKET_COOLDOWN = 200;
	/** Ticks between the rift opening and the step through it. */
	private static final int POCKET_DELAY = 8;

	/** The void's colours: violet light, the near-black of the void itself laid on as real colour, starlight. */
	public static final int VOID_RGB = 0x9B7FD4;
	public static final int VOID_INK = Fx.INK | 0x2A1446;
	public static final int STAR_RGB = 0xE8E0FF;

	private record Step(ServerPlayerEntity player, List<Vec3d> path, Set<UUID> hit, boolean through) {}

	private record Well(ServerWorld world, Vec3d center, long until, UUID owner, Set<UUID> pulled) {}

	/** A creature held in its puller's pocket: where it came from, and when it goes back. */
	private record Held(Entity entity, UUID owner, ServerWorld origin, Vec3d pos, float yaw, long until, boolean ally) {}

	/** A step into (or out of) the pocket, made a moment after the rift opens. */
	private record Passage(ServerPlayerEntity player, long at, boolean in) {}

	private static final List<Step> STEPS = new ArrayList<>();
	private static final java.util.Map<UUID, Integer> STEP_AT = new java.util.HashMap<>();
	private static final List<Well> WELLS = new ArrayList<>();
	private static final List<Held> HELD = new ArrayList<>();
	private static final List<Passage> PASSAGES = new ArrayList<>();
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
			player.damage(world.getDamageSources().magic(), 2);
			world.spawnParticles(ModParticles.VOID_MOTE, player.getX(), player.getY() + 1, player.getZ(), 8, 0.3, 0.5, 0.3, 0.02);
			world.playSound(null, player.getBlockPos(), SoundEvents.BLOCK_FIRE_EXTINGUISH, SoundCategory.PLAYERS, 0.5f, 1.4f);
		} else if (world.hasRain(BlockPos.ofFloored(player.getEyePos()))) {
			refresh(player, StatusEffects.WEAKNESS, 60);
			refresh(player, StatusEffects.SLOWNESS, 60);
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
		if (victim instanceof EndermanEntity) {
			Progression.addXp(player, data, 8);
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
			default -> AbilityResult.LOCKED;
		};
	}

	/**
	 * Void Step: half a second's passage through the void, 14 blocks where you look, through walls, floors and
	 * ceilings alike, coming out where the body fits (never inside the rock, never in lava). Bedrock and other
	 * unbreakable things stop it. What it passes through on the way takes 4 and is slowed.
	 */
	public static AbilityResult step(ServerPlayerEntity player, RaceData data) {
		if (STEPS.stream().anyMatch(s -> s.player() == player)) {
			return AbilityResult.HINTED;
		}
		if (!ready(player, data, 0)) {
			return AbilityResult.COOLDOWN;
		}
		if (data.resource < STEP_COST) {
			return AbilityResult.NO_RESOURCE;
		}
		List<Vec3d> path = stepPath(player, STEP_RANGE);
		if (path.size() < 2) {
			return AbilityResult.NO_SPACE;
		}
		data.addResource(-STEP_COST);
		cooldown(player, data, 0, STEP_COOLDOWN);
		ServerWorld world = player.getServerWorld();
		boolean through = path.stream().anyMatch(p -> !fits(player, p));
		player.stopRiding();
		STEPS.add(new Step(player, path, new HashSet<>(), through));
		STEP_AT.put(player.getUuid(), 0);
		player.addStatusEffect(new StatusEffectInstance(StatusEffects.INVISIBILITY, STEP_TICKS + 4, 0, true, false, false));
		Vec3d start = path.get(0);
		Vec3d end = path.get(path.size() - 1);
		// The world tears where the body goes in and where it will come out; between, a dark streak through the rock
		Fx.rift(world, start.add(0, 1, 0), 2.4f, 1.3f, 16, VOID_RGB);
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
				e.damage(world.getDamageSources().indirectMagic(player, player), 4);
				e.addStatusEffect(new StatusEffectInstance(StatusEffects.SLOWNESS, 60, 1));
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
		player.setVelocity(Vec3d.ZERO);
		player.velocityModified = true;
		player.fallDistance = 0;
		Fx.burst(world, at.add(0, 1, 0), 1.7f, 7, VOID_RGB);
		Fx.ring(world, at, 0.3f, 2.8f, 10, VOID_INK);
		Fx.mark(world, at, null, 1.6f, 40, VOID_INK, Fx.CRACKS);
		Fx.shake(player, 1.2f, 7);
		world.spawnParticles(ModParticles.VOID_MOTE, at.x, at.y + 1, at.z, 30, 0.2, 0.4, 0.2, 0.12);
		world.spawnParticles(ModParticles.STAR, at.x, at.y + 1, at.z, 12, 0.6, 0.8, 0.6, 0.0);
		world.playSound(null, player.getBlockPos(), SoundEvents.ENTITY_ENDERMAN_TELEPORT, SoundCategory.PLAYERS, 1.0f, 1.2f);
		if (through) {
			Progression.addXp(player, RaceState.of(player), 1);
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
		ServerWorld world = player.getServerWorld();
		Vec3d center = aim(player, 20);
		WELLS.add(new Well(world, center, now(player.getServer()) + WELL_TICKS, player.getUuid(), new HashSet<>()));
		Fx.vortex(world, center, 2.4f, WELL_TICKS, VOID_RGB);
		Fx.mark(world, ground(world, center), null, 3.6f, WELL_TICKS + 10, VOID_INK, Fx.CRACKS);
		Fx.shake(player, 0.8f, 8);
		world.playSound(null, BlockPos.ofFloored(center), SoundEvents.BLOCK_BEACON_ACTIVATE, SoundCategory.PLAYERS, 1.2f, 0.5f);
		world.playSound(null, BlockPos.ofFloored(center), SoundEvents.BLOCK_PORTAL_TRIGGER, SoundCategory.PLAYERS, 0.5f, 1.6f);
		return AbilityResult.OK;
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
			ServerPlayerEntity owner = server.getPlayerManager().getPlayer(w.owner());
			ServerWorld world = w.world();
			Box reach = new Box(w.center(), w.center()).expand(WELL_RADIUS);
			if (owner != null) {
				for (LivingEntity e : world.getEntitiesByClass(LivingEntity.class, reach, e -> enemy(e, owner))) {
					pull(e, w.center(), 0.16);
					w.pulled().add(e.getUuid());
					if (now % 10 == 0 && e.getBoundingBox().getCenter().distanceTo(w.center()) < 1.8) {
						e.damage(world.getDamageSources().indirectMagic(owner, owner), 3);
					}
				}
			}
			// Arrows not of their own, and loose things
			for (Entity e : world.getOtherEntities(null, reach, e -> e instanceof ItemEntity
					|| e instanceof ProjectileEntity p && (owner == null || p.getOwner() != owner))) {
				pull(e, w.center(), 0.12);
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
	private static void pull(Entity e, Vec3d center, double strength) {
		Vec3d to = center.subtract(e.getBoundingBox().getCenter());
		double d = to.length();
		if (d < 0.3) {
			e.setVelocity(e.getVelocity().multiply(0.3));
		} else {
			double force = strength * (0.4 + 0.6 * (1 - Math.min(1, d / WELL_RADIUS)));
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
			for (LivingEntity e : world.getEntitiesByClass(LivingEntity.class, new Box(c, c).expand(4), e -> enemy(e, owner))) {
				e.damage(world.getDamageSources().indirectMagic(owner, owner), 6);
			}
			RaceData data = RaceState.of(owner);
			if (data != null && !w.pulled().isEmpty()) {
				Progression.addXp(owner, data, Math.min(8, w.pulled().size()));
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
		if (!ready(player, data, 2)) {
			return AbilityResult.COOLDOWN;
		}
		if (data.resource < EXCISE_COST) {
			return AbilityResult.NO_RESOURCE;
		}
		LivingEntity target = lookedAt(player, EXCISE_RANGE);
		if (target == null || tooBig(target) || HELD.stream().anyMatch(h -> h.entity() == target)) {
			return AbilityResult.NO_TARGET;
		}
		ServerWorld pocket = Pocket.world(player.getServer());
		if (pocket == null) {
			return AbilityResult.LOCKED;
		}
		data.addResource(-EXCISE_COST);
		cooldown(player, data, 2, EXCISE_COOLDOWN);
		ServerWorld world = player.getServerWorld();
		boolean ally = !enemy(target, player);
		int room = Pocket.room(player, data);
		Pocket.build(pocket, room, data.voidborn);
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
		Vec3d into = ally ? Pocket.center(room) : Pocket.cell(room);
		pocket.setChunkForced((int) Math.floor(into.x) >> 4, (int) Math.floor(into.z) >> 4, true);
		Entity moved = Pocket.move(target, pocket, into, yaw);
		HELD.add(new Held(moved, player.getUuid(), world, from, yaw, now(player.getServer()) + EXCISE_TICKS, ally));
		if (!ally) {
			Progression.addXp(player, data, 3);
		}
		return AbilityResult.OK;
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

	private static void tickHeld(MinecraftServer server, long now) {
		for (Iterator<Held> it = HELD.iterator(); it.hasNext(); ) {
			Held h = it.next();
			boolean ownerGone = server.getPlayerManager().getPlayer(h.owner()) == null;
			if (h.until() > now && !ownerGone && !h.entity().isRemoved()) {
				continue;
			}
			it.remove();
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
		if (in) {
			if (!ready(player, data, 3)) {
				return AbilityResult.COOLDOWN;
			}
			if (data.resource < POCKET_COST) {
				return AbilityResult.NO_RESOURCE;
			}
			data.addResource(-POCKET_COST);
			cooldown(player, data, 3, POCKET_COOLDOWN);
		}
		ServerWorld world = player.getServerWorld();
		Vec3d ahead = player.getPos().add(player.getRotationVec(1.0f).multiply(1, 0, 1).normalize().multiply(1.2)).add(0, 1.3, 0);
		Fx.rift(world, ahead, 2.8f, 1.7f, POCKET_DELAY + 18, VOID_RGB);
		Fx.voidScreen(player, POCKET_DELAY * 2 + 6);
		Fx.fov(player, -0.15f, POCKET_DELAY + 4);
		world.playSound(null, player.getBlockPos(), SoundEvents.BLOCK_PORTAL_TRIGGER, SoundCategory.PLAYERS, 0.4f, 1.8f);
		world.playSound(null, player.getBlockPos(), SoundEvents.ENTITY_ENDERMAN_TELEPORT, SoundCategory.PLAYERS, 0.8f, 0.6f);
		PASSAGES.add(new Passage(player, now(player.getServer()) + POCKET_DELAY, in));
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
				Pocket.leave(player, data);
			}
			// The rift closing behind on the far side
			ServerWorld world = player.getServerWorld();
			Vec3d at = player.getPos().add(0, 1.3, 0);
			Fx.rift(world, at, 2.8f, 1.7f, 14, VOID_RGB);
			world.spawnParticles(ModParticles.STAR, at.x, at.y, at.z, 20, 0.8, 1.0, 0.8, 0.0);
			world.playSound(null, player.getBlockPos(), SoundEvents.ENTITY_ENDERMAN_TELEPORT, SoundCategory.PLAYERS, 0.8f, 1.2f);
		}
	}

	// ---- every tick ----

	public static void tick(MinecraftServer server) {
		long now = now(server);
		tickSteps(now);
		tickWells(server, now);
		tickHeld(server, now);
		tickPassages(now);
		Pocket.catchFallers(server);
	}

	/** The race changed: whatever it held goes back, its wells close. */
	public static void cleanup(ServerPlayerEntity player) {
		WELLS.removeIf(w -> w.owner().equals(player.getUuid()));
		STEPS.removeIf(s -> s.player() == player);
		HELD.replaceAll(h -> h.owner().equals(player.getUuid()) ? new Held(h.entity(), h.owner(), h.origin(), h.pos(), h.yaw(), 0, h.ally()) : h);
	}
}
