package euf.mageslegacy.masked;

import euf.mageslegacy.fx.Fx;
import euf.mageslegacy.net.RacePackets;
import euf.mageslegacy.race.AbilityResult;
import euf.mageslegacy.race.Progression;
import euf.mageslegacy.race.Race;
import euf.mageslegacy.race.RaceData;
import euf.mageslegacy.race.RaceState;
import euf.mageslegacy.race.Talents;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import net.fabricmc.fabric.api.networking.v1.PacketByteBufs;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.attribute.EntityAttribute;
import net.minecraft.entity.attribute.EntityAttributeInstance;
import net.minecraft.entity.attribute.EntityAttributeModifier;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.damage.DamageTypes;
import net.minecraft.entity.effect.StatusEffect;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.entity.mob.EvokerFangsEntity;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.entity.mob.Monster;
import net.minecraft.entity.passive.CatEntity;
import net.minecraft.entity.passive.IronGolemEntity;
import net.minecraft.entity.passive.OcelotEntity;
import net.minecraft.entity.passive.TameableEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.projectile.ArrowEntity;
import net.minecraft.entity.projectile.PersistentProjectileEntity;
import net.minecraft.entity.projectile.ProjectileUtil;
import net.minecraft.entity.projectile.TridentEntity;
import net.minecraft.entity.projectile.thrown.PotionEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtList;
import net.minecraft.nbt.NbtString;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.particle.ItemStackParticleEffect;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.potion.PotionUtil;
import net.minecraft.registry.tag.DamageTypeTags;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.EntityHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;

/**
 * The Masked: the nameless mage gave them no face of their own, only the knack of taking others'. The Tearing (R)
 * takes the face off a weakened monster and puts it on: the wearer takes its shape, its gift and its weakness, and its
 * kind takes the wearer for one of its own. The face's Power (V) is that monster's own trick; the Change (Z) goes on
 * to the next face of the gallery; Kin (H) makes the wearer's kind a retinue; the Thousand Faces (Alt, crown) wears
 * them all at once. A worn face wears out, and without one the Masked are weaker than a plain human.
 */
public final class Masked {
	/** Pale bone of the faces, laid on as real colour (see {@link Fx#INK}) under a sickly green glint. */
	public static final int BONE_RGB = 0xE8DCC0;
	public static final int GLINT_RGB = 0xB8E07A;
	private static final int BONE_INK = Fx.INK | 0x3A332A;

	public static final int SWAP_COST = 10;
	public static final int SWAP_COST_CHEAP = 5;
	public static final int KIN_COST = 40;
	public static final int KIN_COST_KING = 25;
	public static final int THOUSAND_COST = 50;
	private static final int RIP_COOLDOWN = 60;
	private static final int SWAP_COOLDOWN = 40;
	private static final int SWAP_COOLDOWN_CHEAP = 20;
	private static final int KIN_COOLDOWN = 600;
	private static final int THOUSAND_COOLDOWN = 6000;
	private static final int THOUSAND_TICKS = 400;
	private static final int LAST_SLOT = 9;
	private static final int LAST_COOLDOWN = 24000;
	/** A worn face lasts ten minutes (fifteen with Long Wear); the gallery's others keep. */
	private static final float WEAR_PER_SECOND = 100f / 600;
	private static final float WEAR_PER_SECOND_LONG = 100f / 900;
	/** A face comes off a monster with at most this much of its life left (Weak Spot: more). */
	private static final float RIP_AT = 0.35f;
	private static final float RIP_AT_WEAK = 0.45f;

	private static final UUID FACELESS_HEALTH = UUID.fromString("5f1d6d0a-4b0e-4a7e-9c55-0d3c6b1e7a01");
	private static final UUID ZOMBIE_HEALTH = UUID.fromString("5f1d6d0a-4b0e-4a7e-9c55-0d3c6b1e7a02");
	private static final UUID WITCH_HEALTH = UUID.fromString("5f1d6d0a-4b0e-4a7e-9c55-0d3c6b1e7a03");
	private static final UUID WITHER_SPEED = UUID.fromString("5f1d6d0a-4b0e-4a7e-9c55-0d3c6b1e7a04");

	/** Monsters of the wearer's kind that follow it for a while (Kin). */
	private record Retinue(UUID owner, long until) {}

	/** A face still felt for a while after the Change (Lingering Trace). */
	private record Trace(Face face, long until) {}

	/** Blows charged by the wither skeleton's Black Blade. */
	private record Blade(int charges, long until) {}

	/** Until when each Masked's kind sees through the face (it struck one of them). */
	private static final Map<UUID, Long> EXPOSED = new HashMap<>();
	private static final Map<UUID, Retinue> RETINUE = new HashMap<>();
	private static final Map<UUID, Long> THOUSAND = new HashMap<>();
	private static final Map<UUID, Trace> TRACE = new HashMap<>();
	private static final Map<UUID, Blade> BLADE = new HashMap<>();
	/** A face was put on or taken off since the last time everyone was told who wears what. */
	private static boolean facesChanged;

	private Masked() {
	}

	public static void reset() {
		EXPOSED.clear();
		RETINUE.clear();
		THOUSAND.clear();
		TRACE.clear();
		BLADE.clear();
		facesChanged = true;
	}

	private static long now(MinecraftServer server) {
		return server.getTicks();
	}

	private static boolean ready(ServerPlayerEntity player, RaceData data, int slot) {
		return data.readyAt.getOrDefault(slot, 0L) <= now(player.getServer());
	}

	private static void cooldown(ServerPlayerEntity player, RaceData data, int slot, int ticks) {
		data.readyAt.put(slot, now(player.getServer()) + ticks);
		data.dirty = true;
	}

	private static RaceData masked(PlayerEntity player) {
		if (!(player instanceof ServerPlayerEntity sp)) {
			return null;
		}
		RaceData data = RaceState.of(sp);
		return data != null && data.race == Race.MASKED ? data : null;
	}

	public static boolean thousand(ServerPlayerEntity player) {
		return THOUSAND.getOrDefault(player.getUuid(), 0L) > now(player.getServer());
	}

	/** The faces whose gifts the Masked has now: the worn one, the whole gallery under the Thousand Faces, a lingering trace. */
	public static Set<Face> active(ServerPlayerEntity player, RaceData data) {
		Set<Face> faces = EnumSet.noneOf(Face.class);
		if (data.masked.worn != null) {
			faces.add(data.masked.worn);
		}
		if (thousand(player)) {
			faces.addAll(data.masked.gallery.keySet());
		}
		Trace trace = TRACE.get(player.getUuid());
		if (trace != null && trace.until() > now(player.getServer()) && data.masked.gallery.containsKey(trace.face())) {
			faces.add(trace.face());
		}
		return faces;
	}

	public static boolean wears(PlayerEntity player, Face face) {
		RaceData data = masked(player);
		return data != null && active((ServerPlayerEntity) player, data).contains(face);
	}

	public static int gallerySize(RaceData data) {
		return 3 + (data.has(Talents.GAL_SLOTS) ? 1 : 0) + (data.has(Talents.GAL_MORE) ? 1 : 0);
	}

	public static int swapCost(RaceData data) {
		return data.has(Talents.GAL_CHEAP) ? SWAP_COST_CHEAP : SWAP_COST;
	}

	public static int kinCost(RaceData data) {
		return data.has(Talents.KIN_KING) ? KIN_COST_KING : KIN_COST;
	}

	public static int powerCost(ServerPlayerEntity player, RaceData data) {
		return data.masked.worn == null ? 0 : thousand(player) ? 0 : data.masked.worn.cost;
	}

