package euf.mageslegacy.race;

import euf.mageslegacy.MagesLegacy;
import java.util.List;
import net.minecraft.advancements.CriterionTrigger;
import net.minecraft.core.registries.Registries;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

/**
 * The deeds behind the advancements of the Mage's Legacy tab ({@code data/mages_legacy/advancement/legacy}). The game
 * code says what really happened ({@link #grant}); the advancements only listen, so they are saved, shown, granted and
 * revoked the vanilla way. A deed of the Voidborn ({@code void_*}) or the Mycelids ({@code myc_*}) counts only for
 * that race, the rest for anyone with a race; none counts in the testing mode. What the saved state of a player can
 * prove (race, levels, talents, Awakenings, the mastery trial) is looked at again by {@link #check}, at login too, so
 * players from before the advancements get those (and only those) at once.
 */
public final class Deeds {
	private static final DeferredRegister<CriterionTrigger<?>> TRIGGERS = DeferredRegister.create(Registries.TRIGGER_TYPE, MagesLegacy.MOD_ID);
	public static final DeferredHolder<CriterionTrigger<?>, DeedTrigger> DEED = TRIGGERS.register("deed", DeedTrigger::new);

	private Deeds() {
	}

	public static void register(IEventBus modBus) {
		TRIGGERS.register(modBus);
		NeoForge.EVENT_BUS.addListener((PlayerEvent.PlayerLoggedInEvent e) -> {
			if (e.getEntity() instanceof ServerPlayer player) {
				check(player);
			}
		});
	}

	/** A deed the server has just seen this player do. */
	public static void grant(ServerPlayer player, String deed) {
		Race race = RaceState.get(player).orElse(null);
		if (race == null || Talents.noCooldown(player)) {
			return;
		}
		if (deed.startsWith("void_") && race != Race.VOIDBORN || deed.startsWith("myc_") && race != Race.MYCELID) {
			return;
		}
		DEED.get().trigger(player, deed);
	}

	/** The deeds the player's saved state proves: called at login, on a level, a talent bought, the trial passed. */
	public static void check(ServerPlayer player) {
		Race race = RaceState.get(player).orElse(null);
		if (race == null) {
			return;
		}
		int level = RaceState.level(player);
		List<String> owned = Talents.owned(player);
		grant(player, "root");
		int gift = 0;
		for (Ability a : Ability.of(race)) {
			if (a.slot == 0 || a.slot == 1) {
				gift = Math.max(gift, a.level);
			}
		}
		if (level >= Math.max(5, gift)) {
			grant(player, "gift");
		}
		boolean tierTwo = false;
		boolean cross = false;
		for (String id : owned) {
			Talent t = Talent.byId(id).orElse(null);
			if (t != null && t.race == race) {
				tierTwo |= !t.automatic() && t.tier() == 2;
				cross |= t.kind == Talent.Kind.CROSS;
			}
		}
		if (level >= 10 && tierTwo) {
			grant(player, "first_choice");
		}
		if (cross) {
			grant(player, "cross");
		}
		int awakened = Awakenings.owned(owned).size();
		if (awakened >= 1) {
			grant(player, "first_awakening");
		}
		if (Mastery.passed(player.getData(RaceState.PROGRESS))) {
			grant(player, "mastery");
		}
		if (awakened >= 2) {
			grant(player, "double");
		}
		if (level >= Progression.MAX_LEVEL) {
			grant(player, "peak");
		}
		if (race == Race.VOIDBORN && euf.mageslegacy.voidborn.Pocket.sizeFor(player) >= 15) {
			grant(player, "void_pocket_big");
		}
		if (race == Race.MYCELID) {
			euf.mageslegacy.mycelid.MyceliumNetwork.checkDeeds(player);
		}
	}
}
