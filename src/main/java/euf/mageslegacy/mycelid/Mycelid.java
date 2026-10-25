package euf.mageslegacy.mycelid;

import euf.mageslegacy.MagesLegacy;
import euf.mageslegacy.net.RacePackets;
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
import net.minecraft.entity.attribute.EntityAttributeInstance;
import net.minecraft.entity.attribute.EntityAttributeModifier;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.effect.StatusEffect;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.entity.mob.Monster;
import net.minecraft.item.FoodComponent;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.particle.DustParticleEffect;
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
import org.joml.Vector3f;

/**
 * Everything the Mycelids do: spores as their resource, night sight and regeneration in the dark, the mushroom diet,
 * the spore jump, the spore cloud and the mycelium network; and their weaknesses (sun, fire, meat).
 */
public final class Mycelid {
	public static final int JUMP_COST = 15;
	public static final int CHAIN_COST = 5;
	public static final int CLOUD_COST = 30;
	public static final int NODE_COST = 40;
	public static final int JUMP_DELAY = 8;
	private static final int JUMP_COOLDOWN = 30;
	private static final int CLOUD_COOLDOWN = 200;
	private static final int NODE_COOLDOWN = 100;
	private static final UUID CAP_ARMOR = UUID.fromString("6d3f8a52-2a7e-4f0e-9a51-3c1b6f0e7c11");

	/** Mushroom -> the effect it gives as a taste. */
	public static final Map<Item, StatusEffect> DIET = Map.of(
			Items.RED_MUSHROOM, StatusEffects.STRENGTH,
			Items.BROWN_MUSHROOM, StatusEffects.RESISTANCE,
			Items.CRIMSON_FUNGUS, StatusEffects.FIRE_RESISTANCE,
			Items.WARPED_FUNGUS, StatusEffects.SPEED);

	private record PendingJump(UUID player, Vec3d target, long at, Vec3d from) {}

	private record Cloud(ServerWorld world, Vec3d center, UUID owner, double radius, int potency, long until) {}

	/** Greenish-grey and dark violet spore dust for the cloud. */
	private static final DustParticleEffect SPORE_DUST = new DustParticleEffect(new Vector3f(0.62f, 0.72f, 0.48f), 1.6f);
	private static final DustParticleEffect SPORE_DUST_DARK = new DustParticleEffect(new Vector3f(0.42f, 0.28f, 0.40f), 2.0f);

	private static final List<PendingJump> JUMPS = new ArrayList<>();
	private static final List<Cloud> CLOUDS = new ArrayList<>();

	private Mycelid() {
	}

