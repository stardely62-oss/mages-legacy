package euf.mageslegacy.race;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.FloatArgumentType;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import java.util.Arrays;
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
 * {@code nocooldown <players> on|off} (for testing: abilities always ready, the resource always full).
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
								.suggests((ctx, builder) -> SharedSuggestionProvider.suggest(java.util.List.of("rift", "worlds", "hole", "bloom", "storm", "rebirth", "superlate"), builder))
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
		return SharedSuggestionProvider.suggest(java.util.List.of("rift", "worlds", "hole", "bloom", "storm", "rebirth"), builder);
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
