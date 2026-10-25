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
import net.minecraft.entity.mob.SlimeEntity;
import net.minecraft.entity.passive.BeeEntity;
import net.minecraft.entity.passive.FoxEntity;
import net.minecraft.entity.passive.LlamaEntity;
import net.minecraft.entity.projectile.LlamaSpitEntity;
import net.minecraft.entity.passive.TameableEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.registry.Registries;
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
import org.jetbrains.annotations.Nullable;

/**
 * Bonds with living beasts. The ritual: sing the Song of Bonds to a beast (sneak + right click with an empty hand),
 * then feed it the food it loves three times, a minute apart at least, the last time by a burning campfire. A bonded
 * beast follows its Shepherd, grows in levels and brings its skill; one that falls rests for a day and comes back.
 */
public final class Bonds {
	public static final int SONG_COST = 20;
	public static final int SONG_TICKS = 24000;
	public static final int FEED_GAP = 1200;
	private static final int TURTLE_SLOT = 6;
	private static final UUID LEVEL_HEALTH = UUID.fromString("5a7c3e91-2b4d-4f6a-8c1e-7d9b2f4a6c55");
	private static final UUID THICK_FUR = UUID.fromString("9b2e6d14-3c5a-4e7f-a1d8-6f3c9e2b7a66");
	private static final UUID ALPHA_HEALTH = UUID.fromString("1d6f8a3b-4e2c-4b9d-b7a5-3e8c2f6d9b77");
	private static final UUID ALPHA_DAMAGE = UUID.fromString("7f3a9c25-6d1b-4a8e-9c4f-2b7e5d1a3c88");

	private Bonds() {
	}

	// ---- the ritual ----

	/**
	 * Sneak + right click with an empty hand sings the Song of Bonds; with the right thing in hand the beast's own way
	 * goes on (see {@link BeastKind.Taming}).
	 */
	public static ActionResult interact(ServerPlayerEntity player, RaceData data, Entity entity, Hand hand) {
		Optional<BeastKind> found = BeastKind.of(entity.getType());
		if (found.isEmpty() || hand != Hand.MAIN_HAND || !(entity instanceof MobEntity mob) || !mob.isAlive()) {
			return ActionResult.PASS;
		}
		BeastKind kind = found.get();
		ShepherdData shepherd = data.shepherd;
		if (shepherd.bond(entity.getUuid()).isPresent() || Creatures.ownerOf(entity) != null) {
			return ActionResult.PASS;
		}
		ItemStack stack = player.getMainHandStack();
		long now = player.getServerWorld().getTime();
		if (stack.isEmpty() && player.isSneaking()) {
			return sing(player, data, mob, kind, now);
		}
		Ritual ritual = shepherd.rituals.get(entity.getUuid());
		if (ritual == null || ritual.listenUntil < now || !kind.food().test(stack)) {
			return ActionResult.PASS;
		}
		return switch (kind.taming()) {
			case FEED_BY_FIRE, FEED_TWICE -> feed(player, data, mob, kind, ritual, stack, now);
			case ASLEEP, ONE_GIFT -> gift(player, data, mob, kind, stack);
			case TWO_KINDS -> flower(player, data, mob, kind, ritual, stack);
			case HEADBUTTS -> ritual.feedings >= needed(kind, data) ? gift(player, data, mob, kind, stack) : hint(player, "mages_legacy.bond.headbutt_first");
			case WATER_GIFT -> player.isTouchingWater() ? gift(player, data, mob, kind, stack) : hint(player, "mages_legacy.bond.swim_with_it");
			default -> ActionResult.PASS;
		};
	}

	/** How many steps this beast's way takes: feedings, songs, headbutts, kills, or seconds of patience. */
	public static int needed(BeastKind kind, RaceData data) {
		return needed(kind, data.has(Talents.PACK_FRIENDLY));
	}

