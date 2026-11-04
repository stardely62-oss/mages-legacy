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
	/** The Bloodkin's: drops of blood thrown out at a point (a: spread, b: how many). */
	public static final int BLOOD = 23;
	/** A thread of blood drawn from one creature (entity) to another (b: its id), drops running along it. */
	public static final int THREAD = 24;
	/** The Blood Dash's wake behind its body (entity) from where it began (from) to where it ends (to): a: its ticks. */
	public static final int BLOOD_TRAIL = 25;
	/** The Harvest's circle closing in on its Bloodkin: a: radius. */
	public static final int BLOOD_RING = 26;
	/** The Sacrifice: blood wrung out of a body winding up round it and drunk back in (follows it). */
	public static final int SACRIFICE = 27;
	/** The player's own view edged in red for a while. */
	public static final int BLOOD_SCREEN = 28;
	/** The Feast: embers of blood rising off a body all its length (follows it). */
	public static final int BLOOD_AURA = 29;
	/** The Blood Storm's whirl round its Bloodkin (follows it): a: radius. */
	public static final int BLOOD_VORTEX = 30;
	/** The Prince of the Night: a body scattered into bats (ticks), or gathered back from them (0); b: 1 for the Last Breath. */
	public static final int BAT_SWARM = 31;
	/** A creature cut by the Blood Dash: a crescent cut across it at a point, a: the dash's yaw (degrees). */
	public static final int BLOOD_SLASH = 32;

	// ---- The Mercenaries' (client side in MercFx) ----
	/** Hunter's Brand's bolt from the hand (from) to where it lands (to) in this many ticks; entity: the shooter. */
	public static final int BRAND_BOLT = 33;
	/** The three-part seal over a branded creature (entity): a: charges lit, b: of how many; ticks left (0 takes it away). */
	public static final int BRAND_SEAL = 34;
	/** The seal folds in and bursts into golden shards at a point; b: 1 for the Chain's wider burst. */
	public static final int BRAND_BLAST = 35;
	/** The Guild Harpoon: the hook flies from its thrower (entity) to (to) in a: ticks, held b: the target's id (-1: a block); ticks: in all; 0 lets go. */
	public static final int HOOK = 36;
	/** The Verdict's seal turning over the condemned (entity), b: the judge's id; 0 takes it away. */
	public static final int VERDICT_SEAL = 37;
	/** A spectral blade swinging at the condemned (entity); a: which one. */
	public static final int VERDICT_BLADE = 38;
	/** The execution: a great golden flash, a shock wave and the seal breaking up, at the condemned (entity). */
	public static final int VERDICT_EXECUTE = 39;
	/** The Life Debt's contract seal round its ward (entity); 0 takes it away. */
	public static final int DEBT_SEAL = 40;
	/** The Debt paid off: a golden wave out from a point, a: radius. */
	public static final int DEBT_WAVE = 41;
	/** The Debt come due on its ward (entity): the seal breaks, red sparks. */
	public static final int DEBT_DUE = 42;
	/** The Last Ransom: a bright seal and coins flung from the body (entity). */
	public static final int RANSOM = 43;
	/** The Guild Legend's banner at a point: a: radius, ticks left (0 furls it); entity: its Mercenary. */
	public static final int BANNER = 44;
	/** One of the banner's blades: from the banner (from) to a foe (entity). */
	public static final int BANNER_BLADE = 45;
	/** The Mage's Shop's door before its Mercenary: a: yaw; ticks (0 swings it shut). */
	public static final int DOOR = 46;
	/** A Nose for Profit: a faint chime and a golden glint towards the quarry (to), for its Mercenary only. */
	public static final int PROFIT_PING = 47;
	/** A contract done: a stamp of the seal and coins round the body (entity). */
	public static final int CONTRACT_DONE = 48;

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

	public static void blood(ServerLevel level, Vec3 at, float spread, int count) {
		send(level, at, new FxPayload(BLOOD, -1, at, at, 20, spread, count));
	}

	public static void thread(ServerLevel level, Entity from, Entity to, int ticks) {
		send(level, from.position(), new FxPayload(THREAD, from.getId(), from.position(), to.position(), ticks, 0, to.getId()));
	}

	public static void bloodTrail(ServerLevel level, Entity body, Vec3 from, Vec3 to, int dashTicks) {
		send(level, from, new FxPayload(BLOOD_TRAIL, body.getId(), from, to, dashTicks, 0, 0));
	}

	public static void bloodSlash(ServerLevel level, Vec3 at, float yaw) {
		send(level, at, new FxPayload(BLOOD_SLASH, -1, at, at, 10, yaw, 0));
	}

	public static void bloodRing(ServerLevel level, Vec3 at, float radius, int ticks) {
		send(level, at, new FxPayload(BLOOD_RING, -1, at, at, ticks, radius, 0));
	}

	public static void sacrifice(ServerLevel level, Entity body) {
		send(level, body.position(), new FxPayload(SACRIFICE, body.getId(), body.position(), body.position(), 30, 0, 0));
	}

	public static void bloodScreen(ServerPlayer player, int ticks) {
		PacketDistributor.sendToPlayer(player, new FxPayload(BLOOD_SCREEN, player.getId(), player.position(), player.position(), ticks, 0, 0));
	}

	public static void bloodAura(ServerLevel level, Entity body, int ticks) {
		send(level, body.position(), new FxPayload(BLOOD_AURA, body.getId(), body.position(), body.position(), ticks, 0, 0));
	}

	public static void bloodVortex(ServerLevel level, Entity heart, float radius, int ticks) {
		send(level, heart.position(), new FxPayload(BLOOD_VORTEX, heart.getId(), heart.position(), heart.position(), ticks, radius, 0));
	}

	/** A body scattered into bats for this long, or (0) gathered back; the Last Breath's comes with a flash. */
	public static void batSwarm(ServerLevel level, Entity body, int ticks, boolean rescue) {
		send(level, body.position(), new FxPayload(BAT_SWARM, body.getId(), body.position(), body.position(), ticks, 0, rescue ? 1 : 0));
	}

	public static void brandBolt(ServerLevel level, Entity shooter, Vec3 from, Vec3 to, int ticks) {
		send(level, from, new FxPayload(BRAND_BOLT, shooter.getId(), from, to, ticks, 0, 0));
	}

	public static void brandSeal(ServerLevel level, Entity target, int charges, int max, int ticks) {
		send(level, target.position(), new FxPayload(BRAND_SEAL, target.getId(), target.position(), target.position(), ticks, charges, max));
	}

	public static void brandBlast(ServerLevel level, Vec3 at, boolean wide) {
		send(level, at, new FxPayload(BRAND_BLAST, -1, at, at, 16, 0, wide ? 1 : 0));
	}

	public static void hook(ServerLevel level, Entity thrower, Vec3 to, Entity target, int fly, int ticks) {
		send(level, thrower.position(), new FxPayload(HOOK, thrower.getId(), thrower.position(), to, ticks, fly, target == null ? -1 : target.getId()));
	}

	public static void hookEnd(ServerLevel level, Entity thrower) {
		send(level, thrower.position(), new FxPayload(HOOK, thrower.getId(), thrower.position(), thrower.position(), 0, 0, -1));
	}

	public static void verdictSeal(ServerLevel level, Entity target, Entity judge, int ticks) {
		send(level, target.position(), new FxPayload(VERDICT_SEAL, target.getId(), target.position(), target.position(), ticks, 0,
				judge == null ? -1 : judge.getId()));
	}

	public static void verdictBlade(ServerLevel level, Entity target, int index) {
		send(level, target.position(), new FxPayload(VERDICT_BLADE, target.getId(), target.position(), target.position(), 12, index, 0));
	}

	public static void verdictExecute(ServerLevel level, Entity target) {
		send(level, target.position(), new FxPayload(VERDICT_EXECUTE, target.getId(), target.position(), target.position(), 24, 0, 0));
	}

	public static void debtSeal(ServerLevel level, Entity ward, int ticks) {
		send(level, ward.position(), new FxPayload(DEBT_SEAL, ward.getId(), ward.position(), ward.position(), ticks, 0, 0));
	}

	public static void debtWave(ServerLevel level, Vec3 at, float radius) {
		send(level, at, new FxPayload(DEBT_WAVE, -1, at, at, 16, radius, 0));
	}

	public static void debtDue(ServerLevel level, Entity ward) {
		send(level, ward.position(), new FxPayload(DEBT_DUE, ward.getId(), ward.position(), ward.position(), 16, 0, 0));
	}

	public static void ransom(ServerLevel level, Entity body) {
		send(level, body.position(), new FxPayload(RANSOM, body.getId(), body.position(), body.position(), 30, 0, 0));
	}

	public static void banner(ServerLevel level, Entity owner, Vec3 at, float radius, int ticks) {
		send(level, at, new FxPayload(BANNER, owner.getId(), at, at, ticks, radius, 0));
	}

	public static void bannerEnd(ServerLevel level, Entity owner, Vec3 at) {
		send(level, at, new FxPayload(BANNER, owner == null ? -1 : owner.getId(), at, at, 0, 0, 0));
	}

	public static void bannerBlade(ServerLevel level, Vec3 from, Entity target) {
		send(level, from, new FxPayload(BANNER_BLADE, target.getId(), from, target.position(), 12, 0, 0));
	}

	public static void door(ServerLevel level, Vec3 at, float yaw, int ticks) {
		send(level, at, new FxPayload(DOOR, -1, at, at, ticks, yaw, 0));
	}

	/** The door of whoever closes the book (the clients find the open door nearest to them). */
	public static void doorClose(ServerLevel level, Entity by) {
		send(level, by.position(), new FxPayload(DOOR, by.getId(), by.position(), by.position(), 0, 0, 0));
	}

	public static void profitPing(ServerPlayer player, Vec3 quarry) {
		PacketDistributor.sendToPlayer(player, new FxPayload(PROFIT_PING, player.getId(), player.position(), quarry, 30, 0, 0));
	}

	public static void contractDone(ServerLevel level, Entity body) {
		send(level, body.position(), new FxPayload(CONTRACT_DONE, body.getId(), body.position(), body.position(), 30, 0, 0));
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
