package euf.mageslegacy.client;

import euf.mageslegacy.MagesLegacy;
import euf.mageslegacy.client.screen.LoreScreen;
import euf.mageslegacy.client.screen.RaceSelectScreen;
import euf.mageslegacy.race.Progression;
import euf.mageslegacy.race.Race;
import euf.mageslegacy.race.RaceState;
import euf.mageslegacy.voidborn.Pocket;
import java.io.IOException;
import java.nio.file.Files;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.function.BooleanSupplier;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Difficulty;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.monster.Husk;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.LevelSettings;
import net.minecraft.world.level.WorldDataConfiguration;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.levelgen.WorldOptions;
import net.minecraft.world.level.levelgen.presets.WorldPresets;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.common.NeoForge;

/**
 * The CI scenario (run with -Dmages_legacy.harness=true): photographs every page of the legend and every race on the
 * choice screen, creates a flat world and checks that joining it without a race opens the legend, then becomes a
 * Voidborn and tries the race for real: the Void Step through a wall, Heavier Gravity on a husk, the Pocket there and
 * back, water burning. Writes harness-result.txt into the game directory and quits.
 */
final class Harness {
	/** One step: waits until it may run, runs, then lets this many ticks pass before the next one. */
	private record Step(String name, BooleanSupplier ready, Runnable action, int after) {
	}

	private static final int TIMEOUT = 20 * 180;
	private static final Deque<Step> STEPS = new ArrayDeque<>();
	private static final List<String> RESULTS = new ArrayList<>();
	private static int waiting;
	private static int idle;
	private static boolean finished;
	/** Things the scenario remembers between steps. */
	private static Husk husk;
	private static Vec3 beforePocket;
	private static float healthBeforeWater;

	private Harness() {
	}

