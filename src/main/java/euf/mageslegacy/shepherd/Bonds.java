package euf.mageslegacy.shepherd;

import euf.mageslegacy.mixin.FoxEntityInvoker;
import euf.mageslegacy.race.Progression;
import euf.mageslegacy.race.RaceData;
import euf.mageslegacy.race.RaceState;
import euf.mageslegacy.race.Talents;
import euf.mageslegacy.shepherd.ShepherdData.Bond;
import euf.mageslegacy.shepherd.ShepherdData.Ritual;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.block.CampfireBlock;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.ItemEntity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.attribute.EntityAttributeInstance;
import net.minecraft.entity.attribute.EntityAttributeModifier;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.entity.mob.Monster;
import net.minecraft.entity.passive.BeeEntity;
import net.minecraft.entity.passive.FoxEntity;
import net.minecraft.entity.passive.TameableEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Formatting;
import net.minecraft.util.Hand;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;

/**
 * Bonds with living beasts. The ritual: sing the Song of Bonds to a beast (sneak + right click with an empty hand),
 * then feed it the food it loves three times, a minute apart at least, the last time by a burning campfire. A bonded
 * beast follows its Shepherd, grows in levels and brings its skill; one that falls rests for a day and comes back.
 */
public final class Bonds {
	public static final int SONG_COST = 20;
	public static final int SONG_TICKS = 24000;
	public static final int FEED_GAP = 1200;
	private static final UUID LEVEL_HEALTH = UUID.fromString("5a7c3e91-2b4d-4f6a-8c1e-7d9b2f4a6c55");
	private static final UUID THICK_FUR = UUID.fromString("9b2e6d14-3c5a-4e7f-a1d8-6f3c9e2b7a66");
	private static final UUID ALPHA_HEALTH = UUID.fromString("1d6f8a3b-4e2c-4b9d-b7a5-3e8c2f6d9b77");
	private static final UUID ALPHA_DAMAGE = UUID.fromString("7f3a9c25-6d1b-4a8e-9c4f-2b7e5d1a3c88");

	private Bonds() {
	}

	// ---- the ritual ----

	/** Sneak + right click with an empty hand, or with the beast's food: the steps of the ritual. */
	public static ActionResult interact(ServerPlayerEntity player, RaceData data, Entity entity, Hand hand) {
		Optional<BeastKind> kind = BeastKind.of(entity.getType());
		if (kind.isEmpty() || hand != Hand.MAIN_HAND || !(entity instanceof MobEntity mob) || !mob.isAlive()) {
			return ActionResult.PASS;
		}
		ShepherdData shepherd = data.shepherd;
		if (shepherd.bond(entity.getUuid()).isPresent() || Creatures.ownerOf(entity) != null) {
			return ActionResult.PASS;
		}
		ItemStack stack = player.getMainHandStack();
		long now = player.getServerWorld().getTime();
		if (stack.isEmpty() && player.isSneaking()) {
			return sing(player, data, mob, now);
		}
		Ritual ritual = shepherd.rituals.get(entity.getUuid());
		if (ritual == null || ritual.listenUntil < now || !kind.get().food().test(stack)) {
			return ActionResult.PASS;
		}
		return feed(player, data, mob, kind.get(), ritual, stack, now);
	}

	private static ActionResult sing(ServerPlayerEntity player, RaceData data, MobEntity mob, long now) {
		if (data.resource < SONG_COST) {
			player.sendMessage(Text.translatable("mages_legacy.ability.no_resource.shepherd"), true);
			return ActionResult.FAIL;
		}
		data.addResource(-SONG_COST);
		Ritual ritual = data.shepherd.rituals.computeIfAbsent(mob.getUuid(), id -> new Ritual());
		ritual.listenUntil = now + SONG_TICKS;
		data.dirty = true;
		ServerWorld world = player.getServerWorld();
		for (int i = 0; i < 8; i++) {
			world.spawnParticles(ParticleTypes.NOTE, mob.getX() + (i - 4) * 0.15, mob.getY() + mob.getHeight() + 0.3 + (i % 3) * 0.2, mob.getZ(),
					0, i / 24.0, 0, 0, 1);
		}
		world.playSound(null, mob.getBlockPos(), SoundEvents.BLOCK_NOTE_BLOCK_FLUTE.value(), SoundCategory.PLAYERS, 1.0f, 0.8f);
		world.playSound(null, mob.getBlockPos(), SoundEvents.BLOCK_NOTE_BLOCK_FLUTE.value(), SoundCategory.PLAYERS, 1.0f, 1.2f);
		player.sendMessage(Text.translatable("mages_legacy.bond.listening", mob.getDisplayName(), feedingsNeeded(data)).formatted(Formatting.GOLD), true);
		Progression.addXp(player, data, 2);
		return ActionResult.SUCCESS;
	}

