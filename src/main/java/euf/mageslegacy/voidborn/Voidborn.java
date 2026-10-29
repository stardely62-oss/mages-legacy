package euf.mageslegacy.voidborn;

import euf.mageslegacy.MagesLegacy;
import euf.mageslegacy.fx.Fx;
import euf.mageslegacy.race.Abilities;
import euf.mageslegacy.race.Ability;
import euf.mageslegacy.race.Race;
import euf.mageslegacy.race.RaceState;
import euf.mageslegacy.race.Progression;
import euf.mageslegacy.race.Talent;
import euf.mageslegacy.race.Talents;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.entity.FlyingMob;
import net.minecraft.world.entity.animal.FlyingAnimal;
import net.minecraft.world.entity.monster.Blaze;
import net.minecraft.world.entity.monster.Vex;
import net.minecraft.world.entity.projectile.Projectile;
import euf.mageslegacy.net.StepPayload;
import net.neoforged.neoforge.common.Tags;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageType;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.OwnableEntity;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.entity.RelativeMovement;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.living.EnderManAngerEvent;
import net.neoforged.neoforge.event.entity.living.LivingDamageEvent;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;
import net.neoforged.neoforge.event.entity.player.AttackEntityEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;

/**
 * The Voidborn: the Void (their resource), the Void Step, Heavier Gravity, and what the void gives and takes:
 * endermen don't mind their gaze, water burns them, rain weakens them. The Pocket lives in {@link Pocket}.
 */
public final class Voidborn {
	public static final ResourceKey<DamageType> VOID_WATER = ResourceKey.create(Registries.DAMAGE_TYPE, MagesLegacy.id("void_water"));

	/** Void per tick: 1 a second, half again in the dark, twice in the End and the Pocket. */
	private static final float REGEN = 1f / 20;
	/** Half a second through the void, as in the first version: a dash, not a stroll. */
	private static final int STEP_TICKS = 10;
	private static final int GRAVITY_TICKS = 4 * 20;
	/** A mark in space lasts 5 minutes and can be walked to from 32 blocks. */
	private static final int MARK_TICKS = 5 * 60 * 20;
	private static final double MARK_REACH = 32;
	/** An open door (Open Door talent) lets allies follow for 3 seconds after the walk. */
	private static final int DOOR_TICKS = 3 * 20;

	/** A player's live state, never saved: cooldowns, a walk through the void, gravity, the last blow taken. */
	public static final class State {
		public final int[] cooldown = new int[Ability.SLOTS];
		public final int[] cooldownTotal = new int[Ability.SLOTS];
		public Step step;
		public int gravity;
		public int lastHurt = -10_000;
		/** A mark in space (Space Mark talent): where, in which world, until when (game time). */
		Vec3 mark;
		ResourceKey<Level> markLevel;
		long markUntil;

		public void startCooldown(int slot, int ticks) {
			cooldown[slot] = ticks;
			cooldownTotal[slot] = ticks;
		}
	}

	/** A walk through the in-between from one point to another; a companion's walk has no rifts of its own. */
	public static final class Step {
		final Vec3 from;
		final Vec3 to;
		final int ticks;
		final boolean companion;
		int tick;

		Step(Vec3 from, Vec3 to, int ticks, boolean companion) {
			this.from = from;
			this.to = to;
			this.ticks = ticks;
			this.companion = companion;
		}

		public int left() {
			return ticks - tick;
		}
	}

	/** An open door left behind a walk: allies stepping into it come out at its other end. */
	private record Door(UUID owner, ResourceKey<Level> level, Vec3 from, Vec3 to, long until) {
	}

	private static final Map<UUID, State> STATES = new HashMap<>();
	private static final List<Door> DOORS = new ArrayList<>();
	/** Who Heavier Gravity holds now: the creature, the Voidborn holding it and until when (for the Black Hole's trial). */
	private static final Map<UUID, UUID> PRESSED_BY = new HashMap<>();
	private static final Map<UUID, Long> PRESSED_UNTIL = new HashMap<>();

	private Voidborn() {
	}

