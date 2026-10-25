package euf.mageslegacy.shepherd;

import euf.mageslegacy.race.AbilityResult;
import euf.mageslegacy.race.Progression;
import euf.mageslegacy.race.Race;
import euf.mageslegacy.race.RaceData;
import euf.mageslegacy.race.RaceState;
import euf.mageslegacy.race.Talents;
import euf.mageslegacy.shepherd.ShepherdData.Bond;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.attribute.EntityAttributeInstance;
import net.minecraft.entity.attribute.EntityAttributeModifier;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.entity.mob.Monster;
import net.minecraft.entity.projectile.ProjectileUtil;
import net.minecraft.item.ArmorItem;
import net.minecraft.item.ArmorMaterials;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtList;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.hit.EntityHitResult;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;
import org.jetbrains.annotations.Nullable;

/**
 * Everything the Shepherds do that isn't the echoes or the bonds themselves: the voice (their resource) and the bond
 * capacity, their weaknesses (less health, a weak hand, heavy armour crowds the bonds), the three abilities (Call,
 * Rally, Swap), the auras of the Voice branch, and race experience from what their creatures do.
 */
public final class Shepherd {
	public static final int BASE_CAPACITY = 4;
	public static final int RALLY_COST = 25;
	public static final int SWAP_COST = 15;
	public static final int SWAP_COST_CHEAP = 5;
	public static final int CREATURE_KILL_XP = 3;
	public static final int ECHO_LEARNED_XP = 20;
	private static final int CALL_COOLDOWN = 20;
	private static final int RALLY_COOLDOWN = 600;
	private static final int SWAP_COOLDOWN = 160;
	private static final int SWAP_COOLDOWN_CHEAP = 60;
	private static final int ARMY_SLOT = 7;
	private static final int ARMY_COOLDOWN = 12000;
	private static final UUID FRAIL = UUID.fromString("2e8b5c1f-7a3d-4c6e-9f2b-1d5a8e3c7b99");

	private Shepherd() {
	}

	public static boolean is(ServerPlayerEntity player) {
		return RaceState.raceOf(player).orElse(null) == Race.SHEPHERD;
	}

	// ---- capacity ----

	/** Bond slots: 4, more from talents, one less for every heavy armour piece (iron counts unless Used to Iron). */
	public static int capacityMax(ServerPlayerEntity player, RaceData data) {
		int max = BASE_CAPACITY + (data.has(Talents.LEGION_CAPACITY) ? 2 : 0) + (data.has(Talents.LEGION_MORE) ? 2 : 0)
				+ (data.has(Talents.VOICE_CAPACITY) ? 1 : 0);
		return Math.max(1, max - heavyArmor(player, data));
	}

	public static int heavyArmor(ServerPlayerEntity player, RaceData data) {
		int heavy = 0;
		for (ItemStack stack : player.getArmorItems()) {
			if (stack.getItem() instanceof ArmorItem armor) {
				var material = armor.getMaterial();
				boolean iron = material == ArmorMaterials.IRON || material == ArmorMaterials.CHAIN;
				if (material == ArmorMaterials.DIAMOND || material == ArmorMaterials.NETHERITE || (iron && !data.has(Talents.VOICE_ARMOR))) {
					heavy++;
				}
			}
		}
		return heavy;
	}

	public static int capacityUsed(RaceData data, UUID owner) {
		int beasts = 0;
		for (Bond bond : data.shepherd.bonds) {
			beasts += BeastKind.byId(bond.kind).map(BeastKind::capacity).orElse(0);
		}
		return beasts + Echoes.capacityUsed(owner);
	}

	public static int capacityFree(ServerPlayerEntity player, RaceData data) {
		return capacityMax(player, data) - capacityUsed(data, player.getUuid());
	}

	// ---- once a second ----

