package euf.mageslegacy.mycelid;

import euf.mageslegacy.MagesLegacy;
import euf.mageslegacy.fx.Fx;
import euf.mageslegacy.fx.ModParticles;
import euf.mageslegacy.net.RacePackets;
import euf.mageslegacy.race.AbilityResult;
import euf.mageslegacy.race.Progression;
import euf.mageslegacy.race.Race;
import euf.mageslegacy.race.RaceData;
import euf.mageslegacy.race.RaceState;
import euf.mageslegacy.race.Talents;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.attribute.EntityAttribute;
import net.minecraft.entity.attribute.EntityAttributeInstance;
import net.minecraft.entity.attribute.EntityAttributeModifier;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.effect.StatusEffect;
import net.minecraft.entity.effect.StatusEffectCategory;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.entity.mob.Monster;
import net.minecraft.item.FoodComponent;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.registry.Registries;
import net.minecraft.registry.tag.DamageTypeTags;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.GlobalPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.RaycastContext;

/**
 * Everything the Mycelids do: spores as their resource, night sight and regeneration in the dark, the mushroom diet,
 * the spore jump, the spore cloud and the mycelium network; and their weaknesses (sun, fire, meat).
 */
public final class Mycelid {
	public static final int JUMP_COST = 15;
	public static final int CHAIN_COST = 5;
	public static final int CLOUD_COST = 30;
	public static final int NODE_COST = 40;
	/** The longest flight of a spore jump, in ticks; see {@link #jumpDelay}. */
	public static final int JUMP_DELAY = 17;
	/** Glowing spore green, and dark moss laid on as real colour under it (see {@link Fx#INK}). */
	private static final int SPORE_RGB = 0x9CFF8A;
	private static final int MOSS_INK = Fx.INK | 0x4E7A3A;
	public static final int CLOUD_KILL_XP = 4;
	public static final int DARK_KILL_XP = 3;
	private static final int JUMP_COOLDOWN = 30;
	private static final int CLOUD_COOLDOWN = 200;
	private static final int CLOUD_COOLDOWN_QUICK = 140;
	public static final int JUMP_COST_CHEAP = 10;
	private static final int REBIRTH_SLOT = 9;
	private static final int FED_NETWORK_SLOT = 8;
	private static final int FED_NETWORK_COOLDOWN = 600;
	private static final int REBIRTH_COOLDOWN = 24000;
	private static final int MIND_RADIUS = 24;
	private static final int NODE_COOLDOWN = 100;
	private static final UUID CAP_ARMOR = UUID.fromString("6d3f8a52-2a7e-4f0e-9a51-3c1b6f0e7c11");
	private static final UUID HEARTY_HEALTH = UUID.fromString("0b8e4c1d-5f2a-4c7e-8d31-9a6f2e4b7c22");

	/** Mushroom -> the effect it gives as a taste. */
	public static final Map<Item, StatusEffect> DIET = Map.of(
			Items.RED_MUSHROOM, StatusEffects.STRENGTH,
			Items.BROWN_MUSHROOM, StatusEffects.RESISTANCE,
			Items.CRIMSON_FUNGUS, StatusEffects.FIRE_RESISTANCE,
			Items.WARPED_FUNGUS, StatusEffects.SPEED);

	private record PendingJump(UUID player, Vec3d target, long at, Vec3d from, int ticks) {}

	/** A spore cloud; one that follows sits on its owner while they are in the same world. */
	private record Cloud(ServerWorld world, Vec3d center, UUID owner, double radius, int potency, long start, long until, boolean follows) {}

	/** Greenish-grey and dark violet spore dust for the cloud. */

	private static final List<PendingJump> JUMPS = new ArrayList<>();
	private static final List<Cloud> CLOUDS = new ArrayList<>();

	private Mycelid() {
	}

	public static void reset() {
		JUMPS.clear();
		CLOUDS.clear();
		MycelidFx.reset();
	}

	// ---- passives, once a second ----

	public static boolean inDark(ServerPlayerEntity player) {
		return player.getWorld().getLightLevel(BlockPos.ofFloored(player.getEyePos())) <= 7;
	}

	public static boolean onMycelium(ServerPlayerEntity player) {
		BlockState below = player.getWorld().getBlockState(player.getBlockPos().down());
		return below.isOf(Blocks.MYCELIUM) || below.isOf(Blocks.PODZOL) || below.isOf(Blocks.RED_MUSHROOM_BLOCK)
				|| below.isOf(Blocks.BROWN_MUSHROOM_BLOCK) || below.isOf(Blocks.MUSHROOM_STEM) || below.isOf(MagesLegacy.MYCELIUM_NODE);
	}

	public static boolean inSun(ServerPlayerEntity player) {
		ServerWorld world = player.getServerWorld();
		return world.getDimension().hasSkyLight() && world.isDay() && !world.isRaining()
				&& world.isSkyVisible(BlockPos.ofFloored(player.getEyePos()));
	}

