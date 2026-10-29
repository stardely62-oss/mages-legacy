package euf.mageslegacy.voidborn;

import euf.mageslegacy.MagesLegacy;
import euf.mageslegacy.fx.Fx;
import euf.mageslegacy.race.Abilities;
import euf.mageslegacy.race.Progression;
import euf.mageslegacy.race.Race;
import euf.mageslegacy.race.RaceState;
import euf.mageslegacy.race.Talent;
import euf.mageslegacy.race.Talents;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
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
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.player.CanPlayerSleepEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.entity.player.PlayerSetSpawnEvent;
import net.neoforged.neoforge.event.level.BlockEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

/**
 * The Pocket: every Voidborn's own island in a starry void, one room of the {@code mages_legacy:pocket} dimension
 * per Voidborn, {@link #SPACING} blocks apart. Players standing within the 3×3 around the Voidborn go in with them and
 * come back with them, each to where they stood. Nothing can be built, poured or pushed past the platform's edge.
 * The Pocket is also the only place a Voidborn can sleep.
 */
public final class Pocket {
	public static final ResourceKey<Level> DIMENSION = ResourceKey.create(Registries.DIMENSION, MagesLegacy.id("pocket"));
	static final int SPACING = 512;
	/** The platform's top surface is at this height; the room's build space spans {@link #BELOW} below to {@link #ABOVE} above. */
	static final int FLOOR = 63;
	static final int BELOW = 32;
	static final int ABOVE = 64;
	/** With High Vaults. */
	static final int BELOW_TALL = 60;
	static final int ABOVE_TALL = 128;
	/** No entering for this long after taking a blow: the Pocket is a home, not a way out of a fight. */
	private static final int HURT_LOCK = 5 * 20;
	/** How far around the platform stray blocks are cleared every second. */
	private static final int GUARD = 2;
	/** The rift opens first; this many ticks later it takes the travellers through. */
	private static final int OPENING = 8;
	/** Rifts opening now: who opened them and how long until they take the travellers. */
	private static final Map<UUID, Integer> OPENING_IN = new HashMap<>();
	private static final Map<UUID, Boolean> OPENING_OUT = new HashMap<>();
	/** Two Doors: who is leaving by the second door. */
	private static final java.util.Set<UUID> FAR_OUT = new java.util.HashSet<>();
	/** Sanctuary: the hurt lock with the talent. */
	private static final int HURT_LOCK_SHORT = 2 * 20;

	private Pocket() {
	}

	public static void register() {
		NeoForge.EVENT_BUS.addListener(Pocket::onPlace);
		NeoForge.EVENT_BUS.addListener(Pocket::onPlayerTick);
		NeoForge.EVENT_BUS.addListener(Pocket::onServerTick);
		NeoForge.EVENT_BUS.addListener(Pocket::onSleep);
		NeoForge.EVENT_BUS.addListener(Pocket::onSetSpawn);
		NeoForge.EVENT_BUS.addListener(Pocket::onLogin);
		NeoForge.EVENT_BUS.addListener(Pocket::onLogout);
	}

	public static boolean isPocket(Level level) {
		return level.dimension() == DIMENSION;
	}

	static int roomAt(double x) {
		return Math.floorDiv((int) Math.floor(x) + SPACING / 2, SPACING);
	}

	static BlockPos centre(int room) {
		return new BlockPos(room * SPACING, FLOOR, 0);
	}

	/** The middle block of a Voidborn's platform (its heart). */
	public static BlockPos platformCentre(MinecraftServer server, UUID owner) {
		return centre(PocketRooms.get(server).roomOf(owner));
	}

	/** Whether a block may stand here: on or above the owner's platform, inside the room's build space. */
	public static boolean canBuild(MinecraftServer server, BlockPos pos) {
		int room = roomAt(pos.getX() + 0.5);
		UUID owner = PocketRooms.get(server).ownerOf(room);
		if (owner == null) {
			return false;
		}
		PocketRooms rooms = PocketRooms.get(server);
		int half = rooms.sizeOf(owner) / 2;
		BlockPos c = centre(room);
		int below = below(rooms, owner);
		int above = above(rooms, owner);
		return Math.abs(pos.getX() - c.getX()) <= half && Math.abs(pos.getZ() - c.getZ()) <= half && pos.getY() >= FLOOR - below
				&& pos.getY() <= FLOOR + above;
	}

