package euf.mageslegacy.mycelid;

import euf.mageslegacy.fx.Fx;
import euf.mageslegacy.race.Abilities;
import euf.mageslegacy.race.Ability;
import euf.mageslegacy.race.Awakenings;
import euf.mageslegacy.race.Race;
import euf.mageslegacy.race.RaceState;
import euf.mageslegacy.race.Talent;
import euf.mageslegacy.race.Talents;
import euf.mageslegacy.voidborn.Voidborn;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import net.minecraft.ChatFormatting;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.RelativeMovement;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.common.Tags;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;
import net.neoforged.neoforge.event.entity.player.AttackEntityEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;
import org.joml.Vector3f;

/**
 * The Mycelid's Awakenings and what they cost the body:
 * <ul>
 * <li>Great Bloom: the Mycelid grows into a huge glowing mushroom, rooted and open to blows, healing allies within 50
 * blocks; pressed again it ends early.</li>
 * <li>Spore Storm: every stored spore let go at once, a whirl around the Mycelid that weakens and confuses enemies and
 * quickens allies.</li>
 * <li>Mycelial Rebirth: a reserve body grown at one of one's nodes; a killing blow is undone there instead.</li>
 * <li>Exhaustion after each: Spores gather half as fast and the basic abilities cost a quarter more; two Awakenings
 * close together exhaust longer. It never blocks an Awakening and never kills.</li>
 * </ul>
 */
public final class Awaken {
	private static final int BLOOM_TICKS = 15 * 20;
	private static final int BLOOM_LONG = 22 * 20;
	private static final double BLOOM_RADIUS = 50;
	private static final int STORM_TICKS = 10 * 20;
	private static final int STORM_LONG = 14 * 20;
	private static final double STORM_RADIUS = 18;
	private static final double STORM_WIDE = 24;
	public static final int STORM_MIN = 50;
	/** Exhaustion: seconds after each Awakening; a second one within a minute exhausts half again as long. */
	private static final int EXHAUST_BLOOM = 10 * 20;
	private static final int EXHAUST_STORM = 15 * 20;
	private static final int EXHAUST_REBIRTH = 30 * 20;
	private static final int CLOSE_TOGETHER = 60 * 20;
	public static final int REBIRTH_COOLDOWN = 20 * 60 * 20;
	private static final DustParticleOptions HEAL_DUST = new DustParticleOptions(new Vector3f(0.55f, 1f, 0.75f), 1.3f);

	private record Bloom(Vec3 at, long until) {
	}

	/** A storm: until when, how wide, and the different enemies it has caught (Breath of the Colony at 6). */
	private record Storm(long until, double radius, Set<UUID> caught) {
	}

	private static final Map<UUID, Bloom> BLOOMS = new HashMap<>();
	private static final Map<UUID, Storm> STORMS = new HashMap<>();
	private static final Map<UUID, Long> EXHAUSTED = new HashMap<>();
	private static final Map<UUID, Long> LAST_AWAKENING = new HashMap<>();
	/** Since when the Bloom and the storm have both been alive. */
	private static final Map<UUID, Long> BOTH = new HashMap<>();

	private Awaken() {
	}

	public static void register() {
		NeoForge.EVENT_BUS.addListener(Awaken::onTick);
		NeoForge.EVENT_BUS.addListener(Awaken::onIncomingDamage);
		NeoForge.EVENT_BUS.addListener(Awaken::onAttack);
		NeoForge.EVENT_BUS.addListener(Awaken::onDeath);
		// Before the cooldowns are saved on the player (Voidborn's own logout listener)
		NeoForge.EVENT_BUS.addListener(EventPriority.HIGH, Awaken::onLogout);
		NeoForge.EVENT_BUS.addListener(Awaken::onChangeWorld);
	}

	/**
	 * Leaving the server (or the server stopping) ends the Awakenings in progress the way their own end does: the
	 * Bloom's cooldown starts, both exhaust.
	 */
	private static void onLogout(PlayerEvent.PlayerLoggedOutEvent event) {
		if (event.getEntity() instanceof ServerPlayer player) {
			leave(player);
		}
	}

	/** What leaving does to the Awakenings in progress (public for the harness, which can't log out). */
	public static void leave(ServerPlayer player) {
		endBloom(player);
		endStorm(player);
	}

	/** A rooted mushroom carried to another world withers there. */
	private static void onChangeWorld(PlayerEvent.PlayerChangedDimensionEvent event) {
		if (event.getEntity() instanceof ServerPlayer player) {
			endBloom(player);
		}
	}

	// ---- Exhaustion -------------------------------------------------------------------------------------------

