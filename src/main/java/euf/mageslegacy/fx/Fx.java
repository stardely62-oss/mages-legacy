package euf.mageslegacy.fx;

import euf.mageslegacy.net.RacePackets;
import java.util.ArrayList;
import java.util.List;
import net.fabricmc.fabric.api.networking.v1.PacketByteBufs;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.entity.Entity;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.Vec3d;

/**
 * Ability effects drawn by the clients as glowing geometry particles can't make: rings that spread, domes, ribbons
 * of light along a path, pillars, and a tint over one player's own screen. The server only says what and where.
 */
public final class Fx {
	public static final int RING = 0;
	public static final int SPHERE = 1;
	public static final int RIBBON = 2;
	public static final int PILLAR = 3;
	public static final int SCREEN = 4;
	public static final int FLIGHT = 5;
	public static final int SLASH = 6;
	public static final int TENDRIL = 7;
	public static final int SIGIL = 8;
	public static final int HELIX = 9;
	public static final int GHOSTS = 10;
	public static final int SHAKE = 11;
	public static final int FOV = 12;
	public static final int PULSE = 13;
	public static final int AURA = 14;
	public static final int SWARM = 15;
	public static final int BURST = 16;
	/**
	 * A flag on a colour: lay it on as real colour under its glow, the way blood should look, rather than glow alone
	 * (which a bright day washes to pink, and which can't show a dark colour at all).
	 */
	public static final int INK = 0x1000000;
	/** How far away players still see an effect. */
	private static final double RANGE = 64;

	private Fx() {
	}

	/** A flat ring on the ground spreading from {@code radius0} to {@code radius1} and fading. */
	public static void ring(ServerWorld world, Vec3d at, float radius0, float radius1, int ticks, int rgb) {
		PacketByteBuf buf = start(RING, ticks, rgb);
		writeVec(buf, at);
		buf.writeFloat(radius0);
		buf.writeFloat(radius1);
		send(world, at, buf);
	}

	/**
	 * A glowing dome that grows in, holds and fades away; {@code follow} is the entity it rides with (or null), and
	 * {@code full} draws a whole sphere instead of the upper half.
	 */
	public static void sphere(ServerWorld world, Vec3d at, float radius, int ticks, int rgb, Entity follow, boolean full) {
		PacketByteBuf buf = start(SPHERE, ticks, rgb);
		writeVec(buf, at);
		buf.writeFloat(radius);
		buf.writeVarInt(follow == null ? -1 : follow.getId());
		buf.writeBoolean(full);
		send(world, at, buf);
	}

	/** A ribbon of light along {@code path} (oldest first), shading from {@code rgb} to {@code rgb2}, then fading. */
	public static void ribbon(ServerWorld world, List<Vec3d> path, int ticks, int rgb, int rgb2, float height) {
		if (path.size() < 2) {
			return;
		}
		PacketByteBuf buf = start(RIBBON, ticks, rgb);
		buf.writeInt(rgb2);
		buf.writeFloat(height);
		// Twenty-odd points are plenty for a smooth streak; the last one always goes in
		int step = Math.max(1, path.size() / 20);
		List<Vec3d> points = new ArrayList<>();
		for (int i = 0; i < path.size(); i += step) {
			points.add(path.get(i));
		}
		if (points.get(points.size() - 1) != path.get(path.size() - 1)) {
			points.add(path.get(path.size() - 1));
		}
		buf.writeVarInt(points.size());
		points.forEach(p -> writeVec(buf, p));
		send(world, path.get(0), buf);
	}

	/** A pillar of light rising from {@code at}. */
	public static void pillar(ServerWorld world, Vec3d at, float radius, float height, int ticks, int rgb) {
		PacketByteBuf buf = start(PILLAR, ticks, rgb);
		writeVec(buf, at);
		buf.writeFloat(radius);
		buf.writeFloat(height);
		send(world, at, buf);
	}

	/** A tint over this one player's screen edges (a flash at its start). */
	public static void screen(ServerPlayerEntity player, int ticks, int rgb) {
		if (ServerPlayNetworking.canSend(player, RacePackets.FX)) {
			ServerPlayNetworking.send(player, RacePackets.FX, start(SCREEN, ticks, rgb));
		}
	}

	/**
	 * This player is about to be carried along a path by the server for {@code ticks}: its client smooths the
	 * movement between the server's steps instead of snapping to each.
	 */
	public static void flight(ServerPlayerEntity player, int ticks) {
		if (ServerPlayNetworking.canSend(player, RacePackets.FX)) {
			ServerPlayNetworking.send(player, RacePackets.FX, start(FLIGHT, ticks, 0));
		}
	}

	/**
	 * A crescent cut through {@code at}: it sweeps across along {@code dir} in a flash, a white-hot edge on a band of
	 * colour, and fades as it widens. {@code roll} turns it from flat (0) to upright (90 degrees).
	 */
	public static void slash(ServerWorld world, Vec3d at, Vec3d dir, float size, float roll, int ticks, int rgb) {
		PacketByteBuf buf = start(SLASH, ticks, rgb);
		writeVec(buf, at);
		writeVec(buf, dir);
		buf.writeFloat(size);
		buf.writeFloat(roll);
		send(world, at, buf);
	}

