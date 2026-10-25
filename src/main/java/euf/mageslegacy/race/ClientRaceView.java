package euf.mageslegacy.race;

import net.minecraft.entity.player.PlayerEntity;

/**
 * What common code may ask about the client's own race. The client sets it from the sync packet; on a dedicated
 * server it simply stays empty.
 */
public final class ClientRaceView {
	public static volatile Race race;

	private ClientRaceView() {
	}

	public static boolean isMycelid(PlayerEntity player) {
		return race == Race.MYCELID;
	}
}