	// ---- the disguise ----

	/**
	 * Whether a monster leaves this player be: its own retinue never turns on it; the kind of a face it has on takes it
	 * for one of its own (unless it struck one of them lately); with Own Scent, other monsters notice it only closer.
	 */
	public static boolean hidesFrom(MobEntity mob, PlayerEntity target) {
		RaceData data = masked(target);
		if (data == null) {
			return false;
		}
		ServerPlayerEntity player = (ServerPlayerEntity) target;
		Retinue retinue = RETINUE.get(mob.getUuid());
		if (retinue != null && retinue.owner().equals(player.getUuid())) {
			return true;
		}
		if (disguisedFrom(player, data, mob)) {
			return true;
		}
		if (data.has(Talents.KIN_SCENT) && data.masked.worn != null) {
			double range = mob.getAttributeValue(EntityAttributes.GENERIC_FOLLOW_RANGE) * 0.6;
			return mob.squaredDistanceTo(player) > range * range;
		}
		return false;
	}

	private static boolean disguisedFrom(ServerPlayerEntity player, RaceData data, MobEntity mob) {
		if (EXPOSED.getOrDefault(player.getUuid(), 0L) > now(player.getServer())) {
			return false;
		}
		Set<Face> faces = data.has(Talents.KIN_ALL) ? data.masked.gallery.keySet() : active(player, data);
		for (Face face : faces) {
			if (face.matches(mob.getType())) {
				return true;
			}
		}
		return false;
	}

	/** Endermen take an enderman's face for one of their own: meeting its eyes doesn't anger them. */
	public static boolean kin(PlayerEntity player) {
		return wears(player, Face.ENDERMAN);
	}

	/** The spider's face climbs walls (the client asks about its own player, see {@code ClientRaceView}). */
	public static boolean climbs(PlayerEntity player) {
		return wears(player, Face.SPIDER);
	}

	/** Monsters of the worn kind near the Masked that are after it now forget it. */
	private static void calmKin(ServerPlayerEntity player, RaceData data) {
		for (MobEntity m : player.getServerWorld().getEntitiesByClass(MobEntity.class, player.getBoundingBox().expand(32),
				m -> m.isAlive() && m.getTarget() == player)) {
			if (hidesFrom(m, player)) {
				m.setTarget(null);
			}
		}
	}

	// ---- once a second ----

	public static void tickSecond(ServerPlayerEntity player, RaceData data) {
		MaskData mask = data.masked;
		Face worn = mask.worn;
		if (worn != null) {
			// The face wears out while it's on; Kept Faces wear half as fast
			float wear = data.has(Talents.GAL_LONG) ? WEAR_PER_SECOND_LONG : WEAR_PER_SECOND;
			if (data.has(Talents.GAL_KEEP)) {
				wear /= 2;
			}
			float fresh = mask.gallery.getOrDefault(worn, 0f) - wear;
			if (fresh <= 0) {
				crumble(player, data, worn);
				worn = null;
			} else {
				mask.gallery.put(worn, fresh);
			}
		}
		// Essence flows while a face is on, more with a full gallery (Collector's Essence); none when faceless
		if (worn != null) {
			data.addResource(0.4f + (data.has(Talents.GAL_ESSENCE) ? 0.15f * mask.gallery.size() : 0));
		}
		data.dirty = true;

		Set<Face> faces = active(player, data);
		// The gifts
		modifier(player, EntityAttributes.GENERIC_MAX_HEALTH, ZOMBIE_HEALTH, "masked_zombie", 4, faces.contains(Face.ZOMBIE));
		if (faces.contains(Face.DROWNED) && player.isTouchingWater()) {
			refresh(player, StatusEffects.WATER_BREATHING, 0);
			refresh(player, StatusEffects.DOLPHINS_GRACE, 0);
		}
		if (faces.contains(Face.SPIDER)) {
			player.addStatusEffect(new StatusEffectInstance(StatusEffects.NIGHT_VISION, 260, 0, true, false, true));
		}
		if (faces.contains(Face.BLAZE)) {
			refresh(player, StatusEffects.FIRE_RESISTANCE, 0);
		}
		if (faces.contains(Face.WITCH)) {
			player.removeStatusEffect(StatusEffects.POISON);
		}
		if (faces.contains(Face.WITHER_SKELETON)) {
			player.removeStatusEffect(StatusEffects.WITHER);
		}

		// The weaknesses: only the worn face's, and Borrowed Skin softens them
		boolean soft = data.has(Talents.KIN_SKIN);
		ServerWorld world = player.getServerWorld();
		boolean sun = world.isDay() && !world.isRaining() && world.isSkyVisible(BlockPos.ofFloored(player.getEyePos()))
				&& player.getEquippedStack(EquipmentSlot.HEAD).isEmpty() && !player.isTouchingWaterOrRain();
		if (sun && (worn == Face.ZOMBIE || worn == Face.SKELETON || worn == Face.DROWNED || worn == Face.PHANTOM)) {
			if (!soft || player.getRandom().nextBoolean()) {
				player.setOnFireFor(soft ? 2 : 4);
			}
		}
		if ((worn == Face.ENDERMAN || worn == Face.BLAZE) && player.isTouchingWaterOrRain()) {
			player.damage(world.getDamageSources().drown(), soft ? 0.5f : 1.0f);
		}
		if (worn == Face.CREEPER && !world.getEntitiesByClass(LivingEntity.class, player.getBoundingBox().expand(soft ? 5 : 8),
				e -> e.isAlive() && (e instanceof CatEntity || e instanceof OcelotEntity)).isEmpty()) {
			refresh(player, StatusEffects.WEAKNESS, soft ? 0 : 1);
			refresh(player, StatusEffects.SLOWNESS, 0);
		}
		modifier(player, EntityAttributes.GENERIC_MAX_HEALTH, WITCH_HEALTH, "masked_witch", soft ? -2 : -4, worn == Face.WITCH);
		modifierMultiply(player, EntityAttributes.GENERIC_MOVEMENT_SPEED, WITHER_SPEED, "masked_wither", soft ? -0.05 : -0.1, worn == Face.WITHER_SKELETON);
		if (worn == Face.ILLAGER) {
			for (IronGolemEntity golem : world.getEntitiesByClass(IronGolemEntity.class, player.getBoundingBox().expand(16),
					g -> g.isAlive() && !g.isPlayerCreated() && g.getTarget() == null)) {
				golem.setTarget(player);
			}
		}
		// Faceless: weaker than a plain human (Bare Bone softens it)
		modifier(player, EntityAttributes.GENERIC_MAX_HEALTH, FACELESS_HEALTH, "masked_faceless", data.has(Talents.RIP_FACELESS) ? -2 : -4, worn == null);

		calmKin(player, data);
	}

	private static void crumble(ServerPlayerEntity player, RaceData data, Face face) {
		data.masked.gallery.remove(face);
		data.masked.worn = null;
		facesChanged = true;
		ServerWorld world = player.getServerWorld();
		Fx.burst(world, player.getEyePos(), 1.2f, 10, BONE_RGB);
		world.spawnParticles(ParticleTypes.ASH, player.getX(), player.getEyeY(), player.getZ(), 20, 0.3, 0.3, 0.3, 0.02);
		world.playSound(null, player.getBlockPos(), SoundEvents.BLOCK_SAND_BREAK, SoundCategory.PLAYERS, 1.0f, 0.6f);
		player.sendMessage(Text.translatable("mages_legacy.masked.crumbled", Text.translatable("mages_legacy.face." + face.id)).formatted(Formatting.GRAY), false);
	}

