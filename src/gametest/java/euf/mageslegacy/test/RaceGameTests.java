package euf.mageslegacy.test;

import euf.mageslegacy.MagesLegacy;
import euf.mageslegacy.frozen.Frozen;
import euf.mageslegacy.mycelid.Mycelid;
import euf.mageslegacy.net.RacePackets;
import euf.mageslegacy.race.AbilityResult;
import euf.mageslegacy.race.Progression;
import euf.mageslegacy.race.Progression.UnlockResult;
import euf.mageslegacy.race.Race;
import euf.mageslegacy.race.RaceData;
import euf.mageslegacy.race.RaceState;
import euf.mageslegacy.race.Talent;
import euf.mageslegacy.race.Talents;
import euf.mageslegacy.shepherd.BeastKind;
import euf.mageslegacy.shepherd.Bonds;
import euf.mageslegacy.shepherd.Creatures;
import euf.mageslegacy.shepherd.EchoType;
import euf.mageslegacy.shepherd.Echoes;
import euf.mageslegacy.shepherd.Shepherd;
import euf.mageslegacy.shepherd.ShepherdData;
import java.util.List;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.advancement.Advancement;
import net.minecraft.block.Blocks;
import net.minecraft.block.entity.AbstractFurnaceBlockEntity;
import net.minecraft.block.JukeboxBlock;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.ItemEntity;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.entity.mob.ZombieEntity;
import net.minecraft.entity.passive.BeeEntity;
import net.minecraft.entity.passive.CatEntity;
import net.minecraft.entity.passive.FoxEntity;
import net.minecraft.entity.passive.GoatEntity;
import net.minecraft.entity.passive.LlamaEntity;
import net.minecraft.entity.passive.ParrotEntity;
import net.minecraft.entity.passive.PolarBearEntity;
import net.minecraft.entity.passive.RabbitEntity;
import net.minecraft.entity.passive.WolfEntity;
import net.minecraft.entity.projectile.ArrowEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.test.GameTest;
import net.minecraft.test.GameTestException;
import net.minecraft.test.TestContext;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.GlobalPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.GameMode;

public class RaceGameTests implements FabricGameTest {
	static ServerPlayerEntity mycelid(TestContext ctx, BlockPos at) {
		ServerPlayerEntity player = (ServerPlayerEntity) ctx.createMockCreativeServerPlayerInWorld();
		player.changeGameMode(GameMode.SURVIVAL);
		player.refreshPositionAndAngles(at.getX() + 0.5, at.getY(), at.getZ() + 0.5, 0, 0);
		RaceState.of(player).reset(Race.MYCELID);
		return player;
	}

	static void floor(ServerWorld world, BlockPos from, int sizeX, int sizeZ) {
		for (int x = 0; x < sizeX; x++) {
			for (int z = 0; z < sizeZ; z++) {
				world.setBlockState(from.add(x, -1, z), Blocks.STONE.getDefaultState());
			}
		}
	}

	static void done(TestContext ctx, ServerPlayerEntity player) {
		player.getServerWorld().removePlayer(player, Entity.RemovalReason.DISCARDED);
		ctx.complete();
	}

	@GameTest(templateName = EMPTY_STRUCTURE)
	public void dataSavesAndLevelsGrantPoints(TestContext ctx) {
		RaceData data = new RaceData();
		data.reset(Race.MYCELID);
		data.addResource(42);
		check(data.addXp(RaceData.xpForNext(0) + RaceData.xpForNext(1)) == 2 && data.level == 2, "two levels from exact xp, got " + data.level);
		data.talents.add(Talents.SWARM_WIDE);
		data.tastes.add("minecraft:strength");
		RaceData copy = RaceData.fromNbt(data.toNbt());
		check(copy.race == Race.MYCELID && copy.level == 2 && (int) copy.resource == 42 && copy.has(Talents.SWARM_WIDE)
				&& copy.tastes.contains("minecraft:strength"), "race data must survive a save");
		check(copy.freePoints() == 1, "2 levels = 2 points, 1 spent: " + copy.freePoints());
		data.addXp(1_000_000);
		check(data.level == RaceData.MAX_LEVEL && data.totalPoints() == 60, "level 50 gives 60 points, got " + data.totalPoints());
		data.addResource(500);
		check(data.resource == RaceData.MAX_RESOURCE, "resource is capped at 100");
		ctx.complete();
	}

	@GameTest(templateName = EMPTY_STRUCTURE, batchId = "choose")
	public void aRaceIsChosenOnceAndOnlyIfAwake(TestContext ctx) {
		ServerPlayerEntity player = (ServerPlayerEntity) ctx.createMockCreativeServerPlayerInWorld();
		RaceData data = RaceState.of(player);
		check(data.race == null, "a new player has no race");
		check(!RacePackets.choose(player, Race.BLOODKIN), "a sleeping race can't be picked yet");
		check(RacePackets.choose(player, Race.MYCELID) && data.race == Race.MYCELID, "picking the Mycelids should work");
		check(!RacePackets.choose(player, Race.MYCELID), "the choice is made once");
		check(RaceState.get(ctx.getWorld().getServer()).data(player.getUuid()) == data, "the data lives in the saved state");
		done(ctx, player);
	}

	@GameTest(templateName = EMPTY_STRUCTURE, batchId = "talents")
	public void talentsNeedPointsPrerequisitesAndMilestones(TestContext ctx) {
		ServerPlayerEntity player = mycelid(ctx, ctx.getAbsolutePos(new BlockPos(1, 2, 1)));
		RaceData data = RaceState.of(player);
		check(Progression.unlock(player, data, Talents.SWARM_WIDE) == UnlockResult.NO_POINTS, "level 0 has no points");
		Progression.addXp(player, data, 2000);
		check(data.freePoints() >= 6, "enough points now: " + data.freePoints());
		check(Progression.unlock(player, data, Talents.SWARM_POTENT) == UnlockResult.NEEDS_TALENT, "the branch goes in order");
		check(Progression.unlock(player, data, Talents.SWARM_WIDE) == UnlockResult.OK && data.has(Talents.SWARM_WIDE), "tier 1 opens at once");
		check(Progression.unlock(player, data, Talents.SWARM_WIDE) == UnlockResult.ALREADY, "no double buy");
		check(Progression.unlock(player, data, Talents.SWARM_POTENT) == UnlockResult.NEEDS_MILESTONE, "tier 2 waits for the Nether");
		check(Progression.unlock(player, data, "nonsense") == UnlockResult.UNKNOWN, "unknown talents are refused");

		Advancement nether = player.server.getAdvancementLoader().get(new Identifier("story/enter_the_nether"));
		for (String criterion : nether.getCriteria().keySet()) {
			player.getAdvancementTracker().grantCriterion(nether, criterion);
		}
		check(Progression.openTiers(player) == 2, "the Nether opens tier 2");
		int before = data.freePoints();
		check(Progression.unlock(player, data, Talents.SWARM_POTENT) == UnlockResult.OK, "tier 2 after the Nether");
		check(data.freePoints() == before - 2, "tier 2 talents cost 2");
		check(Progression.unlock(player, data, Talents.SWARM_LINGERING) == UnlockResult.NEEDS_TALENT, "infection comes before the long bloom");
		done(ctx, player);
	}

	@GameTest(templateName = EMPTY_STRUCTURE)
	public void theTreeHasRoomForBuilds(TestContext ctx) {
		treeShape(Race.MYCELID);
		treeShape(Race.SHEPHERD);
		treeShape(Race.FROZEN);
		ctx.complete();
	}

	private static void treeShape(Race race) {
		List<Talent> all = Talents.of(race);
		check(all.size() == 33, "33 talents for " + race + ", got " + all.size());
		int[] perTier = {0, 3, 4, 3, 1};
		for (int branch = 0; branch < 3; branch++) {
			for (int tier = 1; tier <= 4; tier++) {
				int b = branch;
				int t = tier;
				long count = all.stream().filter(x -> x.branch() == b && x.tier() == t).count();
				check(count == perTier[tier], "branch " + branch + " tier " + tier + " should have " + perTier[tier] + ", has " + count);
			}
		}
		int total = all.stream().mapToInt(Talent::cost).sum();
		check(total == 75 && total > 60, "the whole tree costs 75, more than the 60 points of level 50: " + total);
		for (Talent t : all) {
			if (t.requires() != null) {
				Talent req = Talents.byId(t.requires()).orElse(null);
				check(req != null && req.branch() == t.branch() && req.tier() <= t.tier(), t.id() + " needs a talent of its own branch, not higher");
			}
		}
	}

