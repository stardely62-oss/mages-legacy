package euf.mageslegacy.fx;

import euf.mageslegacy.net.FxPayload;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.network.PacketDistributor;

/** Visual effects the server asks every client nearby to play (drawn with Photon on the client). */
public final class Fx {
	/** A rift tearing open where something passes through space (the Void Step's ends). */
	public static final int RIFT = 0;
	/** The large rift of the Pocket's door. */
	public static final int POCKET_RIFT = 1;
	/** The Void Step's path through the in-between: from, to. */
	public static final int STEP_PATH = 2;
	/** The in-between clinging to the walker while they cross (follows the entity). */
	public static final int STEP_VEIL = 3;
	/** Heavier gravity pressing down around the caster (follows the entity). */
	public static final int GRAVITY = 4;

	private static final double RANGE = 64;

	private Fx() {
	}

	public static void play(ServerLevel level, int kind, Entity follow, Vec3 from, Vec3 to, int ticks) {
		PacketDistributor.sendToPlayersNear(level, null, from.x, from.y, from.z, RANGE,
				new FxPayload(kind, follow == null ? -1 : follow.getId(), from, to, ticks));
	}

	public static void play(ServerLevel level, int kind, Vec3 at, int ticks) {
		play(level, kind, null, at, at, ticks);
	}
}
