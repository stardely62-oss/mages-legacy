package euf.mageslegacy.shepherd;

import euf.mageslegacy.net.RacePackets;
import euf.mageslegacy.race.RaceData;
import euf.mageslegacy.race.RaceState;
import euf.mageslegacy.race.Talents;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.fabricmc.fabric.api.networking.v1.PacketByteBufs;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.block.Blocks;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.SpawnReason;
import net.minecraft.entity.attribute.EntityAttributeInstance;
import net.minecraft.entity.attribute.EntityAttributeModifier;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.entity.mob.Monster;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.particle.DustParticleEffect;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.registry.RegistryKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.RaycastContext;
import net.minecraft.world.World;
import org.jetbrains.annotations.Nullable;
import org.joml.Vector3f;

/**
 * Echoes of the fallen: real mobs of the learned kind that fight for their Shepherd, wrapped in pale spirit light.
 * They vanish at dawn (unless the dead don't sleep), when their Shepherd leaves, or when they fall: no loot, no
 * experience. Kept only in memory; one found in a loaded chunk without its record is a leftover and is removed.
 */
public final class Echoes {
	public static final String TAG = "mages_legacy_echo";
	private static final UUID STURDY = UUID.fromString("3c9d2b7e-6a14-4f0b-9e2d-5b8c1a7f4e33");
	private static final UUID WEAPONS = UUID.fromString("8e1f4a2c-7b35-4d9e-a6c0-2f5b9d3e1a44");
	public static final DustParticleEffect SPIRIT = new DustParticleEffect(new Vector3f(0.62f, 0.91f, 0.88f), 1.0f);

	/** A living echo: whose, what kind, in which world, until when (world time), and what it cost. */
	public record Echo(UUID owner, String type, RegistryKey<World> world, long expiresAt, boolean temporary, int voice) {}

	private static final Map<UUID, Echo> ACTIVE = new HashMap<>();

	/** Cobwebs a spider echo spun, removed again after a few seconds. */
	private record Web(ServerWorld world, BlockPos pos, long until) {}

	private static final List<Web> WEBS = new ArrayList<>();

	private Echoes() {
	}

	public static void reset() {
		ACTIVE.clear();
		WEBS.clear();
	}

	/** The kind of echo (entity type id), or "" for anything else. */
	public static String typeOf(Entity entity) {
		Echo echo = ACTIVE.get(entity.getUuid());
		return echo == null ? "" : echo.type();
	}

	/**
	 * Every kind of echo hits in its own way (texts: mages_legacy.echo.trait.<kind>). Returns the damage, which the
	 * Executioner doubles on the nearly dead.
	 */
	public static float onHit(Entity echo, LivingEntity victim, float amount) {
		Echo info = ACTIVE.get(echo.getUuid());
		if (info == null || !(victim.getWorld() instanceof ServerWorld world)) {
			return amount;
		}
		switch (info.type()) {
			case "minecraft:husk" -> {
				victim.addStatusEffect(new StatusEffectInstance(StatusEffects.SLOWNESS, 60, 1));
				victim.addStatusEffect(new StatusEffectInstance(StatusEffects.HUNGER, 100, 0));
			}
			case "minecraft:skeleton" -> {
				// Its arrows mark the target for the whole army
				victim.addStatusEffect(new StatusEffectInstance(StatusEffects.GLOWING, 100, 0));
				ServerPlayerEntity owner = world.getServer().getPlayerManager().getPlayer(info.owner());
				if (owner != null && victim instanceof Monster) {
					Creatures.mark(owner, victim, world.getServer().getTicks() + 100);
				}
			}
			case "minecraft:stray" -> victim.addStatusEffect(new StatusEffectInstance(StatusEffects.SLOWNESS, 60, 2));
			case "minecraft:spider" -> {
				BlockPos feet = victim.getBlockPos();
				if (world.random.nextFloat() < 0.25f && world.getBlockState(feet).isAir()) {
					world.setBlockState(feet, Blocks.COBWEB.getDefaultState());
					WEBS.add(new Web(world, feet, world.getServer().getTicks() + 100));
				}
			}
			case "minecraft:cave_spider" -> victim.addStatusEffect(victim.isUndead()
					? new StatusEffectInstance(StatusEffects.WITHER, 80, 0) : new StatusEffectInstance(StatusEffects.POISON, 80, 1));
			case "minecraft:vindicator" -> {
				if (victim.getHealth() - amount < victim.getMaxHealth() * 0.3f) {
					amount *= 2;
					world.spawnParticles(ParticleTypes.DAMAGE_INDICATOR, victim.getX(), victim.getY() + 1, victim.getZ(), 6, 0.3, 0.3, 0.3, 0.1);
				}
			}
			case "minecraft:silverfish" -> victim.addStatusEffect(new StatusEffectInstance(StatusEffects.SLOWNESS, 40, 0));
			case "minecraft:pillager" -> victim.addStatusEffect(new StatusEffectInstance(StatusEffects.WEAKNESS, 80, 0));
			case "minecraft:wither_skeleton" -> {
				victim.addStatusEffect(new StatusEffectInstance(StatusEffects.WITHER, 100, 1));
				ServerPlayerEntity owner = world.getServer().getPlayerManager().getPlayer(info.owner());
				if (owner != null) {
					owner.heal(1);
				}
			}
			default -> {
			}
		}
		return amount;
	}