	public static void reset() {
		JUMPS.clear();
		CLOUDS.clear();
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
		EntityAttributeInstance armor = player.getAttributeInstance(EntityAttributes.GENERIC_ARMOR);
		boolean wantCap = data.has(Talents.FLESH_CAP) && !sun;
		if (armor != null && wantCap != (armor.getModifier(CAP_ARMOR) != null)) {
			if (wantCap) {
				armor.addTemporaryModifier(new EntityAttributeModifier(CAP_ARMOR, "mycelid_cap", 4, EntityAttributeModifier.Operation.ADDITION));
			} else {
				armor.removeModifier(CAP_ARMOR);
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

	public static void removeCapArmor(ServerPlayerEntity player) {
		EntityAttributeInstance armor = player.getAttributeInstance(EntityAttributes.GENERIC_ARMOR);
		if (armor != null) {
			armor.removeModifier(CAP_ARMOR);
		}
	}

	// ---- diet ----

	/** Eats a raw mushroom or fungus from the hand: spores, a little food and its taste. */
	public static boolean eat(ServerPlayerEntity player, RaceData data, ItemStack stack) {
		StatusEffect effect = DIET.get(stack.getItem());
		if (effect == null || player.getItemCooldownManager().isCoolingDown(stack.getItem())) {
			return false;
		}
		player.getHungerManager().add(2, 0.3f);
		data.addResource(10);
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
		player.addStatusEffect(new StatusEffectInstance(effect, rich ? 2400 : 1200, rich ? 1 : 0, false, false, true));
		data.dirty = true;
	}

	/** After eating food: stews feed the spores, meat feeds a Mycelid only half as well. */
	public static void afterEating(ServerPlayerEntity player, RaceData data, Item item, FoodComponent food) {
		if (item == Items.MUSHROOM_STEW || item == Items.SUSPICIOUS_STEW) {
			data.addResource(15);
			Progression.addXp(player, data, 5);
		} else if (food.isMeat()) {
			int lost = food.getHunger() / 2;
			player.getHungerManager().setFoodLevel(Math.max(0, player.getHungerManager().getFoodLevel() - lost));
		}
	}

	public static float modifyDamage(ServerPlayerEntity player, DamageSource source, float amount) {
		return source.isIn(DamageTypeTags.IS_FIRE) ? amount * 1.5f : amount;
	}

	// ---- abilities ----

	public enum AbilityResult { OK, COOLDOWN, NO_RESOURCE, NO_TARGET, LOCKED, NO_SPACE }

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

	public static double jumpRange(RaceData data) {
		return data.has(Talents.NET_REACH) ? 12 : 8;
	}

	public static AbilityResult sporeJump(ServerPlayerEntity player, RaceData data) {
		long now = now(player);
		boolean chained = data.chainLeft > 0 && now <= data.chainUntil;
		if (!chained && !ready(player, data, 0)) {
			return AbilityResult.COOLDOWN;
		}
		int cost = chained ? CHAIN_COST : JUMP_COST;
		if (data.resource < cost) {
			return AbilityResult.NO_RESOURCE;
		}
		if (JUMPS.stream().anyMatch(j -> j.player().equals(player.getUuid()))) {
			return AbilityResult.COOLDOWN;
		}
		Vec3d target = landing(player, jumpRange(data));
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
		// The spore flies ahead and the Mycelid sprouts where it landed a moment later
		world.spawnParticles(ParticleTypes.SPORE_BLOSSOM_AIR, target.x, target.y + 0.5, target.z, 25, 0.3, 0.5, 0.3, 0.02);
		world.spawnParticles(ParticleTypes.MYCELIUM, player.getX(), player.getY() + 1, player.getZ(), 20, 0.3, 0.6, 0.3, 0.02);
		world.playSound(null, player.getBlockPos(), SoundEvents.BLOCK_FUNGUS_BREAK, SoundCategory.PLAYERS, 1.0f, 0.7f);
		JUMPS.add(new PendingJump(player.getUuid(), target, now + JUMP_DELAY, player.getPos()));
		data.dirty = true;
		return AbilityResult.OK;
	}

	/** Where the spore lands along the look: the block it hits (on top), or the end of the reach; null if no room. */
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

	/** Lets the body settle on ground up to 4 blocks below; null if it is stuck in a block on the way down. */
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
		// Nothing below within 4 blocks: hang where the spore is and fall from there
		return feet;
	}

	public static AbilityResult sporeCloud(ServerPlayerEntity player, RaceData data) {
		if (!ready(player, data, 1)) {
			return AbilityResult.COOLDOWN;
		}
		if (data.resource < CLOUD_COST) {
			return AbilityResult.NO_RESOURCE;
		}
		data.addResource(-CLOUD_COST);
		cooldown(player, data, 1, CLOUD_COOLDOWN);
		spawnCloud(player.getServerWorld(), player.getPos(), player.getUuid(), data.has(Talents.SWARM_WIDE) ? 6 : 4,
				data.has(Talents.SWARM_POTENT) ? 1 : 0, data.has(Talents.SWARM_LINGERING) ? 240 : 120);
		player.getWorld().playSound(null, player.getBlockPos(), SoundEvents.BLOCK_SPORE_BLOSSOM_BREAK, SoundCategory.PLAYERS, 1.2f, 0.6f);
		Progression.addXp(player, data, 3);
		return AbilityResult.OK;
	}

	public static void spawnCloud(ServerWorld world, Vec3d center, UUID owner, double radius, int potency, int ticks) {
		CLOUDS.add(new Cloud(world, center, owner, radius, potency, world.getServer().getTicks() + ticks));
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
		world.spawnParticles(ParticleTypes.MYCELIUM, pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5, 30, 0.4, 0.4, 0.4, 0.02);
		world.playSound(null, pos, SoundEvents.BLOCK_FUNGUS_PLACE, SoundCategory.BLOCKS, 1.0f, 0.8f);
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
				world.spawnParticles(ParticleTypes.MYCELIUM, player.getX(), player.getY() + 1, player.getZ(), 30, 0.3, 0.8, 0.3, 0.02);
				player.teleport(world, to.x, to.y, to.z, player.getYaw(), player.getPitch());
				player.fallDistance = 0;
				world.spawnParticles(ParticleTypes.SPORE_BLOSSOM_AIR, to.x, to.y + 1, to.z, 30, 0.3, 0.8, 0.3, 0.02);
				world.playSound(null, next.getPos(), SoundEvents.BLOCK_FUNGUS_PLACE, SoundCategory.BLOCKS, 1.0f, 0.6f);
				RaceData travellerData = RaceState.of(player);
				if (travellerData != null && travellerData.race == Race.MYCELID) {
					Progression.addXp(player, travellerData, 2);
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
		for (Iterator<PendingJump> it = JUMPS.iterator(); it.hasNext(); ) {
			PendingJump jump = it.next();
			if (jump.at() > now) {
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
			world.spawnParticles(ParticleTypes.SPORE_BLOSSOM_AIR, jump.target().x, jump.target().y + 1, jump.target().z, 30, 0.4, 0.8, 0.4, 0.02);
			world.playSound(null, player.getBlockPos(), SoundEvents.BLOCK_FUNGUS_PLACE, SoundCategory.PLAYERS, 1.0f, 1.2f);
			if (data != null) {
				if (data.has(Talents.NET_ROOTS)) {
					for (LivingEntity mob : world.getEntitiesByClass(LivingEntity.class, player.getBoundingBox().expand(3),
							e -> e instanceof Monster)) {
						mob.addStatusEffect(new StatusEffectInstance(StatusEffects.SLOWNESS, 40, 4));
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
			Vec3d c = cloud.center();
			if (now % 3 == 0) {
				double r = cloud.radius();
				world.spawnParticles(SPORE_DUST, c.x, c.y + 0.7, c.z, (int) (r * r * 2), r * 0.55, 0.5, r * 0.55, 0.0);
				world.spawnParticles(SPORE_DUST_DARK, c.x, c.y + 0.3, c.z, (int) (r * r), r * 0.55, 0.25, r * 0.55, 0.0);
				world.spawnParticles(ParticleTypes.SPORE_BLOSSOM_AIR, c.x, c.y + 1.2, c.z, (int) (r * 4), r * 0.5, 0.6, r * 0.5, 0.0);
				// The edge of the cloud, so it's clear where it ends
				for (int i = 0; i < 12; i++) {
					double a = (now * 0.05) + i * Math.PI / 6;
					world.spawnParticles(ParticleTypes.MYCELIUM, c.x + Math.cos(a) * r, c.y + 0.2, c.z + Math.sin(a) * r, 1, 0.05, 0.1, 0.05, 0.0);
				}
			}
			if (now % 20 == 0) {
				Box area = new Box(c, c).expand(cloud.radius(), 2, cloud.radius());
				for (LivingEntity entity : world.getEntitiesByClass(LivingEntity.class, area,
						e -> e.isAlive() && e.getPos().isInRange(c, cloud.radius() + 0.5))) {
					applyCloud(entity, cloud);
				}
			}
		}
	}

	/** Living monsters choke on the spores, the undead (immune to poison) rot; players breathe them in and heal. */
	private static void applyCloud(LivingEntity entity, Cloud cloud) {
		if (entity instanceof Monster) {
			entity.addStatusEffect(new StatusEffectInstance(StatusEffects.BLINDNESS, 60, 0));
			entity.addStatusEffect(new StatusEffectInstance(entity.isUndead() ? StatusEffects.WITHER : StatusEffects.POISON, 60, cloud.potency()));
			if (cloud.potency() > 0) {
				entity.addStatusEffect(new StatusEffectInstance(StatusEffects.SLOWNESS, 60, 1));
			}
		} else if (entity instanceof ServerPlayerEntity) {
			entity.addStatusEffect(new StatusEffectInstance(StatusEffects.REGENERATION, 60, 0, true, false, true));
		}
	}

	/** A monster died: if it was inside a cloud whose owner has Infection, the owner gains spores. */
	public static void onDeath(LivingEntity entity) {
		if (!(entity instanceof Monster) || entity.getServer() == null) {
			return;
		}
		for (Cloud cloud : CLOUDS) {
			if (cloud.world() == entity.getWorld() && entity.getPos().isInRange(cloud.center(), cloud.radius() + 0.5)) {
				ServerPlayerEntity owner = entity.getServer().getPlayerManager().getPlayer(cloud.owner());
				RaceData data = owner == null ? null : RaceState.of(owner);
				if (data != null && data.has(Talents.SWARM_INFECTION)) {
					data.addResource(10);
					Progression.addXp(owner, data, 10);
				}
				return;
			}
		}
	}

	public static int activeClouds() {
		return CLOUDS.size();
	}
}
