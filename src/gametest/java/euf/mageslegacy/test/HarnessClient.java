package euf.mageslegacy.test;

import euf.mageslegacy.MagesLegacy;
import euf.mageslegacy.client.ClientRaceState;
import euf.mageslegacy.client.MagesLegacyClient;
import euf.mageslegacy.client.RaceHud;
import euf.mageslegacy.client.screen.BestiaryScreen;
import euf.mageslegacy.client.screen.LoreScreen;
import euf.mageslegacy.client.screen.RaceSelectScreen;
import euf.mageslegacy.client.screen.TalentScreen;
import euf.mageslegacy.net.RacePackets;
import euf.mageslegacy.race.Race;
import euf.mageslegacy.race.RaceData;
import euf.mageslegacy.race.RaceState;
import euf.mageslegacy.race.Talents;
import euf.mageslegacy.shepherd.EchoType;
import euf.mageslegacy.shepherd.Shepherd;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import java.util.function.Consumer;
import java.util.function.Function;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.block.Blocks;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.option.Perspective;
import net.minecraft.client.util.ScreenshotRecorder;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.entity.mob.ZombieEntity;
import net.minecraft.entity.passive.WolfEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.Heightmap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * With -Dmages_legacy.harness=true (runHarness): a new player sees the legend, picks the Mycelids, learns a talent,
 * then jumps, raises a spore cloud, eats a mushroom and travels through mycelium nodes, with screenshots on the way.
 * Writes harness-result.txt; scripts/ci-harness.sh fails the build unless it says PASS and every screenshot exists.
 */
public class HarnessClient implements ClientModInitializer {
	private static final Logger LOGGER = LoggerFactory.getLogger("mages_legacy-harness");

	private record Step(int waitAfter, boolean needsTerrain, Consumer<MinecraftClient> action) {}

	private final Deque<Step> steps = new ArrayDeque<>();
	private final List<String> results = new ArrayList<>();
	private int ticks;
	private int nextStepTick = 60;

	private volatile BlockPos arena;
	private volatile UUID zombie;
	private Vec3d beforeJump;

