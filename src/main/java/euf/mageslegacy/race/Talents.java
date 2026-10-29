package euf.mageslegacy.race;

import euf.mageslegacy.voidborn.Pocket;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.ChatFormatting;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;

/**
 * Who has learnt what. Race levels give talent points (one per level after the first); the core and the basic
 * abilities come free at their level; every other node costs points, needs one of its neighbours towards the core,
 * and the outer rings need race level 10. Awakenings open from race level 30 once their trial is met; the second
 * Awakening needs the superlate trial and none of the three can be woken in this build yet.
 */
public final class Talents {
	/**
	 * What the Awakening trials count, in {@link RaceState#PROGRESS}. Each trial asks for a mastered ability, not hours:
	 * Rift of Space: a Pocket grown to 15×15 and three nights slept in it; Through Worlds: 40 Void Steps through solid
	 * matter, 10 of them in the Nether; Black Hole: 40 enemies killed under Heavier Gravity, 8 of them fliers.
	 */
	public static final String POCKET_NIGHTS = "pocket_nights";
	public static final String STEP_THROUGH = "step_through";
	public static final String STEP_THROUGH_NETHER = "step_through_nether";
	public static final String GRAVITY_KILLS = "gravity_kills";
	public static final String GRAVITY_FLIERS = "gravity_fliers";
	public static final int RIFT_NIGHTS = 3;
	public static final int WORLDS_THROUGH = 40;
	public static final int WORLDS_NETHER = 10;
	public static final int HOLE_KILLS = 40;
	public static final int HOLE_FLIERS = 8;
	/** The first trials as they were (an hour at home, 2000 blocks walked, 100 kills): whoever met them keeps them met. */
	public static final String POCKET_TICKS = "pocket_ticks";
	public static final String STEP_BLOCKS = "step_blocks";
	private static final int OLD_RIFT = 60 * 60 * 20;
	private static final int OLD_WORLDS = 2000;
	private static final int OLD_HOLE = 100;
	/** Set once the superlate trial (the Void's Trial) is passed. */
	public static final String SUPERLATE = "superlate";
	private Talents() {
	}

	public static List<String> owned(ServerPlayer player) {
		return player.getData(RaceState.TALENTS);
	}

	public static boolean has(ServerPlayer player, Talent talent) {
		if (!RaceState.is(player, talent.race)) {
			return false;
		}
		if (talent.automatic()) {
			return RaceState.level(player) >= talent.autoLevel;
		}
		return owned(player).contains(talent.id());
	}

	public static int spent(List<String> owned) {
		int sum = 0;
		for (String id : owned) {
			sum += Talent.byId(id).map(t -> t.cost()).orElse(0);
		}
		return sum;
	}

	public static int freePoints(ServerPlayer player) {
		return Talent.pointsAt(RaceState.level(player)) - spent(owned(player));
	}

	/** Why a talent can't be learnt now, or null if it can. */
	public static String refusal(ServerPlayer player, Talent talent) {
		if (!RaceState.is(player, talent.race) || talent.automatic()) {
			return "mages_legacy.talent.state.automatic";
		}
		if (owned(player).contains(talent.id())) {
			return "mages_legacy.talent.state.owned";
		}
		if (talent.tier() > Progression.openTiers(player)) {
			return "mages_legacy.talent.state.tier_" + talent.tier();
		}
		boolean linked = talent.requires().isEmpty();
		for (Talent near : talent.requires()) {
			linked |= has(player, near);
		}
		if (!linked) {
			return "mages_legacy.talent.state.needs";
		}
		if (talent.kind == Talent.Kind.AWAKENING) {
			String why = awakeningRefusal(owned(player), RaceState.level(player), talent, player.getData(RaceState.PROGRESS));
			if (why != null) {
				return why;
			}
		}
		if (freePoints(player) < talent.cost()) {
			return "mages_legacy.talent.state.no_points";
		}
		return null;
	}

	/**
	 * The two-of-three rule (shared with the client's screen): the first Awakening needs its trial, the second race
	 * level 45 and the superlate trial, a third never opens.
	 */
	public static String awakeningRefusal(List<String> owned, int level, Talent talent, CompoundTag progress) {
		// The Mycelid's Awakenings come in the next part of the race
		if (talent.race == Race.MYCELID) {
			return "mages_legacy.talent.state.awakening_later";
		}
		int awakened = Awakenings.owned(owned).size();
		if (awakened >= 2) {
			return "mages_legacy.talent.state.third_closed";
		}
		if (awakened == 0) {
			return trialDone(talent, owned, progress) ? null : "mages_legacy.talent.state.trial";
		}
		if (level < Awakenings.SUPERLATE_LEVEL) {
			return "mages_legacy.talent.state.superlate_level";
		}
		return progress.getInt(SUPERLATE) > 0 ? null : "mages_legacy.talent.state.superlate_trial";
	}

