package euf.mageslegacy.frozen;

import euf.mageslegacy.fx.Fx;
import euf.mageslegacy.fx.ModParticles;
import euf.mageslegacy.net.RacePackets;
import euf.mageslegacy.race.AbilityResult;
import euf.mageslegacy.race.AbilitySlots;
import euf.mageslegacy.race.Progression;
import euf.mageslegacy.race.Race;
import euf.mageslegacy.race.RaceData;
import euf.mageslegacy.race.RaceState;
import euf.mageslegacy.race.Talents;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import net.fabricmc.fabric.api.networking.v1.PacketByteBufs;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.block.BlockState;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.block.entity.BlockEntityTicker;
import net.minecraft.block.entity.BlockEntityType;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.effect.StatusEffect;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.entity.mob.Monster;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.projectile.ProjectileEntity;
import net.minecraft.entity.projectile.ProjectileUtil;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.particle.DustParticleEffect;
import net.minecraft.particle.ParticleEffect;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.registry.RegistryKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.EntityHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;
import org.jetbrains.annotations.Nullable;
import org.joml.Vector3f;

/**
 * Everything the Frozen do: sand of time as their resource, the trail of their last seconds, Blur (a blow now and
 * then passes through them), the Rewind back along that trail, the Frozen Dome, the Quickening of blocks; and their
 * weaknesses (slow natural healing, shorter potions, numbness when the sand lies unused).
 */
public final class Frozen {
	public static final int REWIND_COST = 30;
	public static final int REWIND_COST_CHEAP = 20;
	public static final int ALLY_COST = 40;
	public static final int DOME_COST = 45;
	public static final int DOME_COST_CHEAP = 35;
	public static final int QUICKEN_COST = 20;
	public static final int FREEZE_COST = 25;
	public static final int CURRENT_COST = 30;
	private static final int REWIND_COOLDOWN = 240;
	private static final int REWIND_COOLDOWN_QUICK = 160;
	private static final int REWIND_TICKS = 10;
	private static final int TRAIL = 80;
	private static final int TRAIL_LONG = 120;
	private static final int RETURN_WINDOW = 60;
	private static final int DOME_COOLDOWN = 500;
	private static final int DOME_TICKS = 120;
	private static final int DOME_TICKS_LONG = 180;
	private static final int QUICKEN_COOLDOWN = 40;
	private static final int QUICKEN_TICKS = 600;
	private static final int QUICKEN_TICKS_LONG = 1200;
	private static final int QUICKEN_SPEED = 10;
	private static final int QUICKEN_MAX = 27;
	private static final double QUICKEN_RANGE = 8;
	private static final int FREEZE_SLOT = AbilitySlots.cooldownSlot(Race.FROZEN, 4);
	private static final int FREEZE_COOLDOWN = 400;
	private static final int FREEZE_TICKS = 60;
	private static final int STOP_SLOT = 5;
	private static final int STOP_COOLDOWN = 6000;
	private static final int STOP_TICKS = 60;
	private static final int STOP_RADIUS = 16;
	private static final int STRIKE_TICKS = 80;
	private static final int ETERNAL_SLOT = 7;
	private static final int ETERNAL_TICKS = 120;
	private static final int CURRENT_SLOT = AbilitySlots.cooldownSlot(Race.FROZEN, 3);
	private static final int CURRENT_TICKS = 600;
	private static final int CURRENT_COOLDOWN = 6000;
	private static final int LONG_COOLDOWN = 12000;
	private static final int NUMB_AFTER = 12000;
	public static final float BLUR_CHANCE = 0.1f;
	public static final float BLUR_CHANCE_OVERTAKE = 0.2f;

	/** Colours of the glowing effects: pale ice, golden sand, the white of a stopped moment. */
	private static final int ICE_RGB = 0x9FCBF2;
	private static final int SAND_RGB = 0xE8C98A;
	private static final int STILL_RGB = 0xE8F4FF;
	/** Ice shards and grains of sand (the race's own particles); fine ice dust for the frozen silhouette. */
	private static final ParticleEffect ICE = ModParticles.ICE_SHARD;
	private static final ParticleEffect SAND = ModParticles.SAND;
	private static final DustParticleEffect ICE_DUST = new DustParticleEffect(new Vector3f(0.62f, 0.80f, 0.95f), 1.2f);

	/** Where every player was, newest last: one point a tick, a few seconds deep. */
	private record Point(Vec3d pos, float health, RegistryKey<World> world) {}

	/** A Rewind under way: the player flies along {@code path} (newest first) over a few ticks. */
	private static final class Flight {
		final ServerPlayerEntity player;
		final ServerPlayerEntity caster;
		final List<Vec3d> path;
		final float health;
		final boolean back;
		int tick;

		Flight(ServerPlayerEntity player, ServerPlayerEntity caster, List<Vec3d> path, float health, boolean back) {
			this.player = player;
			this.caster = caster;
			this.path = path;
			this.health = health;
			this.back = back;
		}
	}

	/** Double Loop: the way back to where the Rewind started, open for a moment after it. */
	private record Way(List<Vec3d> path, long until) {}

	/** A Frozen Dome, or (with {@code stop}) the Still Frame's wider, total halt. */
	private static final class Dome {
		final UUID owner;
		final ServerPlayerEntity player;
		final ServerWorld world;
		Vec3d center;
		final double radius;
		final long until;
		final boolean stop;
		/** Projectile uuid -> its velocity and whether it had gravity, to let it go as it was. */
		final Map<UUID, Held> held = new HashMap<>();
		final Set<UUID> rewarded = new HashSet<>();

		Dome(ServerPlayerEntity player, ServerWorld world, Vec3d center, double radius, long until, boolean stop) {
			this.owner = player.getUuid();
			this.player = player;
			this.world = world;
			this.center = center;
			this.radius = radius;
			this.until = until;
			this.stop = stop;
		}

		boolean contains(Entity e) {
			return e.getWorld() == world && e.getBoundingBox().getCenter().squaredDistanceTo(center) < radius * radius;
		}
	}

	private record Held(Vec3d velocity, boolean noGravity) {}

	private record Quick(UUID owner, ServerWorld world, BlockPos pos, long until) {}

	private record Hold(ServerWorld world, long until) {}

	private record Eternal(long until, float health) {}

	private static final Map<UUID, ArrayDeque<Point>> TRAILS = new HashMap<>();
	private static final List<Flight> FLIGHTS = new ArrayList<>();
	private static final Map<UUID, Way> WAYS = new HashMap<>();
	private static final List<Dome> DOMES = new ArrayList<>();
	private static final List<Quick> QUICK = new ArrayList<>();
	private static final Map<UUID, Hold> HELD_MOBS = new HashMap<>();
	private static final Map<UUID, Eternal> ETERNAL = new HashMap<>();
	/** Strike from the Past: until when the next blow of a Frozen is doubled. */
	private static final Map<UUID, Long> STRIKE = new HashMap<>();
	/** Doubled Moment: blows landed since the last doubled one. */
	private static final Map<UUID, Integer> HITS = new HashMap<>();
	/** The Current: until when a Frozen's abilities recharge twice as fast. */
	private static final Map<UUID, Long> CURRENT = new HashMap<>();
	/** Silhouettes left where a Rewind began: the frozen shape glints for a second. */
	private static final List<Hold> SHAPES_AT = new ArrayList<>();
	private static final List<Vec3d> SHAPES = new ArrayList<>();
	/** When each Frozen last spent sand (numbness creeps in after long idleness), and sand spent towards experience. */
	private static final Map<UUID, Long> SPENT_AT = new HashMap<>();
	private static final Map<UUID, Float> SPENT = new HashMap<>();
	private static boolean quickChanged;

