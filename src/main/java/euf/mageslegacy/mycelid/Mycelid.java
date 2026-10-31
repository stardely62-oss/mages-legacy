package euf.mageslegacy.mycelid;

import euf.mageslegacy.MagesLegacy;
import euf.mageslegacy.fx.Fx;
import euf.mageslegacy.race.Abilities;
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
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.common.Tags;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.entity.living.LivingEntityUseItemEvent;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;
import net.neoforged.neoforge.event.entity.living.MobEffectEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import org.joml.Vector3f;

/**
 * The Mycelid: Spores (the race's resource), sunlight and fire, the fungal diet, Neurospores (a cloud that dulls the
 * nerves of enemies) and Neurosymbiosis (spores that quicken allies). The Mycelium Network lives in
 * {@link MyceliumNetwork}.
 */
public final class Mycelid {
	/** Trial counters, in {@link RaceState#PROGRESS}. */
	public static final String JOURNEYS = "net_journeys";
	public static final String INFECTED_KILLS = "infected_kills";
	public static final String SHARED = "symbiosis_shared";
	public static final String NODES = "net_nodes";
	/** Spores per tick: one a second. */
	private static final float REGEN = 1f / 20;
	private static final ResourceLocation SPEED = MagesLegacy.id("symbiosis_speed");
	private static final ResourceLocation ATTACK = MagesLegacy.id("symbiosis_attack");
	private static final DustParticleOptions SPORE_DUST = new DustParticleOptions(new Vector3f(0.55f, 0.85f, 0.45f), 1.2f);

	/** A cloud of neurospores: where (or whom it follows), how wide, until when. */
	private static final class Cloud {
		final UUID owner;
		final ResourceKey<Level> level;
		Vec3 at;
		final double radius;
		final long until;
		final boolean follow;
		final int linger;
		/** A cloud cast with R (not a trail's or a node's): the enemies it has infected itself, for the deeds. */
		Combo cast;

		Cloud(UUID owner, ResourceKey<Level> level, Vec3 at, double radius, long until, boolean follow, int linger) {
			this.owner = owner;
			this.level = level;
			this.at = at;
			this.radius = radius;
			this.until = until;
			this.follow = follow;
			this.linger = linger;
		}
	}

	/** An infected creature: by whom, until when, whether it has passed the spores on yet. */
	private static final class Infection {
		final UUID owner;
		long until;
		boolean spread;

		Infection(UUID owner, long until) {
			this.owner = owner;
			this.until = until;
		}
	}

	/** One sharing Neurosymbiosis: with whom, until when, whether its first blow was softened yet. */
	private static final class Bond {
		final UUID caster;
		final long until;
		boolean reflexUsed;

		Bond(UUID caster, long until) {
			this.caster = caster;
			this.until = until;
		}
	}

	/**
	 * One casting of Neurospores as the deeds see it: the different enemies its cloud has infected, when the second
	 * was (Neurosymbiosis within 10 s of it makes One Organism, once), and which counts it has given already.
	 */
	private static final class Combo {
		final Set<UUID> infected = new HashSet<>();
		long pairAt = -1;
		boolean organism;
		boolean four;
		boolean five;
	}

	private static final int COMBO_TICKS = 10 * 20;
	/** Each Mycelid's latest casting of Neurospores. */
	private static final Map<UUID, Combo> COMBOS = new HashMap<>();
	private static final List<Cloud> CLOUDS = new ArrayList<>();
	private static final Map<UUID, Infection> INFECTED = new HashMap<>();
	private static final Map<UUID, Bond> BONDS = new HashMap<>();
	/** Symbiosis casters whose aura still gathers newcomers (Living Aura): caster → until. */
	private static final Map<UUID, Long> AURAS = new HashMap<>();

	private Mycelid() {
	}

	public static void register() {
		NeoForge.EVENT_BUS.addListener(Mycelid::onTick);
		NeoForge.EVENT_BUS.addListener(Mycelid::onServerTick);
		NeoForge.EVENT_BUS.addListener(Mycelid::onIncomingDamage);
		NeoForge.EVENT_BUS.addListener(Mycelid::onEat);
		NeoForge.EVENT_BUS.addListener(Mycelid::onDeath);
		NeoForge.EVENT_BUS.addListener(Mycelid::onEffect);
	}