	/** What some echoes do just by being near: the zombie draws monsters onto itself, the drowned and the blaze guard. */
	private static void aura(Echo echo, MobEntity mob, ServerPlayerEntity owner) {
		switch (echo.type()) {
			case "minecraft:zombie" -> {
				for (MobEntity m : mob.getWorld().getEntitiesByClass(MobEntity.class, mob.getBoundingBox().expand(8),
						e -> e instanceof Monster && e.getTarget() == owner)) {
					m.setTarget(mob);
				}
			}
			case "minecraft:drowned" -> {
				if (owner.isTouchingWater() && owner.distanceTo(mob) < 12) {
					owner.addStatusEffect(new StatusEffectInstance(StatusEffects.DOLPHINS_GRACE, 60, 0, true, false, true));
					owner.addStatusEffect(new StatusEffectInstance(StatusEffects.WATER_BREATHING, 100, 0, true, false, true));
				}
			}
			case "minecraft:blaze" -> {
				if (owner.distanceTo(mob) < 8) {
					owner.addStatusEffect(new StatusEffectInstance(StatusEffects.FIRE_RESISTANCE, 60, 0, true, false, true));
				}
			}
			case "minecraft:witch" -> {
				// The herbalist: every 10 seconds a healing brew for the Shepherd and its creatures close by
				if (mob.getServer().getTicks() % 200 < 20) {
					ServerWorld world = (ServerWorld) mob.getWorld();
					if (owner.distanceTo(mob) < 8) {
						owner.addStatusEffect(new StatusEffectInstance(StatusEffects.REGENERATION, 100, 0, true, true, true));
					}
					for (LivingEntity c : world.getEntitiesByClass(LivingEntity.class, mob.getBoundingBox().expand(8),
							e -> Creatures.isCreatureOf(e, owner.getUuid()))) {
						c.addStatusEffect(new StatusEffectInstance(StatusEffects.REGENERATION, 100, 0, true, true));
					}
					world.spawnParticles(ParticleTypes.HAPPY_VILLAGER, mob.getX(), mob.getY() + 1.5, mob.getZ(), 10, 1.5, 0.5, 1.5, 0);
				}
			}
			case "minecraft:ravager" -> {
				// A roar every 6 seconds throws back the monsters around it
				if (mob.getServer().getTicks() % 120 < 20) {
					ServerWorld world = (ServerWorld) mob.getWorld();
					boolean any = false;
					for (LivingEntity m : world.getEntitiesByClass(LivingEntity.class, mob.getBoundingBox().expand(4),
							e -> e instanceof Monster && Creatures.ownerOf(e) == null)) {
						Vec3d away = m.getPos().subtract(mob.getPos());
						m.damage(world.getDamageSources().mobAttack(mob), 3);
						m.takeKnockback(1.5, -away.x, -away.z);
						any = true;
					}
					if (any) {
						world.playSound(null, mob.getBlockPos(), SoundEvents.ENTITY_RAVAGER_ROAR, SoundCategory.HOSTILE, 1.0f, 1.0f);
						world.spawnParticles(ParticleTypes.POOF, mob.getX(), mob.getY() + 1, mob.getZ(), 20, 1.5, 0.5, 1.5, 0.05);
					}
				}
			}
			default -> {
			}
		}
	}