	/** The same, for the client's bestiary: Gentle Voice takes a step off (four seconds off the cat's patience). */
	public static int needed(BeastKind kind, boolean gentle) {
		int friendly = gentle ? 1 : 0;
		return switch (kind.taming()) {
			case FEED_BY_FIRE, HEADBUTTS, HUNT, SONGS -> 3 - friendly;
			case FEED_TWICE, DARK_SONGS -> 2 - friendly;
			case PATIENCE -> 10 - 4 * friendly;
			case LEAD -> 60 - 20 * friendly;
			default -> 1;
		};
	}

	private static ActionResult hint(ServerPlayerEntity player, String key, Object... args) {
		player.sendMessage(Text.translatable(key, args), true);
		return ActionResult.FAIL;
	}

	private static ActionResult sing(ServerPlayerEntity player, RaceData data, MobEntity mob, BeastKind kind, long now) {
		Ritual existing = data.shepherd.rituals.get(mob.getUuid());
		if (kind.taming() == BeastKind.Taming.ASLEEP && !(mob instanceof FoxEntity fox && fox.isSleeping())) {
			return hint(player, "mages_legacy.bond.fox_awake");
		}
		boolean songs = kind.taming() == BeastKind.Taming.SONGS || kind.taming() == BeastKind.Taming.DARK_SONGS;
		if (kind.taming() == BeastKind.Taming.DARK_SONGS && mob.getWorld().getLightLevel(mob.getBlockPos()) > 7) {
			return hint(player, "mages_legacy.bond.too_bright");
		}
		if (songs && existing != null && existing.feedings > 0 && now - existing.lastFeed < FEED_GAP) {
			return hint(player, "mages_legacy.bond.too_soon", (FEED_GAP - (now - existing.lastFeed) + 19) / 20);
		}
		if (data.resource < SONG_COST) {
			return hint(player, "mages_legacy.ability.no_resource.shepherd");
		}
		data.addResource(-SONG_COST);
		Ritual ritual = data.shepherd.rituals.computeIfAbsent(mob.getUuid(), id -> new Ritual());
		ritual.listenUntil = now + SONG_TICKS;
		ritual.kind = kind.id();
		data.dirty = true;
		ServerWorld world = player.getServerWorld();
		for (int i = 0; i < 8; i++) {
			world.spawnParticles(ParticleTypes.NOTE, mob.getX() + (i - 4) * 0.15, mob.getY() + mob.getHeight() + 0.3 + (i % 3) * 0.2, mob.getZ(),
					0, i / 24.0, 0, 0, 1);
		}
		world.playSound(null, mob.getBlockPos(), SoundEvents.BLOCK_NOTE_BLOCK_FLUTE.value(), SoundCategory.PLAYERS, 1.0f, 0.8f);
		world.playSound(null, mob.getBlockPos(), SoundEvents.BLOCK_NOTE_BLOCK_FLUTE.value(), SoundCategory.PLAYERS, 1.0f, 1.2f);
		Progression.addXp(player, data, 2);
		if (songs) {
			// The parrot and the bat are won by the songs themselves
			ritual.feedings++;
			ritual.lastFeed = now;
			if (ritual.feedings >= needed(kind, data)) {
				return complete(player, data, mob, kind) ? ActionResult.SUCCESS : ActionResult.FAIL;
			}
			player.sendMessage(Text.translatable("mages_legacy.bond.progress", ritual.feedings, needed(kind, data)).formatted(Formatting.GOLD), true);
			return ActionResult.SUCCESS;
		}
		player.sendMessage(Text.translatable("mages_legacy.bond.listening", mob.getDisplayName(),
				Text.translatable("mages_legacy.beast." + kind.id() + ".taming", needed(kind, data))).formatted(Formatting.GOLD), true);
		return ActionResult.SUCCESS;
	}

	/** Uses up what was given (a tropical fish bucket leaves its water bucket). */
	private static void consume(ServerPlayerEntity player, ItemStack stack) {
		if (player.getAbilities().creativeMode) {
			return;
		}
		if (stack.isOf(Items.TROPICAL_FISH_BUCKET)) {
			player.setStackInHand(Hand.MAIN_HAND, new ItemStack(Items.WATER_BUCKET));
		} else {
			stack.decrement(1);
		}
	}

