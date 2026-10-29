package euf.mageslegacy.voidborn;

import euf.mageslegacy.MagesLegacy;
import euf.mageslegacy.fx.Fx;
import euf.mageslegacy.race.Abilities;
import euf.mageslegacy.race.RaceState;
import euf.mageslegacy.race.Talent;
import euf.mageslegacy.race.Talents;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.RelativeMovement;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.common.Tags;
import net.neoforged.neoforge.event.entity.player.AttackEntityEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;

/**
 * The Awakening "Black Hole", centred on the Voidborn: they gather (1.5 s), then pull every enemy within 10 blocks
 * in for 3 s, then it collapses: 12 damage to enemies close by and 4 back to the Voidborn, which never takes them
 * below 1 health. The whole time they can't move, strike or travel, and nothing makes them invulnerable. Allies are
 * never pulled; bosses barely. No block is broken.
 */
public final class BlackHole {
	public static final ResourceKey<DamageType> COLLAPSE = ResourceKey.create(Registries.DAMAGE_TYPE, MagesLegacy.id("void_collapse"));
	private static final int GATHER = 30;
	private static final int GATHER_QUICK = 20;
	private static final int PULL = 60;
	private static final double RADIUS = 14;
	private static final double RADIUS_WIDE = 18;
	/** The collapse strikes enemies this close to the heart. */
	private static final double CRUSH = 6;

	private static final class Hole {
		final Vec3 at;
		final int gather;
		int age;

		Hole(Vec3 at, int gather) {
			this.at = at;
			this.gather = gather;
		}
	}

	private static final Map<UUID, Hole> HOLES = new HashMap<>();

	private BlackHole() {
	}

	public static void register() {
		NeoForge.EVENT_BUS.addListener(BlackHole::onTick);
		NeoForge.EVENT_BUS.addListener(BlackHole::onAttack);
		NeoForge.EVENT_BUS.addListener(BlackHole::onUse);
		NeoForge.EVENT_BUS.addListener(BlackHole::onUseBlock);
		NeoForge.EVENT_BUS.addListener((PlayerEvent.PlayerLoggedOutEvent e) -> HOLES.remove(e.getEntity().getUUID()));
	}

	/** Whether the Voidborn is held by their own singularity now (no moving, striking or travelling). */
	public static boolean holding(ServerPlayer player) {
		return HOLES.containsKey(player.getUUID());
	}

