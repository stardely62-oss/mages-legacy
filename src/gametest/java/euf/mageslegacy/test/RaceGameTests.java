package euf.mageslegacy.test;

import euf.mageslegacy.MagesLegacy;
import euf.mageslegacy.mycelid.Mycelid;
import euf.mageslegacy.net.RacePackets;
import euf.mageslegacy.race.Progression;
import euf.mageslegacy.race.Progression.UnlockResult;
import euf.mageslegacy.race.Race;
import euf.mageslegacy.race.RaceData;
import euf.mageslegacy.race.RaceState;
import euf.mageslegacy.race.Talent;
import euf.mageslegacy.race.Talents;
import java.util.List;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.advancement.Advancement;
import net.minecraft.block.Blocks;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.entity.mob.ZombieEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.test.GameTest;
import net.minecraft.test.GameTestException;
import net.minecraft.test.TestContext;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
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
		check(!RacePackets.choose(player, Race.SHEPHERD), "a sleeping race can't be picked yet");
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
		List<Talent> all = Talents.of(Race.MYCELID);
		check(all.size() == 33, "33 Mycelid talents, got " + all.size());
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
		ctx.complete();
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
		check(Mycelid.sporeCloud(player, data) == Mycelid.AbilityResult.OK, "the cloud should rise");
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
		check(Mycelid.sporeJump(player, data) == Mycelid.AbilityResult.OK, "the jump should start");
		check((int) data.resource == 25, "a jump costs 15 spores");
		check(Mycelid.sporeJump(player, data) == Mycelid.AbilityResult.COOLDOWN, "no second jump right away");
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
			check(Mycelid.sporeJump(player, data) == Mycelid.AbilityResult.OK, "the level jump should start");
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
		check(Mycelid.sporeCloud(player, data) == Mycelid.AbilityResult.OK, "the cloud should rise");
		check(data.resource < 1, "a cloud costs 30 spores");
		check(Mycelid.sporeCloud(player, data) == Mycelid.AbilityResult.COOLDOWN, "one cloud at a time");
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

	private static void check(boolean condition, String message) {
		if (!condition) {
			throw new GameTestException(message);
		}
	}
}
