package euf.mageslegacy.shepherd;

import euf.mageslegacy.mixin.MobEntityAccessor;
import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.WeakHashMap;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.ai.goal.ActiveTargetGoal;
import net.minecraft.entity.ai.goal.Goal;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.entity.mob.Monster;
import net.minecraft.entity.passive.AxolotlEntity;
import net.minecraft.entity.passive.BeeEntity;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import org.jetbrains.annotations.Nullable;

/**
 * A Shepherd's creatures, echoes and bonded beasts alike: who owns what, the goals that make them follow and defend
 * their Shepherd, and the marks that tell them whom to attack.
 */
public final class Creatures {
	/** Bonded beasts carry this tag plus "mages_legacy_owner:<uuid>", so the bond survives saves and restarts. */
	public static final String BOND_TAG = "mages_legacy_bond";
	public static final String OWNER_TAG = "mages_legacy_owner:";

	/** Creature uuid -> its Shepherd, for loaded bonded beasts (echoes live in {@link Echoes}). */
	private static final Map<UUID, UUID> BEAST_OWNERS = new HashMap<>();
	/** Mobs that already got their new goals (goals aren't saved, so a reloaded beast is taught again). */
	private static final Set<MobEntity> TAUGHT = Collections.newSetFromMap(new WeakHashMap<>());
	/** Shepherd -> the monster they marked (Hunter's Mark) and until when. */
	private static final Map<UUID, Mark> MARKS = new HashMap<>();

	private record Mark(UUID target, long until) {}

	private Creatures() {
	}

	public static void reset() {
		BEAST_OWNERS.clear();
		MARKS.clear();
	}

	@Nullable
	public static UUID ownerOf(Entity entity) {
		UUID echo = Echoes.ownerOf(entity);
		return echo != null ? echo : BEAST_OWNERS.get(entity.getUuid());
	}

	public static boolean isCreatureOf(Entity entity, UUID owner) {
		return owner.equals(ownerOf(entity));
	}

	public static void registerBeast(Entity entity, UUID owner) {
		BEAST_OWNERS.put(entity.getUuid(), owner);
	}

	public static void forgetBeast(UUID entity) {
		BEAST_OWNERS.remove(entity);
	}

	/** Reads the owner from a loaded bonded beast's tags. */
	@Nullable
	public static UUID taggedOwner(Entity entity) {
		for (String tag : entity.getCommandTags()) {
			if (tag.startsWith(OWNER_TAG)) {
				try {
					return UUID.fromString(tag.substring(OWNER_TAG.length()));
				} catch (IllegalArgumentException ignored) {
					return null;
				}
			}
		}
		return null;
	}

	/** Every loaded creature of this Shepherd in its world within {@code radius}. */
	public static List<LivingEntity> around(ServerPlayerEntity owner, double radius) {
		List<LivingEntity> list = new ArrayList<>();
		for (LivingEntity e : owner.getServerWorld().getEntitiesByClass(LivingEntity.class, owner.getBoundingBox().expand(radius),
				e -> e.isAlive() && isCreatureOf(e, owner.getUuid()))) {
			list.add(e);
		}
		return list;
	}

	public static void mark(ServerPlayerEntity owner, LivingEntity target, long until) {
		MARKS.put(owner.getUuid(), new Mark(target.getUuid(), until));
	}

	@Nullable
	static LivingEntity marked(ServerPlayerEntity owner) {
		Mark mark = MARKS.get(owner.getUuid());
		if (mark == null || mark.until() < owner.getServer().getTicks()) {
			return null;
		}
		return owner.getServerWorld().getEntity(mark.target()) instanceof LivingEntity living && living.isAlive() ? living : null;
	}

	@Nullable
	static ServerPlayerEntity owner(MobEntity mob) {
		UUID id = ownerOf(mob);
		MinecraftServer server = mob.getServer();
		if (id == null || server == null) {
			return null;
		}
		ServerPlayerEntity player = server.getPlayerManager().getPlayer(id);
		return player != null && player.getWorld() == mob.getWorld() ? player : null;
	}