	private Frozen() {
	}

	public static void reset() {
		TRAILS.clear();
		FLIGHTS.clear();
		WAYS.clear();
		DOMES.clear();
		QUICK.clear();
		HELD_MOBS.clear();
		ETERNAL.clear();
		STRIKE.clear();
		HITS.clear();
		CURRENT.clear();
		SHAPES_AT.clear();
		SHAPES.clear();
		SPENT_AT.clear();
		SPENT.clear();
	}

	public static boolean is(ServerPlayerEntity player) {
		return RaceState.raceOf(player).orElse(null) == Race.FROZEN;
	}

	private static long now(MinecraftServer server) {
		return server.getTicks();
	}

	private static boolean ready(ServerPlayerEntity player, RaceData data, int slot) {
		return data.readyAt.getOrDefault(slot, 0L) <= now(player.getServer());
	}

	private static void cooldown(ServerPlayerEntity player, RaceData data, int slot, int ticks) {
		// In the Current time runs twice as fast for the Frozen: the three abilities recharge in half the time
		Long current = CURRENT.get(player.getUuid());
		if (slot <= 2 && current != null && current > now(player.getServer())) {
			ticks /= 2;
		}
		// Fast Hourglass: the Rewind and the Dome recharge a quarter faster
		if (slot <= 1 && data.has(Talents.FLOW_RECHARGE)) {
			ticks = ticks * 3 / 4;
		}
		data.readyAt.put(slot, now(player.getServer()) + ticks);
		data.dirty = true;
	}

	/** Takes the sand; spending wards off numbness and teaches: a point of race experience for every 10 grains. */
	private static void spend(ServerPlayerEntity player, RaceData data, float cost) {
		data.addResource(-cost);
		SPENT_AT.put(player.getUuid(), now(player.getServer()));
		float total = SPENT.getOrDefault(player.getUuid(), 0f) + cost;
		int xp = (int) (total / 10);
		SPENT.put(player.getUuid(), total - xp * 10);
		if (xp > 0) {
			Progression.addXp(player, data, xp);
		}
	}

	public static int rewindCost(boolean cheap) {
		return cheap ? REWIND_COST_CHEAP : REWIND_COST;
	}

	public static int domeCost(boolean cheap) {
		return cheap ? DOME_COST_CHEAP : DOME_COST;
	}


	// ---- every tick ----

	public static void tick(MinecraftServer server) {
		long now = now(server);
		for (ServerPlayerEntity player : server.getPlayerManager().getPlayerList()) {
			record(player);
		}
		tickFlights(server);
		tickDomes(now);
		tickQuick(now);
		// The hourglasses over quickened blocks: sent when the set changes, and every 5 s for those who came near since
		if (quickChanged || now % 100 == 0) {
			quickChanged = false;
			sendQuickened(server, now);
		}
		tickHeld(server, now);
		tickShapes(now);
		ETERNAL.entrySet().removeIf(e -> {
			if (e.getValue().until() > now) {
				return false;
			}
			ServerPlayerEntity player = server.getPlayerManager().getPlayer(e.getKey());
			if (player != null && player.isAlive()) {
				// The moment ends: everything is as it was when it began
				player.setHealth(Math.min(player.getMaxHealth(), e.getValue().health()));
				ServerWorld world = player.getServerWorld();
				world.spawnParticles(ParticleTypes.END_ROD, player.getX(), player.getY() + 1, player.getZ(), 30, 0.4, 0.8, 0.4, 0.05);
				world.playSound(null, player.getBlockPos(), SoundEvents.BLOCK_BELL_RESONATE, SoundCategory.PLAYERS, 0.8f, 1.6f);
			}
			return true;
		});
	}

	/** One more point of the trail; a new world starts a new trail (a Rewind never crosses a portal). */
	public static void record(ServerPlayerEntity player) {
		ArrayDeque<Point> trail = TRAILS.computeIfAbsent(player.getUuid(), id -> new ArrayDeque<>());
		if (!player.isAlive() || player.isSpectator() || isFlying(player)) {
			if (!player.isAlive()) {
				trail.clear();
			}
			return;
		}
		RegistryKey<World> world = player.getWorld().getRegistryKey();
		if (!trail.isEmpty() && !trail.peekLast().world().equals(world)) {
			trail.clear();
		}
		trail.addLast(new Point(player.getPos(), player.getHealth(), world));
		while (trail.size() > TRAIL_LONG + 1) {
			trail.removeFirst();
		}
	}

	public static boolean isFlying(Entity player) {
		for (Flight f : FLIGHTS) {
			if (f.player == player) {
				return true;
			}
		}
		return false;
	}

	// ---- once a second ----

	public static void tickSecond(ServerPlayerEntity player, RaceData data) {
		data.addResource(data.has(Talents.FLOW_SAND) ? 1.0f : 0.5f);
		long now = now(player.getServer());
		long spent = SPENT_AT.computeIfAbsent(player.getUuid(), id -> now);
		if (now - spent > NUMB_AFTER) {
			refresh(player, StatusEffects.SLOWNESS, 0, 60, 30);
		}
		if (data.has(Talents.FLOW_HASTE)) {
			refresh(player, StatusEffects.HASTE, 0, 60, 30);
		}
		if (data.has(Talents.FLOW_SWIFT)) {
			refresh(player, StatusEffects.SPEED, 0, 60, 30);
		}
		for (Dome dome : DOMES) {
			// Quiet Harbour: players under the Dome mend
			if (!dome.stop && dome.owner.equals(player.getUuid()) && data.has(Talents.STASIS_MEND)) {
				for (PlayerEntity p : dome.world.getPlayers()) {
					if (dome.contains(p)) {
						p.addStatusEffect(new StatusEffectInstance(StatusEffects.REGENERATION, 40, 0, true, false, true));
					}
				}
			}
		}
	}

	private static void refresh(ServerPlayerEntity player, StatusEffect effect, int amplifier, int duration, int below) {
		StatusEffectInstance current = player.getStatusEffect(effect);
		if (current == null || current.getAmplifier() < amplifier || current.getDuration() <= below) {
			player.addStatusEffect(new StatusEffectInstance(effect, duration, amplifier, true, false, true));
		}
	}

	// ---- weaknesses ----

	/** Natural healing (from a full stomach) works at half speed, three quarters with Own Rhythm. */
	public static float naturalHealing(PlayerEntity player) {
		if (!(player instanceof ServerPlayerEntity sp) || !is(sp)) {
			return 1;
		}
		return RaceState.of(sp).has(Talents.FLOW_REGEN) ? 0.75f : 0.5f;
	}