	private static void refresh(ServerPlayerEntity player, StatusEffect effect, int amplifier) {
		StatusEffectInstance current = player.getStatusEffect(effect);
		if (current == null || current.getDuration() <= 30) {
			player.addStatusEffect(new StatusEffectInstance(effect, 60, amplifier, true, false, true));
		}
	}

	private static void modifier(ServerPlayerEntity player, EntityAttribute attribute, UUID id, String name, double amount, boolean want) {
		set(player, attribute, id, name, amount, EntityAttributeModifier.Operation.ADDITION, want);
	}

	private static void modifierMultiply(ServerPlayerEntity player, EntityAttribute attribute, UUID id, String name, double amount, boolean want) {
		set(player, attribute, id, name, amount, EntityAttributeModifier.Operation.MULTIPLY_BASE, want);
	}

	/** Adds or removes an attribute change so it matches whether it applies right now (and its amount). */
	private static void set(ServerPlayerEntity player, EntityAttribute attribute, UUID id, String name, double amount,
			EntityAttributeModifier.Operation operation, boolean want) {
		EntityAttributeInstance instance = player.getAttributeInstance(attribute);
		if (instance == null) {
			return;
		}
		EntityAttributeModifier current = instance.getModifier(id);
		if (want == (current != null) && (current == null || current.getValue() == amount)) {
			return;
		}
		if (current != null) {
			instance.removeModifier(id);
		}
		if (want) {
			instance.addTemporaryModifier(new EntityAttributeModifier(id, name, amount, operation));
		}
		player.setHealth(Math.min(player.getHealth(), player.getMaxHealth()));
	}

	// ---- every tick ----

	public static void tick(MinecraftServer server) {
		long now = now(server);
		EXPOSED.values().removeIf(until -> until < now);
		THOUSAND.values().removeIf(until -> until < now);
		TRACE.values().removeIf(t -> t.until() < now);
		BLADE.values().removeIf(b -> b.until() < now || b.charges() <= 0);
		RETINUE.values().removeIf(r -> r.until() < now);
		for (ServerPlayerEntity player : server.getPlayerManager().getPlayerList()) {
			// The phantom's face: held crouching in the air, it glides down
			if (player.isSneaking() && !player.isOnGround() && player.getVelocity().y < 0 && wears(player, Face.PHANTOM)) {
				player.addStatusEffect(new StatusEffectInstance(StatusEffects.SLOW_FALLING, 10, 0, true, false, false));
			}
		}
		if (now % 10 == 0) {
			leadRetinues(server);
		}
		if (facesChanged || now % 40 == 0) {
			facesChanged = false;
			broadcast(server);
		}
	}

	/** The retinue fights what its Masked fights, or what's after it, and keeps close. */
	private static void leadRetinues(MinecraftServer server) {
		for (ServerWorld world : server.getWorlds()) {
			for (Map.Entry<UUID, Retinue> e : RETINUE.entrySet()) {
				if (!(world.getEntity(e.getKey()) instanceof MobEntity mob) || !mob.isAlive()) {
					continue;
				}
				ServerPlayerEntity owner = server.getPlayerManager().getPlayer(e.getValue().owner());
				if (owner == null || owner.getWorld() != world) {
					continue;
				}
				LivingEntity target = foe(owner, mob);
				if (target != null) {
					mob.setTarget(target);
				} else {
					if (mob.getTarget() instanceof PlayerEntity) {
						mob.setTarget(null);
					}
					if (mob.squaredDistanceTo(owner) > 64) {
						mob.getNavigation().startMovingTo(owner, 1.2);
					}
				}
			}
		}
	}

	private static LivingEntity foe(ServerPlayerEntity owner, MobEntity mob) {
		LivingEntity struck = owner.getAttacking();
		if (struck != null && struck.isAlive() && !(struck instanceof PlayerEntity) && owner.age - owner.getLastAttackTime() < 100
				&& !RETINUE.containsKey(struck.getUuid()) && struck != mob) {
			return struck;
		}
		LivingEntity attacker = owner.getAttacker();
		if (attacker != null && attacker.isAlive() && !(attacker instanceof PlayerEntity) && !RETINUE.containsKey(attacker.getUuid()) && attacker != mob) {
			return attacker;
		}
		return owner.getServerWorld().getEntitiesByClass(MobEntity.class, owner.getBoundingBox().expand(12),
				m -> m.isAlive() && m != mob && m instanceof Monster && m.getTarget() == owner && !RETINUE.containsKey(m.getUuid())).stream()
				.min(Comparator.comparingDouble(m -> m.squaredDistanceTo(mob))).orElse(null);
	}

	/** Tells every player who in their world wears which face, so the client draws the wearer in that shape. */
	public static void broadcast(MinecraftServer server) {
		List<ServerPlayerEntity> players = server.getPlayerManager().getPlayerList();
		for (ServerPlayerEntity viewer : players) {
			if (!ServerPlayNetworking.canSend(viewer, RacePackets.FACES)) {
				continue;
			}
			List<int[]> faces = new ArrayList<>();
			for (ServerPlayerEntity player : players) {
				RaceData data = masked(player);
				if (data != null && data.masked.worn != null && player.getWorld() == viewer.getWorld()) {
					faces.add(new int[] {player.getId(), data.masked.worn.ordinal()});
				}
			}
			PacketByteBuf buf = PacketByteBufs.create();
			buf.writeVarInt(faces.size());
			for (int[] f : faces) {
				buf.writeVarInt(f[0]);
				buf.writeVarInt(f[1]);
			}
			ServerPlayNetworking.send(viewer, RacePackets.FACES, buf);
		}
	}

	// ---- abilities ----

	public static AbilityResult use(ServerPlayerEntity player, RaceData data, int slot) {
		return switch (slot) {
			case 0 -> tear(player, data);
			case 1 -> power(player, data);
			case 2 -> change(player, data);
			case 3 -> kinCall(player, data);
			case 4 -> data.has(Talents.GAL_THOUSAND) ? thousandFaces(player, data) : RaceData.talentLocked(player, Talents.GAL_THOUSAND);
			default -> AbilityResult.LOCKED;
		};
	}

	public static double ripReach(RaceData data) {
		return data.has(Talents.RIP_REACH) ? 8 : 5;
	}