	static int below(PocketRooms rooms, UUID owner) {
		return owner != null && rooms.isTall(owner) ? BELOW_TALL : BELOW;
	}

	static int above(PocketRooms rooms, UUID owner) {
		return owner != null && rooms.isTall(owner) ? ABOVE_TALL : ABOVE;
	}

	/** The platform's width from the Voidborn's talents: 5, 9, 15 or 21. */
	public static int sizeFor(ServerPlayer owner) {
		if (Talents.has(owner, Talent.POCKET_WIDE_3)) {
			return 21;
		}
		if (Talents.has(owner, Talent.POCKET_WIDE_2)) {
			return 15;
		}
		return Talents.has(owner, Talent.POCKET_WIDE_1) ? 9 : 5;
	}

	/** Brings the room in line with the talents: its size (laid out at once, nothing built is lost) and its height. */
	public static void refresh(ServerPlayer owner) {
		if (!RaceState.is(owner, Race.VOIDBORN)) {
			return;
		}
		PocketRooms rooms = PocketRooms.get(owner.server);
		rooms.setTall(owner.getUUID(), Talents.has(owner, Talent.POCKET_VAULT));
		grow(owner, sizeFor(owner));
	}

	// ---- Entering and leaving --------------------------------------------------------------------------------

	/** Opens the rift; {@link #OPENING} ticks later it takes the Voidborn and the 3×3 around them through. */
	public static Abilities.Refusal enter(ServerPlayer owner) {
		if (owner.server.getLevel(DIMENSION) == null) {
			return new Abilities.Refusal("mages_legacy.refuse.no_pocket");
		}
		if (OPENING_IN.containsKey(owner.getUUID())) {
			return new Abilities.Refusal("mages_legacy.refuse.busy");
		}
		// Sanctuary shortens the lock; Heavy Threshold lifts it while Heavier Gravity holds
		int lock = Talents.has(owner, Talent.POCKET_SANCTUARY) ? HURT_LOCK_SHORT : HURT_LOCK;
		if (Talents.has(owner, Talent.POCKET_HOLD) && Voidborn.state(owner).gravity > 0) {
			lock = 0;
		}
		if (lock > 0 && Voidborn.hurtRecently(owner, lock)) {
			return new Abilities.Refusal("mages_legacy.refuse.pocket_hurt");
		}
		ServerLevel from = owner.serverLevel();
		openDoor(owner);
		from.playSound(null, owner.getX(), owner.getY(), owner.getZ(), SoundEvents.PORTAL_TRIGGER, SoundSource.PLAYERS, 0.4f, 1.8f);
		from.playSound(null, owner.getX(), owner.getY(), owner.getZ(), SoundEvents.ENDERMAN_TELEPORT, SoundSource.PLAYERS, 0.8f, 0.6f);
		OPENING_IN.put(owner.getUUID(), OPENING);
		OPENING_OUT.put(owner.getUUID(), false);
		return null;
	}

	/** The door of the first version: a tall rift ahead of the Voidborn, the view sinking into the void as they go. */
	private static void openDoor(ServerPlayer player) {
		Vec3 ahead = player.getLookAngle().multiply(1, 0, 1);
		ahead = ahead.lengthSqr() < 1.0E-4 ? Vec3.ZERO : ahead.normalize().scale(1.2);
		Fx.rift(player.serverLevel(), player.position().add(ahead).add(0, 1.3, 0), 2.8f, 1.7f, OPENING + 18);
		Fx.voidScreen(player, OPENING * 2 + 6);
		Fx.fov(player, -0.15f, OPENING + 4);
	}