	public static int feedingsNeeded(RaceData data) {
		return data.has(Talents.PACK_FRIENDLY) ? 2 : 3;
	}

	private static ActionResult feed(ServerPlayerEntity player, RaceData data, MobEntity mob, BeastKind kind, Ritual ritual, ItemStack stack, long now) {
		if (ritual.feedings > 0 && now - ritual.lastFeed < FEED_GAP) {
			player.sendMessage(Text.translatable("mages_legacy.bond.too_soon", (FEED_GAP - (now - ritual.lastFeed) + 19) / 20), true);
			return ActionResult.FAIL;
		}
		int needed = feedingsNeeded(data);
		boolean last = ritual.feedings + 1 >= needed;
		if (last && !data.has(Talents.PACK_CAMPLESS) && !nearCampfire(player.getServerWorld(), mob.getBlockPos())) {
			player.sendMessage(Text.translatable("mages_legacy.bond.need_fire"), true);
			return ActionResult.FAIL;
		}
		if (last && Shepherd.capacityFree(player, data) < kind.capacity()) {
			player.sendMessage(Text.translatable("mages_legacy.ability.no_capacity"), true);
			return ActionResult.FAIL;
		}
		if (!player.getAbilities().creativeMode) {
			if (stack.isOf(Items.TROPICAL_FISH_BUCKET)) {
				player.setStackInHand(Hand.MAIN_HAND, new ItemStack(Items.WATER_BUCKET));
			} else {
				stack.decrement(1);
			}
		}
		ritual.feedings++;
		ritual.lastFeed = now;
		ritual.listenUntil = Math.max(ritual.listenUntil, now + SONG_TICKS);
		data.dirty = true;
		ServerWorld world = player.getServerWorld();
		world.spawnParticles(ParticleTypes.HEART, mob.getX(), mob.getY() + mob.getHeight(), mob.getZ(), 2, 0.3, 0.2, 0.3, 0);
		world.playSound(null, mob.getBlockPos(), SoundEvents.ENTITY_GENERIC_EAT, SoundCategory.NEUTRAL, 0.8f, 1.2f);
		Progression.addXp(player, data, 5);
		if (last) {
			bond(player, data, mob, kind);
		} else {
			player.sendMessage(Text.translatable("mages_legacy.bond.fed", ritual.feedings, needed), true);
		}
		return ActionResult.SUCCESS;
	}

	public static boolean nearCampfire(ServerWorld world, BlockPos pos) {
		for (BlockPos p : BlockPos.iterate(pos.add(-6, -2, -6), pos.add(6, 2, 6))) {
			if (CampfireBlock.isLitCampfire(world.getBlockState(p))) {
				return true;
			}
		}
		return false;
	}

	/** The ritual is done: the beast is this Shepherd's for good. */
	public static void bond(ServerPlayerEntity player, RaceData data, MobEntity mob, BeastKind kind) {
		ShepherdData shepherd = data.shepherd;
		shepherd.rituals.remove(mob.getUuid());
		Bond bond = new Bond();
		bond.entity = mob.getUuid();
		bond.kind = kind.id();
		bond.name = mob.hasCustomName() ? mob.getCustomName().getString() : "";
		shepherd.bonds.add(bond);
		data.dirty = true;
		claim(player.getUuid(), mob, data);
		apply(mob, bond, data);
		ServerWorld world = player.getServerWorld();
		world.spawnParticles(ParticleTypes.HEART, mob.getX(), mob.getY() + mob.getHeight() + 0.2, mob.getZ(), 8, 0.4, 0.3, 0.4, 0);
		world.spawnParticles(ParticleTypes.FLAME, mob.getX(), mob.getY() + 0.2, mob.getZ(), 20, 0.5, 0.1, 0.5, 0.01);
		world.playSound(null, mob.getBlockPos(), SoundEvents.ENTITY_PLAYER_LEVELUP, SoundCategory.PLAYERS, 0.6f, 1.4f);
		player.sendMessage(Text.translatable("mages_legacy.bond.done", mob.getDisplayName(),
				Text.translatable("mages_legacy.beast." + kind.id() + ".skill")).formatted(Formatting.GOLD), false);
		Progression.addXp(player, data, 30);
	}