	private static void fedEffects(ServerPlayerEntity player, MobEntity mob) {
		ServerWorld world = player.getServerWorld();
		world.spawnParticles(ParticleTypes.HEART, mob.getX(), mob.getY() + mob.getHeight(), mob.getZ(), 2, 0.3, 0.2, 0.3, 0);
		world.playSound(null, mob.getBlockPos(), SoundEvents.ENTITY_GENERIC_EAT, SoundCategory.NEUTRAL, 0.8f, 1.2f);
	}

	/** The bond is made if there's room in the bonds; otherwise the Shepherd hears why not. */
	private static boolean complete(ServerPlayerEntity player, RaceData data, MobEntity mob, BeastKind kind) {
		if (Shepherd.capacityFree(player, data) < kind.capacity()) {
			player.sendMessage(Text.translatable("mages_legacy.ability.no_capacity"), true);
			return false;
		}
		bond(player, data, mob, kind);
		return true;
	}

	private static ActionResult feed(ServerPlayerEntity player, RaceData data, MobEntity mob, BeastKind kind, Ritual ritual, ItemStack stack, long now) {
		if (ritual.feedings > 0 && now - ritual.lastFeed < FEED_GAP) {
			return hint(player, "mages_legacy.bond.too_soon", (FEED_GAP - (now - ritual.lastFeed) + 19) / 20);
		}
		int needed = needed(kind, data);
		boolean last = ritual.feedings + 1 >= needed;
		if (last && kind.taming() == BeastKind.Taming.FEED_BY_FIRE && !data.has(Talents.PACK_CAMPLESS)
				&& !nearCampfire(player.getServerWorld(), mob.getBlockPos())) {
			return hint(player, "mages_legacy.bond.need_fire");
		}
		if (last && Shepherd.capacityFree(player, data) < kind.capacity()) {
			return hint(player, "mages_legacy.ability.no_capacity");
		}
		consume(player, stack);
		ritual.feedings++;
		ritual.lastFeed = now;
		ritual.listenUntil = Math.max(ritual.listenUntil, now + SONG_TICKS);
		data.dirty = true;
		fedEffects(player, mob);
		Progression.addXp(player, data, 5);
		if (last) {
			bond(player, data, mob, kind);
		} else {
			player.sendMessage(Text.translatable("mages_legacy.bond.progress", ritual.feedings, needed), true);
		}
		return ActionResult.SUCCESS;
	}

	/** One gift seals it (the sleeping fox's berries, the axolotl's fish, the goat's wheat after the headbutts). */
	private static ActionResult gift(ServerPlayerEntity player, RaceData data, MobEntity mob, BeastKind kind, ItemStack stack) {
		if (Shepherd.capacityFree(player, data) < kind.capacity()) {
			return hint(player, "mages_legacy.ability.no_capacity");
		}
		consume(player, stack);
		fedEffects(player, mob);
		Progression.addXp(player, data, 5);
		bond(player, data, mob, kind);
		return ActionResult.SUCCESS;
	}

	/** The bee wants two different flowers. */
	private static ActionResult flower(ServerPlayerEntity player, RaceData data, MobEntity mob, BeastKind kind, Ritual ritual, ItemStack stack) {
		String id = Registries.ITEM.getId(stack.getItem()).toString();
		if (ritual.note.isEmpty()) {
			consume(player, stack);
			ritual.note = id;
			ritual.feedings = 1;
			data.dirty = true;
			fedEffects(player, mob);
			player.sendMessage(Text.translatable("mages_legacy.bond.progress", 1, 2), true);
			return ActionResult.SUCCESS;
		}
		if (ritual.note.equals(id)) {
			return hint(player, "mages_legacy.bond.another_flower");
		}
		return gift(player, data, mob, kind, stack);
	}

