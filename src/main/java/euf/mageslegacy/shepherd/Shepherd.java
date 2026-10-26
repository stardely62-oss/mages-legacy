package euf.mageslegacy.shepherd;

import euf.mageslegacy.race.AbilityResult;
import euf.mageslegacy.race.Progression;
import euf.mageslegacy.race.Race;
import euf.mageslegacy.race.RaceData;
import euf.mageslegacy.race.RaceState;
import euf.mageslegacy.race.Talents;
import euf.mageslegacy.shepherd.ShepherdData.Bond;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
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
import net.minecraft.text.MutableText;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.hit.EntityHitResult;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;
import org.jetbrains.annotations.Nullable;

/**
 * Everything the Shepherds do that isn't the echoes or the bonds themselves: the voice (their resource) and the bond
 * capacity, their weaknesses (less health, a weak hand, heavy armour crowds the bonds), the abilities (Call, Rally,
 * the Whistle; the Song of Bonds lives in {@link Bonds}), the auras of the Voice branch, and race experience from what their creatures do.
 */
public final class Shepherd {
	public static final int BASE_CAPACITY = 4;
	public static final int RALLY_COST = 25;
	public static final float RALLY_HEAL = 0.3f;
	public static final float RALLY_HEAL_WARM = 0.5f;
	public static final int GATHER_COST = 10;
	public static final int CREATURE_KILL_XP = 3;
	public static final int ECHO_LEARNED_XP = 20;
	private static final int CALL_COOLDOWN = 20;
	private static final int RALLY_COOLDOWN = 600;
	private static final int WHISTLE_COOLDOWN = 10;
	private static final int GATHER_SLOT = 5;
	private static final int GATHER_COOLDOWN = 160;
	private static final int ARMY_SLOT = 7;
	private static final int ARMY_COOLDOWN = 12000;
	private static final int ARMY_KINDS = 6;
	private static final UUID FRAIL = UUID.fromString("2e8b5c1f-7a3d-4c6e-9f2b-1d5a8e3c7b99");

	private Shepherd() {
	}

	public static boolean is(ServerPlayerEntity player) {
		return RaceState.raceOf(player).orElse(null) == Race.SHEPHERD;
	}

	// ---- capacity ----

	/** Bond slots: 4, more from talents, one less for every 2 points of heavy armour (see {@link #heavyArmor}). */
	public static int capacityMax(ServerPlayerEntity player, RaceData data) {
		int max = BASE_CAPACITY + (data.has(Talents.LEGION_CAPACITY) ? 2 : 0) + (data.has(Talents.LEGION_MORE) ? 2 : 0)
				+ (data.has(Talents.VOICE_CAPACITY) ? 1 : 0);
		return Math.max(1, max - heavyArmor(player, data) / 2);
	}

	/**
	 * Heavy armour points: iron and chainmail 1 a piece (none with Used to Iron), diamond and netherite 2. Every 2
	 * points take a bond slot: a full iron set costs 2, a full diamond one 4.
	 */
	public static int heavyArmor(ServerPlayerEntity player, RaceData data) {
		int points = 0;
		for (ItemStack stack : player.getArmorItems()) {
			if (stack.getItem() instanceof ArmorItem armor) {
				var material = armor.getMaterial();
				if (material == ArmorMaterials.DIAMOND || material == ArmorMaterials.NETHERITE) {
					points += 2;
				} else if ((material == ArmorMaterials.IRON || material == ArmorMaterials.CHAIN) && !data.has(Talents.VOICE_ARMOR)) {
					points += 1;
				}
			}
		}
		return points;
	}

	public static int capacityUsed(RaceData data, UUID owner) {
		int beasts = 0;
		for (Bond bond : data.shepherd.bonds) {
			beasts += BeastKind.byId(bond.kind).map(k -> beastCost(k, data)).orElse(0);
		}
		return beasts + Echoes.capacityUsed(owner);
	}

