package euf.mageslegacy.client;

import euf.mageslegacy.net.SyncPayload;
import euf.mageslegacy.race.Race;

/** What the server last told this client about its own race. */
public final class ClientRace {
	private static SyncPayload state;
	/** Ticks left of a walk through the void, counted down here between the server's updates. */
	private static int stepLeft;

	private ClientRace() {
	}

	static void update(SyncPayload payload) {
		state = payload;
		if (payload.stepping() == 0 || stepLeft == 0 || Math.abs(payload.stepping() - stepLeft) > 2) {
			stepLeft = payload.stepping();
		}
	}

	static void tick() {
		if (stepLeft > 0) {
			stepLeft--;
		}
	}

	public static int stepLeft() {
		return stepLeft;
	}

	public static SyncPayload state() {
		return state;
	}

	public static Race race() {
		return state == null ? null : Race.byId(state.race()).orElse(null);
	}

	public static boolean stepping() {
		return stepLeft > 0;
	}
}