	/**
	 * Potions and other good effects last 30% shorter on the Frozen (time slips past them).
	 * Ambient effects (beacons, the races' own passives) and short ones are left alone.
	 */
	public static StatusEffectInstance shorten(LivingEntity entity, StatusEffectInstance effect) {
		if (!(entity instanceof ServerPlayerEntity player) || effect.isAmbient() || effect.isInfinite() || effect.getDuration() <= 60
				|| !effect.getEffectType().isBeneficial() || !is(player)) {
			return effect;
		}
		return new StatusEffectInstance(effect.getEffectType(), (int) (effect.getDuration() * 0.7f), effect.getAmplifier(), false,
				effect.shouldShowParticles(), effect.shouldShowIcon());
	}

	// ---- damage ----

	/**
	 * Whether a blow doesn't land at all: nothing touches a Frozen in the middle of a Rewind, and Blur lets one blow
	 * in ten from a creature pass through them.
	 */
	public static boolean avoids(LivingEntity victim, DamageSource source) {
		if (!(victim instanceof ServerPlayerEntity player)) {
			return false;
		}
		if (isFlying(player)) {
			return true;
		}
		Entity attacker = source.getAttacker();
		if (attacker == null || attacker == player || !is(player)) {
			return false;
		}
		RaceData data = RaceState.of(player);
		if (player.getRandom().nextFloat() >= (data.has(Talents.FLOW_OVERTAKE) ? BLUR_CHANCE_OVERTAKE : BLUR_CHANCE)) {
			return false;
		}
		if (data.has(Talents.FLOW_OVERTAKE)) {
			player.addStatusEffect(new StatusEffectInstance(StatusEffects.SPEED, 40, 1, true, false, true));
		}
		ServerWorld world = player.getServerWorld();
		world.spawnParticles(ICE, player.getX(), player.getY() + 1, player.getZ(), 14, 0.3, 0.6, 0.3, 0);
		world.spawnParticles(ParticleTypes.END_ROD, player.getX(), player.getY() + 1, player.getZ(), 4, 0.3, 0.5, 0.3, 0.02);
		// The blow passes through a frozen afterimage of the body
		Fx.ghosts(world, List.of(player.getPos(), player.getPos()), player.getYaw(), 1, 14, ICE_RGB);
		world.playSound(null, player.getBlockPos(), SoundEvents.ENTITY_ILLUSIONER_MIRROR_MOVE, SoundCategory.PLAYERS, 0.6f, 1.6f);
		data.addResource(4);
		Progression.addXp(player, data, 2);
		return true;
	}

	/**
	 * Blows that land on a Frozen: they give sand; under its own Dome Sand Ward takes a quarter off; the Eternal
	 * Moment keeps the Frozen alive (and starts by itself when health falls low).
	 */
	public static float modifyDamage(ServerPlayerEntity player, DamageSource source, float amount) {
		RaceData data = RaceState.of(player);
		if (amount <= 0) {
			return amount;
		}
		data.addResource(2);
		if (data.has(Talents.STASIS_WARD) && DOMES.stream().anyMatch(d -> !d.stop && d.owner.equals(player.getUuid()) && d.contains(player))) {
			amount *= 0.75f;
		}
		UUID id = player.getUuid();
		if (!ETERNAL.containsKey(id) && data.has(Talents.LOOP_ETERNAL) && ready(player, data, ETERNAL_SLOT)
				&& player.getHealth() - amount < player.getMaxHealth() * 0.25f) {
			ETERNAL.put(id, new Eternal(now(player.getServer()) + ETERNAL_TICKS, player.getHealth()));
			cooldown(player, data, ETERNAL_SLOT, LONG_COOLDOWN);
			ServerWorld world = player.getServerWorld();
			world.spawnParticles(ParticleTypes.END_ROD, player.getX(), player.getY() + 1, player.getZ(), 40, 0.5, 0.9, 0.5, 0.1);
			world.playSound(null, player.getBlockPos(), SoundEvents.BLOCK_BEACON_POWER_SELECT, SoundCategory.PLAYERS, 1.0f, 1.8f);
			player.sendMessage(Text.translatable("mages_legacy.frozen.eternal").formatted(Formatting.AQUA), true);
			Fx.sphere(world, player.getPos(), 1.4f, ETERNAL_TICKS, SAND_RGB, player, false);
			Fx.screen(player, ETERNAL_TICKS, SAND_RGB);
			// The moment closes around the body: sand winds in, a clock face burns under it
			Fx.helix(world, player, 1.3f, 2.6f, 3, true, 24, SAND_RGB);
			Fx.sigil(world, player.getPos(), player, 2.0f, ETERNAL_TICKS, SAND_RGB);
			Fx.burst(world, player.getPos().add(0, 1, 0), 2.6f, 8, STILL_RGB);
			Fx.shake(player, 2.0f, 10);
		}
		if (ETERNAL.containsKey(id)) {
			amount = Math.max(0, Math.min(amount, player.getHealth() - 1));
		}
		return amount;
	}

	/** The Eternal Moment holds: no death while it lasts. */
	public static boolean allowDeath(ServerPlayerEntity player) {
		RaceData data = RaceState.of(player);
		if (data == null || data.race != Race.FROZEN) {
			return true;
		}
		if (ETERNAL.containsKey(player.getUuid())) {
			player.setHealth(1);
			return false;
		}
		return true;
	}

	/**
	 * A Frozen's own blow. Strike from the Past doubles the first one after a Rewind; Viscous Time slows the one hit;
	 * Doubled Moment lands every third blow twice (half again the damage); Sand in the Wounds draws sand from monsters.
	 */
	public static float onHit(LivingEntity victim, DamageSource source, float amount) {
		if (!(source.getAttacker() instanceof ServerPlayerEntity player) || source.getSource() != player || victim == player || !is(player)) {
			return amount;
		}
		RaceData data = RaceState.of(player);
		ServerWorld world = player.getServerWorld();
		Long until = STRIKE.remove(player.getUuid());
		if (until != null && until >= now(player.getServer())) {
			amount *= 2;
			world.spawnParticles(ParticleTypes.END_ROD, victim.getX(), victim.getY() + victim.getHeight() / 2, victim.getZ(), 12, 0.3, 0.3, 0.3, 0.1);
			world.playSound(null, victim.getBlockPos(), SoundEvents.BLOCK_AMETHYST_BLOCK_BREAK, SoundCategory.PLAYERS, 1.0f, 1.5f);
		}
		if (data.has(Talents.FLOW_VISCOUS)) {
			victim.addStatusEffect(new StatusEffectInstance(StatusEffects.SLOWNESS, 40, 1));
		}
		if (data.has(Talents.FLOW_ECHO)) {
			int hits = HITS.merge(player.getUuid(), 1, Integer::sum);
			if (hits >= 3) {
				HITS.remove(player.getUuid());
				amount *= 1.5f;
				world.spawnParticles(ICE, victim.getX(), victim.getY() + victim.getHeight() / 2, victim.getZ(), 10, 0.3, 0.3, 0.3, 0);
				world.playSound(null, victim.getBlockPos(), SoundEvents.ENTITY_PLAYER_ATTACK_SWEEP, SoundCategory.PLAYERS, 0.8f, 1.6f);
			}
		}
		if (data.has(Talents.FLOW_FEED) && victim instanceof Monster) {
			data.addResource(2);
		}
		return amount;
	}