	@GameTest(templateName = EMPTY_STRUCTURE, batchId = "cloud_talents", tickLimit = 100)
	public void cloudTalentsWeakenMonstersAndMendPlayers(TestContext ctx) {
		ServerWorld world = ctx.getWorld();
		BlockPos at = ctx.getAbsolutePos(new BlockPos(2, 2, 2));
		floor(world, at.add(-2, 0, -2), 5, 5);
		ServerPlayerEntity player = mycelid(ctx, at);
		ZombieEntity zombie = EntityType.ZOMBIE.create(world);
		zombie.refreshPositionAndAngles(at.getX() + 2.5, at.getY(), at.getZ() + 0.5, 0, 0);
		zombie.setAiDisabled(true);
		world.spawnEntity(zombie);
		RaceData data = RaceState.of(player);
		data.talents.add(Talents.SWARM_WEAKNESS);
		data.talents.add(Talents.SWARM_MENDING);
		data.addResource(30);
		check(Mycelid.sporeCloud(player, data) == AbilityResult.OK, "the cloud should rise");
		ctx.waitAndRun(25, () -> {
			StatusEffectInstance weakness = zombie.getStatusEffect(StatusEffects.WEAKNESS);
			check(weakness != null && weakness.getAmplifier() == 1, "Rot of Strength: the zombie gets Weakness II");
			StatusEffectInstance regen = player.getStatusEffect(StatusEffects.REGENERATION);
			check(regen != null && regen.getAmplifier() == 1, "Mending Mist: the player gets Regeneration II");
			zombie.discard();
			done(ctx, player);
		});
	}

	@GameTest(templateName = EMPTY_STRUCTURE, batchId = "flesh_talents", tickLimit = 120)
	public void bodyTalentsChangeDamageHealthAndCosts(TestContext ctx) {
		ServerWorld world = ctx.getWorld();
		BlockPos at = ctx.getAbsolutePos(new BlockPos(2, 2, 2));
		floor(world, at.add(-2, 0, -2), 5, 5);
		ServerPlayerEntity player = mycelid(ctx, at);
		RaceData data = RaceState.of(player);
		check(Mycelid.jumpCost(data) == 15, "a plain jump costs 15");
		data.talents.add(Talents.NET_CHEAP);
		check(Mycelid.jumpCost(data) == 10, "Light Spore: the jump costs 10");
		data.talents.add(Talents.FLESH_HEARTY);
		data.talents.add(Talents.NET_LIGHT_STEP);
		data.talents.add(Talents.FLESH_FIREPROOF);
		// Spawn protection of new players lasts 3 seconds
		ctx.waitAndRun(70, () -> {
			Mycelid.tickSecond(player, data);
			check(Math.abs(player.getMaxHealth() - 24) < 0.01f, "Dense Body: 24 max health, got " + player.getMaxHealth());
			player.setHealth(20);
			player.timeUntilRegen = 0;
			player.damage(world.getDamageSources().fall(), 4);
			check(Math.abs(player.getHealth() - 18) < 0.01f, "Soft Step halves the fall: 20 - 2 = 18, got " + player.getHealth());
			player.setHealth(20);
			player.timeUntilRegen = 0;
			player.damage(world.getDamageSources().inFire(), 4);
			check(Math.abs(player.getHealth() - 15.4f) < 0.01f, "Damp Flesh: fire 1.15x, 20 - 4.6 = 15.4, got " + player.getHealth());
			Mycelid.removeCapArmor(player);
			check(Math.abs(player.getMaxHealth() - 20) < 0.01f, "the bonus health goes with the race");
			done(ctx, player);
		});
	}

	@GameTest(templateName = EMPTY_STRUCTURE, batchId = "rebirth", tickLimit = 120)
	public void rebirthSproutsAtTheNearestNode(TestContext ctx) {
		ServerWorld world = ctx.getWorld();
		BlockPos at = ctx.getAbsolutePos(new BlockPos(1, 2, 1));
		floor(world, at.add(-1, 0, -1), 8, 3);
		ServerPlayerEntity player = mycelid(ctx, at);
		RaceData data = RaceState.of(player);
		BlockPos node = at.add(5, 0, 0);
		Mycelid.placeNodeAt(world, node, player.getUuid(), 3);
		data.talents.add(Talents.FLESH_REBIRTH);
		ctx.waitAndRun(70, () -> {
			player.timeUntilRegen = 0;
			player.damage(world.getDamageSources().generic(), 1000);
			check(player.isAlive(), "Rebirth: the killing blow does not kill");
			check(Math.abs(player.getHealth() - player.getMaxHealth() / 2) < 0.01f, "half health after rebirth, got " + player.getHealth());
			check(player.getBlockPos().isWithinDistance(node.up(), 1.5), "sprouted at the node, is at " + player.getBlockPos());
			check(Mycelid.allowDeath(player), "once in 20 minutes: a second killing blow right away is a real death");
			world.removeBlock(node, false);
			done(ctx, player);
		});
	}

	@GameTest(templateName = EMPTY_STRUCTURE, batchId = "diet")
	public void mushroomsGiveSporesAndOneTasteAtATime(TestContext ctx) {
		ServerPlayerEntity player = mycelid(ctx, ctx.getAbsolutePos(new BlockPos(1, 2, 1)));
		RaceData data = RaceState.of(player);
		player.getHungerManager().setFoodLevel(10);
		ItemStack red = new ItemStack(Items.RED_MUSHROOM, 3);
		check(Mycelid.eat(player, data, red), "a Mycelid eats a raw red mushroom");
		check(red.getCount() == 2 && player.getHungerManager().getFoodLevel() == 12, "one mushroom eaten, +2 food");
		check((int) data.resource == 10 && player.hasStatusEffect(StatusEffects.STRENGTH), "spores and the strength taste");
		check(!Mycelid.eat(player, data, red), "a short pause between mushrooms");
		ItemStack brown = new ItemStack(Items.BROWN_MUSHROOM);
		check(Mycelid.eat(player, data, brown), "a brown mushroom next");
		check(player.hasStatusEffect(StatusEffects.RESISTANCE) && !player.hasStatusEffect(StatusEffects.STRENGTH),
				"only one taste at a time: the older one fades");

		data.talents.add(Talents.FLESH_TWO_TASTES);
		player.getItemCooldownManager().remove(Items.RED_MUSHROOM);
		check(Mycelid.eat(player, data, red), "red again");
		check(player.hasStatusEffect(StatusEffects.RESISTANCE) && player.hasStatusEffect(StatusEffects.STRENGTH), "two tastes with the talent");

		// Meat feeds a Mycelid half as well (8 hunger -> 4); goes through the eatFood mixin
		player.getHungerManager().setFoodLevel(4);
		player.eatFood(ctx.getWorld(), new ItemStack(Items.COOKED_BEEF));
		check(player.getHungerManager().getFoodLevel() == 8, "steak should give 4 food to a Mycelid, food is " + player.getHungerManager().getFoodLevel());
		float sporesBefore = data.resource;
		player.eatFood(ctx.getWorld(), new ItemStack(Items.MUSHROOM_STEW));
		check(data.resource >= sporesBefore + 14, "a mushroom stew feeds the spores");
		done(ctx, player);
	}

