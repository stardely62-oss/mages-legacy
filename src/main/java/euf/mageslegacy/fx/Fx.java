package euf.mageslegacy.fx;

import euf.mageslegacy.net.FxPayload;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.network.PacketDistributor;

/**
 * Visual effects the server asks clients to play. The world shapes are drawn with Photon on every client nearby;
 * the screen and camera ones (the void's dark, shakes, rushes of the field of view) only for the player they're for.
 * The shapes are the ones the Voidborn had in the first version of the mod, rebuilt on Photon.
 */
public final class Fx {
	/** A tear in space facing the viewer: a: height, b: width. */
	public static final int RIFT = 0;
	/** A streak of light along a path, its tail drawing in towards the head: from, to, a: height. */
	public static final int RIBBON = 1;
	/** A swelling shell of light: a: radius; b > 0 lays dark ink under it. */
	public static final int BURST = 2;
	/** A wave spreading on the ground: a: from radius, b: to radius. */
	public static final int RING = 3;
	/** The ground cracked open onto the void (follows the entity, if any): a: radius. */
	public static final int CRACKS = 4;
	/** Motes of the void falling in towards an entity from all around: a: radius. */
	public static final int MOTES = 5;
	/** The player's own passage through the void: the view goes dark, stars rush past. */
	public static final int VOID_SCREEN = 6;
	/** The player's camera shakes: a: strength. */
	public static final int SHAKE = 7;
	/** The player's field of view springs out (or in, negative) and eases back: a: amount. */
	public static final int FOV = 8;

	/** A black hole around an entity: a core of dark, a white-hot ring, a disk spiralling in: a: radius of its pull. */
	public static final int VORTEX = 9;
	/** Sounds fall quiet and low for the players near: a: radius. */
	public static final int MUFFLE = 10;

	public static final int VOID_RGB = 0x9B7FD4;
	public static final int STAR_RGB = 0xE8E0FF;

	private static final double RANGE = 64;

	private Fx() {
	}

	private static void send(ServerLevel level, Vec3 at, FxPayload payload) {
		PacketDistributor.sendToPlayersNear(level, null, at.x, at.y, at.z, RANGE, payload);
	}

	public static void rift(ServerLevel level, Vec3 at, float height, float width, int ticks) {
		send(level, at, new FxPayload(RIFT, -1, at, at, ticks, height, width));
	}

	public static void ribbon(ServerLevel level, Vec3 from, Vec3 to, int ticks, float height) {
		send(level, from, new FxPayload(RIBBON, -1, from, to, ticks, height, 0));
	}

	public static void burst(ServerLevel level, Vec3 at, float radius, int ticks, boolean ink) {
		send(level, at, new FxPayload(BURST, -1, at, at, ticks, radius, ink ? 1 : 0));
	}

	public static void ring(ServerLevel level, Vec3 at, float from, float to, int ticks) {
		send(level, at, new FxPayload(RING, -1, at, at, ticks, from, to));
	}

	public static void cracks(ServerLevel level, Vec3 at, Entity follow, float radius, int ticks) {
		send(level, at, new FxPayload(CRACKS, follow == null ? -1 : follow.getId(), at, at, ticks, radius, 0));
	}

	public static void motes(ServerLevel level, Entity around, float radius, int ticks) {
		send(level, around.position(), new FxPayload(MOTES, around.getId(), around.position(), around.position(), ticks, radius, 0));
	}

	public static void vortex(ServerLevel level, Entity around, float radius, int ticks) {
		send(level, around.position(), new FxPayload(VORTEX, around.getId(), around.position(), around.position(), ticks, radius, 0));
	}

	public static void muffle(ServerLevel level, Vec3 at, float radius, int ticks) {
		send(level, at, new FxPayload(MUFFLE, -1, at, at, ticks, radius, 0));
	}

	public static void muffle(ServerPlayer player, int ticks) {
		PacketDistributor.sendToPlayer(player, new FxPayload(MUFFLE, player.getId(), player.position(), player.position(), ticks, 0, 0));
	}

	public static void voidScreen(ServerPlayer player, int ticks) {
		PacketDistributor.sendToPlayer(player, new FxPayload(VOID_SCREEN, player.getId(), player.position(), player.position(), ticks, 0, 0));
	}

	public static void shake(ServerPlayer player, float strength, int ticks) {
		PacketDistributor.sendToPlayer(player, new FxPayload(SHAKE, player.getId(), player.position(), player.position(), ticks, strength, 0));
	}

	public static void fov(ServerPlayer player, float amount, int ticks) {
		PacketDistributor.sendToPlayer(player, new FxPayload(FOV, player.getId(), player.position(), player.position(), ticks, amount, 0));
	}
}
