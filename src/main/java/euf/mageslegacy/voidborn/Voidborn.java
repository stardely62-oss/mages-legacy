package euf.mageslegacy.voidborn;

import euf.mageslegacy.MagesLegacy;
import euf.mageslegacy.fx.Fx;
import euf.mageslegacy.race.Abilities;
import euf.mageslegacy.race.Ability;
import euf.mageslegacy.race.Race;
import euf.mageslegacy.race.RaceState;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.Set;
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
	private static final float STEP_RANGE = 12;
	private static final int STEP_TICKS = 25;
	private static final int GRAVITY_TICKS = 4 * 20;
	/** Heavier Gravity reaches 2 blocks out from the caster's body: a 4×4 area around them. */
	private static final double GRAVITY_REACH = 2;

	/** A player's live state, never saved: cooldowns, a walk through the void, gravity, the last blow taken. */
	public static final class State {
		public final int[] cooldown = new int[Ability.SLOTS];
		public final int[] cooldownTotal = new int[Ability.SLOTS];
		public Step step;
		public int gravity;
		public int lastHurt = -10_000;

		public void startCooldown(int slot, int ticks) {
			cooldown[slot] = ticks;
			cooldownTotal[slot] = ticks;
		}
	}

	/** A walk through the in-between from one point to another. */
	public static final class Step {
		final Vec3 from;
		final Vec3 to;
		int tick;

		Step(Vec3 from, Vec3 to) {
			this.from = from;
			this.to = to;
		}

		public int left() {
			return STEP_TICKS - tick;
		}
	}

	private static final Map<UUID, State> STATES = new HashMap<>();

	private Voidborn() {
	}

	public static void register() {
		NeoForge.EVENT_BUS.addListener(Voidborn::onTick);
		NeoForge.EVENT_BUS.addListener(Voidborn::onIncomingDamage);
		NeoForge.EVENT_BUS.addListener(Voidborn::onDamaged);
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

		if (state.step != null) {
			tickStep(player, state);
		}
		if (state.gravity > 0) {
			tickGravity(player, state);
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

	/** Opens a rift and starts the walk towards where the player looks, to the farthest point the body fits in. */
	public static Abilities.Refusal step(ServerPlayer player) {
		State state = state(player);
		if (state.step != null) {
			return new Abilities.Refusal("mages_legacy.refuse.busy");
		}
		ServerLevel level = player.serverLevel();
		Vec3 from = player.position();
		Vec3 dir = player.getLookAngle();
		// Unbreakable blocks (bedrock, barriers) are the edge of the world: the void doesn't lead through them
		double reach = STEP_RANGE;
		for (double d = 0.25; d <= STEP_RANGE; d += 0.25) {
			if (unbreakableAt(level, player, from.add(dir.scale(d)))) {
				reach = d - 0.5;
				break;
			}
		}
		Vec3 to = null;
		for (double d = reach; d >= 2; d -= 0.5) {
			Vec3 candidate = from.add(dir.scale(d));
			if (fits(level, player, candidate)) {
				to = candidate;
				break;
			}
		}
		if (to == null) {
			return new Abilities.Refusal("mages_legacy.refuse.no_exit");
		}
		state.step = new Step(from, to);
		player.setNoGravity(true);
		player.noPhysics = true;
		player.setDeltaMovement(Vec3.ZERO);
		level.playSound(null, from.x, from.y, from.z, SoundEvents.ENDERMAN_TELEPORT, SoundSource.PLAYERS, 0.7f, 0.5f);
		level.playSound(null, from.x, from.y, from.z, SoundEvents.PORTAL_AMBIENT, SoundSource.PLAYERS, 0.5f, 1.6f);
		Fx.play(level, Fx.RIFT, from.add(0, 1, 0), 30);
		Fx.play(level, Fx.STEP_PATH, null, from.add(0, 1, 0), to.add(0, 1, 0), STEP_TICKS);
		Fx.play(level, Fx.STEP_VEIL, player, from, to, STEP_TICKS);
		return null;
	}

	private static void tickStep(ServerPlayer player, State state) {
		Step step = state.step;
		step.tick++;
		double t = Math.min(1, step.tick / (double) STEP_TICKS);
		// Slow to leave, fast through the middle, slow to arrive
		double eased = t * t * (3 - 2 * t);
		Vec3 at = step.from.lerp(step.to, eased);
		player.connection.teleport(at.x, at.y, at.z, 0, 0, Set.of(RelativeMovement.X_ROT, RelativeMovement.Y_ROT));
		player.setDeltaMovement(Vec3.ZERO);
		player.fallDistance = 0;
		if (step.tick == STEP_TICKS - 4) {
			Fx.play(player.serverLevel(), Fx.RIFT, step.to.add(0, 1, 0), 30);
		}
		if (step.tick >= STEP_TICKS) {
			state.step = null;
			player.setNoGravity(false);
			player.noPhysics = false;
			ServerLevel level = player.serverLevel();
			level.playSound(null, at.x, at.y, at.z, SoundEvents.ENDERMAN_TELEPORT, SoundSource.PLAYERS, 0.6f, 0.7f);
			state.startCooldown(Ability.VOID_STEP.slot, Ability.VOID_STEP.cooldown);
			RaceState.sync(player);
		}
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

	// ---- Heavier Gravity -------------------------------------------------------------------------------------

	public static Abilities.Refusal gravity(ServerPlayer player) {
		State state = state(player);
		state.gravity = GRAVITY_TICKS;
		ServerLevel level = player.serverLevel();
		level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.RESPAWN_ANCHOR_CHARGE, SoundSource.PLAYERS, 0.8f, 0.5f);
		Fx.play(level, Fx.GRAVITY, player, player.position(), player.position(), GRAVITY_TICKS);
		pressDown(player, true);
		return null;
	}

	private static void tickGravity(ServerPlayer player, State state) {
		state.gravity--;
		if ((GRAVITY_TICKS - state.gravity) % 20 == 0) {
			pressDown(player, true);
		}
		if (state.gravity == 0) {
			player.level().playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.BEACON_DEACTIVATE, SoundSource.PLAYERS, 0.6f, 0.6f);
		}
	}

	/** Every enemy in the area: 1 damage and Slowness I for a little longer than the next pulse. */
	private static void pressDown(ServerPlayer player, boolean hurt) {
		for (LivingEntity target : gravityTargets(player)) {
			target.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 30, 0, false, true), player);
			if (hurt) {
				target.hurt(player.damageSources().indirectMagic(player, player), 1);
			}
		}
	}

	public static List<LivingEntity> gravityTargets(ServerPlayer player) {
		AABB area = new AABB(player.getX() - GRAVITY_REACH, player.getY() - 1, player.getZ() - GRAVITY_REACH, player.getX() + GRAVITY_REACH,
				player.getY() + 3, player.getZ() + GRAVITY_REACH);
		return player.level().getEntitiesOfClass(LivingEntity.class, area, e -> e != player && e.isAlive() && isEnemy(player, e));
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