	/**
	 * The Tearing (R): the face of the weakened monster you look at comes off in your hand and goes on at once. The
	 * monster dies of it; the face joins the gallery (the oldest is let go when it's full) and is fresh again if it was
	 * there already. With Tear Open, a monster not yet weak enough is torn at instead.
	 */
	public static AbilityResult tear(ServerPlayerEntity player, RaceData data) {
		if (!ready(player, data, 0)) {
			return AbilityResult.COOLDOWN;
		}
		LivingEntity target = lookedAt(player, ripReach(data), e -> e instanceof Monster);
		if (target == null) {
			return AbilityResult.NO_SIGHT;
		}
		Face face = Face.of(target.getType()).orElse(null);
		ServerWorld world = player.getServerWorld();
		float threshold = data.has(Talents.RIP_WEAK) ? RIP_AT_WEAK : RIP_AT;
		if (face == null || target.getHealth() > target.getMaxHealth() * threshold) {
			if (data.has(Talents.RIP_TEAR)) {
				// Tear Open: no face yet, but a wound
				cooldown(player, data, 0, RIP_COOLDOWN);
				target.damage(world.getDamageSources().indirectMagic(player, player), data.has(Talents.RIP_EXECUTE) ? 8 : 4);
				if (data.has(Talents.RIP_EXECUTE)) {
					target.addStatusEffect(new StatusEffectInstance(StatusEffects.WITHER, 100, 1));
				}
				Fx.slash(world, target.getPos().add(0, target.getHeight() * 0.7, 0), target.getPos().subtract(player.getPos()), 0.8f + target.getWidth(), 30, 8, BONE_INK);
				world.playSound(null, target.getBlockPos(), SoundEvents.ENTITY_SHEEP_SHEAR, SoundCategory.PLAYERS, 1.0f, 0.6f);
				return AbilityResult.OK;
			}
			player.sendMessage(Text.translatable(face == null ? "mages_legacy.masked.no_face" : "mages_legacy.masked.too_strong",
					Math.round(threshold * 100)), true);
			return AbilityResult.HINTED;
		}
		cooldown(player, data, 0, RIP_COOLDOWN);
		Vec3d at = target.getPos().add(0, target.getHeight() * 0.8, 0);
		target.damage(world.getDamageSources().indirectMagic(player, player), Float.MAX_VALUE);

		MaskData mask = data.masked;
		boolean known = mask.gallery.containsKey(face);
		if (!known && mask.gallery.size() >= gallerySize(data)) {
			// A full gallery lets go of its oldest face that isn't worn
			mask.gallery.keySet().stream().filter(f -> f != mask.worn).findFirst().ifPresent(mask.gallery::remove);
		}
		mask.gallery.remove(face);
		mask.gallery.put(face, 100f);
		data.addResource(data.has(Talents.RIP_FEED) ? 40 : 25);
		wear(player, data, face);
		if (!known) {
			// A new face: what it gives, what it costs and what it can do
			player.sendMessage(Text.translatable("mages_legacy.face." + face.id + ".desc").formatted(Formatting.GRAY), false);
		}
		if (data.has(Talents.RIP_HEAL)) {
			player.heal(4);
		}
		if (data.has(Talents.RIP_ARMOR)) {
			player.setAbsorptionAmount(Math.min(8, player.getAbsorptionAmount() + 4));
		}
		if (data.has(Talents.RIP_SHOCK)) {
			for (MobEntity m : world.getEntitiesByClass(MobEntity.class, player.getBoundingBox().expand(8), m -> m.isAlive() && face.matches(m.getType()))) {
				m.addStatusEffect(new StatusEffectInstance(StatusEffects.WEAKNESS, 100, 0));
				m.addStatusEffect(new StatusEffectInstance(StatusEffects.SLOWNESS, 100, 1));
			}
		}
		if (data.has(Talents.RIP_CHAIN)) {
			data.readyAt.put(1, 0L);
		}
		Progression.addXp(player, data, "masked_rip", 3, 60);
		// The face torn off: a pale slash across where it was, bone dust, and the face flies to the Masked
		Fx.slash(world, at, at.subtract(player.getEyePos()), 1.0f + target.getWidth(), 20, 10, BONE_INK);
		Fx.burst(world, at, 1.0f + target.getWidth(), 8, BONE_RGB);
		Fx.ribbon(world, List.of(at, player.getEyePos()), 10, BONE_INK, GLINT_RGB, 0.5f);
		world.spawnParticles(ParticleTypes.WHITE_ASH, at.x, at.y, at.z, 20, 0.3, 0.3, 0.3, 0.02);
		world.playSound(null, target.getBlockPos(), SoundEvents.ENTITY_SHEEP_SHEAR, SoundCategory.PLAYERS, 1.2f, 0.5f);
		world.playSound(null, player.getBlockPos(), SoundEvents.ITEM_ARMOR_EQUIP_LEATHER, SoundCategory.PLAYERS, 1.0f, 0.7f);
		return AbilityResult.OK;
	}

	/** Puts a face of the gallery on: the shape changes, its kind calms down. */
	private static void wear(ServerPlayerEntity player, RaceData data, Face face) {
		data.masked.worn = face;
		data.dirty = true;
		facesChanged = true;
		ServerWorld world = player.getServerWorld();
		// The face wraps around the wearer: bone strands closing in, a pale flash
		Fx.helix(world, player, 0.9f, 2.1f, 3, true, 14, BONE_RGB);
		Fx.burst(world, player.getEyePos(), 1.4f, 8, BONE_INK);
		Fx.screen(player, 10, BONE_RGB);
		world.playSound(null, player.getBlockPos(), SoundEvents.ENTITY_ILLUSIONER_MIRROR_MOVE, SoundCategory.PLAYERS, 1.0f, 0.8f);
		calmKin(player, data);
	}

	/** The Change (Z): the next face of the gallery goes on. */
	public static AbilityResult change(ServerPlayerEntity player, RaceData data) {
		if (!ready(player, data, 2)) {
			return AbilityResult.COOLDOWN;
		}
		MaskData mask = data.masked;
		Face next = next(mask);
		if (next == null || next == mask.worn) {
			player.sendMessage(Text.translatable("mages_legacy.masked.one_face"), true);
			return AbilityResult.HINTED;
		}
		int cost = swapCost(data);
		if (data.resource < cost) {
			return AbilityResult.NO_RESOURCE;
		}
		data.addResource(-cost);
		cooldown(player, data, 2, data.has(Talents.GAL_CHEAP) ? SWAP_COOLDOWN_CHEAP : SWAP_COOLDOWN);
		long now = now(player.getServer());
		if (data.has(Talents.GAL_TRACE) && mask.worn != null) {
			TRACE.put(player.getUuid(), new Trace(mask.worn, now + 200));
		}
		ServerWorld world = player.getServerWorld();
		// The old face left standing a moment where the wearer was
		Fx.ghosts(world, List.of(player.getPos(), player.getPos()), player.getYaw(), 1, 16, BONE_INK);
		wear(player, data, next);
		if (data.has(Talents.GAL_QUICK)) {
			player.addStatusEffect(new StatusEffectInstance(StatusEffects.SPEED, 60, 1, true, true, true));
			for (MobEntity m : world.getEntitiesByClass(MobEntity.class, player.getBoundingBox().expand(8), m -> m.isAlive() && m.getTarget() == player)) {
				m.setTarget(null);
			}
		}
		player.sendMessage(Text.translatable("mages_legacy.masked.now", Text.translatable("mages_legacy.face." + next.id)).formatted(Formatting.GRAY), true);
		return AbilityResult.OK;
	}

	/** The face after the worn one in the gallery (the first one when faceless), or null with an empty gallery. */
	public static Face next(MaskData mask) {
		List<Face> faces = new ArrayList<>(mask.gallery.keySet());
		if (faces.isEmpty()) {
			return null;
		}
		int at = mask.worn == null ? -1 : faces.indexOf(mask.worn);
		return faces.get((at + 1) % faces.size());
	}