	@GameTest(templateName = EMPTY_STRUCTURE, batchId = "jump", tickLimit = 150)
	public void sporeJumpSproutsWhereYouLook(TestContext ctx) {
		ServerWorld world = ctx.getWorld();
		BlockPos start = ctx.getAbsolutePos(new BlockPos(1, 2, 0));
		floor(world, start.add(-1, 0, 0), 3, 20);
		ServerPlayerEntity player = mycelid(ctx, start);
		// Yaw 0 looks south (+Z), a little down so the spore lands on the floor
		player.refreshPositionAndAngles(start.getX() + 0.5, start.getY(), start.getZ() + 0.5, 0, 25);
		RaceData data = RaceState.of(player);
		data.addResource(40);
		Vec3d from = player.getPos();
		check(Mycelid.sporeJump(player, data) == AbilityResult.OK, "the jump should start");
		check((int) data.resource == 25, "a jump costs 15 spores");
		check(Mycelid.sporeJump(player, data) == AbilityResult.COOLDOWN, "no second jump right away");
		ctx.waitAndRun(Mycelid.JUMP_DELAY + 3, () -> {
			double moved = player.getPos().subtract(from).horizontalLength();
			check(moved > 2 && moved <= 8.6, "the player should sprout up to 8 blocks ahead, moved " + moved);
			check(player.getZ() > from.z, "forward is south, now at " + player.getPos());
			check(!world.isSpaceEmpty(player, player.getBoundingBox().offset(0, -0.3, 0)), "landed on the floor");
			// Looking almost level: the spore flies the full reach and the Mycelid settles on the floor there
			Vec3d second = player.getPos();
			player.refreshPositionAndAngles(second.x, second.y, second.z, 0, 3);
			data.readyAt.clear();
			data.chainLeft = 0;
			check(Mycelid.sporeJump(player, data) == AbilityResult.OK, "the level jump should start");
			ctx.waitAndRun(Mycelid.JUMP_DELAY + 3, () -> {
				double far = player.getPos().subtract(second).horizontalLength();
				check(far > 6 && far <= 8.6, "a level jump should cover most of the 8 blocks, moved " + far);
				done(ctx, player);
			});
		});
	}

	@GameTest(templateName = EMPTY_STRUCTURE, batchId = "cloud", tickLimit = 100)
	public void sporeCloudChokesMonstersAndHealsPlayers(TestContext ctx) {
		ServerWorld world = ctx.getWorld();
		BlockPos at = ctx.getAbsolutePos(new BlockPos(2, 2, 2));
		floor(world, at.add(-2, 0, -2), 5, 5);
		ServerPlayerEntity player = mycelid(ctx, at);
		ZombieEntity zombie = EntityType.ZOMBIE.create(world);
		zombie.refreshPositionAndAngles(at.getX() + 2.5, at.getY(), at.getZ() + 0.5, 0, 0);
		zombie.setAiDisabled(true);
		world.spawnEntity(zombie);
		RaceData data = RaceState.of(player);
		data.addResource(30);
		check(Mycelid.sporeCloud(player, data) == AbilityResult.OK, "the cloud should rise");
		check(data.resource < 1, "a cloud costs 30 spores");
		check(Mycelid.sporeCloud(player, data) == AbilityResult.COOLDOWN, "one cloud at a time");
		ctx.waitAndRun(25, () -> {
			check(zombie.hasStatusEffect(StatusEffects.BLINDNESS) && zombie.hasStatusEffect(StatusEffects.WITHER),
					"the zombie goes blind and rots (the undead are immune to poison)");
			check(player.hasStatusEffect(StatusEffects.REGENERATION), "the Mycelid breathes them in and heals");
			zombie.discard();
			done(ctx, player);
		});
	}

	@GameTest(templateName = EMPTY_STRUCTURE, batchId = "dark", tickLimit = 200)
	public void darknessFeedsAndFireBurns(TestContext ctx) {
		ServerWorld world = ctx.getWorld();
		BlockPos at = ctx.getAbsolutePos(new BlockPos(2, 2, 2));
		// A closed stone box: no light gets in
		for (int x = -1; x <= 1; x++) {
			for (int y = -1; y <= 2; y++) {
				for (int z = -1; z <= 1; z++) {
					if (x != 0 || z != 0 || y == -1 || y == 2) {
						world.setBlockState(at.add(x, y, z), Blocks.STONE.getDefaultState());
					}
				}
			}
		}
		world.setBlockState(at.down(), Blocks.MYCELIUM.getDefaultState());
		ServerPlayerEntity player = mycelid(ctx, at);
		RaceData data = RaceState.of(player);
		ServerPlayerEntity human = (ServerPlayerEntity) ctx.createMockCreativeServerPlayerInWorld();
		human.changeGameMode(GameMode.SURVIVAL);
		// Wait out the light update in the box and the 3 s of spawn protection new players get
		ctx.waitAndRun(70, () -> {
			check(Mycelid.inDark(player) && Mycelid.onMycelium(player), "inside a box on mycelium it is dark");
			float before = data.resource;
			Mycelid.tickSecond(player, data);
			check(player.hasStatusEffect(StatusEffects.NIGHT_VISION) && player.hasStatusEffect(StatusEffects.REGENERATION),
					"night vision and regeneration in the dark");
			check(Math.abs(data.resource - before - 2.0f) < 0.01f, "0.5 + 0.5 dark + 1 mycelium = 2 spores a second, got " + (data.resource - before));

			player.setHealth(20);
			player.timeUntilRegen = 0;
			player.damage(world.getDamageSources().inFire(), 4);
			check(Math.abs(player.getHealth() - 14) < 0.01f, "fire hurts a Mycelid 1.5x: 20 - 6 = 14, got " + player.getHealth());
			human.setHealth(20);
			human.damage(world.getDamageSources().inFire(), 4);
			check(Math.abs(human.getHealth() - 16) < 0.01f, "a player without a race takes plain fire damage, got " + human.getHealth());
			world.removePlayer(human, Entity.RemovalReason.DISCARDED);
			done(ctx, player);
		});
	}

	@GameTest(templateName = EMPTY_STRUCTURE, batchId = "nodes")
	public void nodesFormANetworkAnyoneCanTravel(TestContext ctx) {
		ServerWorld world = ctx.getWorld();
		BlockPos base = ctx.getAbsolutePos(new BlockPos(0, 2, 0));
		floor(world, base, 12, 3);
		ServerPlayerEntity owner = mycelid(ctx, base);
		List<BlockPos> spots = List.of(base, base.add(3, 0, 0), base.add(6, 0, 0), base.add(9, 0, 0));
		for (BlockPos spot : spots) {
			Mycelid.placeNodeAt(world, spot, owner.getUuid(), 3);
		}
		RaceState state = RaceState.get(world.getServer());
		check(!world.getBlockState(spots.get(0)).isOf(MagesLegacy.MYCELIUM_NODE), "past the limit the oldest node withers");
		check(state.nodes(owner.getUuid()).size() == 3, "three nodes in the network");

		// A stranger uses the network: from node 2 to node 3
		ServerPlayerEntity guest = (ServerPlayerEntity) ctx.createMockCreativeServerPlayerInWorld();
		guest.refreshPositionAndAngles(spots.get(1).getX() + 1.5, spots.get(1).getY(), spots.get(1).getZ() + 1.5, 0, 0);
		check(Mycelid.travel(guest, spots.get(1)), "travel through a node");
		check(guest.getBlockPos().isWithinDistance(spots.get(2), 1.5), "the guest should come out at the next node, is at " + guest.getBlockPos());

		world.breakBlock(spots.get(2), false);
		check(!state.nodes(owner.getUuid()).contains(GlobalPos.create(world.getRegistryKey(), spots.get(2))), "a broken node leaves the network");
		check(state.nodeOwner(world.getRegistryKey(), spots.get(3)).orElse(null).equals(owner.getUuid()), "nodes know their owner");
		world.removePlayer(guest, Entity.RemovalReason.DISCARDED);
		for (BlockPos spot : spots) {
			world.removeBlock(spot, false);
		}
		done(ctx, owner);
	}

	@GameTest(templateName = EMPTY_STRUCTURE, batchId = "node_farm")
	public void walkingBetweenNodesFarmsNothing(TestContext ctx) {
		ServerWorld world = ctx.getWorld();
		BlockPos base = ctx.getAbsolutePos(new BlockPos(0, 2, 0));
		floor(world, base, 8, 3);
		ServerPlayerEntity player = mycelid(ctx, base);
		RaceData data = RaceState.of(player);
		data.talents.add(Talents.NET_SHARED);
		BlockPos a = base;
		BlockPos b = base.add(5, 0, 0);
		Mycelid.placeNodeAt(world, a, player.getUuid(), 3);
		Mycelid.placeNodeAt(world, b, player.getUuid(), 3);
		int xp = data.xp;
		check(Mycelid.travel(player, a) && Mycelid.travel(player, b) && Mycelid.travel(player, a), "back and forth works");
		check(data.xp == xp, "travelling gives no race experience: " + xp + " -> " + data.xp);
		check(Math.abs(data.resource - 10) < 0.01f, "Fed Network pays 10 spores once, not on every trip: " + data.resource);
		world.removeBlock(a, false);
		world.removeBlock(b, false);
		done(ctx, player);
	}

