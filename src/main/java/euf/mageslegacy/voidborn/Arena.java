package euf.mageslegacy.voidborn;

import euf.mageslegacy.MagesLegacy;
import euf.mageslegacy.fx.Fx;
import euf.mageslegacy.race.Abilities;
import euf.mageslegacy.race.Ability;
import euf.mageslegacy.race.Awakenings;
import euf.mageslegacy.race.RaceState;
import euf.mageslegacy.race.Talent;
import euf.mageslegacy.race.Talents;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ExperienceOrb;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.item.BucketItem;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.portal.DimensionTransition;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.common.Tags;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.event.level.BlockEvent;
import net.neoforged.neoforge.event.level.ExplosionEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

/**
 * The Awakening "Rift of Space": every allowed enemy and ally within 25 blocks is torn out of the world onto a 50×50
 * arena of the void in its own dimension ({@code mages_legacy:rift}), one arena per slot, {@link #SPACING} apart.
 * Nothing on the arena can be built or broken; pressing the Awakening again, the time running out, or its owner dying
 * or leaving ends it, and everyone left goes back to a safe spot where they were taken from; what was dropped there
 * goes back with the owner. The same arenas hold the superlate trial: five waves of the void's creatures.
 */
public final class Arena {
	public static final ResourceKey<Level> DIMENSION = ResourceKey.create(Registries.DIMENSION, MagesLegacy.id("rift"));
	private static final int SPACING = 1024;
	private static final int FLOOR = 64;
	private static final int HALF = 25;
	private static final int WALL = 40;
	private static final double REACH = 25;
	private static final int TICKS = 60 * 20;
	private static final int TICKS_LONG = 90 * 20;
	private static final int OPENING = 20;
	private static final int TRIAL_TICKS = 4 * 60 * 20;
	/** Bosses that must never be moved between worlds (a datapack can add to it): multipart and dimension-bound ones. */
	public static final net.minecraft.tags.TagKey<EntityType<?>> IMMOVABLE = net.minecraft.tags.TagKey.create(Registries.ENTITY_TYPE,
			euf.mageslegacy.MagesLegacy.id("arena_immovable"));

	/** One arena in use: whose, which slot, who was taken (with where they came from), until when. */
	private static final class Session {
		final UUID owner;
		final int slot;
		final boolean trial;
		final Map<UUID, CompoundTag> taken = new HashMap<>();
		int age;
		int length;
		int wave;
		boolean ending;
		/** The Mycelid's Trial of the Colony: the Heart's health (it fails at 0); -1 for other trials. */
		float heart = -1;

		Session(UUID owner, int slot, boolean trial, int length) {
			this.owner = owner;
			this.slot = slot;
			this.trial = trial;
			this.length = length;
		}
	}

	/** Arenas opening now: whose, and how many ticks until the rift takes everyone through. */
	private static final Map<UUID, Integer> OPENING_IN = new HashMap<>();
	private static final Map<UUID, Session> SESSIONS = new HashMap<>();

	private Arena() {
	}

	public static void register() {
		NeoForge.EVENT_BUS.addListener(Arena::onServerTick);
		NeoForge.EVENT_BUS.addListener(Arena::onBreak);
		NeoForge.EVENT_BUS.addListener(Arena::onPlace);
		NeoForge.EVENT_BUS.addListener(Arena::onExplosion);
		NeoForge.EVENT_BUS.addListener(Arena::onBucket);
		NeoForge.EVENT_BUS.addListener(Arena::onDeath);
		NeoForge.EVENT_BUS.addListener(Arena::onLogin);
		NeoForge.EVENT_BUS.addListener(Arena::onLogout);
	}

	public static boolean isArena(Level level) {
		return level.dimension() == DIMENSION;
	}

	public static boolean active(ServerPlayer owner) {
		return SESSIONS.containsKey(owner.getUUID()) || OPENING_IN.containsKey(owner.getUUID());
	}

	private static Vec3 centre(int slot) {
		return new Vec3(slot * SPACING + 0.5, FLOOR + 1, 0.5);
	}