	public static boolean exhausted(Player player) {
		Long until = EXHAUSTED.get(player.getUUID());
		return until != null && until > player.level().getGameTime();
	}

	private static void exhaust(ServerPlayer player, int ticks) {
		long now = player.level().getGameTime();
		Long last = LAST_AWAKENING.get(player.getUUID());
		if (last != null && now - last < CLOSE_TOGETHER) {
			ticks = ticks * 3 / 2;
		}
		LAST_AWAKENING.put(player.getUUID(), now);
		long from = Math.max(now, EXHAUSTED.getOrDefault(player.getUUID(), now));
		EXHAUSTED.put(player.getUUID(), from + ticks);
		player.displayClientMessage(Component.translatable("mages_legacy.message.exhausted", ticks / 20).withStyle(ChatFormatting.DARK_GREEN), true);
	}

	// ---- Great Bloom ------------------------------------------------------------------------------------------

	public static boolean blooming(Player player) {
		return BLOOMS.containsKey(player.getUUID());
	}

	/** The Awakening's key: grow into the Bloom, or wither back out of it early. */
	public static Abilities.Refusal bloom(ServerPlayer player) {
		if (blooming(player)) {
			endBloom(player);
			return null;
		}
		ServerLevel level = player.serverLevel();
		int ticks = Talents.has(player, Talent.BLOOM_LONG) ? BLOOM_LONG : BLOOM_TICKS;
		BLOOMS.put(player.getUUID(), new Bloom(player.position(), level.getGameTime() + ticks));
		// Kept on the player from the start (the Bloom and its cooldown), so even a crash of the server mid-Bloom loses
		// nothing; the end sets the cooldown proper
		int slot = Awakenings.slotOf(Talents.owned(player), Ability.GREAT_BLOOM);
		if (slot >= 0) {
			Voidborn.state(player).startCooldown(slot, ticks + Abilities.cooldown(player, Ability.GREAT_BLOOM));
		}
		Fx.bloom(level, player, ticks, (float) bloomRadius(player, player.position()));
		Fx.hide(level, player, ticks);
		Fx.thirdPerson(player, ticks + 10);
		level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.ROOTED_DIRT_PLACE, SoundSource.PLAYERS, 1.5f, 0.5f);
		level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.AMETHYST_BLOCK_RESONATE, SoundSource.PLAYERS, 1.2f, 0.5f);
		MyceliumNetwork.dissolve(level, player.position());
		return null;
	}

	/** Whether pressing the Bloom's key now ends it (free and without waiting). */
	public static boolean bloomEndsNow(ServerPlayer player) {
		return blooming(player);
	}

	/** Every way the Bloom ends comes here (once: a second call finds nothing to end). */
	private static void endBloom(ServerPlayer player) {
		if (BLOOMS.remove(player.getUUID()) == null) {
			return;
		}
		ServerLevel level = player.serverLevel();
		Fx.bloom(level, player, 0, 0);
		Fx.hide(level, player, 0);
		Fx.thirdPerson(player, 0);
		level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.FUNGUS_BREAK, SoundSource.PLAYERS, 1.2f, 0.6f);
		MyceliumNetwork.dissolve(level, player.position());
		// The Bloom's own cooldown starts when it is over
		int slot = Awakenings.slotOf(Talents.owned(player), Ability.GREAT_BLOOM);
		if (slot >= 0) {
			Voidborn.state(player).startCooldown(slot, Abilities.cooldown(player, Ability.GREAT_BLOOM));
		}
		exhaust(player, EXHAUST_BLOOM);
		RaceState.sync(player);
	}

	private static double bloomRadius(ServerPlayer player, Vec3 at) {
		// Roots of the Colony: beside one of the Mycelid's nodes the healing reaches half as far again
		if (Talents.has(player, Talent.BLOOM_ROOTS)) {
			for (MyceliumNetwork.Node n : MyceliumNetwork.get(player.server).nodesOf(player.getUUID())) {
				if (n.dim == player.level().dimension() && Vec3.atCenterOf(n.pos).distanceTo(at) <= 32) {
					return BLOOM_RADIUS * 1.5;
				}
			}
		}
		return BLOOM_RADIUS;
	}

	private static void tickBloom(ServerPlayer player, Bloom bloom) {
		ServerLevel level = player.serverLevel();
		if (!player.isAlive() || level.getGameTime() >= bloom.until()) {
			endBloom(player);
			return;
		}
		// Rooted: the body stays where it grew
		if (player.position().distanceToSqr(bloom.at()) > 0.0025) {
			player.connection.teleport(bloom.at().x, bloom.at().y, bloom.at().z, player.getYRot(), player.getXRot(),
					Set.of(RelativeMovement.X_ROT, RelativeMovement.Y_ROT));
		}
		player.setDeltaMovement(Vec3.ZERO);
		if (player.tickCount % 20 != 0) {
			return;
		}
		double radius = bloomRadius(player, bloom.at());
		// A point of health a second (Rich Spores: two). Healed directly: Regeneration renewed every second heals at the
		// same pace whatever its level, so Regeneration II gave nothing more
		float heal = Talents.has(player, Talent.BLOOM_HEAL) ? 2 : 1;
		for (LivingEntity ally : level.getEntitiesOfClass(LivingEntity.class, player.getBoundingBox().inflate(radius), e -> e != player && e.isAlive()
				&& e.distanceTo(player) <= radius && Allies.ally(player, e))) {
			// The Great Bloom (the advancement): healing someone who was hurt
			if (ally.getHealth() < ally.getMaxHealth()) {
				euf.mageslegacy.race.Deeds.grant(player, "myc_bloom");
			}
			ally.heal(heal);
			level.sendParticles(HEAL_DUST, ally.getX(), ally.getY() + ally.getBbHeight() + 0.3, ally.getZ(), 4, 0.3, 0.2, 0.3, 0);
		}
	}

	// ---- Spore Storm ------------------------------------------------------------------------------------------

	public static Abilities.Refusal storm(ServerPlayer player) {
		float spores = RaceState.resource(player);
		if (spores < STORM_MIN) {
			return new Abilities.Refusal("mages_legacy.refuse.storm_spores");
		}
		ServerLevel level = player.serverLevel();
		int ticks = Talents.has(player, Talent.STORM_LONG) ? STORM_LONG : STORM_TICKS;
		double radius = Talents.has(player, Talent.STORM_WIDE) ? STORM_WIDE : STORM_RADIUS;
		STORMS.put(player.getUUID(), new Storm(level.getGameTime() + ticks, radius, new java.util.HashSet<>()));
		Fx.storm(level, player, (float) radius, ticks);
		// Every stored spore goes (a Spore Reserve keeps a quarter)
		RaceState.setResource(player, Talents.has(player, Talent.STORM_KEEP) ? spores / 4 : 0);
		level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.ELYTRA_FLYING, SoundSource.PLAYERS, 1.5f, 0.6f);
		level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.PUFFER_FISH_BLOW_UP, SoundSource.PLAYERS, 1.5f, 0.4f);
		Fx.shake(player, 0.8f, 12);
		return null;
	}

	public static boolean storming(Player player) {
		return STORMS.containsKey(player.getUUID());
	}

	/** Every way the storm ends comes here (once). Its cooldown started with it. */
	private static void endStorm(ServerPlayer player) {
		if (STORMS.remove(player.getUUID()) != null) {
			exhaust(player, EXHAUST_STORM);
		}
	}

	private static void tickStorm(ServerPlayer player, Storm storm) {
		ServerLevel level = player.serverLevel();
		long now = level.getGameTime();
		if (!player.isAlive() || now >= storm.until()) {
			endStorm(player);
			return;
		}
		double r = storm.radius();
		// The whirl itself is drawn by the clients (Fx.storm)
		if (player.tickCount % 10 != 0) {
			return;
		}
		boolean blind = Talents.has(player, Talent.STORM_BLIND) && player.tickCount % 60 == 0;
		boolean guard = Talents.has(player, Talent.STORM_ALLY);
		for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class, player.getBoundingBox().inflate(r, 4, r),
				e -> e != player && e.isAlive() && e.distanceTo(player) <= r)) {
			if (Voidborn.isEnemy(player, e)) {
				boolean boss = e.getType().is(Tags.EntityTypes.BOSSES);
				Mycelid.infect(player, e, level, 3 * 20);
				if (storm.caught().add(e.getUUID()) && storm.caught().size() == 6) {
					euf.mageslegacy.race.Deeds.grant(player, "myc_breath");
				}
				e.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, 30, boss ? 0 : 1, false, true), player);
				if (!boss) {
					e.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 30, 1, false, true), player);
					if (e instanceof Mob mob && e.getRandom().nextFloat() < 0.5f) {
						mob.setTarget(null);
						mob.getNavigation().stop();
					}
					if (blind) {
						e.addEffect(new MobEffectInstance(MobEffects.BLINDNESS, 25, 0, false, true), player);
					}
				}
			} else if (Allies.ally(player, e)) {
				e.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SPEED, 30, 0, true, true));
				e.addEffect(new MobEffectInstance(MobEffects.DIG_SPEED, 30, 0, true, true));
				e.removeEffect(MobEffects.MOVEMENT_SLOWDOWN);
				if (guard) {
					e.addEffect(new MobEffectInstance(MobEffects.DAMAGE_RESISTANCE, 30, 0, true, true));
				}
			}
		}
		// The storm's own heart is quickened too
		player.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SPEED, 30, 0, true, true));
	}

	// ---- Mycelial Rebirth -------------------------------------------------------------------------------------

	/** The Awakening's key: grow a reserve body at the own node one stands by (or say how the body is). */
	public static Abilities.Refusal rebirth(ServerPlayer player) {
		return MyceliumNetwork.growBody(player);
	}

	/** A killing blow on a Mycelid with a grown reserve body: the body at the node wakes instead. */
	private static void onDeath(LivingDeathEvent event) {
		if (!(event.getEntity() instanceof ServerPlayer player) || !RaceState.is(player, Race.MYCELID) || event.isCanceled()) {
			return;
		}
		if (Awakenings.slotOf(Talents.owned(player), Ability.MYCELIAL_REBIRTH) < 0) {
			endBloom(player);
			return;
		}
		MyceliumNetwork.Waking waking = MyceliumNetwork.takeBody(player);
		if (waking == null) {
			endBloom(player);
			return;
		}
		Vec3 at = waking.at();
		event.setCanceled(true);
		ServerLevel from = player.serverLevel();
		MyceliumNetwork.dissolve(from, player.position());
		Fx.wake(from, player.position(), true);
		// A Bloom or a storm cut short by the blow ends as it would anyway: cooldown and exhaustion
		endBloom(player);
		endStorm(player);
		ServerLevel to = waking.level();
		player.clearFire();
		player.removeEffect(MobEffects.WITHER);
		player.removeEffect(MobEffects.POISON);
		player.setHealth(player.getMaxHealth() * (Talents.has(player, Talent.REBIRTH_HEALTH) ? 0.5f : 0.3f));
		player.teleportTo(to, at.x, at.y, at.z, player.getYRot(), player.getXRot());
		player.fallDistance = 0;
		player.invulnerableTime = 40;
		MyceliumNetwork.dissolve(to, at);
		Fx.wake(to, at, false);
		to.playSound(null, at.x, at.y, at.z, SoundEvents.TOTEM_USE, SoundSource.PLAYERS, 0.8f, 0.6f);
		to.playSound(null, at.x, at.y, at.z, SoundEvents.FUNGUS_PLACE, SoundSource.PLAYERS, 1.2f, 0.6f);
		exhaust(player, Talents.has(player, Talent.REBIRTH_SOFT) ? EXHAUST_REBIRTH / 2 : EXHAUST_REBIRTH);
		player.displayClientMessage(Component.translatable("mages_legacy.message.reborn").withStyle(ChatFormatting.GREEN), false);
		euf.mageslegacy.race.Deeds.grant(player, "myc_rebirth");
		RaceState.sync(player);
	}

	// ---- Every tick -------------------------------------------------------------------------------------------

	private static void onTick(PlayerTickEvent.Post event) {
		if (!(event.getEntity() instanceof ServerPlayer player)) {
			return;
		}
		Bloom bloom = BLOOMS.get(player.getUUID());
		if (bloom != null) {
			tickBloom(player, bloom);
		}
		Storm storm = STORMS.get(player.getUUID());
		if (storm != null) {
			tickStorm(player, storm);
		}
		// A Perfect Symbiosis: the Bloom and the storm both alive for a whole second
		if (blooming(player) && storming(player)) {
			long now = player.level().getGameTime();
			long since = BOTH.computeIfAbsent(player.getUUID(), id -> now);
			if (now - since >= 20) {
				euf.mageslegacy.race.Deeds.grant(player, "myc_perfect");
			}
		} else {
			BOTH.remove(player.getUUID());
		}
		if (player.tickCount % 200 == 0) {
			long now = player.level().getGameTime();
			EXHAUSTED.values().removeIf(t -> t < now);
		}
	}

	/** Woody Bark: the mushroom form takes a quarter less. */
	private static void onIncomingDamage(LivingIncomingDamageEvent event) {
		if (event.getEntity() instanceof ServerPlayer player && blooming(player) && Talents.has(player, Talent.BLOOM_BARK)) {
			event.setAmount(event.getAmount() * 0.75f);
		}
	}

	/** A rooted mushroom can't strike. */
	private static void onAttack(AttackEntityEvent event) {
		if (blooming(event.getEntity())) {
			event.setCanceled(true);
		}
	}

	/** Forgets a player's Awakenings in progress (a new race). */
	public static void reset(ServerPlayer player) {
		endBloom(player);
		STORMS.remove(player.getUUID());
		EXHAUSTED.remove(player.getUUID());
	}
}