	/**
	 * Turns a mob into a Shepherd's creature: no more hunting players; instead it follows its Shepherd, defends it,
	 * attacks what it marks and, if it can fight, the monsters near it. Bees and axolotls don't pick fights on land.
	 */
	public static void teach(MobEntity mob, boolean fights) {
		if (!TAUGHT.add(mob)) {
			return;
		}
		MobEntityAccessor ai = (MobEntityAccessor) mob;
		if (fights) {
			ai.mages_legacy$targets().getGoals().clear();
			ai.mages_legacy$targets().add(1, new DefendOwnerGoal(mob));
			ai.mages_legacy$targets().add(3, new ActiveTargetGoal<>(mob, MobEntity.class, 10, true, false, e ->
					e instanceof Monster && ownerOf(e) == null && nearOwner(mob, e, 16)));
		}
		ai.mages_legacy$goals().add(fights ? 6 : 2, new FollowOwnerGoal(mob));
	}

	private static boolean nearOwner(MobEntity mob, LivingEntity target, double radius) {
		ServerPlayerEntity owner = owner(mob);
		return owner != null && owner.squaredDistanceTo(target) < radius * radius;
	}

	/** A spot near the owner with room to stand, for creatures that fell too far behind. */
	static Vec3d near(ServerPlayerEntity owner) {
		ServerWorld world = owner.getServerWorld();
		for (int i = 0; i < 10; i++) {
			BlockPos pos = owner.getBlockPos().add(owner.getRandom().nextBetween(-2, 2), 0, owner.getRandom().nextBetween(-2, 2));
			for (int dy = 1; dy >= -2; dy--) {
				BlockPos feet = pos.up(dy);
				if (world.getBlockState(feet).getCollisionShape(world, feet).isEmpty()
						&& world.getBlockState(feet.up()).getCollisionShape(world, feet.up()).isEmpty()
						&& !world.getBlockState(feet.down()).getCollisionShape(world, feet.down()).isEmpty()) {
					return Vec3d.ofBottomCenter(feet);
				}
			}
		}
		return owner.getPos();
	}

	/** Walks after the Shepherd; jumps to its side when left more than 24 blocks behind. */
	static class FollowOwnerGoal extends Goal {
		private final MobEntity mob;
		private ServerPlayerEntity owner;
		private int recalc;

		FollowOwnerGoal(MobEntity mob) {
			this.mob = mob;
			setControls(EnumSet.of(Control.MOVE, Control.LOOK));
		}

		@Override
		public boolean canStart() {
			owner = owner(mob);
			if (owner == null || owner.isSpectator() || mob.getTarget() != null) {
				return false;
			}
			// An axolotl dries out on land: it only follows a Shepherd who swims
			if (mob instanceof AxolotlEntity && !owner.isTouchingWater()) {
				return false;
			}
			return mob.squaredDistanceTo(owner) > 64;
		}

		@Override
		public boolean shouldContinue() {
			return owner != null && owner.isAlive() && mob.getTarget() == null && mob.squaredDistanceTo(owner) > 9;
		}

		@Override
		public void start() {
			recalc = 0;
		}

		@Override
		public void stop() {
			mob.getNavigation().stop();
		}

		@Override
		public void tick() {
			mob.getLookControl().lookAt(owner, 10, mob.getMaxLookPitchChange());
			if (--recalc > 0) {
				return;
			}
			recalc = 10;
			if (mob.squaredDistanceTo(owner) > 24 * 24) {
				Vec3d spot = near(owner);
				mob.refreshPositionAndAngles(spot.x, spot.y, spot.z, mob.getYaw(), mob.getPitch());
				mob.getNavigation().stop();
			} else {
				mob.getNavigation().startMovingTo(owner, mob instanceof BeeEntity ? 1.0 : 1.2);
			}
		}
	}

	/** Attacks whoever hurts the Shepherd, then what the Shepherd attacks or marked. */
	static class DefendOwnerGoal extends Goal {
		private final MobEntity mob;
		private LivingEntity target;

		DefendOwnerGoal(MobEntity mob) {
			this.mob = mob;
			setControls(EnumSet.of(Control.TARGET));
		}

		@Override
		public boolean canStart() {
			ServerPlayerEntity owner = owner(mob);
			if (owner == null) {
				return false;
			}
			LivingEntity candidate = marked(owner);
			if (candidate == null) {
				candidate = owner.getAttacker();
			}
			if (candidate == null && owner.age - owner.getLastAttackTime() < 100) {
				candidate = owner.getAttacking();
			}
			if (candidate == null || candidate == owner || candidate == mob || !candidate.isAlive()
					|| isCreatureOf(candidate, owner.getUuid()) || candidate == mob.getTarget()) {
				return false;
			}
			target = candidate;
			return true;
		}

		@Override
		public void start() {
			mob.setTarget(target);
		}

		@Override
		public boolean shouldContinue() {
			return false;
		}
	}
}
