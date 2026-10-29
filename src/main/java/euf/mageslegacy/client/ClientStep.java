package euf.mageslegacy.client;

import euf.mageslegacy.net.StepPayload;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.client.event.MovementInputUpdateEvent;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import net.neoforged.neoforge.common.NeoForge;

/**
 * The walk through the void as the walker sees it: the world fades into the void between worlds (the Pocket's sky,
 * all around), the body stays still in it with the hands off the keys, and when the server sets the walker down at
 * the far end the void fades away onto the new place. The camera never moves or shakes on its own; only the
 * player's own look turns it.
 */
public final class ClientStep {
	/** Ticks to fade into the void and back out of it. */
	private static final float FADE_IN = 4;
	private static final float FADE_OUT = 6;
	private static StepPayload step;
	private static int tick;
	/** Ticks since the walk ended (the fade back into the world), or -1. */
	private static int after = -1;
	/** The journey is through the Mycelium Network: the threads' green glow instead of the void. */
	private static boolean fungal;
	/** Which way the flight goes (the walk's own direction), kept for the fade out. */
	private static org.joml.Vector3f heading = new org.joml.Vector3f(0, 0, 1);
	/** How far the flight carries the near stars past the eye, whatever the walk's real length. */
	private static final float FLIGHT = 70;
	/** The look the step was cast with: held for the whole walk, so the view never turns on its own. */
	private static float yaw;
	private static float pitch;

	private ClientStep() {
	}

	static void register() {
		NeoForge.EVENT_BUS.addListener(ClientStep::onInput);
		NeoForge.EVENT_BUS.addListener(ClientStep::onRender);
		NeoForge.EVENT_BUS.addListener(ClientStep::onAngles);
	}

	static void start(StepPayload payload) {
		step = payload;
		tick = 0;
		after = -1;
		fungal = payload.fungal();
		LocalPlayer player = Minecraft.getInstance().player;
		if (player != null) {
			yaw = player.getYRot();
			pitch = player.getXRot();
		}
		Vec3 d = payload.to().subtract(payload.from());
		heading = d.lengthSqr() < 1.0E-4 ? new org.joml.Vector3f(0, 0, 1) : new org.joml.Vector3f((float) d.x, (float) d.y, (float) d.z).normalize();
	}

	public static boolean active() {
		return step != null;
	}

	static void tick() {
		if (after >= 0 && ++after > FADE_OUT) {
			after = -1;
		}
		if (step == null) {
			return;
		}
		LocalPlayer player = Minecraft.getInstance().player;
		if (player == null) {
			step = null;
			return;
		}
		player.setDeltaMovement(Vec3.ZERO);
		player.fallDistance = 0;
		hold(player);
		// The server sets the body down at the end; the void lets go a moment later
		if (++tick >= step.ticks() + 1 || player.position().distanceTo(step.from()) > 1.5) {
			step = null;
			after = 0;
		}
	}

	private static boolean holding() {
		return step != null || after >= 0;
	}

	/** Puts the body's look back to the one the step was cast with (the mouse turns nothing in the void). */
	private static void hold(LocalPlayer player) {
		player.setYRot(yaw);
		player.setXRot(pitch);
		player.yRotO = yaw;
		player.xRotO = pitch;
		player.setYHeadRot(yaw);
		player.yHeadRotO = yaw;
		player.yBodyRot = yaw;
		player.yBodyRotO = yaw;
	}

	/** Every frame, before the camera is placed: the held look, whatever the mouse did since the last tick. */
	private static void onAngles(net.neoforged.neoforge.client.event.ViewportEvent.ComputeCameraAngles event) {
		LocalPlayer player = Minecraft.getInstance().player;
		if (!holding() || player == null) {
			return;
		}
		hold(player);
		if (Minecraft.getInstance().options.getCameraType().isFirstPerson()) {
			event.setYaw(yaw);
			event.setPitch(pitch);
		}
	}

	/** In the void the feet find nothing to walk on. */
	private static void onInput(MovementInputUpdateEvent event) {
		if (step != null) {
			var input = event.getInput();
			input.forwardImpulse = 0;
			input.leftImpulse = 0;
			input.up = input.down = input.left = input.right = false;
			input.jumping = false;
			input.shiftKeyDown = false;
		}
	}

	private static float alpha(float partial) {
		if (step != null) {
			return Math.min(1, (tick + partial) / FADE_IN);
		}
		if (after >= 0) {
			return Math.max(0, 1 - (after + partial) / FADE_OUT);
		}
		return 0;
	}

	/** The void drawn over the whole world, after everything in it. */
	private static void onRender(RenderLevelStageEvent event) {
		if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_WEATHER) {
			return;
		}
		float partial = event.getPartialTick().getGameTimeDeltaPartialTick(false);
		float a = alpha(partial);
		if (a > 0) {
			var level = Minecraft.getInstance().level;
			float time = level == null ? 0 : level.getGameTime() + partial;
			// Speeding up out of the start, flying hard through the middle, slowing towards the exit
			float t = step != null ? Math.min(1, (tick + partial) / step.ticks()) : 1;
			float travel = FLIGHT * (1 - (float) Math.cos(Math.PI * t)) / 2;
			float speed = step == null ? 0.05f : FLIGHT * (float) Math.PI / 2 * (float) Math.sin(Math.PI * t) / Math.max(1, step.ticks());
			VoidSky.fungal = fungal;
			VoidSky.draw(event.getModelViewMatrix(), a, time, true, heading, travel, speed);
			VoidSky.fungal = false;
		}
	}
}
