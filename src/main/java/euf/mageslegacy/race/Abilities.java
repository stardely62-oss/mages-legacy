package euf.mageslegacy.race;

import euf.mageslegacy.voidborn.Arena;
import euf.mageslegacy.voidborn.BlackHole;
import euf.mageslegacy.voidborn.Passage;
import euf.mageslegacy.voidborn.Pocket;
import euf.mageslegacy.voidborn.Voidborn;
import java.util.Optional;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

/**
 * A pressed ability key: checks the level, the cooldown and the resource, then lets the race's own code act. An
 * ability that can't happen right now (nowhere to step out, struck too recently) says why and costs nothing.
 */
public final class Abilities {
	private Abilities() {
	}

	/** Why an ability didn't happen; null when it did. */
	public record Refusal(String key) {
	}

	public static void use(ServerPlayer player, int slot) {
		Optional<Race> race = RaceState.get(player);
		if (race.isEmpty() || slot < 0 || slot >= Ability.SLOTS || player.isSpectator()) {
			return;
		}
		// Z and left Alt hold the Awakenings, in the order they were learnt
		Optional<Ability> found = slot == Awakenings.FIRST_SLOT || slot == Awakenings.SECOND_SLOT
				? Awakenings.at(Talents.owned(player), slot) : Ability.of(race.get(), slot);
		if (found.isEmpty()) {
			return;
		}
		Ability ability = found.get();
		Voidborn.State state = Voidborn.state(player);
		int cost = cost(player, ability);
		// A singularity holds its heart in place: nothing else happens until it collapses
		if (BlackHole.holding(player)) {
			refuse(player, Component.translatable("mages_legacy.refuse.singularity"));
			return;
		}
		// Leaving the Pocket and ending an arena are always free and never wait
		boolean free = ability == Ability.POCKET && Pocket.isPocket(player.level()) || ability == Ability.RIFT_ARENA && Arena.endsNow(player);
		// An arena holds whoever is on it until it ends
		if (ability == Ability.POCKET && Arena.isArena(player.level())) {
			refuse(player, Component.translatable("mages_legacy.refuse.arena_here"));
			return;
		}
		if (RaceState.level(player) < ability.level) {
			refuse(player, Component.translatable("mages_legacy.refuse.level", ability.level));
			return;
		}
		if (!free && state.cooldown[slot] > 0) {
			refuse(player, Component.translatable("mages_legacy.refuse.cooldown", String.format("%.1f", state.cooldown[slot] / 20f)));
			return;
		}
		// A mark in space (Space Mark talent) is left with a sneak, free and without waiting
		if (ability == Ability.VOID_STEP && RaceState.level(player) >= ability.level && Voidborn.tryMark(player)) {
			RaceState.sync(player);
			return;
		}
		// The place for Through Worlds is remembered with a sneak, free and without waiting
		if (ability == Ability.PASSAGE && Passage.tryRemember(player)) {
			RaceState.sync(player);
			return;
		}
		if (!free && RaceState.resource(player) < cost) {
			refuse(player, Component.translatable("mages_legacy.refuse.resource." + race.get().id()));
			return;
		}
		Refusal refusal = switch (ability) {
			case VOID_STEP -> Voidborn.step(player);
			case GRAVITY -> Voidborn.gravity(player);
			case POCKET -> free ? Pocket.leave(player) : Pocket.enter(player);
			case RIFT_ARENA -> Arena.use(player);
			case PASSAGE -> Passage.open(player);
			case BLACK_HOLE -> BlackHole.use(player);
		};
		if (refusal != null) {
			refuse(player, Component.translatable(refusal.key()));
			return;
		}
		if (!free) {
			RaceState.setResource(player, RaceState.resource(player) - cost);
			// The Void Step's cooldown starts when the walk ends, the arena's when the arena does
			if (ability != Ability.VOID_STEP && ability != Ability.RIFT_ARENA) {
				state.startCooldown(slot, cooldown(player, ability));
			}
		}
		RaceState.sync(player);
	}

	/** What an ability costs this player, talents counted. */
	public static int cost(ServerPlayer player, Ability ability) {
		if (ability == Ability.POCKET && Talents.has(player, Talent.POCKET_CHEAP)) {
			return 10;
		}
		return ability.cost;
	}

	public static int cooldown(ServerPlayer player, Ability ability) {
		if (ability == Ability.VOID_STEP && Talents.has(player, Talent.STEP_QUICK)) {
			return 4 * 20;
		}
		if (ability == Ability.POCKET && Talents.has(player, Talent.POCKET_CHEAP)) {
			return 5 * 20;
		}
		return ability.cooldown;
	}

	public static void refuse(ServerPlayer player, Component why) {
		player.displayClientMessage(why.copy().withStyle(ChatFormatting.RED), true);
	}
}