	// ---- Shepherds ----

	static ServerPlayerEntity shepherd(TestContext ctx, BlockPos at) {
		ServerPlayerEntity player = (ServerPlayerEntity) ctx.createMockCreativeServerPlayerInWorld();
		player.changeGameMode(GameMode.SURVIVAL);
		player.refreshPositionAndAngles(at.getX() + 0.5, at.getY(), at.getZ() + 0.5, 0, 0);
		RaceState.of(player).reset(Race.SHEPHERD);
		return player;
	}

	static void learn(ServerPlayerEntity player, RaceData data, EchoType type) {
		for (int i = 0; i < type.kills(); i++) {
			Shepherd.countKill(player, data, type);
		}
	}

	@GameTest(templateName = EMPTY_STRUCTURE, batchId = "echo_learn")
	public void echoesAreLearnedFromKills(TestContext ctx) {
		ServerPlayerEntity player = shepherd(ctx, ctx.getAbsolutePos(new BlockPos(1, 2, 1)));
		RaceData data = RaceState.of(player);
		EchoType zombie = EchoType.of(EntityType.ZOMBIE).orElseThrow();
		for (int i = 0; i < zombie.kills() - 1; i++) {
			Shepherd.countKill(player, data, zombie);
		}
		check(!Shepherd.learned(data, zombie), "19 zombies are not enough");
		check(Shepherd.call(player, data) == AbilityResult.NO_ECHO, "nothing to call yet");
		int xp = data.xp + data.level * 1000;
		Shepherd.countKill(player, data, zombie);
		check(Shepherd.learned(data, zombie), "the 20th zombie teaches its echo");
		check(data.shepherd.presets.get(0).getOrDefault(zombie.id(), 0) == 1, "the first learned echo goes into the first squad");
		check(data.xp + data.level * 1000 > xp, "learning an echo gives race experience");
		RaceData copy = RaceData.fromNbt(data.toNbt());
		check(copy.shepherd.kills(zombie.id()) == 20 && copy.shepherd.presets.get(0).getOrDefault(zombie.id(), 0) == 1, "the bestiary survives a save");
		done(ctx, player);
	}

	@GameTest(templateName = EMPTY_STRUCTURE, batchId = "echo_call", tickLimit = 200)
	public void theCallRaisesAnEchoThatFadesWithoutLoot(TestContext ctx) {
		ServerWorld world = ctx.getWorld();
		BlockPos at = ctx.getAbsolutePos(new BlockPos(3, 2, 3));
		floor(world, at.add(-3, 0, -3), 7, 7);
		ServerPlayerEntity player = shepherd(ctx, at);
		RaceData data = RaceState.of(player);
		learn(player, data, EchoType.of(EntityType.ZOMBIE).orElseThrow());
		data.addResource(100);
		check(Shepherd.call(player, data) == AbilityResult.OK, "the Call should raise the echo");
		check(Math.abs(data.resource - 85) < 0.01f, "a zombie echo costs 15 voice, left " + data.resource);
		check(Echoes.count(player.getUuid()) == 1 && Shepherd.capacityUsed(data, player.getUuid()) == 1, "one echo, one bond slot");
		MobEntity echo = world.getEntitiesByClass(MobEntity.class, new Box(at).expand(8), Echoes::isEcho).stream().findFirst().orElse(null);
		check(echo != null, "the echo stands in the world");
		ctx.waitAndRun(70, () -> {
			float before = echo.getHealth();
			echo.timeUntilRegen = 0;
			echo.damage(world.getDamageSources().playerAttack(player), 4);
			check(echo.getHealth() == before, "a Shepherd can't hurt its own echo");
			echo.timeUntilRegen = 0;
			echo.damage(world.getDamageSources().generic(), 1000);
			check(!echo.isAlive() && Echoes.count(player.getUuid()) == 0, "a fallen echo fades");
			check(world.getEntitiesByClass(ItemEntity.class, new Box(at).expand(8), e -> true).isEmpty(), "an echo drops nothing");
			done(ctx, player);
		});
	}

	@GameTest(templateName = EMPTY_STRUCTURE, batchId = "echo_capacity", tickLimit = 100)
	public void heavyArmourCrowdsTheBonds(TestContext ctx) {
		ServerWorld world = ctx.getWorld();
		BlockPos at = ctx.getAbsolutePos(new BlockPos(3, 2, 3));
		floor(world, at.add(-3, 0, -3), 7, 7);
		ServerPlayerEntity player = shepherd(ctx, at);
		RaceData data = RaceState.of(player);
		check(Shepherd.capacityMax(player, data) == 4, "4 bond slots to start");
		player.equipStack(EquipmentSlot.CHEST, new ItemStack(Items.DIAMOND_CHESTPLATE));
		player.equipStack(EquipmentSlot.LEGS, new ItemStack(Items.DIAMOND_LEGGINGS));
		player.equipStack(EquipmentSlot.HEAD, new ItemStack(Items.IRON_HELMET));
		player.equipStack(EquipmentSlot.FEET, new ItemStack(Items.IRON_BOOTS));
		check(Shepherd.capacityMax(player, data) == 1, "2 diamond + 2 iron = 6 points = 3 slots, got " + Shepherd.capacityMax(player, data));
		data.talents.add(Talents.VOICE_ARMOR);
		player.equipStack(EquipmentSlot.LEGS, new ItemStack(Items.IRON_LEGGINGS));
		check(Shepherd.capacityMax(player, data) == 3, "Used to Iron: only the diamond chestplate counts (2 points = 1 slot)");
		learn(player, data, EchoType.of(EntityType.SKELETON).orElseThrow());
		data.shepherd.presets.get(0).clear();
		data.shepherd.presets.get(0).put(EchoType.of(EntityType.SKELETON).orElseThrow().id(), 2);
		data.addResource(100);
		check(Shepherd.call(player, data) == AbilityResult.OK && Echoes.count(player.getUuid()) == 1, "one skeleton fits (2 of 3 slots), the other doesn't");
		data.readyAt.clear();
		check(Shepherd.call(player, data) == AbilityResult.NO_CAPACITY, "no room for the second one"); 
		player.setSneaking(true);
		check(Shepherd.call(player, data) == AbilityResult.OK && Echoes.count(player.getUuid()) == 0, "sneak + Call sends them back");
		done(ctx, player);
	}