	/** Makes a loaded beast this Shepherd's: tags, trust, owner, its new goals. */
	static void claim(UUID owner, MobEntity mob, RaceData data) {
		mob.addCommandTag(Creatures.BOND_TAG);
		mob.getCommandTags().removeIf(t -> t.startsWith(Creatures.OWNER_TAG));
		mob.addCommandTag(Creatures.OWNER_TAG + owner);
		mob.setPersistent();
		Creatures.registerBeast(mob, owner);
		if (mob instanceof TameableEntity tame) {
			tame.setOwnerUuid(owner);
			tame.setTamed(true);
		} else {
			Creatures.teach(mob, !(mob instanceof BeeEntity) && !(mob.getType() == EntityType.AXOLOTL));
		}
		if (mob instanceof FoxEntity fox) {
			((FoxEntityInvoker) fox).mages_legacy$trust(owner);
		}
		if (mob instanceof BeeEntity bee) {
			// A bonded bee stays with its Shepherd instead of vanishing into a hive
			bee.setCannotEnterHiveTicks(Integer.MAX_VALUE);
		}
	}

	/** Health from its level, Thick Fur, and the champion's strength for the Alpha. */
	static void apply(MobEntity mob, Bond bond, RaceData data) {
		modifier(mob, EntityAttributes.GENERIC_MAX_HEALTH, LEVEL_HEALTH, "bond_level", bond.level - 1, true);
		modifier(mob, EntityAttributes.GENERIC_MAX_HEALTH, THICK_FUR, "thick_fur", 4, data.has(Talents.PACK_THICK));
		boolean alpha = data.has(Talents.PACK_ALPHA) && alpha(data) == bond;
		modifier(mob, EntityAttributes.GENERIC_MAX_HEALTH, ALPHA_HEALTH, "alpha", 20, alpha);
		modifier(mob, EntityAttributes.GENERIC_ATTACK_DAMAGE, ALPHA_DAMAGE, "alpha", 4, alpha);
		if (alpha) {
			mob.addStatusEffect(new StatusEffectInstance(StatusEffects.RESISTANCE, 60, 0, true, false));
		}
	}

	private static void modifier(LivingEntity entity, net.minecraft.entity.attribute.EntityAttribute attribute, UUID id, String name,
			double amount, boolean want) {
		EntityAttributeInstance instance = entity.getAttributeInstance(attribute);
		if (instance == null) {
			return;
		}
		EntityAttributeModifier current = instance.getModifier(id);
		if (current != null && (!want || current.getValue() != amount)) {
			instance.removeModifier(id);
			current = null;
		}
		if (want && current == null && amount != 0) {
			instance.addPersistentModifier(new EntityAttributeModifier(id, name, amount, EntityAttributeModifier.Operation.ADDITION));
		}
		if (entity.getHealth() > entity.getMaxHealth()) {
			entity.setHealth(entity.getMaxHealth());
		}
	}

	/** The highest-level beast, the one the Alpha crown makes a champion. */
	static Bond alpha(RaceData data) {
		Bond best = null;
		for (Bond b : data.shepherd.bonds) {
			if (best == null || b.level > best.level) {
				best = b;
			}
		}
		return best;
	}

	public static int maxLevel(RaceData data) {
		return data.has(Talents.PACK_LEVELS) ? 20 : 10;
	}

