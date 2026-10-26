package euf.mageslegacy.voidborn;

import euf.mageslegacy.MagesLegacy;
import euf.mageslegacy.race.RaceData;
import euf.mageslegacy.race.RaceState;
import net.fabricmc.fabric.api.dimension.v1.FabricDimensions;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.entity.Entity;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.TeleportTarget;
import net.minecraft.world.World;

/**
 * The pocket dimension: a starry nothing where every Voidborn has a room of their own, a floating island they step
 * into and back out of to the very spot they left. Rooms lie in a row along x, far apart. Each also has a sealed
 * cell high above it, where creatures pulled out of the world by the Excision wait.
 */
public final class Pocket {
	public static final RegistryKey<World> WORLD = RegistryKey.of(RegistryKeys.WORLD, MagesLegacy.id("pocket"));
	/** Blocks between two rooms. */
	public static final int SPACING = 512;
	/** The floor's height, and the half-size of the island (9x9 inside a ring). */
	public static final int FLOOR = 64;
	public static final int HALF = 4;
	/** The cell's floor, far above the island. */
	private static final int CELL = 200;

	private Pocket() {
	}

	public static ServerWorld world(MinecraftServer server) {
		return server.getWorld(WORLD);
	}

	public static boolean in(Entity entity) {
		return entity.getWorld().getRegistryKey() == WORLD;
	}

	/** The middle of a room's floor, where one steps in. */
	public static Vec3d center(int room) {
		return new Vec3d(room * SPACING + 0.5, FLOOR + 1, 0.5);
	}

	/** Inside the sealed cell above a room. */
	public static Vec3d cell(int room) {
		return new Vec3d(room * SPACING + 0.5, CELL + 1, 0.5);
	}

	/** This player's room, given one on the first visit. */
	public static int room(ServerPlayerEntity player, RaceData data) {
		if (data.voidborn.room < 0) {
			data.voidborn.room = RaceState.get(player.getServer()).nextPocketRoom();
			RaceState.get(player.getServer()).markDirty();
		}
		return data.voidborn.room;
	}

	/**
	 * The island, laid once: a floor of purpur ringed with obsidian, crying obsidian at the corners with end rods for
	 * light. Above it, out of sight, a cell of barrier blocks.
	 */
	public static void build(ServerWorld world, int room, VoidData data) {
		BlockPos c = BlockPos.ofFloored(center(room)).down();
		world.getChunk(c);
		if (!data.built) {
			for (int dx = -HALF - 1; dx <= HALF + 1; dx++) {
				for (int dz = -HALF - 1; dz <= HALF + 1; dz++) {
					boolean edge = Math.abs(dx) == HALF + 1 || Math.abs(dz) == HALF + 1;
					boolean corner = Math.abs(dx) == HALF + 1 && Math.abs(dz) == HALF + 1;
					BlockState state = corner ? Blocks.CRYING_OBSIDIAN.getDefaultState() : edge ? Blocks.OBSIDIAN.getDefaultState()
							: Math.abs(dx) <= 1 && Math.abs(dz) <= 1 ? Blocks.POLISHED_BLACKSTONE_BRICKS.getDefaultState() : Blocks.PURPUR_BLOCK.getDefaultState();
					world.setBlockState(c.add(dx, 0, dz), state);
					if (corner) {
						world.setBlockState(c.add(dx, 1, dz), Blocks.END_ROD.getDefaultState());
					}
				}
			}
			world.setBlockState(c, Blocks.AMETHYST_BLOCK.getDefaultState());
			data.built = true;
		}
		BlockPos cell = BlockPos.ofFloored(cell(room)).down();
		world.getChunk(cell);
		if (!world.getBlockState(cell).isOf(Blocks.BARRIER)) {
			for (int dx = -2; dx <= 2; dx++) {
				for (int dy = 0; dy <= 4; dy++) {
					for (int dz = -2; dz <= 2; dz++) {
						boolean shell = Math.abs(dx) == 2 || Math.abs(dz) == 2 || dy == 0 || dy == 4;
						world.setBlockState(cell.add(dx, dy, dz), shell ? Blocks.BARRIER.getDefaultState() : Blocks.AIR.getDefaultState());
					}
				}
			}
		}
	}

	/** Into one's own room, remembering the way back. */
	public static void enter(ServerPlayerEntity player, RaceData data) {
		ServerWorld pocket = world(player.getServer());
		if (pocket == null) {
			return;
		}
		int room = room(player, data);
		build(pocket, room, data.voidborn);
		data.voidborn.returnWorld = player.getWorld().getRegistryKey();
		data.voidborn.returnPos = player.getPos();
		data.voidborn.returnYaw = player.getYaw();
		data.voidborn.returnPitch = player.getPitch();
		data.dirty = true;
		RaceState.get(player.getServer()).markDirty();
		Vec3d to = center(room);
		player.teleport(pocket, to.x, to.y, to.z, player.getYaw(), 0);
		player.fallDistance = 0;
	}

	/** Back out to where one stepped in (or the world's spawn if that world is gone). */
	public static void leave(ServerPlayerEntity player, RaceData data) {
		MinecraftServer server = player.getServer();
		VoidData v = data.voidborn;
		ServerWorld to = v.returnWorld == null ? null : server.getWorld(v.returnWorld);
		if (to == null || v.returnPos == null) {
			to = server.getOverworld();
			BlockPos spawn = to.getSpawnPos();
			player.teleport(to, spawn.getX() + 0.5, spawn.getY(), spawn.getZ() + 0.5, player.getYaw(), player.getPitch());
		} else {
			player.teleport(to, v.returnPos.x, v.returnPos.y, v.returnPos.z, v.returnYaw, v.returnPitch);
		}
		player.fallDistance = 0;
		v.returnWorld = null;
		v.returnPos = null;
		data.dirty = true;
		RaceState.get(server).markDirty();
	}

	/** Whether this player stands in their own room (and not someone else's, or the cell). */
	public static boolean inOwnRoom(ServerPlayerEntity player, RaceData data) {
		return in(player) && data.voidborn.room >= 0 && Math.abs(player.getX() - center(data.voidborn.room).x) < SPACING / 2.0;
	}

	/** Moves any entity (a player too) to another world and spot; returns the entity as it is there. */
	public static Entity move(Entity entity, ServerWorld to, Vec3d pos, float yaw) {
		to.getChunk(BlockPos.ofFloored(pos));
		if (entity instanceof ServerPlayerEntity player) {
			player.teleport(to, pos.x, pos.y, pos.z, yaw, player.getPitch());
			return player;
		}
		if (entity.getWorld() == to) {
			entity.refreshPositionAndAngles(pos.x, pos.y, pos.z, yaw, entity.getPitch());
			entity.setVelocity(Vec3d.ZERO);
			return entity;
		}
		Entity moved = FabricDimensions.teleport(entity, to, new TeleportTarget(pos, Vec3d.ZERO, yaw, entity.getPitch()));
		return moved == null ? entity : moved;
	}

	/** Falling off the island is no death: whoever drops below it comes back up onto its middle. */
	public static void catchFallers(MinecraftServer server) {
		ServerWorld pocket = world(server);
		if (pocket == null) {
			return;
		}
		for (ServerPlayerEntity player : pocket.getPlayers()) {
			if (player.getY() < FLOOR - 24) {
				int room = (int) Math.floor((player.getX() + SPACING / 2.0) / SPACING);
				Vec3d to = center(Math.max(0, room));
				player.teleport(pocket, to.x, to.y, to.z, player.getYaw(), player.getPitch());
				player.setVelocity(Vec3d.ZERO);
				player.fallDistance = 0;
			}
		}
	}
}
