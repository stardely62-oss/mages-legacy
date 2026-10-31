package euf.mageslegacy.race;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.FloatArgumentType;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Collection;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.RegisterCommandsEvent;

/**
 * For testing and admins: {@code /mages_legacy race <players> <race|none>}, {@code level <players> <1-50>},
 * {@code resource <players> <amount>}, {@code cooldowns <players>} (clears them), {@code talents <players> reset|learn <id>}
 * and {@code talents <players> all [z alt]} (for testing: level 50 and every talent, conditions ignored),
 * {@code nocooldown <players> on|off} (for testing: abilities always ready, the resource always full),
 * {@code awaken <players> <first> [second]} (for testing: just these Awakenings, on Z and Alt, the race level raised to
 * fit; the other talents stay).
 */
public final class RaceCommand {
	private RaceCommand() {
	}

	public static void register() {
		NeoForge.EVENT_BUS.addListener((RegisterCommandsEvent event) -> register(event.getDispatcher()));
	}

	private static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
		dispatcher.register(Commands.literal("mages_legacy").requires(source -> source.hasPermission(2))
				.then(Commands.literal("race").then(Commands.argument("targets", EntityArgument.players())
						.then(Commands.argument("race", StringArgumentType.word())
								.suggests((ctx, builder) -> SharedSuggestionProvider.suggest(
										java.util.stream.Stream.concat(Arrays.stream(Race.values()).map(Race::id), java.util.stream.Stream.of("none")), builder))
								.executes(RaceCommand::race))))
				.then(Commands.literal("level").then(Commands.argument("targets", EntityArgument.players())
						.then(Commands.argument("level", IntegerArgumentType.integer(1, Progression.MAX_LEVEL)).executes(ctx -> {
							int level = IntegerArgumentType.getInteger(ctx, "level");
							return each(ctx, player -> Progression.setLevel(player, level));
						}))))
				.then(Commands.literal("resource").then(Commands.argument("targets", EntityArgument.players())
						.then(Commands.argument("amount", FloatArgumentType.floatArg(0, RaceState.MAX_RESOURCE)).executes(ctx -> {
							float amount = FloatArgumentType.getFloat(ctx, "amount");
							return each(ctx, player -> RaceState.setResource(player, amount));
						}))))
				// For testing: exactly these Awakenings (the first on Z, the second on Alt), no trial asked
				.then(Commands.literal("awaken").then(Commands.argument("targets", EntityArgument.players())
						.then(Commands.argument("first", StringArgumentType.word()).suggests(RaceCommand::awakenings)
								.executes(ctx -> awaken(ctx, StringArgumentType.getString(ctx, "first"), null))
								.then(Commands.argument("second", StringArgumentType.word()).suggests(RaceCommand::awakenings)
										.executes(ctx -> awaken(ctx, StringArgumentType.getString(ctx, "first"), StringArgumentType.getString(ctx, "second")))))))
				.then(Commands.literal("talents").then(Commands.argument("targets", EntityArgument.players())
						.then(Commands.literal("reset").executes(ctx -> each(ctx, Talents::reset)))
						// For testing: the whole tree at once, nothing asked; optionally which Awakenings sit on Z and Alt
						.then(Commands.literal("all").executes(ctx -> each(ctx, player -> Talents.learnAll(player, "rift", "hole")))
								.then(Commands.argument("z", StringArgumentType.word()).suggests(RaceCommand::awakenings)
										.then(Commands.argument("alt", StringArgumentType.word()).suggests(RaceCommand::awakenings)
												.executes(ctx -> {
													String z = StringArgumentType.getString(ctx, "z");
													String alt = StringArgumentType.getString(ctx, "alt");
													return each(ctx, player -> Talents.learnAll(player, z, alt));
												}))))
						.then(Commands.literal("learn").then(Commands.argument("talent", StringArgumentType.word())
								.suggests((ctx, builder) -> SharedSuggestionProvider.suggest(Arrays.stream(Talent.values()).map(Talent::id), builder))
								.executes(ctx -> {
									String id = StringArgumentType.getString(ctx, "talent");
									return each(ctx, player -> Talents.learn(player, id));
								})))))
				.then(Commands.literal("trial").then(Commands.argument("targets", EntityArgument.players())
						.then(Commands.argument("trial", StringArgumentType.word())
								.suggests((ctx, builder) -> SharedSuggestionProvider.suggest(java.util.List.of("rift", "worlds", "hole", "bloom", "storm", "rebirth",
										"feast", "blood_storm", "prince", "superlate"), builder))
								.executes(ctx -> {
									String trial = StringArgumentType.getString(ctx, "trial");
									return each(ctx, player -> {
										switch (trial) {
											case "rift" -> Talents.passTrial(player, Talent.AWAKENING_RIFT);
											case "worlds" -> Talents.passTrial(player, Talent.AWAKENING_WORLDS);
											case "hole" -> Talents.passTrial(player, Talent.AWAKENING_HOLE);
											case "bloom" -> Talents.passTrial(player, Talent.AWAKENING_BLOOM);
											case "storm" -> Talents.passTrial(player, Talent.AWAKENING_STORM);
											case "rebirth" -> Talents.passTrial(player, Talent.AWAKENING_REBIRTH);
											case "feast" -> Talents.passTrial(player, Talent.AWAKENING_FEAST);
											case "blood_storm" -> Talents.passTrial(player, Talent.AWAKENING_BLOOD_STORM);
											case "prince" -> Talents.passTrial(player, Talent.AWAKENING_PRINCE);
											default -> Talents.addProgress(player, Talents.SUPERLATE, 1);
										}
									});
								}))))
				// For testing: every ability always ready and free, until turned off
				.then(Commands.literal("nocooldown").then(Commands.argument("targets", EntityArgument.players())
						.then(Commands.literal("on").executes(ctx -> each(ctx, player -> Talents.setNoCooldown(player, true))))
						.then(Commands.literal("off").executes(ctx -> each(ctx, player -> Talents.setNoCooldown(player, false))))))
				.then(Commands.literal("cooldowns").then(Commands.argument("targets", EntityArgument.players()).executes(ctx -> each(ctx, player -> {
					var state = euf.mageslegacy.voidborn.Voidborn.state(player);
					Arrays.fill(state.cooldown, 0);
				})))));
	}

	private static java.util.concurrent.CompletableFuture<com.mojang.brigadier.suggestion.Suggestions> awakenings(CommandContext<CommandSourceStack> ctx,
			com.mojang.brigadier.suggestion.SuggestionsBuilder builder) {
		return SharedSuggestionProvider.suggest(java.util.List.of("rift", "worlds", "hole", "bloom", "storm", "rebirth", "feast", "blood_storm", "prince"),
				builder);
	}

	/**
	 * Just these Awakenings of the player's race: the ones held before are taken off (with their own talents), the
	 * chosen ones put first in this order (the first on Z, the second on Alt); race level 30 at least, 45 for two.
	 */
	private static int awaken(CommandContext<CommandSourceStack> ctx, String first, String second) throws CommandSyntaxException {
		List<String> picks = second == null ? List.of(first) : List.of(first, second);
		for (String pick : picks) {
			if (Talent.byId("awakening_" + pick).filter(t -> t.kind == Talent.Kind.AWAKENING).isEmpty()) {
				ctx.getSource().sendFailure(Component.literal("Unknown Awakening: " + pick));
				return 0;
			}
		}
		return each(ctx, player -> {
			Race race = RaceState.get(player).orElse(null);
			List<String> chosen = new ArrayList<>();
			for (String pick : picks) {
				Talent.byId("awakening_" + pick).filter(t -> t.race == race && !chosen.contains(t.id())).ifPresent(t -> chosen.add(t.id()));
			}
			if (chosen.isEmpty()) {
				player.sendSystemMessage(Component.literal("Mage's Legacy: none of these Awakenings belong to your race"));
				return;
			}
			List<String> owned = new ArrayList<>(chosen);
			for (String id : Talents.owned(player)) {
				Talent t = Talent.byId(id).orElse(null);
				// Awakenings and their own talents not chosen go; everything else stays
				if (t != null && t.kind != Talent.Kind.AWAKENING && (t.awakening() == null || chosen.contains(t.awakening().id())) && !owned.contains(id)) {
					owned.add(id);
				}
			}
			player.setData(RaceState.TALENTS, owned);
			int level = chosen.size() > 1 ? Awakenings.SUPERLATE_LEVEL : Talent.AWAKENING_LEVEL;
			if (RaceState.level(player) < level) {
				Progression.setLevel(player, level);
			}
		});
	}

	private static int race(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
		String id = StringArgumentType.getString(ctx, "race");
		Race race = Race.byId(id).orElse(null);
		if (race == null && !id.equals("none")) {
			ctx.getSource().sendFailure(Component.literal("Unknown race: " + id));
			return 0;
		}
		return each(ctx, player -> RaceState.set(player, race));
	}

	private interface Action {
		void apply(ServerPlayer player);
	}

	private static int each(CommandContext<CommandSourceStack> ctx, Action action) throws CommandSyntaxException {
		Collection<ServerPlayer> players = EntityArgument.getPlayers(ctx, "targets");
		for (ServerPlayer player : players) {
			action.apply(player);
			RaceState.sync(player);
		}
		ctx.getSource().sendSuccess(() -> Component.literal("Mage's Legacy: " + players.size() + " player(s) updated"), true);
		return players.size();
	}
}
