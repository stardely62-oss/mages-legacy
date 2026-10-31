package euf.mageslegacy.race;

import euf.mageslegacy.MagesLegacy;
import java.util.List;
import net.minecraft.advancements.AdvancementHolder;
import net.minecraft.advancements.AdvancementProgress;
import net.minecraft.advancements.CriterionTrigger;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

/**
 * The deeds behind the advancements of Mage's Legacy ({@code data/mages_legacy/advancement/legacy}). The game code says
 * what really happened ({@link #grant}); the advancements only listen, so they are saved, shown, granted and revoked the
 * vanilla way. Every race has its own tab: its root ({@code void_root}, {@code myc_root}), its own copy of the shared
 * path ({@code gift} ... {@code peak} become {@code void_gift} ... for the player's race) and its own deeds; a deed
 * counts only for that race, and none in the testing mode. What the saved state of a player can prove (race, levels,
 * talents, Awakenings, the mastery trial) is looked at again by {@link #check}, at login too, so old players get those
 * (and only those) at once, quietly. A player who changes race keeps the old race's advancements in an archive, off
 * the screen, and gets them back on becoming that race again.
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
				refresh(player);
			}
		});
	}

	/** The prefix of a race's advancements, or null for a race without a tab yet. */
	public static String prefix(Race race) {
		return race == Race.VOIDBORN ? "void" : race == Race.MYCELID ? "myc" : race == Race.BLOODKIN ? "blood" : null;
	}

	/** The stages of the shared path: each race has its own copy of them. */
	private static final java.util.Set<String> PATH = java.util.Set.of("root", "gift", "first_choice", "cross", "first_awakening", "mastery",
			"double", "peak");

	/** A deed the server has just seen this player do (a stage of the shared path goes to the player's race's copy). */
	public static void grant(ServerPlayer player, String deed) {
		Race race = RaceState.get(player).orElse(null);
		String prefix = race == null ? null : prefix(race);
		if (prefix == null || Talents.noCooldown(player)) {
			return;
		}
		if (PATH.contains(deed)) {
			deed = prefix + "_" + deed;
		}
		if (!deed.startsWith(prefix + "_")) {
			return;
		}
		DEED.get().trigger(player, deed);
	}

	/**
	 * At login and on a new race: the other races' advancements go to the archive, this race's come back from it, then
	 * what the saved state proves is granted, all without chat lines (nothing new happened).
	 */
	public static void refresh(ServerPlayer player) {
		var rule = player.serverLevel().getGameRules().getRule(net.minecraft.world.level.GameRules.RULE_ANNOUNCE_ADVANCEMENTS);
		boolean announce = rule.get();
		rule.set(false, null);
		try {
			if (archive(player)) {
				// Restored, not earned: saved and read back, so the client gets them in one full update, without a toast each
				player.getAdvancements().save();
				player.getAdvancements().reload(player.server.getAdvancements());
			}
			check(player);
		} finally {
			rule.set(announce, null);
		}
	}

	/** Moves the advancements of other races into the archive and brings this race's back; true if any came back. */
	private static boolean archive(ServerPlayer player) {
		boolean restored = false;
		String mine = RaceState.get(player).map(Deeds::prefix).orElse(null);
		CompoundTag archived = player.getData(RaceState.ARCHIVED).copy();
		var advancements = player.getAdvancements();
		for (AdvancementHolder holder : player.server.getAdvancements().getAllAdvancements()) {
			ResourceLocation id = holder.id();
			if (!id.getNamespace().equals(MagesLegacy.MOD_ID) || holder.value().display().isEmpty()) {
				continue;
			}
			String name = id.getPath().substring(id.getPath().indexOf('/') + 1);
			boolean ours = mine != null && name.startsWith(mine + "_");
			AdvancementProgress progress = advancements.getOrStartProgress(holder);
			if (ours && archived.contains(id.toString())) {
				// Back from the archive
				for (Tag criterion : archived.getList(id.toString(), Tag.TAG_STRING)) {
					advancements.award(holder, criterion.getAsString());
				}
				archived.remove(id.toString());
				restored = true;
			} else if (!ours && progress.hasProgress()) {
				ListTag met = new ListTag();
				for (String criterion : progress.getCompletedCriteria()) {
					met.add(StringTag.valueOf(criterion));
				}
				archived.put(id.toString(), met);
				for (String criterion : met.stream().map(Tag::getAsString).toList()) {
					advancements.revoke(holder, criterion);
				}
			}
		}
		player.setData(RaceState.ARCHIVED, archived);
		return restored;
	}

	/** What is in a player's archive (advancement ids), for the harness. */
	public static java.util.Set<String> archived(ServerPlayer player) {
		return player.getData(RaceState.ARCHIVED).getAllKeys();
	}

	/** The deeds the player's saved state proves: called at login, on a level, a talent bought, the trial passed. */
	public static void check(ServerPlayer player) {
		Race race = RaceState.get(player).orElse(null);
		if (race == null || prefix(race) == null) {
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
		// The shared tab of the first build is kept without a display: its stages count again for this race only when
		// the state above proves them, never copied over blindly
		if (race == Race.MYCELID) {
			euf.mageslegacy.mycelid.MyceliumNetwork.checkDeeds(player);
		}
	}
}