	/** The face's Power (V): the worn monster's own trick. */
	public static AbilityResult power(ServerPlayerEntity player, RaceData data) {
		Face face = data.masked.worn;
		if (face == null) {
			player.sendMessage(Text.translatable("mages_legacy.masked.faceless"), true);
			return AbilityResult.HINTED;
		}
		if (!ready(player, data, 1)) {
			return AbilityResult.COOLDOWN;
		}
		int cost = powerCost(player, data);
		if (data.resource < cost) {
			return AbilityResult.NO_RESOURCE;
		}
		float m = data.has(Talents.GAL_POWER) ? 1.25f : 1.0f;
		AbilityResult result = switch (face) {
			case ZOMBIE -> grip(player, m);
			case SKELETON -> volley(player, m);
			case DROWNED -> trident(player, m);
			case CREEPER -> blast(player, m);
			case SPIDER -> web(player, m);
			case ENDERMAN -> step(player);
			case BLAZE -> flame(player, m);
			case WITCH -> brew(player, m);
			case WITHER_SKELETON -> blade(player);
			case PHANTOM -> rise(player, m);
			case ILLAGER -> fangs(player, m);
		};
		if (result != AbilityResult.OK) {
			return result;
		}
		data.addResource(-cost);
		cooldown(player, data, 1, thousand(player) ? face.cooldown / 2 : face.cooldown);
		if (!player.getServerWorld().getEntitiesByClass(MobEntity.class, player.getBoundingBox().expand(16), m2 -> m2.isAlive() && m2 instanceof Monster).isEmpty()) {
			Progression.addXp(player, data, "masked_power", 1, 20);
		}
		return AbilityResult.OK;
	}

	private static LivingEntity foeInSight(ServerPlayerEntity player, double range) {
		return lookedAt(player, range, e -> !(e instanceof PlayerEntity) && !(e instanceof TameableEntity tame && tame.isTamed()) && !RETINUE.containsKey(e.getUuid()));
	}

	/** Zombie, the Dead Grip: 5 damage, held fast for three seconds, and 3 of it back as life. */
	private static AbilityResult grip(ServerPlayerEntity player, float m) {
		LivingEntity target = foeInSight(player, 5);
		if (target == null) {
			return AbilityResult.NO_SIGHT;
		}
		ServerWorld world = player.getServerWorld();
		target.damage(world.getDamageSources().indirectMagic(player, player), 5 * m);
		target.addStatusEffect(new StatusEffectInstance(StatusEffects.SLOWNESS, 60, 3));
		player.heal(3);
		Fx.tendril(world, player, target, 12, BONE_INK, GLINT_RGB);
		Fx.burst(world, target.getPos().add(0, target.getHeight() * 0.6, 0), 0.8f + target.getWidth(), 8, GLINT_RGB);
		world.playSound(null, target.getBlockPos(), SoundEvents.ENTITY_ZOMBIE_ATTACK_WOODEN_DOOR, SoundCategory.PLAYERS, 0.8f, 0.8f);
		return AbilityResult.OK;
	}

	/** Skeleton, the Volley: five arrows in a fan. */
	private static AbilityResult volley(ServerPlayerEntity player, float m) {
		ServerWorld world = player.getServerWorld();
		for (int i = -2; i <= 2; i++) {
			ArrowEntity arrow = new ArrowEntity(world, player);
			arrow.setVelocity(player, player.getPitch(), player.getYaw() + i * 6, 0, 2.8f, 1.0f);
			arrow.setDamage(1.2 * m);
			arrow.pickupType = PersistentProjectileEntity.PickupPermission.CREATIVE_ONLY;
			world.spawnEntity(arrow);
		}
		Fx.ring(world, player.getEyePos().add(player.getRotationVec(1.0f)), 0.2f, 1.5f, 8, BONE_RGB);
		world.playSound(null, player.getBlockPos(), SoundEvents.ENTITY_SKELETON_SHOOT, SoundCategory.PLAYERS, 1.2f, 0.8f);
		return AbilityResult.OK;
	}

	/** Drowned, the Trident: one thrown hard, gone when it has struck. */
	private static AbilityResult trident(ServerPlayerEntity player, float m) {
		ServerWorld world = player.getServerWorld();
		TridentEntity trident = new TridentEntity(world, player, new ItemStack(Items.TRIDENT));
		trident.setVelocity(player, player.getPitch(), player.getYaw(), 0, 2.5f * Math.min(1.2f, m), 0.5f);
		trident.pickupType = PersistentProjectileEntity.PickupPermission.CREATIVE_ONLY;
		world.spawnEntity(trident);
		world.playSound(null, player.getBlockPos(), SoundEvents.ENTITY_DROWNED_SHOOT, SoundCategory.PLAYERS, 1.0f, 1.0f);
		return AbilityResult.OK;
	}

	/** Creeper, the Blast: everything within 5 blocks takes up to 10, the Masked itself 4 (its own face halves that). */
	private static AbilityResult blast(ServerPlayerEntity player, float m) {
		ServerWorld world = player.getServerWorld();
		Vec3d at = player.getPos();
		for (LivingEntity e : world.getEntitiesByClass(LivingEntity.class, player.getBoundingBox().expand(5),
				e -> e != player && e.isAlive() && !(e instanceof PlayerEntity) && !RETINUE.containsKey(e.getUuid()))) {
			double d = Math.sqrt(e.squaredDistanceTo(at));
			if (d > 5) {
				continue;
			}
			e.damage(world.getDamageSources().indirectMagic(player, player), (float) (10 * m * (1 - d / 6)));
			Vec3d push = e.getPos().subtract(at).normalize().multiply(1.2 * (1 - d / 6));
			e.addVelocity(push.x, 0.4, push.z);
			e.velocityModified = true;
		}
		player.damage(world.getDamageSources().explosion(player, player), 4);
		world.spawnParticles(ParticleTypes.EXPLOSION_EMITTER, at.x, at.y + 1, at.z, 1, 0, 0, 0, 0);
		Fx.ring(world, at, 0.5f, 6, 14, GLINT_RGB);
		Fx.shake(player, 2.0f, 10);
		world.playSound(null, player.getBlockPos(), SoundEvents.ENTITY_GENERIC_EXPLODE, SoundCategory.PLAYERS, 1.2f, 1.0f);
		return AbilityResult.OK;
	}

	/** Spider, the Web: the creature you look at (16 blocks) and all within 3 of it stuck and poisoned. */
	private static AbilityResult web(ServerPlayerEntity player, float m) {
		LivingEntity target = foeInSight(player, 16);
		if (target == null) {
			return AbilityResult.NO_SIGHT;
		}
		ServerWorld world = player.getServerWorld();
		for (LivingEntity e : world.getEntitiesByClass(LivingEntity.class, target.getBoundingBox().expand(3),
				e -> e != player && e.isAlive() && !(e instanceof PlayerEntity) && !RETINUE.containsKey(e.getUuid()))) {
			e.addStatusEffect(new StatusEffectInstance(StatusEffects.SLOWNESS, Math.round(100 * m), 3));
			e.addStatusEffect(new StatusEffectInstance(StatusEffects.POISON, 80, 0));
			world.spawnParticles(new ItemStackParticleEffect(ParticleTypes.ITEM, new ItemStack(Items.COBWEB)), e.getX(), e.getY() + e.getHeight() / 2, e.getZ(), 12, 0.4, 0.4, 0.4, 0.02);
		}
		Fx.tendril(world, player, target, 16, BONE_INK, 0xF4F0E8);
		Fx.mark(world, target.getPos(), null, 3.0f, 60, 0xF4F0E8, Fx.CRACKS);
		world.playSound(null, target.getBlockPos(), SoundEvents.ENTITY_SPIDER_AMBIENT, SoundCategory.PLAYERS, 1.0f, 0.7f);
		return AbilityResult.OK;
	}

