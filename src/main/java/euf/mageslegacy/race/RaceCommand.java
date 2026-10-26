package euf.mageslegacy.race;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import euf.mageslegacy.bloodkin.Bloodkin;
import euf.mageslegacy.frozen.Frozen;
import euf.mageslegacy.mycelid.Mycelid;
import euf.mageslegacy.net.RacePackets;
import euf.mageslegacy.shepherd.Shepherd;
import euf.mageslegacy.voidborn.Voidborn;
import java.util.Arrays;
import java.util.Collection;
import net.minecraft.command.CommandSource;
import net.minecraft.command.argument.EntityArgumentType;
import net.minecraft.server.command.CommandManager;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;

/** /race shows your race; operators can set or reset races and hand out experience and resource. */
public final class RaceCommand {
	private RaceCommand() {
	}

	public static void register(CommandDispatcher<ServerCommandSource> dispatcher) {
		dispatcher.register(CommandManager.literal("race")
				.executes(RaceCommand::show)
				.then(CommandManager.literal("set").requires(s -> s.hasPermissionLevel(2))
						.then(CommandManager.argument("targets", EntityArgumentType.players())
								.then(CommandManager.argument("race", StringArgumentType.word())
										.suggests((c, b) -> CommandSource.suggestMatching(Arrays.stream(Race.values()).map(Race::id), b))
										.executes(RaceCommand::set))))
				.then(CommandManager.literal("reset").requires(s -> s.hasPermissionLevel(2))
						.then(CommandManager.argument("targets", EntityArgumentType.players()).executes(RaceCommand::reset)))
				.then(CommandManager.literal("xp").requires(s -> s.hasPermissionLevel(2))
						.then(CommandManager.argument("targets", EntityArgumentType.players())
								.then(CommandManager.argument("amount", IntegerArgumentType.integer(0))
										.executes(c -> each(c, (p, d) -> Progression.addXp(p, d, IntegerArgumentType.getInteger(c, "amount")))))))
				.then(CommandManager.literal("resource").requires(s -> s.hasPermissionLevel(2))
						.then(CommandManager.argument("targets", EntityArgumentType.players())
								.then(CommandManager.argument("amount", IntegerArgumentType.integer(0, 100))
										.executes(c -> each(c, (p, d) -> d.addResource(IntegerArgumentType.getInteger(c, "amount") - d.resource)))))));
	}

	private interface Action {
		void apply(ServerPlayerEntity player, RaceData data);
	}

	private static int each(CommandContext<ServerCommandSource> c, Action action) throws CommandSyntaxException {
		Collection<ServerPlayerEntity> players = EntityArgumentType.getPlayers(c, "targets");
		for (ServerPlayerEntity player : players) {
			RaceData data = RaceState.of(player);
			action.apply(player, data);
			RacePackets.sync(player, data);
		}
		return players.size();
	}

	private static int show(CommandContext<ServerCommandSource> c) throws CommandSyntaxException {
		ServerPlayerEntity player = c.getSource().getPlayerOrThrow();
		RaceData data = RaceState.of(player);
		if (data.race == null) {
			c.getSource().sendFeedback(() -> Text.translatable("mages_legacy.command.no_race"), false);
		} else {
			c.getSource().sendFeedback(() -> Text.translatable("mages_legacy.command.show", Text.translatable(data.race.key()),
					data.level, data.freePoints()), false);
		}
		return 1;
	}

	private static int set(CommandContext<ServerCommandSource> c) throws CommandSyntaxException {
		Race race = Race.byId(StringArgumentType.getString(c, "race")).orElse(null);
		if (race == null) {
			c.getSource().sendError(Text.translatable("mages_legacy.command.unknown_race"));
			return 0;
		}
		return each(c, (p, d) -> {
			cleanup(p, d);
			d.reset(race);
		});
	}

	private static int reset(CommandContext<ServerCommandSource> c) throws CommandSyntaxException {
		return each(c, (p, d) -> {
			cleanup(p, d);
			d.reset(null);
		});
	}

	/** What the old race left on the player: Mycelid armour and health bonuses, a Shepherd's echoes and beasts. */
	private static void cleanup(ServerPlayerEntity player, RaceData data) {
		Mycelid.removeCapArmor(player);
		if (data.race == Race.SHEPHERD) {
			Shepherd.cleanup(player, data);
		}
		if (data.race == Race.FROZEN) {
			Frozen.cleanup(player);
		}
		if (data.race == Race.BLOODKIN) {
			Bloodkin.cleanup(player);
		}
		if (data.race == Race.VOIDBORN) {
			Voidborn.cleanup(player);
		}
	}
}