	@GameTest(templateName = EMPTY_STRUCTURE, batchId = "bond", tickLimit = 200)
	public void theBondRitualNeedsSongFoodAndFire(TestContext ctx) {
		ServerWorld world = ctx.getWorld();
		BlockPos at = ctx.getAbsolutePos(new BlockPos(3, 2, 3));
		floor(world, at.add(-3, 0, -3), 7, 7);
		ServerPlayerEntity player = shepherd(ctx, at);
		RaceData data = RaceState.of(player);
		data.addResource(100);
		WolfEntity wolf = EntityType.WOLF.create(world);
		wolf.refreshPositionAndAngles(at.getX() + 2.5, at.getY(), at.getZ() + 0.5, 0, 0);
		wolf.setAiDisabled(true);
		world.spawnEntity(wolf);

		player.setStackInHand(Hand.MAIN_HAND, new ItemStack(Items.BEEF, 8));
		check(Bonds.interact(player, data, wolf, Hand.MAIN_HAND) == ActionResult.PASS, "food before the Song does nothing special");
		// The Song is its own ability now: on the beast in sight (yaw 0 looks south, +Z), then a cooldown
		player.setStackInHand(Hand.MAIN_HAND, ItemStack.EMPTY);
		check(Bonds.song(player, data) == AbilityResult.NO_BEAST, "no beast in sight, no Song");
		wolf.refreshPositionAndAngles(at.getX() + 0.5, at.getY(), at.getZ() + 2.5, 0, 0);
		// A wolf is low: look down at it
		player.setPitch(30);
		check(Bonds.song(player, data) == AbilityResult.OK, "the Song of Bonds on the wolf ahead");
		player.setPitch(0);
		check(Math.abs(data.resource - 80) < 0.01f, "the Song costs 20 voice");
		check(Bonds.song(player, data) == AbilityResult.COOLDOWN, "and then waits 30 seconds");
		player.setSneaking(true);
		player.setStackInHand(Hand.MAIN_HAND, ItemStack.EMPTY);
		check(Bonds.interact(player, data, wolf, Hand.MAIN_HAND) == ActionResult.PASS, "sneak + right click no longer sings");
		player.setSneaking(false);
		player.setStackInHand(Hand.MAIN_HAND, new ItemStack(Items.BEEF, 8));
		check(Bonds.interact(player, data, wolf, Hand.MAIN_HAND) == ActionResult.SUCCESS, "first feeding");
		check(Bonds.interact(player, data, wolf, Hand.MAIN_HAND) == ActionResult.FAIL, "not again right away");
		ShepherdData.Ritual ritual = data.shepherd.rituals.get(wolf.getUuid());
		ritual.lastFeed -= Bonds.FEED_GAP;
		check(Bonds.interact(player, data, wolf, Hand.MAIN_HAND) == ActionResult.SUCCESS, "second feeding a minute later");
		ritual.lastFeed -= Bonds.FEED_GAP;
		check(Bonds.interact(player, data, wolf, Hand.MAIN_HAND) == ActionResult.SUCCESS, "third feeding");
		ritual.lastFeed -= Bonds.FEED_GAP;
		check(Bonds.interact(player, data, wolf, Hand.MAIN_HAND) == ActionResult.FAIL, "the last one needs a campfire");
		world.setBlockState(at.add(-2, 0, 0), Blocks.CAMPFIRE.getDefaultState());
		check(Bonds.interact(player, data, wolf, Hand.MAIN_HAND) == ActionResult.SUCCESS, "by the fire the bond is made");
		check(data.shepherd.bonds.size() == 1 && wolf.isTamed() && player.getUuid().equals(wolf.getOwnerUuid()), "the wolf is the Shepherd's");
		check(Shepherd.capacityUsed(data, player.getUuid()) == 2, "a wolf takes 2 bond slots");
		check(player.getMainHandStack().getCount() == 4, "four pieces of meat eaten");

		// It falls: rests instead of dying, and comes back when the rest is over
		wolf.timeUntilRegen = 0;
		wolf.damage(world.getDamageSources().generic(), 1000);
		ShepherdData.Bond bond = data.shepherd.bonds.get(0);
		check(wolf.isRemoved() && bond.resting(), "a fallen beast goes to rest");
		bond.restingUntil = world.getTime();
		Bonds.tickOwner(player, data);
		check(!bond.resting() && world.getEntity(bond.entity) instanceof WolfEntity back && back.isAlive(), "and comes back to its Shepherd");
		world.getEntity(bond.entity).discard();
		world.removeBlock(at.add(-2, 0, 0), false);
		done(ctx, player);
	}

	static <T extends MobEntity> T beast(TestContext ctx, EntityType<T> type, BlockPos at) {
		T mob = type.create(ctx.getWorld());
		mob.refreshPositionAndAngles(at.getX() + 0.5, at.getY(), at.getZ() + 0.5, 0, 0);
		mob.setAiDisabled(true);
		ctx.getWorld().spawnEntity(mob);
		return mob;
	}

	static void sing(ServerPlayerEntity player, RaceData data, MobEntity mob) {
		Bonds.singTo(player, data, mob);
	}

	@GameTest(templateName = EMPTY_STRUCTURE, batchId = "taming", tickLimit = 200)
	public void everyBeastIsWonItsOwnWay(TestContext ctx) {
		ServerWorld world = ctx.getWorld();
		BlockPos at = ctx.getAbsolutePos(new BlockPos(4, 2, 4));
		floor(world, at.add(-4, 0, -4), 9, 9);
		ServerPlayerEntity player = shepherd(ctx, at);
		RaceData data = RaceState.of(player);
		data.talents.add(Talents.VOICE_CAPACITY);
		data.talents.add(Talents.LEGION_CAPACITY);
		data.talents.add(Talents.LEGION_MORE);

		// The bee: two different flowers
		BeeEntity bee = beast(ctx, EntityType.BEE, at.add(2, 1, 0));
		data.addResource(100);
		sing(player, data, bee);
		player.setStackInHand(Hand.MAIN_HAND, new ItemStack(Items.POPPY, 4));
		check(Bonds.interact(player, data, bee, Hand.MAIN_HAND) == ActionResult.SUCCESS, "the first flower");
		check(Bonds.interact(player, data, bee, Hand.MAIN_HAND) == ActionResult.FAIL, "not the same flower twice");
		player.setStackInHand(Hand.MAIN_HAND, new ItemStack(Items.DANDELION));
		check(Bonds.interact(player, data, bee, Hand.MAIN_HAND) == ActionResult.SUCCESS && data.shepherd.bond(bee.getUuid()).isEmpty(),
				"a second kind is not yet enough");
		player.setStackInHand(Hand.MAIN_HAND, new ItemStack(Items.CORNFLOWER));
		check(Bonds.interact(player, data, bee, Hand.MAIN_HAND) == ActionResult.SUCCESS && data.shepherd.bond(bee.getUuid()).isPresent(),
				"a third kind of flower wins the bee");

		// The goat: three bare-handed headbutts, then wheat
		GoatEntity goat = beast(ctx, EntityType.GOAT, at.add(-2, 0, 0));
		data.addResource(100);
		sing(player, data, goat);
		player.setStackInHand(Hand.MAIN_HAND, new ItemStack(Items.WHEAT));
		check(Bonds.interact(player, data, goat, Hand.MAIN_HAND) == ActionResult.FAIL, "wheat before the headbutts is not enough");
		player.setStackInHand(Hand.MAIN_HAND, ItemStack.EMPTY);
		for (int i = 0; i < 5; i++) {
			data.shepherd.rituals.get(goat.getUuid()).lastFeed -= 20;
			check(Bonds.headbutt(player, data, goat), "a headbutt counts and is cancelled");
		}
		check(goat.getHealth() == goat.getMaxHealth(), "the headbutts did no harm");
		player.setStackInHand(Hand.MAIN_HAND, new ItemStack(Items.WHEAT));
		check(Bonds.interact(player, data, goat, Hand.MAIN_HAND) == ActionResult.SUCCESS && data.shepherd.bond(goat.getUuid()).isPresent(),
				"then wheat wins the goat");

		// The parrot: three songs a minute apart, with music playing
		ParrotEntity parrot = beast(ctx, EntityType.PARROT, at.add(0, 0, 2));
		data.addResource(100);
		check(Bonds.singTo(player, data, parrot) == AbilityResult.HINTED, "no music, no listening");
		world.setBlockState(at.add(0, 0, 4), Blocks.JUKEBOX.getDefaultState().with(JukeboxBlock.HAS_RECORD, true));
		for (int i = 0; i < 3; i++) {
			data.addResource(100);
			sing(player, data, parrot);
			ShepherdData.Ritual r = data.shepherd.rituals.get(parrot.getUuid());
			if (r != null) {
				r.lastFeed -= Bonds.FEED_GAP;
			}
		}
		check(data.shepherd.bond(parrot.getUuid()).isPresent() && parrot.isTamed(), "three songs win the parrot");

		// The cat: crouching beside it with fish for ten seconds
		CatEntity cat = beast(ctx, EntityType.CAT, at.add(1, 0, -1));
		data.addResource(100);
		sing(player, data, cat);
		player.setStackInHand(Hand.MAIN_HAND, new ItemStack(Items.COD, 2));
		player.setSneaking(true);
		for (int i = 0; i < 20; i++) {
			Bonds.tickOwner(player, data);
		}
		player.setSneaking(false);
		check(data.shepherd.bond(cat.getUuid()).isPresent() && player.getMainHandStack().getCount() == 1, "patience wins the cat, for one fish");

		// The polar bear: three monsters killed in front of it
		PolarBearEntity bear = beast(ctx, EntityType.POLAR_BEAR, at.add(-2, 0, 2));
		data.addResource(100);
		sing(player, data, bear);
		for (int i = 0; i < 5; i++) {
			ZombieEntity zombie = beast(ctx, EntityType.ZOMBIE, at.add(-3, 0, 3));
			Bonds.onHunt(player, data, zombie);
			zombie.discard();
		}
		check(data.shepherd.bond(bear.getUuid()).isPresent(), "strength wins the polar bear");

		// The fox only listens while she sleeps
		FoxEntity fox = beast(ctx, EntityType.FOX, at.add(3, 0, 3));
		data.addResource(100);
		check(Bonds.singTo(player, data, fox) == AbilityResult.HINTED, "an awake fox won't listen");
		for (MobEntity m : List.of(bee, goat, parrot, cat, bear, fox)) {
			m.discard();
		}
		done(ctx, player);
	}