	private static int freeSlot() {
		for (int slot = 0; ; slot++) {
			boolean used = false;
			for (Session s : SESSIONS.values()) {
				used |= s.slot == slot;
			}
			if (!used) {
				return slot;
			}
		}
	}

	// ---- The Awakening ---------------------------------------------------------------------------------------

	/** The Awakening's key: opens the rift, or ends a running arena early. */
	public static Abilities.Refusal use(ServerPlayer owner) {
		Session running = SESSIONS.get(owner.getUUID());
		if (running != null) {
			if (!running.trial) {
				running.ending = true;
			}
			return null;
		}
		if (OPENING_IN.containsKey(owner.getUUID())) {
			return new Abilities.Refusal("mages_legacy.refuse.busy");
		}
		if (owner.server.getLevel(DIMENSION) == null) {
			return new Abilities.Refusal("mages_legacy.refuse.no_pocket");
		}
		if (Pocket.isPocket(owner.level()) || isArena(owner.level())) {
			return new Abilities.Refusal("mages_legacy.refuse.arena_here");
		}
		OPENING_IN.put(owner.getUUID(), OPENING);
		ServerLevel level = owner.serverLevel();
		// A huge rift tears open above the battlefield, the ground cracks, the view sinks
		Fx.rift(level, owner.position().add(0, 4, 0), 7f, 3.2f, OPENING + 14);
		Fx.ring(level, owner.position(), 0.5f, (float) REACH, OPENING);
		Fx.shake(owner, 1.6f, OPENING);
		level.playSound(null, owner.getX(), owner.getY(), owner.getZ(), SoundEvents.END_PORTAL_SPAWN, SoundSource.PLAYERS, 0.8f, 0.7f);
		return null;
	}

	/** Whether pressing the arena's key now is free: ending a running arena costs nothing and doesn't wait. */
	public static boolean endsNow(ServerPlayer owner) {
		Session running = SESSIONS.get(owner.getUUID());
		return running != null && !running.trial;
	}

	private static void open(ServerPlayer owner) {
		ServerLevel arena = owner.server.getLevel(DIMENSION);
		if (arena == null || !owner.isAlive()) {
			return;
		}
		int length = Talents.has(owner, Talent.ARENA_LONG) ? TICKS_LONG : TICKS;
		Session session = new Session(owner.getUUID(), freeSlot(), false, length);
		build(arena, session.slot);
		sweep(arena, session.slot);
		ServerLevel from = owner.serverLevel();
		Vec3 here = owner.position();
		List<Entity> going = new ArrayList<>();
		going.add(owner);
		for (Entity e : from.getEntities(owner, new AABB(here, here).inflate(REACH), e -> e.isAlive() && e.position().distanceTo(here) <= REACH)) {
			if (e instanceof Player p && !p.isSpectator()) {
				going.add(e);
			} else if (e instanceof LivingEntity living && allowed(owner, living)) {
				going.add(e);
			}
		}
		SESSIONS.put(owner.getUUID(), session);
		Vec3 c = centre(session.slot);
		for (Entity e : going) {
			// Everyone keeps their place around the Voidborn, squeezed inside the arena
			Vec3 offset = e.position().subtract(here);
			double x = Math.max(-HALF + 2, Math.min(HALF - 3, offset.x));
			double z = Math.max(-HALF + 2, Math.min(HALF - 3, offset.z));
			Vec3 to = new Vec3(c.x + x, FLOOR + 1, c.z + z);
			take(session, e, to, arena);
		}
		Fx.rift(arena, c.add(0, 4, 0), 7f, 3.2f, 30);
		Fx.burst(arena, c.add(0, 1, 0), 6f, 12, true);
		arena.playSound(null, c.x, c.y, c.z, SoundEvents.END_PORTAL_SPAWN, SoundSource.PLAYERS, 0.8f, 1.2f);
		owner.displayClientMessage(Component.translatable("mages_legacy.message.arena", length / 20).withStyle(ChatFormatting.LIGHT_PURPLE), true);
	}