	public static void tickSecond(ServerPlayerEntity player, RaceData data) {
		boolean dark = inDark(player);
		boolean mycelium = onMycelium(player);
		boolean sun = inSun(player);
		data.addResource(0.5f + (dark ? 0.5f : 0) + (mycelium ? 1.0f : 0));

		if (dark) {
			refresh(player, StatusEffects.NIGHT_VISION, 0, 300, 220);
		}
		if (dark || mycelium) {
			refresh(player, StatusEffects.REGENERATION, data.has(Talents.FLESH_DARK_REGEN) ? 1 : 0, 100, 20);
		}
		if (sun && !data.has(Talents.FLESH_SUNPROOF)) {
			refresh(player, StatusEffects.WEAKNESS, 0, 60, 30);
			player.addExhaustion(0.1f);
		}
		modifier(player, EntityAttributes.GENERIC_ARMOR, CAP_ARMOR, "mycelid_cap", 4, data.has(Talents.FLESH_CAP) && !sun);
		modifier(player, EntityAttributes.GENERIC_MAX_HEALTH, HEARTY_HEALTH, "mycelid_hearty", 4, data.has(Talents.FLESH_HEARTY));
		if (data.has(Talents.NET_MIND)) {
			forestMind(player);
		}
	}

	/** Adds or removes a talent's attribute bonus so it matches whether the talent applies right now. */
	private static void modifier(ServerPlayerEntity player, EntityAttribute attribute, UUID id, String name, double amount, boolean want) {
		EntityAttributeInstance instance = player.getAttributeInstance(attribute);
		if (instance == null || want == (instance.getModifier(id) != null)) {
			return;
		}
		if (want) {
			instance.addTemporaryModifier(new EntityAttributeModifier(id, name, amount, EntityAttributeModifier.Operation.ADDITION));
		} else {
			instance.removeModifier(id);
			player.setHealth(Math.min(player.getHealth(), player.getMaxHealth()));
		}
	}

	/** Forest Mind: monsters near any of your nodes in this world glow, so you see them through walls. */
	private static void forestMind(ServerPlayerEntity player) {
		ServerWorld world = player.getServerWorld();
		for (GlobalPos node : RaceState.get(player.getServer()).nodes(player.getUuid())) {
			if (!node.getDimension().equals(world.getRegistryKey())) {
				continue;
			}
			for (LivingEntity mob : world.getEntitiesByClass(LivingEntity.class, new Box(node.getPos()).expand(MIND_RADIUS),
					e -> e instanceof Monster && e.isAlive())) {
				mob.addStatusEffect(new StatusEffectInstance(StatusEffects.GLOWING, 40, 0, true, false));
			}
		}
	}

	/** Gives the effect only when it is missing or nearly over, so timed effects (regeneration) keep their rhythm. */
	private static void refresh(ServerPlayerEntity player, StatusEffect effect, int amplifier, int duration, int below) {
		StatusEffectInstance current = player.getStatusEffect(effect);
		if (current == null || current.getAmplifier() < amplifier || current.getDuration() <= below) {
			player.addStatusEffect(new StatusEffectInstance(effect, duration, amplifier, true, false, true));
		}
	}

	/** Takes off the talents' attribute bonuses (the race was reset or changed). */
	public static void removeCapArmor(ServerPlayerEntity player) {
		modifier(player, EntityAttributes.GENERIC_ARMOR, CAP_ARMOR, "mycelid_cap", 4, false);
		modifier(player, EntityAttributes.GENERIC_MAX_HEALTH, HEARTY_HEALTH, "mycelid_hearty", 4, false);
	}

	// ---- diet ----

	/** Eats a raw mushroom or fungus from the hand: spores, a little food and its taste. */
	public static boolean eat(ServerPlayerEntity player, RaceData data, ItemStack stack) {
		StatusEffect effect = DIET.get(stack.getItem());
		if (effect == null || player.getItemCooldownManager().isCoolingDown(stack.getItem())) {
			return false;
		}
		player.getHungerManager().add(2, 0.3f);
		boolean feast = data.has(Talents.FLESH_FEAST);
		data.addResource(feast ? 15 : 10);
		if (feast) {
			player.heal(3);
		}
		taste(player, data, effect);
		Progression.addXp(player, data, 5);
		player.getItemCooldownManager().set(stack.getItem(), 10);
		if (!player.getAbilities().creativeMode) {
			stack.decrement(1);
		}
		player.getWorld().playSound(null, player.getBlockPos(), SoundEvents.ENTITY_GENERIC_EAT, SoundCategory.PLAYERS, 0.8f, 1.1f);
		RacePackets.sync(player, data);
		return true;
	}