	private static void pass(ServerPlayer owner) {
		MinecraftServer server = owner.server;
		ServerLevel pocket = server.getLevel(DIMENSION);
		if (pocket == null || !owner.isAlive()) {
			return;
		}
		PocketRooms rooms = PocketRooms.get(server);
		int room = rooms.roomOf(owner.getUUID());
		rooms.setSize(owner.getUUID(), sizeFor(owner));
		buildPlatform(pocket, room, rooms.sizeOf(owner.getUUID()));
		// The 3×3 around the Voidborn goes along (5×5 with Hospitality)
		double side = Talents.has(owner, Talent.POCKET_GUESTS) ? 2.5 : 1.5;
		AABB around = new AABB(owner.getX() - side, owner.getY() - 1, owner.getZ() - side, owner.getX() + side, owner.getY() + 2, owner.getZ() + side);
		List<ServerPlayer> going = new ArrayList<>();
		going.add(owner);
		for (Player other : owner.level().getEntitiesOfClass(Player.class, around, p -> p != owner && p.isAlive() && !p.isSpectator())) {
			if (other instanceof ServerPlayer guest && !isPocket(guest.level())) {
				going.add(guest);
			}
		}
		BlockPos c = centre(room);
		for (int i = 0; i < going.size(); i++) {
			ServerPlayer player = going.get(i);
			CompoundTag back = returnTag(player, owner.getUUID());
			if (i == 0) {
				// Two Doors: the place the owner came in from last time stays as a second way out
				CompoundTag last = player.getData(RaceState.POCKET_LAST_ENTRY);
				if (last.contains("dim")) {
					back.put("far", last);
				}
				player.setData(RaceState.POCKET_LAST_ENTRY, returnTag(player, owner.getUUID()));
			}
			player.setData(RaceState.POCKET_RETURN, back);
			// The owner arrives at the middle, guests around them
			double angle = i == 0 ? 0 : (i - 1) * Math.PI / 4;
			double r = i == 0 ? 0 : 1.5;
			player.teleportTo(pocket, c.getX() + 0.5 + Math.cos(angle) * r, FLOOR + 1, c.getZ() + 0.5 + Math.sin(angle) * r, player.getYRot(),
					player.getXRot());
			player.fallDistance = 0;
		}
		// The rift closing behind on the far side, around the one who came through, stars spilling out of it
		Vec3 arrival = new Vec3(c.getX() + 0.5, FLOOR + 1, c.getZ() + 0.5);
		Fx.rift(pocket, arrival.add(0, 1.3, 0), 2.8f, 1.7f, 14);
		Fx.sparkle(pocket, arrival.add(0, 1.3, 0), 0, 0);
		pocket.playSound(null, arrival.x, arrival.y, arrival.z, SoundEvents.ENDERMAN_TELEPORT, SoundSource.PLAYERS, 0.8f, 1.2f);
	}

