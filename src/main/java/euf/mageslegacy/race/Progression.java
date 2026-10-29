package euf.mageslegacy.race;

import euf.mageslegacy.voidborn.Voidborn;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.monster.Enemy;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;

/**
 * Race levels. For now the race grows from slain monsters (a Voidborn learns twice as much in the dark and in the
 * End) and the Voidborn from nights slept in the Pocket; the talent tree will bring its own sources.
 */
public final class Progression {
	public static final int MAX_LEVEL = 50;

	private Progression() {
	}

	public static void register() {
		NeoForge.EVENT_BUS.addListener(Progression::onDeath);
	}

	/** Experience from this level to the next. */
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
		player.setData(RaceState.LEVEL, level);
		player.setData(RaceState.XP, xp);
	}

	public static void setLevel(ServerPlayer player, int level) {
		int old = RaceState.level(player);
		level = Math.max(1, Math.min(MAX_LEVEL, level));
		player.setData(RaceState.LEVEL, level);
		player.setData(RaceState.XP, 0);
		for (int l = old + 1; l <= level; l++) {
			levelUp(player, l);
		}
		RaceState.sync(player);
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
		if (event.getSource().getEntity() instanceof ServerPlayer player && event.getEntity() instanceof Enemy) {
			int xp = 1;
			if (RaceState.is(player, Race.VOIDBORN) && Voidborn.inDarkOrEnd(player)) {
				xp++;
			}
			addXp(player, xp);
		}
	}
}
