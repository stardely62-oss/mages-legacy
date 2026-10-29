package euf.mageslegacy.client;

import euf.mageslegacy.net.StepPayload;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.phys.Vec3;

/**
 * The walk through the void as this client sees it. The server carries the body with a teleport every tick, which on
 * its own snaps the camera twenty times a second; here the same eased line is laid under the camera each tick, the
 * previous point as the old position, so every frame between ticks slides along it as smoothly as the first version.
 */
final class ClientStep {
	private static StepPayload step;
	private static int tick;

	private ClientStep() {
	}

	static void start(StepPayload payload) {
		step = payload;
		tick = 0;
	}

	static boolean active() {
		return step != null;
	}

	private static Vec3 at(int t) {
		double s = Math.max(0, Math.min(1, t / (double) step.ticks()));
		double eased = s * s * (3 - 2 * s);
		return step.from().lerp(step.to(), eased);
	}

	/** After the player's own tick: put the body on the line, the last point as where it was. */
	static void tick() {
		if (step == null) {
			return;
		}
		LocalPlayer player = Minecraft.getInstance().player;
		if (player == null) {
			step = null;
			return;
		}
		tick++;
		Vec3 before = at(tick - 1);
		Vec3 now = at(tick);
		player.setPos(now.x, now.y, now.z);
		player.xo = before.x;
		player.yo = before.y;
		player.zo = before.z;
		player.setDeltaMovement(Vec3.ZERO);
		player.fallDistance = 0;
		if (tick >= step.ticks()) {
			step = null;
		}
	}
}
