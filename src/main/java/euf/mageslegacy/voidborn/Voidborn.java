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
import net.minecraft.world.entity.monster.Creeper;
import net.minecraft.core.Direction;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.neoforged.neoforge.event.entity.EntityTeleportEvent;
import net.neoforged.neoforge.event.entity.living.LivingChangeTargetEvent;
import net.neoforged.neoforge.event.entity.living.LivingEvent;
import net.neoforged.neoforge.event.entity.living.LivingKnockBackEvent;
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
	/** A second and a quarter in the void between worlds, then out at the far end. */
	private static final int STEP_TICKS = 25;
	private static final int GRAVITY_TICKS = 4 * 20;
	/** A mark in space lasts 5 minutes and can be walked to from 32 blocks. */
	private static final int MARK_TICKS = 5 * 60 * 20;
	private static final double MARK_REACH = 32;
	/** An open door (Open Door talent) lets allies follow for 3 seconds after the walk. */
	private static final int DOOR_TICKS = 3 * 20;
	/** How high the walk may climb to come out on top of what the player looks at. */
	private static final int CLIMB = 8;
	private static final int ECHO_TICKS = 3 * 20;
	private static final int AMBUSH_TICKS = 2 * 20;
	private static final float AMBUSH_DAMAGE = 3;
	private static final float RUPTURE_DAMAGE = 3;
	private static final int LINGER_TICKS = 3 * 20;
	private static final int FEAST_MAX = 3 * 20;
	private static final int SHELTER_COOLDOWN = 10 * 60 * 20;
	private static final float SHELTER_HEALTH = 6;

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
		/** Echo of the Step: where the last walk began, until when it can be walked back to. */
		Vec3 echo;
		ResourceKey<Level> echoLevel;
		long echoUntil;
		/** Strike from the Void: the first blow until then is heavier. */
		long ambushUntil;
		/** Without a Trace: until then nothing farther than a few blocks can set its mind on this player. */
		long hiddenUntil;
		/** Lingering Weight: the field left behind where Heavier Gravity ended. */
		Vec3 linger;
		long lingerUntil;
		double lingerReach;
		/** Weight Feeds: how much longer the field has grown this time. */
		int feast;
		/** Saving Rift: when it can save again, and whether it is about to. */
		long shelterReady;
		boolean shelterNow;

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
		/** A walk back along an Echo: free, and it doesn't start the cooldown again. */
		boolean echo;
		/** Rupture: who has already been torn by this walk. */
		final Set<UUID> struck = new java.util.HashSet<>();
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
	/** Chains of Space: who can't jump or teleport, until when. */
	private static final Map<UUID, Long> CHAINED = new HashMap<>();

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
		NeoForge.EVENT_BUS.addListener(Voidborn::onAmbush);
		NeoForge.EVENT_BUS.addListener(Voidborn::onTarget);
		NeoForge.EVENT_BUS.addListener(Voidborn::onKnockBack);
		NeoForge.EVENT_BUS.addListener(Voidborn::onEnderTeleport);
		NeoForge.EVENT_BUS.addListener(Voidborn::onJump);
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
		if (state.linger != null) {
			tickLinger(player, state);
		}
		if (state.shelterNow) {
			state.shelterNow = false;
			shelter(player, state);
		}
		// Eyes of the Void: the dark is no darkness to them
		if (player.tickCount % 20 == 0 && Talents.has(player, Talent.VOID_EYES) && level.getMaxLocalRawBrightness(player.blockPosition()) <= 7) {
			player.addEffect(new MobEffectInstance(MobEffects.NIGHT_VISION, 260, 0, true, false, true));
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
			to = stepTarget(level, player);
		}
		if (to == null) {
			return new Abilities.Refusal("mages_legacy.refuse.no_exit");
		}
		int ticks = stepTicks(player);
		boolean door = Talents.has(player, Talent.STEP_DOOR);
		begin(player, from, to, ticks, false);
		// The body sinks into the void where it stood: darkness folding in, motes drawn after it
		sinkFx(level, from);
		// Open Door: the way stays open, so the rifts are there for allies to see
		if (door) {
			Fx.rift(level, from.add(0, 1.2, 0), 2.4f, 1.3f, ticks + DOOR_TICKS);
			Fx.rift(level, to.add(0, 1.2, 0), 2.4f, 1.3f, ticks + DOOR_TICKS);
		}
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

	/**
	 * Where the walk leads. Looking at a wall or a ceiling, through it to the farthest open space behind it; looking at
	 * the top of something, or into a wall with nothing behind it, up onto its surface; looking at open air, as far as
	 * the body fits. The look is taken from the eyes, so a glance at the ground nearby means the ground nearby.
	 */
	public static Vec3 stepTarget(ServerLevel level, ServerPlayer player) {
		Vec3 eye = player.getEyePosition();
		Vec3 dir = player.getLookAngle();
		Vec3 feet = new Vec3(0, player.getEyeHeight(Pose.STANDING), 0);
		Vec3 from = player.position();
		double range = stepRange(player);
		// Unbreakable blocks (bedrock, barriers) are the edge of the world: the void doesn't lead through them
		double reach = range;
		for (double d = 0.25; d <= range; d += 0.25) {
			if (unbreakableAt(level, eye.add(dir.scale(d)))) {
				reach = d - 0.5;
				break;
			}
		}
		if (reach < 1) {
			return null;
		}
		BlockHitResult hit = level.clip(new ClipContext(eye, eye.add(dir.scale(reach)), ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, player));
		boolean blocked = hit.getType() == HitResult.Type.BLOCK;
		double wall = blocked ? hit.getLocation().distanceTo(eye) : reach;
		if (blocked && hit.getDirection() != Direction.UP) {
			for (double d = reach; d > wall + 0.4; d -= 0.5) {
				Vec3 candidate = eye.add(dir.scale(d)).subtract(feet);
				if (fits(level, player, candidate)) {
					return candidate;
				}
			}
		}
		if (blocked) {
			Vec3 top = surface(level, player, hit);
			if (top != null && top.distanceTo(from) >= 1.5) {
				return top;
			}
		}
		for (double d = wall - 0.3; d >= 1.5; d -= 0.5) {
			Vec3 candidate = eye.add(dir.scale(d)).subtract(feet);
			if (fits(level, player, candidate) && candidate.distanceTo(from) >= 1.5) {
				return candidate;
			}
		}
		return null;
	}

	/** The first place to stand on top of the block that was looked at, climbing its column up to {@link #CLIMB} blocks. */
	private static Vec3 surface(ServerLevel level, ServerPlayer player, BlockHitResult hit) {
		BlockPos base = hit.getBlockPos();
		Vec3 exact = hit.getLocation();
		for (int k = 1; k <= CLIMB; k++) {
			BlockPos at = base.above(k);
			if (unbreakable(level, at)) {
				return null;
			}
			if (level.getBlockState(at.below()).getCollisionShape(level, at.below()).isEmpty()) {
				continue;
			}
			// Where the eye fell if the body fits there, else the middle of the block
			for (Vec3 c : new Vec3[] {new Vec3(exact.x, at.getY(), exact.z), Vec3.atBottomCenterOf(at)}) {
				if (fits(level, player, c)) {
					return c;
				}
			}
		}
		return null;
	}

	/** Echo of the Step: within a few seconds of a walk the same key walks back to where it began, free. */
	public static boolean tryEcho(ServerPlayer player) {
		State state = state(player);
		ServerLevel level = player.serverLevel();
		if (state.echo == null || state.step != null || state.echoLevel != level.dimension() || level.getGameTime() > state.echoUntil
				|| !Talents.has(player, Talent.STEP_ECHO)) {
			return false;
		}
		Vec3 back = state.echo;
		state.echo = null;
		if (!fits(level, player, back)) {
			return false;
		}
		Vec3 from = player.position();
		int ticks = stepTicks(player);
		begin(player, from, back, ticks, false);
		state.step.echo = true;
		sinkFx(level, from);
		level.playSound(null, from.x, from.y, from.z, SoundEvents.ENDERMAN_TELEPORT, SoundSource.PLAYERS, 0.8f, 0.8f);
		return true;
	}

	private static void begin(ServerPlayer player, Vec3 from, Vec3 to, int ticks, boolean companion) {
		state(player).step = new Step(from, to, ticks, companion);
		player.stopRiding();
		player.setNoGravity(true);
		player.noPhysics = true;
		player.setDeltaMovement(Vec3.ZERO);
		// The body is gone into the void: only the cloud it leaves along the way is seen
		player.addEffect(new MobEffectInstance(MobEffects.INVISIBILITY, ticks + 4, 0, true, false, false));
		// The walker's own view goes into the void (ClientStep); the world's sounds come from far away
		PacketDistributor.sendToPlayer(player, new StepPayload(from, to, ticks));
		Fx.muffle(player, ticks + 4);
	}

	private static void sinkFx(ServerLevel level, Vec3 at) {
		Fx.burst(level, at.add(0, 1, 0), 1.1f, 7, true);
		Fx.sparkle(level, at.add(0, 1, 0), 24, 0.05f);
		if (onGround(level, at)) {
			Fx.cracks(level, at, null, 1.1f, 30);
		}
	}

	/** Whether something solid lies right under these feet (marks on the ground only go where there is ground). */
	private static boolean onGround(ServerLevel level, Vec3 feet) {
		BlockPos below = BlockPos.containing(feet.x, feet.y - 0.2, feet.z);
		return !level.getBlockState(below).getCollisionShape(level, below).isEmpty();
	}

	private static void tickStep(ServerPlayer player, State state) {
		Step step = state.step;
		step.tick++;
		// In the void the body hangs still; it comes out at the far end all at once
		player.setDeltaMovement(Vec3.ZERO);
		player.fallDistance = 0;
		if (step.tick < step.ticks) {
			return;
		}
		Vec3 at = step.to;
		player.connection.teleport(at.x, at.y, at.z, 0, 0, Set.of(RelativeMovement.X_ROT, RelativeMovement.Y_ROT));
		// Rupture: whatever stood along the way is torn a little
		if (!step.companion && Talents.has(player, Talent.STEP_RUPTURE)) {
			rupture(player, step);
		}
		state.step = null;
		player.setNoGravity(false);
		player.noPhysics = false;
		player.removeEffect(MobEffects.INVISIBILITY);
		player.hurtMarked = true;
		ServerLevel level = player.serverLevel();
		// Coming out: a flash of the void, motes and stars thrown out, and on solid ground a wave and cracks
		Fx.burst(level, at.add(0, 1, 0), 1.5f, 7, false);
		Fx.sparkle(level, at.add(0, 1, 0), 30, 0.1f);
		if (onGround(level, at)) {
			Fx.ring(level, at, 0.3f, 2.6f, 10);
			Fx.cracks(level, at, null, 1.5f, 40);
		}
		level.playSound(null, at.x, at.y, at.z, SoundEvents.ENDERMAN_TELEPORT, SoundSource.PLAYERS, 1.0f, 1.2f);
		if (step.companion) {
			return;
		}
		long now = level.getGameTime();
		if (!step.echo) {
			state.startCooldown(Ability.VOID_STEP.slot, Abilities.cooldown(player, Ability.VOID_STEP));
			Talents.addProgress(player, Talents.STEP_BLOCKS, (int) Math.round(step.from.distanceTo(step.to)));
			// Walking through something, not just across open air, teaches the race; Stone Path gives some Void back for it
			if (!level.noCollision(player, player.getBoundingBox().move(step.from.subtract(at)).expandTowards(step.to.subtract(step.from)).deflate(0.2))) {
				Progression.addXp(player, Progression.STEP, 1, 20);
				if (Talents.has(player, Talent.STEP_STONE)) {
					RaceState.setResource(player, RaceState.resource(player) + 8);
				}
			}
			if (Talents.has(player, Talent.STEP_ECHO)) {
				state.echo = step.from;
				state.echoLevel = level.dimension();
				state.echoUntil = now + ECHO_TICKS;
			}
		}
		// Without a Trace: whatever hunted the walker loses them, and for 3 seconds can't find them again from afar
		if (Talents.has(player, Talent.STEP_TRACE)) {
			for (Mob mob : level.getEntitiesOfClass(Mob.class, player.getBoundingBox().inflate(16), m -> m.getTarget() == player)) {
				mob.setTarget(null);
			}
			state.hiddenUntil = now + 60;
		}
		if (Talents.has(player, Talent.STEP_SOFT)) {
			player.addEffect(new MobEffectInstance(MobEffects.SLOW_FALLING, 60, 0, true, false, true));
		}
		if (Talents.has(player, Talent.STEP_AMBUSH)) {
			state.ambushUntil = now + AMBUSH_TICKS;
		}
		// Sense of the Void: everything hostile around shows through the walls for a moment
		if (Talents.has(player, Talent.STEP_SENSE)) {
			for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class, player.getBoundingBox().inflate(12), e -> e.isAlive() && isEnemy(player, e))) {
				e.addEffect(new MobEffectInstance(MobEffects.GLOWING, 80, 0, false, false), player);
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

	private static void rupture(ServerPlayer player, Step step) {
		ServerLevel level = player.serverLevel();
		Vec3 a = step.from.add(0, 0.9, 0);
		Vec3 b = step.to.add(0, 0.9, 0);
		AABB swept = new AABB(step.from, step.to).inflate(1.2, 1.2, 1.2).expandTowards(0, 1.8, 0);
		for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class, swept, e -> e != player && e.isAlive() && isEnemy(player, e))) {
			if (distanceToSegment(e.position().add(0, e.getBbHeight() / 2, 0), a, b) <= 1.2 && step.struck.add(e.getUUID())) {
				e.hurt(player.damageSources().indirectMagic(player, player), RUPTURE_DAMAGE);
				e.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 40, 0, false, true), player);
				Fx.press(level, e);
			}
		}
	}

	private static double distanceToSegment(Vec3 p, Vec3 a, Vec3 b) {
		Vec3 ab = b.subtract(a);
		double len = ab.lengthSqr();
		double t = len < 1.0E-6 ? 0 : Math.max(0, Math.min(1, p.subtract(a).dot(ab) / len));
		return p.distanceTo(a.add(ab.scale(t)));
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

	private static boolean unbreakableAt(ServerLevel level, Vec3 point) {
		return unbreakable(level, BlockPos.containing(point));
	}

	private static boolean unbreakable(ServerLevel level, BlockPos pos) {
		var block = level.getBlockState(pos);
		return !block.isAir() && block.getDestroySpeed(level, pos) < 0;
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
		state.feast = 0;
		state.linger = null;
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
		// Heavy Fuse: creepers under the weight can't gather themselves to burst
		if (Talents.has(player, Talent.GRAV_FUSE)) {
			for (LivingEntity e : gravityTargets(player)) {
				if (e instanceof Creeper creeper && !creeper.isIgnited()) {
					creeper.setSwellDir(-1);
				}
			}
		}
		if (state.gravity == 0) {
			player.level().playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.BEACON_DEACTIVATE, SoundSource.PLAYERS, 0.6f, 0.6f);
			// Lingering Weight: the field stays where it ended a little longer, slowing without harm
			if (Talents.has(player, Talent.GRAV_LINGER)) {
				state.linger = player.position();
				state.lingerUntil = player.level().getGameTime() + LINGER_TICKS;
				state.lingerReach = gravityReach(player);
				Fx.ring(player.serverLevel(), state.linger, 0.3f, (float) state.lingerReach + 0.5f, 14);
				Fx.cracks(player.serverLevel(), state.linger, null, (float) state.lingerReach, LINGER_TICKS);
			}
		}
	}

	private static void tickLinger(ServerPlayer player, State state) {
		ServerLevel level = player.serverLevel();
		if (level.getGameTime() > state.lingerUntil) {
			state.linger = null;
			return;
		}
		if (player.tickCount % 10 != 0) {
			return;
		}
		double r = state.lingerReach;
		Vec3 c = state.linger;
		for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class, new AABB(c.x - r, c.y - 1, c.z - r, c.x + r, c.y + 3, c.z + r),
				e -> e != player && e.isAlive() && isEnemy(player, e))) {
			e.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 20, 0, false, true), player);
		}
	}

	private static boolean flies(LivingEntity e) {
		return e.isNoGravity() || e instanceof FlyingMob || e instanceof FlyingAnimal || e instanceof Blaze || e instanceof Vex;
	}

	/** Every enemy in the area: 1 damage and Slowness for a little longer than the next pulse, pulled in with Attraction. */
	private static void pressDown(ServerPlayer player) {
		int slowness = Talents.has(player, Talent.GRAV_SLOW) ? 1 : 0;
		boolean pull = Talents.has(player, Talent.GRAV_PULL);
		boolean chain = Talents.has(player, Talent.GRAV_CHAIN);
		long until = player.level().getGameTime() + 40;
		List<LivingEntity> targets = gravityTargets(player);
		// Each pulse is heard as a heavy beat and seen as a ring tightening from the field's edge
		player.level().playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.WARDEN_HEARTBEAT, SoundSource.PLAYERS, 1.2f, 0.55f);
		if (!targets.isEmpty()) {
			player.level().playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.ANVIL_LAND, SoundSource.PLAYERS, 0.25f, 0.5f);
		}
		// The Weight Feeds: every held enemy gives a little Void back, up to 3 a pulse
		if (Talents.has(player, Talent.GRAV_DRAIN) && !targets.isEmpty()) {
			RaceState.setResource(player, RaceState.resource(player) + Math.min(3, targets.size()));
		}
		for (LivingEntity target : targets) {
			if (chain) {
				CHAINED.put(target.getUUID(), until - 10);
			}
			target.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 30, slowness, false, true), player);
			PRESSED_BY.put(target.getUUID(), player.getUUID());
			PRESSED_UNTIL.put(target.getUUID(), until);
			target.hurt(player.damageSources().indirectMagic(player, player), 1);
			Fx.press(player.serverLevel(), target);
			// The weight drives it into the ground: dust of whatever it stands on bursts out from under it
			ServerLevel level = player.serverLevel();
			BlockPos under = BlockPos.containing(target.getX(), target.getY() - 0.2, target.getZ());
			var ground = level.getBlockState(under);
			if (!ground.isAir()) {
				level.sendParticles(new net.minecraft.core.particles.BlockParticleOption(net.minecraft.core.particles.ParticleTypes.BLOCK, ground),
						target.getX(), target.getY() + 0.1, target.getZ(), 14, target.getBbWidth() * 0.4, 0.05, target.getBbWidth() * 0.4, 0.15);
			}
			if (!target.getType().is(Tags.EntityTypes.BOSSES)) {
				Vec3 v = target.getDeltaMovement();
				target.setDeltaMovement(v.x * 0.5, Math.min(v.y, -0.35), v.z * 0.5);
				target.hurtMarked = true;
			}
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
		return Talents.has(player, Talent.GRAV_WIDE) ? 4.5 : 3;
	}

	private static AABB gravityArea(ServerPlayer player) {
		double reach = gravityReach(player);
		return new AABB(player.getX() - reach, player.getY() - 1, player.getZ() - reach, player.getX() + reach, player.getY() + 3,
				player.getZ() + reach);
	}

	public static List<LivingEntity> gravityTargets(ServerPlayer player) {
		double reach = gravityReach(player);
		// A circle, as the field is drawn on the ground
		return player.level().getEntitiesOfClass(LivingEntity.class, gravityArea(player), e -> e != player && e.isAlive() && isEnemy(player, e)
				&& Math.hypot(e.getX() - player.getX(), e.getZ() - player.getZ()) <= reach + e.getBbWidth() / 2);
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
				// Growing Weight: every enemy that dies under it holds the field a second longer, 3 at most
				State state = state(player);
				if (Talents.has(player, Talent.GRAV_FEAST) && state.gravity > 0 && state.feast < FEAST_MAX) {
					state.gravity += 20;
					state.feast += 20;
				}
			}
		}
		CHAINED.remove(victim);
		if (PRESSED_BY.size() > 512 || CHAINED.size() > 512) {
			long now = event.getEntity().level().getGameTime();
			PRESSED_UNTIL.entrySet().removeIf(e -> e.getValue() < now);
			PRESSED_BY.keySet().retainAll(PRESSED_UNTIL.keySet());
			CHAINED.entrySet().removeIf(e -> e.getValue() < now);
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
			State state = state(player);
			state.lastHurt = player.tickCount;
			// Saving Rift: brought low, the Voidborn is pulled home on the next tick
			if (player.isAlive() && player.getHealth() <= SHELTER_HEALTH && Talents.has(player, Talent.POCKET_SHELTER)
					&& player.level().getGameTime() >= state.shelterReady) {
				state.shelterNow = true;
			}
		}
	}

	private static void shelter(ServerPlayer player, State state) {
		ServerLevel level = player.serverLevel();
		if (!player.isAlive() || Pocket.isPocket(level) || Arena.isArena(level) || BlackHole.holding(player) || state.step != null
				|| RaceState.resource(player) < 20) {
			return;
		}
		if (Pocket.rescue(player)) {
			RaceState.setResource(player, RaceState.resource(player) - 20);
			state.shelterReady = level.getGameTime() + SHELTER_COOLDOWN;
			player.displayClientMessage(Component.translatable("mages_legacy.message.shelter").withStyle(ChatFormatting.LIGHT_PURPLE), true);
			RaceState.sync(player);
		}
	}

	/** Strike from the Void: the first blow right after coming out is heavier. */
	private static void onAmbush(LivingIncomingDamageEvent event) {
		if (event.getSource().getEntity() instanceof ServerPlayer player && event.getSource().getDirectEntity() == player
				&& event.getEntity() != player) {
			State state = STATES.get(player.getUUID());
			if (state != null && state.ambushUntil >= player.level().getGameTime()) {
				state.ambushUntil = 0;
				event.setAmount(event.getAmount() + AMBUSH_DAMAGE);
				Fx.burst(player.serverLevel(), event.getEntity().position().add(0, 1, 0), 0.8f, 6, true);
			}
		}
	}

	/** Without a Trace: for a moment after the walk nothing farther than 4 blocks can set its mind on the walker. */
	private static void onTarget(LivingChangeTargetEvent event) {
		if (event.getNewAboutToBeSetTarget() instanceof ServerPlayer player) {
			State state = STATES.get(player.getUUID());
			if (state != null && state.hiddenUntil >= player.level().getGameTime() && event.getEntity().distanceTo(player) > 4) {
				event.setCanceled(true);
			}
		}
	}

	/** Footing: nothing knocks the Voidborn back while their gravity holds. */
	private static void onKnockBack(LivingKnockBackEvent event) {
		if (event.getEntity() instanceof ServerPlayer player && Talents.has(player, Talent.GRAV_ANCHOR)) {
			State state = STATES.get(player.getUUID());
			if (state != null && state.gravity > 0) {
				event.setCanceled(true);
			}
		}
	}

	private static boolean chained(LivingEntity e) {
		Long until = CHAINED.get(e.getUUID());
		return until != null && until >= e.level().getGameTime();
	}

	/** Chains of Space: what the weight holds can't blink away (endermen, shulkers)... */
	private static void onEnderTeleport(EntityTeleportEvent.EnderEntity event) {
		if (chained(event.getEntityLiving())) {
			event.setCanceled(true);
		}
	}

	/** ...nor jump. */
	private static void onJump(LivingEvent.LivingJumpEvent event) {
		if (!event.getEntity().level().isClientSide && chained(event.getEntity())) {
			Vec3 v = event.getEntity().getDeltaMovement();
			event.getEntity().setDeltaMovement(v.x, Math.min(v.y, 0), v.z);
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