	public static boolean isEcho(Entity entity) {
		return ACTIVE.containsKey(entity.getUuid());
	}

	@Nullable
	public static UUID ownerOf(Entity entity) {
		Echo echo = ACTIVE.get(entity.getUuid());
		return echo == null ? null : echo.owner();
	}

	/** Bond slots this Shepherd's echoes take (the Army of Oblivion's temporary ones are free). */
	public static int capacityUsed(UUID owner) {
		int used = 0;
		for (Map.Entry<String, Integer> e : countByType(owner).entrySet()) {
			used += EchoType.byId(e.getKey()).map(t -> t.units(e.getValue()) * t.capacity()).orElse(0);
		}
		return used;
	}

	/** How many echoes of each kind walk with this Shepherd now (the Army of Oblivion's temporary ones aside). */
	public static Map<String, Integer> countByType(UUID owner) {
		Map<String, Integer> counts = new HashMap<>();
		for (Echo echo : ACTIVE.values()) {
			if (echo.owner().equals(owner) && !echo.temporary()) {
				counts.merge(echo.type(), 1, Integer::sum);
			}
		}
		return counts;
	}

	/** Spots for a squad: the first at the Call's spot, the rest in a ring around it where there's room. */
	static Vec3d around(ServerWorld world, Vec3d base, int index) {
		if (index == 0) {
			return base;
		}
		double a = index * 2.4;
		double r = 1.5 + (index / 6) * 1.2;
		BlockPos column = BlockPos.ofFloored(base.x + Math.cos(a) * r, base.y, base.z + Math.sin(a) * r);
		for (int dy = 1; dy >= -2; dy--) {
			BlockPos feet = column.up(dy);
			if (world.getBlockState(feet).getCollisionShape(world, feet).isEmpty()
					&& world.getBlockState(feet.up()).getCollisionShape(world, feet.up()).isEmpty()
					&& !world.getBlockState(feet.down()).getCollisionShape(world, feet.down()).isEmpty()) {
				return Vec3d.ofBottomCenter(feet);
			}
		}
		return base;
	}

	public static int count(UUID owner) {
		return (int) ACTIVE.values().stream().filter(e -> e.owner().equals(owner)).count();
	}

	/** Where the Call lands: the block you look at within 8, else in front of you. */
	static Vec3d callSpot(ServerPlayerEntity player) {
		ServerWorld world = player.getServerWorld();
		Vec3d eye = player.getEyePos();
		Vec3d end = eye.add(player.getRotationVec(1.0f).multiply(8));
		BlockHitResult hit = world.raycast(new RaycastContext(eye, end, RaycastContext.ShapeType.COLLIDER, RaycastContext.FluidHandling.NONE, player));
		if (hit.getType() == HitResult.Type.BLOCK) {
			BlockPos feet = hit.getBlockPos().offset(hit.getSide());
			if (world.getBlockState(feet).getCollisionShape(world, feet).isEmpty()
					&& world.getBlockState(feet.up()).getCollisionShape(world, feet.up()).isEmpty()) {
				return Vec3d.ofBottomCenter(feet);
			}
		}
		return Creatures.near(player);
	}