	/** Enderman, the Step: to the block you look at, up to 16 away. */
	private static AbilityResult step(ServerPlayerEntity player) {
		HitResult hit = player.raycast(16, 1.0f, false);
		if (!(hit instanceof BlockHitResult block) || hit.getType() != HitResult.Type.BLOCK) {
			return AbilityResult.NO_TARGET;
		}
		ServerWorld world = player.getServerWorld();
		BlockPos pos = block.getBlockPos().offset(block.getSide());
		Vec3d to = null;
		for (int up = 0; up <= 2 && to == null; up++) {
			Vec3d at = Vec3d.ofBottomCenter(pos.up(up));
			if (world.isSpaceEmpty(player, player.getBoundingBox().offset(at.subtract(player.getPos())))) {
				to = at;
			}
		}
		if (to == null) {
			return AbilityResult.NO_SPACE;
		}
		Vec3d from = player.getPos();
		player.requestTeleport(to.x, to.y, to.z);
		player.fallDistance = 0;
		Fx.ghosts(world, List.of(from, from), player.getYaw(), 1, 14, Fx.INK | 0x2A1A3A);
		world.spawnParticles(ParticleTypes.PORTAL, from.x, from.y + 1, from.z, 40, 0.3, 0.8, 0.3, 0.3);
		world.spawnParticles(ParticleTypes.PORTAL, to.x, to.y + 1, to.z, 40, 0.3, 0.8, 0.3, 0.3);
		world.playSound(null, BlockPos.ofFloored(from), SoundEvents.ENTITY_ENDERMAN_TELEPORT, SoundCategory.PLAYERS, 1.0f, 1.0f);
		world.playSound(null, player.getBlockPos(), SoundEvents.ENTITY_ENDERMAN_TELEPORT, SoundCategory.PLAYERS, 1.0f, 1.2f);
		return AbilityResult.OK;
	}

	/** Blaze, the Blaze's Fire: the creature you look at (16 blocks) and those by it burn, 4 damage at once. */
	private static AbilityResult flame(ServerPlayerEntity player, float m) {
		LivingEntity target = foeInSight(player, 16);
		if (target == null) {
			return AbilityResult.NO_SIGHT;
		}
		ServerWorld world = player.getServerWorld();
		for (LivingEntity e : world.getEntitiesByClass(LivingEntity.class, target.getBoundingBox().expand(2),
				e -> e != player && e.isAlive() && !(e instanceof PlayerEntity) && !RETINUE.containsKey(e.getUuid()))) {
			e.setOnFireFor(6);
			e.damage(world.getDamageSources().indirectMagic(player, player), (e == target ? 4 : 2) * m);
			world.spawnParticles(ParticleTypes.FLAME, e.getX(), e.getY() + e.getHeight() / 2, e.getZ(), 16, 0.3, 0.5, 0.3, 0.03);
		}
		Fx.ribbon(world, List.of(player.getEyePos(), target.getPos().add(0, target.getHeight() / 2, 0)), 12, Fx.INK | 0x5A2008, 0xFFB040, 0.6f);
		Fx.burst(world, target.getPos().add(0, target.getHeight() / 2, 0), 1.5f, 8, 0xFFB040);
		world.playSound(null, player.getBlockPos(), SoundEvents.ENTITY_BLAZE_SHOOT, SoundCategory.PLAYERS, 1.0f, 1.0f);
		return AbilityResult.OK;
	}

	/** Witch, the Brew: a thrown flask of poison, weakness and slowness, and a sip of regeneration for herself. */
	private static AbilityResult brew(ServerPlayerEntity player, float m) {
		ServerWorld world = player.getServerWorld();
		ItemStack flask = PotionUtil.setCustomPotionEffects(new ItemStack(Items.SPLASH_POTION), List.of(
				new StatusEffectInstance(StatusEffects.POISON, Math.round(120 * m), 1),
				new StatusEffectInstance(StatusEffects.WEAKNESS, 160, 0),
				new StatusEffectInstance(StatusEffects.SLOWNESS, 160, 0)));
		flask.getOrCreateNbt().putInt("CustomPotionColor", 0x4E8A3A);
		PotionEntity potion = new PotionEntity(world, player);
		potion.setItem(flask);
		potion.setVelocity(player, player.getPitch(), player.getYaw(), -20.0f, 0.9f, 1.0f);
		world.spawnEntity(potion);
		player.addStatusEffect(new StatusEffectInstance(StatusEffects.REGENERATION, 100, 0, true, true, true));
		world.spawnParticles(ParticleTypes.WITCH, player.getX(), player.getEyeY(), player.getZ(), 12, 0.4, 0.3, 0.4, 0.02);
		world.playSound(null, player.getBlockPos(), SoundEvents.ENTITY_WITCH_THROW, SoundCategory.PLAYERS, 1.0f, 1.0f);
		return AbilityResult.OK;
	}

	/** Wither skeleton, the Black Blade: the next three blows within 6 seconds strike 4 harder and wither. */
	private static AbilityResult blade(ServerPlayerEntity player) {
		ServerWorld world = player.getServerWorld();
		BLADE.put(player.getUuid(), new Blade(3, now(player.getServer()) + 120));
		Fx.aura(world, player, 120, 0x5A5A5A, Fx.EMBERS);
		Fx.burst(world, player.getPos().add(0, 1, 0), 1.2f, 8, Fx.INK | 0x202020);
		world.playSound(null, player.getBlockPos(), SoundEvents.ENTITY_WITHER_SKELETON_AMBIENT, SoundCategory.PLAYERS, 1.0f, 0.7f);
		return AbilityResult.OK;
	}

	/** Phantom, the Rise: up into the air on the phantom's wings, the creatures around knocked back, a slow glide down. */
	private static AbilityResult rise(ServerPlayerEntity player, float m) {
		ServerWorld world = player.getServerWorld();
		for (LivingEntity e : world.getEntitiesByClass(LivingEntity.class, player.getBoundingBox().expand(3),
				e -> e != player && e.isAlive() && !(e instanceof PlayerEntity) && !RETINUE.containsKey(e.getUuid()))) {
			e.damage(world.getDamageSources().indirectMagic(player, player), 4 * m);
			Vec3d push = e.getPos().subtract(player.getPos()).normalize().multiply(1.0);
			e.addVelocity(push.x, 0.3, push.z);
			e.velocityModified = true;
		}
		player.addVelocity(0, 1.5, 0);
		player.velocityModified = true;
		player.fallDistance = 0;
		player.addStatusEffect(new StatusEffectInstance(StatusEffects.SLOW_FALLING, 120, 0, true, true, true));
		Fx.ring(world, player.getPos(), 0.4f, 4, 12, 0x8FA8C8);
		Fx.ribbon(world, List.of(player.getPos(), player.getPos().add(0, 6, 0)), 14, Fx.INK | 0x1A2438, 0x8FA8C8, 1.0f);
		world.playSound(null, player.getBlockPos(), SoundEvents.ENTITY_PHANTOM_FLAP, SoundCategory.PLAYERS, 1.2f, 0.8f);
		world.playSound(null, player.getBlockPos(), SoundEvents.ENTITY_PHANTOM_SWOOP, SoundCategory.PLAYERS, 1.0f, 1.0f);
		return AbilityResult.OK;
	}

	/** Illager, the Evoker's Fangs: a row of fangs bursts from the ground along the look, 12 blocks. */
	private static AbilityResult fangs(ServerPlayerEntity player, float m) {
		ServerWorld world = player.getServerWorld();
		float yaw = player.getYaw() * MathHelper.RADIANS_PER_DEGREE;
		double dx = -Math.sin(yaw);
		double dz = Math.cos(yaw);
		int count = m > 1 ? 14 : 12;
		int placed = 0;
		for (int i = 1; i <= count; i++) {
			double x = player.getX() + dx * i * 1.0;
			double z = player.getZ() + dz * i * 1.0;
			Double y = ground(world, x, player.getY() + 2, z);
			if (y == null) {
				continue;
			}
			world.spawnEntity(new EvokerFangsEntity(world, x, y, z, player.getYaw() * MathHelper.RADIANS_PER_DEGREE, i, player));
			placed++;
		}
		if (placed == 0) {
			return AbilityResult.NO_SPACE;
		}
		world.playSound(null, player.getBlockPos(), SoundEvents.ENTITY_EVOKER_CAST_SPELL, SoundCategory.PLAYERS, 1.0f, 1.0f);
		return AbilityResult.OK;
	}

