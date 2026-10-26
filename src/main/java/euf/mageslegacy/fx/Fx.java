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