	/** Whether this Awakening's trial is met (the client passes the flag the server worked out as "done_<id>"). */
	public static boolean trialDone(Talent talent, List<String> owned, CompoundTag p) {
		if (p.getBoolean("done_" + talent.id())) {
			return true;
		}
		return switch (talent) {
			case AWAKENING_RIFT -> owned.contains(Talent.POCKET_WIDE_2.id()) && p.getInt(POCKET_NIGHTS) >= RIFT_NIGHTS
					|| p.getInt(POCKET_TICKS) >= OLD_RIFT;
			case AWAKENING_WORLDS -> p.getInt(STEP_THROUGH) >= WORLDS_THROUGH && p.getInt(STEP_THROUGH_NETHER) >= WORLDS_NETHER
					|| p.getInt(STEP_BLOCKS) >= OLD_WORLDS;
			case AWAKENING_HOLE -> p.getInt(GRAVITY_KILLS) >= HOLE_KILLS && p.getInt(GRAVITY_FLIERS) >= HOLE_FLIERS
					|| p.getInt(GRAVITY_KILLS) >= OLD_HOLE;
			default -> false;
		};
	}

	/** Marks a trial met (admin command). */
	public static void passTrial(ServerPlayer player, Talent talent) {
		CompoundTag tag = player.getData(RaceState.PROGRESS).copy();
		tag.putBoolean("done_" + talent.id(), true);
		player.setData(RaceState.PROGRESS, tag);
	}

	public static void learn(ServerPlayer player, String id) {
		Talent talent = Talent.byId(id).orElse(null);
		if (talent == null) {
			return;
		}
		String why = refusal(player, talent);
		// Clicking the second Awakening before its trial starts the trial
		if ("mages_legacy.talent.state.superlate_trial".equals(why)) {
			if (!euf.mageslegacy.voidborn.Arena.startTrial(player)) {
				Abilities.refuse(player, Component.translatable("mages_legacy.refuse.arena_here"));
			}
			return;
		}
		if (why != null) {
			Abilities.refuse(player, Component.translatable(why));
			return;
		}
		List<String> list = new ArrayList<>(owned(player));
		list.add(talent.id());
		player.setData(RaceState.TALENTS, list);
		applied(player, talent);
		player.level().playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.ENCHANTMENT_TABLE_USE, SoundSource.PLAYERS, 0.8f, 0.8f);
		player.displayClientMessage(Component.translatable("mages_legacy.message.learnt", Component.translatable(talent.key() + ".name"))
				.withStyle(ChatFormatting.LIGHT_PURPLE), true);
		RaceState.sync(player);
	}

	/** What a talent changes in the world the moment it is learnt (the rest is read as the abilities run). */
	private static void applied(ServerPlayer player, Talent talent) {
		switch (talent) {
			case POCKET_WIDE_1, POCKET_WIDE_2, POCKET_WIDE_3, POCKET_VAULT -> Pocket.refresh(player);
			default -> {
			}
		}
	}

	/**
	 * For testing: race level 50, a full resource and every talent of the race, whatever their tiers, trials and
	 * points. All three Awakenings are learnt; {@code z} and {@code alt} (rift, worlds, hole) say which two sit on
	 * Z and Alt.
	 */
	public static void learnAll(ServerPlayer player, String z, String alt) {
		Race race = RaceState.get(player).orElse(null);
		if (race == null) {
			return;
		}
		Progression.setLevel(player, Progression.MAX_LEVEL);
		RaceState.setResource(player, RaceState.MAX_RESOURCE);
		List<String> list = new ArrayList<>();
		for (String pick : new String[] {z, alt, "rift", "worlds", "hole"}) {
			Talent.byId("awakening_" + pick).filter(t -> t.race == race && !list.contains(t.id())).ifPresent(t -> list.add(t.id()));
		}
		for (Talent t : Talent.of(race)) {
			if (!t.automatic() && !list.contains(t.id())) {
				list.add(t.id());
			}
		}
		player.setData(RaceState.TALENTS, list);
		Pocket.refresh(player);
		RaceState.sync(player);
	}

	/** Testing mode: no cooldowns and a resource that never runs out (kept in the progress tag). */
	public static final String NO_COOLDOWN = "test_no_cooldown";

	public static void setNoCooldown(ServerPlayer player, boolean on) {
		CompoundTag tag = player.getData(RaceState.PROGRESS).copy();
		tag.putBoolean(NO_COOLDOWN, on);
		player.setData(RaceState.PROGRESS, tag);
	}

	public static boolean noCooldown(ServerPlayer player) {
		return player.getData(RaceState.PROGRESS).getBoolean(NO_COOLDOWN);
	}

	/** Takes back every talent, refunding its points (admin command). */
	public static void reset(ServerPlayer player) {
		// The Awakenings stay once woken: forgetting them would undo their trials
		List<String> kept = new ArrayList<>();
		for (Talent awakened : Awakenings.owned(owned(player))) {
			kept.add(awakened.id());
		}
		player.setData(RaceState.TALENTS, kept);
		Pocket.refresh(player);
		RaceState.sync(player);
	}

	public static int progress(ServerPlayer player, String key) {
		return player.getData(RaceState.PROGRESS).getInt(key);
	}

	public static void addProgress(ServerPlayer player, String key, int amount) {
		CompoundTag tag = player.getData(RaceState.PROGRESS).copy();
		tag.putInt(key, tag.getInt(key) + amount);
		player.setData(RaceState.PROGRESS, tag);
	}
}