	// ---- abilities ----

	public static AbilityResult use(ServerPlayerEntity player, RaceData data, int slot) {
		return switch (slot) {
			case 0 -> rewind(player, data);
			case 1 -> dome(player, data);
			case 2 -> quicken(player, data);
			case 3 -> data.has(Talents.FLOW_CURRENT) ? current(player, data) : RaceData.talentLocked(player, Talents.FLOW_CURRENT);
			case 4 -> data.has(Talents.STASIS_FREEZE) ? freeze(player, data) : RaceData.talentLocked(player, Talents.STASIS_FREEZE);
			default -> AbilityResult.LOCKED;
		};
	}

	/** The trail this Frozen may rewind along, newest first: 4 seconds, 6 with the Long Loop. */
	private static List<Point> trail(ServerPlayerEntity player, RaceData caster) {
		ArrayDeque<Point> deque = TRAILS.get(player.getUuid());
		List<Point> list = new ArrayList<>();
		if (deque == null) {
			return list;
		}
		int depth = caster.has(Talents.LOOP_LONG) ? TRAIL_LONG : TRAIL;
		Iterator<Point> it = deque.descendingIterator();
		while (it.hasNext() && list.size() <= depth) {
			list.add(it.next());
		}
		return list;
	}

	/**
	 * Rewind: back along your own trail to where you were 4 seconds ago, as a streak of light that nothing can touch.
	 * With Double Loop, pressing again soon after flies back to where the Rewind began. Sneaking (Another's Moment)
	 * rewinds the player you look at instead.
	 */
	public static AbilityResult rewind(ServerPlayerEntity player, RaceData data) {
		if (isFlying(player)) {
			return AbilityResult.HINTED;
		}
		long now = now(player.getServer());
		Way way = WAYS.remove(player.getUuid());
		if (way != null && way.until() >= now && !data.alt) {
			FLIGHTS.add(new Flight(player, player, way.path(), player.getHealth(), true));
			startFx(player);
			flightFx(player, way.path());
			return AbilityResult.OK;
		}
		if (!ready(player, data, 0)) {
			return AbilityResult.COOLDOWN;
		}
		ServerPlayerEntity target = player;
		int cost = rewindCost(data.has(Talents.LOOP_CHEAP));
		if (data.alt) {
			if (!data.has(Talents.LOOP_ALLY)) {
				return RaceData.talentLocked(player, Talents.LOOP_ALLY);
			}
			target = lookedAtPlayer(player, 16);
			if (target == null) {
				return AbilityResult.NO_TARGET;
			}
			cost = ALLY_COST;
		}
		if (data.resource < cost) {
			return AbilityResult.NO_RESOURCE;
		}
		List<Point> trail = trail(target, data);
		if (trail.size() < 5) {
			player.sendMessage(Text.translatable("mages_legacy.frozen.no_trail"), true);
			return AbilityResult.HINTED;
		}
		spend(player, data, cost);
		cooldown(player, data, 0, data.has(Talents.LOOP_QUICK) ? REWIND_COOLDOWN_QUICK : REWIND_COOLDOWN);
		// A Rewind right after a blow saved the Frozen from the next one: that teaches
		if (target == player && player.hurtTime > 0) {
			Progression.addXp(player, data, 5);
		}
		fly(target, player, data, trail, true);
		return AbilityResult.OK;
	}

	/** Starts the flight of {@code target} back along {@code trail} (newest first). */
	private static void fly(ServerPlayerEntity target, ServerPlayerEntity caster, RaceData data, List<Point> trail, boolean shards) {
		List<Vec3d> path = new ArrayList<>();
		for (Point p : trail) {
			path.add(p.pos());
		}
		Point oldest = trail.get(trail.size() - 1);
		target.stopRiding();
		FLIGHTS.add(new Flight(target, caster, path, oldest.health(), false));
		startFx(target);
		flightFx(target, path);
		if (shards && target == caster && data.has(Talents.LOOP_SHARDS)) {
			shards(caster, target.getPos());
		}
	}

	/** The start of a flight: the body turns to light, a frozen silhouette stays behind. */
	private static void startFx(ServerPlayerEntity player) {
		ServerWorld world = player.getServerWorld();
		player.addStatusEffect(new StatusEffectInstance(StatusEffects.INVISIBILITY, REWIND_TICKS + 4, 0, true, false, false));
		SHAPES.add(player.getPos());
		SHAPES_AT.add(new Hold(world, now(player.getServer()) + 20));
		world.spawnParticles(ParticleTypes.FLASH, player.getX(), player.getY() + 1, player.getZ(), 1, 0, 0, 0, 0);
		world.playSound(null, player.getBlockPos(), SoundEvents.ITEM_TRIDENT_RETURN, SoundCategory.PLAYERS, 1.0f, 1.6f);
		world.playSound(null, player.getBlockPos(), SoundEvents.BLOCK_AMETHYST_BLOCK_CHIME, SoundCategory.PLAYERS, 1.2f, 0.6f);
	}

	/** The flight seen by all: a streak of light from ice to sand along the way; for the flier, the screen's edges glow. */
	private static void flightFx(ServerPlayerEntity player, List<Vec3d> path) {
		Fx.flight(player, REWIND_TICKS);
		Fx.ribbon(player.getServerWorld(), path, 26, ICE_RGB, SAND_RGB, 1.3f);
		Fx.screen(player, 16, ICE_RGB);
		// Frozen afterimages of the moments flown back through, and the view drawn in as time runs backwards
		Fx.ghosts(player.getServerWorld(), path, player.getYaw(), 5, 22, ICE_RGB);
		Fx.fov(player, -0.12f, 14);
		Fx.shake(player, 0.6f, 6);
	}

	/** Shards of the Moment: where the Rewind began bursts into frost. */
	private static void shards(ServerPlayerEntity owner, Vec3d at) {
		ServerWorld world = owner.getServerWorld();
		for (LivingEntity m : world.getEntitiesByClass(LivingEntity.class, new Box(at, at).expand(3),
				e -> e.isAlive() && e instanceof Monster)) {
			m.damage(world.getDamageSources().indirectMagic(owner, owner), 4);
			m.addStatusEffect(new StatusEffectInstance(StatusEffects.SLOWNESS, 60, 1));
		}
		world.spawnParticles(ParticleTypes.SNOWFLAKE, at.x, at.y + 1, at.z, 40, 1.2, 0.6, 1.2, 0.08);
		world.spawnParticles(ICE, at.x, at.y + 1, at.z, 30, 1.0, 0.6, 1.0, 0);
		Fx.ring(world, at, 0.2f, 3.5f, 10, ICE_RGB);
		Fx.burst(world, at.add(0, 1, 0), 3.0f, 8, ICE_RGB);
		world.playSound(null, BlockPos.ofFloored(at), SoundEvents.BLOCK_GLASS_BREAK, SoundCategory.PLAYERS, 1.0f, 0.7f);
	}