	/**
	 * Enemies and creatures that can travel. Bosses go too, unless they can't be moved safely: the ender dragon,
	 * anything made of several parts, anything that refuses to change worlds, and whatever the
	 * {@code mages_legacy:arena_immovable} tag names (for modded bosses that break when moved).
	 */
	private static boolean allowed(ServerPlayer owner, LivingEntity e) {
		if (e.getType().is(IMMOVABLE) || e instanceof net.minecraft.world.entity.boss.enderdragon.EnderDragon || e.isMultipartEntity()
				|| !e.canChangeDimensions(e.level(), e.level()) || e.isPassenger()) {
			return false;
		}
		return Voidborn.isEnemy(owner, e) || e instanceof Mob mob && mob.isLeashed() || e instanceof net.minecraft.world.entity.OwnableEntity owned
				&& owned.getOwnerUUID() != null;
	}

	private static void take(Session session, Entity e, Vec3 to, ServerLevel arena) {
		CompoundTag back = new CompoundTag();
		back.putString("dim", e.level().dimension().location().toString());
		back.putDouble("x", e.getX());
		back.putDouble("y", e.getY());
		back.putDouble("z", e.getZ());
		if (e instanceof ServerPlayer player) {
			player.setData(RaceState.ARENA_RETURN, back);
			player.teleportTo(arena, to.x, to.y, to.z, player.getYRot(), player.getXRot());
			player.fallDistance = 0;
			Fx.voidScreen(player, 14);
			session.taken.put(player.getUUID(), back);
			return;
		}
		Entity moved = e.changeDimension(new DimensionTransition(arena, to, Vec3.ZERO, e.getYRot(), e.getXRot(), DimensionTransition.DO_NOTHING));
		if (moved != null) {
			session.taken.put(moved.getUUID(), back);
		}
	}

	// ---- Running and ending ------------------------------------------------------------------------------------

	private static void onServerTick(ServerTickEvent.Post event) {
		MinecraftServer server = event.getServer();
		for (UUID id : new ArrayList<>(OPENING_IN.keySet())) {
			int left = OPENING_IN.get(id) - 1;
			if (left > 0) {
				OPENING_IN.put(id, left);
				continue;
			}
			OPENING_IN.remove(id);
			ServerPlayer owner = server.getPlayerList().getPlayer(id);
			if (owner != null) {
				open(owner);
			}
		}
		ServerLevel arena = server.getLevel(DIMENSION);
		if (arena == null) {
			return;
		}
		for (Iterator<Session> it = SESSIONS.values().iterator(); it.hasNext(); ) {
			Session session = it.next();
			session.age++;
			ServerPlayer owner = server.getPlayerList().getPlayer(session.owner);
			if (owner == null || !owner.isAlive() || !isArena(owner.level())) {
				session.ending = true;
			}
			if (session.trial && owner != null && !session.ending) {
				tickTrial(arena, owner, session);
			}
			if (!session.trial && owner != null && session.age % 40 == 0) {
				blessAllies(arena, owner, session);
			}
			if (session.age >= session.length) {
				if (session.trial && owner != null) {
					owner.displayClientMessage(Component.translatable("mages_legacy.message.trial_failed").withStyle(ChatFormatting.RED), false);
				}
				session.ending = true;
			}
			if (session.ending) {
				close(server, arena, session);
				it.remove();
			}
		}
	}

	/** The arena's talents: Regeneration, Strength and Resistance for the owner's allies (players) on it. */
	private static void blessAllies(ServerLevel arena, ServerPlayer owner, Session session) {
		for (ServerPlayer p : arena.getEntitiesOfClass(ServerPlayer.class, bounds(session.slot))) {
			if (Talents.has(owner, Talent.ARENA_REGEN)) {
				p.addEffect(new MobEffectInstance(MobEffects.REGENERATION, 60, 0, true, true));
			}
			if (Talents.has(owner, Talent.ARENA_MIGHT)) {
				p.addEffect(new MobEffectInstance(MobEffects.DAMAGE_BOOST, 60, 0, true, true));
			}
			if (Talents.has(owner, Talent.ARENA_WARD)) {
				p.addEffect(new MobEffectInstance(MobEffects.DAMAGE_RESISTANCE, 60, 0, true, true));
			}
		}
	}