	@Override
	public void onInitializeClient() {
		if (!Boolean.getBoolean("mages_legacy.harness")) {
			return;
		}
		// A new player: the legend opens by itself
		step(40, true, c -> {
			check("lore_screen_on_join", c.currentScreen instanceof LoreScreen);
			if (c.currentScreen instanceof LoreScreen lore) {
				lore.revealAll();
			}
		});
		step(5, false, c -> screenshot(c, "lore"));
		step(20, false, c -> {
			if (c.currentScreen instanceof LoreScreen lore) {
				lore.next();
			}
		});
		step(20, false, c -> {
			check("choice_screen", c.currentScreen instanceof RaceSelectScreen);
			screenshot(c, "choose_mycelid");
			if (c.currentScreen instanceof RaceSelectScreen screen) {
				screen.select(Race.BLOODKIN);
			}
		});
		step(10, false, c -> {
			screenshot(c, "choose_bloodkin");
			if (c.currentScreen instanceof RaceSelectScreen screen) {
				screen.choose();
			}
		});
		step(20, false, c -> {
			check("sleeping_race_refused", serverRace(c) == null);
			if (c.currentScreen instanceof RaceSelectScreen screen) {
				screen.select(Race.MYCELID);
				screen.choose();
			}
		});
		step(20, false, c -> {
			check("chosen_on_server", serverRace(c) == Race.MYCELID);
			check("chosen_on_client", ClientRaceState.race == Race.MYCELID);
			check("choice_screen_closed", c.currentScreen == null);
			commands(c, "gamerule sendCommandFeedback false", "gamerule doDaylightCycle false", "gamerule doWeatherCycle false",
					"gamerule doMobSpawning false", "time set 1000", "weather clear", "gamemode survival @a",
					"effect give @a minecraft:resistance 600 4 true", "effect give @a minecraft:saturation 600 0 true",
					"race xp @a 3000", "race resource @a 100", "clear @a",
					"give @a minecraft:red_mushroom 8", "give @a minecraft:brown_mushroom 8");
			onServer(c, s -> {
				arena = prepareArena(s.getOverworld());
				return null;
			});
		});
		step(40, true, c -> tp(c, Vec3d.ofBottomCenter(arena).add(-6, 0, 0), Vec3d.ofBottomCenter(arena).add(4, 0, 0)));
		step(20, true, c -> screenshot(c, "hud"));

		// The talent tree: learn the jump chain with a click
		step(10, false, c -> c.setScreen(new TalentScreen()));
		step(20, false, c -> {
			check("tree_has_points", ClientRaceState.freePoints > 0);
			if (c.currentScreen instanceof TalentScreen tree) {
				check("tree_click_learns", tree.click(Talents.NET_CHAIN));
			}
		});
		step(20, false, c -> {
			check("talent_on_server", onServer(c, s -> RaceState.get(s).data(c.player.getUuid()).has(Talents.NET_CHAIN)));
			screenshot(c, "talents");
			if (c.currentScreen instanceof TalentScreen tree) {
				tree.toggleGuide();
			}
		});
		// A short screen (GUI scale 4): the guide and the tree no longer fit and must scroll
		step(5, false, c -> {
			screenshot(c, "talents_guide");
			c.options.getGuiScale().setValue(4);
			c.onResolutionChanged();
		});
		step(5, false, c -> {
			if (c.currentScreen instanceof TalentScreen tree) {
				tree.mouseScrolled(c.currentScreen.width / 2.0, c.currentScreen.height / 2.0, -3);
			}
		});
		step(5, false, c -> {
			screenshot(c, "talents_guide_scrolled");
			if (c.currentScreen instanceof TalentScreen tree) {
				tree.toggleGuide();
				tree.mouseScrolled(c.currentScreen.width / 2.0, c.currentScreen.height / 2.0, -10);
			}
		});
		step(5, false, c -> {
			screenshot(c, "talents_scrolled");
			c.options.getGuiScale().setValue(0);
			c.onResolutionChanged();
			c.setScreen(null);
		});

		// Spore jump towards the arena centre
		step(10, false, c -> {
			beforeJump = c.player.getPos();
			MagesLegacyClient.useAbility(0);
		});
		step(4, false, c -> screenshot(c, "spore_jump"));
		step(20, false, c -> {
			double moved = c.player.getPos().subtract(beforeJump).horizontalLength();
			check("spore_jump_moved", moved > 2);
			check("jump_chain_open", ClientRaceState.chainLeft == 2);
			LOGGER.info("[harness] jumped {} blocks", String.format(Locale.ROOT, "%.1f", moved));
		});

		// Spore cloud around a zombie
		step(10, false, c -> {
			commands(c, "race resource @a 100");
			onServer(c, s -> {
				ServerWorld world = s.getOverworld();
				ZombieEntity mob = EntityType.ZOMBIE.create(world);
				mob.refreshPositionAndAngles(arena.getX() + 2.5, arena.getY(), arena.getZ() + 0.5, 90, 0);
				mob.setAiDisabled(true);
				mob.setPersistent();
				// A helmet keeps it from burning in the morning sun
				mob.equipStack(EquipmentSlot.HEAD, new ItemStack(Items.LEATHER_HELMET));
				world.spawnEntity(mob);
				zombie = mob.getUuid();
				return null;
			});
			tp(c, Vec3d.ofBottomCenter(arena).add(-1, 0, 0), Vec3d.ofBottomCenter(arena).add(2.5, 1, 0));
		});
		step(30, false, c -> {
			MagesLegacyClient.useAbility(1);
			// From behind the shoulder, so the whole cloud is in the frame
			c.options.setPerspective(Perspective.THIRD_PERSON_BACK);
		});
		step(30, false, c -> {
			screenshot(c, "spore_cloud");
			c.options.setPerspective(Perspective.FIRST_PERSON);
			String effects = onServer(c, s -> s.getOverworld().getEntity(zombie) instanceof ZombieEntity z
					? z.getStatusEffects().toString() + " hp " + z.getHealth() : "no zombie");
			LOGGER.info("[harness] zombie in the cloud: {}", effects);
			check("cloud_chokes_zombie", onServer(c, s -> s.getOverworld().getEntity(zombie) instanceof ZombieEntity z
					&& z.hasStatusEffect(StatusEffects.BLINDNESS)
					&& (z.hasStatusEffect(StatusEffects.WITHER) || z.hasStatusEffect(StatusEffects.POISON))));
		});

		// Eat a raw mushroom from the hand
		step(10, false, c -> {
			selectItem(c, Items.RED_MUSHROOM);
			c.player.setPitch(-60);
			c.interactionManager.interactItem(c.player, Hand.MAIN_HAND);
		});
		step(10, false, c -> check("mushroom_taste", onServer(c, s ->
				s.getPlayerManager().getPlayer(c.player.getUuid()).hasStatusEffect(StatusEffects.STRENGTH))));

		// Mycelium nodes open after the Nether: grow two and travel between them
		step(10, false, c -> {
			commands(c, "advancement grant @a only minecraft:story/enter_the_nether", "race resource @a 100");
			tp(c, Vec3d.ofBottomCenter(arena).add(-7, 0, 2), Vec3d.ofBottomCenter(arena).add(-7, -1, 4));
		});
		step(20, false, c -> MagesLegacyClient.useAbility(2));
		step(20, false, c -> {
			commands(c, "race resource @a 100");
			resetCooldowns(c);
			tp(c, Vec3d.ofBottomCenter(arena).add(7, 0, -2), Vec3d.ofBottomCenter(arena).add(7, -1, -4));
		});
		step(20, false, c -> MagesLegacyClient.useAbility(2));
		step(20, false, c -> {
			check("two_nodes", onServer(c, s -> RaceState.get(s).nodes(c.player.getUuid()).size() == 2));
			tp(c, Vec3d.ofBottomCenter(arena).add(5, 0, -5.5), Vec3d.ofBottomCenter(arena).add(7, 0.3, -3));
		});
		step(30, true, c -> screenshot(c, "node"));
		step(10, false, c -> {
			BlockPos node = arena.add(7, 0, -3);
			BlockHitResult hit = new BlockHitResult(Vec3d.ofCenter(node), Direction.UP, node, false);
			c.interactionManager.interactBlock(c.player, Hand.MAIN_HAND, hit);
		});
		step(20, false, c -> {
			check("node_travel", c.player.getBlockPos().isWithinDistance(arena.add(-7, 1, 3), 2));
			screenshot(c, "node_arrived");
		});

		// ---- Shepherds: switch race, learn echoes, the bestiary, a real Call, a bond, the Rally ----
		step(10, false, c -> {
			commands(c, "race set @a shepherd", "race xp @a 3000", "race resource @a 100", "kill @e[type=!player]");
			UUID me = c.player.getUuid();
			onServer(c, s -> {
				ServerPlayerEntity player = s.getPlayerManager().getPlayer(me);
				RaceData data = RaceState.get(s).data(me);
				for (EntityType<?> type : List.of(EntityType.ZOMBIE, EntityType.SKELETON, EntityType.SPIDER)) {
					EchoType echo = EchoType.of(type).orElseThrow();
					for (int i = 0; i < echo.kills(); i++) {
						Shepherd.countKill(player, data, echo);
					}
				}
				for (int i = 0; i < 4; i++) {
					Shepherd.countKill(player, data, EchoType.of(EntityType.WITHER_SKELETON).orElseThrow());
				}
				RacePackets.sync(player, data);
				return null;
			});
		});
		step(20, false, c -> {
			check("shepherd_chosen", serverRace(c) == Race.SHEPHERD && ClientRaceState.race == Race.SHEPHERD);
			check("echoes_learned", RaceHud.activeSquad().getOrDefault("minecraft:zombie", 0) == 1);
			c.setScreen(new BestiaryScreen());
		});
		step(20, false, c -> screenshot(c, "bestiary"));
		step(10, false, c -> {
			c.setScreen(null);
			tp(c, Vec3d.ofBottomCenter(arena).add(-6, 0, 0), Vec3d.ofBottomCenter(arena).add(-2, 0, 0));
		});
		step(10, false, c -> KeyBinding.onKeyPressed(MagesLegacyClient.ABILITY_KEYS[0].getDefaultKey()));
		step(30, false, c -> {
			check("echo_called", ClientRaceState.extra.getInt("echoes") == 1);
			MagesLegacyClient.editSquad(0, "minecraft:skeleton", 1);
			resetCooldowns(c);
		});
		step(10, false, c -> KeyBinding.onKeyPressed(MagesLegacyClient.ABILITY_KEYS[0].getDefaultKey()));
		step(30, false, c -> {
			check("two_echoes", ClientRaceState.extra.getInt("echoes") == 2 && ClientRaceState.extra.getInt("capUsed") == 3);
			c.options.hudHidden = true;
			tp(c, Vec3d.ofBottomCenter(arena).add(-8, 0.5, 3), Vec3d.ofBottomCenter(arena).add(-3, 1, 0));
		});
		step(20, true, c -> screenshot(c, "echoes"));
		// Sneak + Call sends the echoes back; one Call then brings the whole squad at once
		step(5, false, c -> c.options.sneakKey.setPressed(true));
		step(5, false, c -> KeyBinding.onKeyPressed(MagesLegacyClient.ABILITY_KEYS[0].getDefaultKey()));
		step(20, false, c -> {
			c.options.sneakKey.setPressed(false);
			check("echoes_dismissed", ClientRaceState.extra.getInt("echoes") == 0 && ClientRaceState.extra.getInt("capUsed") == 0);
			commands(c, "race resource @a 100");
			resetCooldowns(c);
		});
		step(10, false, c -> KeyBinding.onKeyPressed(MagesLegacyClient.ABILITY_KEYS[0].getDefaultKey()));
		step(20, false, c -> {
			check("squad_at_once", ClientRaceState.extra.getInt("echoes") == 2);
			c.setScreen(new BestiaryScreen());
		});
		step(20, false, c -> screenshot(c, "bestiary_squad"));
		step(5, false, c -> c.setScreen(null));
		step(5, false, c -> c.options.sneakKey.setPressed(true));
		step(5, false, c -> KeyBinding.onKeyPressed(MagesLegacyClient.ABILITY_KEYS[0].getDefaultKey()));
		step(20, false, c -> c.options.sneakKey.setPressed(false));

		// A wolf, the Song (its own key, H, on the wolf in sight), three feedings, the last by a campfire
		step(10, false, c -> {
			c.options.hudHidden = false;
			commands(c, "race resource @a 100", "clear @a", "give @a minecraft:beef 8");
			onServer(c, s -> {
				ServerWorld world = s.getOverworld();
				WolfEntity wolf = EntityType.WOLF.create(world);
				wolf.refreshPositionAndAngles(arena.getX() + 3.5, arena.getY(), arena.getZ() + 2.5, 0, 0);
				wolf.setAiDisabled(true);
				world.spawnEntity(wolf);
				world.setBlockState(arena.add(5, 0, 2), Blocks.CAMPFIRE.getDefaultState());
				return null;
			});
			tp(c, Vec3d.ofBottomCenter(arena).add(1.5, 0, 2.5), Vec3d.ofBottomCenter(arena).add(3.5, 0.5, 2.5));
			c.player.getInventory().selectedSlot = 8;
		});
		step(10, false, c -> KeyBinding.onKeyPressed(MagesLegacyClient.ABILITY_KEYS[3].getDefaultKey()));
		step(10, false, c -> {
			UUID me = c.player.getUuid();
			check("song_key", onServer(c, s -> !RaceState.get(s).data(me).shepherd.rituals.isEmpty()));
			selectItem(c, Items.BEEF);
		});
		for (int i = 0; i < 4; i++) {
			// The ritual wants a minute between feedings; the harness moves the last one back before each click
			// (the click itself reaches the server a tick later)
			step(2, false, c -> {
				UUID me = c.player.getUuid();
				onServer(c, s -> {
					RaceState.get(s).data(me).shepherd.rituals.values().forEach(r -> r.lastFeed -= 2400);
					return null;
				});
			});
			step(10, false, c -> interactWolf(c));
		}
		// The bond is made on the server a tick after the last click and reaches the client with the next sync
		step(20, false, c -> { });
		step(20, false, c -> {
			check("wolf_bonded", ClientRaceState.extra.getList("bonds", 10).size() == 1);
			screenshot(c, "bonded");
			resetCooldowns(c);
			c.player.getInventory().selectedSlot = 8;
		});
		// Petting the wolf with a real right click, empty-handed
		step(10, false, c -> interactWolf(c));
		step(10, false, c -> {
			UUID me = c.player.getUuid();
			check("wolf_petted", onServer(c, s -> RaceState.get(s).data(me).shepherd.bonds.get(0).pettedAt > 0));
		});
		step(10, false, c -> KeyBinding.onKeyPressed(MagesLegacyClient.ABILITY_KEYS[1].getDefaultKey()));
		step(20, false, c -> {
			UUID me = c.player.getUuid();
			check("rally_strengthens", onServer(c, s -> s.getOverworld().getEntitiesByClass(WolfEntity.class,
					new Box(arena).expand(12), w -> me.equals(w.getOwnerUuid())).stream().allMatch(w -> w.hasStatusEffect(StatusEffects.STRENGTH))));
			screenshot(c, "hud_shepherd");
			c.setScreen(new BestiaryScreen());
		});
		step(20, false, c -> {
			screenshot(c, "bestiary_bonded");
			c.setScreen(new TalentScreen());
		});
		step(20, false, c -> {
			screenshot(c, "talents_shepherd");
			c.setScreen(null);
		});
		step(20, false, this::finish);

		ClientTickEvents.END_CLIENT_TICK.register(this::tick);
	}