	/** A bare-handed headbutt at a listening goat: counts, and does no harm. True cancels the blow. */
	public static boolean headbutt(ServerPlayerEntity player, RaceData data, Entity entity) {
		BeastKind kind = BeastKind.of(entity.getType()).orElse(null);
		Ritual ritual = data.shepherd.rituals.get(entity.getUuid());
		long now = player.getServerWorld().getTime();
		if (kind == null || kind.taming() != BeastKind.Taming.HEADBUTTS || ritual == null || ritual.listenUntil < now
				|| !player.getMainHandStack().isEmpty()) {
			return false;
		}
		if (now - ritual.lastFeed < 10) {
			return true;
		}
		if (ritual.feedings < needed(kind, data)) {
			ritual.feedings++;
			ritual.lastFeed = now;
			data.dirty = true;
			ServerWorld world = player.getServerWorld();
			world.spawnParticles(ParticleTypes.CRIT, entity.getX(), entity.getY() + entity.getHeight() * 0.7, entity.getZ(), 8, 0.3, 0.2, 0.3, 0.1);
			world.playSound(null, entity.getBlockPos(), SoundEvents.ENTITY_GOAT_RAM_IMPACT, SoundCategory.NEUTRAL, 0.8f, 1.2f);
			player.sendMessage(Text.translatable(ritual.feedings >= needed(kind, data) ? "mages_legacy.bond.headbutts_done"
					: "mages_legacy.bond.progress", ritual.feedings, needed(kind, data)).formatted(Formatting.GOLD), true);
		}
		return true;
	}

	/** A monster died near a listening polar bear: it saw the Shepherd's strength. */
	public static void onHunt(ServerPlayerEntity owner, RaceData data, LivingEntity victim) {
		if (data.shepherd.rituals.isEmpty()) {
			return;
		}
		long now = owner.getServerWorld().getTime();
		for (MobEntity bear : owner.getServerWorld().getEntitiesByClass(MobEntity.class, victim.getBoundingBox().expand(12), e -> e.isAlive()
				&& BeastKind.of(e.getType()).map(k -> k.taming() == BeastKind.Taming.HUNT).orElse(false))) {
			Ritual ritual = data.shepherd.rituals.get(bear.getUuid());
			if (ritual == null || ritual.listenUntil < now) {
				continue;
			}
			BeastKind kind = BeastKind.of(bear.getType()).orElseThrow();
			ritual.feedings++;
			data.dirty = true;
			if (ritual.feedings >= needed(kind, data)) {
				complete(owner, data, bear, kind);
			} else {
				owner.sendMessage(Text.translatable("mages_legacy.bond.progress", ritual.feedings, needed(kind, data)).formatted(Formatting.GOLD), true);
			}
		}
	}

