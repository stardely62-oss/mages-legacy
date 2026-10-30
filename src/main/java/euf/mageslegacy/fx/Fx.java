package euf.mageslegacy.fx;

import euf.mageslegacy.net.FxPayload;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.network.PacketDistributor;

/**
 * Visual effects the server asks clients to play. The world shapes are drawn with Veil shaders and GeckoLib models on
 * every client nearby;
 * the screen and camera ones (the void's dark, shakes, rushes of the field of view) only for the player they're for.
 * The shapes follow the ones the Voidborn had in the first version of the mod.
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

	/** The cloud of void motes and stars a walker leaves along the way (follows the entity). */
	public static final int STEP_TRAIL = 11;
	/** Void motes bursting out and stars glinting, at a point: a: how many motes, b: how fast. */
	public static final int SPARKLE = 12;
	/** Heavier Gravity's field around the caster (follows them): a: reach. */
	public static final int GRAVITY_FIELD = 13;
	/** The weight landing on one creature held by the field (follows it). */
	public static final int PRESS = 14;
	/** A body gone from sight for a while, armour and all (in the void, or the heart of a singularity). */
	public static final int HIDE = 15;
	/** The player's own view steps back behind them for a while (third person), then returns. */
	public static final int THIRD_PERSON = 16;
	/** The Great Bloom: the giant mushroom grown where a Mycelid stands (follows them); 0 ticks withers it now. */
	public static final int BLOOM = 17;
	/** The Spore Storm's whirl round a Mycelid (follows them): a: radius. */
	public static final int STORM = 18;
	/** A cloud of neurospores on the ground (following its Mycelid if entity is one): a: radius. */
	public static final int SPORE_CLOUD = 19;
	/** A thread of the symbiosis to one sharing it (entity), from the caster (b: the caster's id; a: the reach, only on the caster's own). */
	public static final int BOND = 20;
	/** A reserve body standing beside its node until used (from: its feet, to: the node; ticks: left to grow, a: of how many; ticks < 0: gone). */
	public static final int BODY = 21;
	/** Mycelial Rebirth: a body coming apart into spores (b: 1) or a reserve body gathering and standing up (b: 0). */
	public static final int WAKE = 22;

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
		vortex(level, around, radius, ticks, 8);
	}

	/** {@code gather}: how many of its ticks the singularity spends being born before it pulls. */
	public static void vortex(ServerLevel level, Entity around, float radius, int ticks, int gather) {
		send(level, around.position(), new FxPayload(VORTEX, around.getId(), around.position(), around.position(), ticks, radius, gather));
	}

	public static void bloom(ServerLevel level, Entity body, int ticks, float field) {
		send(level, body.position(), new FxPayload(BLOOM, body.getId(), body.position(), body.position(), ticks, field, 0));
	}

	public static void sporeCloud(ServerLevel level, Vec3 at, Entity follow, float radius, int ticks) {
		send(level, at, new FxPayload(SPORE_CLOUD, follow == null ? -1 : follow.getId(), at, at, ticks, radius, 0));
	}

	public static void bond(ServerLevel level, Entity caster, Entity ally, int ticks, float radius) {
		send(level, ally.position(), new FxPayload(BOND, ally.getId(), ally.position(), ally.position(), ticks, radius, caster.getId()));
	}

	/** A reserve body standing at its node: left of total ticks still to grow; left below 0 takes it away. */
	public static void body(ServerLevel level, Vec3 at, Vec3 node, int left, int total) {
		send(level, at, new FxPayload(BODY, -1, at, node, left, total, 0));
	}

	/** A reserve body used up: taken away for the players near and for its owner wherever they are (they died far off). */
	public static void bodyGone(ServerLevel level, ServerPlayer owner, Vec3 node) {
		FxPayload payload = new FxPayload(BODY, -1, node, node, -1, 0, 0);
		send(level, node, payload);
		if (owner.level() != level || owner.position().distanceTo(node) > RANGE) {
			PacketDistributor.sendToPlayer(owner, payload);
		}
	}

	public static void wake(ServerLevel level, Vec3 at, boolean out) {
		send(level, at, new FxPayload(WAKE, -1, at, at, 20, 0, out ? 1 : 0));
	}

	public static void storm(ServerLevel level, Entity heart, float radius, int ticks) {
		send(level, heart.position(), new FxPayload(STORM, heart.getId(), heart.position(), heart.position(), ticks, radius, 0));
	}

	public static void thirdPerson(ServerPlayer player, int ticks) {
		PacketDistributor.sendToPlayer(player, new FxPayload(THIRD_PERSON, player.getId(), player.position(), player.position(), ticks, 0, 0));
	}

	public static void muffle(ServerLevel level, Vec3 at, float radius, int ticks) {
		send(level, at, new FxPayload(MUFFLE, -1, at, at, ticks, radius, 0));
	}

	public static void muffle(ServerPlayer player, int ticks) {
		PacketDistributor.sendToPlayer(player, new FxPayload(MUFFLE, player.getId(), player.position(), player.position(), ticks, 0, 0));
	}

	public static void stepTrail(ServerLevel level, Entity walker, int ticks) {
		send(level, walker.position(), new FxPayload(STEP_TRAIL, walker.getId(), walker.position(), walker.position(), ticks, 0, 0));
	}

	public static void sparkle(ServerLevel level, Vec3 at, int motes, float speed) {
		send(level, at, new FxPayload(SPARKLE, -1, at, at, 20, motes, speed));
	}

	public static void gravityField(ServerLevel level, Entity caster, float reach, int ticks) {
		send(level, caster.position(), new FxPayload(GRAVITY_FIELD, caster.getId(), caster.position(), caster.position(), ticks, reach, 0));
	}

	public static void press(ServerLevel level, Entity target) {
		send(level, target.position(), new FxPayload(PRESS, target.getId(), target.position(), target.position(), 12, target.getBbWidth(),
				target.getBbHeight()));
	}

	public static void hide(ServerLevel level, Entity body, int ticks) {
		send(level, body.position(), new FxPayload(HIDE, body.getId(), body.position(), body.position(), ticks, 0, 0));
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