	/**
	 * Calls an echo of {@code type} to {@code at}. A temporary one lives {@code ticks}; a normal one until the next
	 * dawn, or until dismissed with the Undying talent.
	 */
	@Nullable
	public static MobEntity summon(ServerPlayerEntity player, RaceData data, EchoType type, Vec3d at, boolean temporary, int ticks, int voice) {
		ServerWorld world = player.getServerWorld();
		if (!(type.type().create(world) instanceof MobEntity mob)) {
			return null;
		}
		mob.refreshPositionAndAngles(at.x, at.y, at.z, player.getYaw() + 180, 0);
		mob.initialize(world, world.getLocalDifficulty(mob.getBlockPos()), SpawnReason.MOB_SUMMONED, null, null);
		// No jockeys: a skeleton riding the spider or a chicken under the zombie would be ordinary hostile mobs
		mob.getPassengerList().forEach(Entity::discard);
		if (mob.getVehicle() != null) {
			mob.getVehicle().discard();
		}
		mob.setPersistent();
		mob.addCommandTag(TAG);
		mob.setCanPickUpLoot(false);
		for (EquipmentSlot slot : EquipmentSlot.values()) {
			mob.setEquipmentDropChance(slot, 0);
		}
		mob.setCustomName(Text.translatable("mages_legacy.echo.name", type.type().getName()).formatted(Formatting.AQUA));
		mob.setCustomNameVisible(false);
		mob.addStatusEffect(new StatusEffectInstance(StatusEffects.FIRE_RESISTANCE, StatusEffectInstance.INFINITE, 0, true, false));
		if (data.has(Talents.LEGION_STURDY)) {
			addModifier(mob, EntityAttributes.GENERIC_MAX_HEALTH, STURDY, 0.2, EntityAttributeModifier.Operation.MULTIPLY_BASE);
			mob.setHealth(mob.getMaxHealth());
		}
		if (data.has(Talents.LEGION_WEAPONS)) {
			addModifier(mob, EntityAttributes.GENERIC_ATTACK_DAMAGE, WEAPONS, 2, EntityAttributeModifier.Operation.ADDITION);
		}
		long time = world.getTimeOfDay();
		long expires = temporary ? time + ticks
				: data.has(Talents.LEGION_SLEEPLESS) ? Long.MAX_VALUE
				// Dawn is the next multiple of a day in world time
				: (time / 24000 + 1) * 24000;
		ACTIVE.put(mob.getUuid(), new Echo(player.getUuid(), type.id(), world.getRegistryKey(), expires, temporary, voice));
		Creatures.teach(mob, true);
		world.spawnEntity(mob);
		broadcast(world.getServer());
		rise(world, at);
		world.playSound(null, mob.getBlockPos(), SoundEvents.PARTICLE_SOUL_ESCAPE, SoundCategory.PLAYERS, 1.5f, 0.7f);
		world.playSound(null, mob.getBlockPos(), SoundEvents.ENTITY_ALLAY_AMBIENT_WITHOUT_ITEM, SoundCategory.PLAYERS, 0.6f, 0.5f);
		return mob;
	}

	private static void addModifier(MobEntity mob, net.minecraft.entity.attribute.EntityAttribute attribute, UUID id, double amount,
			EntityAttributeModifier.Operation operation) {
		EntityAttributeInstance instance = mob.getAttributeInstance(attribute);
		if (instance != null && instance.getModifier(id) == null) {
			instance.addPersistentModifier(new EntityAttributeModifier(id, "mages_legacy_echo", amount, operation));
		}
	}

	/** Pale light rising out of the ground where an echo answers. */
	static void rise(ServerWorld world, Vec3d at) {
		for (int i = 0; i < 24; i++) {
			double a = i * 0.8;
			double h = i / 24.0 * 2.0;
			world.spawnParticles(SPIRIT, at.x + Math.cos(a) * 0.5, at.y + h, at.z + Math.sin(a) * 0.5, 1, 0, 0, 0, 0);
		}
		world.spawnParticles(ParticleTypes.SOUL, at.x, at.y + 0.5, at.z, 12, 0.3, 0.3, 0.3, 0.02);
	}

	/** An echo fades: a wisp of souls, then nothing. */
	public static void fade(Entity entity) {
		vanish(entity);
		ACTIVE.remove(entity.getUuid());
	}

	/** The fading itself, without touching the records (for loops over them). */
	private static void vanish(Entity entity) {
		if (entity.getWorld() instanceof ServerWorld world) {
			world.spawnParticles(ParticleTypes.SOUL, entity.getX(), entity.getY() + 1, entity.getZ(), 16, 0.3, 0.5, 0.3, 0.03);
			world.spawnParticles(SPIRIT, entity.getX(), entity.getY() + 1, entity.getZ(), 20, 0.3, 0.6, 0.3, 0);
			world.playSound(null, entity.getBlockPos(), SoundEvents.PARTICLE_SOUL_ESCAPE, SoundCategory.NEUTRAL, 1.0f, 1.2f);
		}
		entity.discard();
	}

	/** Sends all of this Shepherd's echoes back. */
	public static int dismiss(MinecraftServer server, UUID owner) {
		int count = 0;
		for (Iterator<Map.Entry<UUID, Echo>> it = ACTIVE.entrySet().iterator(); it.hasNext(); ) {
			Map.Entry<UUID, Echo> e = it.next();
			if (!e.getValue().owner().equals(owner)) {
				continue;
			}
			ServerWorld world = server.getWorld(e.getValue().world());
			Entity entity = world == null ? null : world.getEntity(e.getKey());
			it.remove();
			if (entity != null) {
				vanish(entity);
			}
			count++;
		}
		return count;
	}