	static void start() {
		Minecraft mc = Minecraft.getInstance();
		then("title", () -> mc.getOverlay() == null && mc.screen instanceof TitleScreen && mc.player == null, () -> mc.setScreen(new LoreScreen()), 20);
		for (int page = 0; page < 5; page++) {
			int index = page;
			run(() -> ((LoreScreen) mc.screen).showPage(index), 15);
			shot("lore_" + (page + 1));
		}
		run(() -> ((LoreScreen) mc.screen).next(), 10);
		for (Race race : Race.values()) {
			run(() -> {
				RaceSelectScreen screen = (RaceSelectScreen) mc.screen;
				screen.select(race);
				check(race.id() + (race.playable ? " can be chosen" : " sleeps"), screen.canChoose() == race.playable);
			}, 10);
			shot("choose_" + race.id());
		}
		run(Harness::createWorld, 0);
		then("join opens the legend", () -> mc.player != null && mc.screen instanceof LoreScreen, () -> check("join opens the legend", true), 60);
		shot("join_legend");

		// Becoming a Voidborn through the screens, as a player would
		run(() -> ((LoreScreen) mc.screen).next(), 10);
		run(() -> {
			RaceSelectScreen screen = (RaceSelectScreen) mc.screen;
			screen.select(Race.VOIDBORN);
			screen.choose();
		}, 30);
		run(() -> check("chose the Voidborn", RaceState.is(player(), Race.VOIDBORN) && mc.screen == null), 0);
		run(() -> server().execute(Harness::setUp), 40);
		shot("hud");

		// The Void Step through a wall
		run(() -> MagesLegacyClient.useAbility(0), 12);
		shot("step_mid");
		run(() -> {
		}, 25);
		run(() -> {
			ServerPlayer player = player();
			check("void step went through the wall (z=" + String.format("%.1f", player.getZ()) + ")",
					player.getZ() > 6 && player.level().noCollision(player));
		}, 5);
		shot("step_end");

		// The same walk seen from behind: the rifts and the path
		run(() -> server().execute(() -> {
			ServerPlayer player = player();
			java.util.Arrays.fill(euf.mageslegacy.voidborn.Voidborn.state(player).cooldown, 0);
			RaceState.setResource(player, RaceState.MAX_RESOURCE);
			player.connection.teleport(player.getX(), player.getY(), player.getZ(), 180, 0);
		}), 10);
		run(() -> mc.options.setCameraType(net.minecraft.client.CameraType.THIRD_PERSON_BACK), 5);
		run(() -> MagesLegacyClient.useAbility(0), 5);
		shot("step_side_start");
		run(() -> {
		}, 8);
		shot("step_side_mid");
		run(() -> {
		}, 10);
		shot("step_side_end");
		run(() -> {
		}, 15);

		// Heavier Gravity on a husk (a zombie would burn in the sun and spoil the count)
		run(() -> server().execute(() -> {
			ServerPlayer player = player();
			husk = EntityType.HUSK.create(player.serverLevel());
			husk.moveTo(player.getX() + 1.2, player.getY(), player.getZ() + 1.2, 0, 0);
			husk.setNoAi(true);
			husk.setPersistenceRequired();
			player.serverLevel().addFreshEntity(husk);
		}), 10);
		run(() -> server().execute(() -> RaceState.setResource(player(), RaceState.MAX_RESOURCE)), 2);
		run(() -> MagesLegacyClient.useAbility(1), 10);
		shot("gravity_side");
		run(() -> mc.options.setCameraType(net.minecraft.client.CameraType.FIRST_PERSON), 3);
		shot("gravity");
		run(() -> {
		}, 40);
		run(() -> check("gravity hurt and slowed the husk (" + husk.getHealth() + "/" + husk.getMaxHealth() + ")",
				husk.getHealth() <= husk.getMaxHealth() - 2 && husk.hasEffect(MobEffects.MOVEMENT_SLOWDOWN)), 5);

		// The Pocket and back
		run(() -> beforePocket = player().position(), 0);
		run(() -> mc.options.setCameraType(net.minecraft.client.CameraType.THIRD_PERSON_BACK), 3);
		run(() -> MagesLegacyClient.useAbility(3), 8);
		shot("pocket_rift");
		run(() -> {
		}, 40);
		run(() -> mc.options.setCameraType(net.minecraft.client.CameraType.FIRST_PERSON), 2);
		run(() -> {
			ServerPlayer player = player();
			MinecraftServer server = server();
			BlockPos heart = Pocket.platformCentre(server, player.getUUID());
			check("pocket entered with its platform", Pocket.isPocket(player.level())
					&& player.serverLevel().getBlockState(heart).is(Blocks.CRYING_OBSIDIAN));
			check("pocket keeps its edge", Pocket.canBuild(server, heart.offset(1, 1, 1)) && !Pocket.canBuild(server, heart.offset(4, 1, 0)));
			mc.player.setXRot(-10);
		}, 20);
		shot("pocket");
		run(() -> MagesLegacyClient.useAbility(3), 50);
		run(() -> {
			ServerPlayer player = player();
			check("pocket returned to where it was opened", !Pocket.isPocket(player.level()) && player.position().distanceTo(beforePocket) < 2);
		}, 5);
		shot("pocket_back");

		// The talent tree: open it, learn three talents, see the Pocket grow
		run(() -> server().execute(() -> Progression.setLevel(player(), 20)), 10);
		run(() -> mc.setScreen(new euf.mageslegacy.client.screen.TalentScreen()), 20);
		shot("talents");
		run(() -> {
			var screen = (euf.mageslegacy.client.screen.TalentScreen) mc.screen;
			screen.learn(euf.mageslegacy.race.Talent.POCKET_WIDE_1);
			screen.learn(euf.mageslegacy.race.Talent.STEP_FAR);
			screen.learn(euf.mageslegacy.race.Talent.GRAV_WIDE);
		}, 20);
		run(() -> {
			ServerPlayer player = player();
			check("talents learnt", euf.mageslegacy.race.Talents.has(player, euf.mageslegacy.race.Talent.POCKET_WIDE_1)
					&& euf.mageslegacy.race.Talents.has(player, euf.mageslegacy.race.Talent.STEP_FAR)
					&& euf.mageslegacy.race.Talents.has(player, euf.mageslegacy.race.Talent.GRAV_WIDE));
			check("pocket grew to 9x9", Pocket.sizeFor(player) == 9
					&& Pocket.canBuild(server(), Pocket.platformCentre(server(), player.getUUID()).offset(4, 1, 4)));
			check("an awakening can't be learnt yet", euf.mageslegacy.race.Talents.refusal(player, euf.mageslegacy.race.Talent.AWAKENING_HOLE) != null);
		}, 5);
		shot("talents_learnt");
		run(() -> mc.setScreen(null), 5);
		run(() -> {
			var screen = new euf.mageslegacy.client.screen.TalentScreen();
			mc.setScreen(screen);
		}, 10);
		run(() -> mc.screen.mouseClicked(mc.screen.width / 2 + 136, 18, 0), 10);
		shot("talents_guide");
		run(() -> mc.setScreen(null), 5);

		// The Black Hole: trial counted, the path learnt, a husk 7 blocks off pulled in and crushed
		run(() -> server().execute(() -> {
			ServerPlayer player = player();
			Progression.setLevel(player, 45);
			RaceState.setResource(player, RaceState.MAX_RESOURCE);
			euf.mageslegacy.race.Talents.addProgress(player, euf.mageslegacy.race.Talents.GRAVITY_KILLS, 100);
			euf.mageslegacy.race.Talents.learn(player, "grav_pull");
			euf.mageslegacy.race.Talents.learn(player, "awakening_hole");
			husk = EntityType.HUSK.create(player.serverLevel());
			husk.moveTo(player.getX() + 7, player.getY(), player.getZ(), 0, 0);
			husk.setPersistenceRequired();
			player.serverLevel().addFreshEntity(husk);
			player.setHealth(player.getMaxHealth());
		}), 10);
		run(() -> check("black hole learnt", euf.mageslegacy.race.Talents.has(player(), euf.mageslegacy.race.Talent.AWAKENING_HOLE)), 0);
		run(() -> mc.options.setCameraType(net.minecraft.client.CameraType.THIRD_PERSON_BACK), 3);
		run(() -> MagesLegacyClient.useAbility(2), 40);
		shot("black_hole");
		run(() -> {
		}, 30);
		shot("black_hole_pull");
		run(() -> {
		}, 30);
		run(() -> {
			ServerPlayer player = player();
			check("black hole pulled and crushed the husk (" + husk.getHealth() + ", d=" + String.format("%.1f", husk.distanceTo(player)) + ")",
					!husk.isAlive() || husk.getHealth() < husk.getMaxHealth() - 8);
			check("collapse left the Voidborn alive (" + player.getHealth() + ")", player.getHealth() >= 1 && player.isAlive());
			server().execute(() -> {
				husk.discard();
				player.setHealth(player.getMaxHealth());
			});
		}, 5);

		// The second Awakening: the superlate trial is marked passed, the arena path learnt, Alt opens the arena
		run(() -> server().execute(() -> {
			ServerPlayer player = player();
			euf.mageslegacy.race.Talents.addProgress(player, euf.mageslegacy.race.Talents.SUPERLATE, 1);
			for (String id : new String[] {"pocket_wide_2", "pocket_wide_3", "awakening_rift"}) {
				euf.mageslegacy.race.Talents.learn(player, id);
			}
			RaceState.setResource(player, RaceState.MAX_RESOURCE);
			husk = EntityType.HUSK.create(player.serverLevel());
			husk.moveTo(player.getX() + 3, player.getY(), player.getZ() + 3, 0, 0);
			husk.setNoAi(true);
			husk.setPersistenceRequired();
			player.serverLevel().addFreshEntity(husk);
		}), 10);
		run(() -> {
			ServerPlayer player = player();
			check("two awakenings, the third closed", euf.mageslegacy.race.Talents.has(player, euf.mageslegacy.race.Talent.AWAKENING_RIFT)
					&& euf.mageslegacy.race.Talents.refusal(player, euf.mageslegacy.race.Talent.AWAKENING_WORLDS) != null);
		}, 0);
		run(() -> MagesLegacyClient.useAbility(4), 10);
		shot("arena_opening");
		run(() -> {
		}, 40);
		run(() -> {
			ServerPlayer player = player();
			check("arena took the Voidborn and the husk", euf.mageslegacy.voidborn.Arena.isArena(player.level())
					&& !player.serverLevel().getEntitiesOfClass(Husk.class, player.getBoundingBox().inflate(30)).isEmpty());
		}, 5);
		shot("arena");
		run(() -> MagesLegacyClient.useAbility(4), 40);
		run(() -> check("arena ended back home", !euf.mageslegacy.voidborn.Arena.isArena(player().level())), 5);
		shot("arena_back");
		run(() -> mc.options.setCameraType(net.minecraft.client.CameraType.FIRST_PERSON), 3);

		// Water burns
		run(() -> server().execute(() -> {
			ServerPlayer player = player();
			healthBeforeWater = player.getHealth();
			player.serverLevel().setBlockAndUpdate(player.blockPosition(), Blocks.WATER.defaultBlockState());
		}), 50);
		run(() -> {
			ServerPlayer player = player();
			check("water burns the Voidborn (" + healthBeforeWater + " -> " + player.getHealth() + ")", player.getHealth() < healthBeforeWater);
			server().execute(() -> {
				player.serverLevel().setBlockAndUpdate(player.blockPosition(), Blocks.AIR.defaultBlockState());
				player.setHealth(player.getMaxHealth());
			});
		}, 5);
		run(Harness::finish, 0);
		NeoForge.EVENT_BUS.addListener(Harness::tick);
	}