	/** The top of the ground under a point, searching 5 blocks down, or null. */
	private static Double ground(ServerWorld world, double x, double fromY, double z) {
		BlockPos pos = BlockPos.ofFloored(x, fromY, z);
		for (int i = 0; i < 6; i++) {
			BlockPos below = pos.down();
			if (world.getBlockState(pos).getCollisionShape(world, pos).isEmpty() && world.getBlockState(below).isSideSolidFullSquare(world, below, net.minecraft.util.math.Direction.UP)) {
				return (double) pos.getY();
			}
			pos = below;
		}
		return null;
	}

	/** Kin (H): monsters of the worn face's kind near you (4, more with talents) follow you and fight for you. */
	public static AbilityResult kinCall(ServerPlayerEntity player, RaceData data) {
		Face face = data.masked.worn;
		if (face == null) {
			player.sendMessage(Text.translatable("mages_legacy.masked.faceless"), true);
			return AbilityResult.HINTED;
		}
		if (!ready(player, data, 3)) {
			return AbilityResult.COOLDOWN;
		}
		int cost = kinCost(data);
		if (data.resource < cost) {
			return AbilityResult.NO_RESOURCE;
		}
		ServerWorld world = player.getServerWorld();
		Set<Face> kinds = data.has(Talents.KIN_ALL) ? data.masked.gallery.keySet() : Set.of(face);
		List<MobEntity> kin = new ArrayList<>(world.getEntitiesByClass(MobEntity.class, player.getBoundingBox().expand(16),
				m -> m.isAlive() && Face.of(m.getType()).map(kinds::contains).orElse(false) && !RETINUE.containsKey(m.getUuid())));
		if (kin.isEmpty()) {
			player.sendMessage(Text.translatable("mages_legacy.masked.no_kin"), true);
			return AbilityResult.HINTED;
		}
		data.addResource(-cost);
		cooldown(player, data, 3, KIN_COOLDOWN);
		kin.sort(Comparator.comparingDouble(m -> m.squaredDistanceTo(player)));
		int size = data.has(Talents.KIN_KING) ? 10 : data.has(Talents.KIN_RETINUE) ? 6 : 4;
		int ticks = data.has(Talents.KIN_LONG) ? 600 : 400;
		long until = now(player.getServer()) + ticks;
		for (MobEntity m : kin.subList(0, Math.min(size, kin.size()))) {
			RETINUE.put(m.getUuid(), new Retinue(player.getUuid(), until));
			if (m.getTarget() instanceof PlayerEntity) {
				m.setTarget(null);
			}
			if (data.has(Talents.KIN_STRONG)) {
				m.addStatusEffect(new StatusEffectInstance(StatusEffects.STRENGTH, ticks, data.has(Talents.KIN_KING) ? 1 : 0));
				m.addStatusEffect(new StatusEffectInstance(StatusEffects.RESISTANCE, ticks, 0));
			}
			Fx.tendril(world, player, m, 20, BONE_INK, BONE_RGB);
			Fx.aura(world, m, ticks, BONE_RGB, Fx.MOTES);
		}
		Fx.ring(world, player.getPos(), 0.5f, 16, 18, BONE_RGB);
		world.playSound(null, player.getBlockPos(), SoundEvents.EVENT_RAID_HORN.value(), SoundCategory.PLAYERS, 0.6f, 1.4f);
		return AbilityResult.OK;
	}

	/** The Thousand Faces (Alt, the Gallery's crown): 20 seconds of every gallery face's gift at once, the powers free and twice as quick. */
	public static AbilityResult thousandFaces(ServerPlayerEntity player, RaceData data) {
		if (!ready(player, data, 4)) {
			return AbilityResult.COOLDOWN;
		}
		if (data.masked.gallery.size() < 2) {
			player.sendMessage(Text.translatable("mages_legacy.masked.one_face"), true);
			return AbilityResult.HINTED;
		}
		if (data.resource < THOUSAND_COST) {
			return AbilityResult.NO_RESOURCE;
		}
		data.addResource(-THOUSAND_COST);
		cooldown(player, data, 4, THOUSAND_COOLDOWN);
		THOUSAND.put(player.getUuid(), now(player.getServer()) + THOUSAND_TICKS);
		data.readyAt.put(1, 0L);
		ServerWorld world = player.getServerWorld();
		// Faces all around the wearer at once: shades in a ring, strands closing in, the world flickering pale
		List<Vec3d> ring = new ArrayList<>();
		for (int i = 0; i < 6; i++) {
			double a = i * Math.PI / 3;
			ring.add(player.getPos().add(Math.cos(a) * 1.8, 0, Math.sin(a) * 1.8));
		}
		Fx.ghosts(world, ring, player.getYaw(), 6, 30, BONE_INK);
		Fx.helix(world, player, 1.4f, 2.4f, 5, true, 30, BONE_RGB);
		Fx.aura(world, player, THOUSAND_TICKS, GLINT_RGB, Fx.MOTES);
		Fx.screen(player, 20, BONE_RGB);
		world.playSound(null, player.getBlockPos(), SoundEvents.ENTITY_ILLUSIONER_CAST_SPELL, SoundCategory.PLAYERS, 1.2f, 0.7f);
		world.playSound(null, player.getBlockPos(), SoundEvents.ENTITY_ILLUSIONER_MIRROR_MOVE, SoundCategory.PLAYERS, 1.0f, 0.5f);
		return AbilityResult.OK;
	}

	private static LivingEntity lookedAt(ServerPlayerEntity player, double range, java.util.function.Predicate<LivingEntity> filter) {
		Vec3d eye = player.getEyePos();
		Vec3d look = player.getRotationVec(1.0f);
		Vec3d end = eye.add(look.multiply(range));
		Box box = player.getBoundingBox().stretch(look.multiply(range)).expand(1);
		EntityHitResult hit = ProjectileUtil.raycast(player, eye, end, box,
				e -> e instanceof LivingEntity living && living.isAlive() && e != player && filter.test(living), range * range);
		return hit != null && hit.getEntity() instanceof LivingEntity living ? living : null;
	}

	// ---- damage ----

	/** A blow on one of the Masked: the face's gifts and weaknesses; with Kin Guard, its kind turns on the attacker. */
	public static float modifyDamage(ServerPlayerEntity player, DamageSource source, float amount) {
		RaceData data = masked(player);
		if (data == null || amount <= 0) {
			return amount;
		}
		Set<Face> faces = active(player, data);
		if (faces.contains(Face.CREEPER) && source.isIn(DamageTypeTags.IS_EXPLOSION)) {
			amount *= 0.5f;
		}
		if (faces.contains(Face.ENDERMAN) && source.isIn(DamageTypeTags.IS_FALL)) {
			amount *= 0.5f;
		}
		if (data.masked.worn == Face.SPIDER && source.isIn(DamageTypeTags.IS_FIRE)) {
			amount *= data.has(Talents.KIN_SKIN) ? 1.25f : 1.5f;
		}
		if (data.has(Talents.KIN_GUARD) && data.masked.worn != null && source.getAttacker() instanceof LivingEntity attacker
				&& !(attacker instanceof PlayerEntity)) {
			for (MobEntity m : player.getServerWorld().getEntitiesByClass(MobEntity.class, player.getBoundingBox().expand(16),
					m -> m.isAlive() && m != attacker && data.masked.worn.matches(m.getType()))) {
				m.setTarget(attacker);
			}
		}
		return amount;
	}