	private static AABB bounds(int slot) {
		Vec3 c = centre(slot);
		return new AABB(c.x - HALF - 1, FLOOR - 20, c.z - HALF - 1, c.x + HALF + 1, FLOOR + WALL + 1, c.z + HALF + 1);
	}

	/** Everyone back where they were taken from; drops and experience go with the owner; the arena is swept. */
	private static void close(MinecraftServer server, ServerLevel arena, Session session) {
		Vec3 c = centre(session.slot);
		if (session.heart >= 0) {
			arena.removeBlock(heartPos(session.slot), false);
		}
		Fx.rift(arena, c.add(0, 4, 0), 7f, 3.2f, 20);
		ServerPlayer owner = server.getPlayerList().getPlayer(session.owner);
		CompoundTag ownerBack = session.taken.get(session.owner);
		for (Entity e : arena.getEntities((Entity) null, bounds(session.slot), Entity::isAlive)) {
			CompoundTag back = session.taken.get(e.getUUID());
			if (e instanceof ServerPlayer player) {
				sendBack(player, back);
			} else if (e instanceof ItemEntity || e instanceof ExperienceOrb) {
				// Loot won on the arena comes home with its owner
				if (ownerBack != null) {
					returnEntity(server, e, ownerBack);
				} else {
					e.discard();
				}
			} else if (back != null && !e.getTags().contains(TRIAL_TAG)) {
				returnEntity(server, e, back);
			} else {
				e.discard();
			}
		}
		Passage.closeIn(DIMENSION);
		if (owner != null && !session.trial) {
			int slot = Awakenings.slotOf(Talents.owned(owner), Ability.RIFT_ARENA);
			if (slot >= 0) {
				Voidborn.state(owner).startCooldown(slot, Abilities.cooldown(owner, Ability.RIFT_ARENA));
			}
			RaceState.sync(owner);
		}
	}

	private static void returnEntity(MinecraftServer server, Entity e, CompoundTag back) {
		ServerLevel level = server.getLevel(ResourceKey.create(Registries.DIMENSION, ResourceLocation.parse(back.getString("dim"))));
		if (level == null) {
			e.discard();
			return;
		}
		Vec3 to = new Vec3(back.getDouble("x"), back.getDouble("y"), back.getDouble("z"));
		e.changeDimension(new DimensionTransition(level, to, Vec3.ZERO, e.getYRot(), e.getXRot(), DimensionTransition.DO_NOTHING));
	}