	@GameTest(templateName = EMPTY_STRUCTURE, batchId = "care", tickLimit = 200)
	public void bondedBeastsArePettedFedAndToldToStay(TestContext ctx) {
		ServerWorld world = ctx.getWorld();
		BlockPos at = ctx.getAbsolutePos(new BlockPos(4, 2, 4));
		floor(world, at.add(-4, 0, -4), 9, 9);
		ServerPlayerEntity player = shepherd(ctx, at);
		RaceData data = RaceState.of(player);
		data.addResource(100);
		RabbitEntity rabbit = beast(ctx, EntityType.RABBIT, at.add(2, 0, 0));
		sing(player, data, rabbit);
		player.setStackInHand(Hand.MAIN_HAND, new ItemStack(Items.GOLDEN_CARROT, 2));
		Bonds.interact(player, data, rabbit, Hand.MAIN_HAND);
		data.shepherd.rituals.get(rabbit.getUuid()).lastFeed -= Bonds.FEED_GAP;
		Bonds.interact(player, data, rabbit, Hand.MAIN_HAND);
		ShepherdData.Bond bond = data.shepherd.bond(rabbit.getUuid()).orElseThrow();
		check(Bonds.singTo(player, data, rabbit) == AbilityResult.HINTED, "singing to your own beast only reminds how to care for it");

		// Petting: hearts always, the reward once in two minutes
		player.setStackInHand(Hand.MAIN_HAND, ItemStack.EMPTY);
		float voice = data.resource;
		int xp = bond.xp + bond.level * 1000;
		check(Bonds.interact(player, data, rabbit, Hand.MAIN_HAND) == ActionResult.SUCCESS, "petting");
		check(data.resource > voice && bond.xp + bond.level * 1000 > xp, "petting warms the voice and teaches the beast");
		voice = data.resource;
		Bonds.interact(player, data, rabbit, Hand.MAIN_HAND);
		check(data.resource == voice, "petting again right away gives nothing more");

		// Feeding: its treat heals it and keeps it fed; a fed, healthy beast refuses more
		rabbit.setHealth(1);
		player.setStackInHand(Hand.MAIN_HAND, new ItemStack(Items.CARROT, 3));
		check(Bonds.interact(player, data, rabbit, Hand.MAIN_HAND) == ActionResult.SUCCESS && rabbit.getHealth() > 1, "a carrot heals the rabbit");
		long now = world.getTime();
		check(bond.fedUntil > now && Bonds.fedBonus(data, bond, now) > 1, "the rabbit is fed and bites harder");
		rabbit.setHealth(rabbit.getMaxHealth());
		check(Bonds.interact(player, data, rabbit, Hand.MAIN_HAND) == ActionResult.FAIL && player.getMainHandStack().getCount() == 2,
				"a fed, healthy beast won't eat more");

		// Sneak + right click: it stays, and doesn't jump back to its Shepherd
		player.setStackInHand(Hand.MAIN_HAND, ItemStack.EMPTY);
		player.setSneaking(true);
		Bonds.interact(player, data, rabbit, Hand.MAIN_HAND);
		player.setSneaking(false);
		check(bond.staying && rabbit.getCommandTags().contains(Creatures.STAY_TAG), "told to stay");
		check(RaceData.fromNbt(data.toNbt()).shepherd.bonds.get(0).staying, "staying survives a save");

		// Not Today: the first killing blow is shrugged off, the next one sends it to rest
		data.talents.add(Talents.PACK_NOT_TODAY);
		rabbit.timeUntilRegen = 0;
		rabbit.damage(world.getDamageSources().generic(), 1000);
		check(rabbit.isAlive() && !bond.resting() && rabbit.getHealth() > 0, "Not Today keeps the rabbit in the fight");
		rabbit.timeUntilRegen = 0;
		rabbit.clearStatusEffects();
		rabbit.damage(world.getDamageSources().generic(), 1000);
		check(bond.resting(), "but only once in five minutes");
		bond.body = null;

		// Last Breath: a falling echo bursts into frost
		data.talents.add(Talents.LEGION_LAST_BREATH);
		EchoType zombieType = EchoType.of(EntityType.ZOMBIE).orElseThrow();
		learn(player, data, zombieType);
		MobEntity echo = Echoes.summon(player, data, zombieType, Vec3d.ofBottomCenter(at.add(-2, 0, 0)), false, 0, 0);
		ZombieEntity foe = beast(ctx, EntityType.ZOMBIE, at.add(-3, 0, 1));
		float before = foe.getHealth();
		echo.timeUntilRegen = 0;
		echo.damage(world.getDamageSources().generic(), 1000);
		check(foe.getHealth() < before && foe.hasStatusEffect(StatusEffects.SLOWNESS), "the frost hurts and slows the zombie nearby");
		foe.discard();
		Echoes.dismiss(world.getServer(), player.getUuid());
		done(ctx, player);
	}

	@GameTest(templateName = EMPTY_STRUCTURE, batchId = "echo_traits", tickLimit = 100)
	public void everyEchoHitsInItsOwnWay(TestContext ctx) {
		ServerWorld world = ctx.getWorld();
		BlockPos at = ctx.getAbsolutePos(new BlockPos(3, 2, 3));
		floor(world, at.add(-3, 0, -3), 7, 7);
		ServerPlayerEntity player = shepherd(ctx, at);
		RaceData data = RaceState.of(player);
		MobEntity vindicator = Echoes.summon(player, data, EchoType.of(EntityType.VINDICATOR).orElseThrow(), Vec3d.ofBottomCenter(at.add(2, 0, 0)),
				false, 0, 0);
		MobEntity stray = Echoes.summon(player, data, EchoType.of(EntityType.STRAY).orElseThrow(), Vec3d.ofBottomCenter(at.add(-2, 0, 0)),
				false, 0, 0);
		ZombieEntity zombie = beast(ctx, EntityType.ZOMBIE, at.add(0, 0, 2));
		check(Echoes.onHit(vindicator, zombie, 4) == 4, "a healthy foe takes the plain blow");
		zombie.setHealth(5);
		check(Echoes.onHit(vindicator, zombie, 4) == 8, "the Executioner doubles it on the nearly dead");
		Echoes.onHit(stray, zombie, 2);
		StatusEffectInstance slow = zombie.getStatusEffect(StatusEffects.SLOWNESS);
		check(slow != null && slow.getAmplifier() == 2, "a stray echo's arrows freeze: Slowness III");
		Echoes.dismiss(world.getServer(), player.getUuid());
		zombie.discard();
		done(ctx, player);
	}

	@GameTest(templateName = EMPTY_STRUCTURE, batchId = "squad", tickLimit = 100)
	public void theCallBringsTheWholeSquadAtOnce(TestContext ctx) {
		ServerWorld world = ctx.getWorld();
		BlockPos at = ctx.getAbsolutePos(new BlockPos(4, 2, 4));
		floor(world, at.add(-4, 0, -4), 9, 9);
		ServerPlayerEntity player = shepherd(ctx, at);
		RaceData data = RaceState.of(player);
		EchoType zombie = EchoType.of(EntityType.ZOMBIE).orElseThrow();
		EchoType silverfish = EchoType.of(EntityType.SILVERFISH).orElseThrow();
		learn(player, data, zombie);
		learn(player, data, silverfish);
		check(Shepherd.editPreset(data, 0, zombie.id(), 1) && Shepherd.editPreset(data, 0, silverfish.id(), 1), "the squad is edited");
		check(!Shepherd.editPreset(data, 0, EchoType.of(EntityType.RAVAGER).orElseThrow().id(), 1), "an unlearned echo can't join");
		data.addResource(100);
		check(Shepherd.call(player, data) == AbilityResult.OK, "the Call");
		check(Echoes.count(player.getUuid()) == 5, "two zombies and three silverfish at once, got " + Echoes.count(player.getUuid()));
		check(Shepherd.capacityUsed(data, player.getUuid()) == 3, "the three silverfish share one bond slot");
		check(Math.abs(data.resource - 60) < 0.01f, "2 x 15 + 10 voice, left " + data.resource);
		data.readyAt.clear();
		Shepherd.call(player, data);
		check(Echoes.count(player.getUuid()) == 5 && Math.abs(data.resource - 60) < 0.01f, "a full squad isn't called twice");
		Shepherd.selectPreset(player, data, 1);
		data.readyAt.clear();
		check(Shepherd.call(player, data) == AbilityResult.NO_ECHO, "squad II is empty");
		Echoes.dismiss(world.getServer(), player.getUuid());
		done(ctx, player);
	}