	/** A bonded beast took part in a kill: it learns, and levels up every 30 x level experience. */
	public static void beastXp(ServerPlayerEntity owner, RaceData data, Bond bond, MobEntity mob, int amount) {
		if (bond.level >= maxLevel(data)) {
			return;
		}
		bond.xp += data.has(Talents.PACK_LEVELS) ? amount * 2 : amount;
		while (bond.level < maxLevel(data) && bond.xp >= 30 * bond.level) {
			bond.xp -= 30 * bond.level;
			bond.level++;
			apply(mob, bond, data);
			mob.heal(4);
			owner.sendMessage(Text.translatable("mages_legacy.bond.level", mob.getDisplayName(), bond.level).formatted(Formatting.GREEN), true);
			if (mob.getWorld() instanceof ServerWorld world) {
				world.spawnParticles(ParticleTypes.HAPPY_VILLAGER, mob.getX(), mob.getY() + mob.getHeight(), mob.getZ(), 10, 0.4, 0.4, 0.4, 0);
			}
		}
		data.dirty = true;
	}

	// ---- falling and coming back ----

	public static long restTicks(RaceData data) {
		return data.has(Talents.PACK_INSTANT) ? 1200 : data.has(Talents.PACK_QUICK_REST) ? 6000 : 24000;
	}

	/** A bonded beast would die: it rests instead, and comes back to its Shepherd later. False cancels the death. */
	public static boolean allowDeath(LivingEntity entity) {
		if (!(entity instanceof MobEntity mob) || entity.getServer() == null || !entity.getCommandTags().contains(Creatures.BOND_TAG)) {
			return true;
		}
		UUID ownerId = Creatures.taggedOwner(entity);
		RaceData data = ownerId == null ? null : RaceState.get(entity.getServer()).data(ownerId);
		Bond bond = data == null ? null : data.shepherd.bond(entity.getUuid()).orElse(null);
		if (bond == null) {
			return true;
		}
		mob.setHealth(mob.getMaxHealth());
		mob.clearStatusEffects();
		mob.extinguish();
		NbtCompound body = new NbtCompound();
		mob.saveSelfNbt(body);
		bond.body = body;
		bond.restingUntil = ((ServerWorld) mob.getWorld()).getTime() + restTicks(data);
		data.dirty = true;
		ServerWorld world = (ServerWorld) mob.getWorld();
		world.spawnParticles(ParticleTypes.CLOUD, mob.getX(), mob.getY() + 0.5, mob.getZ(), 15, 0.3, 0.3, 0.3, 0.02);
		ServerPlayerEntity owner = entity.getServer().getPlayerManager().getPlayer(ownerId);
		if (owner != null) {
			owner.sendMessage(Text.translatable("mages_legacy.bond.rests", mob.getDisplayName(), restTicks(data) / 1200).formatted(Formatting.YELLOW), false);
		}
		Creatures.forgetBeast(mob.getUuid());
		mob.discard();
		return false;
	}

	/** Once a second for an online Shepherd: rested beasts come back, nearby ones use their skills. */
	public static void tickOwner(ServerPlayerEntity owner, RaceData data) {
		ServerWorld world = owner.getServerWorld();
		long time = world.getTime();
		for (Bond bond : data.shepherd.bonds) {
			if (bond.resting()) {
				if (time >= bond.restingUntil) {
					wake(owner, data, bond);
				}
				continue;
			}
			if (world.getEntity(bond.entity) instanceof MobEntity mob && mob.isAlive()) {
				apply(mob, bond, data);
				skill(owner, data, bond, mob, time);
			}
		}
	}

	private static void wake(ServerPlayerEntity owner, RaceData data, Bond bond) {
		ServerWorld world = owner.getServerWorld();
		Entity entity = EntityType.loadEntityWithPassengers(bond.body, world, e -> {
			Vec3d spot = Creatures.near(owner);
			e.refreshPositionAndAngles(spot.x, spot.y, spot.z, e.getYaw(), e.getPitch());
			return e;
		});
		if (!(entity instanceof MobEntity mob)) {
			return;
		}
		// A fresh uuid: the body that fell may still be on the world's books for a tick, and a clash would refuse it
		mob.setUuid(UUID.randomUUID());
		bond.body = null;
		bond.restingUntil = 0;
		bond.entity = mob.getUuid();
		data.dirty = true;
		world.spawnNewEntityAndPassengers(mob);
		claim(owner.getUuid(), mob, data);
		world.spawnParticles(ParticleTypes.HEART, mob.getX(), mob.getY() + mob.getHeight(), mob.getZ(), 5, 0.3, 0.3, 0.3, 0);
		owner.sendMessage(Text.translatable("mages_legacy.bond.back", mob.getDisplayName()).formatted(Formatting.GREEN), true);
	}