	public static void register() {
		NeoForge.EVENT_BUS.addListener(Voidborn::onTick);
		NeoForge.EVENT_BUS.addListener(Voidborn::onServerTick);
		NeoForge.EVENT_BUS.addListener(Voidborn::onIncomingDamage);
		NeoForge.EVENT_BUS.addListener(Voidborn::onDamaged);
		NeoForge.EVENT_BUS.addListener(Voidborn::onDeath);
		NeoForge.EVENT_BUS.addListener(Voidborn::onEndermanAnger);
		NeoForge.EVENT_BUS.addListener(Voidborn::onAttack);
		NeoForge.EVENT_BUS.addListener(Voidborn::onRightClickItem);
		NeoForge.EVENT_BUS.addListener(Voidborn::onRightClickBlock);
		NeoForge.EVENT_BUS.addListener(Voidborn::onLeftClickBlock);
		NeoForge.EVENT_BUS.addListener(Voidborn::onInteractEntity);
		NeoForge.EVENT_BUS.addListener(Voidborn::onLogout);
	}

	public static State state(Player player) {
		return STATES.computeIfAbsent(player.getUUID(), id -> new State());
	}

	/** Forgets the live state (a new race, a logout); a walk in progress ends where it is. */
	public static void reset(ServerPlayer player) {
		State state = STATES.remove(player.getUUID());
		if (state != null && state.step != null) {
			player.setNoGravity(false);
			player.noPhysics = false;
		}
	}

	public static boolean stepping(Player player) {
		State state = STATES.get(player.getUUID());
		return state != null && state.step != null;
	}

	public static boolean inDarkOrEnd(ServerPlayer player) {
		return player.level().dimension() == Level.END || Pocket.isPocket(player.level())
				|| player.level().getMaxLocalRawBrightness(player.blockPosition()) <= 7;
	}