	// ---- server-side helpers ----

	/** A 21x13 mycelium field at the spawn with 6 blocks of air above; returns the block above its centre. */
	private static BlockPos prepareArena(ServerWorld world) {
		BlockPos spawn = world.getSpawnPos();
		// Heightmaps of unloaded chunks read as the bottom of the world: generate the chunk first
		world.getChunk(spawn.getX() >> 4, spawn.getZ() >> 4);
		BlockPos top = world.getTopPosition(Heightmap.Type.MOTION_BLOCKING_NO_LEAVES, spawn);
		for (int dx = -10; dx <= 10; dx++) {
			for (int dz = -6; dz <= 6; dz++) {
				world.setBlockState(top.add(dx, -1, dz), Blocks.MYCELIUM.getDefaultState());
				world.setBlockState(top.add(dx, -2, dz), Blocks.DIRT.getDefaultState());
				for (int dy = 0; dy < 6; dy++) {
					world.setBlockState(top.add(dx, dy, dz), Blocks.AIR.getDefaultState());
				}
			}
		}
		world.setBlockState(top.add(-3, 0, 4), Blocks.RED_MUSHROOM.getDefaultState());
		world.setBlockState(top.add(4, 0, 5), Blocks.BROWN_MUSHROOM.getDefaultState());
		return top;
	}