	// ---- Spores, sun and fire ---------------------------------------------------------------------------------

	/** In the open under the day sun of the overworld: what a fungus likes least. */
	public static boolean inSunlight(ServerPlayer player) {
		Level level = player.level();
		return level.dimension() == Level.OVERWORLD && level.isDay() && !level.isRaining() && level.canSeeSky(player.blockPosition().above());
	}

	private static void onTick(PlayerTickEvent.Post event) {
		if (!(event.getEntity() instanceof ServerPlayer player) || !RaceState.is(player, Race.MYCELID)) {
			return;
		}
		// Spores gather by themselves: half as fast under the open sun (at the full pace with a Dense Shell); faster at
		// night or underground with Night Growth
		float regen = REGEN;
		boolean sun = inSunlight(player);
		if (sun) {
			regen = Talents.has(player, Talent.SYM_ENV) ? regen : regen / 2;
		} else if (Talents.has(player, Talent.SYM_NIGHT) && (!player.level().isDay() || !player.level().canSeeSky(player.blockPosition().above()))) {
			regen *= 1.5f;
		}
		// Exhausted after an Awakening: half as fast
		if (Awaken.exhausted(player)) {
			regen /= 2;
		}
		RaceState.setResource(player, RaceState.resource(player) + regen);
	}

	/** Fire takes a fungus apart: half again as much from flame and lava (a fifth with a Dense Shell). */
	private static void onIncomingDamage(LivingIncomingDamageEvent event) {
		LivingEntity target = event.getEntity();
		if (target instanceof ServerPlayer player && RaceState.is(player, Race.MYCELID) && event.getSource().is(DamageTypeTags.IS_FIRE)) {
			event.setAmount(event.getAmount() * (Talents.has(player, Talent.SYM_ENV) ? 1.2f : 1.5f));
		}
		// Protective Reflex: the first blow on one sharing Neurosymbiosis is softened
		Bond bond = BONDS.get(target.getUUID());
		if (bond != null && !bond.reflexUsed && target.level().getServer() != null) {
			ServerPlayer caster = target.level().getServer().getPlayerList().getPlayer(bond.caster);
			if (caster != null && Talents.has(caster, Talent.SYM_REFLEX)) {
				bond.reflexUsed = true;
				event.setAmount(event.getAmount() * 0.7f);
			}
		}
		// Symbiotic Spores: the blows of those sharing it infect
		if (event.getSource().getEntity() instanceof LivingEntity attacker && event.getSource().getDirectEntity() == attacker) {
			Bond by = BONDS.get(attacker.getUUID());
			if (by != null && target.level().getServer() != null) {
				ServerPlayer caster = target.level().getServer().getPlayerList().getPlayer(by.caster);
				if (caster != null && Talents.has(caster, Talent.SYM_SPORES) && Voidborn.isEnemy(caster, target)) {
					infect(caster, target, (ServerLevel) target.level(), 4 * 20);
				}
			}
		}
	}

	/** The fungal diet (three talents): more from fungal food, then Spores, then food for the network. */
	private static void onEat(LivingEntityUseItemEvent.Finish event) {
		if (!(event.getEntity() instanceof ServerPlayer player) || !RaceState.is(player, Race.MYCELID)
				|| !event.getItem().is(MyceliumNetwork.FUNGAL_FOOD)) {
			return;
		}
		if (Talents.has(player, Talent.DIET_1)) {
			player.getFoodData().eat(3, 0.6f);
		}
		if (Talents.has(player, Talent.DIET_2)) {
			RaceState.setResource(player, RaceState.resource(player) + 15);
		}
		if (Talents.has(player, Talent.DIET_3)) {
			MyceliumNetwork.get(player.server).addNutrients(player.getUUID(), 8);
		}
		player.serverLevel().sendParticles(ParticleTypes.HAPPY_VILLAGER, player.getX(), player.getY() + 1.2, player.getZ(), 8, 0.3, 0.3, 0.3, 0);
		RaceState.sync(player);
	}