	/** Adds a taste; with one taste allowed (two with the talent), the oldest one fades. */
	public static void taste(ServerPlayerEntity player, RaceData data, StatusEffect effect) {
		String id = Registries.STATUS_EFFECT.getId(effect).toString();
		data.tastes.remove(id);
		data.tastes.add(id);
		int max = data.has(Talents.FLESH_TWO_TASTES) ? 2 : 1;
		while (data.tastes.size() > max) {
			StatusEffect old = Registries.STATUS_EFFECT.get(new Identifier(data.tastes.remove(0)));
			if (old != null) {
				player.removeStatusEffect(old);
			}
		}
		boolean rich = data.has(Talents.FLESH_RICH);
		// Tastes work from the inside: no swirl of particles in front of the eyes, only the icon
		// Rich Tastes raise a level, but resistance from mushrooms stops at I: II would take 40% off every blow, always
		int level = rich && effect != StatusEffects.RESISTANCE ? 1 : 0;
		player.addStatusEffect(new StatusEffectInstance(effect, rich ? 2400 : 1200, level, false, false, true));
		data.dirty = true;
	}

	/** After eating food: stews feed the spores, meat feeds a Mycelid only half as well. */
	public static void afterEating(ServerPlayerEntity player, RaceData data, Item item, FoodComponent food) {
		if (item == Items.MUSHROOM_STEW || item == Items.SUSPICIOUS_STEW) {
			data.addResource(15);
			Progression.addXp(player, data, 5);
		} else if (food.isMeat() && !data.has(Talents.FLESH_MEAT)) {
			int lost = food.getHunger() / 2;
			player.getHungerManager().setFoodLevel(Math.max(0, player.getHungerManager().getFoodLevel() - lost));
		}
	}

	/** Fire burns half again as badly (less with Damp Flesh); talents soften falls and poison attackers. */
	public static float modifyDamage(ServerPlayerEntity player, DamageSource source, float amount) {
		RaceData data = RaceState.of(player);
		boolean has = data != null;
		if (source.isIn(DamageTypeTags.IS_FIRE)) {
			return amount * (has && data.has(Talents.FLESH_FIREPROOF) ? 1.15f : 1.5f);
		}
		if (source.isIn(DamageTypeTags.IS_FALL) && has && data.has(Talents.NET_LIGHT_STEP)) {
			return amount * 0.5f;
		}
		if (has && data.has(Talents.FLESH_SPORE_BLOOD) && source.getAttacker() instanceof LivingEntity attacker && attacker instanceof Monster) {
			attacker.addStatusEffect(new StatusEffectInstance(attacker.isUndead() ? StatusEffects.WITHER : StatusEffects.POISON, 60, 0));
		}
		return amount;
	}

	/**
	 * Rebirth: a killing blow instead sprouts the Mycelid again at its nearest node in this world, with half health.
	 * Once in 20 minutes. Returns false to cancel the death.
	 */
	public static boolean allowDeath(ServerPlayerEntity player) {
		RaceData data = RaceState.of(player);
		if (data == null || data.race != Race.MYCELID || !data.has(Talents.FLESH_REBIRTH) || !ready(player, data, REBIRTH_SLOT)) {
			return true;
		}
		ServerWorld world = player.getServerWorld();
		GlobalPos nearest = null;
		for (GlobalPos node : RaceState.get(player.getServer()).nodes(player.getUuid())) {
			if (node.getDimension().equals(world.getRegistryKey()) && world.getBlockState(node.getPos()).isOf(MagesLegacy.MYCELIUM_NODE)
					&& (nearest == null || node.getPos().getSquaredDistance(player.getPos()) < nearest.getPos().getSquaredDistance(player.getPos()))) {
				nearest = node;
			}
		}
		if (nearest == null) {
			return true;
		}
		Vec3d fell = player.getPos();
		Vec3d to = Vec3d.ofBottomCenter(nearest.getPos().up());
		player.setHealth(player.getMaxHealth() / 2);
		player.extinguish();
		player.fallDistance = 0;
		player.teleport(world, to.x, to.y, to.z, player.getYaw(), player.getPitch());
		MycelidFx.rebirth(world, fell, to);
		// The mycelium gives the body back: spores wind in, a column of light
		Fx.helix(world, player, 1.4f, 2.6f, 4, true, 26, SPORE_RGB);
		Fx.pillar(world, to, 0.7f, 5, 24, SPORE_RGB);
		Fx.threads(world, to, 2.4f, 1.5f, 14, 44, SPORE_RGB);
		Fx.nebula(world, to, null, 2.4f, 40, SPORE_RGB, 0xC8FFB0);
		Fx.burst(world, to.add(0, 1, 0), 3.0f, 9, MOSS_INK);
		Fx.screen(player, 30, SPORE_RGB);
		Fx.shake(player, 2.0f, 12);
		world.playSound(null, nearest.getPos(), SoundEvents.BLOCK_FUNGUS_PLACE, SoundCategory.PLAYERS, 1.2f, 0.5f);
		world.playSound(null, nearest.getPos(), SoundEvents.ITEM_TOTEM_USE, SoundCategory.PLAYERS, 0.5f, 1.4f);
		player.sendMessage(Text.translatable("mages_legacy.mycelid.reborn").formatted(net.minecraft.util.Formatting.GREEN), false);
		cooldown(player, data, REBIRTH_SLOT, REBIRTH_COOLDOWN);
		return false;
	}

	// ---- abilities ----