	private static void onTick(PlayerTickEvent.Post event) {
		if (!(event.getEntity() instanceof ServerPlayer player)) {
			return;
		}
		State state = state(player);
		for (int i = 0; i < Ability.SLOTS; i++) {
			if (state.cooldown[i] > 0) {
				state.cooldown[i]--;
			}
		}
		// Companions of a walk (Fellow Traveller) walk too, whatever their race
		if (state.step != null) {
			tickStep(player, state);
		}
		if (!RaceState.is(player, Race.VOIDBORN)) {
			return;
		}
		ServerLevel level = player.serverLevel();
		// The Void gathers by itself
		float regen = REGEN;
		if (level.getMaxLocalRawBrightness(player.blockPosition()) <= 7) {
			regen *= 1.5f;
		}
		if (level.dimension() == Level.END || Pocket.isPocket(level)) {
			regen *= 2;
		}
		RaceState.setResource(player, RaceState.resource(player) + regen);

		if (state.gravity > 0) {
			tickGravity(player, state);
		}
		if (state.mark != null && player.tickCount % 40 == 0) {
			if (level.getGameTime() > state.markUntil) {
				state.mark = null;
			} else if (level.dimension() == state.markLevel) {
				Fx.rift(level, state.mark.add(0, 1.2, 0), 1.2f, 0.6f, 44);
			}
		}
		if (player.tickCount % 20 == 0 && !player.isCreative() && !player.isSpectator()) {
			// Water burns like it burns endermen; rain weakens
			if (player.isInWaterOrBubble()) {
				DamageSource burn = new DamageSource(level.registryAccess().registryOrThrow(Registries.DAMAGE_TYPE).getHolderOrThrow(VOID_WATER));
				player.hurt(burn, 1);
			} else if (level.isRainingAt(player.blockPosition().above())) {
				player.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, 40, 0, true, true));
				player.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 40, 0, true, true));
			}
		}
	}

	// ---- The Void Step ---------------------------------------------------------------------------------------

	public static double stepRange(ServerPlayer player) {
		return Talents.has(player, Talent.STEP_FAR_2) ? 24 : Talents.has(player, Talent.STEP_FAR) ? 16 : 12;
	}

	public static int stepTicks(ServerPlayer player) {
		return STEP_TICKS;
	}

	/**
	 * Sneaking with the Space Mark talent leaves a mark in space instead of walking (free, no cooldown); returns
	 * whether it did.
	 */
	public static boolean tryMark(ServerPlayer player) {
		if (!player.isShiftKeyDown() || !Talents.has(player, Talent.STEP_MARK)) {
			return false;
		}
		State state = state(player);
		state.mark = player.position();
		state.markLevel = player.level().dimension();
		state.markUntil = player.level().getGameTime() + MARK_TICKS;
		Fx.rift(player.serverLevel(), state.mark.add(0, 1.2, 0), 1.6f, 0.8f, 30);
		Fx.burst(player.serverLevel(), state.mark.add(0, 1, 0), 0.8f, 6, false);
		player.level().playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.AMETHYST_BLOCK_RESONATE, SoundSource.PLAYERS, 1f,
				0.6f);
		player.displayClientMessage(Component.translatable("mages_legacy.message.mark_set").withStyle(ChatFormatting.LIGHT_PURPLE), true);
		return true;
	}

	/** Opens a rift and starts the walk towards where the player looks (or to their mark), to a point the body fits in. */
	public static Abilities.Refusal step(ServerPlayer player) {
		State state = state(player);
		if (state.step != null) {
			return new Abilities.Refusal("mages_legacy.refuse.busy");
		}
		ServerLevel level = player.serverLevel();
		Vec3 from = player.position();
		Vec3 to = null;
		// A mark in reach pulls the walk to itself
		if (state.mark != null && state.markLevel == level.dimension() && level.getGameTime() <= state.markUntil
				&& state.mark.distanceTo(from) <= MARK_REACH && fits(level, player, state.mark)) {
			to = state.mark;
			state.mark = null;
		}
		if (to == null) {
			Vec3 dir = player.getLookAngle();
			double range = stepRange(player);
			// Unbreakable blocks (bedrock, barriers) are the edge of the world: the void doesn't lead through them
			double reach = range;
			for (double d = 0.25; d <= range; d += 0.25) {
				if (unbreakableAt(level, player, from.add(dir.scale(d)))) {
					reach = d - 0.5;
					break;
				}
			}
			for (double d = reach; d >= 2; d -= 0.5) {
				Vec3 candidate = from.add(dir.scale(d));
				if (fits(level, player, candidate)) {
					to = candidate;
					break;
				}
			}
		}
		if (to == null) {
			return new Abilities.Refusal("mages_legacy.refuse.no_exit");
		}
		int ticks = stepTicks(player);
		boolean door = Talents.has(player, Talent.STEP_DOOR);
		begin(player, from, to, ticks, false);
		// The first version's walk: a rift at each end, a streak between them, the view sinking into the void
		Fx.rift(level, from.add(0, 1, 0), 2.4f, 1.3f, door ? ticks + DOOR_TICKS : 16);
		Fx.rift(level, to.add(0, 1, 0), 2.4f, 1.3f, door ? ticks + DOOR_TICKS : ticks + 14);
		Fx.ribbon(level, from, to, ticks + 12, 1.1f);
		Fx.burst(level, from.add(0, 1, 0), 1.3f, 6, true);
		Fx.sparkle(level, from.add(0, 1, 0), 24, 0.06f);
		level.playSound(null, from.x, from.y, from.z, SoundEvents.ENDERMAN_TELEPORT, SoundSource.PLAYERS, 1.0f, 0.5f);
		level.playSound(null, from.x, from.y, from.z, SoundEvents.RESPAWN_ANCHOR_DEPLETE.value(), SoundSource.PLAYERS, 0.6f, 1.6f);
		if (door) {
			DOORS.add(new Door(player.getUUID(), level.dimension(), from, to, level.getGameTime() + ticks + DOOR_TICKS));
		}
		// Fellow Traveller: players right beside go along, each keeping their place beside the walker
		if (Talents.has(player, Talent.STEP_ALLY)) {
			for (Player other : level.getEntitiesOfClass(Player.class, player.getBoundingBox().inflate(2, 1, 2),
					p -> p != player && p.isAlive() && !p.isSpectator() && !stepping(p))) {
				if (other instanceof ServerPlayer companion) {
					Vec3 offset = companion.position().subtract(from);
					Vec3 end = to.add(offset);
					if (fits(level, companion, end)) {
						begin(companion, companion.position(), end, ticks, true);
					}
				}
			}
		}
		return null;
	}

	private static void begin(ServerPlayer player, Vec3 from, Vec3 to, int ticks, boolean companion) {
		state(player).step = new Step(from, to, ticks, companion);
		player.stopRiding();
		player.setNoGravity(true);
		player.noPhysics = true;
		player.setDeltaMovement(Vec3.ZERO);
		// The body is gone into the void: only the cloud it leaves along the way is seen
		player.addEffect(new MobEffectInstance(MobEffects.INVISIBILITY, ticks + 4, 0, true, false, false));
		PacketDistributor.sendToPlayer(player, new StepPayload(from, to, ticks));
		Fx.stepTrail(player.serverLevel(), player, ticks);
		Fx.voidScreen(player, ticks + 4);
		Fx.fov(player, 0.3f, ticks + 6);
		// In the in-between the world's sounds come from far away
		Fx.muffle(player, ticks + 4);
	}

	private static void tickStep(ServerPlayer player, State state) {
		Step step = state.step;
		step.tick++;
		double t = Math.min(1, step.tick / (double) step.ticks);
		// Slow to leave, fast through the middle, slow to arrive
		double eased = t * t * (3 - 2 * t);
		Vec3 at = step.from.lerp(step.to, eased);
		player.connection.teleport(at.x, at.y, at.z, 0, 0, Set.of(RelativeMovement.X_ROT, RelativeMovement.Y_ROT));
		player.setDeltaMovement(Vec3.ZERO);
		player.fallDistance = 0;
		if (step.tick < step.ticks) {
			return;
		}
		state.step = null;
		player.setNoGravity(false);
		player.noPhysics = false;
		player.removeEffect(MobEffects.INVISIBILITY);
		player.hurtMarked = true;
		ServerLevel level = player.serverLevel();
		// Coming out, as in the first version: a flash of the void, a wave, the ground cracked, motes and stars thrown out
		Fx.burst(level, at.add(0, 1, 0), 1.7f, 7, false);
		Fx.ring(level, at, 0.3f, 2.8f, 10);
		Fx.cracks(level, at, null, 1.6f, 40);
		Fx.sparkle(level, at.add(0, 1, 0), 30, 0.12f);
		Fx.shake(player, 1.2f, 7);
		level.playSound(null, at.x, at.y, at.z, SoundEvents.ENDERMAN_TELEPORT, SoundSource.PLAYERS, 1.0f, 1.2f);
		if (step.companion) {
			return;
		}
		state.startCooldown(Ability.VOID_STEP.slot, Abilities.cooldown(player, Ability.VOID_STEP));
		Talents.addProgress(player, Talents.STEP_BLOCKS, (int) Math.round(step.from.distanceTo(step.to)));
		// Walking through something, not just across open air, teaches the race
		if (!level.noCollision(player, player.getBoundingBox().move(step.from.subtract(at)).expandTowards(step.to.subtract(step.from)).deflate(0.2))) {
			Progression.addXp(player, Progression.STEP, 1, 20);
		}
		// Without a Trace: whatever hunted the walker loses them
		if (Talents.has(player, Talent.STEP_TRACE)) {
			for (Mob mob : level.getEntitiesOfClass(Mob.class, player.getBoundingBox().inflate(16), m -> m.getTarget() == player)) {
				mob.setTarget(null);
			}
		}
		// Heavy Exit: a weak field of gravity where the walk comes out
		if (Talents.has(player, Talent.STEP_HEAVY)) {
			for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class, player.getBoundingBox().inflate(2.5, 1, 2.5),
					e -> e != player && e.isAlive() && isEnemy(player, e))) {
				e.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 40, 0, false, true), player);
			}
			Fx.ring(level, at, 0.3f, 2.4f, 10);
			Fx.cracks(level, at, null, 1.3f, 30);
		}
		RaceState.sync(player);
	}

	/** Whether the player's standing body fits at this point: no blocks, no lava, inside the world. */
	static boolean fits(ServerLevel level, Player player, Vec3 at) {
		AABB box = player.getDimensions(Pose.STANDING).makeBoundingBox(at);
		if (box.minY < level.getMinBuildHeight() || box.maxY > level.getMaxBuildHeight() || !level.getWorldBorder().isWithinBounds(box)) {
			return false;
		}
		if (!level.noCollision(player, box)) {
			return false;
		}
		return BlockPos.betweenClosedStream(box).noneMatch(pos -> level.getFluidState(pos).is(FluidTags.LAVA));
	}

	private static boolean unbreakableAt(ServerLevel level, Player player, Vec3 feet) {
		AABB box = player.getDimensions(Pose.STANDING).makeBoundingBox(feet);
		return BlockPos.betweenClosedStream(box).anyMatch(pos -> {
			var block = level.getBlockState(pos);
			return !block.isAir() && block.getDestroySpeed(level, pos) < 0;
		});
	}

	/** Open Door: allies who step into the walker's first rift in time come out of the second. */
	private static void onServerTick(ServerTickEvent.Post event) {
		if (DOORS.isEmpty()) {
			return;
		}
		MinecraftServer server = event.getServer();
		for (Iterator<Door> it = DOORS.iterator(); it.hasNext(); ) {
			Door door = it.next();
			ServerLevel level = server.getLevel(door.level());
			if (level == null || level.getGameTime() > door.until()) {
				it.remove();
				continue;
			}
			for (ServerPlayer other : level.getEntitiesOfClass(ServerPlayer.class, new AABB(door.from(), door.from()).inflate(0.9, 1.5, 0.9),
					p -> !p.getUUID().equals(door.owner()) && p.isAlive() && !p.isSpectator() && !stepping(p))) {
				if (fits(level, other, door.to())) {
					Fx.burst(level, door.from().add(0, 1, 0), 0.9f, 6, true);
					other.teleportTo(door.to().x, door.to().y, door.to().z);
					other.fallDistance = 0;
					Fx.burst(level, door.to().add(0, 1, 0), 0.9f, 6, false);
					Fx.voidScreen(other, 8);
					level.playSound(null, door.to().x, door.to().y, door.to().z, SoundEvents.ENDERMAN_TELEPORT, SoundSource.PLAYERS, 0.6f, 0.8f);
				}
			}
		}
	}

	// ---- Heavier Gravity -------------------------------------------------------------------------------------

	public static Abilities.Refusal gravity(ServerPlayer player) {
		State state = state(player);
		state.gravity = GRAVITY_TICKS;
		ServerLevel level = player.serverLevel();
		level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.RESPAWN_ANCHOR_CHARGE, SoundSource.PLAYERS, 0.8f, 0.5f);
		float reach = (float) gravityReach(player);
		// Space folds in on the caster and grows heavy: the field holds for as long as the gravity does
		Fx.gravityField(level, player, reach, GRAVITY_TICKS);
		Fx.shake(player, 0.7f, 8);
		Fx.fov(player, -0.08f, 12);
		pressDown(player);
		return null;
	}

	private static void tickGravity(ServerPlayer player, State state) {
		state.gravity--;
		if ((GRAVITY_TICKS - state.gravity) % 20 == 0) {
			pressDown(player);
		}
		AABB area = gravityArea(player);
		// Earthbound: whatever flies in the field is dragged to the ground
		if (Talents.has(player, Talent.GRAV_FLIERS)) {
			for (LivingEntity e : gravityTargets(player)) {
				if (flies(e) && !e.getType().is(Tags.EntityTypes.BOSSES)) {
					Vec3 v = e.getDeltaMovement();
					e.setDeltaMovement(v.x * 0.6, Math.min(v.y, -0.45), v.z * 0.6);
					e.hurtMarked = true;
				}
			}
		}
		// Bent Paths: what is shot through the field falls
		if (Talents.has(player, Talent.GRAV_ARROWS)) {
			for (Projectile shot : player.level().getEntitiesOfClass(Projectile.class, area,
					p -> !(p.getOwner() instanceof Player))) {
				Vec3 v = shot.getDeltaMovement();
				shot.setDeltaMovement(v.x * 0.5, Math.min(v.y, -0.5), v.z * 0.5);
				shot.hurtMarked = true;
			}
		}
		if (state.gravity == 0) {
			player.level().playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.BEACON_DEACTIVATE, SoundSource.PLAYERS, 0.6f, 0.6f);
		}
	}

	private static boolean flies(LivingEntity e) {
		return e.isNoGravity() || e instanceof FlyingMob || e instanceof FlyingAnimal || e instanceof Blaze || e instanceof Vex;
	}

	/** Every enemy in the area: 1 damage and Slowness for a little longer than the next pulse, pulled in with Attraction. */
	private static void pressDown(ServerPlayer player) {
		int slowness = Talents.has(player, Talent.GRAV_SLOW) ? 1 : 0;
		boolean pull = Talents.has(player, Talent.GRAV_PULL);
		long until = player.level().getGameTime() + 40;
		for (LivingEntity target : gravityTargets(player)) {
			target.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 30, slowness, false, true), player);
			PRESSED_BY.put(target.getUUID(), player.getUUID());
			PRESSED_UNTIL.put(target.getUUID(), until);
			target.hurt(player.damageSources().indirectMagic(player, player), 1);
			Fx.press(player.serverLevel(), target);
			Progression.addXp(player, Progression.GRAVITY, 1, 30);
			if (pull && !target.getType().is(Tags.EntityTypes.BOSSES)) {
				Vec3 in = player.position().subtract(target.position()).multiply(1, 0, 1);
				if (in.lengthSqr() > 0.5) {
					target.setDeltaMovement(target.getDeltaMovement().add(in.normalize().scale(0.45)).add(0, 0.08, 0));
					target.hurtMarked = true;
				}
			}
		}
	}

	public static double gravityReach(ServerPlayer player) {
		return Talents.has(player, Talent.GRAV_WIDE) ? 3 : 2;
	}

	private static AABB gravityArea(ServerPlayer player) {
		double reach = gravityReach(player);
		return new AABB(player.getX() - reach, player.getY() - 1, player.getZ() - reach, player.getX() + reach, player.getY() + 3,
				player.getZ() + reach);
	}

	public static List<LivingEntity> gravityTargets(ServerPlayer player) {
		return player.level().getEntitiesOfClass(LivingEntity.class, gravityArea(player), e -> e != player && e.isAlive() && isEnemy(player, e));
	}

	/** Monsters, and anything that has set its mind on this player; never players, pets or armour stands. */
	static boolean isEnemy(ServerPlayer player, LivingEntity entity) {
		if (entity instanceof Player || entity instanceof ArmorStand) {
			return false;
		}
		if (entity instanceof OwnableEntity owned && owned.getOwnerUUID() != null) {
			return false;
		}
		return entity instanceof Enemy || entity instanceof Mob mob && mob.getTarget() == player;
	}

	/** An enemy dying while held by Heavier Gravity counts towards the Black Hole's trial. */
	private static void onDeath(LivingDeathEvent event) {
		UUID victim = event.getEntity().getUUID();
		UUID caster = PRESSED_BY.remove(victim);
		Long until = PRESSED_UNTIL.remove(victim);
		if (caster != null && until != null && event.getEntity().level().getGameTime() <= until
				&& event.getEntity().level().getServer() != null) {
			ServerPlayer player = event.getEntity().level().getServer().getPlayerList().getPlayer(caster);
			if (player != null) {
				Talents.addProgress(player, Talents.GRAVITY_KILLS, 1);
			}
		}
		if (PRESSED_BY.size() > 512) {
			long now = event.getEntity().level().getGameTime();
			PRESSED_UNTIL.entrySet().removeIf(e -> e.getValue() < now);
			PRESSED_BY.keySet().retainAll(PRESSED_UNTIL.keySet());
		}
	}

	// ---- Events ----------------------------------------------------------------------------------------------

	/** In the in-between nothing reaches the walker. */
	private static void onIncomingDamage(LivingIncomingDamageEvent event) {
		if (event.getEntity() instanceof ServerPlayer player && stepping(player)) {
			event.setCanceled(true);
		}
	}

	private static void onDamaged(LivingDamageEvent.Post event) {
		if (event.getEntity() instanceof ServerPlayer player && event.getNewDamage() > 0) {
			state(player).lastHurt = player.tickCount;
		}
	}

	public static boolean hurtRecently(ServerPlayer player, int ticks) {
		return player.tickCount - state(player).lastHurt < ticks;
	}

	private static void onEndermanAnger(EnderManAngerEvent event) {
		if (event.getPlayer() instanceof ServerPlayer player && RaceState.is(player, Race.VOIDBORN)) {
			event.setCanceled(true);
		}
	}

	private static void onAttack(AttackEntityEvent event) {
		if (stepping(event.getEntity())) {
			event.setCanceled(true);
		}
	}

	private static void onRightClickItem(PlayerInteractEvent.RightClickItem event) {
		if (stepping(event.getEntity())) {
			event.setCanceled(true);
		}
	}

	private static void onRightClickBlock(PlayerInteractEvent.RightClickBlock event) {
		if (stepping(event.getEntity())) {
			event.setCanceled(true);
		}
	}

	private static void onLeftClickBlock(PlayerInteractEvent.LeftClickBlock event) {
		if (stepping(event.getEntity())) {
			event.setCanceled(true);
		}
	}

	private static void onInteractEntity(PlayerInteractEvent.EntityInteract event) {
		if (stepping(event.getEntity())) {
			event.setCanceled(true);
		}
	}

	private static void onLogout(PlayerEvent.PlayerLoggedOutEvent event) {
		if (event.getEntity() instanceof ServerPlayer player) {
			State state = STATES.get(player.getUUID());
			// A walk cut short by leaving ends at its exit, never inside a wall
			if (state != null && state.step != null) {
				player.teleportTo(state.step.to.x, state.step.to.y, state.step.to.z);
			}
			reset(player);
		}
	}
}
