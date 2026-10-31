package euf.mageslegacy.race;

import euf.mageslegacy.voidborn.Voidborn;
import net.minecraft.ChatFormatting;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.monster.Enemy;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;

/**
 * Race levels and the tree's tiers, as in the first version: experience comes from the race's own deeds and from
 * monsters, every source capped per 10 minutes of world time (a race grows by playing, not by farming); the tree's
 * tiers open with race levels alone.
 */
public final class Progression {
	public static final int MAX_LEVEL = 50;
	/** Experience sources and how much each may give per {@link #WINDOW} ticks. */
	public static final String KILLS = "kills";
	public static final String STEP = "step";
	public static final String GRAVITY = "gravity";
	public static final String HUNT = "hunt";
	private static final int WINDOW = 10 * 60 * 20;

	private Progression() {
	}

	public static void register() {
		NeoForge.EVENT_BUS.addListener(Progression::onDeath);
	}

	/** Experience from this level to the next: about 19k to level 30 and 67k to 50. */
	public static int xpForNext(int level) {
		return 50 + 20 * level + level * level;
	}

	public static void addXp(ServerPlayer player, int amount) {
		if (RaceState.get(player).isEmpty() || amount <= 0) {
			return;
		}
		int level = RaceState.level(player);
		int xp = player.getData(RaceState.XP) + amount;
		while (level < MAX_LEVEL && xp >= xpForNext(level)) {
			xp -= xpForNext(level);
			level++;
			levelUp(player, level);
		}
		if (level >= MAX_LEVEL) {
			xp = 0;
		}
		boolean grew = level != RaceState.level(player);
		player.setData(RaceState.LEVEL, level);
		player.setData(RaceState.XP, xp);
		if (grew) {
			Deeds.check(player);
		}
	}

	/** Experience from one source, no more than {@code cap} of it per 10 minutes of world time. */
	public static void addXp(ServerPlayer player, String source, int amount, int cap) {
		CompoundTag progress = player.getData(RaceState.PROGRESS).copy();
		long now = player.level().getGameTime();
		String key = "xp_" + source;
		if (now - progress.getLong(key + "_t") >= WINDOW) {
			progress.putLong(key + "_t", now);
			progress.putInt(key, 0);
		}
		int given = Math.min(amount, cap - progress.getInt(key));
		if (given <= 0) {
			return;
		}
		progress.putInt(key, progress.getInt(key) + given);
		player.setData(RaceState.PROGRESS, progress);
		addXp(player, given);
	}

	public static void setLevel(ServerPlayer player, int level) {
		int old = RaceState.level(player);
		level = Math.max(1, Math.min(MAX_LEVEL, level));
		player.setData(RaceState.LEVEL, level);
		player.setData(RaceState.XP, 0);
		for (int l = old + 1; l <= level; l++) {
			levelUp(player, l);
		}
		Deeds.check(player);
		RaceState.sync(player);
	}

	/** How many tiers of the tree are open: each opens with its race level (1, 10, 20, 30). */
	public static int openTiers(ServerPlayer player) {
		int tier = 1;
		while (tier < Talent.MAX_TIER && RaceState.level(player) >= Talent.TIER_LEVELS[tier]) {
			tier++;
		}
		return tier;
	}

	private static void levelUp(ServerPlayer player, int level) {
		player.displayClientMessage(Component.translatable("mages_legacy.message.level_up", level).withStyle(ChatFormatting.LIGHT_PURPLE), true);
		player.level().playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.PLAYER_LEVELUP, SoundSource.PLAYERS, 0.6f, 0.7f);
		RaceState.get(player).ifPresent(race -> {
			for (Ability ability : Ability.of(race)) {
				if (ability.level == level) {
					player.displayClientMessage(Component.translatable("mages_legacy.message.unlocked", Component.translatable(ability.key() + ".name"),
							Ability.KEY_NAMES[ability.slot]).withStyle(ChatFormatting.GOLD), false);
				}
			}
		});
	}

	private static void onDeath(LivingDeathEvent event) {
		if (!(event.getSource().getEntity() instanceof ServerPlayer player) || !(event.getEntity() instanceof Enemy)) {
			return;
		}
		if (RaceState.is(player, Race.VOIDBORN)) {
			// The void knows its own: endermen teach the most, the dark twice what the light does
			if (event.getEntity().getType() == EntityType.ENDERMAN) {
				addXp(player, HUNT, 8, 80);
			}
			addXp(player, KILLS, Voidborn.inDarkOrEnd(player) ? 2 : 1, 60);
		} else {
			addXp(player, KILLS, 1, 60);
		}
	}
}