	/** The owner leaving takes every guest of their room home too; a Voidborn visiting leaves alone. */
	public static Abilities.Refusal leave(ServerPlayer player) {
		if (OPENING_IN.containsKey(player.getUUID())) {
			return new Abilities.Refusal("mages_legacy.refuse.busy");
		}
		openDoor(player);
		player.serverLevel().playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.PORTAL_TRIGGER, SoundSource.PLAYERS, 0.4f, 1.8f);
		player.serverLevel().playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.ENDERMAN_TELEPORT, SoundSource.PLAYERS, 0.8f,
				0.6f);
		OPENING_IN.put(player.getUUID(), OPENING);
		OPENING_OUT.put(player.getUUID(), true);
		if (player.isShiftKeyDown() && Talents.has(player, Talent.POCKET_TWO_DOORS)) {
			FAR_OUT.add(player.getUUID());
		} else {
			FAR_OUT.remove(player.getUUID());
		}
		return null;
	}

	/** Saving Rift: takes the Voidborn (and whoever stands by) into the Pocket at once; false if there is no Pocket. */
	public static boolean rescue(ServerPlayer owner) {
		if (owner.server.getLevel(DIMENSION) == null || isPocket(owner.level())) {
			return false;
		}
		OPENING_IN.remove(owner.getUUID());
		openDoor(owner);
		owner.serverLevel().playSound(null, owner.getX(), owner.getY(), owner.getZ(), SoundEvents.ENDERMAN_TELEPORT, SoundSource.PLAYERS, 1f, 0.5f);
		pass(owner);
		return true;
	}

	private static void passOut(ServerPlayer player) {
		if (!isPocket(player.level())) {
			return;
		}
		ServerLevel pocket = player.serverLevel();
		int room = roomAt(player.getX());
		UUID owner = PocketRooms.get(player.server).ownerOf(room);
		List<ServerPlayer> going = new ArrayList<>();
		going.add(player);
		if (player.getUUID().equals(owner)) {
			for (ServerPlayer other : pocket.players()) {
				if (other != player && roomAt(other.getX()) == room) {
					going.add(other);
				}
			}
		}
		boolean far = FAR_OUT.remove(player.getUUID());
		for (ServerPlayer p : going) {
			sendHome(p, far && p == player);
		}
	}

	/** Takes a player out of a Pocket if they are in one (a lost race, a reset). */
	public static void leaveIfInside(ServerPlayer player) {
		OPENING_IN.remove(player.getUUID());
		FAR_OUT.remove(player.getUUID());
		if (isPocket(player.level())) {
			passOut(player);
		}
	}

	private static CompoundTag returnTag(ServerPlayer player, UUID owner) {
		CompoundTag tag = new CompoundTag();
		tag.putString("dim", player.level().dimension().location().toString());
		tag.putDouble("x", player.getX());
		tag.putDouble("y", player.getY());
		tag.putDouble("z", player.getZ());
		tag.putFloat("yaw", player.getYRot());
		tag.putFloat("pitch", player.getXRot());
		tag.putUUID("owner", owner);
		return tag;
	}

	/** Back to where the player came from, or the nearest safe spot above or below it, or the world spawn. */
	static void sendHome(ServerPlayer player) {
		sendHome(player, false);
	}

	/** {@code far}: by the second door (Two Doors), to where the owner came in from the time before. */
	static void sendHome(ServerPlayer player, boolean far) {
		MinecraftServer server = player.server;
		CompoundTag tag = player.getData(RaceState.POCKET_RETURN);
		boolean host = tag.hasUUID("owner") && tag.getUUID("owner").equals(player.getUUID());
		if (far && tag.contains("far")) {
			tag = tag.getCompound("far");
		}
		ServerLevel level = null;
		Vec3 at = null;
		float yaw = player.getYRot();
		float pitch = player.getXRot();
		if (tag.contains("dim")) {
			level = server.getLevel(ResourceKey.create(Registries.DIMENSION, ResourceLocation.parse(tag.getString("dim"))));
			at = new Vec3(tag.getDouble("x"), tag.getDouble("y"), tag.getDouble("z"));
			yaw = tag.getFloat("yaw");
			pitch = tag.getFloat("pitch");
		}
		if (level == null || isPocket(level)) {
			level = server.overworld();
			at = null;
		}
		Vec3 safe = at == null ? null : safeNear(level, player, at);
		if (safe == null) {
			BlockPos spawn = level.getSharedSpawnPos();
			int y = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, spawn.getX(), spawn.getZ());
			safe = new Vec3(spawn.getX() + 0.5, y, spawn.getZ() + 0.5);
		}
		player.removeData(RaceState.POCKET_RETURN);
		player.teleportTo(level, safe.x, safe.y, safe.z, yaw, pitch);
		player.fallDistance = 0;
		// The Door's Recoil: what waits outside is slowed as the Voidborn steps back into the world
		if (host && Talents.has(player, Talent.DOOR_RECOIL)) {
			for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class, player.getBoundingBox().inflate(4, 2, 4),
					e -> e != player && e.isAlive() && Voidborn.isEnemy(player, e))) {
				e.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 60, 1, false, true), player);
			}
			Fx.ring(level, safe, 0.3f, 4.2f, 12);
		}
		// The Way Home: back in the world, the Void Step is ready at once
		if (host && Talents.has(player, Talent.STEP_HOMEWARD)) {
			Voidborn.state(player).cooldown[euf.mageslegacy.race.Ability.VOID_STEP.slot] = 0;
		}
		Fx.rift(level, safe.add(0, 1.3, 0), 2.8f, 1.7f, 14);
		Fx.sparkle(level, safe.add(0, 1.3, 0), 0, 0);
		level.playSound(null, safe.x, safe.y, safe.z, SoundEvents.ENDERMAN_TELEPORT, SoundSource.PLAYERS, 0.8f, 1.2f);
	}

	private static Vec3 safeNear(ServerLevel level, ServerPlayer player, Vec3 at) {
		level.getChunk(BlockPos.containing(at));
		for (int dy = 0; dy <= 16; dy++) {
			for (int sign : new int[] {1, -1}) {
				if (dy == 0 && sign < 0) {
					continue;
				}
				Vec3 c = at.add(0, dy * sign, 0);
				if (Voidborn.fits(level, player, c)) {
					return c;
				}
			}
		}
		return null;
	}

	// ---- The platform ----------------------------------------------------------------------------------------

	/** Lays the platform out to its size; a larger size only fills empty space, so nothing built is lost. */
	static void buildPlatform(ServerLevel pocket, int room, int size) {
		BlockPos c = centre(room);
		int half = size / 2;
		for (int dx = -half; dx <= half; dx++) {
			for (int dz = -half; dz <= half; dz++) {
				BlockPos pos = c.offset(dx, 0, dz);
				if (pocket.getBlockState(pos).isAir()) {
					boolean heart = dx == 0 && dz == 0;
					boolean rim = Math.abs(dx) == half || Math.abs(dz) == half;
					pocket.setBlock(pos, (heart ? Blocks.CRYING_OBSIDIAN : rim ? Blocks.OBSIDIAN : Blocks.END_STONE_BRICKS).defaultBlockState(),
							Block.UPDATE_ALL);
				}
			}
		}
	}

	/** Grows a Voidborn's platform (the talents will call this). */
	public static void grow(ServerPlayer owner, int size) {
		PocketRooms rooms = PocketRooms.get(owner.server);
		rooms.setSize(owner.getUUID(), size);
		ServerLevel pocket = owner.server.getLevel(DIMENSION);
		if (pocket != null) {
			buildPlatform(pocket, rooms.roomOf(owner.getUUID()), size);
		}
	}

	// ---- Keeping the edge ------------------------------------------------------------------------------------

	private static void onPlace(BlockEvent.EntityPlaceEvent event) {
		if (event.getLevel() instanceof ServerLevel level && isPocket(level) && !canBuild(level.getServer(), event.getPos())) {
			event.setCanceled(true);
			if (event.getEntity() instanceof ServerPlayer player) {
				Abilities.refuse(player, Component.translatable("mages_legacy.refuse.pocket_edge"));
			}
		}
	}

	private static void onServerTick(ServerTickEvent.Post event) {
		MinecraftServer server = event.getServer();
		ServerLevel pocket = server.getLevel(DIMENSION);
		if (pocket == null) {
			return;
		}
		if (server.getTickCount() % 20 == 0) {
			// Whatever got past the edge (fluids, pistons, other mods' machines) is cleared
			PocketRooms rooms = PocketRooms.get(server);
			List<Integer> seen = new ArrayList<>();
			for (ServerPlayer player : pocket.players()) {
				int room = roomAt(player.getX());
				if (!seen.contains(room)) {
					seen.add(room);
					UUID owner = rooms.ownerOf(room);
					clearEdge(pocket, room, owner == null ? 5 : rooms.sizeOf(owner), below(rooms, owner), above(rooms, owner));
				}
			}
		}
		if (server.getTickCount() % 40 == 0) {
			PocketRooms rooms = PocketRooms.get(server);
			for (ServerPlayer player : pocket.players()) {
				UUID owner = rooms.ownerOf(roomAt(player.getX()));
				if (owner == null) {
					continue;
				}
				if (owner.equals(player.getUUID())) {
					// Time at home counts towards the Rift of Space's trial
					Talents.addProgress(player, Talents.POCKET_TICKS, 40);
					if (Talents.has(player, Talent.POCKET_GARDEN)) {
						tendGarden(pocket, player);
					}
					continue;
				}
				// Hospitality: guests rest well while their host is home
				ServerPlayer host = server.getPlayerList().getPlayer(owner);
				if (host != null && isPocket(host.level()) && Talents.has(host, Talent.POCKET_GUESTS)) {
					player.addEffect(new MobEffectInstance(MobEffects.REGENERATION, 60, 0, true, true));
				}
			}
		}
		skipNightIfRested(server, pocket);
		// Rifts that have finished opening take their travellers
		for (UUID id : new ArrayList<>(OPENING_IN.keySet())) {
			int left = OPENING_IN.get(id) - 1;
			if (left > 0) {
				OPENING_IN.put(id, left);
				continue;
			}
			OPENING_IN.remove(id);
			boolean out = Boolean.TRUE.equals(OPENING_OUT.remove(id));
			ServerPlayer player = server.getPlayerList().getPlayer(id);
			if (player != null) {
				if (out) {
					passOut(player);
				} else if (!isPocket(player.level())) {
					pass(player);
				}
			}
		}
	}

	private static void clearEdge(ServerLevel pocket, int room, int size, int below, int above) {
		BlockPos c = centre(room);
		int half = size / 2;
		int outer = half + GUARD;
		BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
		for (int dx = -outer; dx <= outer; dx++) {
			for (int dz = -outer; dz <= outer; dz++) {
				if (Math.abs(dx) <= half && Math.abs(dz) <= half) {
					continue;
				}
				for (int y = FLOOR - below; y <= FLOOR + above; y++) {
					pos.set(c.getX() + dx, y, c.getZ() + dz);
					if (!pocket.getBlockState(pos).isAir()) {
						pocket.setBlock(pos, Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
					}
				}
			}
		}
	}

	/** Garden in the Void: what grows around the Voidborn at home grows about twice as fast (8 extra growth ticks every 2 s). */
	private static void tendGarden(ServerLevel pocket, ServerPlayer owner) {
		var random = owner.getRandom();
		for (int i = 0; i < 8; i++) {
			BlockPos pos = owner.blockPosition().offset(random.nextInt(9) - 4, random.nextInt(5) - 2, random.nextInt(9) - 4);
			var block = pocket.getBlockState(pos);
			if (block.getBlock() instanceof net.minecraft.world.level.block.BonemealableBlock && block.isRandomlyTicking()) {
				block.randomTick(pocket, pos, random);
			}
		}
	}

	private static void onPlayerTick(PlayerTickEvent.Post event) {
		// Hearth: at home in their own Pocket a Voidborn doesn't hunger
		if (event.getEntity() instanceof ServerPlayer player && isPocket(player.level()) && player.tickCount % 10 == 0
				&& player.getUUID().equals(PocketRooms.get(player.server).ownerOf(roomAt(player.getX())))
				&& Talents.has(player, Talent.POCKET_HEARTH)) {
			player.getFoodData().setExhaustion(0);
		}
		if (event.getEntity() instanceof ServerPlayer player && isPocket(player.level()) && player.getY() < FLOOR - BELOW) {
			// Nobody falls out of a Pocket: the void hands them back to the platform
			BlockPos c = centre(roomAt(player.getX()));
			player.teleportTo(c.getX() + 0.5, FLOOR + 1, c.getZ() + 0.5);
			player.fallDistance = 0;
		}
	}

	// ---- Sleep -----------------------------------------------------------------------------------------------

	/** Beds of the world don't hold a Voidborn; in the Pocket anyone may sleep. */
	private static void onSleep(CanPlayerSleepEvent event) {
		ServerPlayer player = event.getEntity();
		if (RaceState.is(player, Race.VOIDBORN) && !isPocket(player.level()) && event.getProblem() == null) {
			event.setProblem(Player.BedSleepingProblem.OTHER_PROBLEM);
			player.displayClientMessage(Component.translatable("mages_legacy.refuse.bed"), true);
		}
	}

	/** A bed in the Pocket is for sleeping, never a spawn point. */
	private static void onSetSpawn(PlayerSetSpawnEvent event) {
		if (event.getSpawnLevel() == DIMENSION) {
			event.setCanceled(true);
		}
	}

	/**
	 * Sleeping in a Pocket counts towards the night's skip as if the sleeper were in their bed in the world: with
	 * enough of the overworld asleep (the playersSleepingPercentage rule) the night passes.
	 */
	private static void skipNightIfRested(MinecraftServer server, ServerLevel pocket) {
		List<ServerPlayer> rested = new ArrayList<>();
		for (ServerPlayer player : pocket.players()) {
			if (player.isSleepingLongEnough()) {
				rested.add(player);
			}
		}
		if (rested.isEmpty()) {
			return;
		}
		ServerLevel overworld = server.overworld();
		if (!overworld.isNight() && !overworld.isThundering()) {
			return;
		}
		int total = rested.size();
		int sleeping = rested.size();
		for (ServerPlayer player : overworld.players()) {
			if (!player.isSpectator()) {
				total++;
				if (player.isSleepingLongEnough()) {
					sleeping++;
				}
			}
		}
		int percent = overworld.getGameRules().getInt(GameRules.RULE_PLAYERS_SLEEPING_PERCENTAGE);
		if (sleeping < Math.max(1, Mth.ceil(total * percent / 100f))) {
			return;
		}
		long time = overworld.getDayTime() + 24000L;
		overworld.setDayTime(time - time % 24000L);
		if (overworld.isRaining()) {
			overworld.setWeatherParameters(0, 0, false, false);
		}
		for (ServerPlayer player : rested) {
			player.stopSleepInBed(false, true);
			if (RaceState.is(player, Race.VOIDBORN)) {
				Progression.addXp(player, 10);
			}
			// The Void's Cradle: a full Void and a little shield for the morning
			if (Talents.has(player, Talent.POCKET_CRADLE)) {
				RaceState.setResource(player, RaceState.MAX_RESOURCE);
				player.addEffect(new MobEffectInstance(MobEffects.ABSORPTION, 5 * 60 * 20, 0, true, true));
			}
		}
		for (ServerPlayer player : overworld.players()) {
			if (player.isSleeping()) {
				player.stopSleepInBed(false, true);
			}
		}
	}

	// ---- Guests who outlast their host -----------------------------------------------------------------------

	/** A guest who comes back while their host isn't home goes home too. */
	private static void onLogin(PlayerEvent.PlayerLoggedInEvent event) {
		if (event.getEntity() instanceof ServerPlayer player && isPocket(player.level())) {
			UUID owner = PocketRooms.get(player.server).ownerOf(roomAt(player.getX()));
			ServerPlayer host = owner == null ? null : player.server.getPlayerList().getPlayer(owner);
			if (!player.getUUID().equals(owner) && (host == null || !isPocket(host.level()))) {
				sendHome(player);
			}
		}
	}

	/** A host who leaves the game sends their guests home. */
	private static void onLogout(PlayerEvent.PlayerLoggedOutEvent event) {
		OPENING_IN.remove(event.getEntity().getUUID());
		OPENING_OUT.remove(event.getEntity().getUUID());
		if (event.getEntity() instanceof ServerPlayer player && isPocket(player.level())) {
			int room = roomAt(player.getX());
			if (player.getUUID().equals(PocketRooms.get(player.server).ownerOf(room))) {
				for (ServerPlayer other : new ArrayList<>(player.serverLevel().players())) {
					if (other != player && roomAt(other.getX()) == room) {
						sendHome(other);
					}
				}
			}
		}
	}

	static boolean sameRoom(Entity a, Entity b) {
		return roomAt(a.getX()) == roomAt(b.getX());
	}
}