	/**
	 * Once a second: a cat being won by patience counts the seconds the Shepherd crouches by it with fish in hand; a
	 * llama counts the seconds it walks on the Shepherd's lead.
	 */
	private static void patience(ServerPlayerEntity owner, RaceData data, long now) {
		for (MobEntity llama : owner.getServerWorld().getEntitiesByClass(MobEntity.class, owner.getBoundingBox().expand(12), e -> e.isAlive()
				&& e.getHoldingEntity() == owner && BeastKind.of(e.getType()).map(k -> k.taming() == BeastKind.Taming.LEAD).orElse(false))) {
			Ritual ritual = data.shepherd.rituals.get(llama.getUuid());
			BeastKind kind = BeastKind.of(llama.getType()).orElseThrow();
			if (ritual == null || ritual.listenUntil < now) {
				continue;
			}
			ritual.feedings++;
			data.dirty = true;
			if (ritual.feedings >= needed(kind, data)) {
				complete(owner, data, llama, kind);
			} else if (ritual.feedings % 10 == 0) {
				owner.sendMessage(Text.translatable("mages_legacy.bond.progress", ritual.feedings, needed(kind, data)).formatted(Formatting.GOLD), true);
			}
		}
		for (MobEntity cat : owner.getServerWorld().getEntitiesByClass(MobEntity.class, owner.getBoundingBox().expand(4), e -> e.isAlive()
				&& BeastKind.of(e.getType()).map(k -> k.taming() == BeastKind.Taming.PATIENCE).orElse(false))) {
			Ritual ritual = data.shepherd.rituals.get(cat.getUuid());
			BeastKind kind = BeastKind.of(cat.getType()).orElseThrow();
			if (ritual == null || ritual.listenUntil < now) {
				continue;
			}
			if (!owner.isSneaking() || !kind.food().test(owner.getMainHandStack())) {
				ritual.feedings = Math.max(0, ritual.feedings - 1);
				continue;
			}
			ritual.feedings++;
			data.dirty = true;
			owner.getServerWorld().spawnParticles(ParticleTypes.HEART, cat.getX(), cat.getY() + cat.getHeight(), cat.getZ(), 1, 0.2, 0.1, 0.2, 0);
			if (ritual.feedings >= needed(kind, data)) {
				if (complete(owner, data, cat, kind)) {
					consume(owner, owner.getMainHandStack());
				}
			} else {
				owner.sendMessage(Text.translatable("mages_legacy.bond.progress", ritual.feedings, needed(kind, data)).formatted(Formatting.GOLD), true);
			}
		}
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
				Text.translatable("mages_legacy.beast." + kind.id() + ".skill_desc")).formatted(Formatting.GOLD), false);
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
			// Foxes and polar bears fight for their Shepherd; bees, axolotls, goats and frogs help in their own ways
			Creatures.teach(mob, mob.getType() == EntityType.FOX || mob.getType() == EntityType.POLAR_BEAR);
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
		patience(owner, data, time);
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
		// Goats, frogs and axolotls think with a brain, not with goals: when they fall behind they jump to the Shepherd
		// (so do bats and turtles, the one flutters and the other is too slow to keep up)
		boolean brainy = mob.getType() == EntityType.GOAT || mob.getType() == EntityType.FROG || mob.getType() == EntityType.AXOLOTL
				|| mob.getType() == EntityType.BAT || mob.getType() == EntityType.TURTLE;
		boolean swimmer = mob.getType() == EntityType.AXOLOTL || mob.getType() == EntityType.DOLPHIN;
		if ((brainy || swimmer) && dist > 12 && (!swimmer || owner.isTouchingWater())) {
			Vec3d spot = Creatures.near(owner);
			mob.refreshPositionAndAngles(spot.x, spot.y, spot.z, mob.getYaw(), mob.getPitch());
		}
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
				if (dist < 16 && world.isNight()) {
					owner.addStatusEffect(new StatusEffectInstance(StatusEffects.NIGHT_VISION, 300, 0, true, false, true));
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
				// Now and then she digs something up (every 5 minutes, 3 with Second Skill)
				int every = strong ? 3600 : 6000;
				if (dist < 24 && Math.floorMod(time + mob.getUuid().hashCode(), every) < 20) {
					ItemStack find = FoxFinds.roll(mob.getRandom());
					world.spawnEntity(new ItemEntity(world, owner.getX(), owner.getY() + 0.5, owner.getZ(), find));
					world.spawnParticles(ParticleTypes.HAPPY_VILLAGER, mob.getX(), mob.getY() + 0.5, mob.getZ(), 8, 0.3, 0.3, 0.3, 0);
					owner.sendMessage(Text.translatable("mages_legacy.beast.fox.found", find.getName()).formatted(Formatting.GOLD), true);
				}
			}
			case "bee" -> {
				if (time % 160 < 20) {
					double r = strong ? 10 : 6;
					Box area = mob.getBoundingBox().expand(r);
					if (owner.getBoundingBox().intersects(area)) {
						owner.addStatusEffect(new StatusEffectInstance(StatusEffects.REGENERATION, 100, 0, true, true));
						owner.removeStatusEffect(StatusEffects.POISON);
						owner.removeStatusEffect(StatusEffects.WITHER);
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
					owner.addStatusEffect(new StatusEffectInstance(StatusEffects.DOLPHINS_GRACE, 60, 0, true, true));
					owner.addStatusEffect(new StatusEffectInstance(StatusEffects.REGENERATION, 60, 0, true, true));
					owner.removeStatusEffect(StatusEffects.POISON);
				}
			}
			case "goat" -> {
				// Rams the nearest monster at its Shepherd's side every 4 seconds (every 2 with Second Skill)
				if (time % (strong ? 40 : 80) < 20 && dist < 16) {
					LivingEntity target = nearestMonster(world, owner, 6);
					if (target != null) {
						Vec3d away = target.getPos().subtract(owner.getPos()).normalize();
						Vec3d spot = target.getPos().subtract(away.multiply(1.2));
						mob.refreshPositionAndAngles(spot.x, target.getY(), spot.z, mob.getYaw(), mob.getPitch());
						target.damage(world.getDamageSources().mobAttack(mob), strong ? 6 : 4);
						target.takeKnockback(1.6, -away.x, -away.z);
						world.playSound(null, target.getBlockPos(), SoundEvents.ENTITY_GOAT_RAM_IMPACT, SoundCategory.NEUTRAL, 1.0f, 1.0f);
						world.spawnParticles(ParticleTypes.CRIT, target.getX(), target.getY() + 1, target.getZ(), 10, 0.3, 0.3, 0.3, 0.2);
					}
				}
			}
			case "polar_bear" -> {
				// The tank: monsters going for the Shepherd turn on the bear
				mob.addStatusEffect(new StatusEffectInstance(StatusEffects.RESISTANCE, 60, strong ? 1 : 0, true, false));
				if (dist < 16) {
					for (MobEntity m : world.getEntitiesByClass(MobEntity.class, mob.getBoundingBox().expand(8),
							e -> e instanceof Monster && e.getTarget() == owner)) {
						m.setTarget(mob);
					}
				}
			}
			case "parrot" -> {
				// Cries out when something hunts the Shepherd, and catches it when it falls
				for (MobEntity m : world.getEntitiesByClass(MobEntity.class, owner.getBoundingBox().expand(strong ? 24 : 16),
						e -> e instanceof Monster && e.getTarget() == owner)) {
					if (!m.hasStatusEffect(StatusEffects.GLOWING)) {
						owner.sendMessage(Text.translatable("mages_legacy.beast.parrot.warn", m.getDisplayName()).formatted(Formatting.RED), true);
						world.playSound(null, mob.getBlockPos(), SoundEvents.ENTITY_PARROT_IMITATE_CREEPER, SoundCategory.NEUTRAL, 0.8f, 1.2f);
					}
					m.addStatusEffect(new StatusEffectInstance(StatusEffects.GLOWING, 60, 0, true, false));
				}
				if (dist < 16 && owner.fallDistance > 4) {
					owner.addStatusEffect(new StatusEffectInstance(StatusEffects.SLOW_FALLING, 60, 0, true, false, true));
				}
			}
			case "frog" -> {
				// Every 3 seconds the tongue pulls the nearest monster in and slows it; small slimes it simply eats
				if (time % 60 < 20) {
					LivingEntity target = nearestMonster(world, mob, strong ? 12 : 8);
					if (target instanceof SlimeEntity slime && slime.getSize() <= 1) {
						world.spawnParticles(ParticleTypes.ITEM_SLIME, slime.getX(), slime.getY(), slime.getZ(), 8, 0.2, 0.2, 0.2, 0.05);
						slime.discard();
					} else if (target != null) {
						Vec3d pull = mob.getPos().subtract(target.getPos()).normalize().multiply(0.9);
						target.addVelocity(pull.x, 0.25, pull.z);
						target.velocityModified = true;
						target.addStatusEffect(new StatusEffectInstance(StatusEffects.SLOWNESS, 60, strong ? 2 : 1));
						world.playSound(null, mob.getBlockPos(), SoundEvents.ENTITY_FROG_TONGUE, SoundCategory.NEUTRAL, 1.0f, 1.0f);
					}
				}
			}
			case "llama" -> {
				// Spits at the nearest monster near its Shepherd every 3 seconds
				if (time % 60 < 20 && dist < 16 && mob instanceof LlamaEntity llama) {
					LivingEntity target = nearestMonster(world, owner, 10);
					if (target != null) {
						LlamaSpitEntity spit = new LlamaSpitEntity(world, llama);
						Vec3d to = target.getEyePos().subtract(spit.getPos());
						spit.setVelocity(to.x, to.y + to.horizontalLength() * 0.1, to.z, 1.5f, 2);
						world.spawnEntity(spit);
						target.addStatusEffect(new StatusEffectInstance(StatusEffects.SLOWNESS, 60, strong ? 2 : 1));
						world.playSound(null, mob.getBlockPos(), SoundEvents.ENTITY_LLAMA_SPIT, SoundCategory.NEUTRAL, 1.0f, 1.0f);
					}
				}
			}
			case "rabbit" -> {
				if (dist < 16) {
					owner.addStatusEffect(new StatusEffectInstance(StatusEffects.LUCK, 60, strong ? 1 : 0, true, false, true));
					owner.addStatusEffect(new StatusEffectInstance(StatusEffects.JUMP_BOOST, 60, strong ? 1 : 0, true, false, true));
				}
			}
			case "turtle" -> {
				// The shell: on the brink of death the Shepherd hardens, once in 90 seconds (60 with Second Skill)
				long ticks = owner.getServer().getTicks();
				if (dist < 24 && owner.getHealth() < owner.getMaxHealth() * 0.3f && data.readyAt.getOrDefault(TURTLE_SLOT, 0L) <= ticks) {
					owner.addStatusEffect(new StatusEffectInstance(StatusEffects.RESISTANCE, 200, 1, true, true, true));
					owner.addStatusEffect(new StatusEffectInstance(StatusEffects.ABSORPTION, 200, 1, true, true, true));
					data.readyAt.put(TURTLE_SLOT, ticks + (strong ? 1200 : 1800));
					world.playSound(null, owner.getBlockPos(), SoundEvents.ITEM_ARMOR_EQUIP_TURTLE, SoundCategory.PLAYERS, 1.0f, 0.8f);
					owner.sendMessage(Text.translatable("mages_legacy.beast.turtle.shell").formatted(Formatting.GREEN), true);
				}
			}
			case "dolphin" -> {
				if (owner.isTouchingWater() && dist < (strong ? 24 : 16)) {
					owner.addStatusEffect(new StatusEffectInstance(StatusEffects.CONDUIT_POWER, 300, 0, true, false, true));
					owner.addStatusEffect(new StatusEffectInstance(StatusEffects.DOLPHINS_GRACE, 60, 0, true, false, true));
				}
			}
			case "bat" -> {
				// Echolocation: underground, monsters around glow
				if (dist < 24 && !world.isSkyVisible(owner.getBlockPos())) {
					for (LivingEntity m : world.getEntitiesByClass(LivingEntity.class, owner.getBoundingBox().expand(strong ? 32 : 20),
							e -> e instanceof Monster)) {
						m.addStatusEffect(new StatusEffectInstance(StatusEffects.GLOWING, 40, 0, true, false));
					}
				}
			}
			default -> {
			}
		}
	}

	@Nullable
	private static LivingEntity nearestMonster(ServerWorld world, Entity center, double radius) {
		LivingEntity best = null;
		for (LivingEntity m : world.getEntitiesByClass(LivingEntity.class, center.getBoundingBox().expand(radius),
				e -> e instanceof Monster && e.isAlive() && Creatures.ownerOf(e) == null)) {
			if (best == null || m.squaredDistanceTo(center) < best.squaredDistanceTo(center)) {
				best = m;
			}
		}
		return best;
	}

	/** A bonded beast's blow: the wolf's bite slows. */
	public static void onBeastHit(Entity beast, LivingEntity victim) {
		if (beast.getType() == EntityType.WOLF) {
			victim.addStatusEffect(new StatusEffectInstance(StatusEffects.SLOWNESS, 40, 1));
		}
	}

	/** Polar bear guard: a Shepherd with its bonded bear close by takes a fifth less damage. */
	public static boolean bearGuards(ServerPlayerEntity owner, RaceData data) {
		for (Bond bond : data.shepherd.bonds) {
			if (bond.kind.equals("polar_bear") && !bond.resting() && owner.getServerWorld().getEntity(bond.entity) instanceof MobEntity bear
					&& bear.isAlive() && bear.distanceTo(owner) < 6) {
				return true;
			}
		}
		return false;
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
