package euf.mageslegacy.voidborn;

import euf.mageslegacy.MagesLegacy;
import euf.mageslegacy.race.RaceData;
import euf.mageslegacy.race.RaceState;
import euf.mageslegacy.race.Talents;
import java.util.List;
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
	/** The floor's height, and the half-size of the island: 9x9 inside a ring, 15x15 with Wider Pocket. */
	public static final int FLOOR = 64;
	public static final int HALF = 4;
	public static final int HALF_WIDE = 7;
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

	/** How far this Voidborn's island reaches from its middle. */
	public static int half(RaceData data) {
		return data.has(Talents.POCKET_WIDE) ? HALF_WIDE : HALF;
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
	 * The island, laid once and widened later if it must: a floor of purpur ringed with obsidian, crying obsidian at
	 * the corners with end rods for light, amethyst in the middle. Widening takes away only the old ring and its rods
	 * (whatever was built on the island stays). Above it, out of sight, a cell of barrier blocks.
	 */
	public static void build(ServerWorld world, int room, VoidData data, int half) {
		BlockPos c = BlockPos.ofFloored(center(room)).down();
		world.getChunk(c);
		if (data.builtHalf < half) {
			int old = data.builtHalf;
			if (old > 0) {
				for (int dx = -old - 1; dx <= old + 1; dx++) {
					for (int dz = -old - 1; dz <= old + 1; dz++) {
						if (Math.max(Math.abs(dx), Math.abs(dz)) == old + 1) {
							world.setBlockState(c.add(dx, 0, dz), Blocks.PURPUR_BLOCK.getDefaultState());
							if (world.getBlockState(c.add(dx, 1, dz)).isOf(Blocks.END_ROD)) {
								world.removeBlock(c.add(dx, 1, dz), false);
							}
						}
					}
				}
			}
			for (int dx = -half - 1; dx <= half + 1; dx++) {
				for (int dz = -half - 1; dz <= half + 1; dz++) {
					int ring = Math.max(Math.abs(dx), Math.abs(dz));
					if (old > 0 && ring <= old + 1) {
						continue;
					}
					boolean edge = ring == half + 1;
					boolean corner = Math.abs(dx) == half + 1 && Math.abs(dz) == half + 1;
					BlockState state = corner ? Blocks.CRYING_OBSIDIAN.getDefaultState() : edge ? Blocks.OBSIDIAN.getDefaultState()
							: ring <= 1 ? Blocks.POLISHED_BLACKSTONE_BRICKS.getDefaultState() : Blocks.PURPUR_BLOCK.getDefaultState();
					world.setBlockState(c.add(dx, 0, dz), state);
					if (corner) {
						world.setBlockState(c.add(dx, 1, dz), Blocks.END_ROD.getDefaultState());
					}
				}
			}
			if (old == 0) {
				world.setBlockState(c, Blocks.AMETHYST_BLOCK.getDefaultState());
			}
			data.builtHalf = half;
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

	/** Into one's own room, remembering the way back; with Guests, the players standing close come along. */
	public static void enter(ServerPlayerEntity player, RaceData data) {
		ServerWorld pocket = world(player.getServer());
		if (pocket == null) {
			return;
		}
		int room = room(player, data);
		build(pocket, room, data.voidborn, half(data));
		List<ServerPlayerEntity> guests = !data.has(Talents.POCKET_GUESTS) ? List.of()
				: player.getServerWorld().getPlayers(p -> p != player && p.isAlive() && !p.isSpectator() && p.squaredDistanceTo(player) < 9);
		data.voidborn.returnWorld = player.getWorld().getRegistryKey();
		data.voidborn.returnPos = player.getPos();
		data.voidborn.returnYaw = player.getYaw();
		data.voidborn.returnPitch = player.getPitch();
		data.dirty = true;
		RaceState.get(player.getServer()).markDirty();
		Vec3d to = center(room);
		player.teleport(pocket, to.x, to.y, to.z, player.getYaw(), 0);
		player.fallDistance = 0;
		for (int i = 0; i < guests.size(); i++) {
			double a = Math.PI * 2 * i / Math.max(1, guests.size());
			guests.get(i).teleport(pocket, to.x + Math.cos(a) * 2, to.y, to.z + Math.sin(a) * 2, guests.get(i).getYaw(), 0);
		}
	}

	/**
	 * Back out to where one stepped in (or the world's spawn if that world is gone); {@code home} (Way Home) goes to
	 * one's own spawn point instead. Guests in the room come out with their host.
	 */
	public static void leave(ServerPlayerEntity player, RaceData data, boolean home) {
		MinecraftServer server = player.getServer();
		VoidData v = data.voidborn;
		List<ServerPlayerEntity> guests = player.getServerWorld().getPlayers(p -> p != player && inRoom(p, v.room));
		ServerWorld to = v.returnWorld == null ? null : server.getWorld(v.returnWorld);
		if (home && server.getWorld(player.getSpawnPointDimension()) != null && player.getSpawnPointPosition() != null) {
			ServerWorld spawnWorld = server.getWorld(player.getSpawnPointDimension());
			BlockPos spawn = player.getSpawnPointPosition();
			Vec3d at = net.minecraft.entity.player.PlayerEntity.findRespawnPosition(spawnWorld, spawn, player.getSpawnAngle(), player.isSpawnForced(), true)
					.orElse(Vec3d.ofBottomCenter(spawnWorld.getSpawnPos()));
			player.teleport(spawnWorld, at.x, at.y, at.z, player.getYaw(), player.getPitch());
		} else if (to == null || v.returnPos == null) {
			to = server.getOverworld();
			BlockPos spawn = to.getSpawnPos();
			player.teleport(to, spawn.getX() + 0.5, spawn.getY(), spawn.getZ() + 0.5, player.getYaw(), player.getPitch());
		} else {
			player.teleport(to, v.returnPos.x, v.returnPos.y, v.returnPos.z, v.returnYaw, v.returnPitch);
		}
		player.fallDistance = 0;
		for (ServerPlayerEntity guest : guests) {
			guest.teleport(player.getServerWorld(), player.getX(), player.getY(), player.getZ(), guest.getYaw(), guest.getPitch());
			guest.fallDistance = 0;
		}
		v.returnWorld = null;
		v.returnPos = null;
		data.dirty = true;
		RaceState.get(server).markDirty();
	}

	/** Whether this player stands in their own room (and not someone else's, or the cell). */
	public static boolean inOwnRoom(ServerPlayerEntity player, RaceData data) {
		return inRoom(player, data.voidborn.room);
	}

	/** Whether an entity is in the pocket, within a given room's stretch of it. */
	public static boolean inRoom(Entity entity, int room) {
		return in(entity) && room >= 0 && Math.abs(entity.getX() - center(room).x) < SPACING / 2.0;
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