	private static MinecraftServer server() {
		return Minecraft.getInstance().getSingleplayerServer();
	}

	private static ServerPlayer player() {
		return server().getPlayerList().getPlayer(Minecraft.getInstance().player.getUUID());
	}

	/** Level 5 for every ability, full Void, noon, and a stone wall 4 blocks south of the player, who faces it. */
	private static void setUp() {
		ServerPlayer player = player();
		ServerLevel level = player.serverLevel();
		Progression.setLevel(player, 5);
		RaceState.setResource(player, RaceState.MAX_RESOURCE);
		level.setDayTime(6000);
		int y = player.getBlockY();
		player.teleportTo(level, 0.5, y, 0.5, 0, 0);
		for (int x = -2; x <= 2; x++) {
			for (int dy = 0; dy < 3; dy++) {
				level.setBlockAndUpdate(new BlockPos(x, y + dy, 4), Blocks.STONE.defaultBlockState());
			}
		}
	}

	private static void then(String name, BooleanSupplier ready, Runnable action, int after) {
		STEPS.add(new Step(name, ready, action, after));
	}

	private static void run(Runnable action, int after) {
		then("step", () -> true, action, after);
	}

	private static void shot(String name) {
		run(() -> {
			Minecraft mc = Minecraft.getInstance();
			Screenshot.grab(mc.gameDirectory, name + ".png", mc.getMainRenderTarget(), message -> {
			});
		}, 2);
	}

