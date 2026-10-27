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
	/** Tier N needs milestone N-1: none, the Nether, a summoned Wither, the dead dragon; and a race level as well. */
	private static final String[] MILESTONES = {null, "story/enter_the_nether", "nether/summon_wither", "end/kill_dragon"};
	public static final int[] TIER_LEVELS = {0, 10, 20, 30};

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
		RaceData data = RaceState.of(player);
		if (data == null || data.level < TIER_LEVELS[tier - 1]) {
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

	/** How much stronger a race's abilities are at its level: 1.5% a level, three quarters more at level 50. */
	public static float power(RaceData data) {
		return 1 + 0.015f * data.level;
	}

	/**
	 * A race grows into its powers: damage from its abilities (void and spore magic, a race's own blows of power) and
	 * from a Shepherd's creatures grows with the race level. Ordinary weapon blows stay as they are.
	 */
	public static float scale(net.minecraft.entity.damage.DamageSource source, float amount) {
		net.minecraft.entity.Entity attacker = source.getAttacker();
		ServerPlayerEntity owner = null;
		if (attacker instanceof ServerPlayerEntity player && source.isOf(net.minecraft.entity.damage.DamageTypes.INDIRECT_MAGIC)
				&& source.getSource() == player) {
			owner = player;
		} else if (attacker != null && !(attacker instanceof net.minecraft.entity.player.PlayerEntity) && attacker.getServer() != null) {
			java.util.UUID id = euf.mageslegacy.shepherd.Creatures.ownerOf(attacker);
			owner = id == null ? null : attacker.getServer().getPlayerManager().getPlayer(id);
		}
		RaceData data = owner == null ? null : RaceState.of(owner);
		return data == null || data.race == null ? amount : amount * power(data);
	}

	/** Every monster a race's player kills teaches a little, whatever the race. */
	public static void onKill(ServerPlayerEntity player, net.minecraft.entity.LivingEntity victim) {
		RaceData data = RaceState.of(player);
		if (data != null && data.race != null && victim instanceof net.minecraft.entity.mob.Monster) {
			addXp(player, data, KILLS, 1, 60);
		}
	}

	/** How long a capped source's window lasts: ten minutes of world time. */
	public static final long XP_WINDOW = 12000;
	/** Capped sources several abilities share: any monster killed, and a race's own hunting (kills its powers made). */
	public static final String KILLS = "kills";
	public static final String HUNT = "hunt";
	public static final int HUNT_CAP = 80;

	/**
	 * Experience from a source that a farm could feed forever (a pile of mushrooms, a pen of chickens, an enderman
	 * farm): at most {@code cap} from {@code source} in ten minutes, so the race grows by playing, not by repeating.
	 */
	public static void addXp(ServerPlayerEntity player, RaceData data, String source, int amount, int cap) {
		long now = player.getServerWorld().getTime();
		long[] window = data.xpTaken.get(source);
		if (window == null || now - window[0] >= XP_WINDOW || now < window[0]) {
			window = new long[] {now, 0};
			data.xpTaken.put(source, window);
		}
		int allowed = (int) Math.min(amount, cap - window[1]);
		if (allowed <= 0) {
			return;
		}
		window[1] += allowed;
		addXp(player, data, allowed);
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