	public static AbilityResult use(ServerPlayerEntity player, RaceData data, int slot) {
		return switch (slot) {
			case 0 -> sporeJump(player, data);
			case 1 -> sporeCloud(player, data);
			case 2 -> placeNode(player, data);
			default -> AbilityResult.LOCKED;
		};
	}

	private static long now(ServerPlayerEntity player) {
		return player.getServer().getTicks();
	}

	private static boolean ready(ServerPlayerEntity player, RaceData data, int slot) {
		return data.readyAt.getOrDefault(slot, 0L) <= now(player);
	}

	private static void cooldown(ServerPlayerEntity player, RaceData data, int slot, int ticks) {
		data.readyAt.put(slot, now(player) + ticks);
		data.dirty = true;
	}

	public static int jumpCost(RaceData data) {
		return data.has(Talents.NET_CHEAP) ? JUMP_COST_CHEAP : JUMP_COST;
	}

	public static double jumpRange(RaceData data) {
		return data.has(Talents.NET_REACH) ? 22 : 16;
	}

	/** How long the spores fly to a spot this far: a far jump leaves the body standing longer. */
	public static int jumpDelay(double distance) {
		return Math.min(JUMP_DELAY, 6 + (int) Math.round(distance / 2));
	}

	public static AbilityResult sporeJump(ServerPlayerEntity player, RaceData data) {
		long now = now(player);
		boolean chained = data.chainLeft > 0 && now <= data.chainUntil;
		if (!chained && !ready(player, data, 0)) {
			return AbilityResult.COOLDOWN;
		}
		int cost = chained ? CHAIN_COST : jumpCost(data);
		if (data.resource < cost) {
			return AbilityResult.NO_RESOURCE;
		}
		if (JUMPS.stream().anyMatch(j -> j.player().equals(player.getUuid()))) {
			return AbilityResult.COOLDOWN;
		}
		// The chain's quick jumps are short hops, half the reach
		Vec3d target = landing(player, chained ? jumpRange(data) / 2 : jumpRange(data));
		if (target == null) {
			return AbilityResult.NO_SPACE;
		}
		data.addResource(-cost);
		if (chained) {
			data.chainLeft--;
		} else {
			data.chainLeft = data.has(Talents.NET_CHAIN) ? 2 : 0;
			cooldown(player, data, 0, JUMP_COOLDOWN);
		}
		data.chainUntil = now + 60;
		ServerWorld world = player.getServerWorld();
		// The body crumbles into spores, one flies ahead along an arc and the Mycelid sprouts where it lands
		MycelidFx.crumble(world, player.getPos());
		MycelidFx.landingMark(world, target, 0);
		Fx.burst(world, player.getPos().add(0, 1, 0), 1.3f, 6, MOSS_INK);
		// The body scatters into a puff of spores, and a comet of them flies ahead to where it will sprout
		Fx.nebula(world, player.getPos(), null, 1.2f, 18, SPORE_RGB, 0xC8FFB0);
		int delay = jumpDelay(target.distanceTo(player.getPos()));
		Fx.comet(world, player.getPos(), target, delay, SPORE_RGB);
		world.playSound(null, player.getBlockPos(), SoundEvents.BLOCK_FUNGUS_BREAK, SoundCategory.PLAYERS, 1.0f, 0.7f);
		JUMPS.add(new PendingJump(player.getUuid(), target, now + delay, player.getPos(), delay));
		data.dirty = true;
		return AbilityResult.OK;
	}

	/**
	 * Where the spore lands along the look: on top of the block it hits, or on whatever block is under the end of the
	 * reach, never in the air; null if there is nothing to take root on.
	 */
	public static Vec3d landing(ServerPlayerEntity player, double range) {
		ServerWorld world = player.getServerWorld();
		Vec3d eye = player.getEyePos();
		Vec3d look = player.getRotationVec(1.0f);
		BlockHitResult hit = world.raycast(new RaycastContext(eye, eye.add(look.multiply(range)),
				RaycastContext.ShapeType.COLLIDER, RaycastContext.FluidHandling.NONE, player));
		Vec3d end = hit.getType() == HitResult.Type.MISS ? eye.add(look.multiply(range))
				: hit.getPos().subtract(look.multiply(0.4));
		// The spore marks where the feet go; walk back towards the player until the body fits, then settle on ground
		for (double back = 0; back <= range; back += 0.5) {
			Vec3d feet = end.subtract(look.multiply(back));
			if (hit.getType() == HitResult.Type.BLOCK && hit.getSide() == Direction.UP && back == 0) {
				feet = Vec3d.ofBottomCenter(hit.getBlockPos().up());
			}
			if (feet.isInRange(player.getPos(), 1.5)) {
				break;
			}
			Vec3d grounded = drop(player, feet);
			if (grounded != null) {
				return grounded;
			}
		}
		return null;
	}