	/**
	 * An echo would die: it fades instead, so it drops nothing. With Return, half its Call comes back to the
	 * Shepherd. Returns false to cancel the death.
	 */
	public static boolean allowDeath(Entity entity) {
		Echo echo = ACTIVE.get(entity.getUuid());
		if (echo == null) {
			return true;
		}
		ServerPlayerEntity owner = entity.getServer() == null ? null : entity.getServer().getPlayerManager().getPlayer(echo.owner());
		RaceData data = owner == null ? null : RaceState.of(owner);
		if (data != null && data.has(Talents.LEGION_RETURN) && !echo.temporary()) {
			data.addResource(echo.voice() / 2f);
		}
		fade(entity);
		return false;
	}

	/** A mob with the echo tag came out of a saved chunk: its Shepherd's session is over, so it goes. */
	public static void onLoad(Entity entity) {
		if (entity.getCommandTags().contains(TAG) && !ACTIVE.containsKey(entity.getUuid())) {
			entity.discard();
		}
	}

	/** Once a second: dawn, Shepherds who left, echoes left behind; and a faint aura. */
	public static void tickSecond(MinecraftServer server) {
		List<UUID> gone = new ArrayList<>();
		for (Map.Entry<UUID, Echo> e : ACTIVE.entrySet()) {
			Echo echo = e.getValue();
			ServerWorld world = server.getWorld(echo.world());
			Entity entity = world == null ? null : world.getEntity(e.getKey());
			ServerPlayerEntity owner = server.getPlayerManager().getPlayer(echo.owner());
			if (entity == null || !entity.isAlive()) {
				gone.add(e.getKey());
				continue;
			}
			if (owner == null || world.getTimeOfDay() >= echo.expiresAt()) {
				gone.add(e.getKey());
				vanish(entity);
				continue;
			}
			if (owner.getServerWorld() != world || owner.squaredDistanceTo(entity) > 64 * 64) {
				// Left behind in another world or far away: it answers again at its Shepherd's side
				if (owner.getServerWorld() == world) {
					Vec3d spot = Creatures.near(owner);
					entity.refreshPositionAndAngles(spot.x, spot.y, spot.z, entity.getYaw(), entity.getPitch());
				} else {
					gone.add(e.getKey());
					vanish(entity);
				}
				continue;
			}
			world.spawnParticles(SPIRIT, entity.getX(), entity.getY() + entity.getHeight() * 0.6, entity.getZ(), 3,
					entity.getWidth() * 0.4, entity.getHeight() * 0.3, entity.getWidth() * 0.4, 0);
			if (entity instanceof MobEntity mob) {
				aura(echo, mob, owner);
			}
		}
		gone.forEach(ACTIVE::remove);
		long ticks = server.getTicks();
		WEBS.removeIf(web -> {
			if (web.until() > ticks) {
				return false;
			}
			if (web.world().getBlockState(web.pos()).isOf(Blocks.COBWEB)) {
				web.world().removeBlock(web.pos(), false);
			}
			return true;
		});
		broadcast(server);
	}

	/** Tells every player which entities in their world are echoes (the client draws them see-through and pale). */
	public static void broadcast(MinecraftServer server) {
		for (ServerPlayerEntity player : server.getPlayerManager().getPlayerList()) {
			if (!ServerPlayNetworking.canSend(player, RacePackets.ECHOES)) {
				continue;
			}
			List<Integer> ids = new ArrayList<>();
			for (Map.Entry<UUID, Echo> e : ACTIVE.entrySet()) {
				if (e.getValue().world().equals(player.getWorld().getRegistryKey())) {
					Entity entity = player.getServerWorld().getEntity(e.getKey());
					if (entity != null) {
						ids.add(entity.getId());
					}
				}
			}
			PacketByteBuf buf = PacketByteBufs.create();
			buf.writeVarInt(ids.size());
			ids.forEach(buf::writeVarInt);
			ServerPlayNetworking.send(player, RacePackets.ECHOES, buf);
		}
	}
}