	@GameTest(templateName = EMPTY_STRUCTURE, batchId = "taming2", tickLimit = 100)
	public void theLlamaWalksAndTheRabbitTakesAGift(TestContext ctx) {
		ServerWorld world = ctx.getWorld();
		BlockPos at = ctx.getAbsolutePos(new BlockPos(4, 2, 4));
		floor(world, at.add(-4, 0, -4), 9, 9);
		ServerPlayerEntity player = shepherd(ctx, at);
		RaceData data = RaceState.of(player);
		RabbitEntity rabbit = beast(ctx, EntityType.RABBIT, at.add(2, 0, 0));
		data.addResource(100);
		sing(player, data, rabbit);
		player.setStackInHand(Hand.MAIN_HAND, new ItemStack(Items.GOLDEN_CARROT, 2));
		check(Bonds.interact(player, data, rabbit, Hand.MAIN_HAND) == ActionResult.SUCCESS, "the first golden carrot");
		data.shepherd.rituals.get(rabbit.getUuid()).lastFeed -= Bonds.FEED_GAP;
		check(Bonds.interact(player, data, rabbit, Hand.MAIN_HAND) == ActionResult.SUCCESS && data.shepherd.bond(rabbit.getUuid()).isPresent(),
				"the second wins the rabbit");
		LlamaEntity llama = beast(ctx, EntityType.LLAMA, at.add(-2, 0, 0));
		data.addResource(100);
		sing(player, data, llama);
		llama.attachLeash(player, true);
		for (int i = 0; i < 120; i++) {
			Bonds.tickOwner(player, data);
		}
		check(data.shepherd.bond(llama.getUuid()).isPresent(), "two minutes on the lead win the llama");

		// Beasts grow from what happens around them; levels and the leader make their skill stronger; names stick
		ShepherdData.Bond bond = data.shepherd.bond(rabbit.getUuid()).orElseThrow();
		check(Bonds.power(data, bond) == 0, "a fresh beast has power 0");
		for (int i = 0; i < 12; i++) {
			ZombieEntity zombie = beast(ctx, EntityType.ZOMBIE, at.add(1, 0, 1));
			Shepherd.onKill(player, zombie);
			zombie.discard();
		}
		check(bond.level >= 2, "a rabbit that never fights still learns from kills nearby: level " + bond.level);
		while (bond.level < 5) {
			Bonds.beastXp(player, data, bond, rabbit, 50);
		}
		check(Bonds.power(data, bond) == 1, "level 5 gives power 1");
		data.talents.add(Talents.PACK_ALPHA);
		Bonds.toggleLeader(data, data.shepherd.bonds.indexOf(bond));
		check(bond.leader && Bonds.power(data, bond) == 3, "the chosen leader with the Alpha crown: +2 power");
		Bonds.rename(player, data, data.shepherd.bonds.indexOf(bond), "Пушок");
		check("Пушок".equals(rabbit.getCustomName().getString()) && bond.name.equals("Пушок"), "the name is on the beast and in the bond");
		rabbit.discard();
		llama.discard();
		done(ctx, player);
	}

	@GameTest(templateName = EMPTY_STRUCTURE, batchId = "whistle", tickLimit = 100)
	public void theWhistleOrdersThePackAndTheRallyHealsIt(TestContext ctx) {
		ServerWorld world = ctx.getWorld();
		BlockPos at = ctx.getAbsolutePos(new BlockPos(4, 2, 4));
		floor(world, at.add(-4, 0, -4), 9, 9);
		ServerPlayerEntity player = shepherd(ctx, at);
		RaceData data = RaceState.of(player);
		EchoType zombieType = EchoType.of(EntityType.ZOMBIE).orElseThrow();
		learn(player, data, zombieType);
		MobEntity echo = Echoes.summon(player, data, zombieType, Vec3d.ofBottomCenter(at.add(-2, 0, 0)), false, 0, 0);
		ZombieEntity foe = beast(ctx, EntityType.ZOMBIE, at.add(3, 0, 3));

		// The Whistle: Guard -> Hunt -> Follow, for free
		check(data.shepherd.stance == ShepherdData.GUARD, "the pack starts on Guard");
		float guarded = Shepherd.modifyDamage(echo, world.getDamageSources().mobAttack(foe), 10);
		check(Math.abs(guarded - 8) < 0.01f, "on Guard a creature takes 20% less, got " + guarded);
		data.addResource(-data.resource);
		check(Shepherd.whistle(player, data) == AbilityResult.OK && data.shepherd.stance == ShepherdData.HUNT, "Hunt, and it costs no voice");
		Shepherd.tickSecond(player, data);
		check(echo.getTarget() == foe, "on Hunt the echo goes for the zombie four blocks away");
		data.readyAt.clear();
		Shepherd.whistle(player, data);
		check(data.shepherd.stance == ShepherdData.FOLLOW && echo.getTarget() == null, "Follow calls it back from a foe that hurts nobody");
		check(RaceData.fromNbt(data.toNbt()).shepherd.stance == ShepherdData.FOLLOW, "the order survives a save");

		// The Rally heals the pack a lot and the Shepherd a little
		echo.setHealth(2);
		player.setHealth(6);
		data.addResource(100);
		check(Shepherd.rally(player, data) == AbilityResult.OK, "the Rally");
		check(echo.getHealth() >= 2 + echo.getMaxHealth() * Shepherd.RALLY_HEAL - 0.01f, "the echo heals 30%, now " + echo.getHealth());
		check(Math.abs(player.getHealth() - 10) < 0.01f, "the Shepherd heals 2 hearts, now " + player.getHealth());
		foe.discard();

		// Sneak + G gathers the creatures to the Shepherd, on its own cooldown
		echo.refreshPositionAndAngles(at.getX() + 3.5, at.getY(), at.getZ() - 3.5, 0, 0);
		player.setSneaking(true);
		check(Shepherd.whistle(player, data) == AbilityResult.OK && echo.squaredDistanceTo(player) < 16, "the Gather brings the echo close");
		player.setSneaking(false);

		// Light Bonds: a wolf takes one slot instead of two
		RaceData light = data;
		check(Shepherd.beastCost(BeastKind.byId("wolf").orElseThrow(), light) == 2, "a wolf takes two slots");
		light.talents.add(Talents.PACK_LIGHT);
		check(Shepherd.beastCost(BeastKind.byId("wolf").orElseThrow(), light) == 1 && Shepherd.beastCost(BeastKind.byId("rabbit").orElseThrow(), light) == 1,
				"Light Bonds: the wolf takes one, the rabbit still one");
		Echoes.dismiss(world.getServer(), player.getUuid());
		done(ctx, player);
	}

	@GameTest(templateName = EMPTY_STRUCTURE, batchId = "shepherd_body", tickLimit = 100)
	public void shepherdsAreFrailButTheirCreaturesSpareThem(TestContext ctx) {
		ServerWorld world = ctx.getWorld();
		BlockPos at = ctx.getAbsolutePos(new BlockPos(2, 2, 2));
		ServerPlayerEntity player = shepherd(ctx, at);
		RaceData data = RaceState.of(player);
		Shepherd.tickSecond(player, data);
		check(Math.abs(player.getMaxHealth() - 16) < 0.01f, "a Shepherd has 2 hearts less, got " + player.getMaxHealth());
		ZombieEntity zombie = EntityType.ZOMBIE.create(world);
		zombie.refreshPositionAndAngles(at.getX() + 1.5, at.getY(), at.getZ() + 0.5, 0, 0);
		check(Math.abs(Shepherd.modifyDamage(zombie, world.getDamageSources().playerAttack(player), 4) - 3) < 0.01f, "its own blow is a quarter weaker");
		data.talents.add(Talents.VOICE_LIFE);
		data.talents.add(Talents.VOICE_STRENGTH);
		Shepherd.tickSecond(player, data);
		check(Math.abs(player.getMaxHealth() - 18) < 0.01f, "Sturdy Body gives one heart back, got " + player.getMaxHealth());
		check(Math.abs(Shepherd.modifyDamage(zombie, world.getDamageSources().playerAttack(player), 10) - 9) < 0.01f, "Steady Hand: a tenth weaker");
		zombie.discard();
		done(ctx, player);
	}