	/** What each beast does for its Shepherd; Second Skill makes every one stronger. */
	private static void skill(ServerPlayerEntity owner, RaceData data, Bond bond, MobEntity mob, long time) {
		boolean strong = data.has(Talents.PACK_SKILL);
		ServerWorld world = owner.getServerWorld();
		double dist = mob.distanceTo(owner);
		switch (bond.kind) {
			case "wolf" -> {
				if (dist < 16) {
					mob.addStatusEffect(new StatusEffectInstance(StatusEffects.STRENGTH, 60, strong ? 1 : 0, true, false));
				}
			}
			case "cat" -> {
				double r = strong ? 20 : 12;
				for (LivingEntity m : world.getEntitiesByClass(LivingEntity.class, mob.getBoundingBox().expand(r), e -> e instanceof Monster)) {
					m.addStatusEffect(new StatusEffectInstance(StatusEffects.GLOWING, 40, 0, true, false));
				}
			}
			case "fox" -> {
				if (time % 60 < 20 && dist < 24) {
					double r = strong ? 12 : 8;
					for (ItemEntity item : world.getEntitiesByClass(ItemEntity.class, mob.getBoundingBox().expand(r),
							i -> i.isAlive() && !i.cannotPickup() && i.squaredDistanceTo(owner) > 36)) {
						world.spawnParticles(ParticleTypes.POOF, item.getX(), item.getY() + 0.2, item.getZ(), 3, 0.1, 0.1, 0.1, 0);
						item.refreshPositionAndAngles(owner.getX(), owner.getY() + 0.5, owner.getZ(), 0, 0);
					}
				}
			}
			case "bee" -> {
				if (time % 160 < 20) {
					double r = strong ? 10 : 6;
					Box area = mob.getBoundingBox().expand(r);
					if (owner.getBoundingBox().intersects(area)) {
						owner.addStatusEffect(new StatusEffectInstance(StatusEffects.REGENERATION, 100, 0, true, true));
					}
					for (LivingEntity c : world.getEntitiesByClass(LivingEntity.class, area, e -> Creatures.isCreatureOf(e, owner.getUuid()))) {
						c.addStatusEffect(new StatusEffectInstance(StatusEffects.REGENERATION, 100, 0, true, true));
					}
					world.spawnParticles(ParticleTypes.WAX_ON, mob.getX(), mob.getY() + 0.5, mob.getZ(), 6, r * 0.3, 0.4, r * 0.3, 0);
				}
			}
			case "axolotl" -> {
				if (owner.isTouchingWater() && dist < (strong ? 20 : 12)) {
					owner.addStatusEffect(new StatusEffectInstance(StatusEffects.WATER_BREATHING, 300, 0, true, true));
					owner.removeStatusEffect(StatusEffects.POISON);
				}
			}
			default -> {
			}
		}
	}

	/** When a bonded beast loads with its chunk: register it and give back its goals (goals aren't saved). */
	public static void onLoad(Entity entity, RaceData ownerData, UUID owner) {
		if (entity instanceof MobEntity mob && ownerData.shepherd.bond(entity.getUuid()).isPresent()) {
			claim(owner, mob, ownerData);
		}
	}

	/** The Shepherd changed race: the beasts are free again (resting ones come back first). */
	public static void releaseAll(ServerPlayerEntity owner, RaceData data) {
		for (Bond bond : List.copyOf(data.shepherd.bonds)) {
			if (bond.resting()) {
				wake(owner, data, bond);
			}
			if (owner.getServerWorld().getEntity(bond.entity) instanceof MobEntity mob) {
				mob.getCommandTags().remove(Creatures.BOND_TAG);
				mob.getCommandTags().removeIf(t -> t.startsWith(Creatures.OWNER_TAG));
				Creatures.forgetBeast(mob.getUuid());
			}
		}
	}
}