	/** A real right click on the nearest wolf, the way a player would. */
	private static void interactWolf(MinecraftClient client) {
		WolfEntity wolf = client.world.getEntitiesByClass(WolfEntity.class, client.player.getBoundingBox().expand(6), w -> true)
				.stream().findFirst().orElse(null);
		if (wolf != null) {
			client.interactionManager.interactEntity(client.player, wolf, Hand.MAIN_HAND);
		}
	}

	private static Race serverRace(MinecraftClient client) {
		UUID me = client.player.getUuid();
		return onServer(client, s -> RaceState.get(s).data(me).race);
	}

	private static void resetCooldowns(MinecraftClient client) {
		UUID me = client.player.getUuid();
		onServer(client, s -> {
			RaceData data = RaceState.get(s).data(me);
			data.readyAt.clear();
			return null;
		});
	}

	private static <T> T onServer(MinecraftClient client, Function<MinecraftServer, T> action) {
		MinecraftServer server = client.getServer();
		return server.submit(() -> action.apply(server)).join();
	}

	private static void commands(MinecraftClient client, String... commands) {
		onServer(client, server -> {
			for (String command : commands) {
				server.getCommandManager().executeWithPrefix(server.getCommandSource(), command);
			}
			return null;
		});
	}

	// ---- client-side helpers ----

