package euf.mageslegacy.race;

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
		Optional<Ability> found = Ability.of(race.get(), slot);
		if (found.isEmpty()) {
			return;
		}
		Ability ability = found.get();
		Voidborn.State state = Voidborn.state(player);
		// Leaving the Pocket is always free and never waits
		boolean free = ability == Ability.POCKET && Pocket.isPocket(player.level());
		if (RaceState.level(player) < ability.level) {
			refuse(player, Component.translatable("mages_legacy.refuse.level", ability.level));
			return;
		}
		if (!free && state.cooldown[slot] > 0) {
			refuse(player, Component.translatable("mages_legacy.refuse.cooldown", String.format("%.1f", state.cooldown[slot] / 20f)));
			return;
		}
		if (!free && RaceState.resource(player) < ability.cost) {
			refuse(player, Component.translatable("mages_legacy.refuse.resource." + race.get().id()));
			return;
		}
		Refusal refusal = switch (ability) {
			case VOID_STEP -> Voidborn.step(player);
			case GRAVITY -> Voidborn.gravity(player);
			case POCKET -> free ? Pocket.leave(player) : Pocket.enter(player);
		};
		if (refusal != null) {
			refuse(player, Component.translatable(refusal.key()));
			return;
		}
		if (!free) {
			RaceState.setResource(player, RaceState.resource(player) - ability.cost);
			// The Void Step's cooldown starts when the walk ends
			if (ability != Ability.VOID_STEP) {
				state.startCooldown(slot, ability.cooldown);
			}
		}
		RaceState.sync(player);
	}

	public static void refuse(ServerPlayer player, Component why) {
		player.displayClientMessage(why.copy().withStyle(ChatFormatting.RED), true);
	}
}