	static ServerPlayerEntity frozen(TestContext ctx, BlockPos at) {
		ServerPlayerEntity player = (ServerPlayerEntity) ctx.createMockCreativeServerPlayerInWorld();
		player.changeGameMode(GameMode.SURVIVAL);
		player.refreshPositionAndAngles(at.getX() + 0.5, at.getY(), at.getZ() + 0.5, 0, 0);
		RaceState.of(player).reset(Race.FROZEN);
		return player;
	}

	@GameTest(templateName = EMPTY_STRUCTURE, batchId = "rewind", tickLimit = 100)
	public void theRewindFliesBackAlongTheTrail(TestContext ctx) {
		ServerWorld world = ctx.getWorld();
		BlockPos at = ctx.getAbsolutePos(new BlockPos(1, 2, 1));
		floor(world, at.add(-1, 0, -1), 9, 3);
		ServerPlayerEntity player = frozen(ctx, at);
		RaceData data = RaceState.of(player);
		data.addResource(100);
		check(Frozen.rewind(player, data) == AbilityResult.HINTED, "no trail yet, no Rewind");
		Vec3d start = player.getPos();
		// Walk six blocks east over 30 ticks, the trail recording every tick
		for (int i = 0; i <= 30; i++) {
			player.refreshPositionAndAngles(start.x + i * 0.2, start.y, start.z, 0, 0);
			Frozen.record(player);
		}
		Vec3d left = player.getPos();
		check(Frozen.rewind(player, data) == AbilityResult.OK, "the Rewind");
		check(Math.abs(data.resource - 70) < 0.01f, "it costs 30 sand, left " + data.resource);
		check(Frozen.avoids(player, world.getDamageSources().generic()), "nothing touches a Frozen in flight");
		for (int i = 0; i < 12; i++) {
			Frozen.tick(world.getServer());
		}
		check(player.getPos().distanceTo(start) < 0.5, "back where it was 30 ticks ago, at " + player.getPos() + " from " + left);
		check(!Frozen.avoids(player, world.getDamageSources().generic()), "and solid again on landing");
		check(Frozen.rewind(player, data) == AbilityResult.COOLDOWN, "then it waits");
		done(ctx, player);
	}

	@GameTest(templateName = EMPTY_STRUCTURE, batchId = "frozen_dome", tickLimit = 100)
	public void theDomeHoldsEnemiesAndArrows(TestContext ctx) {
		ServerWorld world = ctx.getWorld();
		BlockPos at = ctx.getAbsolutePos(new BlockPos(4, 2, 4));
		floor(world, at.add(-4, 0, -4), 9, 9);
		ServerPlayerEntity player = frozen(ctx, at);
		RaceData data = RaceState.of(player);
		ZombieEntity zombie = beast(ctx, EntityType.ZOMBIE, at.add(2, 0, 0));
		ArrowEntity arrow = new ArrowEntity(world, at.getX() - 2.0, at.getY() + 1.5, at.getZ() + 0.5);
		arrow.setVelocity(1, 0, 0);
		arrow.setNoGravity(true);
		world.spawnEntity(arrow);
		data.addResource(100);
		check(Frozen.dome(player, data) == AbilityResult.OK, "the Dome");
		Frozen.tick(world.getServer());
		StatusEffectInstance slow = zombie.getStatusEffect(StatusEffects.SLOWNESS);
		check(slow != null && slow.getAmplifier() >= 3, "the zombie under the Dome all but stops");
		check(arrow.getVelocity().lengthSquared() < 1.0E-6, "the arrow hangs in the air");
		check(Frozen.dome(player, data) == AbilityResult.COOLDOWN, "one Dome at a time");
		zombie.discard();
		arrow.discard();
		done(ctx, player);
	}

	@GameTest(templateName = EMPTY_STRUCTURE, batchId = "quicken", tickLimit = 100)
	public void theQuickeningSpeedsWorkingBlocks(TestContext ctx) {
		ServerWorld world = ctx.getWorld();
		BlockPos at = ctx.getAbsolutePos(new BlockPos(1, 2, 1));
		floor(world, at.add(-1, 0, -1), 3, 3);
		world.setBlockState(at.down(), Blocks.FARMLAND.getDefaultState());
		world.setBlockState(at, Blocks.WHEAT.getDefaultState());
		world.setBlockState(at.east(), Blocks.FURNACE.getDefaultState());
		check(Frozen.quickenable(world, at) && Frozen.quickenable(world, at.east()), "crops and furnaces can be quickened");
		check(!Frozen.quickenable(world, at.add(-1, -1, 0)), "plain stone can't");
		ServerPlayerEntity player = frozen(ctx, at);
		RaceData data = RaceState.of(player);
		data.addResource(100);
		player.setPitch(90);
		check(Frozen.quicken(player, data) == AbilityResult.OK && Frozen.quickened(world, at), "looking down at the wheat quickens it");
		// A burning furnace, quickened: 25 ticks of the Quickening smelt as much as 250 plain ones (one item)
		if (world.getBlockEntity(at.east()) instanceof AbstractFurnaceBlockEntity furnace) {
			furnace.setStack(0, new ItemStack(Items.RAW_IRON, 8));
			furnace.setStack(1, new ItemStack(Items.COAL, 4));
			// Facing east and down at the furnace beside the wheat
			// (a living entity looks where its head turns, so the head too)
			player.setPitch(45);
			player.setYaw(-90);
			player.setHeadYaw(-90);
			data.readyAt.clear();
			AbilityResult quickened = Frozen.quicken(player, data);
			for (int i = 0; i < 25; i++) {
				Frozen.tick(world.getServer());
			}
			net.minecraft.util.hit.HitResult hit = player.raycast(6, 1.0f, false);
			check(furnace.getStack(2).getCount() >= 1, "the quickened furnace smelts: " + quickened + ", quickened " + Frozen.quickened(world, at.east())
					+ ", look " + hit.getType() + " at " + (hit instanceof net.minecraft.util.hit.BlockHitResult b ? b.getBlockPos().subtract(at) : null)
					+ ", furnace " + furnace.createNbt());
		}
		world.removeBlock(at.east(), false);
		done(ctx, player);
	}

	@GameTest(templateName = EMPTY_STRUCTURE, batchId = "frozen_body", tickLimit = 100)
	public void theFrozenHealSlowAndHoldOnToTheMoment(TestContext ctx) {
		ServerPlayerEntity player = frozen(ctx, ctx.getAbsolutePos(new BlockPos(1, 2, 1)));
		RaceData data = RaceState.of(player);
		check(Frozen.naturalHealing(player) == 0.5f, "natural healing at half speed");
		StatusEffectInstance speed = Frozen.shorten(player, new StatusEffectInstance(StatusEffects.SPEED, 600));
		check(speed.getDuration() == 420, "a potion lasts 30% shorter, got " + speed.getDuration());
		// Doubled Moment: every third blow lands half again as hard; Viscous Time slows
		data.talents.add(Talents.FLOW_ECHO);
		data.talents.add(Talents.FLOW_VISCOUS);
		ZombieEntity zombie = EntityType.ZOMBIE.create(ctx.getWorld());
		float[] blows = new float[3];
		for (int i = 0; i < 3; i++) {
			blows[i] = Frozen.onHit(zombie, player.getDamageSources().playerAttack(player), 4);
		}
		check(blows[0] == 4 && blows[1] == 4 && blows[2] == 6, "the third blow is doubled by half: " + blows[2]);
		check(zombie.hasStatusEffect(StatusEffects.SLOWNESS), "the zombie's time thickens");
		data.talents.add(Talents.LOOP_ETERNAL);
		player.setHealth(player.getMaxHealth());
		float first = Frozen.modifyDamage(player, player.getDamageSources().generic(), player.getMaxHealth() - 2);
		player.setHealth(player.getHealth() - first);
		check(Frozen.modifyDamage(player, player.getDamageSources().generic(), 100) <= player.getHealth() - 1,
				"the Eternal Moment began on the low blow: nothing kills now");
		done(ctx, player);
	}

	private static void check(boolean condition, String message) {
		if (!condition) {
			throw new GameTestException(message);
		}
	}
}
