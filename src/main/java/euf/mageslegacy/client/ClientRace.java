package euf.mageslegacy.client;

import euf.mageslegacy.net.SyncPayload;
import euf.mageslegacy.race.Race;

/** What the server last told this client about its own race. */
public final class ClientRace {
	private static SyncPayload state;

	private ClientRace() {
	}

	static void update(SyncPayload payload) {
		state = payload;
	}

	public static SyncPayload state() {
		return state;
	}

	public static Race race() {
		return state == null ? null : Race.byId(state.race()).orElse(null);
	}

	public static boolean stepping() {
		return state != null && state.stepping() > 0;
	}
}