	private static void tickFlights(MinecraftServer server) {
		for (Iterator<Flight> it = FLIGHTS.iterator(); it.hasNext(); ) {
			Flight f = it.next();
			ServerPlayerEntity player = f.player;
			if (player.isRemoved() || !player.isAlive()) {
				it.remove();
				continue;
			}
			ServerWorld world = player.getServerWorld();
			int last = f.path.size() - 1;
			int from = Math.round((float) f.tick * last / REWIND_TICKS);
			f.tick++;
			int to = Math.min(last, Math.round((float) f.tick * last / REWIND_TICKS));
			// A streak of light along the stretch flown this tick
			Vec3d a = f.path.get(from);
			Vec3d b = f.path.get(to);
			for (int i = 0; i <= 6; i++) {
				Vec3d p = a.lerp(b, i / 6.0).add(0, 1.0, 0);
				world.spawnParticles(ParticleTypes.END_ROD, p.x, p.y, p.z, 1, 0.05, 0.15, 0.05, 0.005);
				world.spawnParticles(i % 2 == 0 ? ICE : SAND, p.x, p.y - 0.2, p.z, 2, 0.15, 0.3, 0.15, 0);
			}
			player.setVelocity(Vec3d.ZERO);
			player.fallDistance = 0;
			player.networkHandler.requestTeleport(b.x, b.y, b.z, player.getYaw(), player.getPitch());
			if (to >= last) {
				it.remove();
				land(player, f, world);
			}
		}
	}

	/** The end of a flight: a flash, the trail's health if the Body's Memory allows, monsters lose sight of you. */
	private static void land(ServerPlayerEntity player, Flight f, ServerWorld world) {
		player.removeStatusEffect(StatusEffects.INVISIBILITY);
		player.extinguish();
		player.fallDistance = 0;
		player.setVelocity(Vec3d.ZERO);
		player.velocityModified = true;
		world.spawnParticles(ParticleTypes.FLASH, player.getX(), player.getY() + 1, player.getZ(), 1, 0, 0, 0, 0);
		world.spawnParticles(ParticleTypes.END_ROD, player.getX(), player.getY() + 1, player.getZ(), 24, 0.3, 0.6, 0.3, 0.12);
		Fx.pillar(world, player.getPos(), 0.55f, 3.5f, 14, SAND_RGB);
		Fx.ring(world, player.getPos(), 0.3f, 3, 12, ICE_RGB);
		// Time sets again: a flash, and a clock face of sand under the feet
		Fx.burst(world, player.getPos().add(0, 1, 0), 1.6f, 7, STILL_RGB);
		Fx.sigil(world, player.getPos(), null, 1.8f, 24, SAND_RGB);
		Fx.shake(player, 1.2f, 6);
		world.spawnParticles(SAND, player.getX(), player.getY() + 0.5, player.getZ(), 20, 0.5, 0.3, 0.5, 0);
		world.playSound(null, player.getBlockPos(), SoundEvents.BLOCK_AMETHYST_CLUSTER_BREAK, SoundCategory.PLAYERS, 1.0f, 1.4f);
		world.playSound(null, player.getBlockPos(), SoundEvents.BLOCK_BELL_RESONATE, SoundCategory.PLAYERS, 0.5f, 2.0f);
		if (f.back) {
			return;
		}
		for (MobEntity m : world.getEntitiesByClass(MobEntity.class, player.getBoundingBox().expand(32), e -> e.getTarget() == player)) {
			m.setTarget(null);
		}
		ServerPlayerEntity caster = f.caster;
		RaceData data = caster.isRemoved() ? null : RaceState.of(caster);
		if (data == null) {
			return;
		}
		// Body's Memory: half of the health lost on the way comes back
		if (data.has(Talents.LOOP_HEAL) && f.health > player.getHealth()) {
			player.setHealth(Math.min(player.getMaxHealth(), player.getHealth() + (f.health - player.getHealth()) / 2));
		}
		// Strike from the Past: the first blow after landing hits twice as hard
		if (caster == player && data.has(Talents.LOOP_STRIKE)) {
			STRIKE.put(player.getUuid(), now(player.getServer()) + STRIKE_TICKS);
		}
		if (data.has(Talents.LOOP_CLEANSE)) {
			player.getStatusEffects().stream().filter(e -> !e.getEffectType().isBeneficial()).map(StatusEffectInstance::getEffectType).toList()
					.forEach(player::removeStatusEffect);
		}
		if (data.has(Talents.LOOP_HASTE)) {
			player.addStatusEffect(new StatusEffectInstance(StatusEffects.SPEED, 80, 1, false, true, true));
		}
		if (caster == player && data.has(Talents.LOOP_RETURN)) {
			List<Vec3d> forward = new ArrayList<>(f.path);
			java.util.Collections.reverse(forward);
			WAYS.put(player.getUuid(), new Way(forward, now(player.getServer()) + RETURN_WINDOW));
		}
	}

	private static void tickShapes(long now) {
		for (int i = SHAPES.size() - 1; i >= 0; i--) {
			Hold h = SHAPES_AT.get(i);
			Vec3d at = SHAPES.get(i);
			if (h.until() < now) {
				SHAPES.remove(i);
				SHAPES_AT.remove(i);
				continue;
			}
			// A body-shaped glint of frost: a column of ice light, shoulders and head
			if (now % 2 == 0) {
				h.world().spawnParticles(ICE_DUST, at.x, at.y + 0.9, at.z, 6, 0.18, 0.45, 0.18, 0);
				h.world().spawnParticles(ICE_DUST, at.x, at.y + 1.6, at.z, 3, 0.12, 0.1, 0.12, 0);
				h.world().spawnParticles(ParticleTypes.END_ROD, at.x, at.y + 1.1, at.z, 1, 0.2, 0.5, 0.2, 0);
			}
		}
	}

	@Nullable
	private static ServerPlayerEntity lookedAtPlayer(ServerPlayerEntity player, double range) {
		LivingEntity hit = lookedAt(player, range, e -> e instanceof ServerPlayerEntity);
		return hit instanceof ServerPlayerEntity p ? p : null;
	}

	@Nullable
	private static LivingEntity lookedAt(ServerPlayerEntity player, double range, java.util.function.Predicate<LivingEntity> filter) {
		Vec3d eye = player.getEyePos();
		Vec3d look = player.getRotationVec(1.0f);
		Vec3d end = eye.add(look.multiply(range));
		Box box = player.getBoundingBox().stretch(look.multiply(range)).expand(1);
		EntityHitResult hit = ProjectileUtil.raycast(player, eye, end, box,
				e -> e instanceof LivingEntity living && living.isAlive() && e != player && filter.test(living), range * range);
		return hit != null && hit.getEntity() instanceof LivingEntity living ? living : null;
	}

	// ---- the Dome ----