	/** Lets the body settle on a block up to 4 blocks below; null if it is stuck on the way down or there is nothing to land on. */
	private static Vec3d drop(ServerPlayerEntity player, Vec3d feet) {
		ServerWorld world = player.getServerWorld();
		for (double down = 0; down <= 4; down += 0.25) {
			Box box = player.getDimensions(player.getPose()).getBoxAt(feet.subtract(0, down, 0));
			if (!world.isSpaceEmpty(player, box)) {
				return null;
			}
			if (!world.isSpaceEmpty(player, box.offset(0, -0.3, 0))) {
				return feet.subtract(0, down, 0);
			}
		}
		// Nothing below within 4 blocks: a spore can't take root in the air
		return null;
	}

	public static AbilityResult sporeCloud(ServerPlayerEntity player, RaceData data) {
		if (!ready(player, data, 1)) {
			return AbilityResult.COOLDOWN;
		}
		if (data.resource < CLOUD_COST) {
			return AbilityResult.NO_RESOURCE;
		}
		data.addResource(-CLOUD_COST);
		cooldown(player, data, 1, data.has(Talents.SWARM_QUICK) ? CLOUD_COOLDOWN_QUICK : CLOUD_COOLDOWN);
		double radius = data.has(Talents.SWARM_WIDE) ? 6 : 4;
		spawnCloud(player.getServerWorld(), player.getPos(), player.getUuid(), radius,
				data.has(Talents.SWARM_POTENT) ? 2 : 1, data.has(Talents.SWARM_LINGERING) ? 240 : 120, data.has(Talents.SWARM_FOLLOW));
		MycelidFx.cloudBurst(player.getServerWorld(), player.getPos(), radius);
		// The cloud itself: a glowing nebula of spores for as long as it lasts, and a puff as it bursts out
		ServerWorld here = player.getServerWorld();
		int lasts = data.has(Talents.SWARM_LINGERING) ? 240 : 120;
		Fx.nebula(here, player.getPos(), data.has(Talents.SWARM_FOLLOW) ? player : null, (float) radius, lasts, SPORE_RGB, 0xC8FFB0);
		Fx.burst(here, player.getPos().add(0, 1, 0), (float) radius, 9, MOSS_INK);
		Fx.shake(player, 0.8f, 8);
		if (data.has(Talents.SWARM_BURST)) {
			burst(player, radius);
		}
		player.getWorld().playSound(null, player.getBlockPos(), SoundEvents.BLOCK_SPORE_BLOSSOM_BREAK, SoundCategory.PLAYERS, 1.2f, 0.6f);
		Progression.addXp(player, data, 3);
		return AbilityResult.OK;
	}

	/** The spore jump's arc, start to landing, for its trail of spores. */
	private static List<Vec3d> arcPath(Vec3d from, Vec3d to) {
		List<Vec3d> path = new ArrayList<>();
		double apex = 1.5 + from.distanceTo(to) * 0.15;
		for (int i = 0; i <= 16; i++) {
			double t = i / 16.0;
			path.add(from.lerp(to, t).add(0, 4 * apex * t * (1 - t) - 0.6, 0));
		}
		return path;
	}

	public static void spawnCloud(ServerWorld world, Vec3d center, UUID owner, double radius, int potency, int ticks) {
		spawnCloud(world, center, owner, radius, potency, ticks, false);
	}

	public static void spawnCloud(ServerWorld world, Vec3d center, UUID owner, double radius, int potency, int ticks, boolean follows) {
		long now = world.getServer().getTicks();
		CLOUDS.add(new Cloud(world, center, owner, radius, potency, now, now + ticks, follows));
	}

	/** Spore Burst: the cloud bursts out as it forms, hurting monsters in it and throwing them back. */
	private static void burst(ServerPlayerEntity player, double radius) {
		ServerWorld world = player.getServerWorld();
		for (LivingEntity mob : world.getEntitiesByClass(LivingEntity.class, player.getBoundingBox().expand(radius, 2, radius),
				e -> e instanceof Monster && e.isAlive() && e.getPos().isInRange(player.getPos(), radius + 0.5))) {
			mob.damage(world.getDamageSources().playerAttack(player), 4);
			Vec3d away = mob.getPos().subtract(player.getPos());
			mob.takeKnockback(1.0, -away.x, -away.z);
		}
		MycelidFx.blast(world, player.getPos(), radius);
		Fx.burst(world, player.getPos().add(0, 1, 0), (float) radius + 1, 8, SPORE_RGB);
		Fx.ring(world, player.getPos(), 0.5f, (float) radius + 2, 10, MOSS_INK);
		Fx.shake(player, 2.0f, 8);
		world.playSound(null, player.getBlockPos(), SoundEvents.ENTITY_GENERIC_EXPLODE, SoundCategory.PLAYERS, 0.5f, 1.6f);
	}

	/** Where a cloud is now: on its owner if it follows them, where it formed otherwise. */
	private static Vec3d centerOf(Cloud cloud) {
		if (cloud.follows()) {
			ServerPlayerEntity owner = cloud.world().getServer().getPlayerManager().getPlayer(cloud.owner());
			if (owner != null && owner.isAlive() && owner.getServerWorld() == cloud.world()) {
				return owner.getPos();
			}
		}
		return cloud.center();
	}