	public static void tickSecond(ServerPlayerEntity player, RaceData data) {
		List<LivingEntity> near = Creatures.around(player, 16);
		// The voice comes back by itself, faster with creatures around
		float regen = 1.0f + Math.min(1.5f, near.size() * 0.25f);
		data.addResource(data.has(Talents.VOICE_DEEP) ? regen * 1.5f : regen);

		frail(player, !data.has(Talents.VOICE_LIFE));
		if (data.has(Talents.VOICE_AURA)) {
			for (LivingEntity c : near) {
				c.addStatusEffect(new StatusEffectInstance(StatusEffects.REGENERATION, 60, 0, true, false));
			}
		}
		if (data.has(Talents.PACK_HEAL) && Creatures.around(player, 6).stream().filter(e -> !Echoes.isEcho(e)).count() >= 2) {
			player.addStatusEffect(new StatusEffectInstance(StatusEffects.REGENERATION, 60, 0, true, false, true));
		}
		if (data.has(Talents.PACK_FURY) && player.getHealth() < player.getMaxHealth() / 2) {
			for (LivingEntity c : near) {
				if (!Echoes.isEcho(c)) {
					c.addStatusEffect(new StatusEffectInstance(StatusEffects.STRENGTH, 60, 1, true, false));
					c.addStatusEffect(new StatusEffectInstance(StatusEffects.SPEED, 60, 0, true, false));
				}
			}
		}
		Bonds.tickOwner(player, data);
	}

	/** −4 maximum health (2 hearts), unless Sturdy Body took it away. */
	public static void frail(ServerPlayerEntity player, boolean want) {
		EntityAttributeInstance health = player.getAttributeInstance(EntityAttributes.GENERIC_MAX_HEALTH);
		if (health == null || want == (health.getModifier(FRAIL) != null)) {
			return;
		}
		if (want) {
			health.addTemporaryModifier(new EntityAttributeModifier(FRAIL, "shepherd_frail", -4, EntityAttributeModifier.Operation.ADDITION));
			player.setHealth(Math.min(player.getHealth(), player.getMaxHealth()));
		} else {
			health.removeModifier(FRAIL);
		}
	}

	/** The race changed: echoes go, beasts are released, the health penalty ends. */
	public static void cleanup(ServerPlayerEntity player, RaceData data) {
		Echoes.dismiss(player.getServer(), player.getUuid());
		Bonds.releaseAll(player, data);
		frail(player, false);
	}

	// ---- damage ----

	/** A Shepherd and its own creatures never hurt each other (a stray arrow, a swing in the crowd). */
	public static boolean friendlyFire(LivingEntity victim, DamageSource source) {
		Entity attacker = source.getAttacker();
		if (attacker == null || attacker == victim) {
			return false;
		}
		UUID victimSide = victim instanceof ServerPlayerEntity p ? p.getUuid() : Creatures.ownerOf(victim);
		UUID attackerSide = attacker instanceof ServerPlayerEntity p ? p.getUuid() : Creatures.ownerOf(attacker);
		return victimSide != null && victimSide.equals(attackerSide);
	}

	/** Damage changes for Shepherds and their creatures (friendly fire is stopped earlier, in {@link #friendlyFire}). */
	public static float modifyDamage(LivingEntity victim, DamageSource source, float amount) {
		if (friendlyFire(victim, source)) {
			return amount;
		}
		Entity attacker = source.getAttacker();
		if (attacker instanceof ServerPlayerEntity shepherd && source.getSource() == shepherd && is(shepherd)) {
			RaceData data = RaceState.of(shepherd);
			if (data.has(Talents.VOICE_MARK) && victim instanceof Monster) {
				Creatures.mark(shepherd, victim, shepherd.getServer().getTicks() + 100);
				victim.addStatusEffect(new StatusEffectInstance(StatusEffects.GLOWING, 100, 0, true, false));
			}
			if (!data.has(Talents.VOICE_STRENGTH)) {
				amount *= 0.75f;
			}
		}
		if (attacker != null && Echoes.isEcho(attacker) && ownerHas(attacker, Talents.LEGION_BURNING)) {
			victim.setOnFireFor(3);
		}
		UUID beastOwner = victim.getCommandTags().contains(Creatures.BOND_TAG) ? Creatures.ownerOf(victim) : null;
		if (beastOwner != null && dataOf(victim, beastOwner).map(d -> d.has(Talents.PACK_ARMOR)).orElse(false)) {
			amount *= 0.7f;
		}
		if (victim instanceof ServerPlayerEntity shepherd && is(shepherd) && RaceState.of(shepherd).has(Talents.VOICE_HEART) && amount > 0) {
			amount = shareHeart(shepherd, source, amount);
		}
		return amount;
	}