	/** A player goes back to where they were taken from, or the nearest safe spot there, or the world spawn. */
	static void sendBack(ServerPlayer player, CompoundTag back) {
		if (back == null) {
			back = player.getData(RaceState.ARENA_RETURN);
		}
		MinecraftServer server = player.server;
		ServerLevel level = back.contains("dim") ? server.getLevel(ResourceKey.create(Registries.DIMENSION, ResourceLocation.parse(back.getString("dim"))))
				: null;
		Vec3 at = level == null ? null : new Vec3(back.getDouble("x"), back.getDouble("y"), back.getDouble("z"));
		if (level == null || isArena(level)) {
			level = server.overworld();
			at = null;
		}
		Vec3 safe = null;
		if (at != null) {
			level.getChunk(BlockPos.containing(at));
			for (int dy = 0; dy <= 16 && safe == null; dy++) {
				for (int sign : new int[] {1, -1}) {
					Vec3 c = at.add(0, dy * sign, 0);
					if (safe == null && Voidborn.fits(level, player, c)) {
						safe = c;
					}
				}
			}
		}
		if (safe == null) {
			BlockPos spawn = level.getSharedSpawnPos();
			safe = new Vec3(spawn.getX() + 0.5, level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, spawn.getX(), spawn.getZ()), spawn.getZ() + 0.5);
		}
		player.removeData(RaceState.ARENA_RETURN);
		player.teleportTo(level, safe.x, safe.y, safe.z, player.getYRot(), player.getXRot());
		player.fallDistance = 0;
		Fx.rift(level, safe.add(0, 1.3, 0), 2.8f, 1.7f, 14);
		Fx.voidScreen(player, 10);
	}

	/**
	 * Clears what an earlier arena left on this slot (a server stopped mid-fight keeps no sessions, so its creatures
	 * and drops would still be standing there): nothing but players survives into a new fight.
	 */
	private static void sweep(ServerLevel arena, int slot) {
		arena.getChunk(BlockPos.containing(centre(slot)));
		for (Entity e : arena.getEntities((Entity) null, bounds(slot), e -> !(e instanceof Player))) {
			e.discard();
		}
	}

	// ---- The arena itself --------------------------------------------------------------------------------------

	/**
	 * A 50×50 floor of obsidian with rings of crying obsidian and amethyst, blackstone at the rim, four obsidian
	 * pillars crowned with end rods at the corners; invisible walls and a ceiling keep everyone in. Built once per slot.
	 */
	private static void build(ServerLevel arena, int slot) {
		BlockPos c = BlockPos.containing(centre(slot)).below();
		if (arena.getBlockState(c).is(Blocks.CRYING_OBSIDIAN) && arena.getBlockState(c.offset(HALF, WALL, 0)).is(Blocks.BARRIER)) {
			return;
		}
		for (int dx = -HALF; dx < HALF; dx++) {
			for (int dz = -HALF; dz < HALF; dz++) {
				int ring = Math.max(Math.abs(dx), Math.abs(dz));
				BlockState floor = ring >= HALF - 1 ? Blocks.POLISHED_BLACKSTONE_BRICKS.defaultBlockState()
						: dx == 0 && dz == 0 ? Blocks.CRYING_OBSIDIAN.defaultBlockState()
						: ring % 8 == 0 ? Blocks.CRYING_OBSIDIAN.defaultBlockState()
						: (dx == 0 || dz == 0) && ring % 2 == 0 ? Blocks.AMETHYST_BLOCK.defaultBlockState()
						: Blocks.OBSIDIAN.defaultBlockState();
				arena.setBlock(c.offset(dx, 0, dz), floor, Block.UPDATE_CLIENTS);
				arena.setBlock(c.offset(dx, WALL, dz), Blocks.BARRIER.defaultBlockState(), Block.UPDATE_CLIENTS);
			}
		}
		for (int y = 1; y < WALL; y++) {
			for (int i = -HALF - 1; i <= HALF; i++) {
				arena.setBlock(c.offset(i, y, -HALF - 1), Blocks.BARRIER.defaultBlockState(), Block.UPDATE_CLIENTS);
				arena.setBlock(c.offset(i, y, HALF), Blocks.BARRIER.defaultBlockState(), Block.UPDATE_CLIENTS);
				arena.setBlock(c.offset(-HALF - 1, y, i), Blocks.BARRIER.defaultBlockState(), Block.UPDATE_CLIENTS);
				arena.setBlock(c.offset(HALF, y, i), Blocks.BARRIER.defaultBlockState(), Block.UPDATE_CLIENTS);
			}
		}
		for (int[] corner : new int[][] {{-18, -18}, {17, -18}, {-18, 17}, {17, 17}}) {
			for (int y = 1; y <= 6; y++) {
				arena.setBlock(c.offset(corner[0], y, corner[1]), Blocks.OBSIDIAN.defaultBlockState(), Block.UPDATE_CLIENTS);
			}
			arena.setBlock(c.offset(corner[0], 7, corner[1]), Blocks.END_ROD.defaultBlockState(), Block.UPDATE_CLIENTS);
		}
		// The ceiling's barrier is the marker the arena is built
		arena.setBlock(c.offset(HALF, WALL, 0), Blocks.BARRIER.defaultBlockState(), Block.UPDATE_CLIENTS);
	}

	// ---- Nothing changes on the arena --------------------------------------------------------------------------

	private static void onBreak(BlockEvent.BreakEvent event) {
		if (event.getLevel() instanceof ServerLevel level && isArena(level)) {
			event.setCanceled(true);
		}
	}

	private static void onPlace(BlockEvent.EntityPlaceEvent event) {
		if (event.getLevel() instanceof ServerLevel level && isArena(level)) {
			event.setCanceled(true);
		}
	}

	private static void onExplosion(ExplosionEvent.Detonate event) {
		if (isArena(event.getLevel())) {
			event.getAffectedBlocks().clear();
		}
	}

	private static void onBucket(PlayerInteractEvent.RightClickItem event) {
		if (isArena(event.getLevel()) && event.getItemStack().getItem() instanceof BucketItem) {
			event.setCanceled(true);
		}
	}

	// ---- Deaths, logins, logouts -------------------------------------------------------------------------------

	private static void onDeath(LivingDeathEvent event) {
		if (!(event.getEntity() instanceof ServerPlayer player)) {
			return;
		}
		Session trial = SESSIONS.get(player.getUUID());
		// The trial is never mortal: falling in it fails it instead
		if (trial != null && trial.trial && isArena(player.level())) {
			event.setCanceled(true);
			player.setHealth(1);
			player.displayClientMessage(Component.translatable("mages_legacy.message.trial_failed").withStyle(ChatFormatting.RED), false);
			trial.ending = true;
		}
	}

	/** Somebody coming back into an arena that's no longer there goes home. */
	private static void onLogin(PlayerEvent.PlayerLoggedInEvent event) {
		if (event.getEntity() instanceof ServerPlayer player && isArena(player.level())) {
			boolean held = false;
			for (Session s : SESSIONS.values()) {
				held |= s.taken.containsKey(player.getUUID());
			}
			if (!held) {
				sendBack(player, null);
			}
		}
	}

	private static void onLogout(PlayerEvent.PlayerLoggedOutEvent event) {
		OPENING_IN.remove(event.getEntity().getUUID());
		Session session = SESSIONS.get(event.getEntity().getUUID());
		if (session != null) {
			session.ending = true;
			if (event.getEntity() instanceof ServerPlayer player && player.getServer() != null) {
				ServerLevel arena = player.getServer().getLevel(DIMENSION);
				if (arena != null) {
					close(player.getServer(), arena, session);
					SESSIONS.remove(player.getUUID());
				}
			}
		}
	}

	// ---- The superlate trial -----------------------------------------------------------------------------------

	static final String TRIAL_TAG = "mages_legacy_trial";

	/**
	 * The Void's Trial, for the second Awakening: alone on an arena, five waves of the void's creatures, all to be
	 * beaten within four minutes. Falling doesn't kill, it fails the trial; it can be tried again.
	 */
	public static boolean startTrial(ServerPlayer player) {
		ServerLevel arena = player.server.getLevel(DIMENSION);
		if (arena == null || active(player) || Pocket.isPocket(player.level()) || isArena(player.level())) {
			return false;
		}
		Session session = new Session(player.getUUID(), freeSlot(), true, TRIAL_TICKS);
		build(arena, session.slot);
		sweep(arena, session.slot);
		// The Mycelid's trial is the Trial of the Colony: a Heart of the colony grows in the middle, to be kept alive
		boolean colony = euf.mageslegacy.race.RaceState.is(player, euf.mageslegacy.race.Race.MYCELID);
		if (colony) {
			session.heart = HEART;
			arena.setBlockAndUpdate(heartPos(session.slot), euf.mageslegacy.mycelid.MyceliumNodeBlock.NODE.get().defaultBlockState()
					.setValue(euf.mageslegacy.mycelid.MyceliumNodeBlock.MOTHER, true));
		}
		SESSIONS.put(player.getUUID(), session);
		Fx.rift(player.serverLevel(), player.position().add(0, 2, 0), 4f, 2f, 20);
		take(session, player, centre(session.slot).add(0, 0, 8), arena);
		player.displayClientMessage(Component.translatable(colony ? "mages_legacy.message.colony_trial_start" : "mages_legacy.message.trial_start")
				.withStyle(ChatFormatting.LIGHT_PURPLE), false);
		return true;
	}

	private static final float HEART = 100;

	private static BlockPos heartPos(int slot) {
		return BlockPos.containing(centre(slot));
	}

	/**
	 * The Heart of the colony: every creature of the trial within 2.5 blocks gnaws at it (1 health a second each);
	 * its health shows to the Mycelid; at 0 it rots and the trial fails.
	 */
	private static boolean tickHeart(ServerLevel arena, ServerPlayer player, Session session) {
		Vec3 c = Vec3.atCenterOf(heartPos(session.slot));
		if (session.age % 20 == 0) {
			int gnawing = arena.getEntitiesOfClass(Mob.class, new AABB(c, c).inflate(2.5, 2, 2.5), m -> m.isAlive() && m.getTags().contains(TRIAL_TAG)).size();
			session.heart -= gnawing;
			if (gnawing > 0) {
				arena.sendParticles(net.minecraft.core.particles.ParticleTypes.DAMAGE_INDICATOR, c.x, c.y + 0.6, c.z, gnawing * 2, 0.4, 0.3, 0.4, 0.1);
			}
			arena.sendParticles(net.minecraft.core.particles.ParticleTypes.SPORE_BLOSSOM_AIR, c.x, c.y + 1, c.z, 6, 0.6, 0.4, 0.6, 0);
			// The rot makes for the Heart; only a Mycelid standing in its way turns it aside
			for (Mob mob : arena.getEntitiesOfClass(Mob.class, bounds(session.slot), m -> m.isAlive() && m.getTags().contains(TRIAL_TAG))) {
				if (mob.distanceTo(player) > 5 && mob.distanceToSqr(c) > 4) {
					mob.setTarget(null);
					mob.getNavigation().moveTo(c.x, c.y, c.z, 1.0);
				}
			}
			player.displayClientMessage(Component.translatable("mages_legacy.message.colony_heart", Math.max(0, Math.round(session.heart)), session.wave)
					.withStyle(session.heart > 30 ? ChatFormatting.GREEN : ChatFormatting.RED), true);
		}
		if (session.heart <= 0) {
			arena.removeBlock(heartPos(session.slot), false);
			player.displayClientMessage(Component.translatable("mages_legacy.message.colony_heart_lost").withStyle(ChatFormatting.RED), false);
			session.ending = true;
			return false;
		}
		return true;
	}

	private static void tickTrial(ServerLevel arena, ServerPlayer player, Session session) {
		if (session.heart >= 0 && !tickHeart(arena, player, session)) {
			return;
		}
		if (session.age < 40) {
			return;
		}
		long alive = arena.getEntitiesOfClass(Mob.class, bounds(session.slot), m -> m.isAlive() && m.getTags().contains(TRIAL_TAG)).size();
		if (alive > 0) {
			return;
		}
		if (session.wave >= 5) {
			Talents.addProgress(player, Talents.SUPERLATE, 1);
			player.displayClientMessage(Component.translatable("mages_legacy.message.trial_passed").withStyle(ChatFormatting.GOLD), false);
			arena.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.UI_TOAST_CHALLENGE_COMPLETE, SoundSource.PLAYERS, 1f, 1f);
			session.ending = true;
			RaceState.sync(player);
			return;
		}
		session.wave++;
		Vec3 c = centre(session.slot);
		// Five waves of the void's creatures, the last the Echo of the Mage with its guard; for the colony, five waves of
		// rot that go for the Heart
		List<EntityType<? extends Mob>> wave = session.heart >= 0 ? switch (session.wave) {
			case 1 -> List.of(EntityType.ZOMBIE, EntityType.ZOMBIE, EntityType.ZOMBIE, EntityType.ZOMBIE, EntityType.SILVERFISH, EntityType.SILVERFISH);
			case 2 -> List.of(EntityType.SPIDER, EntityType.SPIDER, EntityType.CAVE_SPIDER, EntityType.CAVE_SPIDER, EntityType.CAVE_SPIDER,
					EntityType.ZOMBIE);
			case 3 -> List.of(EntityType.HUSK, EntityType.HUSK, EntityType.HUSK, EntityType.WITCH, EntityType.WITCH);
			case 4 -> List.of(EntityType.VINDICATOR, EntityType.VINDICATOR, EntityType.ZOMBIE, EntityType.ZOMBIE, EntityType.SPIDER, EntityType.WITCH);
			default -> List.of(EntityType.RAVAGER, EntityType.ZOMBIE, EntityType.ZOMBIE, EntityType.HUSK, EntityType.CAVE_SPIDER, EntityType.CAVE_SPIDER);
		} : switch (session.wave) {
			case 1 -> List.of(EntityType.ENDERMITE, EntityType.ENDERMITE, EntityType.ENDERMITE, EntityType.ENDERMITE, EntityType.ENDERMAN,
					EntityType.ENDERMAN);
			case 2 -> List.of(EntityType.ENDERMAN, EntityType.ENDERMAN, EntityType.ENDERMAN, EntityType.ENDERMAN, EntityType.PHANTOM,
					EntityType.PHANTOM);
			case 3 -> List.of(EntityType.SHULKER, EntityType.SHULKER, EntityType.ENDERMAN, EntityType.ENDERMAN, EntityType.ENDERMAN);
			case 4 -> List.of(EntityType.ENDERMAN, EntityType.ENDERMAN, EntityType.ENDERMAN, EntityType.VEX, EntityType.VEX, EntityType.VEX,
					EntityType.PHANTOM, EntityType.PHANTOM);
			default -> List.of(EntityType.WITHER_SKELETON, EntityType.ENDERMAN, EntityType.ENDERMAN, EntityType.ENDERMAN, EntityType.SHULKER);
		};
		for (int i = 0; i < wave.size(); i++) {
			Mob mob = wave.get(i).create(arena);
			if (mob == null) {
				continue;
			}
			double a = Math.PI * 2 * i / wave.size();
			Vec3 at = c.add(Math.cos(a) * 12, 0, Math.sin(a) * 12 - 4);
			mob.moveTo(at.x, at.y, at.z, 0, 0);
			mob.addTag(TRIAL_TAG);
			mob.setPersistenceRequired();
			// The colony's foes make for the Heart, not the Mycelid
			if (session.heart >= 0) {
				Vec3 heart = Vec3.atCenterOf(heartPos(session.slot));
				mob.getNavigation().moveTo(heart.x, heart.y, heart.z, 1.0);
			}
			if (mob.getType() == EntityType.RAVAGER && session.heart >= 0) {
				// The Rot that Eats: the last of the colony's trial
				mob.setCustomName(Component.translatable("mages_legacy.trial.rot"));
			}
			if (mob.getType() == EntityType.WITHER_SKELETON) {
				// The Echo of the Mage: the last of the trial
				mob.setCustomName(Component.translatable("mages_legacy.trial.echo"));
				var health = mob.getAttribute(Attributes.MAX_HEALTH);
				if (health != null) {
					health.setBaseValue(120);
				}
				mob.setHealth(120);
				var attack = mob.getAttribute(Attributes.ATTACK_DAMAGE);
				if (attack != null) {
					attack.setBaseValue(attack.getBaseValue() + 4);
				}
			}
			arena.addFreshEntity(mob);
			if (session.heart < 0) {
				mob.setTarget(player);
			}
			Fx.rift(arena, at.add(0, 1.3, 0), 2.4f, 1.3f, 16);
		}
		player.displayClientMessage(Component.translatable("mages_legacy.message.trial_wave", session.wave).withStyle(ChatFormatting.LIGHT_PURPLE),
				true);
	}

	/** Whether this projectile or creature belongs on the arena (for the sweep). */
	static boolean inArena(Entity e) {
		return isArena(e.level()) && !(e instanceof Projectile);
	}
}
