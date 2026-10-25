package euf.mageslegacy.race;

import euf.mageslegacy.net.RacePackets;
import net.minecraft.advancement.Advancement;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.Identifier;

/** Race experience, levels, milestones that open the tiers of the tree, and buying talents. */
public final class Progression {
	public static final int MAX_TIER = 4;
	/** Tier N needs milestone N-1: none, the Nether, a summoned Wither, the dead dragon. */
	private static final String[] MILESTONES = {null, "story/enter_the_nether", "nether/summon_wither", "end/kill_dragon"};

	public enum UnlockResult { OK, NO_RACE, UNKNOWN, OTHER_RACE, ALREADY, NO_POINTS, NEEDS_TALENT, NEEDS_MILESTONE }

	private Progression() {
	}

	public static boolean tierOpen(ServerPlayerEntity player, int tier) {
		if (tier <= 1) {
			return true;
		}
		if (tier > MAX_TIER) {
			return false;
		}
		Advancement advancement = player.server.getAdvancementLoader().get(new Identifier(MILESTONES[tier - 1]));
		return advancement != null && player.getAdvancementTracker().getProgress(advancement).isDone();
	}

	/** Highest open tier, for the client's tree screen. */
	public static int openTiers(ServerPlayerEntity player) {
		int tier = 1;
		while (tier < MAX_TIER && tierOpen(player, tier + 1)) {
			tier++;
		}
		return tier;
	}

	public static void addXp(ServerPlayerEntity player, RaceData data, int amount) {
		int gained = data.addXp(amount);
		if (gained > 0) {
			player.sendMessage(Text.translatable("mages_legacy.message.level_up", data.level, data.freePoints()).formatted(Formatting.GREEN), false);
			player.getWorld().playSound(null, player.getBlockPos(), SoundEvents.ENTITY_PLAYER_LEVELUP, SoundCategory.PLAYERS, 0.6f, 0.7f);
		}
	}

	public static UnlockResult unlock(ServerPlayerEntity player, RaceData data, String id) {
		if (data.race == null) {
			return UnlockResult.NO_RACE;
		}
		Talent talent = Talents.byId(id).orElse(null);
		if (talent == null) {
			return UnlockResult.UNKNOWN;
		}
		if (talent.race() != data.race) {
			return UnlockResult.OTHER_RACE;
		}
		if (data.has(id)) {
			return UnlockResult.ALREADY;
		}
		if (talent.requires() != null && !data.has(talent.requires())) {
			return UnlockResult.NEEDS_TALENT;
		}
		if (!tierOpen(player, talent.tier())) {
			return UnlockResult.NEEDS_MILESTONE;
		}
		if (data.freePoints() < talent.cost()) {
			return UnlockResult.NO_POINTS;
		}
		data.talents.add(id);
		data.dirty = true;
		RacePackets.sync(player, data);
		return UnlockResult.OK;
	}
}