	public static int nodeLimit(RaceData data) {
		return data.has(Talents.NET_NODES) ? 5 : 3;
	}

	public static int nodeRange(RaceData data) {
		return data.has(Talents.NET_FAR) ? 500 : 150;
	}

	public static AbilityResult placeNode(ServerPlayerEntity player, RaceData data) {
		if (!Progression.tierOpen(player, 2)) {
			return AbilityResult.LOCKED;
		}
		if (!ready(player, data, 2)) {
			return AbilityResult.COOLDOWN;
		}
		if (data.resource < NODE_COST) {
			return AbilityResult.NO_RESOURCE;
		}
		ServerWorld world = player.getServerWorld();
		Vec3d eye = player.getEyePos();
		BlockHitResult hit = world.raycast(new RaycastContext(eye, eye.add(player.getRotationVec(1.0f).multiply(5)),
				RaycastContext.ShapeType.OUTLINE, RaycastContext.FluidHandling.NONE, player));
		if (hit.getType() != HitResult.Type.BLOCK) {
			return AbilityResult.NO_TARGET;
		}
		BlockPos pos = hit.getBlockPos().up();
		if (!world.getBlockState(pos).isReplaceable() || !world.getBlockState(pos.down()).isSideSolidFullSquare(world, pos.down(), Direction.UP)) {
			return AbilityResult.NO_SPACE;
		}
		placeNodeAt(world, pos, player.getUuid(), nodeLimit(data));
		data.addResource(-NODE_COST);
		cooldown(player, data, 2, NODE_COOLDOWN);
		Progression.addXp(player, data, 5);
		return AbilityResult.OK;
	}

	/** Grows a node and adds it to the owner's network; past the limit the oldest node withers away. */
	public static void placeNodeAt(ServerWorld world, BlockPos pos, UUID owner, int limit) {
		RaceState state = RaceState.get(world.getServer());
		List<GlobalPos> nodes = state.nodes(owner);
		world.setBlockState(pos, MagesLegacy.MYCELIUM_NODE.getDefaultState());
		nodes.add(GlobalPos.create(world.getRegistryKey(), pos));
		while (nodes.size() > limit) {
			GlobalPos old = nodes.remove(0);
			ServerWorld oldWorld = world.getServer().getWorld(old.getDimension());
			if (oldWorld != null && oldWorld.getBlockState(old.getPos()).isOf(MagesLegacy.MYCELIUM_NODE)) {
				oldWorld.removeBlock(old.getPos(), false);
			}
		}
		state.markDirty();
		MycelidFx.nodeGrow(world, Vec3d.ofBottomCenter(pos));
		Fx.pillar(world, Vec3d.ofBottomCenter(pos), 0.4f, 2.5f, 16, SPORE_RGB);
		Fx.threads(world, Vec3d.ofBottomCenter(pos), 0.9f, 1.8f, 7, 30, SPORE_RGB);
		world.playSound(null, pos, SoundEvents.BLOCK_FUNGUS_PLACE, SoundCategory.BLOCKS, 1.0f, 0.8f);
		world.playSound(null, pos, SoundEvents.BLOCK_ROOTS_PLACE, SoundCategory.BLOCKS, 1.0f, 0.6f);
	}

	/**
	 * Travel through a node: to the next node of the same network (in the order they grew) within the owner's range.
	 * Anyone may travel, so a Mycelid's network serves friends too.
	 */
	public static boolean travel(ServerPlayerEntity player, BlockPos from) {
		ServerWorld world = player.getServerWorld();
		RaceState state = RaceState.get(world.getServer());
		UUID owner = state.nodeOwner(world.getRegistryKey(), from).orElse(null);
		if (owner == null) {
			return false;
		}
		List<GlobalPos> nodes = state.nodes(owner);
		int range = nodeRange(state.data(owner));
		int start = nodes.indexOf(GlobalPos.create(world.getRegistryKey(), from));
		for (int i = 1; i < nodes.size(); i++) {
			GlobalPos next = nodes.get((start + i) % nodes.size());
			if (next.getDimension().equals(world.getRegistryKey()) && next.getPos().isWithinDistance(from, range)
					&& world.getBlockState(next.getPos()).isOf(MagesLegacy.MYCELIUM_NODE)) {
				Vec3d to = Vec3d.ofBottomCenter(next.getPos().up());
				MycelidFx.column(world, player.getPos());
				// Carried through the mycelium: a streak from node to node, a burst where the body comes up
				Fx.ribbon(world, List.of(player.getPos(), to), 16, MOSS_INK, SPORE_RGB, 0.8f);
				Fx.burst(world, to.add(0, 1, 0), 1.5f, 7, SPORE_RGB);
				Fx.fov(player, 0.15f, 10);
				player.teleport(world, to.x, to.y, to.z, player.getYaw(), player.getPitch());
				player.fallDistance = 0;
				MycelidFx.column(world, to);
				MycelidFx.ring(world, to, 0.3, 1.8, 6, MycelidFx.SPORE);
				world.spawnParticles(ModParticles.SPORE, to.x, to.y + 0.5, to.z, 40, 0.5, 1.0, 0.5, 0.02);
				world.playSound(null, next.getPos(), SoundEvents.BLOCK_FUNGUS_PLACE, SoundCategory.BLOCKS, 1.0f, 0.6f);
				RaceData travellerData = RaceState.of(player);
				// No race experience for travelling: walking back and forth between two nodes would farm it. For the
				// same reason the Fed Network bonus comes at most once in 30 seconds.
				if (travellerData != null && travellerData.race == Race.MYCELID) {
					if (travellerData.has(Talents.NET_HASTE)) {
						player.addStatusEffect(new StatusEffectInstance(StatusEffects.SPEED, 200, 1, false, false, true));
					}
					if (travellerData.has(Talents.NET_SHARED) && ready(player, travellerData, FED_NETWORK_SLOT)) {
						player.heal(4);
						travellerData.addResource(10);
						cooldown(player, travellerData, FED_NETWORK_SLOT, FED_NETWORK_COOLDOWN);
					}
				}
				return true;
			}
		}
		player.sendMessage(Text.translatable("mages_legacy.mycelid.node_alone"), true);
		return true;
	}