	public static double domeRadius(RaceData data) {
		return data.has(Talents.STASIS_WIDE) ? 7 : 5;
	}

	/** Frozen Dome: for 6 seconds enemies under it all but stop, and their arrows and fireballs hang in the air. */
	public static AbilityResult dome(ServerPlayerEntity player, RaceData data) {
		if (!ready(player, data, 1)) {
			return AbilityResult.COOLDOWN;
		}
		int cost = domeCost(data.has(Talents.STASIS_CHEAP));
		if (data.resource < cost) {
			return AbilityResult.NO_RESOURCE;
		}
		spend(player, data, cost);
		cooldown(player, data, 1, DOME_COOLDOWN);
		ServerWorld world = player.getServerWorld();
		long now = now(player.getServer());
		int domeTicks = data.has(Talents.STASIS_LONG) ? DOME_TICKS_LONG : DOME_TICKS;
		DOMES.add(new Dome(player, world, player.getPos(), domeRadius(data), now + domeTicks, false));
		// A dome of frosted glass (real colour, so it shows by day), a clock face of sand on the ground under it
		ServerPlayerEntity follow = data.has(Talents.STASIS_FOLLOW) ? player : null;
		Fx.sphere(world, player.getPos(), (float) domeRadius(data), domeTicks, Fx.INK | ICE_RGB, follow, false);
		Fx.sigil(world, player.getPos(), follow, (float) domeRadius(data) * 0.92f, domeTicks, SAND_RGB);
		Fx.ring(world, player.getPos(), 0.5f, (float) domeRadius(data), 8, SAND_RGB);
		Fx.burst(world, player.getPos().add(0, 1, 0), (float) domeRadius(data), 8, STILL_RGB);
		Fx.shake(player, 1.0f, 8);
		// The Still Frame crown: now and then the Dome stops the whole world around for a moment
		if (data.has(Talents.STASIS_STOP) && ready(player, data, STOP_SLOT)) {
			DOMES.add(new Dome(player, world, player.getPos(), STOP_RADIUS, now + STOP_TICKS, true));
			Fx.sphere(world, player.getPos(), STOP_RADIUS, STOP_TICKS, STILL_RGB, null, true);
			Fx.screen(player, STOP_TICKS, STILL_RGB);
			Fx.burst(world, player.getPos().add(0, 1, 0), STOP_RADIUS, 10, STILL_RGB);
			Fx.sigil(world, player.getPos(), null, STOP_RADIUS * 0.6f, STOP_TICKS, STILL_RGB);
			Fx.shake(player, 2.5f, 12);
			cooldown(player, data, STOP_SLOT, STOP_COOLDOWN);
			world.playSound(null, player.getBlockPos(), SoundEvents.BLOCK_END_PORTAL_SPAWN, SoundCategory.PLAYERS, 0.5f, 2.0f);
			player.sendMessage(Text.translatable("mages_legacy.frozen.still_frame").formatted(Formatting.AQUA), true);
		}
		world.playSound(null, player.getBlockPos(), SoundEvents.BLOCK_BEACON_ACTIVATE, SoundCategory.PLAYERS, 1.0f, 1.6f);
		world.playSound(null, player.getBlockPos(), SoundEvents.BLOCK_AMETHYST_BLOCK_CHIME, SoundCategory.PLAYERS, 1.0f, 0.5f);
		return AbilityResult.OK;
	}

	/** Whether this mob counts as an enemy of the Frozen's side: a monster, or anything hunting a player. */
	private static boolean enemy(Entity e) {
		return e instanceof LivingEntity && !(e instanceof PlayerEntity) && e.isAlive()
				&& (e instanceof Monster || (e instanceof MobEntity m && m.getTarget() instanceof PlayerEntity));
	}

	private static void tickDomes(long now) {
		for (Iterator<Dome> it = DOMES.iterator(); it.hasNext(); ) {
			Dome d = it.next();
			ServerPlayerEntity owner = d.player.isRemoved() ? null : d.player;
			RaceData data = owner == null ? null : RaceState.of(owner);
			if (!d.stop && owner != null && owner.getWorld() == d.world && data.has(Talents.STASIS_FOLLOW)) {
				d.center = owner.getPos();
			}
			if (now >= d.until || owner == null) {
				release(d, owner, data);
				it.remove();
				continue;
			}
			Box box = new Box(d.center, d.center).expand(d.radius);
			for (LivingEntity e : d.world.getEntitiesByClass(LivingEntity.class, box, e -> (d.stop ? !(e instanceof PlayerEntity) && e.isAlive() : enemy(e))
					&& d.contains(e))) {
				hold(e, d.stop, data != null && data.has(Talents.STASIS_DEEP));
			}
			for (ProjectileEntity p : d.world.getEntitiesByClass(ProjectileEntity.class, box, p -> d.contains(p) && !(p.getOwner() instanceof PlayerEntity))) {
				if (!d.held.containsKey(p.getUuid())) {
					if (p.getVelocity().lengthSquared() < 1.0E-4) {
						continue;
					}
					d.held.put(p.getUuid(), new Held(p.getVelocity(), p.hasNoGravity()));
				}
				p.setNoGravity(true);
				p.setVelocity(Vec3d.ZERO);
				p.velocityModified = true;
			}
			if (now % 4 == 0) {
				shell(d);
			}
		}
	}

	/** Holds a mob nearly still (or, under the Still Frame, wholly): slowness, and its motion damped. */
	private static void hold(LivingEntity e, boolean stop, boolean deep) {
		e.addStatusEffect(new StatusEffectInstance(StatusEffects.SLOWNESS, 10, stop ? 9 : 3, true, false));
		if (stop || deep) {
			e.addStatusEffect(new StatusEffectInstance(StatusEffects.WEAKNESS, 10, stop ? 9 : 1, true, false));
		}
		e.setVelocity(e.getVelocity().multiply(stop ? 0 : 0.3, stop ? 0 : 1, stop ? 0 : 0.3));
		e.velocityModified = true;
	}

	/** Inside the Dome time stands: grains of sand hang still in the air, glinting (the shell itself is drawn by the clients). */
	private static void shell(Dome d) {
		var random = d.world.getRandom();
		int grains = d.stop ? 24 : 8;
		for (int i = 0; i < grains; i++) {
			double theta = random.nextDouble() * Math.PI * 2;
			double r = Math.sqrt(random.nextDouble()) * d.radius * 0.9;
			double y = random.nextDouble() * d.radius * 0.7;
			d.world.spawnParticles(ModParticles.STILL_SAND, d.center.x + Math.cos(theta) * r, d.center.y + 0.2 + y, d.center.z + Math.sin(theta) * r, 1, 0, 0,
					0, 0);
		}
	}