	private static void check(String name, boolean ok) {
		RESULTS.add((ok ? "PASS " : "FAIL ") + name);
	}

	private static void createWorld() {
		Minecraft mc = Minecraft.getInstance();
		GameRules rules = new GameRules();
		rules.getRule(GameRules.RULE_DAYLIGHT).set(false, null);
		rules.getRule(GameRules.RULE_WEATHER_CYCLE).set(false, null);
		rules.getRule(GameRules.RULE_DOMOBSPAWNING).set(false, null);
		LevelSettings settings = new LevelSettings("harness", GameType.SURVIVAL, false, Difficulty.NORMAL, true, rules, WorldDataConfiguration.DEFAULT);
		mc.createWorldOpenFlows().createFreshLevel("harness", settings, new WorldOptions(1L, false, false),
				registries -> registries.registryOrThrow(Registries.WORLD_PRESET).getHolderOrThrow(WorldPresets.FLAT).value().createWorldDimensions(),
				new TitleScreen());
	}

	private static void tick(ClientTickEvent.Post event) {
		if (finished) {
			return;
		}
		if (waiting > 0) {
			waiting--;
			return;
		}
		Step step = STEPS.peek();
		if (step == null) {
			return;
		}
		if (!step.ready().getAsBoolean()) {
			if (++idle > TIMEOUT) {
				check(step.name() + " (timed out)", false);
				finish();
			}
			return;
		}
		idle = 0;
		STEPS.poll();
		try {
			step.action().run();
		} catch (RuntimeException e) {
			MagesLegacy.LOGGER.error("Harness step failed", e);
			check(step.name() + " threw " + e, false);
			finish();
			return;
		}
		waiting = step.after();
	}

	private static void finish() {
		if (finished) {
			return;
		}
		finished = true;
		Minecraft mc = Minecraft.getInstance();
		RESULTS.add("DONE");
		try {
			Files.write(mc.gameDirectory.toPath().resolve("harness-result.txt"), RESULTS);
		} catch (IOException e) {
			MagesLegacy.LOGGER.error("Can't write the harness result", e);
		}
		RESULTS.forEach(line -> MagesLegacy.LOGGER.info("[harness] {}", line));
		mc.stop();
	}
}
