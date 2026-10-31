package euf.mageslegacy.mycelid;

import java.util.HashMap;
import java.util.Map;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.OwnableEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.scores.PlayerTeam;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.living.LivingDamageEvent;

/**
 * Whom a Mycelid's gifts reach (Neurosymbiosis, the Great Bloom, the Spore Storm, Healing Nodes, Fungal Larders): the
 * Mycelid, their own pets and the players on their side. Nothing to set up for a cooperative server: without teams and
 * with PvP on, every player is an ally until the two come to blows (then not for 30 s). Minecraft teams decide when
 * there are any: one team are allies (friendly fire or not), different teams are not, nor a player outside a team
 * while PvP is on. With PvP off on the server everyone outside another team is an ally.
 */
public final class Allies {
	private static final int CLASH_TICKS = 30 * 20;
	/** Two players who hurt each other ("a|b", sorted): the game time of the last blow. */
	private static final Map<String, Long> CLASHES = new HashMap<>();

	private Allies() {
	}

	public static void register() {
		NeoForge.EVENT_BUS.addListener(Allies::onDamaged);
	}

	/** Whether a Mycelid's gift reaches this creature. */
	public static boolean ally(ServerPlayer caster, LivingEntity e) {
		if (e == caster) {
			return true;
		}
		if (e instanceof OwnableEntity owned && owned.getOwnerUUID() != null) {
			return caster.getUUID().equals(owned.getOwnerUUID());
		}
		return e instanceof Player p && !p.isSpectator() && friendly(caster, p);
	}

	/** Whether two players are on one side. */
	public static boolean friendly(ServerPlayer a, Player b) {
		PlayerTeam ta = a.getTeam();
		PlayerTeam tb = b.getTeam();
		if (ta != null && tb != null) {
			return ta.isAlliedTo(tb);
		}
		if (!a.server.isPvpAllowed()) {
			return true;
		}
		if (ta != null || tb != null) {
			return false;
		}
		Long last = CLASHES.get(pair(a, b));
		return last == null || a.level().getGameTime() - last > CLASH_TICKS;
	}

	private static String pair(Player a, Player b) {
		String x = a.getStringUUID();
		String y = b.getStringUUID();
		return x.compareTo(y) < 0 ? x + "|" + y : y + "|" + x;
	}

	/** A blow from one player to another makes them foes for a while. */
	private static void onDamaged(LivingDamageEvent.Post event) {
		if (event.getEntity() instanceof ServerPlayer victim && event.getSource().getEntity() instanceof Player attacker && attacker != victim
				&& event.getNewDamage() > 0) {
			long now = victim.level().getGameTime();
			CLASHES.put(pair(victim, attacker), now);
			if (CLASHES.size() > 256) {
				CLASHES.values().removeIf(t -> now - t > CLASH_TICKS);
			}
		}
	}
}