	/** Bond slots a beast takes: Light Bonds take one off (never below one). */
	public static int beastCost(BeastKind kind, RaceData data) {
		return beastCost(kind, data.has(Talents.PACK_LIGHT));
	}

	public static int beastCost(BeastKind kind, boolean light) {
		return light ? Math.max(1, kind.capacity() - 1) : kind.capacity();
	}

	public static int capacityFree(ServerPlayerEntity player, RaceData data) {
		return capacityMax(player, data) - capacityUsed(data, player.getUuid());
	}

	// ---- once a second ----

	public static void tickSecond(ServerPlayerEntity player, RaceData data) {
		List<LivingEntity> near = Creatures.around(player, 16);
		// The voice comes back by itself, faster with creatures around: about two minutes for a full bar alone
		float regen = 0.5f + Math.min(1.0f, near.size() * 0.2f);
		data.addResource(data.has(Talents.VOICE_DEEP) ? regen * 1.5f : regen);

		frail(player, data.has(Talents.VOICE_LIFE) ? -2 : -4);
		if (data.has(Talents.VOICE_AURA)) {
			for (LivingEntity c : near) {
				c.addStatusEffect(new StatusEffectInstance(StatusEffects.REGENERATION, 60, 0, true, false));
			}
		}
		if (data.has(Talents.PACK_FURY) && player.getHealth() < player.getMaxHealth() / 2) {
			for (LivingEntity c : near) {
				if (!Echoes.isEcho(c)) {
					c.addStatusEffect(new StatusEffectInstance(StatusEffects.STRENGTH, 60, 1, true, false));
					c.addStatusEffect(new StatusEffectInstance(StatusEffects.SPEED, 60, 0, true, false));
				}
			}
		}
		stance(player, data, near);
		Bonds.tickOwner(player, data);
	}

	/** Maximum health lost: 4 (2 hearts), 2 with Sturdy Body; 0 takes the penalty away. */
	public static void frail(ServerPlayerEntity player, double amount) {
		EntityAttributeInstance health = player.getAttributeInstance(EntityAttributes.GENERIC_MAX_HEALTH);
		if (health == null) {
			return;
		}
		EntityAttributeModifier current = health.getModifier(FRAIL);
		if (current != null && current.getValue() == amount) {
			return;
		}
		if (current != null) {
			health.removeModifier(FRAIL);
		}
		if (amount != 0) {
			health.addTemporaryModifier(new EntityAttributeModifier(FRAIL, "shepherd_frail", amount, EntityAttributeModifier.Operation.ADDITION));
			player.setHealth(Math.min(player.getHealth(), player.getMaxHealth()));
		}
	}

