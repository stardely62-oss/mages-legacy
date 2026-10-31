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
	private static float yawBefore;
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
		run(() -> check("a new Voidborn has the Way of the Void tab, not the Colony's", done("void_root") && !done("myc_root")), 0);
		run(() -> server().execute(Harness::setUp), 40);
		shot("hud");

		// The Void Step through a wall
		run(() -> MagesLegacyClient.useAbility(0), 12);
		shot("step_mid");
		run(() -> {
		}, 30);
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
		run(() -> {
			yawBefore = mc.player.getYRot();
			MagesLegacyClient.useAbility(0);
		}, 1);
		film("step", 18, 2);
		run(() -> check("the step leaves the look alone (" + yawBefore + " -> " + mc.player.getYRot() + ")",
				Math.abs(mc.player.getYRot() - yawBefore) < 1), 5);

		// A glance at the ground a few blocks ahead leads onto that ground, not into "nowhere to come out"
		run(() -> server().execute(() -> {
			ServerPlayer player = player();
			player.connection.teleport(player.getX(), player.getY(), player.getZ(), 90, 35);
		}), 5);
		run(() -> server().execute(() -> {
			ServerPlayer player = player();
			net.minecraft.world.phys.Vec3 to = euf.mageslegacy.voidborn.Voidborn.stepTarget(player.serverLevel(), player);
			check("step at the ground nearby comes out on its surface (" + to + ")",
					to != null && Math.abs(to.y - player.getY()) < 0.01 && to.distanceTo(player.position()) >= 1.5);
		}), 5);
		// Into the wall with the look a little down: still through it, standing on the ground behind
		run(() -> server().execute(() -> {
			ServerPlayer player = player();
			int y = (int) Math.floor(player.getY());
			player.connection.teleport(0.5, y, 0.5, 0, 15);
		}), 5);
		run(() -> server().execute(() -> {
			ServerPlayer player = player();
			net.minecraft.world.phys.Vec3 to = euf.mageslegacy.voidborn.Voidborn.stepTarget(player.serverLevel(), player);
			check("step looking a little down still goes through the wall (" + to + ")",
					to != null && to.z > 5 && Math.abs(to.y - player.getY()) < 0.01);
		}), 5);
		// A look a little down over open ground (the ground farther than the step reaches): out on the ground ahead
		run(() -> server().execute(() -> {
			ServerPlayer player = player();
			player.connection.teleport(0.5, Math.floor(player.getY()), 0.5, 90, 5);
		}), 5);
		run(() -> server().execute(() -> {
			ServerPlayer player = player();
			net.minecraft.world.phys.Vec3 to = euf.mageslegacy.voidborn.Voidborn.stepTarget(player.serverLevel(), player);
			check("step a little down over open ground lands on it (" + to + ")",
					to != null && to.x < -6 && Math.abs(to.y - player.getY()) < 0.01);
		}), 5);
		// The sizes: a field of weight of radius 7 and a black hole pulling from 20 blocks
		run(() -> server().execute(() -> {
			ServerPlayer player = player();
			check("gravity radius 7, black hole 20 (" + euf.mageslegacy.voidborn.Voidborn.gravityReach(player) + ", "
					+ euf.mageslegacy.voidborn.BlackHole.radius(player) + ")", euf.mageslegacy.voidborn.Voidborn.gravityReach(player) == 7
					&& euf.mageslegacy.voidborn.BlackHole.radius(player) == 20);
		}), 2);
		// Through Worlds alone on the server: nobody to open it to, and nothing spent
		run(() -> server().execute(() -> {
			ServerPlayer player = player();
			float before = RaceState.resource(player);
			var why = euf.mageslegacy.voidborn.Passage.choose(player);
			check("a passage with nobody else online is refused (" + (why == null ? "null" : why.key()) + ")",
					why != null && RaceState.resource(player) == before);
		}), 2);
		run(() -> {
			int missing = 0;
			for (euf.mageslegacy.race.Talent t : euf.mageslegacy.race.Talent.of(Race.VOIDBORN)) {
				if (!net.minecraft.client.resources.language.I18n.exists(t.key() + ".name") || !net.minecraft.client.resources.language.I18n.exists(t.key() + ".desc")) {
					missing++;
					MagesLegacy.LOGGER.error("Talent without text: {}", t.id());
				}
			}
			check("the tree has ~60 nodes, each with a name and a description", euf.mageslegacy.race.Talent.of(Race.VOIDBORN).size() >= 55 && missing == 0);
		}, 0);

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
		run(() -> MagesLegacyClient.useAbility(1), 1);
		film("gravity", 10, 3);
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
		run(() -> MagesLegacyClient.useAbility(3), 1);
		film("pocket", 6, 2);
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
		run(() -> {
			yawBefore = mc.player.getYRot();
			MagesLegacyClient.useAbility(2);
		}, 1);
		film("black_hole", 10, 9);
		run(() -> {
			ServerPlayer player = player();
			check("the black hole leaves the look alone (" + yawBefore + " -> " + mc.player.getYRot() + ")",
					Math.abs(mc.player.getYRot() - yawBefore) < 1 && Math.abs(player.getYRot() - yawBefore) < 1);
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
		voidMastery();
		mycelid();
		run(Harness::finish, 0);
		NeoForge.EVENT_BUS.addListener(Harness::tick);
	}

	/** The Mycelid: neurospores on a husk, symbiosis, two nodes that join by themselves, a journey, the screens. */
	private static void mycelid() {
		Minecraft mc = Minecraft.getInstance();
		int[] y = new int[1];
		run(() -> server().execute(() -> {
			ServerPlayer player = player();
			RaceState.set(player, Race.MYCELID);
			Progression.setLevel(player, 10);
			RaceState.setResource(player, RaceState.MAX_RESOURCE);
			y[0] = (int) Math.floor(player.getY());
			player.connection.teleport(40.5, y[0], 40.5, 0, 0);
			husk = EntityType.HUSK.create(player.serverLevel());
			husk.moveTo(42.5, y[0], 42.5, 0, 0);
			husk.setNoAi(true);
			husk.setPersistenceRequired();
			player.serverLevel().addFreshEntity(husk);
		}), 10);
		run(() -> check("became a Mycelid", RaceState.is(player(), Race.MYCELID)), 0);
		run(() -> check("a Voidborn turned Mycelid: the Colony's tab open, the Void's archived, not lost (" + euf.mageslegacy.race.Deeds.archived(player())
				.size() + " kept)", done("myc_root") && !done("void_root") && !done("void_walls")
				&& euf.mageslegacy.race.Deeds.archived(player()).contains("mages_legacy:legacy/void_walls")), 0);
		run(() -> mc.options.setCameraType(net.minecraft.client.CameraType.THIRD_PERSON_BACK), 3);
		run(() -> MagesLegacyClient.useAbility(0), 1);
		film("spores", 8, 3);
		run(() -> check("neurospores weaken the husk", husk.hasEffect(net.minecraft.world.effect.MobEffects.WEAKNESS)), 0);
		// A tamed wolf beside the Mycelid, to share the symbiosis with
		run(() -> server().execute(() -> {
			ServerPlayer player = player();
			var wolf = EntityType.WOLF.create(player.serverLevel());
			wolf.moveTo(player.getX() - 3, player.getY(), player.getZ() + 1, 0, 0);
			wolf.tame(player);
			wolf.setOrderedToSit(true);
			player.serverLevel().addFreshEntity(wolf);
		}), 5);
		run(() -> MagesLegacyClient.useAbility(1), 1);
		film("symbiosis", 6, 4);
		run(() -> {
			var speed = player().getAttribute(net.minecraft.world.entity.ai.attributes.Attributes.MOVEMENT_SPEED);
			check("neurosymbiosis quickens the Mycelid", speed != null && speed.getModifier(MagesLegacy.id("symbiosis_speed")) != null);
		}, 0);
		// Two nodes 20 blocks apart join by themselves; the journey from the second to the first
		run(() -> server().execute(() -> {
			husk.discard();
			euf.mageslegacy.mycelid.MyceliumNetwork.grow(player());
			player().connection.teleport(60.5, y[0], 40.5, 0, 0);
		}), 5);
		run(() -> server().execute(() -> euf.mageslegacy.mycelid.MyceliumNetwork.grow(player())), 5);
		run(() -> {
			ServerPlayer player = player();
			check("two nodes grown", euf.mageslegacy.mycelid.MyceliumNetwork.get(server()).nodesOf(player.getUUID()).size() == 2);
			server().execute(() -> player.connection.teleport(61.5, y[0], 41.5, 90, 0));
		}, 5);
		run(() -> server().execute(() -> {
			ServerPlayer player = player();
			euf.mageslegacy.mycelid.MyceliumNetwork.openNode(player, player.serverLevel(), new net.minecraft.core.BlockPos(60, y[0], 40));
		}), 10);
		shot("network_node");
		run(() -> {
			if (mc.screen instanceof euf.mageslegacy.client.screen.MyceliumScreen screen) {
				screen.travelFirst();
			}
		}, 10);
		film("network_travel", 8, 6);
		run(() -> {
			ServerPlayer player = player();
			check("travelled through the network to the first node (x=" + String.format("%.1f", player.getX()) + ")",
					Math.abs(player.getX() - 40.5) < 3 && Math.abs(player.getZ() - 40.5) < 3);
		}, 5);
		run(() -> server().execute(() -> euf.mageslegacy.mycelid.MyceliumNetwork.openColony(player())), 10);
		shot("network_colony");
		// The network at server scale, on a network of its own: limits, far threads, colonies, closed nodes, a restart
		run(() -> RESULTS.addAll(server().submit(() -> euf.mageslegacy.mycelid.MyceliumNetwork.selfCheck(player())).join()), 5);
		run(() -> mc.setScreen(new euf.mageslegacy.client.screen.TalentScreen()), 20);
		shot("talents_mycelid");
		run(() -> mc.setScreen(null), 5);

		// The Awakenings: the Great Bloom on Z and the Spore Storm on Alt
		run(() -> server().execute(() -> {
			ServerPlayer player = player();
			Progression.setLevel(player, 50);
			euf.mageslegacy.race.Talents.learnAll(player, "bloom", "storm");
			euf.mageslegacy.race.Talents.setNoCooldown(player, true);
			player.connection.teleport(46.5, y[0], 46.5, 135, 10);
		}), 10);
		run(() -> MagesLegacyClient.useAbility(2), 40);
		run(() -> check("the Great Bloom grows", euf.mageslegacy.mycelid.Awaken.blooming(player())), 0);
		film("bloom", 8, 4);
		run(() -> MagesLegacyClient.useAbility(2), 10);
		run(() -> check("pressed again, the Bloom ends and exhausts", !euf.mageslegacy.mycelid.Awaken.blooming(player())
				&& euf.mageslegacy.mycelid.Awaken.exhausted(player())), 20);
		run(() -> server().execute(() -> {
			ServerPlayer player = player();
			// The testing mode refills the spores every tick: off, to see the storm take them
			euf.mageslegacy.race.Talents.setNoCooldown(player, false);
			java.util.Arrays.fill(euf.mageslegacy.voidborn.Voidborn.state(player).cooldown, 0);
			RaceState.setResource(player, RaceState.MAX_RESOURCE);
			husk = EntityType.HUSK.create(player.serverLevel());
			husk.moveTo(player.getX() - 2.2, player.getY(), player.getZ() - 2.2, 0, 0);
			husk.setNoAi(true);
			husk.setPersistenceRequired();
			player.serverLevel().addFreshEntity(husk);
		}), 5);
		// Neurospores first, so the husk wears two badges side by side
		run(() -> MagesLegacyClient.useAbility(0), 2);
		run(() -> MagesLegacyClient.useAbility(4), 25);
		run(() -> {
			var weak = husk.getEffect(net.minecraft.world.effect.MobEffects.WEAKNESS);
			check("the Spore Storm weakens hard (" + (weak == null ? "none" : weak.getAmplifier()) + ")", weak != null && weak.getAmplifier() >= 1);
			check("the Spore Storm takes the spores (" + RaceState.resource(player()) + ")", RaceState.resource(player()) < 50);
		}, 0);
		film("storm", 6, 4);
		bloomAndStorm();
		// Mycelial Rebirth: a reserve body at the Mother Mycelium, a killing blow far away
		run(() -> server().execute(() -> {
			ServerPlayer player = player();
			husk.discard();
			euf.mageslegacy.race.Talents.reset(player);
			euf.mageslegacy.race.Talents.learnAll(player, "rebirth", "bloom");
			euf.mageslegacy.race.Talents.setNoCooldown(player, true);
			// To one side of the node, the body off the centre of the view (the storm gone first)
			player.connection.teleport(44.5, y[0], 40.5, 115, 20);
		}), 240);
		run(() -> MagesLegacyClient.useAbility(2), 4);
		film("body", 8, 12);
		run(() -> server().execute(() -> {
			ServerPlayer player = player();
			euf.mageslegacy.mycelid.MyceliumNetwork.ripenBody(player);
			player.connection.teleport(90.5, y[0], 90.5, 0, 0);
		}), 10);
		// Blooming far away when the blow comes: the Bloom ends as it would, its cooldown starts
		run(() -> server().execute(() -> {
			ServerPlayer player = player();
			euf.mageslegacy.race.Talents.setNoCooldown(player, false);
			java.util.Arrays.fill(euf.mageslegacy.voidborn.Voidborn.state(player).cooldown, 0);
			RaceState.setResource(player, RaceState.MAX_RESOURCE);
		}), 3);
		run(() -> MagesLegacyClient.useAbility(4), 10);
		run(() -> check("blooming before the blow", euf.mageslegacy.mycelid.Awaken.blooming(player())), 0);
		run(() -> server().execute(() -> player().hurt(player().damageSources().fellOutOfWorld(), 1000)), 10);
		film("rebirth", 6, 3);
		run(() -> {
			ServerPlayer player = player();
			int cd = euf.mageslegacy.voidborn.Voidborn.state(player).cooldown[4];
			check("reborn mid-Bloom, the Bloom is over with its 5-minute cooldown (" + cd + ")", !euf.mageslegacy.mycelid.Awaken.blooming(player)
					&& cd > 5900 && cd <= 6000);
			check("a killing blow wakes the reserve body by the Mother Mycelium (alive=" + player.isAlive() + ", x=" + String.format("%.1f", player.getX())
					+ ")", player.isAlive() && Math.abs(player.getX() - 40.5) < 3 && Math.abs(player.getZ() - 40.5) < 3);
			server().execute(() -> euf.mageslegacy.race.Talents.setNoCooldown(player, false));
		}, 5);
		mycelidMastery();
		balance(y);
		advancements();
		bloodkin();
		fungalDiet(y);
		run(() -> server().execute(() -> player().kill()), 20);
	}

	/** Talents, the testing mode off, cooldowns clear, Spores full, food low (no natural healing to blur the counts). */
	private static void fresh(String... talents) {
		server().execute(() -> {
			ServerPlayer player = player();
			euf.mageslegacy.race.Talents.setNoCooldown(player, false);
			player.setData(RaceState.TALENTS, new ArrayList<>(List.of(talents)));
			java.util.Arrays.fill(euf.mageslegacy.voidborn.Voidborn.state(player).cooldown, 0);
			euf.mageslegacy.mycelid.Awaken.reset(player);
			RaceState.setResource(player, RaceState.MAX_RESOURCE);
			player.getFoodData().setFoodLevel(10);
			player.getFoodData().setSaturation(0);
		});
	}

	private static net.minecraft.world.entity.animal.Wolf patient;

	/**
	 * The Mycelid's balance patch at work: the Bloom's healing (allies 2, Rich Spores 3, itself 1), the storm's Strength,
	 * Spores under the sun, the symbiosis' reflex and shared recovery, Healing Nodes resting after a blow, Glowing Paths'
	 * meal, cross talents needing both sides, Humble Body from afar and Gentle Awakening.
	 */
	private static void balance(int[] y) {
		crossTalents(Race.MYCELID);
		float[] at = new float[4];
		// A wounded tamed wolf beside the Mycelid
		run(() -> {
			fresh("awakening_bloom", "awakening_storm");
			server().execute(() -> {
				ServerPlayer player = player();
				player.connection.teleport(40.5, y[0], 43.5, 0, 0);
				patient = EntityType.WOLF.create(player.serverLevel());
				patient.moveTo(42.5, y[0], 43.5, 0, 0);
				patient.tame(player);
				patient.setOrderedToSit(true);
				player.serverLevel().addFreshEntity(patient);
			});
		}, 10);
		for (boolean rich : new boolean[] {false, true}) {
			run(() -> {
				if (rich) {
					fresh("awakening_bloom", "awakening_storm", "bloom_heal");
				}
				server().execute(() -> {
					patient.setHealth(2);
					player().setHealth(4);
				});
			}, 5);
			run(() -> MagesLegacyClient.useAbility(2), 1);
			run(() -> {
				at[0] = patient.getHealth();
				at[1] = player().getHealth();
			}, 60);
			run(() -> {
				float ally = patient.getHealth() - at[0];
				float self = player().getHealth() - at[1];
				check("the Bloom heals allies " + (rich ? 3 : 2) + " a second, itself 1 (3 s: +" + ally + ", +" + self + ")",
						ally >= (rich ? 7 : 5) && ally <= (rich ? 10 : 7) && self >= 2 && self <= 4 && patient.getHealth() <= patient.getMaxHealth());
			}, 0);
			run(() -> MagesLegacyClient.useAbility(2), 10);
		}
		// The storm: Speed and Strength for the Mycelid and its pet, no Haste; Shoulder to Shoulder adds Resistance
		run(() -> fresh("awakening_bloom", "awakening_storm", "storm_ally"), 5);
		run(() -> MagesLegacyClient.useAbility(4), 15);
		run(() -> {
			ServerPlayer player = player();
			check("the storm gives Speed I and Strength I, not Haste (" + player.hasEffect(net.minecraft.world.effect.MobEffects.DAMAGE_BOOST) + ")",
					player.hasEffect(net.minecraft.world.effect.MobEffects.DAMAGE_BOOST) && player.hasEffect(net.minecraft.world.effect.MobEffects.MOVEMENT_SPEED)
							&& player.hasEffect(net.minecraft.world.effect.MobEffects.DAMAGE_RESISTANCE)
							&& patient.hasEffect(net.minecraft.world.effect.MobEffects.DAMAGE_BOOST)
							&& !patient.hasEffect(net.minecraft.world.effect.MobEffects.DIG_SPEED));
		}, 0);
		// Spores under the day sun: half a second, a whole one with a Dense Shell
		for (boolean shell : new boolean[] {false, true}) {
			run(() -> {
				fresh(shell ? new String[] {"awakening_bloom", "sym_env"} : new String[] {"awakening_bloom"});
				server().execute(() -> RaceState.setResource(player(), 10));
			}, 5);
			run(() -> at[2] = RaceState.resource(player()), 100);
			run(() -> {
				float got = RaceState.resource(player()) - at[2];
				boolean sun = euf.mageslegacy.mycelid.Mycelid.inSunlight(player());
				check("under the sun " + (shell ? "with a Dense Shell " : "") + "5 s give " + got + " Spores", sun && (shell ? got > 4.4 && got < 5.6
						: got > 2.2 && got < 2.8));
			}, 0);
		}
		// Neurosymbiosis: the first blow 30% softer, 6 health back when the bond ends
		run(() -> {
			fresh("sym_reflex", "sym_share");
			server().execute(() -> player().setHealth(20));
		}, 5);
		run(() -> MagesLegacyClient.useAbility(1), 5);
		run(() -> server().execute(() -> {
			ServerPlayer player = player();
			player.invulnerableTime = 0;
			player.hurt(player.damageSources().generic(), 10);
		}), 3);
		run(() -> at[3] = player().getHealth(), 0);
		run(() -> check("Protective Reflex: 10 damage becomes 7 (" + (20 - at[3]) + ")", Math.abs(20 - at[3] - 7) < 0.01), 180);
		run(() -> check("Shared Recovery: 6 health once the bond ends (" + at[3] + " -> " + player().getHealth() + ")",
				Math.abs(player().getHealth() - at[3] - 6) < 0.01), 0);
		// Healing Nodes: by the Mother Mycelium, mending a point every 2 s, and resting 5 s after a blow
		run(() -> {
			fresh("heal_nodes");
			server().execute(() -> {
				player().connection.teleport(41.5, y[0], 41.5, 0, 0);
				player().setHealth(10);
			});
		}, 5);
		run(() -> at[0] = player().getHealth(), 90);
		run(() -> {
			check("Healing Nodes mend (" + at[0] + " -> " + player().getHealth() + ")", player().getHealth() - at[0] >= 1 && player().getHealth() - at[0] <= 3);
			server().execute(() -> {
				player().invulnerableTime = 0;
				player().hurt(player().damageSources().generic(), 1);
			});
		}, 3);
		run(() -> at[0] = player().getHealth(), 80);
		run(() -> check("...but not within 5 s of a blow (" + at[0] + " -> " + player().getHealth() + ")", player().getHealth() <= at[0]), 0);
		// Glowing Paths: up to 4, never past 16, once a minute
		run(() -> server().execute(() -> {
			ServerPlayer player = player();
			player.getFoodData().setFoodLevel(10);
			euf.mageslegacy.mycelid.MyceliumNetwork.glowFeed(player);
			int first = player.getFoodData().getFoodLevel();
			euf.mageslegacy.mycelid.MyceliumNetwork.glowFeed(player);
			int again = player.getFoodData().getFoodLevel();
			var tag = player.getData(RaceState.PROGRESS).copy();
			tag.remove("glow_fed_at");
			player.setData(RaceState.PROGRESS, tag);
			player.getFoodData().setFoodLevel(15);
			euf.mageslegacy.mycelid.MyceliumNetwork.glowFeed(player);
			int capped = player.getFoodData().getFoodLevel();
			check("Glowing Paths: 10 -> " + first + ", again within a minute " + again + ", from 15 up to " + capped, first == 14 && again == 14
					&& capped == 16);
		}), 3);
		// Cross talents: the Mycelid's need both sides, the Voidborn's keep one
		run(() -> {
			var spores = euf.mageslegacy.race.Talent.SPORE_NODES;
			var reach = euf.mageslegacy.race.Talent.NET_REACH;
			var quick = euf.mageslegacy.race.Talent.SPORE_QUICK;
			check("cross talents of both races need both neighbours", !euf.mageslegacy.race.Talents.linked(spores, t -> t == reach)
					&& euf.mageslegacy.race.Talents.linked(spores, t -> t == reach || t == quick)
					&& !euf.mageslegacy.race.Talents.linked(euf.mageslegacy.race.Talent.STEP_DOOR, t -> t == euf.mageslegacy.race.Talent.STEP_QUICK));
		}, 0);
		// Humble Body: from 50 blocks away, the body starts at the Mother Mycelium; a node not one's own is refused
		run(() -> {
			fresh("awakening_rebirth", "rebirth_cheap", "rebirth_soft");
			server().execute(() -> {
				ServerPlayer player = player();
				euf.mageslegacy.mycelid.MyceliumNetwork.harnessNutrients(player, 200);
				player.connection.teleport(90.5, y[0], 90.5, 0, 0);
				euf.mageslegacy.mycelid.MyceliumNetwork.act(player, "body", "minecraft:overworld@1,2,3", "");
			});
		}, 5);
		run(() -> server().execute(() -> euf.mageslegacy.mycelid.MyceliumNetwork.act(player(), "body",
				euf.mageslegacy.mycelid.MyceliumNetwork.key(net.minecraft.world.level.Level.OVERWORLD, new net.minecraft.core.BlockPos(40, y[0], 40)), "")), 5);
		run(() -> {
			String body = euf.mageslegacy.mycelid.MyceliumNetwork.bodyAt(player());
			check("Humble Body: grown from afar at one's own node (" + body + ")", body != null && body.endsWith("@40," + y[0] + ",40")
					&& player().distanceToSqr(90.5, y[0], 90.5) < 4);
			server().execute(() -> {
				euf.mageslegacy.mycelid.MyceliumNetwork.ripenBody(player());
				player().hurt(player().damageSources().fellOutOfWorld(), 1000);
			});
		}, 10);
		run(() -> server().execute(() -> player().addEffect(new net.minecraft.world.effect.MobEffectInstance(
				net.minecraft.world.effect.MobEffects.MOVEMENT_SLOWDOWN, 100, 1))), 3);
		run(() -> {
			ServerPlayer player = player();
			check("Gentle Awakening: reborn with no exhaustion of the rebirth and unslowed (" + euf.mageslegacy.mycelid.Awaken.exhausted(player) + ")",
					player.isAlive() && Math.abs(player.getX() - 40.5) < 3 && !euf.mageslegacy.mycelid.Awaken.exhausted(player)
							&& !player.hasEffect(net.minecraft.world.effect.MobEffects.MOVEMENT_SLOWDOWN));
			patient.discard();
		}, 0);
	}

	/** A second Awakening's node clicked at level 45: the trial starts here, nobody is taken anywhere. */
	private static void startMastery(String first, String near, String second) {
		double[] at = new double[3];
		run(() -> server().execute(() -> {
			ServerPlayer player = player();
			euf.mageslegacy.race.Talents.setNoCooldown(player, false);
			player.setData(RaceState.TALENTS, new ArrayList<>(List.of(first, near)));
			player.setData(RaceState.PROGRESS, new net.minecraft.nbt.CompoundTag());
			Progression.setLevel(player, 45);
			java.util.Arrays.fill(euf.mageslegacy.voidborn.Voidborn.state(player).cooldown, 0);
			RaceState.setResource(player, RaceState.MAX_RESOURCE);
			at[0] = player.getX();
			at[2] = player.getZ();
			euf.mageslegacy.race.Talents.learn(player, second);
		}), 10);
		run(() -> {
			ServerPlayer player = player();
			var tag = player.getData(RaceState.PROGRESS);
			check("level 45: the second Awakening's node starts the mastery trial, not an arena (" + second + ")",
					euf.mageslegacy.race.Mastery.started(tag) && !euf.mageslegacy.race.Talents.owned(player).contains(second)
							&& !euf.mageslegacy.voidborn.Arena.isArena(player.level()) && Math.abs(player.getX() - at[0]) < 0.5
							&& Math.abs(player.getZ() - at[2]) < 0.5);
			check("while it runs the node stays closed", "mages_legacy.talent.state.mastery_running".equals(
					euf.mageslegacy.race.Talents.refusal(player, euf.mageslegacy.race.Talent.byId(second).orElseThrow())));
		}, 0);
	}

	private static int mastery(euf.mageslegacy.race.Mastery.Goal goal) {
		return euf.mageslegacy.race.Mastery.count(player().getData(RaceState.PROGRESS), goal);
	}

	private static void husks(int count, double radius) {
		server().execute(() -> {
			ServerPlayer player = player();
			for (int i = 0; i < count; i++) {
				double a = Math.PI * 2 * i / count;
				var h = EntityType.HUSK.create(player.serverLevel());
				h.moveTo(player.getX() + Math.cos(a) * radius, player.getY(), player.getZ() + Math.sin(a) * radius, 0, 0);
				h.setNoAi(true);
				h.setPersistenceRequired();
				h.addTag("harness_mastery");
				player.serverLevel().addFreshEntity(h);
			}
		});
	}

	private static void clearHusks() {
		server().execute(() -> {
			for (ServerLevel level : server().getAllLevels()) {
				for (var e : level.getAllEntities()) {
					if (e.getTags().contains("harness_mastery")) {
						e.discard();
					}
				}
			}
		});
	}

	/**
	 * Every cross talent of a race: with one neighbour the node stays closed on the server and in the tree, with both it
	 * opens and is bought; one learnt under the old one-of rule is kept.
	 */
	private static void crossTalents(Race race) {
		for (var t : euf.mageslegacy.race.Talent.of(race)) {
			if (t.kind != euf.mageslegacy.race.Talent.Kind.CROSS) {
				continue;
			}
			var near = t.requires();
			run(() -> server().execute(() -> {
				ServerPlayer player = player();
				euf.mageslegacy.race.Talents.setNoCooldown(player, false);
				Progression.setLevel(player, 30);
				player.setData(RaceState.TALENTS, new ArrayList<>(List.of(near.get(0).id())));
				RaceState.sync(player);
			}), 6);
			run(() -> {
				String server = euf.mageslegacy.race.Talents.refusal(player(), t);
				String client = euf.mageslegacy.client.screen.TalentScreen.shownRefusal(t);
				check(t.id() + ": one neighbour is not enough (server " + server + ", tree " + client + ")",
						"mages_legacy.talent.state.needs".equals(server) && "mages_legacy.talent.state.needs".equals(client));
				server().execute(() -> {
					player().setData(RaceState.TALENTS, new ArrayList<>(List.of(near.get(0).id(), near.get(1).id())));
					RaceState.sync(player());
				});
			}, 6);
			run(() -> {
				String server = euf.mageslegacy.race.Talents.refusal(player(), t);
				String client = euf.mageslegacy.client.screen.TalentScreen.shownRefusal(t);
				server().execute(() -> euf.mageslegacy.race.Talents.learn(player(), t.id()));
				check(t.id() + ": both neighbours open it (server " + server + ", tree " + client + ")", server == null && client == null);
			}, 4);
			run(() -> {
				boolean bought = euf.mageslegacy.race.Talents.owned(player()).contains(t.id());
				// Learnt in an old world with one neighbour only: it stays
				server().execute(() -> player().setData(RaceState.TALENTS, new ArrayList<>(List.of(near.get(1).id(), t.id()))));
				check(t.id() + ": bought", bought);
			}, 3);
			run(() -> check(t.id() + ": an old one learnt with one neighbour is kept", euf.mageslegacy.race.Talents.has(player(), t)), 0);
		}
	}

	/** The Voidborn's trial done for real: a step through stone with a foe near, Heavier Gravity on four, the Pocket from two worlds. */
	private static void voidMastery() {
		crossTalents(Race.VOIDBORN);
		run(() -> server().execute(Harness::setUp), 10);
		startMastery("awakening_hole", "pocket_wide_3", "awakening_rift");
		run(() -> husks(1, 6), 5);
		run(() -> MagesLegacyClient.useAbility(0), 30);
		run(() -> check("a step through stone with a foe near counts (" + mastery(euf.mageslegacy.race.Mastery.Goal.STEP_THROUGH) + "/15)",
				mastery(euf.mageslegacy.race.Mastery.Goal.STEP_THROUGH) == 1 && done("void_walls")), 0);
		run(() -> husks(4, 3), 5);
		run(() -> MagesLegacyClient.useAbility(1), 50);
		run(() -> check("Heavier Gravity on four foes counts once (" + mastery(euf.mageslegacy.race.Mastery.Goal.GRAVITY_FOUR) + "/5)",
				mastery(euf.mageslegacy.race.Mastery.Goal.GRAVITY_FOUR) == 1 && done("void_gravity")), 0);
		// The trial's counts filled the way a long game would, but for the Pocket's two worlds, done for real below
		run(() -> server().execute(() -> {
			clearHusks();
			ServerPlayer player = player();
			var tag = player.getData(RaceState.PROGRESS).copy();
			tag.putInt(euf.mageslegacy.race.Mastery.Goal.STEP_THROUGH.tag, 15);
			tag.putInt(euf.mageslegacy.race.Mastery.Goal.GRAVITY_FOUR.tag, 5);
			player.setData(RaceState.PROGRESS, tag);
			java.util.Arrays.fill(euf.mageslegacy.voidborn.Voidborn.state(player).cooldown, 0);
		}), 5);
		// The same world twice is still one world
		pocketFrom(net.minecraft.world.level.Level.OVERWORLD, 1);
		pocketFrom(net.minecraft.world.level.Level.OVERWORLD, 1);
		run(() -> server().execute(() -> {
			ServerPlayer player = player();
			player.teleportTo(server().getLevel(net.minecraft.world.level.Level.NETHER), 0.5, 129, 0.5, 0, 0);
		}), 20);
		pocketFrom(net.minecraft.world.level.Level.NETHER, 2);
		run(() -> {
			ServerPlayer player = player();
			check("the trial is passed, the third goal counting worlds, not visits", euf.mageslegacy.race.Mastery.passed(player.getData(RaceState.PROGRESS))
					&& done("void_mastery") && done("void_pocket") && done("void_pocket_big"));
			server().execute(() -> {
				player.teleportTo(server().overworld(), 0.5, -60, 0.5, 0, 0);
				euf.mageslegacy.race.Talents.learn(player, "awakening_rift");
			});
		}, 20);
		run(() -> {
			ServerPlayer player = player();
			var owned = euf.mageslegacy.race.Talents.owned(player);
			check("passed, the second Awakening is bought for its points and sits on Alt", euf.mageslegacy.race.Awakenings.at(owned,
					euf.mageslegacy.race.Awakenings.FIRST_SLOT).orElse(null) == euf.mageslegacy.race.Ability.BLACK_HOLE
					&& euf.mageslegacy.race.Awakenings.at(owned, euf.mageslegacy.race.Awakenings.SECOND_SLOT).orElse(null) == euf.mageslegacy.race.Ability.RIFT_ARENA
					&& euf.mageslegacy.race.Talents.refusal(player, euf.mageslegacy.race.Talent.AWAKENING_WORLDS) != null && done("void_double"));
			// Saves from before: the arena trial's flag still opens the second Awakening; the trial is the race's, not a path's
			var old = new net.minecraft.nbt.CompoundTag();
			old.putInt(euf.mageslegacy.race.Talents.SUPERLATE, 1);
			var hole = List.of("awakening_hole");
			check("an old passed trial still counts, a new one starts, then runs; 44 is too early", euf.mageslegacy.race.Talents.awakeningRefusal(hole, 45,
					euf.mageslegacy.race.Talent.AWAKENING_WORLDS, old) == null
					&& "mages_legacy.talent.state.superlate_trial".equals(euf.mageslegacy.race.Talents.awakeningRefusal(hole, 45,
							euf.mageslegacy.race.Talent.AWAKENING_WORLDS, new net.minecraft.nbt.CompoundTag()))
					&& "mages_legacy.talent.state.superlate_level".equals(euf.mageslegacy.race.Talents.awakeningRefusal(hole, 44,
							euf.mageslegacy.race.Talent.AWAKENING_WORLDS, old)));
		}, 5);
	}

	/**
	 * Into the Pocket and safely back out, from where the player stands now; then the count of worlds. Each step waits
	 * for the server (a world generating for the first time can keep it a few seconds behind).
	 */
	private static void pocketFrom(net.minecraft.resources.ResourceKey<net.minecraft.world.level.Level> world, int expect) {
		then("standing in " + world.location().getPath(), () -> player().level().dimension() == world && player().onGround(), () -> {
		}, 20);
		run(() -> server().execute(() -> {
			java.util.Arrays.fill(euf.mageslegacy.voidborn.Voidborn.state(player()).cooldown, 0);
			RaceState.setResource(player(), RaceState.MAX_RESOURCE);
		}), 5);
		run(() -> MagesLegacyClient.useAbility(3), 5);
		then("into the Pocket from " + world.location().getPath(), () -> euf.mageslegacy.voidborn.Pocket.isPocket(player().level()),
				() -> check("into the Pocket from " + world.location().getPath(), true), 10);
		run(() -> MagesLegacyClient.useAbility(3), 5);
		then("back in " + world.location().getPath(), () -> player().level().dimension() == world, () -> check("back in "
				+ world.location().getPath() + ", " + expect + " world(s) of 2 (" + mastery(euf.mageslegacy.race.Mastery.Goal.POCKET_WORLDS) + ")",
				mastery(euf.mageslegacy.race.Mastery.Goal.POCKET_WORLDS) == expect), 10);
	}

	/** The Mycelid's trial: the testing mode counts nothing; five castings of Neurospores on five and with Neurosymbiosis after; three far threads. */
	private static void mycelidMastery() {
		run(() -> server().execute(() -> player().connection.teleport(40.5, player().getY(), 40.5, 0, 0)), 5);
		startMastery("awakening_bloom", "spore_wide_2", "awakening_storm");
		run(() -> husks(5, 2), 5);
		run(() -> server().execute(() -> euf.mageslegacy.race.Talents.setNoCooldown(player(), true)), 3);
		run(() -> MagesLegacyClient.useAbility(0), 25);
		run(() -> check("the testing mode counts nothing (" + mastery(euf.mageslegacy.race.Mastery.Goal.SPORE_MIND) + ")",
				mastery(euf.mageslegacy.race.Mastery.Goal.SPORE_MIND) == 0 && !done("myc_epidemic")), 0);
		for (int i = 1; i <= 5; i++) {
			int round = i;
			run(() -> server().execute(() -> {
				ServerPlayer player = player();
				euf.mageslegacy.race.Talents.setNoCooldown(player, false);
				java.util.Arrays.fill(euf.mageslegacy.voidborn.Voidborn.state(player).cooldown, 0);
				RaceState.setResource(player, RaceState.MAX_RESOURCE);
			}), 3);
			run(() -> MagesLegacyClient.useAbility(0), 25);
			run(() -> MagesLegacyClient.useAbility(1), 5);
			run(() -> MagesLegacyClient.useAbility(1), 5);
			run(() -> check("casting " + round + ": five infected, then the symbiosis (" + mastery(euf.mageslegacy.race.Mastery.Goal.SPORE_MIND) + ", "
					+ mastery(euf.mageslegacy.race.Mastery.Goal.ONE_ORGANISM) + ")", mastery(euf.mageslegacy.race.Mastery.Goal.SPORE_MIND) == round
					&& mastery(euf.mageslegacy.race.Mastery.Goal.ONE_ORGANISM) == round), 0);
		}
		run(() -> check("A Quiet Epidemic", done("myc_epidemic")), 0);
		run(() -> {
			clearHusks();
			List<String> lines = server().submit(() -> euf.mageslegacy.mycelid.MyceliumNetwork.harnessThreads(player())).join();
			RESULTS.addAll(lines);
		}, 10);
		run(() -> {
			ServerPlayer player = player();
			check("three far threads pass the trial", euf.mageslegacy.race.Mastery.passed(player.getData(RaceState.PROGRESS)) && done("myc_mastery")
					&& done("myc_roots"));
			server().execute(() -> euf.mageslegacy.race.Talents.learn(player, "awakening_storm"));
		}, 5);
		run(() -> {
			var owned = euf.mageslegacy.race.Talents.owned(player());
			check("the Mycelid's second Awakening bought, the Bloom on Z, the storm on Alt", euf.mageslegacy.race.Awakenings.at(owned,
					euf.mageslegacy.race.Awakenings.FIRST_SLOT).orElse(null) == euf.mageslegacy.race.Ability.GREAT_BLOOM
					&& euf.mageslegacy.race.Awakenings.at(owned, euf.mageslegacy.race.Awakenings.SECOND_SLOT).orElse(null) == euf.mageslegacy.race.Ability.SPORE_STORM
					&& done("myc_double"));
		}, 0);
	}

	private static boolean done(String id) {
		var holder = server().getAdvancements().get(MagesLegacy.id("legacy/" + id));
		return holder != null && player().getAdvancements().getOrStartProgress(holder).isDone();
	}

	/**
	 * The two race tabs: every advancement loaded with a title, under its own race's root, with its own painted icon, the
	 * secrets hidden, the old shared tab kept invisible; the saved state and an old world's records; the vanilla
	 * commands; the archive on a change of race; and both tabs as a player sees them.
	 */
	private static void advancements() {
		run(() -> {
			int shown = 0;
			int hidden = 0;
			int technical = 0;
			int missing = 0;
			int strays = 0;
			java.util.Set<Integer> icons = new java.util.HashSet<>();
			var all = server().getAdvancements();
			for (var holder : all.getAllAdvancements()) {
				if (!holder.id().getNamespace().equals(MagesLegacy.MOD_ID)) {
					continue;
				}
				var display = holder.value().display().orElse(null);
				if (display == null) {
					technical++;
					continue;
				}
				shown++;
				hidden += display.isHidden() ? 1 : 0;
				if (display.getTitle().getContents() instanceof net.minecraft.network.chat.contents.TranslatableContents t
						&& !net.minecraft.client.resources.language.I18n.exists(t.getKey())) {
					missing++;
				}
				// Up to its root, every ancestor of the same race
				String race = holder.id().getPath().substring("legacy/".length()).split("_")[0];
				var at = holder;
				while (at != null) {
					if (!at.id().getPath().startsWith("legacy/" + race + "_")) {
						strays++;
					}
					at = at.value().parent().map(all::get).orElse(null);
				}
				var icon = display.getIcon();
				Integer data = icon.get(net.minecraft.core.component.DataComponents.CUSTOM_MODEL_DATA) == null ? null
						: icon.get(net.minecraft.core.component.DataComponents.CUSTOM_MODEL_DATA).value();
				if (icon.is(euf.mageslegacy.item.LegacyIcon.ICON.get()) && data != null) {
					icons.add(data);
				}
			}
			check("three race tabs: " + shown + " advancements, " + hidden + " hidden, " + technical + " old kept invisible, " + missing
					+ " without a title, " + strays + " under another race", shown == 51 && hidden == 3 && technical == 8 && missing == 0 && strays == 0);
			check("every advancement wears its own painted icon (" + icons.size() + ")", icons.size() == 51);
			check("the Colony's path follows the saved state (" + done("myc_root") + done("myc_gift") + done("myc_first_awakening") + ")",
					done("myc_root") && done("myc_gift") && done("myc_first_awakening") && !done("void_root"));
			// An old world: the shared tab's records stay, and count again only where the state proves them
			String name = player().getGameProfile().getName();
			server().execute(() -> {
				// Peak was earned at level 50 earlier in the run: taken back, the old shared record alone must not return it at 45
				server().getCommands().performPrefixedCommand(server().createCommandSourceStack(),
						"advancement revoke " + name + " only mages_legacy:legacy/myc_peak");
				for (String old : new String[] {"first_awakening", "peak"}) {
					server().getCommands().performPrefixedCommand(server().createCommandSourceStack(),
							"advancement grant " + name + " only mages_legacy:legacy/" + old);
				}
				euf.mageslegacy.race.Deeds.refresh(player());
			});
		}, 5);
		run(() -> {
			check("an old world's shared records stay, Peak isn't copied without level 50 (" + done("first_awakening") + done("peak")
					+ done("myc_first_awakening") + done("myc_peak") + ")", done("first_awakening") && done("peak") && done("myc_first_awakening")
					&& !done("myc_peak"));
			String name = player().getGameProfile().getName();
			server().execute(() -> server().getCommands().performPrefixedCommand(server().createCommandSourceStack(),
					"advancement revoke " + name + " only mages_legacy:legacy/myc_epidemic"));
		}, 5);
		run(() -> {
			boolean revoked = !done("myc_epidemic");
			String name = player().getGameProfile().getName();
			server().execute(() -> server().getCommands().performPrefixedCommand(server().createCommandSourceStack(),
					"advancement grant " + name + " only mages_legacy:legacy/myc_epidemic"));
			check("revoked by the vanilla command (" + revoked + ")", revoked);
		}, 5);
		run(() -> check("granted by the vanilla command", done("myc_epidemic")), 0);
		tabShot("advancements_mycelid", "myc_root");
		// Back to the Voidborn: the Void's history returns from the archive, the Colony's goes into it
		run(() -> server().execute(() -> {
			Minecraft.getInstance().execute(() -> Minecraft.getInstance().getToasts().clear());
			RaceState.set(player(), Race.VOIDBORN);
		}), 20);
		run(() -> check("Voidborn again: the Void's advancements back, the Colony's archived", done("void_root") && done("void_walls")
				&& done("void_mastery") && !done("myc_root") && !done("myc_epidemic")
				&& euf.mageslegacy.race.Deeds.archived(player()).contains("mages_legacy:legacy/myc_epidemic")), 0);
		tabShot("advancements_voidborn", "void_root");
	}

	private static net.minecraft.world.entity.monster.Vindicator[] prey = new net.minecraft.world.entity.monster.Vindicator[3];
	private static net.minecraft.world.entity.animal.Wolf hound;

	/** A Bloodkin's live numbers, read on the server. */
	private static float blood() {
		return RaceState.resource(player());
	}

	private static int slotCooldown(int slot) {
		return euf.mageslegacy.voidborn.Voidborn.state(player()).cooldown[slot];
	}

	/** Night, Spores-free: the Bloodkin at level 30 on open ground, blood full, food low (no natural healing to blur counts). */
	/**
	 * Films from a still point instead of the player's eyes: a client-only stand there, looking at a point, becomes
	 * the camera (the player is drawn, the HUD and the hand are hidden). watchSelf() gives the view back.
	 */
	private static void watch(Vec3 at, Vec3 target) {
		Minecraft mc = Minecraft.getInstance();
		var stand = EntityType.ARMOR_STAND.create(mc.level);
		if (stand == null) {
			return;
		}
		Vec3 eye = at.add(0, -stand.getEyeHeight(), 0);
		Vec3 d = target.subtract(at);
		float yaw = (float) (Math.toDegrees(Math.atan2(d.z, d.x)) - 90);
		float pitch = (float) -Math.toDegrees(Math.atan2(d.y, Math.sqrt(d.x * d.x + d.z * d.z)));
		stand.moveTo(eye.x, eye.y, eye.z, yaw, pitch);
		stand.setYHeadRot(yaw);
		stand.setInvisible(true);
		mc.options.setCameraType(net.minecraft.client.CameraType.FIRST_PERSON);
		mc.options.hideGui = true;
		mc.setCameraEntity(stand);
	}

	private static void watchSelf() {
		Minecraft mc = Minecraft.getInstance();
		mc.options.hideGui = false;
		mc.setCameraEntity(mc.player);
	}

	private static void bloodSetUp(String... talents) {
		server().execute(() -> {
			ServerPlayer player = player();
			euf.mageslegacy.race.Talents.setNoCooldown(player, false);
			player.setData(RaceState.TALENTS, new ArrayList<>(List.of(talents)));
			java.util.Arrays.fill(euf.mageslegacy.voidborn.Voidborn.state(player).cooldown, 0);
			RaceState.setResource(player, RaceState.MAX_RESOURCE);
			player.getFoodData().setFoodLevel(10);
			player.getFoodData().setSaturation(0);
			player.setHealth(player.getMaxHealth());
			RaceState.sync(player);
		});
	}

	/**
	 * The Bloodkin: chosen; blood that only drains; the Dash cutting three once each; the Harvest healing and drinking;
	 * the Sacrifice that never kills; a Transfusion into a wolf; two Awakenings of three on Z and Alt; the Prince of the
	 * Night's Bat Swarm (cost, length, walls, Long Flight) and its Last Breath (the edge, a killing blow, 30% and 50%,
	 * once in 20 minutes, kept on the player), each apart from the other; the Feast and the Blood Storm together.
	 */
	private static void bloodkin() {
		Minecraft mc = Minecraft.getInstance();
		int[] y = new int[1];
		double[] at = new double[4];
		run(() -> server().execute(() -> {
			ServerPlayer player = player();
			RaceState.set(player, Race.BLOODKIN);
			Progression.setLevel(player, 30);
			player.serverLevel().setDayTime(18000);
			y[0] = -60;
			player.teleportTo(server().overworld(), 300.5, y[0], 0.5, 0, 0);
		}), 10);
		run(() -> check("became a Bloodkin, its own tab open (" + done("blood_root") + ")", RaceState.is(player(), Race.BLOODKIN) && done("blood_root")
				&& !done("void_root") && !done("myc_root")), 0);
		// Blood never gathers by itself
		run(() -> {
			bloodSetUp();
			server().execute(() -> RaceState.setResource(player(), 50));
		}, 5);
		run(() -> at[0] = blood(), 60);
		run(() -> check("blood only drains (" + at[0] + " -> " + blood() + ")", blood() < at[0] && blood() > at[0] - 3), 0);
		// The Dash through three vindicators in a row: each cut once
		run(() -> {
			bloodSetUp();
			server().execute(() -> {
				ServerPlayer player = player();
				player.connection.teleport(300.5, y[0], 0.5, 0, 0);
				for (int i = 0; i < 3; i++) {
					prey[i] = EntityType.VINDICATOR.create(player.serverLevel());
					prey[i].moveTo(300.5, y[0], 2.5 + i * 2, 180, 0);
					prey[i].setNoAi(true);
					prey[i].setPersistenceRequired();
					player.serverLevel().addFreshEntity(prey[i]);
				}
			});
			mc.options.setCameraType(net.minecraft.client.CameraType.THIRD_PERSON_BACK);
		}, 10);
		// Seen from the side, from a still point: the wake stretched along the way, the cuts, the stains
		run(() -> watch(new Vec3(308.5, y[0] + 3.5, 5.5), new Vec3(300.5, y[0] + 0.8, 5.5)), 2);
		run(() -> MagesLegacyClient.useAbility(0), 1);
		film("blood_dash", 8, 2);
		run(() -> {
			watchSelf();
			mc.options.setCameraType(net.minecraft.client.CameraType.THIRD_PERSON_BACK);
		}, 0);
		run(() -> {
			float[] hp = {prey[0].getHealth(), prey[1].getHealth(), prey[2].getHealth()};
			boolean once = true;
			for (float h : hp) {
				once &= Math.abs(h - (prey[0].getMaxHealth() - 6)) < 0.01f;
			}
			check("the Dash cuts each of three exactly once (" + hp[0] + ", " + hp[1] + ", " + hp[2] + ")", once
					&& euf.mageslegacy.race.Talents.progress(player(), euf.mageslegacy.bloodkin.Bloodkin.DASH_MULTI) == 1);
			check("the Dash went on past them (z=" + String.format("%.1f", player().getZ()) + ")", player().getZ() > 8);
		}, 0);
		// The Harvest: 4 to each, 2 health from each, 5 blood from each
		run(() -> server().execute(() -> {
			ServerPlayer player = player();
			RaceState.setResource(player, 100);
			player.setHealth(10);
		}), 3);
		// From high up to one side: the whole circle, the threads from each vindicator
		run(() -> watch(player().position().add(9, 9, -6), player().position().add(0, 0, -2)), 2);
		run(() -> MagesLegacyClient.useAbility(1), 1);
		film("blood_harvest", 8, 3);
		run(() -> {
			watchSelf();
			mc.options.setCameraType(net.minecraft.client.CameraType.THIRD_PERSON_BACK);
		}, 0);
		run(() -> {
			check("the Harvest draws 4 from each, heals 2 from each, drinks 5 from each (" + prey[0].getHealth() + ", hp " + player().getHealth() + ", blood "
					+ blood() + ")", Math.abs(prey[0].getHealth() - (prey[0].getMaxHealth() - 10)) < 0.01f && Math.abs(player().getHealth() - 16) < 0.6f
					&& blood() > 72 && blood() < 76 && euf.mageslegacy.race.Talents.progress(player(), euf.mageslegacy.bloodkin.Bloodkin.HARVEST_GROUP) == 1);
			for (var v : prey) {
				server().execute(v::discard);
			}
		}, 0);
		// The Sacrifice: refused when it would kill; 4 health for 40 blood otherwise
		run(Harness::bloodSetUp, 3);
		run(() -> server().execute(() -> {
			ServerPlayer player = player();
			player.connection.teleport(300.5, y[0], 0.5, 0, 0);
			player.setHealth(4);
			RaceState.setResource(player, 10);
		}), 5);
		run(() -> MagesLegacyClient.useAbility(3), 5);
		run(() -> {
			check("the Sacrifice never kills (" + player().getHealth() + ", " + blood() + ")", Math.abs(player().getHealth() - 4) < 0.01f && blood() < 11);
			server().execute(() -> player().setHealth(20));
		}, 3);
		run(() -> watch(new Vec3(303.5, y[0] + 2.4, 3.5), new Vec3(300.5, y[0] + 1.1, 0.5)), 2);
		run(() -> MagesLegacyClient.useAbility(3), 1);
		film("blood_sacrifice", 8, 4);
		run(Harness::watchSelf, 0);
		run(() -> check("the Sacrifice: 4 health for 40 blood (" + player().getHealth() + ", " + blood() + ")", Math.abs(player().getHealth() - 16) < 0.01f
				&& blood() > 48 && blood() < 51 && euf.mageslegacy.race.Talents.progress(player(), euf.mageslegacy.bloodkin.Bloodkin.SACRIFICES) == 1), 0);
		// A Transfusion into one's own wolf in sight
		run(() -> server().execute(() -> {
			ServerPlayer player = player();
			java.util.Arrays.fill(euf.mageslegacy.voidborn.Voidborn.state(player).cooldown, 0);
			RaceState.setResource(player, 60);
			hound = EntityType.WOLF.create(player.serverLevel());
			hound.moveTo(300.5, y[0], 3.5, 180, 0);
			hound.tame(player);
			hound.setOrderedToSit(true);
			player.serverLevel().addFreshEntity(hound);
			hound.setHealth(10);
			player.connection.teleport(300.5, y[0], 0.5, 0, 15);
		}), 10);
		run(() -> watch(new Vec3(305, y[0] + 2.6, 2), new Vec3(300.5, y[0] + 0.8, 2)), 0);
		run(() -> MagesLegacyClient.useAbility(3), 1);
		film("blood_transfusion", 5, 3);
		run(Harness::watchSelf, 0);
		run(() -> {
			check("a Transfusion heals the wolf 6 for 25 blood and 2 health (" + hound.getHealth() + ", " + player().getHealth() + ", " + blood() + ")",
					// (16, and the Regeneration it gives heals its first point at once: 17)
					hound.getHealth() >= 16 && hound.getHealth() <= 17 && Math.abs(player().getHealth() - 14) < 0.01f && blood() < 36 && blood() > 33);
			server().execute(hound::discard);
		}, 0);
		// Two Awakenings of three: the Prince on Z, the Feast on Alt, the Storm closed (set by the test command)
		run(Harness::bloodSetUp, 3);
		run(() -> server().execute(() -> server().getCommands().performPrefixedCommand(server().createCommandSourceStack(),
				"mages_legacy awaken " + player().getGameProfile().getName() + " prince feast")), 5);
		run(() -> {
			var owned = euf.mageslegacy.race.Talents.owned(player());
			check("the Prince on Z, the Feast on Alt, the Blood Storm closed", euf.mageslegacy.race.Awakenings.at(owned,
					euf.mageslegacy.race.Awakenings.FIRST_SLOT).orElse(null) == euf.mageslegacy.race.Ability.NIGHT_PRINCE
					&& euf.mageslegacy.race.Awakenings.at(owned, euf.mageslegacy.race.Awakenings.SECOND_SLOT).orElse(null) == euf.mageslegacy.race.Ability.FEAST
					&& "mages_legacy.talent.state.third_closed".equals(euf.mageslegacy.race.Talents.awakeningRefusal(owned, 50,
							euf.mageslegacy.race.Talent.AWAKENING_BLOOD_STORM, player().getData(RaceState.PROGRESS))));
		}, 0);
		// The Bat Swarm by hand: 30 blood, 3 s, its own 25 s, untouchable, stopped by a wall; the Last Breath untouched
		run(() -> server().execute(() -> {
			ServerPlayer player = player();
			player.connection.teleport(300.5, y[0], 0.5, 0, 0);
			for (int x = -3; x <= 3; x++) {
				for (int dy = 0; dy < 5; dy++) {
					player.serverLevel().setBlockAndUpdate(new net.minecraft.core.BlockPos(300 + x, y[0] + dy, 12), Blocks.STONE.defaultBlockState());
				}
			}
		}), 10);
		run(() -> mc.options.setCameraType(net.minecraft.client.CameraType.FIRST_PERSON), 3);
		run(() -> MagesLegacyClient.useAbility(2), 2);
		run(() -> {
			check("the Bat Swarm turns the view to third person (" + mc.options.getCameraType() + ")",
					mc.options.getCameraType() == net.minecraft.client.CameraType.THIRD_PERSON_BACK);
			check("the Bat Swarm: 30 blood, its own 25 s, the Last Breath still ready (" + blood() + ", " + slotCooldown(2) + ", "
					+ euf.mageslegacy.bloodkin.Bloodkin.rescueReady(player()) + ")", euf.mageslegacy.bloodkin.Bloodkin.swarming(player()) && blood() > 68
					&& blood() < 71 && slotCooldown(2) > 480 && slotCooldown(2) <= 500 && euf.mageslegacy.bloodkin.Bloodkin.rescueReady(player()) == 0);
			at[1] = player().getHealth();
			server().execute(() -> player().hurt(player().damageSources().generic(), 5));
		}, 0);
		film("bat_swarm", 8, 4);
		run(() -> check("nothing touches the swarm (" + at[1] + " -> " + player().getHealth() + "), still flying at 1.7 s", player().getHealth() >= at[1]
				&& euf.mageslegacy.bloodkin.Bloodkin.swarming(player())), 0);
		run(() -> {
		}, 40);
		run(() -> {
			check("the swarm is over after 3 s, the body back, the wall not passed (z=" + String.format("%.1f", player().getZ()) + ")",
					!euf.mageslegacy.bloodkin.Bloodkin.swarming(player()) && !player().isNoGravity() && player().getZ() < 12);
			check("the view is back in the eyes (" + mc.options.getCameraType() + ")", mc.options.getCameraType() == net.minecraft.client.CameraType.FIRST_PERSON);
			mc.options.setCameraType(net.minecraft.client.CameraType.THIRD_PERSON_BACK);
		}, 0);
		// Long Flight: 5 s by hand
		run(() -> {
			bloodSetUp("awakening_prince", "awakening_feast", "prince_long");
			server().execute(() -> player().connection.teleport(300.5, y[0], 0.5, 180, 0));
		}, 5);
		run(() -> MagesLegacyClient.useAbility(2), 90);
		run(() -> check("Long Flight: still a swarm at 4.5 s", euf.mageslegacy.bloodkin.Bloodkin.swarming(player())), 25);
		run(() -> check("...and over by 5.7 s", !euf.mageslegacy.bloodkin.Bloodkin.swarming(player())), 0);
		// The Last Breath: a blow over the edge, from low health
		run(() -> {
			bloodSetUp("awakening_prince", "awakening_feast", "prince_long", "prince_shadow");
			server().execute(() -> {
				ServerPlayer player = player();
				player.connection.teleport(300.5, y[0], -20.5, 0, 0);
				player.setHealth(8);
				player.invulnerableTime = 0;
				player.hurt(player.damageSources().generic(), 12);
			});
		}, 2);
		film("last_breath", 8, 3);
		run(() -> {
			ServerPlayer player = player();
			long ready = euf.mageslegacy.bloodkin.Bloodkin.rescueReady(player) - player.level().getGameTime();
			check("the Last Breath: a killing blow from 8 of 20 leaves 30% (" + player.getHealth() + "), 20 minutes (" + ready / 20 + " s), the swarm's own key "
					+ "untouched (" + slotCooldown(2) + ")", player.isAlive() && Math.abs(player.getHealth() - 6) < 0.01f && ready > 23900 && ready <= 24000
					&& slotCooldown(2) == 0);
		}, 50);
		run(() -> check("the Last Breath's swarm keeps its 3 s with Long Flight, then Shadow of Death's Resistance",
				!euf.mageslegacy.bloodkin.Bloodkin.swarming(player()) && player().hasEffect(net.minecraft.world.effect.MobEffects.DAMAGE_RESISTANCE)), 0);
		// Once in 20 minutes: over the edge again, nothing saves (a blow that doesn't kill)
		run(() -> server().execute(() -> {
			ServerPlayer player = player();
			player.removeAllEffects();
			player.setHealth(10);
			player.invulnerableTime = 0;
			player.hurt(player.damageSources().generic(), 9);
		}), 3);
		run(() -> check("a second time within 20 minutes nothing saves (" + player().getHealth() + ")", Math.abs(player().getHealth() - 1) < 0.01f
				&& !euf.mageslegacy.bloodkin.Bloodkin.swarming(player())), 0);
		// The swarm by hand still works while the Last Breath waits
		run(() -> server().execute(() -> {
			ServerPlayer player = player();
			player.setHealth(20);
			RaceState.setResource(player, 100);
		}), 3);
		run(() -> MagesLegacyClient.useAbility(2), 3);
		run(() -> check("the Bat Swarm works while the Last Breath waits", euf.mageslegacy.bloodkin.Bloodkin.swarming(player())), 110);
		// A killing blow from full health, the Second Heart: 50%
		run(() -> {
			bloodSetUp("awakening_prince", "awakening_feast", "prince_heart");
			server().execute(() -> {
				ServerPlayer player = player();
				euf.mageslegacy.voidborn.Voidborn.state(player).princeReady = 0;
				player.invulnerableTime = 0;
				player.hurt(player.damageSources().generic(), 40);
			});
		}, 5);
		run(() -> check("a killing blow from full health is caught too; with a Second Heart 50% (" + player().getHealth() + ")", player().isAlive()
				&& Math.abs(player().getHealth() - 10) < 0.01f), 100);
		run(() -> {
			var tag = player().getData(RaceState.COOLDOWNS);
			check("the Last Breath's wait is kept on the player (" + tag.getInt("prince") + ")", tag.getInt("prince") > 23000);
		}, 0);
		// The Feast and the Blood Storm together, a vindicator killed under both
		run(() -> {
			bloodSetUp("awakening_feast", "awakening_blood_storm");
			server().execute(() -> {
				ServerPlayer player = player();
				player.connection.teleport(300.5, y[0], -20.5, 0, 0);
				prey[0] = EntityType.VINDICATOR.create(player.serverLevel());
				prey[0].moveTo(300.5, y[0], -18.5, 180, 0);
				prey[0].setNoAi(true);
				player.serverLevel().addFreshEntity(prey[0]);
			});
		}, 10);
		run(() -> MagesLegacyClient.useAbility(2), 3);
		run(() -> MagesLegacyClient.useAbility(4), 3);
		film("blood_storm", 6, 4);
		run(() -> {
			check("the Feast and the Blood Storm at once, the storm drawing blood", euf.mageslegacy.bloodkin.Bloodkin.feasting(player())
					&& euf.mageslegacy.bloodkin.Bloodkin.storming(player()) && prey[0].getHealth() < prey[0].getMaxHealth());
			server().execute(() -> {
				prey[0].setHealth(1);
				prey[0].invulnerableTime = 0;
				player().attack(prey[0]);
			});
		}, 5);
		run(() -> check("a kill under both: the Scarlet Feast", done("blood_scarlet") && done("blood_swarm") && done("blood_last_breath")), 0);
		tabShot("advancements_bloodkin", "blood_root");
		run(() -> mc.setScreen(new euf.mageslegacy.client.screen.TalentScreen()), 20);
		shot("talents_bloodkin");
		run(() -> mc.setScreen(null), 5);
	}

	/** Holds the use key down this long (a real right click held, as a player eats), then lets go. */
	private static void holdUse(int ticks) {
		run(() -> Minecraft.getInstance().options.keyUse.setDown(true), ticks);
		run(() -> Minecraft.getInstance().options.keyUse.setDown(false), 3);
	}

	/** A stack in the main hand, food at 10 with no saturation, the eyes on the open sky. */
	private static void meal(net.minecraft.world.item.Item item, int count, int[] y) {
		server().execute(() -> {
			ServerPlayer player = player();
			player.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND, new net.minecraft.world.item.ItemStack(item, count));
			player.getFoodData().setFoodLevel(10);
			player.getFoodData().setSaturation(0);
			player.connection.teleport(80.5, y[0], 80.5, 0, -90);
		});
	}

	private static int held() {
		return player().getMainHandItem().getCount();
	}

	/**
	 * Raw mushrooms: a level 1 Mycelid eats all four (5 food, 3 saturation, one gone), the diet's talents once each meal
	 * (Living Recovery once in 30 s), no nutrients from eating; still placed on podzol and fed to a node (Shift: the
	 * stack); a Voidborn and a player with no race can't eat them; the first Mother Mycelium tells how to feed the colony,
	 * once; the race choice can't be left with Esc.
	 */
	private static void fungalDiet(int[] y) {
		Minecraft mc = Minecraft.getInstance();
		int[] before = new int[2];
		// The first Mother of a new Mycelid: the old nodes gone, a new one grown, the lesson given once
		run(() -> server().execute(() -> {
			ServerPlayer player = player();
			RaceState.set(player, Race.MYCELID);
			for (int x : new int[] {40, 60}) {
				player.serverLevel().removeBlock(new net.minecraft.core.BlockPos(x, y[0], 40), false);
			}
			player.connection.teleport(80.5, y[0], 80.5, 0, 0);
		}), 10);
		run(() -> {
			boolean fresh = !player().getData(RaceState.PROGRESS).getBoolean(euf.mageslegacy.mycelid.MyceliumNetwork.TUTORIAL);
			server().execute(() -> euf.mageslegacy.mycelid.MyceliumNetwork.grow(player()));
			check("a new Mycelid hasn't had the lesson yet", fresh);
		}, 10);
		run(() -> {
			check("the first Mother Mycelium gives the lesson, kept on the player", player().getData(RaceState.PROGRESS)
					.getBoolean(euf.mageslegacy.mycelid.MyceliumNetwork.TUTORIAL));
			before[1] = euf.mageslegacy.mycelid.MyceliumNetwork.get(server()).nutrients(player().getUUID());
		}, 0);
		// All four, at level 1, no talents
		for (var item : new net.minecraft.world.item.Item[] {net.minecraft.world.item.Items.RED_MUSHROOM, net.minecraft.world.item.Items.BROWN_MUSHROOM,
				net.minecraft.world.item.Items.CRIMSON_FUNGUS, net.minecraft.world.item.Items.WARPED_FUNGUS}) {
			run(() -> meal(item, 4, y), 5);
			holdUse(45);
			run(() -> {
				var food = player().getFoodData();
				check("a level 1 Mycelid eats " + item + ": food " + food.getFoodLevel() + ", saturation " + food.getSaturationLevel() + ", left " + held(),
						food.getFoodLevel() == 15 && Math.abs(food.getSaturationLevel() - 3) < 0.01f && held() == 3);
			}, 0);
		}
		run(() -> check("eating gives the colony no nutrients (" + before[1] + " -> " + euf.mageslegacy.mycelid.MyceliumNetwork.get(server())
				.nutrients(player().getUUID()) + ")", euf.mageslegacy.mycelid.MyceliumNetwork.get(server()).nutrients(player().getUUID()) == before[1]), 0);
		// The diet: +3 food, +15 Spores, 4 health once in 30 s
		run(() -> server().execute(() -> {
			ServerPlayer player = player();
			player.setData(RaceState.TALENTS, new ArrayList<>(List.of("diet_1", "diet_2", "diet_3")));
			RaceState.setResource(player, 50);
			player.setHealth(10);
		}), 3);
		run(() -> meal(net.minecraft.world.item.Items.RED_MUSHROOM, 4, y), 5);
		holdUse(45);
		run(() -> {
			check("with the diet: food " + player().getFoodData().getFoodLevel() + " (+5 +3), Spores " + RaceState.resource(player()) + " (+15), health "
					+ player().getHealth() + " (+4)", player().getFoodData().getFoodLevel() == 18 && RaceState.resource(player()) > 64
					&& RaceState.resource(player()) < 70 && Math.abs(player().getHealth() - 14) < 0.01f);
			server().execute(() -> player().setHealth(10));
		}, 3);
		run(() -> meal(net.minecraft.world.item.Items.BROWN_MUSHROOM, 4, y), 5);
		holdUse(45);
		run(() -> check("Living Recovery waits 30 s (health " + player().getHealth() + ")", Math.abs(player().getHealth() - 10) < 0.01f
				&& player().getFoodData().getFoodLevel() == 18), 0);
		// Still placed, still fed to a node
		run(() -> server().execute(() -> {
			ServerPlayer player = player();
			player.serverLevel().setBlockAndUpdate(new net.minecraft.core.BlockPos(80, y[0] - 1, 83), Blocks.PODZOL.defaultBlockState());
			player.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND, new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.BROWN_MUSHROOM, 4));
			player.getFoodData().setFoodLevel(10);
		}), 5);
		run(() -> mc.gameMode.useItemOn(mc.player, net.minecraft.world.InteractionHand.MAIN_HAND, new net.minecraft.world.phys.BlockHitResult(
				new net.minecraft.world.phys.Vec3(80.5, y[0], 83.5), net.minecraft.core.Direction.UP, new net.minecraft.core.BlockPos(80, y[0] - 1, 83), false)), 5);
		run(() -> {
			check("a Mycelid still places a mushroom on podzol", player().level().getBlockState(new net.minecraft.core.BlockPos(80, y[0], 83))
					.is(Blocks.BROWN_MUSHROOM) && held() == 3 && player().getFoodData().getFoodLevel() == 10);
			before[0] = euf.mageslegacy.mycelid.MyceliumNetwork.get(server()).nutrients(player().getUUID());
		}, 0);
		net.minecraft.core.BlockPos node = new net.minecraft.core.BlockPos(80, y[0], 80);
		// Looking at the node's side from two blocks off, one press of the use key
		run(() -> server().execute(() -> player().connection.teleport(80.5, y[0], 78.5, 0, 45)), 8);
		run(() -> net.minecraft.client.KeyMapping.click(mc.options.keyUse.getKey()), 6);
		run(() -> check("a click on the node feeds the colony a mushroom, not the Mycelid (" + before[0] + " -> "
				+ euf.mageslegacy.mycelid.MyceliumNetwork.get(server()).nutrients(player().getUUID()) + ", left " + held() + ", aimed at "
				+ (mc.hitResult instanceof net.minecraft.world.phys.BlockHitResult b ? mc.level.getBlockState(b.getBlockPos()) + "@" + b.getBlockPos() : "nothing")
				+ ", node " + player().level().getBlockState(node) + ")",
				euf.mageslegacy.mycelid.MyceliumNetwork.get(server()).nutrients(player().getUUID()) == before[0] + 4 && held() == 2
						&& player().getFoodData().getFoodLevel() == 10), 0);
		// Crouching lowers the eyes: a flatter look still finds the node
		run(() -> mc.options.keyShift.setDown(true), 6);
		run(() -> server().execute(() -> player().connection.teleport(80.5, y[0], 78.5, 0, 30)), 8);
		run(() -> net.minecraft.client.KeyMapping.click(mc.options.keyUse.getKey()), 6);
		run(() -> {
			mc.options.keyShift.setDown(false);
			check("Shift: the whole stack to the colony (" + euf.mageslegacy.mycelid.MyceliumNetwork.get(server()).nutrients(player().getUUID()) + ", left "
					+ held() + ")", euf.mageslegacy.mycelid.MyceliumNetwork.get(server()).nutrients(player().getUUID()) == before[0] + 12 && held() == 0);
		}, 3);
		// Only a Mycelid: a Voidborn and a player with no race keep their mushrooms
		for (Race race : new Race[] {Race.VOIDBORN, null}) {
			run(() -> server().execute(() -> RaceState.set(player(), race)), 5);
			run(() -> meal(net.minecraft.world.item.Items.RED_MUSHROOM, 4, y), 5);
			holdUse(45);
			run(() -> check((race == null ? "no race" : "a Voidborn") + " can't eat a raw mushroom (food " + player().getFoodData().getFoodLevel() + ", left "
					+ held() + ")", player().getFoodData().getFoodLevel() == 10 && held() == 4 && !player().isUsingItem()), 0);
		}
		// With no race, Esc doesn't leave the choice
		run(() -> mc.setScreen(new euf.mageslegacy.client.screen.RaceSelectScreen()), 5);
		run(() -> mc.screen.keyPressed(org.lwjgl.glfw.GLFW.GLFW_KEY_ESCAPE, 0, 0), 3);
		run(() -> {
			check("Esc doesn't leave the race choice", mc.screen instanceof euf.mageslegacy.client.screen.RaceSelectScreen);
			mc.setScreen(null);
			server().execute(() -> RaceState.set(player(), Race.MYCELID));
		}, 5);
	}

	/** One race's tab, as a player opens it. */
	private static void tabShot(String name, String root) {
		run(() -> {
			Minecraft mc = Minecraft.getInstance();
			var client = mc.player.connection.getAdvancements();
			var tab = client.get(MagesLegacy.id("legacy/" + root));
			mc.setScreen(new net.minecraft.client.gui.screens.advancements.AdvancementsScreen(client));
			if (tab != null) {
				client.setSelectedTab(tab, true);
			}
		}, 20);
		shot(name);
		run(() -> Minecraft.getInstance().setScreen(null), 3);
	}

	/**
	 * The Great Bloom and the Spore Storm at once in plain survival (no testing mode): the Bloom first, the storm once
	 * 10 Spores have grown back in the shade. Then the Bloom's cooldown through a logout, a second call, the save on
	 * the player and a trip to another world.
	 */
	private static void bloomAndStorm() {
		int bloomSlot = euf.mageslegacy.race.Awakenings.FIRST_SLOT;
		int full = 5 * 60 * 20;
		int[] seen = new int[1];
		run(() -> server().execute(() -> {
			ServerPlayer player = player();
			husk.discard();
			euf.mageslegacy.mycelid.Awaken.reset(player);
			euf.mageslegacy.race.Talents.setNoCooldown(player, false);
			java.util.Arrays.fill(euf.mageslegacy.voidborn.Voidborn.state(player).cooldown, 0);
			RaceState.setResource(player, RaceState.MAX_RESOURCE);
			net.minecraft.core.BlockPos roof = player.blockPosition().above(3);
			for (int dx = -2; dx <= 2; dx++) {
				for (int dz = -2; dz <= 2; dz++) {
					player.serverLevel().setBlockAndUpdate(roof.offset(dx, 0, dz), net.minecraft.world.level.block.Blocks.STONE.defaultBlockState());
				}
			}
		}), 5);
		run(() -> MagesLegacyClient.useAbility(bloomSlot), 5);
		run(() -> {
			ServerPlayer player = player();
			int cd = euf.mageslegacy.voidborn.Voidborn.state(player).cooldown[bloomSlot];
			check("survival: the Bloom grows for 60 Spores (" + RaceState.resource(player) + " left)", euf.mageslegacy.mycelid.Awaken.blooming(player)
					&& RaceState.resource(player) <= 41);
			check("the Bloom's cooldown is kept on the player from its start (" + cd + ")", cd > full);
		}, 210);
		run(() -> check("10 s in the shade grow 10 Spores back (" + RaceState.resource(player()) + ")", RaceState.resource(player()) >= 50), 0);
		run(() -> MagesLegacyClient.useAbility(euf.mageslegacy.race.Awakenings.SECOND_SLOT), 5);
		run(() -> check("the Spore Storm whirls round the blooming Mycelid", euf.mageslegacy.mycelid.Awaken.blooming(player())
				&& euf.mageslegacy.mycelid.Awaken.storming(player())), 0);
		run(() -> server().execute(() -> euf.mageslegacy.mycelid.Awaken.leave(player())), 3);
		run(() -> {
			ServerPlayer player = player();
			seen[0] = euf.mageslegacy.voidborn.Voidborn.state(player).cooldown[bloomSlot];
			check("leaving mid-Bloom ends it with the full cooldown and exhaustion (" + seen[0] + ")", !euf.mageslegacy.mycelid.Awaken.blooming(player)
					&& !euf.mageslegacy.mycelid.Awaken.storming(player) && seen[0] > full - 20 && seen[0] <= full
					&& euf.mageslegacy.mycelid.Awaken.exhausted(player));
		}, 5);
		run(() -> server().execute(() -> euf.mageslegacy.mycelid.Awaken.leave(player())), 3);
		run(() -> {
			int cd = euf.mageslegacy.voidborn.Voidborn.state(player()).cooldown[bloomSlot];
			check("a second end changes nothing (" + seen[0] + " -> " + cd + ")", cd < seen[0]);
		}, 100);
		run(() -> {
			int[] left = player().getData(RaceState.COOLDOWNS).getIntArray("left");
			check("the cooldown is saved on the player (" + (left.length > bloomSlot ? left[bloomSlot] : -1) + ")",
					left.length > bloomSlot && left[bloomSlot] > full - 200);
		}, 0);
		// Carried to another world mid-Bloom: it withers there, the cooldown starts
		run(() -> server().execute(() -> {
			ServerPlayer player = player();
			euf.mageslegacy.voidborn.Voidborn.state(player).cooldown[bloomSlot] = 0;
			RaceState.setResource(player, RaceState.MAX_RESOURCE);
		}), 3);
		run(() -> MagesLegacyClient.useAbility(bloomSlot), 5);
		double[] home = new double[3];
		run(() -> server().execute(() -> {
			ServerPlayer player = player();
			home[0] = player.getX();
			home[1] = player.getY();
			home[2] = player.getZ();
			player.teleportTo(server().getLevel(net.minecraft.world.level.Level.NETHER), 0.5, 130, 0.5, player.getYRot(), player.getXRot());
		}), 10);
		run(() -> {
			ServerPlayer player = player();
			int cd = euf.mageslegacy.voidborn.Voidborn.state(player).cooldown[bloomSlot];
			check("another world ends the Bloom with its cooldown (" + cd + ")", !euf.mageslegacy.mycelid.Awaken.blooming(player) && cd > full - 40
					&& cd <= full);
			server().execute(() -> player.teleportTo(server().overworld(), home[0], home[1], home[2], player.getYRot(), player.getXRot()));
		}, 20);
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

	/** A run of screenshots, every few ticks: the CI lays them out in a sheet, so the animation itself can be judged. */
	private static void film(String name, int frames, int every) {
		// No advancement toasts or chat over the effect being filmed
		run(() -> {
			Minecraft.getInstance().getToasts().clear();
			Minecraft.getInstance().gui.getChat().clearMessages(false);
		}, 0);
		for (int i = 0; i < frames; i++) {
			int frame = i;
			run(() -> {
				Minecraft mc = Minecraft.getInstance();
				Screenshot.grab(mc.gameDirectory, String.format("film_%s_%02d.png", name, frame), mc.getMainRenderTarget(), message -> {
				});
			}, every);
		}
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
		// Advancements still count, but their chat lines stay out of the films
		rules.getRule(GameRules.RULE_ANNOUNCE_ADVANCEMENTS).set(false, null);
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