	// ---- Neurospores ------------------------------------------------------------------------------------------

	public static double sporeRadius(ServerPlayer player) {
		return Talents.has(player, Talent.SPORE_WIDE_2) ? 9 : Talents.has(player, Talent.SPORE_WIDE) ? 7 : 5;
	}

	private static int sporeTicks(ServerPlayer player) {
		return Talents.has(player, Talent.SPORE_LONG) ? 9 * 20 : 6 * 20;
	}

	/** R: a cloud of neurospores where the Mycelid stands (riding with them with a Spore Trail). */
	public static Abilities.Refusal neurospores(ServerPlayer player) {
		ServerLevel level = player.serverLevel();
		int linger = Talents.has(player, Talent.SPORE_LINGER) ? 8 * 20 : 3 * 20;
		boolean trail = Talents.has(player, Talent.SPORE_TRAIL);
		Cloud cloud = new Cloud(player.getUUID(), level.dimension(), player.position(), sporeRadius(player), level.getGameTime() + sporeTicks(player),
				trail, linger);
		cloud.cast = new Combo();
		COMBOS.put(player.getUUID(), cloud.cast);
		CLOUDS.add(cloud);
		Fx.sporeCloud(level, player.position(), trail ? player : null, (float) sporeRadius(player), sporeTicks(player));
		level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.SPORE_BLOSSOM_PLACE, SoundSource.PLAYERS, 1.2f, 0.6f);
		level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.PUFFER_FISH_BLOW_UP, SoundSource.PLAYERS, 0.8f, 0.5f);
		burst(level, player.position(), sporeRadius(player));
		return null;
	}

	/** A cast cloud has infected one more enemy: A Quiet Epidemic at 4, a step of the mastery trial at 5. */
	private static void castInfected(ServerPlayer owner, Combo combo, long now) {
		int n = combo.infected.size();
		if (n >= 2 && combo.pairAt < 0) {
			combo.pairAt = now;
		}
		if (n >= 4 && !combo.four) {
			combo.four = true;
			Deeds.grant(owner, "myc_epidemic");
		}
		if (n >= 5 && !combo.five) {
			combo.five = true;
			Mastery.add(owner, Mastery.Goal.SPORE_MIND);
		}
	}

	/**
	 * One Organism: Neurosymbiosis within 10 s after a casting of Neurospores infected two enemies, one of them still
	 * infected by this Mycelid within 16 blocks. A casting counts once.
	 */
	private static void organism(ServerPlayer player, ServerLevel level) {
		Combo combo = COMBOS.get(player.getUUID());
		long now = level.getGameTime();
		if (combo == null || combo.organism || combo.pairAt < 0 || now - combo.pairAt > COMBO_TICKS) {
			return;
		}
		for (UUID id : combo.infected) {
			Infection inf = INFECTED.get(id);
			if (inf != null && inf.owner.equals(player.getUUID()) && inf.until >= now && level.getEntity(id) instanceof LivingEntity e && e.isAlive()
					&& e.distanceTo(player) <= 16) {
				combo.organism = true;
				Mastery.add(player, Mastery.Goal.ONE_ORGANISM);
				return;
			}
		}
	}

	/** A weak cloud of a Mycelid's spores somewhere (Spore Trail, Spore Nodes). */
	static void cloudAt(ServerPlayer owner, ServerLevel level, Vec3 at, double radius, int ticks) {
		CLOUDS.add(new Cloud(owner.getUUID(), level.dimension(), at, radius, level.getGameTime() + ticks, false, 3 * 20));
		Fx.sporeCloud(level, at, null, (float) radius, ticks);
	}

	private static void burst(ServerLevel level, Vec3 at, double radius) {
		level.sendParticles(SPORE_DUST, at.x, at.y + 0.8, at.z, (int) (radius * 3), radius * 0.6, 0.5, radius * 0.6, 0);
	}

	/** Infects a creature with a Mycelid's spores for this long (or longer if it already is). */
	static void infect(ServerPlayer owner, LivingEntity target, ServerLevel level, int ticks) {
		long until = level.getGameTime() + ticks;
		Infection had = INFECTED.get(target.getUUID());
		if (had == null) {
			INFECTED.put(target.getUUID(), new Infection(owner.getUUID(), until));
		} else {
			had.until = Math.max(had.until, until);
		}
	}

	// ---- Neurosymbiosis ---------------------------------------------------------------------------------------

	private static double bondRadius(ServerPlayer player) {
		return Talents.has(player, Talent.SYM_UNITY) ? 10 : 6;
	}

	private static int bondTicks(ServerPlayer player) {
		return Talents.has(player, Talent.SYM_UNITY) ? 12 * 20 : 8 * 20;
	}

	/** V: the Mycelid, the players around them and their own pets share quickening spores. */
	public static Abilities.Refusal symbiosis(ServerPlayer player) {
		ServerLevel level = player.serverLevel();
		long until = level.getGameTime() + bondTicks(player);
		int others = 0;
		for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class, player.getBoundingBox().inflate(bondRadius(player)),
				e -> e.isAlive() && Allies.ally(player, e))) {
			bond(player, e, until);
			Fx.bond(level, player, e, bondTicks(player), e == player ? (float) bondRadius(player) : 0);
			if (e != player) {
				others++;
				Deeds.grant(player, "myc_one");
			}
		}
		if (Talents.has(player, Talent.SYM_AURA)) {
			AURAS.put(player.getUUID(), until);
		}
		organism(player, level);
		if (others > 0) {
			Talents.addProgress(player, SHARED, 1);
			Progression.addXp(player, "symbiosis", others, 20);
		}
		level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.AMETHYST_BLOCK_RESONATE, SoundSource.PLAYERS, 1f, 1.3f);
		level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.SPORE_BLOSSOM_HIT, SoundSource.PLAYERS, 1f, 0.8f);
		return null;
	}

	private static void bond(ServerPlayer caster, LivingEntity e, long until) {
		// A bond renewed while it still holds keeps its Protective Reflex spent: one softened blow per bond, not per recast
		Bond had = BONDS.get(e.getUUID());
		Bond bond = new Bond(caster.getUUID(), until);
		bond.reflexUsed = had != null && had.caster.equals(caster.getUUID()) && had.reflexUsed && had.until >= e.level().getGameTime();
		BONDS.put(e.getUUID(), bond);
		double speed = Talents.has(caster, Talent.SYM_PULSE) ? 0.30 : 0.20;
		modify(e.getAttribute(Attributes.MOVEMENT_SPEED), SPEED, speed);
		modify(e.getAttribute(Attributes.ATTACK_SPEED), ATTACK, 0.15);
		// Steadier nerves shrug off a slowing
		e.removeEffect(MobEffects.MOVEMENT_SLOWDOWN);
		if (Talents.has(caster, Talent.SYM_PULSE)) {
			e.addEffect(new MobEffectInstance(MobEffects.JUMP, (int) (until - e.level().getGameTime()), 0, true, true));
		}
		((ServerLevel) e.level()).sendParticles(ParticleTypes.HAPPY_VILLAGER, e.getX(), e.getY() + e.getBbHeight() * 0.7, e.getZ(), 3, 0.3, 0.4, 0.3, 0);
	}

	private static void modify(AttributeInstance attribute, ResourceLocation id, double amount) {
		if (attribute != null) {
			attribute.removeModifier(id);
			attribute.addTransientModifier(new AttributeModifier(id, amount, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL));
		}
	}

	private static void unbond(LivingEntity e) {
		AttributeInstance speed = e.getAttribute(Attributes.MOVEMENT_SPEED);
		if (speed != null) {
			speed.removeModifier(SPEED);
		}
		AttributeInstance attack = e.getAttribute(Attributes.ATTACK_SPEED);
		if (attack != null) {
			attack.removeModifier(ATTACK);
		}
	}

	/** Spore Adaptation: while it lasts, nothing slows one sharing Neurosymbiosis. */
	private static void onEffect(MobEffectEvent.Applicable event) {
		if (event.getEffectInstance() == null || event.getEffectInstance().getEffect().value() != MobEffects.MOVEMENT_SLOWDOWN.value()) {
			return;
		}
		Bond bond = BONDS.get(event.getEntity().getUUID());
		if (bond != null && event.getEntity().level().getServer() != null) {
			ServerPlayer caster = event.getEntity().level().getServer().getPlayerList().getPlayer(bond.caster);
			if (caster != null && Talents.has(caster, Talent.SYM_ADAPT)) {
				event.setResult(MobEffectEvent.Applicable.Result.DO_NOT_APPLY);
			}
		}
	}

	// ---- Every tick: clouds, infections, bonds ----------------------------------------------------------------

	private static void onServerTick(ServerTickEvent.Post event) {
		MinecraftServer server = event.getServer();
		int tick = server.getTickCount();
		if (tick % 10 == 0) {
			tickClouds(server);
		}
		if (tick % 20 == 0) {
			tickInfected(server);
		}
		if (tick % 10 == 5) {
			tickBonds(server);
		}
	}

	private static void tickClouds(MinecraftServer server) {
		for (Iterator<Cloud> it = CLOUDS.iterator(); it.hasNext(); ) {
			Cloud cloud = it.next();
			ServerLevel level = server.getLevel(cloud.level);
			ServerPlayer owner = server.getPlayerList().getPlayer(cloud.owner);
			if (level == null || owner == null || level.getGameTime() > cloud.until) {
				it.remove();
				continue;
			}
			if (cloud.follow && owner.level() == level) {
				cloud.at = owner.position();
			}
			double r = cloud.radius;
			for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class, new AABB(cloud.at, cloud.at).inflate(r, 3, r),
					e -> e.isAlive() && Voidborn.isEnemy(owner, e) && e.position().distanceTo(cloud.at) <= r + 0.5)) {
				boolean fresh = !INFECTED.containsKey(e.getUUID());
				infect(owner, e, level, cloud.linger);
				if (cloud.cast != null && cloud.cast.infected.add(e.getUUID())) {
					castInfected(owner, cloud.cast, level.getGameTime());
				}
				// Contagion: a newly infected creature passes the spores to those right beside it
				if (fresh && Talents.has(owner, Talent.SPORE_SPREAD)) {
					for (LivingEntity near : level.getEntitiesOfClass(LivingEntity.class, e.getBoundingBox().inflate(3),
							n -> n != e && n.isAlive() && Voidborn.isEnemy(owner, n))) {
						infect(owner, near, level, cloud.linger);
						INFECTED.get(near.getUUID()).spread = true;
					}
				}
			}
		}
	}

	/**
	 * Infected creatures: Weakness (Tremor: II), a little slowness, now and then losing their target and stumbling
	 * (Broken Coordination: more often); bosses only feel the Weakness. Marked ones glow; Feeding on the Infected gives
	 * the Mycelid Spores back.
	 */
	private static void tickInfected(MinecraftServer server) {
		Map<UUID, Integer> fed = new HashMap<>();
		for (ServerLevel level : server.getAllLevels()) {
			for (Iterator<Map.Entry<UUID, Infection>> it = INFECTED.entrySet().iterator(); it.hasNext(); ) {
				Map.Entry<UUID, Infection> entry = it.next();
				if (!(level.getEntity(entry.getKey()) instanceof LivingEntity e)) {
					continue;
				}
				Infection inf = entry.getValue();
				ServerPlayer owner = server.getPlayerList().getPlayer(inf.owner);
				if (!e.isAlive() || owner == null || level.getGameTime() > inf.until) {
					it.remove();
					continue;
				}
				boolean boss = e.getType().is(Tags.EntityTypes.BOSSES);
				e.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, 30, !boss && Talents.has(owner, Talent.SPORE_FEEBLE) ? 1 : 0, false, true), owner);
				if (!boss) {
					e.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 30, 0, false, true), owner);
					double chance = Talents.has(owner, Talent.SPORE_CONFUSE) ? 0.45 : 0.25;
					if (e instanceof Mob mob && e.getRandom().nextDouble() < chance) {
						mob.setTarget(null);
						mob.getNavigation().stop();
						Vec3 stumble = new Vec3(e.getRandom().nextDouble() - 0.5, 0, e.getRandom().nextDouble() - 0.5).normalize().scale(0.25);
						e.setDeltaMovement(e.getDeltaMovement().add(stumble));
						e.hurtMarked = true;
					}
				}
				if (Talents.has(owner, Talent.SPORE_MARK)) {
					e.addEffect(new MobEffectInstance(MobEffects.GLOWING, 30, 0, false, false));
				}
				if (Talents.has(owner, Talent.SPORE_FEED)) {
					fed.merge(owner.getUUID(), 1, Integer::sum);
				}
				level.sendParticles(SPORE_DUST, e.getX(), e.getY() + e.getBbHeight() * 0.8, e.getZ(), 3, 0.25, 0.3, 0.25, 0);
			}
		}
		// Infections of creatures that left the loaded world run out by themselves
		long now = server.overworld().getGameTime();
		INFECTED.values().removeIf(i -> now > i.until);
		fed.forEach((id, n) -> {
			ServerPlayer owner = server.getPlayerList().getPlayer(id);
			if (owner != null) {
				RaceState.setResource(owner, RaceState.resource(owner) + Math.min(4, n));
			}
		});
	}

	private static void tickBonds(MinecraftServer server) {
		long now = server.overworld().getGameTime();
		for (Iterator<Map.Entry<UUID, Bond>> it = BONDS.entrySet().iterator(); it.hasNext(); ) {
			Map.Entry<UUID, Bond> entry = it.next();
			LivingEntity e = find(server, entry.getKey());
			if (e == null) {
				it.remove();
				continue;
			}
			if (!e.isAlive() || now > entry.getValue().until) {
				unbond(e);
				// Shared Recovery: the bond ends with a little health back
				ServerPlayer caster = server.getPlayerList().getPlayer(entry.getValue().caster);
				if (e.isAlive() && caster != null && Talents.has(caster, Talent.SYM_SHARE)) {
					e.heal(6);
				}
				it.remove();
			}
		}
		// Living Aura: whoever comes near the caster while it lasts joins
		for (Iterator<Map.Entry<UUID, Long>> it = AURAS.entrySet().iterator(); it.hasNext(); ) {
			Map.Entry<UUID, Long> entry = it.next();
			ServerPlayer caster = server.getPlayerList().getPlayer(entry.getKey());
			if (caster == null || now > entry.getValue()) {
				it.remove();
				continue;
			}
			ServerLevel level = caster.serverLevel();
			for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class, caster.getBoundingBox().inflate(bondRadius(caster)), e -> e.isAlive()
					&& !BONDS.containsKey(e.getUUID()) && Allies.ally(caster, e))) {
				bond(caster, e, entry.getValue());
				Fx.bond(level, caster, e, (int) (entry.getValue() - now), 0);
			}
		}
	}

	private static LivingEntity find(MinecraftServer server, UUID id) {
		ServerPlayer player = server.getPlayerList().getPlayer(id);
		if (player != null) {
			return player;
		}
		for (ServerLevel level : server.getAllLevels()) {
			if (level.getEntity(id) instanceof LivingEntity e) {
				return e;
			}
		}
		return null;
	}

	/** Whether this creature shares a Mycelid's Neurosymbiosis now. */
	public static boolean bonded(LivingEntity e) {
		return BONDS.containsKey(e.getUUID());
	}

	/** An infected enemy dying teaches the Mycelid and counts towards the Spore Storm's trial. */
	private static void onDeath(LivingDeathEvent event) {
		Infection inf = INFECTED.remove(event.getEntity().getUUID());
		if (inf != null && event.getEntity().level().getServer() != null) {
			ServerPlayer owner = event.getEntity().level().getServer().getPlayerList().getPlayer(inf.owner);
			if (owner != null) {
				Talents.addProgress(owner, INFECTED_KILLS, 1);
				Progression.addXp(owner, "spores", 1, 40);
			}
		}
		BONDS.remove(event.getEntity().getUUID());
	}

	/** Forgets a player's live state (a new race). */
	public static void reset(ServerPlayer player) {
		Awaken.reset(player);
		BONDS.remove(player.getUUID());
		unbond(player);
		AURAS.remove(player.getUUID());
		COMBOS.remove(player.getUUID());
		CLOUDS.removeIf(c -> c.owner.equals(player.getUUID()));
	}
}
