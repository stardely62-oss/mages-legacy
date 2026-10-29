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
	/** What the Awakening trials count, in {@link RaceState#PROGRESS}. */
	public static final String POCKET_TICKS = "pocket_ticks";
	public static final String STEP_BLOCKS = "step_blocks";
	public static final String GRAVITY_KILLS = "gravity_kills";

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
			sum += Talent.byId(id).map(t -> t.cost).orElse(0);
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
		if (talent.kind == Talent.Kind.AWAKENING || talent.awakening() != null) {
			return "mages_legacy.talent.state.awakening_later";
		}
		if (RaceState.level(player) < talent.levelNeeded()) {
			return "mages_legacy.talent.state.level";
		}
		boolean linked = talent.requires().isEmpty();
		for (Talent near : talent.requires()) {
			linked |= has(player, near);
		}
		if (!linked) {
			return "mages_legacy.talent.state.needs";
		}
		if (freePoints(player) < talent.cost) {
			return "mages_legacy.talent.state.no_points";
		}
		return null;
	}

	public static void learn(ServerPlayer player, String id) {
		Talent talent = Talent.byId(id).orElse(null);
		if (talent == null) {
			return;
		}
		String why = refusal(player, talent);
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

	/** Takes back every talent, refunding its points (admin command). */
	public static void reset(ServerPlayer player) {
		player.setData(RaceState.TALENTS, List.of());
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