	/** The race changed: echoes go, beasts are released, the health penalty ends. */
	public static void cleanup(ServerPlayerEntity player, RaceData data) {
		Echoes.dismiss(player.getServer(), player.getUuid());
		Bonds.releaseAll(player, data);
		frail(player, 0);
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
			// A Shepherd's own blow is a quarter weaker; Steady Hand only narrows it to a tenth
			amount *= data.has(Talents.VOICE_STRENGTH) ? 0.9f : 0.75f;
		}
		if (attacker != null && Echoes.isEcho(attacker)) {
			amount = Echoes.onHit(attacker, victim, amount);
		}
		UUID creatureOwner = attacker == null || attacker instanceof ServerPlayerEntity ? null : Creatures.ownerOf(attacker);
		ServerPlayerEntity master = creatureOwner == null || attacker.getServer() == null ? null
				: attacker.getServer().getPlayerManager().getPlayer(creatureOwner);
		if (master != null) {
			RaceData data = RaceState.of(master);
			// A foe marked by a skeleton echo's arrows takes more from the Shepherd's creatures
			if (victim == Creatures.marked(master)) {
				amount *= 1.3f;
			}
			// The Banner: creatures near their Shepherd hit harder
			if (data.has(Talents.VOICE_BANNER) && attacker.squaredDistanceTo(master) < 16 * 16) {
				amount *= 1.2f;
			}
			// A fed beast bites harder
			Optional<Bond> bond = data.shepherd.bond(attacker.getUuid());
			if (bond.isPresent()) {
				amount *= Bonds.fedBonus(data, bond.get(), master.getServerWorld().getTime());
			}
		}
		if (attacker != null && attacker.getCommandTags().contains(Creatures.BOND_TAG)) {
			Bonds.onBeastHit(attacker, victim);
		}
		// On Guard the pack stands firm: a fifth less damage
		UUID guardOwner = victim instanceof ServerPlayerEntity ? null : Creatures.ownerOf(victim);
		if (guardOwner != null && dataOf(victim, guardOwner).map(d -> d.shepherd.stance == ShepherdData.GUARD).orElse(false)) {
			amount *= 0.8f;
		}
		UUID beastOwner = victim.getCommandTags().contains(Creatures.BOND_TAG) ? Creatures.ownerOf(victim) : null;
		if (beastOwner != null && dataOf(victim, beastOwner).map(d -> d.has(Talents.PACK_ARMOR)).orElse(false)) {
			amount *= 0.7f;
		}
		if (victim instanceof ServerPlayerEntity shepherd && is(shepherd) && amount > 0) {
			RaceData data = RaceState.of(shepherd);
			if (Bonds.bearGuards(shepherd, data)) {
				amount *= 0.8f;
			}
			if (data.has(Talents.VOICE_HEART)) {
				amount = shareHeart(shepherd, source, amount);
			}
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
			case 2 -> whistle(player, data);
			case 3 -> Bonds.song(player, data);
			default -> AbilityResult.LOCKED;
		};
	}

	private static long now(ServerPlayerEntity player) {
		return player.getServer().getTicks();
	}

	static boolean ready(ServerPlayerEntity player, RaceData data, int slot) {
		return data.readyAt.getOrDefault(slot, 0L) <= now(player);
	}

	static void cooldown(ServerPlayerEntity player, RaceData data, int slot, int ticks) {
		data.readyAt.put(slot, now(player) + ticks);
		data.dirty = true;
	}

	public static boolean learned(RaceData data, EchoType type) {
		return data.shepherd.kills(type.id()) >= type.killsNeeded();
	}

	public static final int PRESET_MAX = 12;

	/** The echoes of the active squad that aren't walking with this Shepherd yet, in the squad's order. */
	public static List<EchoType> missing(RaceData data, Map<String, Integer> alive) {
		List<EchoType> list = new ArrayList<>();
		data.shepherd.presets.get(data.shepherd.activePreset).forEach((id, count) -> EchoType.byId(id).ifPresent(type -> {
			for (int i = type.units(alive.getOrDefault(id, 0)); i < count; i++) {
				list.add(type);
			}
		}));
		return list;
	}

	/**
	 * Call: the active squad rises where you look, all at once. Echoes of it already with you stay; only the missing
	 * ones come, as many as the voice and the bond slots allow. Sneaking sends all your echoes back.
	 */
	public static AbilityResult call(ServerPlayerEntity player, RaceData data) {
		if (player.isSneaking()) {
			int gone = Echoes.dismiss(player.getServer(), player.getUuid());
			player.sendMessage(Text.translatable("mages_legacy.echo.dismissed", gone), true);
			return AbilityResult.OK;
		}
		if (!ready(player, data, 0)) {
			return AbilityResult.COOLDOWN;
		}
		if (data.shepherd.presets.get(data.shepherd.activePreset).isEmpty()) {
			return AbilityResult.NO_ECHO;
		}
		List<EchoType> missing = missing(data, Echoes.countByType(player.getUuid()));
		if (missing.isEmpty()) {
			player.sendMessage(Text.translatable("mages_legacy.echo.squad_full"), true);
			return AbilityResult.OK;
		}
		boolean quiet = data.has(Talents.LEGION_CHEAP);
		Vec3d base = Echoes.callSpot(player);
		int summoned = 0;
		boolean lackVoice = false;
		boolean lackRoom = false;
		for (EchoType type : missing) {
			int cost = type.voiceCost(quiet);
			if (!learned(data, type)) {
				continue;
			}
			if (data.resource < cost) {
				lackVoice = true;
				continue;
			}
			if (capacityFree(player, data) < type.capacity()) {
				lackRoom = true;
				continue;
			}
			// A group (silverfish) comes several at once for one price and one slot
			int spot = summoned;
			boolean any = false;
			for (int g = 0; g < type.group(); g++) {
				any |= Echoes.summon(player, data, type, Echoes.around(player.getServerWorld(), base, spot++), false, 0,
						g == 0 ? cost : 0) != null;
			}
			if (any) {
				data.addResource(-cost);
				summoned++;
			}
		}
		if (summoned == 0) {
			return lackVoice ? AbilityResult.NO_RESOURCE : lackRoom ? AbilityResult.NO_CAPACITY : AbilityResult.NO_SPACE;
		}
		if (summoned < missing.size()) {
			player.sendMessage(Text.translatable("mages_legacy.echo.partial", summoned, missing.size(),
					Text.translatable(lackVoice ? "mages_legacy.echo.lack_voice" : "mages_legacy.echo.lack_room")), true);
		}
		cooldown(player, data, 0, CALL_COOLDOWN);
		return AbilityResult.OK;
	}

	/** Bestiary: one more (+1) or one less (-1) of a learned echo in a squad. */
	public static boolean editPreset(RaceData data, int index, String id, int delta) {
		if (index < 0 || index >= data.shepherd.presets.size()) {
			return false;
		}
		Optional<EchoType> type = EchoType.byId(id);
		if (type.isEmpty() || (delta > 0 && !learned(data, type.get()))) {
			return false;
		}
		Map<String, Integer> preset = data.shepherd.presets.get(index);
		int total = preset.values().stream().mapToInt(Integer::intValue).sum();
		int now = preset.getOrDefault(id, 0);
		int next = Math.max(0, now + Integer.signum(delta));
		if (next > now && total >= PRESET_MAX) {
			return false;
		}
		if (next == 0) {
			preset.remove(id);
		} else {
			preset.put(id, next);
		}
		data.dirty = true;
		return true;
	}

	/** Makes squad {@code index} the one the Call brings, and says what it holds. */
	public static void selectPreset(ServerPlayerEntity player, RaceData data, int index) {
		data.shepherd.activePreset = Math.floorMod(index, data.shepherd.presets.size());
		data.dirty = true;
		MutableText list = Text.empty();
		data.shepherd.presets.get(data.shepherd.activePreset).forEach((id, count) -> EchoType.byId(id).ifPresent(type -> {
			if (!list.getSiblings().isEmpty()) {
				list.append(", ");
			}
			list.append(type.type().getName()).append(" ×" + count);
		}));
		player.sendMessage(Text.translatable("mages_legacy.echo.squad", ROMAN[data.shepherd.activePreset],
				list.getSiblings().isEmpty() ? Text.translatable("mages_legacy.echo.squad_empty") : list).formatted(Formatting.AQUA), true);
	}

	public static final String[] ROMAN = {"I", "II", "III"};

	/**
	 * Rally: every creature within 24 blocks grows stronger and faster for 10 seconds (Loud Rally adds resistance)
	 * and heals; the players around heal 2 hearts. The Army of Oblivion crown calls every echo you know for 30 s.
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
		if (data.has(Talents.LEGION_ARMY) && ready(player, data, ARMY_SLOT)) {
			// The six mightiest kinds learned (by the voice their Call costs), not every one
			EchoType.ALL.stream().filter(type -> learned(data, type)).sorted((a, b) -> Integer.compare(b.voice(), a.voice())).limit(ARMY_KINDS)
					.forEach(type -> Echoes.summon(player, data, type, Creatures.near(player), true, 600, 0));
			cooldown(player, data, ARMY_SLOT, ARMY_COOLDOWN);
		}
		// The horn heals the pack a lot (30% of their health, half with Warm Hands, which also cleanse them) and the
		// players around only a little
		boolean warm = data.has(Talents.VOICE_MEND);
		for (LivingEntity c : Creatures.around(player, 24)) {
			c.addStatusEffect(new StatusEffectInstance(StatusEffects.STRENGTH, 200, loud ? 1 : 0, false, true));
			c.addStatusEffect(new StatusEffectInstance(StatusEffects.SPEED, 200, 0, false, true));
			if (loud) {
				c.addStatusEffect(new StatusEffectInstance(StatusEffects.RESISTANCE, 200, 0, false, true));
			}
			c.heal(c.getMaxHealth() * (warm ? RALLY_HEAL_WARM : RALLY_HEAL));
			if (warm) {
				c.getStatusEffects().stream().filter(e -> !e.getEffectType().isBeneficial()).map(StatusEffectInstance::getEffectType).toList()
						.forEach(c::removeStatusEffect);
			}
			world.spawnParticles(ParticleTypes.ANGRY_VILLAGER, c.getX(), c.getY() + c.getHeight() + 0.3, c.getZ(), 1, 0, 0, 0, 0);
			world.spawnParticles(ParticleTypes.HEART, c.getX(), c.getY() + c.getHeight() + 0.2, c.getZ(), 2, 0.3, 0.2, 0.3, 0);
		}
		for (ServerPlayerEntity p : world.getEntitiesByClass(ServerPlayerEntity.class, player.getBoundingBox().expand(24),
				p -> p.isAlive() && !p.isSpectator())) {
			p.heal(4);
		}
		world.playSound(null, player.getBlockPos(), SoundEvents.EVENT_RAID_HORN.value(), SoundCategory.PLAYERS, 0.8f, 1.3f);
		world.spawnParticles(Echoes.SPIRIT, player.getX(), player.getY() + 1.6, player.getZ(), 30, 1.0, 0.4, 1.0, 0.05);
		return AbilityResult.OK;
	}

	/**
	 * The Whistle: the pack's order changes, Follow (close, fighting only back), Guard (in a ring around you, picking
	 * off what comes near, taking less damage) or Hunt (going after every monster within 24 blocks). It costs no
	 * voice. Sneaking gathers instead: your creatures within 64 blocks come to your side, on their own cooldown.
	 */
	public static AbilityResult whistle(ServerPlayerEntity player, RaceData data) {
		ServerWorld world = player.getServerWorld();
		if (player.isSneaking()) {
			if (!ready(player, data, GATHER_SLOT)) {
				return AbilityResult.COOLDOWN;
			}
			if (data.resource < GATHER_COST) {
				return AbilityResult.NO_RESOURCE;
			}
			// Beasts told to stay keep their post
			List<LivingEntity> creatures = Creatures.around(player, 64);
			creatures.removeIf(c -> c.getCommandTags().contains(Creatures.STAY_TAG));
			if (creatures.isEmpty()) {
				return AbilityResult.NO_CREATURE;
			}
			for (int i = 0; i < creatures.size(); i++) {
				LivingEntity c = creatures.get(i);
				world.spawnParticles(Echoes.SPIRIT, c.getX(), c.getY() + 1, c.getZ(), 10, 0.3, 0.5, 0.3, 0);
				Vec3d spot = Echoes.around(world, Creatures.near(player), i);
				c.refreshPositionAndAngles(spot.x, spot.y, spot.z, c.getYaw(), c.getPitch());
				if (c instanceof MobEntity mob) {
					mob.setTarget(null);
					mob.getNavigation().stop();
				}
			}
			world.playSound(null, player.getBlockPos(), SoundEvents.ENTITY_ENDERMAN_TELEPORT, SoundCategory.PLAYERS, 0.7f, 1.2f);
			data.addResource(-GATHER_COST);
			cooldown(player, data, GATHER_SLOT, GATHER_COOLDOWN);
			return AbilityResult.OK;
		}
		if (!ready(player, data, 2)) {
			return AbilityResult.COOLDOWN;
		}
		ShepherdData shepherd = data.shepherd;
		shepherd.stance = (shepherd.stance + 1) % 3;
		data.dirty = true;
		cooldown(player, data, 2, WHISTLE_COOLDOWN);
		for (LivingEntity c : Creatures.around(player, 32)) {
			// Called back to heel: whatever they chased that isn't hurting anyone of ours is let go
			if (shepherd.stance == ShepherdData.FOLLOW && c instanceof MobEntity mob && !threatens(mob.getTarget(), player)) {
				mob.setTarget(null);
			}
			world.spawnParticles(ParticleTypes.NOTE, c.getX(), c.getY() + c.getHeight() + 0.3, c.getZ(), 0, shepherd.stance / 2.0, 0, 0, 1);
		}
		float pitch = shepherd.stance == ShepherdData.HUNT ? 2.0f : shepherd.stance == ShepherdData.GUARD ? 1.6f : 1.2f;
		world.playSound(null, player.getBlockPos(), SoundEvents.BLOCK_NOTE_BLOCK_FLUTE.value(), SoundCategory.PLAYERS, 1.2f, pitch);
		world.playSound(null, player.getBlockPos(), SoundEvents.BLOCK_NOTE_BLOCK_FLUTE.value(), SoundCategory.PLAYERS, 1.0f, pitch * 0.85f);
		player.sendMessage(Text.translatable("mages_legacy.whistle." + shepherd.stance).formatted(Formatting.GOLD), true);
		return AbilityResult.OK;
	}

	/** Whether this mob is fighting the Shepherd or one of its creatures. */
	private static boolean threatens(@Nullable LivingEntity mob, ServerPlayerEntity owner) {
		if (!(mob instanceof MobEntity m) || !mob.isAlive()) {
			return false;
		}
		LivingEntity target = m.getTarget();
		return target == owner || (target != null && Creatures.isCreatureOf(target, owner.getUuid()));
	}

	/**
	 * The Whistle's order, once a second. Hunt: idle creatures go for the nearest monster within 24 blocks. Guard:
	 * monsters coming for the Shepherd are met by the nearest idle creature. Follow: whatever a creature chases far
	 * from the Shepherd without being a threat is let go.
	 */
	private static void stance(ServerPlayerEntity player, RaceData data, List<LivingEntity> near) {
		int stance = data.shepherd.stance;
		ServerWorld world = player.getServerWorld();
		if (stance == ShepherdData.FOLLOW) {
			for (LivingEntity c : near) {
				if (c instanceof MobEntity mob && mob.getTarget() != null && mob.getTarget().squaredDistanceTo(player) > 64
						&& !threatens(mob.getTarget(), player)) {
					mob.setTarget(null);
				}
			}
			return;
		}
		double radius = Creatures.huntRadius(stance);
		List<MobEntity> foes = world.getEntitiesByClass(MobEntity.class, player.getBoundingBox().expand(stance == ShepherdData.GUARD ? 12 : radius),
				e -> e.isAlive() && e instanceof Monster && Creatures.ownerOf(e) == null
						&& (stance == ShepherdData.HUNT || e.getTarget() == player || e.squaredDistanceTo(player) < radius * radius));
		if (foes.isEmpty()) {
			return;
		}
		for (LivingEntity c : near) {
			if (c instanceof MobEntity mob && (mob.getTarget() == null || !mob.getTarget().isAlive())
					&& mob.getAttributeInstance(EntityAttributes.GENERIC_ATTACK_DAMAGE) != null) {
				MobEntity nearest = null;
				for (MobEntity foe : foes) {
					if (nearest == null || foe.squaredDistanceTo(mob) < nearest.squaredDistanceTo(mob)) {
						nearest = foe;
					}
				}
				mob.setTarget(nearest);
			}
		}
	}

	/** Loss's Echo: one of your creatures fell (an echo faded in battle, a beast went to rest). */
	public static void onLoss(ServerPlayerEntity owner, RaceData data) {
		if (data.has(Talents.VOICE_LOSS)) {
			owner.addStatusEffect(new StatusEffectInstance(StatusEffects.ABSORPTION, 300, 0, true, true, true));
			data.addResource(10);
		}
	}

	@Nullable
	static LivingEntity lookedAt(ServerPlayerEntity player, double range, java.util.function.Predicate<LivingEntity> filter) {
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
		if (!(victim instanceof Monster)) {
			return;
		}
		Bonds.onHunt(owner, data, victim);
		// Every bonded beast near the fight learns from it; the one that struck the blow learns most
		for (ShepherdData.Bond bond : data.shepherd.bonds) {
			if (!bond.resting() && owner.getServerWorld().getEntity(bond.entity) instanceof MobEntity beast && beast.isAlive()
					&& beast.squaredDistanceTo(victim) < 16 * 16) {
				Bonds.beastXp(owner, data, bond, beast, beast == killer ? 10 : 4);
			}
		}
		if (killer == owner) {
			return;
		}
		Progression.addXp(owner, data, CREATURE_KILL_XP);
		if (Echoes.isEcho(killer) && data.has(Talents.LEGION_LEECH)) {
			data.addResource(5);
		}
	}

	public static void countKill(ServerPlayerEntity owner, RaceData data, EchoType type) {
		boolean before = learned(data, type);
		data.shepherd.kills.merge(type.id(), 1, Integer::sum);
		data.dirty = true;
		if (!before && learned(data, type)) {
			// The first echo learned goes straight into the first squad, so the Call works at once
			if (data.shepherd.presets.stream().allMatch(Map::isEmpty)) {
				data.shepherd.presets.get(0).put(type.id(), 1);
				data.shepherd.selectedEcho = type.id();
			}
			owner.sendMessage(Text.translatable("mages_legacy.echo.learned", type.type().getName()).formatted(Formatting.AQUA), false);
			owner.getWorld().playSound(null, owner.getBlockPos(), SoundEvents.PARTICLE_SOUL_ESCAPE, SoundCategory.PLAYERS, 1.5f, 0.6f);
			Progression.addXp(owner, data, ECHO_LEARNED_XP);
		}
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
			b.putInt("fedSeconds", (int) Math.max(0, (b.getLong("fedUntil") - time) / 20));
			b.putBoolean("petReady", b.getLong("pettedAt") == 0 || time - b.getLong("pettedAt") >= Bonds.PET_GAP);
		}
		nbt.putInt("capUsed", capacityUsed(data, player.getUuid()));
		nbt.putInt("capMax", capacityMax(player, data));
		nbt.putInt("echoes", Echoes.count(player.getUuid()));
		NbtCompound alive = new NbtCompound();
		Echoes.countByType(player.getUuid()).forEach(alive::putInt);
		nbt.put("alive", alive);
		NbtList rituals = new NbtList();
		long now = player.getServerWorld().getTime();
		data.shepherd.rituals.forEach((id, r) -> {
			if (r.listenUntil > now && !r.kind.isEmpty()) {
				NbtCompound e = new NbtCompound();
				e.putString("kind", r.kind);
				e.putInt("progress", r.feedings);
				e.putInt("minutes", (int) ((r.listenUntil - now) / 1200));
				rituals.add(e);
			}
		});
		nbt.put("rituals", rituals);
		return nbt;
	}
}