	/**
	 * A living stream from one entity to another: a winding ribbon, thick at its source and thin where it arrives,
	 * with bright pulses running along it.
	 */
	public static void tendril(ServerWorld world, Entity from, Entity to, int ticks, int rgb, int rgb2) {
		PacketByteBuf buf = start(TENDRIL, ticks, rgb);
		buf.writeInt(rgb2);
		buf.writeVarInt(from.getId());
		buf.writeVarInt(to.getId());
		send(world, from.getPos(), buf);
	}

	/** A circle of runes on the ground that springs open, slowly turns and beats with light, riding with {@code follow} if given. */
	public static void sigil(ServerWorld world, Vec3d at, Entity follow, float radius, int ticks, int rgb) {
		PacketByteBuf buf = start(SIGIL, ticks, rgb);
		writeVec(buf, at);
		buf.writeVarInt(follow == null ? -1 : follow.getId());
		buf.writeFloat(radius);
		send(world, at, buf);
	}

	/** Strands of light that wind up around an entity, rising, then draw into it ({@code inward}) or fly apart. */
	public static void helix(ServerWorld world, Entity around, float radius, float height, int strands, boolean inward, int ticks, int rgb) {
		PacketByteBuf buf = start(HELIX, ticks, rgb);
		buf.writeVarInt(around.getId());
		buf.writeFloat(radius);
		buf.writeFloat(height);
		buf.writeVarInt(strands);
		buf.writeBoolean(inward);
		send(world, around.getPos(), buf);
	}

	/**
	 * Glowing afterimages of a body left along {@code path}, facing {@code yaw}, the oldest fading first. They stay
	 * behind the body: none where it ends up.
	 */
	public static void ghosts(ServerWorld world, List<Vec3d> path, float yaw, int count, int ticks, int rgb) {
		if (path.size() < 2) {
			return;
		}
		PacketByteBuf buf = start(GHOSTS, ticks, rgb);
		buf.writeFloat(yaw);
		buf.writeVarInt(count);
		for (int i = 0; i < count; i++) {
			writeVec(buf, path.get((int) ((path.size() - 1) * 0.75 * (i + 0.5) / count)));
		}
		send(world, path.get(0), buf);
	}

	/** A burst: a shell that flashes out from {@code at} to {@code radius} in a few ticks and is gone. */
	public static void burst(ServerWorld world, Vec3d at, float radius, int ticks, int rgb) {
		PacketByteBuf buf = start(BURST, ticks, rgb);
		writeVec(buf, at);
		buf.writeFloat(radius);
		send(world, at, buf);
	}

	/** What an aura gives off: crimson embers, grains of time, glowing spores, spirit wisps, golden motes. */
	public static final int EMBERS = 0;
	public static final int GRAINS = 1;
	public static final int SPORES = 2;
	public static final int WISPS = 3;
	public static final int MOTES = 4;

	/** Particles of {@code kind} rising off an entity for {@code ticks}, made by the clients themselves. */
	public static void aura(ServerWorld world, Entity around, int ticks, int rgb, int kind) {
		PacketByteBuf buf = start(AURA, ticks, rgb);
		buf.writeVarInt(around.getId());
		buf.writeVarInt(kind);
		send(world, around.getPos(), buf);
	}

	/** {@code count} bats circling an entity for {@code ticks}: a body scattered into a swarm. */
	public static void swarm(ServerWorld world, Entity around, int count, int ticks) {
		PacketByteBuf buf = start(SWARM, ticks, 0);
		buf.writeVarInt(around.getId());
		buf.writeVarInt(count);
		send(world, around.getPos(), buf);
	}

	/** This player's camera shakes, {@code strength} degrees at first, dying away over {@code ticks}. */
	public static void shake(ServerPlayerEntity player, float strength, int ticks) {
		sendTo(player, start(SHAKE, ticks, 0), buf -> buf.writeFloat(strength));
	}

	/** This player's view widens by {@code amount} (0.2 is a fifth) at once and eases back over {@code ticks}: a rush. */
	public static void fov(ServerPlayerEntity player, float amount, int ticks) {
		sendTo(player, start(FOV, ticks, 0), buf -> buf.writeFloat(amount));
	}

	/** This player's screen edges beat like a heart, {@code beats} times over {@code ticks}. */
	public static void pulse(ServerPlayerEntity player, int beats, int ticks, int rgb) {
		sendTo(player, start(PULSE, ticks, rgb), buf -> buf.writeVarInt(beats));
	}

	private static void sendTo(ServerPlayerEntity player, PacketByteBuf buf, java.util.function.Consumer<PacketByteBuf> rest) {
		if (ServerPlayNetworking.canSend(player, RacePackets.FX)) {
			rest.accept(buf);
			ServerPlayNetworking.send(player, RacePackets.FX, buf);
		}
	}

	private static PacketByteBuf start(int kind, int ticks, int rgb) {
		PacketByteBuf buf = PacketByteBufs.create();
		buf.writeVarInt(kind);
		buf.writeVarInt(ticks);
		buf.writeInt(rgb);
		return buf;
	}

	private static void writeVec(PacketByteBuf buf, Vec3d v) {
		buf.writeDouble(v.x);
		buf.writeDouble(v.y);
		buf.writeDouble(v.z);
	}

	private static void send(ServerWorld world, Vec3d at, PacketByteBuf buf) {
		for (ServerPlayerEntity player : world.getPlayers()) {
			if (player.squaredDistanceTo(at) < RANGE * RANGE && ServerPlayNetworking.canSend(player, RacePackets.FX)) {
				ServerPlayNetworking.send(player, RacePackets.FX, PacketByteBufs.copy(buf));
			}
		}
	}
}
