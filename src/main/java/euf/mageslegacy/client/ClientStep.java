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
final class ClientStep {
	/** Ticks to fade into the void and back out of it. */
	private static final float FADE_IN = 4;
	private static final float FADE_OUT = 6;
	private static StepPayload step;
	private static int tick;
	/** Ticks since the walk ended (the fade back into the world), or -1. */
	private static int after = -1;

	private ClientStep() {
	}

	static void register() {
		NeoForge.EVENT_BUS.addListener(ClientStep::onInput);
		NeoForge.EVENT_BUS.addListener(ClientStep::onRender);
	}

	static void start(StepPayload payload) {
		step = payload;
		tick = 0;
		after = -1;
	}

	static boolean active() {
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
		// The server sets the body down at the end; the void lets go a moment later
		if (++tick >= step.ticks() + 1 || player.position().distanceTo(step.from()) > 1.5) {
			step = null;
			after = 0;
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
			VoidSky.draw(event.getModelViewMatrix(), a, time, true);
		}
	}
}