	/** Any blow a Masked strikes: its kind sees through it; faceless hands are weak; the faces' gifts. */
	public static float onHit(LivingEntity victim, DamageSource source, float amount) {
		if (amount <= 0 || !(source.getAttacker() instanceof ServerPlayerEntity player)) {
			return amount;
		}
		RaceData data = masked(player);
		if (data == null || victim == player) {
			return amount;
		}
		long now = now(player.getServer());
		Set<Face> faces = active(player, data);
		for (Face face : faces) {
			if (face.matches(victim.getType())) {
				// Struck one of its own: the kind sees through the face for a while
				EXPOSED.put(player.getUuid(), now + (data.has(Talents.KIN_DEEP) ? 100 : 300));
			}
		}
		if (source.isOf(DamageTypes.PLAYER_ATTACK) && source.getSource() == player) {
			if (data.masked.worn == null) {
				amount *= data.has(Talents.RIP_FACELESS) ? 0.95f : 0.85f;
			}
			if (faces.contains(Face.ILLAGER)) {
				amount *= 1.15f;
			}
			if (faces.contains(Face.WITHER_SKELETON)) {
				victim.addStatusEffect(new StatusEffectInstance(StatusEffects.WITHER, 60, 0));
			}
			Blade blade = BLADE.get(player.getUuid());
			if (blade != null && blade.until() > now && blade.charges() > 0) {
				amount += 4;
				victim.addStatusEffect(new StatusEffectInstance(StatusEffects.WITHER, 100, 1));
				BLADE.put(player.getUuid(), new Blade(blade.charges() - 1, blade.until()));
				Fx.slash(player.getServerWorld(), victim.getPos().add(0, victim.getHeight() * 0.6, 0), victim.getPos().subtract(player.getPos()),
						0.8f + victim.getWidth(), 60, 8, Fx.INK | 0x101010);
			}
		} else if (source.getSource() instanceof PersistentProjectileEntity && !(source.getSource() instanceof TridentEntity) && faces.contains(Face.SKELETON)) {
			amount *= 1.3f;
		}
		return amount;
	}

	/** A monster died: by the Masked's hand (Fresh Blood keeps its face fresh) or its retinue's (the hunt pays). */
	public static void onDeath(LivingEntity victim, DamageSource source) {
		if (victim.getServer() == null) {
			return;
		}
		RETINUE.remove(victim.getUuid());
		if (!(victim instanceof Monster)) {
			return;
		}
		if (source.getAttacker() instanceof ServerPlayerEntity player) {
			RaceData data = masked(player);
			if (data != null && data.masked.worn != null) {
				data.addResource(2);
				if (data.has(Talents.GAL_FRESH)) {
					data.masked.gallery.computeIfPresent(data.masked.worn, (f, fresh) -> Math.min(100, fresh + 5));
				}
			}
			return;
		}
		Retinue retinue = source.getAttacker() == null ? null : RETINUE.get(source.getAttacker().getUuid());
		ServerPlayerEntity owner = retinue == null ? null : victim.getServer().getPlayerManager().getPlayer(retinue.owner());
		RaceData data = owner == null ? null : masked(owner);
		if (data != null) {
			Progression.addXp(owner, data, Progression.HUNT, 2, Progression.HUNT_CAP);
			if (data.has(Talents.KIN_HEAL)) {
				owner.heal(2);
			}
			if (data.has(Talents.KIN_FEAST)) {
				data.addResource(5);
			}
		}
	}

	/** The Last Face (the Tearing's crown): a killing blow tears off the worn face instead, once in 20 minutes. */
	public static boolean allowDeath(ServerPlayerEntity player) {
		RaceData data = masked(player);
		if (data == null || !data.has(Talents.RIP_LAST) || data.masked.worn == null || !ready(player, data, LAST_SLOT)) {
			return true;
		}
		Face face = data.masked.worn;
		data.masked.gallery.remove(face);
		data.masked.worn = null;
		facesChanged = true;
		player.setHealth(player.getMaxHealth() * 0.4f);
		data.readyAt.put(LAST_SLOT, now(player.getServer()) + LAST_COOLDOWN);
		data.dirty = true;
		ServerWorld world = player.getServerWorld();
		Fx.burst(world, player.getEyePos(), 2.5f, 12, BONE_INK);
		Fx.ghosts(world, List.of(player.getPos(), player.getPos()), player.getYaw(), 1, 30, BONE_INK);
		Fx.screen(player, 24, BONE_RGB);
		world.spawnParticles(ParticleTypes.WHITE_ASH, player.getX(), player.getEyeY(), player.getZ(), 40, 0.4, 0.4, 0.4, 0.03);
		world.playSound(null, player.getBlockPos(), SoundEvents.ITEM_TOTEM_USE, SoundCategory.PLAYERS, 0.6f, 0.7f);
		player.sendMessage(Text.translatable("mages_legacy.masked.last_face", Text.translatable("mages_legacy.face." + face.id)).formatted(Formatting.GRAY), false);
		return false;
	}

	/** Takes the Masked's faces, retinue and timers away (the race was reset or changed) and its attribute changes off. */
	public static void cleanup(ServerPlayerEntity player) {
		UUID id = player.getUuid();
		EXPOSED.remove(id);
		THOUSAND.remove(id);
		TRACE.remove(id);
		BLADE.remove(id);
		RETINUE.values().removeIf(r -> r.owner().equals(id));
		for (UUID modifier : List.of(FACELESS_HEALTH, ZOMBIE_HEALTH, WITCH_HEALTH)) {
			EntityAttributeInstance health = player.getAttributeInstance(EntityAttributes.GENERIC_MAX_HEALTH);
			if (health != null) {
				health.removeModifier(modifier);
			}
		}
		EntityAttributeInstance speed = player.getAttributeInstance(EntityAttributes.GENERIC_MOVEMENT_SPEED);
		if (speed != null) {
			speed.removeModifier(WITHER_SPEED);
		}
		player.setHealth(Math.min(player.getHealth(), player.getMaxHealth()));
		facesChanged = true;
	}

	/** What the HUD needs: the worn face and how fresh, the next one for Z, the gallery, the Thousand Faces. */
	public static NbtCompound clientInfo(ServerPlayerEntity player, RaceData data) {
		NbtCompound nbt = new NbtCompound();
		MaskData mask = data.masked;
		nbt.putString("face", mask.worn == null ? "" : mask.worn.id);
		nbt.putInt("fresh", mask.worn == null ? 0 : (int) Math.ceil(mask.gallery.getOrDefault(mask.worn, 0f)));
		Face next = next(mask);
		nbt.putString("next", next == null || next == mask.worn ? "" : next.id);
		NbtList gallery = new NbtList();
		mask.gallery.keySet().forEach(f -> gallery.add(NbtString.of(f.id)));
		nbt.put("gallery", gallery);
		nbt.putInt("powerCost", powerCost(player, data));
		nbt.putBoolean("thousand", thousand(player));
		return nbt;
	}

	/** Whether an entity is in some Masked's retinue (the mixin keeps it off players). */
	public static boolean inRetinue(Entity entity) {
		return RETINUE.containsKey(entity.getUuid());
	}
}