	private static Optional<RaceData> dataOf(Entity entity, UUID owner) {
		return entity.getServer() == null ? Optional.empty() : Optional.of(RaceState.get(entity.getServer()).data(owner));
	}

	private static boolean ownerHas(Entity creature, String talent) {
		UUID owner = Creatures.ownerOf(creature);
		return owner != null && dataOf(creature, owner).map(d -> d.has(talent)).orElse(false);
	}

	/** One Heart: half the blow is shared out among the creatures within 16 blocks. */
	private static float shareHeart(ServerPlayerEntity shepherd, DamageSource source, float amount) {
		List<LivingEntity> near = Creatures.around(shepherd, 16);
		if (near.isEmpty()) {
			return amount;
		}
		float shared = amount / 2;
		for (LivingEntity c : near) {
			c.damage(shepherd.getDamageSources().magic(), shared / near.size());
		}
		return amount - shared;
	}

	// ---- abilities ----

	public static AbilityResult use(ServerPlayerEntity player, RaceData data, int slot) {
		return switch (slot) {
			case 0 -> call(player, data);
			case 1 -> rally(player, data);
			case 2 -> swap(player, data);
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

	public static boolean learned(RaceData data, EchoType type) {
		return data.shepherd.kills(type.id()) >= type.killsNeeded(data.has(Talents.LEGION_THRESHOLDS));
	}

	/** Call: the echo picked in the bestiary rises where you look. Sneaking sends all your echoes back. */
	public static AbilityResult call(ServerPlayerEntity player, RaceData data) {
		if (player.isSneaking()) {
			int gone = Echoes.dismiss(player.getServer(), player.getUuid());
			player.sendMessage(Text.translatable("mages_legacy.echo.dismissed", gone), true);
			return AbilityResult.OK;
		}
		if (!ready(player, data, 0)) {
			return AbilityResult.COOLDOWN;
		}
		EchoType type = EchoType.byId(data.shepherd.selectedEcho).orElse(null);
		if (type == null || !learned(data, type)) {
			return AbilityResult.NO_ECHO;
		}
		int cost = type.voiceCost(data.has(Talents.LEGION_CHEAP));
		if (data.resource < cost) {
			return AbilityResult.NO_RESOURCE;
		}
		if (capacityFree(player, data) < type.capacity()) {
			return AbilityResult.NO_CAPACITY;
		}
		if (Echoes.summon(player, data, type, Echoes.callSpot(player), false, 0, cost) == null) {
			return AbilityResult.NO_SPACE;
		}
		data.addResource(-cost);
		cooldown(player, data, 0, CALL_COOLDOWN);
		return AbilityResult.OK;
	}

	/**
	 * Rally: every creature within 24 blocks grows stronger and faster for 10 seconds (Loud Rally adds resistance).
	 * Command turns them all on what you look at; the Army of Oblivion crown calls every echo you know for 30 s.
	 */
	public static AbilityResult rally(ServerPlayerEntity player, RaceData data) {
		if (!ready(player, data, 1)) {
			return AbilityResult.COOLDOWN;
		}
		if (data.resource < RALLY_COST) {
			return AbilityResult.NO_RESOURCE;
		}
		data.addResource(-RALLY_COST);
		cooldown(player, data, 1, RALLY_COOLDOWN);
		ServerWorld world = player.getServerWorld();
		boolean loud = data.has(Talents.VOICE_RALLY);
		LivingEntity target = data.has(Talents.VOICE_COMMAND) ? lookedAt(player, 32, e -> !Creatures.isCreatureOf(e, player.getUuid())) : null;
		if (data.has(Talents.LEGION_ARMY) && ready(player, data, ARMY_SLOT)) {
			for (EchoType type : EchoType.ALL) {
				if (learned(data, type)) {
					Echoes.summon(player, data, type, Creatures.near(player), true, 600, 0);
				}
			}
			cooldown(player, data, ARMY_SLOT, ARMY_COOLDOWN);
		}
		for (LivingEntity c : Creatures.around(player, 24)) {
			c.addStatusEffect(new StatusEffectInstance(StatusEffects.STRENGTH, 200, loud ? 1 : 0, false, true));
			c.addStatusEffect(new StatusEffectInstance(StatusEffects.SPEED, 200, 0, false, true));
			if (loud) {
				c.addStatusEffect(new StatusEffectInstance(StatusEffects.RESISTANCE, 200, 0, false, true));
			}
			if (target != null && c instanceof MobEntity mob) {
				mob.setTarget(target);
			}
			world.spawnParticles(ParticleTypes.ANGRY_VILLAGER, c.getX(), c.getY() + c.getHeight() + 0.3, c.getZ(), 1, 0, 0, 0, 0);
		}
		world.playSound(null, player.getBlockPos(), SoundEvents.EVENT_RAID_HORN.value(), SoundCategory.PLAYERS, 0.8f, 1.3f);
		world.spawnParticles(Echoes.SPIRIT, player.getX(), player.getY() + 1.6, player.getZ(), 30, 1.0, 0.4, 1.0, 0.05);
		return AbilityResult.OK;
	}

	/** Swap: trade places with the creature of yours you look at, up to 24 blocks away. */
	public static AbilityResult swap(ServerPlayerEntity player, RaceData data) {
		if (!ready(player, data, 2)) {
			return AbilityResult.COOLDOWN;
		}
		boolean cheap = data.has(Talents.VOICE_SWAP);
		int cost = cheap ? SWAP_COST_CHEAP : SWAP_COST;
		if (data.resource < cost) {
			return AbilityResult.NO_RESOURCE;
		}
		LivingEntity creature = lookedAt(player, 24, e -> Creatures.isCreatureOf(e, player.getUuid()));
		if (creature == null) {
			return AbilityResult.NO_CREATURE;
		}
		ServerWorld world = player.getServerWorld();
		Vec3d mine = player.getPos();
		Vec3d theirs = creature.getPos();
		world.spawnParticles(Echoes.SPIRIT, mine.x, mine.y + 1, mine.z, 20, 0.3, 0.6, 0.3, 0);
		world.spawnParticles(Echoes.SPIRIT, theirs.x, theirs.y + 1, theirs.z, 20, 0.3, 0.6, 0.3, 0);
		player.teleport(world, theirs.x, theirs.y, theirs.z, player.getYaw(), player.getPitch());
		creature.refreshPositionAndAngles(mine.x, mine.y, mine.z, creature.getYaw(), creature.getPitch());
		player.fallDistance = 0;
		world.playSound(null, player.getBlockPos(), SoundEvents.ENTITY_ENDERMAN_TELEPORT, SoundCategory.PLAYERS, 0.7f, 1.4f);
		data.addResource(-cost);
		cooldown(player, data, 2, cheap ? SWAP_COOLDOWN_CHEAP : SWAP_COOLDOWN);
		return AbilityResult.OK;
	}

	@Nullable
	private static LivingEntity lookedAt(ServerPlayerEntity player, double range, java.util.function.Predicate<LivingEntity> filter) {
		Vec3d eye = player.getEyePos();
		Vec3d look = player.getRotationVec(1.0f);
		Vec3d end = eye.add(look.multiply(range));
		Box box = player.getBoundingBox().stretch(look.multiply(range)).expand(1);
		EntityHitResult hit = ProjectileUtil.raycast(player, eye, end, box,
				e -> e instanceof LivingEntity living && living.isAlive() && e != player && filter.test(living), range * range);
		return hit != null && hit.getEntity() instanceof LivingEntity living ? living : null;
	}

	// ---- experience and the bestiary ----

	/**
	 * Someone killed something. A Shepherd's own kills and its creatures' kills fill the bestiary; creatures' kills
	 * also give race experience (and a bonded beast its own), and with Harvest some voice.
	 */
	public static void onKill(Entity killer, LivingEntity victim) {
		if (killer.getServer() == null) {
			return;
		}
		UUID ownerId = killer instanceof ServerPlayerEntity p ? p.getUuid() : Creatures.ownerOf(killer);
		ServerPlayerEntity owner = ownerId == null ? null : killer.getServer().getPlayerManager().getPlayer(ownerId);
		if (owner == null || !is(owner) || Creatures.ownerOf(victim) != null) {
			return;
		}
		RaceData data = RaceState.of(owner);
		EchoType.of(victim.getType()).ifPresent(type -> countKill(owner, data, type));
		if (killer == owner || !(victim instanceof Monster)) {
			return;
		}
		Progression.addXp(owner, data, CREATURE_KILL_XP);
		if (Echoes.isEcho(killer) && data.has(Talents.LEGION_LEECH)) {
			data.addResource(5);
		}
		if (killer instanceof MobEntity mob) {
			data.shepherd.bond(killer.getUuid()).ifPresent(bond -> Bonds.beastXp(owner, data, bond, mob, 10));
		}
	}

	public static void countKill(ServerPlayerEntity owner, RaceData data, EchoType type) {
		boolean before = learned(data, type);
		data.shepherd.kills.merge(type.id(), 1, Integer::sum);
		data.dirty = true;
		if (!before && learned(data, type)) {
			if (data.shepherd.selectedEcho.isEmpty()) {
				data.shepherd.selectedEcho = type.id();
			}
			owner.sendMessage(Text.translatable("mages_legacy.echo.learned", type.type().getName()).formatted(Formatting.AQUA), false);
			owner.getWorld().playSound(null, owner.getBlockPos(), SoundEvents.PARTICLE_SOUL_ESCAPE, SoundCategory.PLAYERS, 1.5f, 0.6f);
			Progression.addXp(owner, data, ECHO_LEARNED_XP);
		}
	}

	/** Pick the echo the Call brings (from the bestiary); only learned ones. */
	public static boolean select(ServerPlayerEntity player, RaceData data, String id) {
		Optional<EchoType> type = EchoType.byId(id);
		if (type.isEmpty() || !learned(data, type.get())) {
			return false;
		}
		data.shepherd.selectedEcho = id;
		data.dirty = true;
		return true;
	}

	/** What the client's HUD and bestiary need beyond the common sync. */
	public static NbtCompound clientInfo(ServerPlayerEntity player, RaceData data) {
		NbtCompound nbt = data.shepherd.toNbt();
		nbt.remove("rituals");
		NbtList bonds = nbt.getList("bonds", 10);
		long time = player.getServerWorld().getTime();
		for (int i = 0; i < bonds.size(); i++) {
			NbtCompound b = bonds.getCompound(i);
			b.remove("body");
			b.putInt("restSeconds", (int) Math.max(0, (b.getLong("restingUntil") - time) / 20));
		}
		nbt.putInt("capUsed", capacityUsed(data, player.getUuid()));
		nbt.putInt("capMax", capacityMax(player, data));
		nbt.putInt("echoes", Echoes.count(player.getUuid()));
		return nbt;
	}
}