	/**
	 * The Dome ends: hanging projectiles fly on as they were (back at their shooters with the Turned Course), and
	 * with Brittleness the enemies inside take a shattering blow.
	 */
	private static void release(Dome d, @Nullable ServerPlayerEntity owner, @Nullable RaceData data) {
		boolean back = data != null && data.has(Talents.STASIS_RETURN);
		for (Map.Entry<UUID, Held> e : d.held.entrySet()) {
			if (!(d.world.getEntity(e.getKey()) instanceof ProjectileEntity p) || p.isRemoved()) {
				continue;
			}
			p.setNoGravity(e.getValue().noGravity());
			Vec3d velocity = e.getValue().velocity();
			if (back && p.getOwner() instanceof LivingEntity shooter && shooter.isAlive() && owner != null) {
				velocity = shooter.getEyePos().subtract(p.getPos()).normalize().multiply(Math.max(1.2, velocity.length()));
				p.setOwner(owner);
			}
			p.setVelocity(velocity);
			p.velocityModified = true;
		}
		if (!d.stop && owner != null && data.has(Talents.STASIS_SHATTER)) {
			for (LivingEntity e : d.world.getEntitiesByClass(LivingEntity.class, new Box(d.center, d.center).expand(d.radius), e -> enemy(e) && d.contains(e))) {
				e.damage(d.world.getDamageSources().indirectMagic(owner, owner), 6);
				d.world.spawnParticles(ParticleTypes.SNOWFLAKE, e.getX(), e.getY() + 1, e.getZ(), 10, 0.3, 0.4, 0.3, 0.05);
			}
			d.world.playSound(null, BlockPos.ofFloored(d.center), SoundEvents.BLOCK_GLASS_BREAK, SoundCategory.PLAYERS, 1.2f, 0.5f);
		}
		d.world.playSound(null, BlockPos.ofFloored(d.center), SoundEvents.BLOCK_BEACON_DEACTIVATE, SoundCategory.PLAYERS, 0.8f, 1.6f);
	}

	/** A monster died under a Dome: its Frozen learns from it. */
	public static void onDeath(LivingEntity entity) {
		if (!(entity instanceof Monster) || entity.getServer() == null) {
			return;
		}
		for (Dome d : DOMES) {
			if (!d.stop && d.contains(entity) && d.rewarded.add(entity.getUuid())) {
				ServerPlayerEntity owner = d.player;
				if (!owner.isRemoved()) {
					Progression.addXp(owner, RaceState.of(owner), 4);
				}
			}
		}
	}

	/** Stopping Gaze (sneak + V): the enemy you look at freezes still for 3 seconds. */
	public static AbilityResult freeze(ServerPlayerEntity player, RaceData data) {
		if (!ready(player, data, FREEZE_SLOT)) {
			return AbilityResult.COOLDOWN;
		}
		if (data.resource < FREEZE_COST) {
			return AbilityResult.NO_RESOURCE;
		}
		LivingEntity target = lookedAt(player, 16, Frozen::enemy);
		if (target == null) {
			return AbilityResult.NO_TARGET;
		}
		spend(player, data, FREEZE_COST);
		cooldown(player, data, FREEZE_SLOT, FREEZE_COOLDOWN);
		HELD_MOBS.put(target.getUuid(), new Hold(player.getServerWorld(), now(player.getServer()) + FREEZE_TICKS));
		// A beam of frozen time from the eye to the enemy, and the enemy sealed in ice
		ServerWorld here = player.getServerWorld();
		Fx.tendril(here, player, target, 12, STILL_RGB, ICE_RGB);
		Fx.sphere(here, target.getPos().add(0, target.getHeight() / 2, 0), Math.max(target.getWidth(), target.getHeight()) * 0.75f,
				FREEZE_TICKS, Fx.INK | ICE_RGB, null, true);
		Fx.burst(here, target.getPos().add(0, target.getHeight() / 2, 0), 1.2f + target.getWidth(), 7, STILL_RGB);
		here.spawnParticles(ICE, target.getX(), target.getY() + target.getHeight() / 2, target.getZ(), 16, 0.4, 0.4, 0.4, 0);
		player.getServerWorld().playSound(null, target.getBlockPos(), SoundEvents.BLOCK_GLASS_PLACE, SoundCategory.PLAYERS, 1.0f, 0.5f);
		return AbilityResult.OK;
	}

	private static void tickHeld(MinecraftServer server, long now) {
		HELD_MOBS.entrySet().removeIf(e -> {
			Entity entity = e.getValue().world().getEntity(e.getKey());
			if (!(entity instanceof LivingEntity living) || !living.isAlive() || e.getValue().until() < now) {
				return true;
			}
			hold(living, true, false);
			if (now % 3 == 0) {
				e.getValue().world().spawnParticles(ICE, living.getX(), living.getY() + living.getHeight() / 2, living.getZ(), 4,
						living.getWidth() / 2, living.getHeight() / 2, living.getWidth() / 2, 0);
			}
			return false;
		});
	}

	// ---- the Quickening ----

	/**
	 * Quickening: the block you look at works ten times faster for 30 seconds: furnaces, brewing stands, campfires,
	 * and anything that grows. Wide Flow takes the blocks around it too, for a minute.
	 */
	public static AbilityResult quicken(ServerPlayerEntity player, RaceData data) {
		if (!ready(player, data, 2)) {
			return AbilityResult.COOLDOWN;
		}
		int cost = QUICKEN_COST;
		if (data.resource < cost) {
			return AbilityResult.NO_RESOURCE;
		}
		ServerWorld world = player.getServerWorld();
		HitResult hit = player.raycast(QUICKEN_RANGE, 1.0f, false);
		if (!(hit instanceof BlockHitResult blockHit) || hit.getType() != HitResult.Type.BLOCK) {
			player.sendMessage(Text.translatable("mages_legacy.frozen.nothing_to_quicken"), true);
			return AbilityResult.HINTED;
		}
		BlockPos center = blockHit.getBlockPos();
		int r = data.has(Talents.FLOW_AREA) ? 1 : 0;
		long until = now(player.getServer()) + (data.has(Talents.FLOW_AREA) ? QUICKEN_TICKS_LONG : QUICKEN_TICKS);
		int added = 0;
		for (BlockPos pos : BlockPos.iterate(center.add(-r, -r, -r), center.add(r, r, r))) {
			if (quickenable(world, pos)) {
				BlockPos fixed = pos.toImmutable();
				QUICK.removeIf(q -> q.world() == world && q.pos().equals(fixed));
				QUICK.add(new Quick(player.getUuid(), world, fixed, until));
				quickChanged = true;
				world.spawnParticles(SAND, fixed.getX() + 0.5, fixed.getY() + 1.0, fixed.getZ() + 0.5, 6, 0.3, 0.1, 0.3, 0);
				added++;
			}
		}
		if (added == 0) {
			player.sendMessage(Text.translatable("mages_legacy.frozen.nothing_to_quicken"), true);
			return AbilityResult.HINTED;
		}
		// One Frozen keeps no more than 27 blocks quickened: the oldest go first
		List<Quick> mine = QUICK.stream().filter(q -> q.owner().equals(player.getUuid())).toList();
		for (int i = 0; i < mine.size() - QUICKEN_MAX; i++) {
			QUICK.remove(mine.get(i));
		}
		spend(player, data, cost);
		cooldown(player, data, 2, QUICKEN_COOLDOWN);
		Progression.addXp(player, data, 3);
		// Say what was quickened and for how long: a furnace without fuel shows nothing by itself
		long seconds = (until - now(player.getServer())) / 20;
		Text what = added > 1 ? Text.translatable("mages_legacy.frozen.quickened_many", added)
				: world.getBlockState(center).getBlock().getName();
		player.sendMessage(Text.translatable("mages_legacy.frozen.quickened", what, seconds).formatted(Formatting.GOLD), true);
		world.spawnParticles(ParticleTypes.END_ROD, center.getX() + 0.5, center.getY() + 1.1, center.getZ() + 0.5, 8, 0.3, 0.2, 0.3, 0.02);
		world.playSound(null, center, SoundEvents.BLOCK_SAND_FALL, SoundCategory.PLAYERS, 1.0f, 1.6f);
		Fx.ring(world, Vec3d.ofBottomCenter(center.up()), 0.2f, 1.6f + r, 12, SAND_RGB);
		Fx.pillar(world, Vec3d.ofBottomCenter(center.up()), 0.45f, 1.5f, 10, SAND_RGB);
		Fx.sigil(world, Vec3d.ofBottomCenter(center.up()), null, 1.1f + (float) r, 30, SAND_RGB);
		world.playSound(null, center, SoundEvents.BLOCK_AMETHYST_BLOCK_CHIME, SoundCategory.PLAYERS, 0.8f, 1.8f);
		return AbilityResult.OK;
	}