	// ---- every tick ----

	public static void tick(MinecraftServer server) {
		long now = server.getTicks();
		MycelidFx.tick(now);
		for (Iterator<PendingJump> it = JUMPS.iterator(); it.hasNext(); ) {
			PendingJump jump = it.next();
			if (jump.at() > now) {
				ServerPlayerEntity flying = server.getPlayerManager().getPlayer(jump.player());
				if (flying != null) {
					double progress = 1 - (jump.at() - now) / (double) jump.ticks();
					MycelidFx.arc(flying.getServerWorld(), jump.from(), jump.target(), progress);
					MycelidFx.landingMark(flying.getServerWorld(), jump.target(), progress);
				}
				continue;
			}
			it.remove();
			ServerPlayerEntity player = server.getPlayerManager().getPlayer(jump.player());
			if (player == null || !player.isAlive()) {
				continue;
			}
			RaceData data = RaceState.of(player);
			ServerWorld world = player.getServerWorld();
			if (data != null && data.has(Talents.SWARM_TRAIL)) {
				spawnCloud(world, jump.from(), player.getUuid(), 2.5, 0, 60);
			}
			player.teleport(world, jump.target().x, jump.target().y, jump.target().z, player.getYaw(), player.getPitch());
			player.fallDistance = 0;
			MycelidFx.sprout(world, jump.target());
			// The body sprouting out of the ground in a burst
			Fx.burst(world, jump.target().add(0, 1, 0), 1.8f, 7, SPORE_RGB);
			// Threads of mycelium tear up out of the ground and knit the body together; the spores thrown out burst over
			// whatever stands close
			Fx.threads(world, jump.target(), 1.9f, 1.3f, 11, 24, SPORE_RGB);
			for (LivingEntity mob : world.getEntitiesByClass(LivingEntity.class, player.getBoundingBox().expand(3), e -> e instanceof Monster && e.isAlive())) {
				mob.damage(world.getDamageSources().indirectMagic(player, player), 4);
				Vec3d away = mob.getPos().subtract(jump.target());
				mob.takeKnockback(0.8, -away.x, -away.z);
				mob.addStatusEffect(new StatusEffectInstance(mob.isUndead() ? StatusEffects.WITHER : StatusEffects.POISON, 60, 0));
			}
			Fx.helix(world, player, 1.0f, 2.2f, 3, true, 14, SPORE_RGB);
			Fx.ring(world, jump.target(), 0.3f, 3.0f, 12, MOSS_INK);
			Fx.fov(player, 0.12f, 10);
			Fx.shake(player, 1.0f, 6);
			// The arc of the jump stays a moment as a trail of spores drifting down
			for (Vec3d p : arcPath(jump.from(), jump.target())) {
				world.spawnParticles(ModParticles.SPORE, p.x, p.y + 1, p.z, 2, 0.12, 0.12, 0.12, 0.01);
			}
			world.playSound(null, player.getBlockPos(), SoundEvents.BLOCK_FUNGUS_PLACE, SoundCategory.PLAYERS, 1.0f, 1.2f);
			world.playSound(null, player.getBlockPos(), SoundEvents.BLOCK_MOSS_PLACE, SoundCategory.PLAYERS, 0.8f, 0.8f);
			if (data != null) {
				if (data.has(Talents.NET_ROOTS)) {
					for (LivingEntity mob : world.getEntitiesByClass(LivingEntity.class, player.getBoundingBox().expand(3),
							e -> e instanceof Monster)) {
						mob.addStatusEffect(new StatusEffectInstance(StatusEffects.SLOWNESS, 40, 4));
					}
				}
				if (data.has(Talents.NET_CLEANSE)) {
					for (StatusEffectInstance effect : new ArrayList<>(player.getStatusEffects())) {
						if (effect.getEffectType().getCategory() == StatusEffectCategory.HARMFUL) {
							player.removeStatusEffect(effect.getEffectType());
						}
					}
				}
				Progression.addXp(player, data, 2);
			}
		}

		for (Iterator<Cloud> it = CLOUDS.iterator(); it.hasNext(); ) {
			Cloud cloud = it.next();
			if (cloud.until() <= now) {
				it.remove();
				continue;
			}
			ServerWorld world = cloud.world();
			Vec3d c = centerOf(cloud);
			if (now % 3 == 0) {
				double r = cloud.radius();
				// Glowing spores drift up through the cloud (they live a few seconds, so a few at a time are plenty)
				world.spawnParticles(ModParticles.SPORE, c.x, c.y + 0.5, c.z, (int) (r * r * 0.4), r * 0.5, 0.5, r * 0.5, 0.0);
				world.spawnParticles(ParticleTypes.SPORE_BLOSSOM_AIR, c.x, c.y + 1.2, c.z, (int) (r * 4), r * 0.5, 0.6, r * 0.5, 0.0);
				MycelidFx.vortex(world, c, r, now - cloud.start());
				// The edge of the cloud, so it's clear where it ends
				for (int i = 0; i < 12; i++) {
					double a = (now * 0.05) + i * Math.PI / 6;
					world.spawnParticles(ParticleTypes.MYCELIUM, c.x + Math.cos(a) * r, c.y + 0.2, c.z + Math.sin(a) * r, 1, 0.05, 0.1, 0.05, 0.0);
				}
			}
			// Bites at once, then every second
			if ((now - cloud.start()) % 20 == 0) {
				Box area = new Box(c, c).expand(cloud.radius(), 2, cloud.radius());
				for (LivingEntity entity : world.getEntitiesByClass(LivingEntity.class, area,
						e -> e.isAlive() && e.getPos().isInRange(c, cloud.radius() + 0.5))) {
					applyCloud(entity, cloud, owner(cloud));
				}
			}
		}
	}