	private void step(int waitAfter, boolean needsTerrain, Consumer<MinecraftClient> action) {
		steps.add(new Step(waitAfter, needsTerrain, action));
	}

	private void tick(MinecraftClient client) {
		if (client.player == null || client.world == null || client.getServer() == null || steps.isEmpty()) {
			return;
		}
		ticks++;
		if (ticks < nextStepTick) {
			return;
		}
		Step next = steps.peek();
		if (next.needsTerrain() && !client.worldRenderer.isTerrainRenderComplete() && ticks < nextStepTick + 400) {
			return;
		}
		steps.poll();
		try {
			next.action().accept(client);
		} catch (RuntimeException e) {
			LOGGER.error("[harness] step failed", e);
			check("step_without_exception", false);
		}
		nextStepTick = ticks + next.waitAfter();
	}

	/** Teleports the player (server side) to {@code from}, looking at {@code at}. */
	private static void tp(MinecraftClient client, Vec3d from, Vec3d at) {
		Vec3d d = at.subtract(from.add(0, client.player.getStandingEyeHeight(), 0));
		float yaw = (float) Math.toDegrees(Math.atan2(-d.x, d.z));
		float pitch = (float) -Math.toDegrees(Math.atan2(d.y, Math.sqrt(d.x * d.x + d.z * d.z)));
		commands(client, String.format(Locale.ROOT, "tp @a %.2f %.2f %.2f %.1f %.1f", from.x, from.y, from.z, yaw, pitch));
	}

	private static void selectItem(MinecraftClient client, Item item) {
		for (int i = 0; i < 9; i++) {
			if (client.player.getInventory().getStack(i).isOf(item)) {
				client.player.getInventory().selectedSlot = i;
				return;
			}
		}
	}

	private void screenshot(MinecraftClient client, String name) {
		ScreenshotRecorder.saveScreenshot(client.runDirectory, name + ".png", client.getFramebuffer(), message -> {});
		LOGGER.info("[harness] screenshot {}.png", name);
	}

	private void finish(MinecraftClient client) {
		boolean pass = !results.isEmpty() && results.stream().allMatch(line -> line.endsWith(": OK"));
		results.add("RESULT " + (pass ? "PASS" : "FAIL"));
		try {
			Files.write(client.runDirectory.toPath().resolve("harness-result.txt"), results, StandardCharsets.UTF_8);
		} catch (IOException e) {
			LOGGER.error("[harness] could not write the result file", e);
		}
		LOGGER.info("[harness] {}", results.get(results.size() - 1));
		client.scheduleStop();
	}

	private void check(String name, boolean ok) {
		String line = name + ": " + (ok ? "OK" : "FAIL");
		results.add(line);
		LOGGER.info("[harness] {}", line);
	}
}
