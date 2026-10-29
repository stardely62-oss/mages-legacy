package euf.mageslegacy.client;

import euf.mageslegacy.MagesLegacy;
import euf.mageslegacy.client.screen.LoreScreen;
import euf.mageslegacy.client.screen.RaceSelectScreen;
import euf.mageslegacy.race.Race;
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
import net.minecraft.core.registries.Registries;
import net.minecraft.world.Difficulty;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.LevelSettings;
import net.minecraft.world.level.WorldDataConfiguration;
import net.minecraft.world.level.levelgen.WorldOptions;
import net.minecraft.world.level.levelgen.presets.WorldPresets;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.common.NeoForge;

/**
 * The CI scenario (run with -Dmages_legacy.harness=true): photographs every page of the legend and every race on the
 * choice screen, checks that all of them still sleep, then creates a flat world and checks that joining it without a
 * race opens the legend. Writes harness-result.txt into the game directory and quits.
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
				check(race.id() + " sleeps", !screen.canChoose());
			}, 10);
			shot("choose_" + race.id());
		}
		run(Harness::createWorld, 0);
		then("join opens the legend", () -> mc.player != null && mc.screen instanceof LoreScreen, () -> check("join opens the legend", true), 60);
		shot("join_legend");
		run(() -> finish(), 0);
		NeoForge.EVENT_BUS.addListener(Harness::tick);
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
		LevelSettings settings = new LevelSettings("harness", GameType.CREATIVE, false, Difficulty.PEACEFUL, true, new GameRules(),
				WorldDataConfiguration.DEFAULT);
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