	/** The owner's race data, if they are online. */
	private static RaceData owner(Cloud cloud) {
		ServerPlayerEntity owner = cloud.world().getServer().getPlayerManager().getPlayer(cloud.owner());
		return owner == null ? null : RaceState.of(owner);
	}

	/**
	 * Living monsters choke on the spores, the undead (immune to poison) rot; players breathe them in and heal. The
	 * owner's talents add weakness to monsters and stronger healing to players.
	 */
	private static void applyCloud(LivingEntity entity, Cloud cloud, RaceData owner) {
		if (entity instanceof Monster) {
			entity.addStatusEffect(new StatusEffectInstance(StatusEffects.BLINDNESS, 60, 0));
			entity.addStatusEffect(new StatusEffectInstance(entity.isUndead() ? StatusEffects.WITHER : StatusEffects.POISON, 60, cloud.potency()));
			if (cloud.potency() > 0) {
				entity.addStatusEffect(new StatusEffectInstance(StatusEffects.SLOWNESS, 60, 1));
			}
			if (owner != null && owner.has(Talents.SWARM_WEAKNESS)) {
				entity.addStatusEffect(new StatusEffectInstance(StatusEffects.WEAKNESS, 60, 1));
			}
		} else if (entity instanceof ServerPlayerEntity) {
			int level = owner != null && owner.has(Talents.SWARM_MENDING) ? 1 : 0;
			entity.addStatusEffect(new StatusEffectInstance(StatusEffects.REGENERATION, 60, level, true, false, true));
		}
	}

	/**
	 * A monster died: if it was inside a Mycelid's cloud, the owner gains race experience; with Infection also
	 * spores, and with Bloom the body bursts into a small cloud of its own.
	 */
	public static void onDeath(LivingEntity entity) {
		if (!(entity instanceof Monster) || entity.getServer() == null) {
			return;
		}
		for (Cloud cloud : CLOUDS) {
			if (cloud.world() == entity.getWorld() && entity.getPos().isInRange(centerOf(cloud), cloud.radius() + 0.5)) {
				ServerPlayerEntity owner = entity.getServer().getPlayerManager().getPlayer(cloud.owner());
				RaceData data = owner == null ? null : RaceState.of(owner);
				if (data == null || data.race != Race.MYCELID) {
					return;
				}
				Progression.addXp(owner, data, CLOUD_KILL_XP);
				if (data.has(Talents.SWARM_INFECTION)) {
					data.addResource(10);
					Progression.addXp(owner, data, 6);
				}
				if (data.has(Talents.SWARM_BLOOM)) {
					spawnCloud(cloud.world(), entity.getPos(), cloud.owner(), 3, cloud.potency(), 60);
				}
				return;
			}
		}
	}

	/** A Mycelid killed a monster with its own hands in the dark: the hunt the race was made for. */
	public static void onKill(ServerPlayerEntity player, LivingEntity victim) {
		RaceData data = RaceState.of(player);
		if (data != null && data.race == Race.MYCELID && victim instanceof Monster && inDark(player)) {
			Progression.addXp(player, data, DARK_KILL_XP);
		}
	}

	public static int activeClouds() {
		return CLOUDS.size();
	}
}