	public static Abilities.Refusal use(ServerPlayer player) {
		if (holding(player)) {
			return new Abilities.Refusal("mages_legacy.refuse.busy");
		}
		int gather = Talents.has(player, Talent.HOLE_QUICK) ? GATHER_QUICK : GATHER;
		Hole hole = new Hole(player.position(), gather);
		HOLES.put(player.getUUID(), hole);
		ServerLevel level = player.serverLevel();
		float radius = (float) radius(player);
		Fx.vortex(level, player, radius, gather + PULL, gather);
		// The caster sees their singularity from outside, and comes back to their own eyes when it collapses
		Fx.thirdPerson(player, gather + PULL + 12);
		// The Voidborn is gone into their own singularity until it collapses
		Fx.hide(level, player, gather + PULL);
		player.addEffect(new net.minecraft.world.effect.MobEffectInstance(net.minecraft.world.effect.MobEffects.INVISIBILITY, gather + PULL, 0, true,
				false, false));
		Fx.muffle(level, player.position(), radius + 6, gather + PULL + 6);
		Fx.shake(player, 0.6f, gather);
		level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.BEACON_POWER_SELECT, SoundSource.PLAYERS, 1f, 0.5f);
		return null;
	}

	public static double radius(ServerPlayer player) {
		return Talents.has(player, Talent.HOLE_WIDE) ? RADIUS_WIDE : RADIUS;
	}

	private static void onTick(PlayerTickEvent.Post event) {
		if (!(event.getEntity() instanceof ServerPlayer player)) {
			return;
		}
		Hole hole = HOLES.get(player.getUUID());
		if (hole == null) {
			return;
		}
		if (!player.isAlive()) {
			HOLES.remove(player.getUUID());
			return;
		}
		hole.age++;
		// The heart of a singularity doesn't move
		if (player.position().distanceToSqr(hole.at) > 0.0025) {
			// The rotation is passed as it is: vanilla sets the server's to what is given and sends the difference, even when relative
			player.connection.teleport(hole.at.x, hole.at.y, hole.at.z, player.getYRot(), player.getXRot(), Set.of(RelativeMovement.X_ROT, RelativeMovement.Y_ROT));
		}
		player.setDeltaMovement(Vec3.ZERO);
		ServerLevel level = player.serverLevel();
		if (hole.age > hole.gather && hole.age <= hole.gather + PULL) {
			pull(player, level, hole, (hole.age - hole.gather) / (float) PULL);
		}
		if (hole.age == hole.gather) {
			level.playSound(null, hole.at.x, hole.at.y, hole.at.z, SoundEvents.WARDEN_SONIC_CHARGE, SoundSource.PLAYERS, 1f, 0.6f);
		}
		if (hole.age >= hole.gather + PULL) {
			collapse(player, level, hole);
			HOLES.remove(player.getUUID());
			player.removeEffect(net.minecraft.world.effect.MobEffects.INVISIBILITY);
		}
	}

	/** Enemies drawn in, harder the closer they come and the longer it lasts; bosses only a little. */
	private static void pull(ServerPlayer player, ServerLevel level, Hole hole, float t) {
		double radius = radius(player);
		Vec3 heart = hole.at.add(0, 1, 0);
		for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class, player.getBoundingBox().inflate(radius),
				e -> e != player && e.isAlive() && Voidborn.isEnemy(player, e) && e.position().distanceTo(hole.at) <= radius)) {
			Vec3 in = heart.subtract(e.position());
			double d = in.length();
			if (d < 1.2) {
				e.setDeltaMovement(e.getDeltaMovement().scale(0.3));
				e.hurtMarked = true;
				continue;
			}
			double strength = (0.08 + 0.22 * t) * (1.4 - d / radius);
			if (e.getType().is(Tags.EntityTypes.BOSSES)) {
				strength *= 0.2;
			}
			e.setDeltaMovement(e.getDeltaMovement().scale(0.6).add(in.normalize().scale(strength)));
			e.hurtMarked = true;
			e.fallDistance = 0;
		}
	}

	private static void collapse(ServerPlayer player, ServerLevel level, Hole hole) {
		float damage = Talents.has(player, Talent.HOLE_DEEP) ? 16 : 12;
		DamageSource source = new DamageSource(level.registryAccess().registryOrThrow(Registries.DAMAGE_TYPE).getHolderOrThrow(COLLAPSE), player, player);
		int crushed = 0;
		for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class, player.getBoundingBox().inflate(CRUSH),
				e -> e != player && e.isAlive() && Voidborn.isEnemy(player, e) && e.position().distanceTo(hole.at) <= CRUSH)) {
			e.invulnerableTime = 0;
			e.hurt(source, damage);
			crushed++;
			Vec3 out = e.position().subtract(hole.at).multiply(1, 0, 1);
			if (out.lengthSqr() > 1.0E-4) {
				e.setDeltaMovement(out.normalize().scale(0.6).add(0, 0.35, 0));
				e.hurtMarked = true;
			}
		}
		// The recoil: 4 to the Voidborn, never below 1 health (outside blows still can kill)
		// A Sated Abyss: fed by three or more, the collapse spares its heart and gives back some Void
		boolean sated = crushed >= 3 && Talents.has(player, Talent.HOLE_SATED);
		if (sated) {
			RaceState.setResource(player, RaceState.resource(player) + 15);
		}
		float self = sated ? 0 : Math.min(4, player.getHealth() - 1);
		if (self > 0) {
			player.invulnerableTime = 0;
			DamageSource recoil = new DamageSource(level.registryAccess().registryOrThrow(Registries.DAMAGE_TYPE).getHolderOrThrow(COLLAPSE));
			player.hurt(recoil, self);
			if (player.getHealth() < 1) {
				player.setHealth(1);
			}
		}
		Fx.burst(level, hole.at.add(0, 1, 0), 7f, 10, true);
		Fx.ring(level, hole.at, 0.5f, 9f, 14);
		Fx.voidScreen(player, 8);
		Fx.shake(player, 2.5f, 14);
		level.playSound(null, hole.at.x, hole.at.y, hole.at.z, SoundEvents.GENERIC_EXPLODE.value(), SoundSource.PLAYERS, 1.2f, 0.5f);
		level.playSound(null, hole.at.x, hole.at.y, hole.at.z, SoundEvents.WARDEN_SONIC_BOOM, SoundSource.PLAYERS, 1f, 0.6f);
	}

	private static void onAttack(AttackEntityEvent event) {
		if (event.getEntity() instanceof ServerPlayer p && holding(p)) {
			event.setCanceled(true);
		}
	}

	private static void onUse(PlayerInteractEvent.RightClickItem event) {
		if (event.getEntity() instanceof ServerPlayer p && holding(p)) {
			event.setCanceled(true);
		}
	}

	private static void onUseBlock(PlayerInteractEvent.RightClickBlock event) {
		if (event.getEntity() instanceof ServerPlayer p && holding(p)) {
			event.setCanceled(true);
		}
	}
}
