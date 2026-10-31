package euf.mageslegacy.race;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import net.minecraft.ChatFormatting;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;

/**
 * The mastery trial that opens the second Awakening: three deeds of the race's own nature, done in the world at the
 * player's own pace, alone or with others. It starts with a click on a second Awakening's node from race level 45,
 * counts only from then on, and once all three are met the second Awakening can be bought (any of the two left) for
 * its usual points. Kept in {@link RaceState#PROGRESS}; passed means {@link Talents#SUPERLATE} set, as the old arena
 * trial did, so whoever passed that keeps it.
 */
public final class Mastery {
	/** Set (to the game time) when the trial is started. */
	public static final String SINCE = "mastery_since";

	/** One of the three deeds of a race's trial: its counter (a number, or a list of distinct things) and its goal. */
	public enum Goal {
		/** Void Steps through solid matter with a hostile creature within 12 blocks as the walk began. */
		STEP_THROUGH(Race.VOIDBORN, "m_step", 15, false),
		/** Heavier Gravity holding 4 different enemies in one casting. */
		GRAVITY_FOUR(Race.VOIDBORN, "m_gravity", 5, false),
		/** The Pocket as a shelter: entered from a world and come back to it safely, in two different ordinary worlds. */
		POCKET_WORLDS(Race.VOIDBORN, "m_pocket_worlds", 2, true),
		/** Grown threads of 512 blocks or more, each a different pair of nodes. */
		DEEP_ROOTS(Race.MYCELID, "m_roots", 3, true),
		/** Neurospores infecting 5 different enemies with one cloud. */
		SPORE_MIND(Race.MYCELID, "m_spores", 5, false),
		/** Neurospores on 2 enemies, then Neurosymbiosis within 10 s while one of them is still infected near by. */
		ONE_ORGANISM(Race.MYCELID, "m_organism", 5, false),
		/** Blood Dashes cutting through 3 or more enemies each. */
		DASH_CHAIN(Race.BLOODKIN, "m_dash_chain", 10, false),
		/** Harvests drawing blood from 5 or more enemies at once. */
		HARVEST_HORDE(Race.BLOODKIN, "m_harvest_horde", 5, false),
		/** From Thirst (under 20 blood) back to 80 or more within 30 seconds. */
		BLOOD_TIDE(Race.BLOODKIN, "m_blood_tide", 5, false);

		public final Race race;
		public final String tag;
		public final int need;
		final boolean distinct;

		Goal(Race race, String tag, int need, boolean distinct) {
			this.race = race;
			this.tag = tag;
			this.need = need;
			this.distinct = distinct;
		}

		/** Translation key: mages_legacy.mastery.goal.<id> (.desc for what to do). */
		public String key() {
			return "mages_legacy.mastery.goal." + name().toLowerCase(Locale.ROOT);
		}
	}

	private Mastery() {
	}

	public static List<Goal> goals(Race race) {
		List<Goal> list = new ArrayList<>();
		for (Goal g : Goal.values()) {
			if (g.race == race) {
				list.add(g);
			}
		}
		return list;
	}

	public static boolean started(CompoundTag progress) {
		return progress.getLong(SINCE) > 0;
	}

	public static boolean passed(CompoundTag progress) {
		return progress.getInt(Talents.SUPERLATE) > 0;
	}

	public static int count(CompoundTag progress, Goal goal) {
		return Math.min(goal.need, goal.distinct ? progress.getList(goal.tag, Tag.TAG_STRING).size() : progress.getInt(goal.tag));
	}

	/** The game time the trial began (0 if it hasn't). */
	public static long since(ServerPlayer player) {
		return player.getData(RaceState.PROGRESS).getLong(SINCE);
	}

	/** A click on the second Awakening at level 45 with the trial not yet begun. */
	public static void start(ServerPlayer player) {
		CompoundTag tag = player.getData(RaceState.PROGRESS).copy();
		if (started(tag) || passed(tag)) {
			return;
		}
		tag.putLong(SINCE, Math.max(1, player.level().getGameTime()));
		player.setData(RaceState.PROGRESS, tag);
		player.displayClientMessage(Component.translatable("mages_legacy.message.mastery_started").withStyle(ChatFormatting.GOLD), false);
		RaceState.get(player).ifPresent(race -> {
			for (Goal g : goals(race)) {
				player.displayClientMessage(Component.literal(" • ").append(Component.translatable(g.key())).append(": ")
						.append(Component.translatable(g.key() + ".desc")).append(" (0/" + g.need + ")").withStyle(ChatFormatting.LIGHT_PURPLE), false);
			}
		});
		player.level().playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.BEACON_ACTIVATE, SoundSource.PLAYERS, 0.8f, 0.8f);
		RaceState.sync(player);
	}

	/** Whether this deed counts now: the player's own race, the trial running, never in the testing mode. */
	private static boolean counting(ServerPlayer player, Goal goal, CompoundTag tag) {
		return RaceState.is(player, goal.race) && started(tag) && !passed(tag) && !Talents.noCooldown(player) && count(tag, goal) < goal.need;
	}

	/** One more of a counted deed. */
	public static void add(ServerPlayer player, Goal goal) {
		CompoundTag tag = player.getData(RaceState.PROGRESS).copy();
		if (!counting(player, goal, tag)) {
			return;
		}
		tag.putInt(goal.tag, tag.getInt(goal.tag) + 1);
		advanced(player, goal, tag);
	}

	/** A deed counted once per distinct thing (a world, a pair of nodes): the same one again changes nothing. */
	public static void addDistinct(ServerPlayer player, Goal goal, String what) {
		CompoundTag tag = player.getData(RaceState.PROGRESS).copy();
		if (!counting(player, goal, tag)) {
			return;
		}
		ListTag list = tag.getList(goal.tag, Tag.TAG_STRING);
		for (Tag t : list) {
			if (t.getAsString().equals(what)) {
				return;
			}
		}
		list.add(StringTag.valueOf(what));
		tag.put(goal.tag, list);
		advanced(player, goal, tag);
	}

	private static void advanced(ServerPlayer player, Goal goal, CompoundTag tag) {
		boolean all = true;
		for (Goal g : goals(goal.race)) {
			all &= count(tag, g) >= g.need;
		}
		if (all) {
			tag.putInt(Talents.SUPERLATE, 1);
		}
		player.setData(RaceState.PROGRESS, tag);
		player.displayClientMessage(Component.translatable("mages_legacy.message.mastery_step", Component.translatable(goal.key()), count(tag, goal), goal.need)
				.withStyle(ChatFormatting.LIGHT_PURPLE), true);
		if (all) {
			player.displayClientMessage(Component.translatable("mages_legacy.message.mastery_done").withStyle(ChatFormatting.GOLD), false);
			player.level().playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.UI_TOAST_CHALLENGE_COMPLETE, SoundSource.PLAYERS, 1f, 1f);
			Deeds.check(player);
		}
		RaceState.sync(player);
	}
}