	/** Whether a block has anything to quicken: a working block entity (furnace, brewing stand) or random growth. */
	public static boolean quickenable(ServerWorld world, BlockPos pos) {
		BlockState state = world.getBlockState(pos);
		BlockEntity be = world.getBlockEntity(pos);
		return state.hasRandomTicks() || (be != null && ticker(world, state, be) != null);
	}

	@SuppressWarnings({"unchecked", "rawtypes"})
	@Nullable
	private static BlockEntityTicker ticker(World world, BlockState state, BlockEntity be) {
		return state.getBlockEntityTicker(world, (BlockEntityType) be.getType());
	}

	@SuppressWarnings({"unchecked", "rawtypes"})
	private static void tickQuick(long now) {
		for (Iterator<Quick> it = QUICK.iterator(); it.hasNext(); ) {
			Quick q = it.next();
			if (q.until() < now || !q.world().isChunkLoaded(q.pos())) {
				it.remove();
				quickChanged = true;
				continue;
			}
			BlockState state = q.world().getBlockState(q.pos());
			BlockEntity be = q.world().getBlockEntity(q.pos());
			BlockEntityTicker ticker = be == null ? null : ticker(q.world(), state, be);
			if (ticker != null) {
				for (int i = 1; i < QUICKEN_SPEED; i++) {
					ticker.tick(q.world(), q.pos(), state, be);
				}
			}
			// Growth: plants get a random tick every half second, about ten times their usual pace
			if (state.hasRandomTicks() && now % 10 == 0) {
				state.randomTick(q.world(), q.pos(), q.world().getRandom());
			}
			if (now % 5 == 0) {
				q.world().spawnParticles(SAND, q.pos().getX() + 0.5, q.pos().getY() + 1.05, q.pos().getZ() + 0.5, 3, 0.3, 0.05, 0.3, 0);
				q.world().spawnParticles(ParticleTypes.WAX_OFF, q.pos().getX() + 0.5, q.pos().getY() + 0.5, q.pos().getZ() + 0.5, 1, 0.4, 0.4, 0.4, 0);
			}
		}
	}

	/** Tells every player which blocks in their world are quickened and for how long. */
	private static void sendQuickened(MinecraftServer server, long now) {
		for (ServerPlayerEntity player : server.getPlayerManager().getPlayerList()) {
			if (!ServerPlayNetworking.canSend(player, RacePackets.QUICKENED)) {
				continue;
			}
			List<Quick> here = QUICK.stream().filter(q -> q.world() == player.getWorld()).toList();
			PacketByteBuf buf = PacketByteBufs.create();
			buf.writeVarInt(here.size());
			for (Quick q : here) {
				buf.writeBlockPos(q.pos());
				buf.writeVarInt((int) Math.max(0, q.until() - now));
			}
			ServerPlayNetworking.send(player, RacePackets.QUICKENED, buf);
		}
	}

	public static boolean quickened(ServerWorld world, BlockPos pos) {
		return QUICK.stream().anyMatch(q -> q.world() == world && q.pos().equals(pos));
	}

	/**
	 * The Current crown (sneak + G): for 30 seconds the Frozen does everything twice as fast, its abilities
	 * recharging in half the time too. Once in 5 minutes.
	 */
	public static AbilityResult current(ServerPlayerEntity player, RaceData data) {
		if (!ready(player, data, CURRENT_SLOT)) {
			return AbilityResult.COOLDOWN;
		}
		if (data.resource < CURRENT_COST) {
			return AbilityResult.NO_RESOURCE;
		}
		spend(player, data, CURRENT_COST);
		cooldown(player, data, CURRENT_SLOT, CURRENT_COOLDOWN);
		CURRENT.put(player.getUuid(), now(player.getServer()) + CURRENT_TICKS);
		player.addStatusEffect(new StatusEffectInstance(StatusEffects.SPEED, CURRENT_TICKS, 1, true, true, true));
		player.addStatusEffect(new StatusEffectInstance(StatusEffects.HASTE, CURRENT_TICKS, 1, true, true, true));
		player.addStatusEffect(new StatusEffectInstance(StatusEffects.STRENGTH, CURRENT_TICKS, 0, true, true, true));
		ServerWorld world = player.getServerWorld();
		world.spawnParticles(SAND, player.getX(), player.getY() + 1, player.getZ(), 40, 0.6, 0.8, 0.6, 0);
		world.playSound(null, player.getBlockPos(), SoundEvents.BLOCK_BEACON_POWER_SELECT, SoundCategory.PLAYERS, 1.0f, 2.0f);
		Fx.ring(world, player.getPos(), 0.5f, 6, 14, SAND_RGB);
		Fx.screen(player, CURRENT_TICKS, SAND_RGB);
		// Time pours through the body: sand winds up and out, and grains stream off it all the Current long
		Fx.aura(world, player, CURRENT_TICKS, SAND_RGB, Fx.GRAINS);
		Fx.helix(world, player, 1.2f, 2.8f, 4, false, 24, SAND_RGB);
		Fx.sigil(world, player.getPos(), player, 2.2f, 40, SAND_RGB);
		Fx.burst(world, player.getPos().add(0, 1, 0), 2.6f, 8, SAND_RGB);
		Fx.fov(player, 0.12f, 20);
		Fx.shake(player, 1.5f, 10);
		return AbilityResult.OK;
	}

	/** The race changed: its domes, quickenings and moments end. */
	public static void cleanup(ServerPlayerEntity player) {
		UUID id = player.getUuid();
		DOMES.removeIf(d -> d.owner.equals(id));
		QUICK.removeIf(q -> q.owner().equals(id));
		quickChanged = true;
		ETERNAL.remove(id);
		WAYS.remove(id);
	}
}
