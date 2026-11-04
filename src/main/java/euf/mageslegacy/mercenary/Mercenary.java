package euf.mageslegacy.mercenary;

import euf.mageslegacy.MagesLegacy;
import euf.mageslegacy.fx.Fx;
import euf.mageslegacy.mycelid.Allies;
import euf.mageslegacy.net.MercViewPayload;
import euf.mageslegacy.race.Abilities;
import euf.mageslegacy.race.Ability;
import euf.mageslegacy.race.Awakenings;
import euf.mageslegacy.race.Deeds;
import euf.mageslegacy.race.Mastery;
import euf.mageslegacy.race.Progression;
import euf.mageslegacy.race.Race;
import euf.mageslegacy.race.RaceState;
import euf.mageslegacy.race.Talent;
import euf.mageslegacy.race.Talents;
import euf.mageslegacy.voidborn.Voidborn;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import net.minecraft.ChatFormatting;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.OwnableEntity;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.monster.Shulker;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.common.Tags;
import net.neoforged.neoforge.event.entity.living.LivingDamageEvent;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import net.neoforged.neoforge.network.PacketDistributor;

/**
 * The Mercenaries: bounty hunters of the nameless mage's guild, who turn contracts into strength. No mana: their
 * abilities wait only on their cooldowns. Hunter's Brand (R) marks a foe with a three-part seal of Debt that blows up
 * on the third charge; the Guild Harpoon (V) drags the light to them and them to the heavy; the Mage's Shop (H) holds
 * the contracts and the wares. Awakenings: the Guild's Verdict (one foe judged by spectral blades), the Ransom (a Life
 * Debt deferring blows, paid off by fighting, and the Last Ransom bought with tokens at death's door) and the Guild
 * Legend (a banner that strengthens allies and cuts foes). Everything is worked out here, on the server; the clients
 * only draw it.
 */
public final class Mercenary {
	public static final int COPPER_RGB = 0xC8823C;
	public static final int GOLD_RGB = 0xD9A441;

	// ---- Hunter's Brand ----
	public static final double BRAND_RANGE = 20;
	public static final double BRAND_RANGE_FAR = 28;
	public static final float BRAND_HIT = 4;
	public static final int BRAND_TICKS = 10 * 20;
	public static final int BRAND_TICKS_LONG = 15 * 20;
	public static final int BRAND_CHARGES = 3;
	public static final int BRAND_CHARGES_COMPOUND = 4;
	public static final float BLAST = 6;
	public static final float BLAST_COMPOUND = 10;
	public static final float BLAST_PLAYER_CAP = 4;
	/** A charge counts no more often than this on one brand (multi-hits, odd projectiles). */
	public static final int CHARGE_GAP = 8;
	private static final float BLAST_SHIELD = 2;
	private static final float BLAST_SHIELD_CAP = 4;
	private static final float PLEDGE_SHIELD = 4;
	private static final float PLEDGE_SHIELD_CAP = 6;
	private static final int BLAST_SHIELD_TICKS = 4 * 20;

	// ---- Guild Harpoon ----
	public static final double HOOK_RANGE = 18;
	public static final double HOOK_RANGE_LONG = 24;
	public static final float HOOK_HIT = 2;
	public static final float HOOK_HIT_STUN = 4;
	public static final int HOOK_MAX_TICKS = 24;
	private static final double HOOK_SPEED = 1.6;
	private static final int FALL_GUARD = 20;
	private static final int DOUBLE_WINDOW = 3 * 20;
	private static final int PRECISE_WINDOW = 3 * 20;
	private static final float LIFELINE_SHIELD = 4;

	// ---- Awakenings ----
	public static final int VERDICT_TICKS = 15 * 20;
	public static final int VERDICT_TICKS_LONG = 20 * 20;
	public static final double VERDICT_RANGE = 20;
	public static final float VERDICT_BLADE = 3;
	public static final float VERDICT_BLADE_PLAYER = 2;
	public static final float VERDICT_EXECUTE = 8;
	public static final float VERDICT_EXECUTE_PLAYER = 4;
	public static final int VERDICT_BLADES = 5;
	public static final int VERDICT_BLADES_MORE = 7;
	public static final int VERDICT_EXECUTE_AFTER = 6;
	private static final int VERDICT_REFUND = 40 * 20;

	public static final int DEBT_TICKS = 10 * 20;
	public static final float DEBT_SHARE = 0.4f;
	public static final float DEBT_MAX = 12;
	public static final double DEBT_ALLY_RANGE = 12;
	public static final float DEBT_WAVE = 4;
	public static final float DEBT_WAVE_GOLD = 6;
	public static final double DEBT_WAVE_RADIUS = 4;
	public static final int RANSOM_TOKENS = 30;
	public static final int RANSOM_COOLDOWN = 20 * 60 * 20;

	public static final int BANNER_TICKS = 15 * 20;
	public static final int BANNER_TICKS_LONG = 18 * 20;
	public static final int BANNER_EXTEND_MAX = 6 * 20;
	public static final double BANNER_RADIUS = 10;
	public static final double BANNER_RADIUS_WIDE = 13;
	public static final float BANNER_DAMAGE = 0.15f;
	public static final float BANNER_WARD = 0.10f;
	public static final float BANNER_WARD_WALL = 0.15f;
	public static final float BANNER_BLADE = 3;
	private static final int BANNER_BLADE_EVERY = 40;

	// ---- the innate trait and the Craft ----
	public static final float PROFIT = 0.10f;
	public static final float PROFIT_TRACKED = 0.20f;
	public static final double PROFIT_RANGE = 12;
	private static final int SECOND_WIND_COOLDOWN = 60 * 20;

	/** Trial counters, in the progress tag. */
	public static final String T_BLASTS = "merc_blasts";
	public static final String T_HUNTS = "merc_hunts";
	public static final String T_CONTRACTS = "merc_contracts";
	public static final String T_KIND = "merc_kind_";
	public static final String T_SPENT = "merc_spent";
	public static final String T_HUNT_DAMAGE = "merc_hunt_damage";
	public static final int VERDICT_TRIAL_BLASTS = 20;
	public static final int VERDICT_TRIAL_HUNTS = 5;
	public static final int RANSOM_TRIAL_CONTRACTS = 10;
	public static final int RANSOM_TRIAL_SPENT = 80;
	public static final int LEGEND_TRIAL_CONTRACTS = 8;
	public static final int LEGEND_TRIAL_DAMAGE = 300;

	private static final ResourceLocation CUIRASS_ARMOR = MagesLegacy.id("merc_cuirass_armor");
	private static final ResourceLocation CUIRASS_TOUGHNESS = MagesLegacy.id("merc_cuirass_toughness");
	private static final ResourceLocation SHIELD_BLAST = MagesLegacy.id("merc_shield_blast");
	private static final ResourceLocation SHIELD_LIFELINE = MagesLegacy.id("merc_shield_lifeline");

	/** A Hunter's Brand on a creature: whose, how many charges of how many, until when, when the last charge came. */
	static final class Brand {
		final UUID owner;
		final UUID target;
		final ResourceKey<Level> level;
		int charges;
		final int max;
		long until;
		long lastCharge;
		/** Passed on by Joint Liability: such a brand doesn't pass on again. */
		final boolean spread;

		Brand(UUID owner, UUID target, ResourceKey<Level> level, int charges, int max, long until, long lastCharge, boolean spread) {
			this.owner = owner;
			this.target = target;
			this.level = level;
			this.charges = charges;
			this.max = max;
			this.until = until;
			this.lastCharge = lastCharge;
			this.spread = spread;
		}
	}

	/** A brand's bolt on its way: it lands on its targets (one; two with a Piercing Shot) when it arrives. */
	private record Bolt(UUID owner, ResourceKey<Level> level, List<UUID> targets, long arrive) {
	}

	/** The harpoon thrown: flying ({@code fly} ticks), then pulling for no more than {@link #HOOK_MAX_TICKS}. */
	static final class Hook {
		static final int PULL_TARGET = 0;
		static final int TO_ENTITY = 1;
		static final int TO_BLOCK = 2;
		final int mode;
		final UUID target;
		final Vec3 anchor;
		final int fly;
		final boolean ally;
		int tick;
		int stuck;
		Vec3 last;

		Hook(int mode, UUID target, Vec3 anchor, int fly, boolean ally) {
			this.mode = mode;
			this.target = target;
			this.anchor = anchor;
			this.fly = fly;
			this.ally = ally;
		}
	}

	/** The Guild's Verdict on one creature. */
	static final class Verdict {
		final UUID target;
		final ResourceKey<Level> level;
		final long until;
		int hits;
		int blades;
		boolean executed;
		boolean refunded;

		Verdict(UUID target, ResourceKey<Level> level, long until) {
			this.target = target;
			this.level = level;
			this.until = until;
		}
	}

	/** A Life Debt: who signed it, whom it shields, until when, what is owed. */
	static final class Debt {
		final UUID owner;
		final UUID ward;
		final ResourceKey<Level> level;
		final long until;
		float owed;
		boolean everOwed;

		Debt(UUID owner, UUID ward, ResourceKey<Level> level, long until) {
			this.owner = owner;
			this.ward = ward;
			this.level = level;
			this.until = until;
		}
	}

	/** The Guild Legend's banner. */
	static final class Banner {
		final UUID owner;
		final ResourceKey<Level> level;
		final Vec3 at;
		long until;
		int extended;
		final double radius;
		final float ward;
		final int targets;
		final int maxExtend;
		final Set<UUID> counted = new HashSet<>();
		int age;

		Banner(UUID owner, ResourceKey<Level> level, Vec3 at, long until, double radius, float ward, int targets) {
			this.owner = owner;
			this.level = level;
			this.at = at;
			this.until = until;
			this.radius = radius;
			this.ward = ward;
			this.targets = targets;
			this.maxExtend = BANNER_EXTEND_MAX;
		}
	}

	/** A Mercenary's live state, one per player. */
	static final class State {
		Hook hook;
		long fallGuard;
		long secondHook;
		/** The throw now asked for is Double Throw's second one. */
		boolean takingSecond;
		long precise;
		long secondWind;
		float huntDamage;
		/** Shields given by the Brand and the Lifeline: until when (their amounts are the attribute bonus itself). */
		long blastShieldUntil;
		long lifelineUntil;
		/** The newest brand this Mercenary laid (for Reassignment and the HUD). */
		UUID lastBranded;
		/** The account, cached with the tag it was read from (a hit reads it without parsing it again). */
		CompoundTag cachedTag;
		Account cached;
	}

	private static final Map<UUID, State> STATES = new HashMap<>();
	/** Brands by the creature they are on (a creature can bear one of each Mercenary). */
	private static final Map<UUID, List<Brand>> BRANDS = new HashMap<>();
	private static final List<Bolt> BOLTS = new ArrayList<>();
	private static final Map<UUID, Verdict> VERDICTS = new HashMap<>();
	private static final Map<UUID, Debt> DEBTS = new HashMap<>();
	private static final Map<UUID, Debt> WARDS = new HashMap<>();
	private static final Map<UUID, Banner> BANNERS = new HashMap<>();
	/** Who stands under a banner now: the best damage bonus and ward of any banner over them, and until when. */
	private static final Map<UUID, float[]> BUFFED = new HashMap<>();
	/** Allied creatures that got a Lifeline's shield: until when. */
	private static final Map<UUID, Long> ALLY_SHIELDS = new HashMap<>();
	/** Set while the Mercenary's own abilities strike, so their damage never counts as a blow. */
	private static int ownDamage;

	private Mercenary() {
	}

	public static void register() {
		NeoForge.EVENT_BUS.addListener(Mercenary::onPlayerTick);
		NeoForge.EVENT_BUS.addListener(Mercenary::onServerTick);
		NeoForge.EVENT_BUS.addListener(Mercenary::onIncoming);
		NeoForge.EVENT_BUS.addListener(EventPriority.LOW, Mercenary::onDamagePre);
		NeoForge.EVENT_BUS.addListener(Mercenary::onDamaged);
		NeoForge.EVENT_BUS.addListener(EventPriority.HIGHEST, Mercenary::onDeathFirst);
		NeoForge.EVENT_BUS.addListener(Mercenary::onDeath);
		NeoForge.EVENT_BUS.addListener((PlayerEvent.PlayerLoggedOutEvent e) -> {
			if (e.getEntity() instanceof ServerPlayer player) {
				leaving(player, true);
			}
		});
		NeoForge.EVENT_BUS.addListener((PlayerEvent.PlayerChangedDimensionEvent e) -> {
			if (e.getEntity() instanceof ServerPlayer player) {
				leaving(player, false);
			}
		});
	}

	static State state(Player player) {
		return STATES.computeIfAbsent(player.getUUID(), id -> new State());
	}

	public static boolean is(Player player) {
		return player instanceof ServerPlayer sp && RaceState.is(sp, Race.MERCENARY);
	}

	private static long now(ServerPlayer player) {
		return player.server.overworld().getGameTime();
	}

	private static long now(MinecraftServer server) {
		return server.overworld().getGameTime();
	}

	/** The account, read once per change of the stored tag. */
	public static Account account(ServerPlayer player) {
		State s = state(player);
		CompoundTag tag = player.getData(RaceState.MERC);
		if (s.cached == null || s.cachedTag != tag) {
			s.cached = Account.of(player);
			s.cachedTag = tag;
		}
		return s.cached;
	}

	/** Writes the account back (the cache then holds the new tag). */
	public static void save(ServerPlayer player, Account a) {
		CompoundTag tag = a.write();
		player.setData(RaceState.MERC, tag);
		State s = state(player);
		s.cached = a;
		s.cachedTag = tag;
	}

	// ---- who is whom ---------------------------------------------------------------------------------------------

	/**
	 * A foe of this Mercenary's: alive, not an ally (allied players, one's own pets), never someone else's pet or an
	 * armour stand; a player only with PvP on and the teams' leave.
	 */
	public static boolean foe(ServerPlayer owner, Entity e) {
		if (!(e instanceof LivingEntity living) || e == owner || !living.isAlive() || e instanceof ArmorStand || e.isSpectator()) {
			return false;
		}
		if (Allies.ally(owner, living)) {
			return false;
		}
		if (e instanceof OwnableEntity owned && owned.getOwnerUUID() != null) {
			return false;
		}
		if (e instanceof Player p) {
			return !p.isCreative() && owner.server.isPvpAllowed() && owner.canHarmPlayer(p);
		}
		return true;
	}

	/** A foe that fights: a monster, a mob after a player, or a hostile player (the banner's blades, Joint Liability). */
	static boolean fighter(ServerPlayer owner, LivingEntity e) {
		return foe(owner, e) && (e instanceof Enemy || e instanceof Player || e instanceof Mob mob && mob.getTarget() instanceof Player);
	}

	public static String typeId(Entity e) {
		return BuiltInRegistries.ENTITY_TYPE.getKey(e.getType()).toString();
	}

	/** Whether a hunt in this Mercenary's hand is after the creature. */
	public static boolean hunted(ServerPlayer player, Entity e) {
		return account(player).hunting(typeId(e));
	}

	private static boolean boss(Entity e) {
		return e.getType().is(Tags.EntityTypes.BOSSES);
	}

	/** Damage from the Mercenary's own abilities: never a blow of theirs, past the hurt pause. */
	static void strike(ServerPlayer owner, LivingEntity victim, float amount) {
		if (amount <= 0 || !victim.isAlive()) {
			return;
		}
		ownDamage++;
		try {
			victim.invulnerableTime = 0;
			victim.hurt(owner.damageSources().indirectMagic(owner, owner), amount);
		} finally {
			ownDamage--;
		}
	}

	private static float cap(LivingEntity target, float amount, float playerCap) {
		return target instanceof Player ? Math.min(amount, playerCap) : amount;
	}

	/** Gives a timed shield of absorption (no more than cap from this one source): a max-absorption bonus and the absorption itself. */
	private static void shield(LivingEntity e, ResourceLocation id, float amount, float cap) {
		AttributeInstance max = e.getAttribute(Attributes.MAX_ABSORPTION);
		if (max == null) {
			return;
		}
		AttributeModifier old = max.getModifier(id);
		double had = old == null ? 0 : old.amount();
		double now = Math.min(cap, had + amount);
		max.removeModifier(id);
		max.addTransientModifier(new AttributeModifier(id, now, AttributeModifier.Operation.ADD_VALUE));
		e.setAbsorptionAmount(e.getAbsorptionAmount() + (float) (now - had));
	}

	private static void unshield(LivingEntity e, ResourceLocation id) {
		AttributeInstance max = e.getAttribute(Attributes.MAX_ABSORPTION);
		if (max == null || max.getModifier(id) == null) {
			return;
		}
		double had = max.getModifier(id).amount();
		max.removeModifier(id);
		// What is left of this shield goes; what other sources gave stays
		e.setAbsorptionAmount((float) Math.max(0, Math.min(e.getAbsorptionAmount() - had + 0.0001, e.getMaxAbsorption())));
		if (e.getAbsorptionAmount() < 0.001f) {
			e.setAbsorptionAmount(0);
		}
	}

	/** What a raycast from the eyes finds first: a block or a creature this filter allows (never through a wall). */
	static HitResult look(ServerPlayer player, double range, java.util.function.Predicate<Entity> filter) {
		Vec3 eye = player.getEyePosition();
		Vec3 end = eye.add(player.getViewVector(1).scale(range));
		BlockHitResult block = player.level().clip(new ClipContext(eye, end, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, player));
		Vec3 stop = block.getType() == HitResult.Type.MISS ? end : block.getLocation();
		EntityHitResult entity = ProjectileUtil.getEntityHitResult(player.level(), player, eye, stop,
				player.getBoundingBox().expandTowards(stop.subtract(eye)).inflate(1), e -> e != player && filter.test(e));
		if (entity != null) {
			return entity;
		}
		return block;
	}

	// ---- the abilities ---------------------------------------------------------------------------------------------

	public static boolean busy(Player player) {
		State s = STATES.get(player.getUUID());
		return s != null && s.hook != null;
	}

	/** Double Throw: a second harpoon free within 3 s of the first (once). */
	public static boolean secondHook(ServerPlayer player) {
		State s = STATES.get(player.getUUID());
		if (s != null && s.secondHook > now(player)) {
			s.takingSecond = true;
			return true;
		}
		return false;
	}

	public static double brandRange(ServerPlayer player) {
		return Talents.has(player, Talent.BRAND_FAR) ? BRAND_RANGE_FAR : BRAND_RANGE;
	}

	/** Hunter's Brand (R): a fast bolt of copper and gold to the first foe in sight; it brands what it hits. */
	public static Abilities.Refusal brand(ServerPlayer player) {
		double range = brandRange(player);
		Vec3 eye = player.getEyePosition();
		Vec3 dir = player.getViewVector(1);
		Vec3 end = eye.add(dir.scale(range));
		BlockHitResult block = player.level().clip(new ClipContext(eye, end, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, player));
		Vec3 stop = block.getType() == HitResult.Type.MISS ? end : block.getLocation();
		List<UUID> targets = new ArrayList<>();
		Vec3 from = eye;
		Vec3 landed = stop;
		int want = Talents.has(player, Talent.BRAND_PIERCE) ? 2 : 1;
		Set<Entity> skip = new HashSet<>();
		for (int i = 0; i < want; i++) {
			EntityHitResult hit = ProjectileUtil.getEntityHitResult(player.level(), player, from, stop,
					new AABB(from, stop).inflate(1), e -> !skip.contains(e) && foe(player, e));
			if (hit == null) {
				break;
			}
			skip.add(hit.getEntity());
			targets.add(hit.getEntity().getUUID());
			if (i == 0) {
				landed = hit.getLocation();
			}
			from = hit.getLocation();
		}
		ServerLevel level = player.serverLevel();
		Vec3 hand = handPos(player);
		int ticks = (int) Math.max(2, Math.min(7, Math.ceil(hand.distanceTo(landed) / 3.5)));
		BOLTS.add(new Bolt(player.getUUID(), level.dimension(), targets, now(player) + ticks));
		Fx.brandBolt(level, player, hand, landed, ticks);
		level.playSound(null, player.blockPosition(), SoundEvents.CROSSBOW_SHOOT, SoundSource.PLAYERS, 0.8f, 1.5f);
		level.playSound(null, player.blockPosition(), SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.PLAYERS, 0.8f, 0.7f);
		return null;
	}

	static Vec3 handPos(ServerPlayer player) {
		Vec3 look = player.getViewVector(1);
		Vec3 side = new Vec3(-look.z, 0, look.x);
		side = side.lengthSqr() < 1.0E-6 ? Vec3.ZERO : side.normalize();
		return player.getEyePosition().add(look.scale(0.6)).add(side.scale(0.35)).add(0, -0.35, 0);
	}

	private static void tickBolts(MinecraftServer server) {
		long now = now(server);
		for (Iterator<Bolt> it = BOLTS.iterator(); it.hasNext();) {
			Bolt b = it.next();
			if (b.arrive() > now) {
				continue;
			}
			it.remove();
			ServerPlayer owner = server.getPlayerList().getPlayer(b.owner());
			ServerLevel level = server.getLevel(b.level());
			if (owner == null || level == null || owner.level() != level || !is(owner)) {
				continue;
			}
			for (UUID id : b.targets()) {
				if (level.getEntity(id) instanceof LivingEntity target && target.isAlive() && foe(owner, target)) {
					landBrand(owner, target);
				}
			}
		}
	}

	/** The bolt lands: 4 damage and the first charge of Debt (a brand already there only gets its time back). */
	static void landBrand(ServerPlayer owner, LivingEntity target) {
		ServerLevel level = owner.serverLevel();
		long now = now(owner);
		strike(owner, target, BRAND_HIT);
		level.playSound(null, target.blockPosition(), SoundEvents.ANVIL_LAND, SoundSource.PLAYERS, 0.35f, 1.9f);
		if (!target.isAlive()) {
			return;
		}
		int ticks = Talents.has(owner, Talent.BRAND_LINGER) ? BRAND_TICKS_LONG : BRAND_TICKS;
		int max = Talents.has(owner, Talent.BRAND_COMPOUND) ? BRAND_CHARGES_COMPOUND : BRAND_CHARGES;
		Brand brand = brandOn(target, owner.getUUID());
		State s = state(owner);
		if (brand != null) {
			brand.until = now + ticks;
		} else {
			int charges = 1;
			// Reassignment: the charges of the last brand move over to the new one (never enough to blow it at once)
			if (Talents.has(owner, Talent.BRAND_SWAP) && s.lastBranded != null && !s.lastBranded.equals(target.getUUID())) {
				Brand old = brandOn(s.lastBranded, owner.getUUID());
				if (old != null && old.until > now) {
					charges = Math.max(1, Math.min(max - 1, old.charges));
					removeBrand(level, old);
				}
			}
			brand = new Brand(owner.getUUID(), target.getUUID(), level.dimension(), charges, max, now + ticks, now, false);
			BRANDS.computeIfAbsent(target.getUUID(), id -> new ArrayList<>()).add(brand);
		}
		s.lastBranded = target.getUUID();
		Fx.brandSeal(level, target, brand.charges, brand.max, (int) (brand.until - now));
		Deeds.grant(owner, "merc_first_brand");
	}

	static Brand brandOn(UUID target, UUID owner) {
		List<Brand> list = BRANDS.get(target);
		if (list == null) {
			return null;
		}
		for (Brand b : list) {
			if (b.owner.equals(owner)) {
				return b;
			}
		}
		return null;
	}

	static Brand brandOn(Entity target, UUID owner) {
		return brandOn(target.getUUID(), owner);
	}

	public static boolean branded(Entity target, ServerPlayer owner) {
		Brand b = brandOn(target, owner.getUUID());
		return b != null && b.until > now(owner);
	}

	private static void removeBrand(ServerLevel level, Brand b) {
		List<Brand> list = BRANDS.get(b.target);
		if (list != null) {
			list.remove(b);
			if (list.isEmpty()) {
				BRANDS.remove(b.target);
			}
		}
		Entity e = level == null ? null : level.getEntity(b.target);
		if (e != null) {
			Fx.brandSeal(level, e, 0, b.max, 0);
		}
	}

	/** One more charge of Debt on a branded creature (a blow, a projectile, the harpoon): the last one blows the brand. */
	static void charge(ServerPlayer owner, LivingEntity target, boolean byHook) {
		Brand b = brandOn(target, owner.getUUID());
		long now = now(owner);
		if (b == null || b.until <= now || now - b.lastCharge < CHARGE_GAP) {
			return;
		}
		b.charges++;
		b.lastCharge = now;
		ServerLevel level = owner.serverLevel();
		if (b.charges >= b.max) {
			blast(owner, target, b, byHook);
		} else {
			Fx.brandSeal(level, target, b.charges, b.max, (int) (b.until - now));
			level.playSound(null, target.blockPosition(), SoundEvents.CHAIN_HIT, SoundSource.PLAYERS, 0.9f, 1.2f + 0.2f * b.charges);
		}
	}

	/** The brand blows: the Debt paid in one stroke, once (the brand is gone before any of it lands). */
	/** What a brand's burst deals to this creature: 6 (10 with Compound Interest), the talents' halves on top, 4 at most to a player. */
	public static float burstFor(ServerPlayer owner, LivingEntity target) {
		float damage = Talents.has(owner, Talent.BRAND_COMPOUND) ? BLAST_COMPOUND : BLAST;
		if (Talents.has(owner, Talent.BRAND_FINISHER) && target.getHealth() < target.getMaxHealth() * 0.3f) {
			damage *= 1.5f;
		}
		if (Talents.has(owner, Talent.CROSS_CONTRACT_KILL) && hunted(owner, target)) {
			damage *= 1.5f;
		}
		return cap(target, damage, BLAST_PLAYER_CAP);
	}

	static void blast(ServerPlayer owner, LivingEntity target, Brand b, boolean byHook) {
		ServerLevel level = owner.serverLevel();
		removeBrand(null, b);
		boolean hostile = target instanceof Enemy;
		float damage = burstFor(owner, target);
		Vec3 at = target.position().add(0, target.getBbHeight() * 0.6, 0);
		strike(owner, target, damage);
		Fx.brandBlast(level, at, Talents.has(owner, Talent.BRAND_CHAIN));
		level.playSound(null, target.blockPosition(), SoundEvents.ANVIL_PLACE, SoundSource.PLAYERS, 0.5f, 1.6f);
		level.playSound(null, target.blockPosition(), SoundEvents.CHAIN_BREAK, SoundSource.PLAYERS, 1, 0.8f);
		Fx.shake(owner, 0.8f, 5);
		// The pay-out: a shield of absorption for the Mercenary
		boolean pledge = Talents.has(owner, Talent.BRAND_PLEDGE);
		shield(owner, SHIELD_BLAST, pledge ? PLEDGE_SHIELD : BLAST_SHIELD, pledge ? PLEDGE_SHIELD_CAP : BLAST_SHIELD_CAP);
		state(owner).blastShieldUntil = now(owner) + BLAST_SHIELD_TICKS;
		if (Talents.has(owner, Talent.BRAND_CHAIN)) {
			for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class, target.getBoundingBox().inflate(3),
					e -> e != target && fighter(owner, e) && e.distanceTo(target) <= 3)) {
				strike(owner, e, cap(e, (Talents.has(owner, Talent.BRAND_COMPOUND) ? BLAST_COMPOUND : BLAST) / 2, BLAST_PLAYER_CAP / 2));
			}
		}
		if (Talents.has(owner, Talent.BRAND_SPREAD) && !b.spread) {
			LivingEntity next = null;
			double best = 36;
			for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class, target.getBoundingBox().inflate(6),
					e -> e != target && fighter(owner, e) && brandOn(e, owner.getUUID()) == null)) {
				double d = e.distanceToSqr(target);
				if (d < best) {
					best = d;
					next = e;
				}
			}
			if (next != null) {
				long now = now(owner);
				int ticks = Talents.has(owner, Talent.BRAND_LINGER) ? BRAND_TICKS_LONG : BRAND_TICKS;
				Brand passed = new Brand(owner.getUUID(), next.getUUID(), level.dimension(), 1, b.max, now + ticks, now, true);
				BRANDS.computeIfAbsent(next.getUUID(), id -> new ArrayList<>()).add(passed);
				Fx.brandBolt(level, owner, at, next.position().add(0, next.getBbHeight() * 0.6, 0), 3);
				Fx.brandSeal(level, next, 1, b.max, ticks);
			}
		}
		if (Talents.has(owner, Talent.CRAFT_SHOULDER)) {
			for (LivingEntity ally : level.getEntitiesOfClass(LivingEntity.class, owner.getBoundingBox().inflate(8),
					e -> e != owner && e.isAlive() && Allies.ally(owner, e))) {
				ally.addEffect(new MobEffectInstance(MobEffects.DAMAGE_RESISTANCE, 60, 0, true, true), owner);
			}
		}
		Voidborn.State vs = Voidborn.state(owner);
		if (Talents.has(owner, Talent.CROSS_RHYTHM)) {
			vs.cooldown[Ability.GUILD_HARPOON.slot] = Math.max(0, vs.cooldown[Ability.GUILD_HARPOON.slot] - 60);
		}
		if (Talents.has(owner, Talent.BRAND_COLLECT) && !target.isAlive()) {
			vs.cooldown[Ability.HUNTER_BRAND.slot] = 0;
		}
		if (hostile) {
			count(owner, T_BLASTS, 1);
			Mastery.add(owner, Mastery.Goal.BRAND_BLASTS);
			if (byHook) {
				Mastery.add(owner, Mastery.Goal.HOOK_BLASTS);
			}
		}
		Deeds.grant(owner, "merc_first_blast");
		if (byHook) {
			Deeds.grant(owner, "merc_combo");
		}
		RaceState.sync(owner);
	}

	private static void tickBrands(MinecraftServer server) {
		long now = now(server);
		for (Iterator<Map.Entry<UUID, List<Brand>>> it = BRANDS.entrySet().iterator(); it.hasNext();) {
			List<Brand> list = it.next().getValue();
			list.removeIf(b -> {
				if (b.until > now) {
					return false;
				}
				ServerLevel level = server.getLevel(b.level);
				Entity e = level == null ? null : level.getEntity(b.target);
				if (e != null) {
					Fx.brandSeal(level, e, 0, b.max, 0);
				}
				return true;
			});
			if (list.isEmpty()) {
				it.remove();
			}
		}
	}

	public static double hookRange(ServerPlayer player) {
		return Talents.has(player, Talent.HARP_LONG) ? HOOK_RANGE_LONG : HOOK_RANGE;
	}

	/**
	 * The Guild Harpoon (V): a hook on a golden chain to what is in sight. A light creature is dragged to the
	 * Mercenary; to a heavy one, a boss, a fixed one or a player the Mercenary is drawn; to a block, the Mercenary
	 * flies. Nothing in reach: nothing thrown, nothing spent.
	 */
	public static Abilities.Refusal harpoon(ServerPlayer player) {
		double range = hookRange(player);
		boolean lifeline = Talents.has(player, Talent.CRAFT_LIFELINE);
		HitResult hit = look(player, range, e -> foe(player, e) || lifeline && e instanceof LivingEntity l && l.isAlive() && Allies.ally(player, l)
				&& !(e instanceof ArmorStand));
		ServerLevel level = player.serverLevel();
		Hook hook;
		Vec3 to;
		Entity target = null;
		if (hit instanceof EntityHitResult eh) {
			target = eh.getEntity();
			to = eh.getLocation();
			boolean ally = !foe(player, target);
			int mode = ally || heavy(target) ? Hook.TO_ENTITY : Hook.PULL_TARGET;
			hook = new Hook(mode, target.getUUID(), to, flyTicks(player, to), ally);
		} else if (hit instanceof BlockHitResult bh && bh.getType() != HitResult.Type.MISS) {
			to = bh.getLocation();
			Vec3 back = player.getViewVector(1).scale(-0.6);
			hook = new Hook(Hook.TO_BLOCK, null, to.add(back), flyTicks(player, to), false);
		} else {
			state(player).takingSecond = false;
			return new Abilities.Refusal("mages_legacy.refuse.no_anchor");
		}
		State s = state(player);
		s.hook = hook;
		hook.last = player.position();
		if (s.takingSecond) {
			s.takingSecond = false;
			s.secondHook = 0;
		} else if (Talents.has(player, Talent.HARP_DOUBLE)) {
			s.secondHook = now(player) + hook.fly + HOOK_MAX_TICKS + DOUBLE_WINDOW;
		}
		Fx.hook(level, player, to, target, hook.fly, hook.fly + HOOK_MAX_TICKS);
		level.playSound(null, player.blockPosition(), SoundEvents.TRIDENT_THROW.value(), SoundSource.PLAYERS, 1, 0.8f);
		level.playSound(null, player.blockPosition(), SoundEvents.CHAIN_PLACE, SoundSource.PLAYERS, 1, 0.9f);
		return null;
	}

	private static int flyTicks(ServerPlayer player, Vec3 to) {
		return (int) Math.max(2, Math.min(7, Math.ceil(player.getEyePosition().distanceTo(to) / 3.5)));
	}

	/** Too heavy to drag: a boss, a big or very hardy body, a shulker, a player. */
	static boolean heavy(Entity e) {
		if (e instanceof Player || e instanceof Shulker || boss(e) || !(e instanceof Mob)) {
			return true;
		}
		LivingEntity l = (LivingEntity) e;
		return e.getBbWidth() > 1.4f || l.getMaxHealth() > 60;
	}

	private static void tickHook(ServerPlayer player, State s) {
		Hook h = s.hook;
		h.tick++;
		ServerLevel level = player.serverLevel();
		Entity target = h.target == null ? null : level.getEntity(h.target);
		if (h.target != null && (target == null || !target.isAlive())) {
			endHook(player, s);
			return;
		}
		if (h.tick < h.fly) {
			return;
		}
		if (h.tick == h.fly) {
			arrive(player, s, h, target);
			if (s.hook == null) {
				return;
			}
		}
		int pulled = h.tick - h.fly;
		if (pulled > HOOK_MAX_TICKS) {
			endHook(player, s);
			return;
		}
		if (h.mode == Hook.PULL_TARGET) {
			Vec3 d = player.position().subtract(target.position());
			double dist = d.length();
			if (dist <= 1.8 + target.getBbWidth() / 2) {
				target.setDeltaMovement(target.getDeltaMovement().scale(0.2));
				target.hurtMarked = true;
				endHook(player, s);
				return;
			}
			Vec3 v = d.normalize().scale(Math.min(1.3, 0.35 * dist + 0.3));
			if (target.onGround()) {
				v = v.add(0, 0.12, 0);
			}
			target.setDeltaMovement(v);
			target.hurtMarked = true;
			target.fallDistance = 0;
			if (stuck(h, target.position())) {
				endHook(player, s);
			}
		} else {
			Vec3 anchor = target != null ? target.position().add(0, target.getBbHeight() * 0.5, 0) : h.anchor;
			Vec3 body = player.position().add(0, 0.9, 0);
			Vec3 d = anchor.subtract(body);
			double dist = d.length();
			double reach = target != null ? target.getBbWidth() / 2 + 1.2 : 1.1;
			if (dist <= reach) {
				player.setDeltaMovement(player.getDeltaMovement().scale(0.25));
				player.hurtMarked = true;
				endHook(player, s);
				return;
			}
			Vec3 v = d.normalize().scale(Math.min(HOOK_SPEED, 0.4 * dist + 0.4));
			player.setDeltaMovement(v);
			player.hurtMarked = true;
			player.fallDistance = 0;
			if (stuck(h, player.position())) {
				endHook(player, s);
			}
		}
	}

	/** No headway for three ticks (a wall in the way): the chain lets go. */
	private static boolean stuck(Hook h, Vec3 pos) {
		if (h.last != null && pos.distanceToSqr(h.last) < 0.0025 && h.tick - h.fly > 2) {
			h.stuck++;
		} else {
			h.stuck = 0;
		}
		h.last = pos;
		return h.stuck >= 3;
	}

	/** The hook strikes home: the blow, a charge of Debt, the Craft's gifts. */
	private static void arrive(ServerPlayer player, State s, Hook h, Entity target) {
		ServerLevel level = player.serverLevel();
		level.playSound(null, BlockPosOf(h.anchor), SoundEvents.ANVIL_LAND, SoundSource.PLAYERS, 0.4f, 1.4f);
		if (!(target instanceof LivingEntity living)) {
			return;
		}
		if (h.ally) {
			shield(player, SHIELD_LIFELINE, LIFELINE_SHIELD, LIFELINE_SHIELD);
			shield(living, SHIELD_LIFELINE, LIFELINE_SHIELD, LIFELINE_SHIELD);
			long until = now(player) + 6 * 20;
			s.lifelineUntil = until;
			ALLY_SHIELDS.put(living.getUUID(), until);
			return;
		}
		boolean stun = Talents.has(player, Talent.HARP_STUN);
		strike(player, living, cap(living, stun ? HOOK_HIT_STUN : HOOK_HIT, 2));
		if (stun && h.mode == Hook.PULL_TARGET) {
			living.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 30, 2), player);
		}
		if (Talents.has(player, Talent.HARP_PRECISE)) {
			s.precise = now(player) + PRECISE_WINDOW;
		}
		Voidborn.State vs = Voidborn.state(player);
		if (Talents.has(player, Talent.CROSS_GUILD_GRAB) && hunted(player, living)) {
			vs.cooldown[Ability.HUNTER_BRAND.slot] = Math.max(0, vs.cooldown[Ability.HUNTER_BRAND.slot] - 80);
		}
		Brand b = brandOn(living, player.getUUID());
		if (b != null && b.until > now(player)) {
			if (Talents.has(player, Talent.CROSS_TENSION)) {
				b.until = now(player) + (Talents.has(player, Talent.BRAND_LINGER) ? BRAND_TICKS_LONG : BRAND_TICKS);
			}
			// The harpoon is a blow of its own: the charge counts whatever the last blow was
			b.lastCharge = Math.min(b.lastCharge, now(player) - CHARGE_GAP);
			charge(player, living, true);
		}
		Fx.shake(player, 0.6f, 4);
		if (!living.isAlive()) {
			endHook(player, s);
		}
	}

	private static net.minecraft.core.BlockPos BlockPosOf(Vec3 v) {
		return net.minecraft.core.BlockPos.containing(v);
	}

	private static void endHook(ServerPlayer player, State s) {
		if (s.hook == null) {
			return;
		}
		if (s.hook.mode != Hook.PULL_TARGET) {
			s.fallGuard = now(player) + FALL_GUARD;
			player.fallDistance = 0;
		}
		s.hook = null;
		Fx.hookEnd(player.serverLevel(), player);
		player.level().playSound(null, player.blockPosition(), SoundEvents.CHAIN_BREAK, SoundSource.PLAYERS, 0.5f, 1.4f);
	}

	/** The Mage's Shop (H): the guild's book opens, a door of warm light stands before the Mercenary. */
	public static Abilities.Refusal openShop(ServerPlayer player) {
		Account a = account(player);
		Contracts.rotate(player, a);
		if (a.offers.isEmpty()) {
			Contracts.fill(player, a);
		}
		save(player, a);
		ServerLevel level = player.serverLevel();
		Vec3 look = player.getViewVector(1).multiply(1, 0, 1);
		look = look.lengthSqr() < 1.0E-4 ? new Vec3(0, 0, 1) : look.normalize();
		Fx.door(level, player.position().add(look.scale(2.6)), player.getYRot(), 6000);
		level.playSound(null, player.blockPosition(), SoundEvents.WOODEN_DOOR_OPEN, SoundSource.PLAYERS, 1, 0.6f);
		level.playSound(null, player.blockPosition(), SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.PLAYERS, 1, 1.2f);
		PacketDistributor.sendToPlayer(player, new MercViewPayload(view(player, a)));
		return null;
	}

	/** The Guild's Verdict (an Awakening): one foe in sight judged for 15 seconds. */
	public static Abilities.Refusal verdict(ServerPlayer player) {
		HitResult hit = look(player, VERDICT_RANGE, e -> foe(player, e));
		if (!(hit instanceof EntityHitResult eh) || !(eh.getEntity() instanceof LivingEntity target)) {
			return new Abilities.Refusal("mages_legacy.refuse.no_target");
		}
		ServerLevel level = player.serverLevel();
		endVerdict(player.server, player.getUUID());
		int ticks = Talents.has(player, Talent.VERDICT_LONG) ? VERDICT_TICKS_LONG : VERDICT_TICKS;
		VERDICTS.put(player.getUUID(), new Verdict(target.getUUID(), level.dimension(), now(player) + ticks));
		Fx.verdictSeal(level, target, player, ticks);
		Fx.shake(player, 1.2f, 8);
		level.playSound(null, target.blockPosition(), SoundEvents.BELL_BLOCK, SoundSource.PLAYERS, 1, 0.6f);
		level.playSound(null, player.blockPosition(), SoundEvents.ANVIL_USE, SoundSource.PLAYERS, 0.6f, 0.5f);
		return null;
	}

	public static UUID condemned(ServerPlayer player) {
		Verdict v = VERDICTS.get(player.getUUID());
		return v == null ? null : v.target;
	}

	private static void endVerdict(MinecraftServer server, UUID owner) {
		Verdict v = VERDICTS.remove(owner);
		if (v == null) {
			return;
		}
		ServerLevel level = server.getLevel(v.level);
		Entity e = level == null ? null : level.getEntity(v.target);
		if (e != null) {
			Fx.verdictSeal(level, e, null, 0);
		}
	}

	/** A blow on the condemned: every second one calls a spectral blade; the sixth calls the execution. */
	private static void verdictBlow(ServerPlayer player, Verdict v, LivingEntity target) {
		v.hits++;
		ServerLevel level = player.serverLevel();
		float boss = boss(target) && Talents.has(player, Talent.VERDICT_BOSS) ? 1.5f : 1;
		int max = Talents.has(player, Talent.VERDICT_BLADES) ? VERDICT_BLADES_MORE : VERDICT_BLADES;
		if (v.hits % 2 == 0 && v.blades < max) {
			v.blades++;
			strike(player, target, target instanceof Player ? VERDICT_BLADE_PLAYER : VERDICT_BLADE * boss);
			Fx.verdictBlade(level, target, v.blades);
			level.playSound(null, target.blockPosition(), SoundEvents.PLAYER_ATTACK_SWEEP, SoundSource.PLAYERS, 1, 1.3f);
		}
		if (v.hits >= VERDICT_EXECUTE_AFTER && !v.executed && target.isAlive()) {
			v.executed = true;
			strike(player, target, target instanceof Player ? VERDICT_EXECUTE_PLAYER : VERDICT_EXECUTE * boss);
			Fx.verdictExecute(level, target);
			Fx.shake(player, 2, 10);
			level.playSound(null, target.blockPosition(), SoundEvents.ANVIL_LAND, SoundSource.PLAYERS, 1, 0.5f);
			level.playSound(null, target.blockPosition(), SoundEvents.BELL_RESONATE, SoundSource.PLAYERS, 1, 0.8f);
			if (Talents.has(player, Talent.VERDICT_TRIUMPH)) {
				player.addEffect(new MobEffectInstance(MobEffects.DAMAGE_BOOST, 6 * 20, 0, true, true));
				player.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SPEED, 6 * 20, 0, true, true));
			}
			Deeds.grant(player, "merc_execution");
		}
	}

	/** The Ransom's Life Debt (an Awakening): on oneself, or with Shift on an ally in sight within 12 blocks. */
	public static Abilities.Refusal lifeDebt(ServerPlayer player) {
		LivingEntity ward = player;
		if (player.isShiftKeyDown()) {
			HitResult hit = look(player, DEBT_ALLY_RANGE, e -> e instanceof LivingEntity l && l.isAlive() && e != player && Allies.ally(player, l)
					&& !(e instanceof ArmorStand));
			if (!(hit instanceof EntityHitResult eh)) {
				return new Abilities.Refusal("mages_legacy.refuse.no_ally");
			}
			ward = (LivingEntity) eh.getEntity();
		}
		if (DEBTS.containsKey(player.getUUID())) {
			return new Abilities.Refusal("mages_legacy.refuse.debt_running");
		}
		Debt other = WARDS.get(ward.getUUID());
		if (other != null) {
			return new Abilities.Refusal("mages_legacy.refuse.debt_taken");
		}
		ServerLevel level = player.serverLevel();
		Debt d = new Debt(player.getUUID(), ward.getUUID(), level.dimension(), now(player) + DEBT_TICKS);
		DEBTS.put(player.getUUID(), d);
		WARDS.put(ward.getUUID(), d);
		Fx.debtSeal(level, ward, DEBT_TICKS);
		level.playSound(null, ward.blockPosition(), SoundEvents.BOOK_PAGE_TURN, SoundSource.PLAYERS, 1, 0.6f);
		level.playSound(null, ward.blockPosition(), SoundEvents.AMETHYST_BLOCK_RESONATE, SoundSource.PLAYERS, 1, 0.7f);
		return null;
	}

	/** The Life Debt this player signed, if any (for the HUD and the tests). */
	public static float debtOwed(ServerPlayer owner) {
		Debt d = DEBTS.get(owner.getUUID());
		return d == null ? 0 : d.owed;
	}

	/** The Life Debt shielding this creature: what is owed (0 if none). */
	public static float debtOn(Entity ward) {
		Debt d = WARDS.get(ward.getUUID());
		return d == null ? 0 : d.owed;
	}

	/** What the Debt would cost if it ended now: half of what is left (a quarter less first with the Write-Off). */
	public static float settlement(ServerPlayer owner, float owed) {
		return owed * (Talents.has(owner, Talent.RANSOM_WRITE_OFF) ? 0.75f : 1) * 0.5f;
	}

	/** Pays part of a Debt down; paid off before its end, it ends well: health back, a golden wave. */
	private static void repay(MinecraftServer server, Debt d, float amount) {
		if (amount <= 0 || d.owed <= 0) {
			return;
		}
		d.owed = Math.max(0, d.owed - amount);
		if (d.owed > 0.0001f || !d.everOwed) {
			return;
		}
		ServerPlayer owner = server.getPlayerList().getPlayer(d.owner);
		closeDebt(server, d);
		if (owner == null) {
			return;
		}
		ServerLevel level = owner.serverLevel();
		owner.heal(2);
		float wave = Talents.has(owner, Talent.RANSOM_GOLD) ? DEBT_WAVE_GOLD : DEBT_WAVE;
		for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class, owner.getBoundingBox().inflate(DEBT_WAVE_RADIUS),
				e -> fighter(owner, e) && e.distanceTo(owner) <= DEBT_WAVE_RADIUS)) {
			strike(owner, e, cap(e, wave, 4));
		}
		Fx.debtWave(level, owner.position(), (float) DEBT_WAVE_RADIUS);
		Fx.shake(owner, 1.2f, 8);
		level.playSound(null, owner.blockPosition(), SoundEvents.PLAYER_LEVELUP, SoundSource.PLAYERS, 0.6f, 1.6f);
		level.playSound(null, owner.blockPosition(), SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.PLAYERS, 1, 0.6f);
		owner.displayClientMessage(Component.translatable("mages_legacy.merc.debt_paid").withStyle(ChatFormatting.GOLD), true);
		Deeds.grant(owner, "merc_debt_paid");
	}

	private static void closeDebt(MinecraftServer server, Debt d) {
		DEBTS.remove(d.owner);
		WARDS.remove(d.ward);
		ServerLevel level = server.getLevel(d.level);
		Entity ward = level == null ? null : level.getEntity(d.ward);
		if (ward != null) {
			Fx.debtSeal(level, ward, 0);
		}
	}

	/** The Debt's end with something left: half of it (after any Write-Off) falls on the ward, deferred no more. */
	private static void settle(MinecraftServer server, Debt d) {
		closeDebt(server, d);
		ServerPlayer owner = server.getPlayerList().getPlayer(d.owner);
		float due = owner != null ? settlement(owner, d.owed) : d.owed * 0.5f;
		if (due <= 0.01f) {
			return;
		}
		for (ServerLevel level : server.getAllLevels()) {
			if (level.getEntity(d.ward) instanceof LivingEntity ward && ward.isAlive()) {
				payDue(ward, due);
				return;
			}
		}
	}

	private static void payDue(LivingEntity ward, float due) {
		ownDamage++;
		try {
			ward.invulnerableTime = 0;
			ward.hurt(ward.damageSources().magic(), due);
		} finally {
			ownDamage--;
		}
		if (ward.level() instanceof ServerLevel level) {
			Fx.debtDue(level, ward);
			level.playSound(null, ward.blockPosition(), SoundEvents.CHAIN_BREAK, SoundSource.PLAYERS, 1, 0.5f);
		}
		if (ward instanceof ServerPlayer p) {
			p.displayClientMessage(Component.translatable("mages_legacy.merc.debt_due", String.format("%.1f", due)).withStyle(ChatFormatting.RED), true);
		}
	}

	private static void tickDebts(MinecraftServer server) {
		long now = now(server);
		for (Debt d : List.copyOf(DEBTS.values())) {
			if (d.until <= now) {
				settle(server, d);
			}
		}
	}

	public static long ransomReady(ServerPlayer player) {
		return account(player).ransomReady;
	}

	/** The Guild Legend (an Awakening): a banner where the Mercenary stands. */
	public static Abilities.Refusal legend(ServerPlayer player) {
		ServerLevel level = player.serverLevel();
		endBanner(player.server, player.getUUID());
		int ticks = Talents.has(player, Talent.LEGEND_LONG) ? BANNER_TICKS_LONG : BANNER_TICKS;
		double radius = Talents.has(player, Talent.LEGEND_WIDE) ? BANNER_RADIUS_WIDE : BANNER_RADIUS;
		float ward = Talents.has(player, Talent.LEGEND_WARD) ? BANNER_WARD_WALL : BANNER_WARD;
		int targets = Talents.has(player, Talent.LEGEND_BLADES) ? 4 : 3;
		Banner b = new Banner(player.getUUID(), level.dimension(), player.position(), now(player) + ticks, radius, ward, targets);
		BANNERS.put(player.getUUID(), b);
		Fx.banner(level, player, b.at, (float) radius, ticks);
		Fx.shake(player, 1.4f, 8);
		level.playSound(null, player.blockPosition(), SoundEvents.ANVIL_PLACE, SoundSource.PLAYERS, 0.7f, 0.6f);
		level.playSound(null, player.blockPosition(), SoundEvents.RAID_HORN.value(), SoundSource.PLAYERS, 0.5f, 1.4f);
		Deeds.grant(player, "merc_legend");
		return null;
	}

	private static void endBanner(MinecraftServer server, UUID owner) {
		Banner b = BANNERS.remove(owner);
		if (b == null) {
			return;
		}
		ServerLevel level = server.getLevel(b.level);
		ServerPlayer p = server.getPlayerList().getPlayer(owner);
		if (level != null) {
			Fx.bannerEnd(level, p, b.at);
		}
	}

	public static long bannerLeft(ServerPlayer player) {
		Banner b = BANNERS.get(player.getUUID());
		return b == null ? 0 : Math.max(0, b.until - now(player));
	}

	private static void tickBanners(MinecraftServer server) {
		long now = now(server);
		BUFFED.values().removeIf(v -> v[2] < now);
		for (Banner b : List.copyOf(BANNERS.values())) {
			ServerPlayer owner = server.getPlayerList().getPlayer(b.owner);
			ServerLevel level = server.getLevel(b.level);
			if (owner == null || level == null || b.until <= now || !is(owner) || owner.level() != level) {
				endBanner(server, b.owner);
				continue;
			}
			b.age++;
			if (b.age % 10 == 1) {
				AABB box = new AABB(b.at, b.at).inflate(b.radius, b.radius / 2 + 2, b.radius);
				for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class, box, e -> e.isAlive() && Allies.ally(owner, e)
						&& e.position().distanceTo(b.at) <= b.radius)) {
					float[] buff = BUFFED.computeIfAbsent(e.getUUID(), id -> new float[3]);
					// Two banners never add up: the strongest holds
					buff[0] = Math.max(buff[0], BANNER_DAMAGE);
					buff[1] = Math.max(buff[1], b.ward);
					buff[2] = now + 12;
				}
			}
			if (b.age % BANNER_BLADE_EVERY == 0) {
				bannerBlades(owner, level, b);
			}
		}
	}

	/** The banner's blades: up to 3 (4) foes in its circle, the branded ones first, then the nearest. */
	private static void bannerBlades(ServerPlayer owner, ServerLevel level, Banner b) {
		AABB box = new AABB(b.at, b.at).inflate(b.radius, b.radius / 2 + 2, b.radius);
		List<LivingEntity> foes = new ArrayList<>(level.getEntitiesOfClass(LivingEntity.class, box,
				e -> fighter(owner, e) && e.position().distanceTo(b.at) <= b.radius));
		foes.sort((x, y) -> {
			boolean bx = branded(x, owner);
			boolean by = branded(y, owner);
			if (bx != by) {
				return bx ? -1 : 1;
			}
			return Double.compare(x.position().distanceToSqr(b.at), y.position().distanceToSqr(b.at));
		});
		for (int i = 0; i < Math.min(b.targets, foes.size()); i++) {
			LivingEntity e = foes.get(i);
			strike(owner, e, cap(e, BANNER_BLADE, 2));
			Fx.bannerBlade(level, b.at, e);
		}
		if (!foes.isEmpty()) {
			level.playSound(null, BlockPosOf(b.at), SoundEvents.PLAYER_ATTACK_SWEEP, SoundSource.PLAYERS, 0.8f, 1.5f);
		}
	}

	/** The damage bonus of a banner over this creature (0 if none). */
	public static float bannerDamage(Entity e) {
		float[] buff = BUFFED.get(e.getUUID());
		return buff == null ? 0 : buff[0];
	}

	public static float bannerWard(Entity e) {
		float[] buff = BUFFED.get(e.getUUID());
		return buff == null ? 0 : buff[1];
	}

	// ---- the events ------------------------------------------------------------------------------------------------

	private static void onServerTick(ServerTickEvent.Post event) {
		MinecraftServer server = event.getServer();
		tickBolts(server);
		tickBrands(server);
		tickDebts(server);
		tickBanners(server);
		long now = now(server);
		for (Verdict v : List.copyOf(VERDICTS.values())) {
			if (v.until <= now) {
				VERDICTS.entrySet().stream().filter(e -> e.getValue() == v).findFirst().ifPresent(e -> endVerdict(server, e.getKey()));
			}
		}
		if (!ALLY_SHIELDS.isEmpty() && now % 10 == 0) {
			for (Iterator<Map.Entry<UUID, Long>> it = ALLY_SHIELDS.entrySet().iterator(); it.hasNext();) {
				Map.Entry<UUID, Long> e = it.next();
				if (e.getValue() <= now) {
					it.remove();
					for (ServerLevel level : server.getAllLevels()) {
						if (level.getEntity(e.getKey()) instanceof LivingEntity l) {
							unshield(l, SHIELD_LIFELINE);
						}
					}
				}
			}
		}
	}

	private static void onPlayerTick(PlayerTickEvent.Post event) {
		if (!(event.getEntity() instanceof ServerPlayer player)) {
			return;
		}
		// A Life Debt that couldn't be paid when its debtor left: paid two seconds after the next login
		if (player.tickCount == 40 && player.getData(RaceState.MERC).getFloat("debtOwed") > 0) {
			Account a = Account.of(player);
			float due = a.debtOwed;
			a.debtOwed = 0;
			a.save(player);
			STATES.remove(player.getUUID());
			payDue(player, due);
		}
		State s = STATES.get(player.getUUID());
		if (!RaceState.is(player, Race.MERCENARY)) {
			if (s != null) {
				cleanup(player);
			}
			return;
		}
		s = state(player);
		if (s.hook != null) {
			tickHook(player, s);
		}
		long now = now(player);
		if (s.blastShieldUntil > 0 && s.blastShieldUntil <= now) {
			s.blastShieldUntil = 0;
			unshield(player, SHIELD_BLAST);
		}
		if (s.lifelineUntil > 0 && s.lifelineUntil <= now) {
			s.lifelineUntil = 0;
			unshield(player, SHIELD_LIFELINE);
		}
		if (player.tickCount % 20 == 0) {
			tickSecond(player, s);
		}
	}

	private static void tickSecond(ServerPlayer player, State s) {
		Account a = account(player);
		boolean changed = Contracts.rotate(player, a);
		changed |= Contracts.tickSecond(player, a);
		if (changed) {
			save(player, a);
		}
		// The bar under the slots: how far the first contract in hand is
		float shown = a.active.isEmpty() ? 0 : 100 * a.active.get(0).share();
		if (Math.abs(shown - RaceState.resource(player)) > 0.01f) {
			RaceState.setResource(player, shown);
		}
		attribute(player, Attributes.ARMOR, CUIRASS_ARMOR, 2, Talents.has(player, Talent.CRAFT_CUIRASS));
		attribute(player, Attributes.ARMOR_TOUGHNESS, CUIRASS_TOUGHNESS, 1, Talents.has(player, Talent.CRAFT_CUIRASS));
		ServerLevel level = player.serverLevel();
		if (a.hasHunts()) {
			// The Tip-off: the quarry within 32 blocks glows
			if (Talents.has(player, Talent.CON_SCOUT)) {
				for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class, player.getBoundingBox().inflate(32),
						e -> e.isAlive() && a.hunting(typeId(e)))) {
					e.addEffect(new MobEffectInstance(MobEffects.GLOWING, 40, 0, true, false), player);
				}
			}
			// A Nose for Profit: every two seconds, the nearest quarry within 12 blocks rings faintly from its side
			if (player.tickCount % 40 == 0) {
				LivingEntity nearest = null;
				double best = PROFIT_RANGE * PROFIT_RANGE;
				for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class, player.getBoundingBox().inflate(PROFIT_RANGE),
						e -> e.isAlive() && a.hunting(typeId(e)))) {
					double d = e.distanceToSqr(player);
					if (d < best) {
						best = d;
						nearest = e;
					}
				}
				if (nearest != null) {
					Fx.profitPing(player, nearest.position().add(0, nearest.getBbHeight() * 0.5, 0));
				}
			}
		}
	}

	private static void attribute(ServerPlayer player, Holder<Attribute> attribute, ResourceLocation id, double amount, boolean want) {
		AttributeInstance instance = player.getAttribute(attribute);
		if (instance == null || want == instance.hasModifier(id)) {
			return;
		}
		if (want) {
			instance.addTransientModifier(new AttributeModifier(id, amount, AttributeModifier.Operation.ADD_VALUE));
		} else {
			instance.removeModifier(id);
		}
	}

	private static boolean melee(DamageSource source, ServerPlayer player) {
		return source.is(DamageTypes.PLAYER_ATTACK) && source.getDirectEntity() == player;
	}

	/** A real blow of the player's: a melee strike or their own projectile (never an ability, thorns or the like). */
	private static boolean direct(DamageSource source, ServerPlayer player) {
		return ownDamage == 0 && source.getEntity() == player && (melee(source, player)
				|| source.getDirectEntity() instanceof Projectile p && p.getOwner() == player);
	}

	/** Before armour: the Mercenary's bonuses, the banners' strength and ward, the harpoon's soft landing. */
	private static void onIncoming(LivingIncomingDamageEvent event) {
		LivingEntity victim = event.getEntity();
		DamageSource source = event.getSource();
		if (victim instanceof ServerPlayer player && source.is(DamageTypeTags.IS_FALL)) {
			State s = STATES.get(player.getUUID());
			if (s != null && (s.hook != null || s.fallGuard > now(player))) {
				event.setCanceled(true);
				return;
			}
		}
		float amount = event.getAmount();
		if (amount <= 0) {
			return;
		}
		if (source.getEntity() instanceof LivingEntity attacker && attacker != victim) {
			float banner = bannerDamage(attacker);
			if (banner > 0 && ownDamage == 0) {
				amount *= 1 + banner;
			}
		}
		float ward = bannerWard(victim);
		if (ward > 0) {
			amount *= 1 - ward;
		}
		if (source.getEntity() instanceof ServerPlayer player && victim != player && is(player) && ownDamage == 0) {
			float bonus = 0;
			if (hunted(player, victim)) {
				bonus += Talents.has(player, Talent.CON_TRACKER) ? PROFIT_TRACKED : PROFIT;
			}
			boolean melee = melee(source, player);
			if (melee && Talents.has(player, Talent.CRAFT_BLADE)) {
				bonus += 0.12f;
			}
			if (source.is(DamageTypeTags.IS_PROJECTILE) && Talents.has(player, Talent.CRAFT_BOW)) {
				bonus += 0.15f;
			}
			State s = state(player);
			if (melee && s.precise > now(player)) {
				s.precise = 0;
				bonus += 0.4f;
			}
			amount *= 1 + Math.min(1, bonus);
		}
		event.setAmount(amount);
	}

	/**
	 * After armour: a Life Debt defers 40% of what really lands (up to 12 owed; never what passes through every
	 * protection: the void, /kill); Second Wind catches a fall under 30% of health.
	 */
	private static void onDamagePre(LivingDamageEvent.Pre event) {
		LivingEntity victim = event.getEntity();
		float amount = event.getNewDamage();
		if (amount <= 0 || ownDamage > 0) {
			return;
		}
		Debt d = WARDS.get(victim.getUUID());
		if (d != null && !event.getSource().is(DamageTypeTags.BYPASSES_INVULNERABILITY)) {
			float room = DEBT_MAX - d.owed;
			float defer = Math.min(amount * DEBT_SHARE, Math.max(0, room));
			if (defer > 0) {
				d.owed += defer;
				d.everOwed = true;
				amount -= defer;
				event.setNewDamage(amount);
			}
		}
		if (victim instanceof ServerPlayer player && is(player) && Talents.has(player, Talent.CRAFT_SECOND_WIND)) {
			State s = state(player);
			float after = player.getHealth() - amount;
			if (after > 0 && after < player.getMaxHealth() * 0.3f && s.secondWind <= now(player)) {
				s.secondWind = now(player) + SECOND_WIND_COOLDOWN;
				player.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SPEED, 80, 1, true, true));
				player.addEffect(new MobEffectInstance(MobEffects.REGENERATION, 80, 0, true, true));
				player.level().playSound(null, player.blockPosition(), SoundEvents.PLAYER_BREATH, SoundSource.PLAYERS, 1, 1.2f);
			}
		}
	}

	/** After the blow landed: charges of Debt, the Verdict's count, the Life Debt paid down, the Legend's trial. */
	private static void onDamaged(LivingDamageEvent.Post event) {
		LivingEntity victim = event.getEntity();
		DamageSource source = event.getSource();
		float dealt = event.getNewDamage();
		if (dealt <= 0 || ownDamage > 0 || !(source.getEntity() instanceof ServerPlayer player) || victim == player) {
			return;
		}
		MinecraftServer server = player.server;
		// The Life Debt is paid down by the blows of its signer (and, a tenth as well, by the ward's own)
		if (direct(source, player)) {
			Debt signed = DEBTS.get(player.getUUID());
			if (signed != null && !signed.ward.equals(victim.getUUID()) && foe(player, victim)) {
				float rate = !signed.ward.equals(player.getUUID()) && Talents.has(player, Talent.RANSOM_GUARANTOR) ? 0.3f : 0.2f;
				if (branded(victim, player)) {
					rate = Math.max(rate, 0.3f);
				}
				repay(server, signed, dealt * rate);
			}
			Debt shielding = WARDS.get(player.getUUID());
			if (shielding != null && !shielding.owner.equals(player.getUUID()) && foe(player, victim)) {
				repay(server, shielding, dealt * 0.1f);
			}
		}
		if (!is(player) || !direct(source, player)) {
			return;
		}
		State s = state(player);
		if (hunted(player, victim) && foe(player, victim)) {
			s.huntDamage += dealt;
			int whole = (int) s.huntDamage;
			if (whole > 0) {
				count(player, T_HUNT_DAMAGE, whole);
				s.huntDamage -= whole;
			}
		}
		Verdict v = VERDICTS.get(player.getUUID());
		if (v != null && v.target.equals(victim.getUUID())) {
			verdictBlow(player, v, victim);
		}
		if (victim.isAlive()) {
			charge(player, victim, false);
		}
	}

	/** The Last Ransom: a killing blow bought off for 30 tokens (a Totem of Undying in hand acts first, alone). */
	private static void onDeathFirst(LivingDeathEvent event) {
		if (!(event.getEntity() instanceof ServerPlayer player) || !is(player) || event.getSource().is(DamageTypeTags.BYPASSES_INVULNERABILITY)
				|| Awakenings.slotOf(Talents.owned(player), Ability.LIFE_DEBT) < 0) {
			return;
		}
		if (player.getMainHandItem().is(Items.TOTEM_OF_UNDYING) || player.getOffhandItem().is(Items.TOTEM_OF_UNDYING)) {
			return;
		}
		Account a = account(player);
		long now = now(player);
		if (a.tokens < RANSOM_TOKENS || a.ransomReady > now) {
			return;
		}
		a.tokens -= RANSOM_TOKENS;
		a.ransomReady = now + RANSOM_COOLDOWN;
		save(player, a);
		event.setCanceled(true);
		player.setHealth(player.getMaxHealth() * 0.4f);
		player.clearFire();
		if (Talents.has(player, Talent.RANSOM_SECOND)) {
			player.addEffect(new MobEffectInstance(MobEffects.DAMAGE_RESISTANCE, 60, 0, true, true));
		}
		ServerLevel level = player.serverLevel();
		Fx.ransom(level, player);
		Fx.shake(player, 2, 12);
		level.playSound(null, player.blockPosition(), SoundEvents.TOTEM_USE, SoundSource.PLAYERS, 0.6f, 1.3f);
		level.playSound(null, player.blockPosition(), SoundEvents.VILLAGER_TRADE, SoundSource.PLAYERS, 1, 0.5f);
		player.displayClientMessage(Component.translatable("mages_legacy.merc.ransom", RANSOM_TOKENS).withStyle(ChatFormatting.GOLD), false);
		Deeds.grant(player, "merc_ransom");
		RaceState.sync(player);
	}

	/** Deaths: hunts counted (a Partner's kill near a Mercenary too), the Verdict's end, the banner's time. */
	private static void onDeath(LivingDeathEvent event) {
		LivingEntity victim = event.getEntity();
		MinecraftServer server = victim.getServer();
		if (server == null) {
			return;
		}
		// A ward who dies owes nothing more
		Debt warded = WARDS.get(victim.getUUID());
		if (warded != null) {
			closeDebt(server, warded);
		}
		for (Map.Entry<UUID, Verdict> e : List.copyOf(VERDICTS.entrySet())) {
			if (e.getValue().target.equals(victim.getUUID())) {
				ServerPlayer owner = server.getPlayerList().getPlayer(e.getKey());
				Verdict v = e.getValue();
				if (owner != null && event.getSource().getEntity() == owner && Talents.has(owner, Talent.VERDICT_REFUND) && !v.refunded) {
					v.refunded = true;
					int slot = Awakenings.slotOf(Talents.owned(owner), Ability.GUILD_VERDICT);
					if (slot >= 0) {
						Voidborn.State vs = Voidborn.state(owner);
						vs.cooldown[slot] = Math.max(0, vs.cooldown[slot] - VERDICT_REFUND);
					}
				}
				endVerdict(server, e.getKey());
			}
		}
		if (victim instanceof Player || victim instanceof ArmorStand) {
			removeBrandsOf(victim);
			return;
		}
		Entity killerEntity = event.getSource().getEntity();
		String type = typeId(victim);
		if (killerEntity instanceof ServerPlayer killer && is(killer)) {
			Account a = account(killer);
			boolean trophy = Talents.has(killer, Talent.CROSS_TROPHY) && branded(victim, killer);
			if (a.hunting(type)) {
				Contracts.onKill(killer, a, type, trophy);
				save(killer, a);
			}
			Banner b = BANNERS.get(killer.getUUID());
			if (b != null && b.level == victim.level().dimension() && victim.position().distanceTo(b.at) <= b.radius && fighterDead(victim)
					&& b.counted.add(victim.getUUID()) && b.extended < b.maxExtend) {
				int add = Math.min(40, b.maxExtend - b.extended);
				b.extended += add;
				b.until += add;
				Fx.banner(killer.serverLevel(), killer, b.at, (float) b.radius, (int) (b.until - now(killer)));
			}
		}
		// Partners: an ally's kill near a Mercenary counts towards that Mercenary's hunts
		if (killerEntity instanceof LivingEntity killer && victim.level() instanceof ServerLevel level) {
			for (ServerPlayer merc : level.players()) {
				if (merc == killer || !is(merc) || merc.distanceTo(victim) > 24 || !Talents.has(merc, Talent.CON_PARTNERS) || !Allies.ally(merc, killer)) {
					continue;
				}
				Account a = account(merc);
				if (a.hunting(type)) {
					Contracts.onKill(merc, a, type, false);
					save(merc, a);
				}
			}
		}
		removeBrandsOf(victim);
	}

	/** A creature that counted as a foe when it died (not a pet, not a stand). */
	private static boolean fighterDead(LivingEntity e) {
		return e instanceof Enemy || e instanceof Mob mob && mob.getTarget() instanceof Player;
	}

	private static void removeBrandsOf(Entity e) {
		List<Brand> list = BRANDS.remove(e.getUUID());
		if (list != null && e.level() instanceof ServerLevel level && !list.isEmpty()) {
			Fx.brandSeal(level, e, 0, list.get(0).max, 0);
		}
	}

	/** Leaving the server (or this world): the harpoon lets go; the Debts this player signed or bore are settled. */
	private static void leaving(ServerPlayer player, boolean logout) {
		MinecraftServer server = player.server;
		State s = STATES.get(player.getUUID());
		if (s != null && s.hook != null) {
			s.hook = null;
		}
		Debt warded = WARDS.get(player.getUUID());
		if (warded != null) {
			if (logout) {
				// Whoever leaves with a debt still pays it: at the next login
				closeDebt(server, warded);
				ServerPlayer owner = server.getPlayerList().getPlayer(warded.owner);
				float due = owner != null ? settlement(owner, warded.owed) : warded.owed * 0.5f;
				if (due > 0.01f) {
					Account a = Account.of(player);
					a.debtOwed += due;
					a.save(player);
				}
			} else {
				settle(server, warded);
			}
		}
		Debt signed = DEBTS.get(player.getUUID());
		if (signed != null && !signed.ward.equals(player.getUUID())) {
			settle(server, signed);
		}
		endVerdict(server, player.getUUID());
		if (logout) {
			endBanner(server, player.getUUID());
			BOLTS.removeIf(b -> b.owner().equals(player.getUUID()));
			STATES.remove(player.getUUID());
		}
	}

	/** The race given up (or reset): every live effect of the Mercenary's gone, the attribute bonuses off. */
	public static void reset(ServerPlayer player) {
		cleanup(player);
	}

	private static void cleanup(ServerPlayer player) {
		MinecraftServer server = player.server;
		State s = STATES.remove(player.getUUID());
		if (s != null && s.hook != null) {
			Fx.hookEnd(player.serverLevel(), player);
		}
		Debt signed = DEBTS.get(player.getUUID());
		if (signed != null) {
			settle(server, signed);
		}
		endVerdict(server, player.getUUID());
		endBanner(server, player.getUUID());
		BOLTS.removeIf(b -> b.owner().equals(player.getUUID()));
		for (List<Brand> list : List.copyOf(BRANDS.values())) {
			for (Brand b : List.copyOf(list)) {
				if (b.owner.equals(player.getUUID())) {
					removeBrand(server.getLevel(b.level), b);
				}
			}
		}
		attribute(player, Attributes.ARMOR, CUIRASS_ARMOR, 0, false);
		attribute(player, Attributes.ARMOR_TOUGHNESS, CUIRASS_TOUGHNESS, 0, false);
		unshield(player, SHIELD_BLAST);
		unshield(player, SHIELD_LIFELINE);
	}

	// ---- the account's news ----------------------------------------------------------------------------------------

	/** Tokens spent in the Shop: the Ransom's trial. */
	static void spent(ServerPlayer player, Account a, int price) {
		count(player, T_SPENT, price);
		if (a.spent >= 100) {
			Deeds.grant(player, "merc_customer");
		}
	}

	/** A contract done: Silver Guarantee's gifts. */
	static void contractDone(ServerPlayer player, Account a, Contract c) {
		if (Talents.has(player, Talent.CROSS_SILVER)) {
			player.heal(6);
			Voidborn.state(player).cooldown[Ability.GUILD_HARPOON.slot] = 0;
			Debt d = DEBTS.get(player.getUUID());
			if (d != null) {
				repay(player.server, d, 4);
			}
		}
	}

	/** Trial counters count only for a Mercenary out of the testing mode. */
	private static void count(ServerPlayer player, String key, int amount) {
		if (is(player) && !Talents.noCooldown(player)) {
			Talents.addProgress(player, key, amount);
		}
	}

	// ---- what the clients see ---------------------------------------------------------------------------------------

	/**
	 * For the HUD (after the shared trial numbers in the sync): tokens; the newest brand's charges, its size and ticks
	 * left; the Life Debt signed: owed ×10, ticks left, what its end would cost ×10; the Verdict's blows and ticks left;
	 * the banner's ticks left; seconds until the Last Ransom; the trial counters.
	 */
	public static int[] hud(ServerPlayer player) {
		int[] out = new int[HUD_SIZE];
		if (!is(player)) {
			return out;
		}
		Account a = account(player);
		State s = state(player);
		long now = now(player);
		out[0] = a.tokens;
		Brand b = s.lastBranded == null ? null : brandOn(s.lastBranded, player.getUUID());
		if (b != null && b.until > now) {
			out[1] = b.charges;
			out[2] = b.max;
			out[3] = (int) (b.until - now);
		}
		Debt d = DEBTS.get(player.getUUID());
		if (d != null) {
			out[4] = Math.round(d.owed * 10);
			out[5] = (int) Math.max(0, d.until - now);
			out[6] = Math.round(settlement(player, d.owed) * 10);
		}
		Verdict v = VERDICTS.get(player.getUUID());
		if (v != null) {
			out[7] = v.hits;
			out[8] = (int) Math.max(0, v.until - now);
		}
		out[9] = (int) bannerLeft(player);
		out[10] = (int) Math.max(0, (a.ransomReady - now + 19) / 20);
		CompoundTag p = player.getData(RaceState.PROGRESS);
		out[11] = p.getInt(T_BLASTS);
		out[12] = p.getInt(T_HUNTS);
		out[13] = p.getInt(T_CONTRACTS);
		out[14] = p.getInt(T_KIND + "hunt");
		out[15] = p.getInt(T_KIND + "deliver");
		out[16] = p.getInt(T_KIND + "explore");
		out[17] = p.getInt(T_SPENT);
		out[18] = p.getInt(T_HUNT_DAMAGE);
		var owned = Talents.owned(player);
		out[19] = Talents.trialDone(Talent.AWAKENING_VERDICT, owned, p) ? 1 : 0;
		out[20] = Talents.trialDone(Talent.AWAKENING_RANSOM, owned, p) ? 1 : 0;
		out[21] = Talents.trialDone(Talent.AWAKENING_LEGEND, owned, p) ? 1 : 0;
		return out;
	}

	public static final int HUD_SIZE = 22;

	/** The pinned contract for the HUD line: "kind|target|progress|amount", or empty. */
	public static String note(ServerPlayer player) {
		if (!is(player)) {
			return "";
		}
		Account a = account(player);
		if (a.active.isEmpty()) {
			return "";
		}
		Contract c = a.active.get(0);
		return c.kind.id() + "|" + c.target + "|" + c.progress + "|" + c.amount;
	}

	/** The book as the client shows it: the board, the hand, the wares, every price and pay already worked out. */
	public static CompoundTag view(ServerPlayer player, Account a) {
		CompoundTag tag = new CompoundTag();
		tag.putInt("tokens", a.tokens);
		tag.putInt("hand", Contracts.handSlots(player));
		tag.putInt("board", Contracts.offerCount(player));
		tag.putInt("reroll", Shop.discounted(player, Contracts.rerollPrice(player, a)));
		tag.putLong("nextDay", Contracts.untilNextDay(player));
		tag.putInt("tiers", Progression.openTiers(player));
		tag.putInt("streak", Talents.has(player, Talent.CON_STREAK) ? a.streak : -1);
		tag.putInt("completed", a.completed);
		ListTag offers = new ListTag();
		for (Contract c : a.offers) {
			offers.add(contractView(player, a, c));
		}
		tag.put("offers", offers);
		ListTag active = new ListTag();
		for (Contract c : a.active) {
			CompoundTag ct = contractView(player, a, c);
			if (c.kind == Contract.Kind.DELIVER) {
				ResourceLocation id = ResourceLocation.tryParse(c.target);
				int have = 0;
				if (id != null) {
					var item = BuiltInRegistries.ITEM.get(id);
					for (int i = 0; i < player.getInventory().getContainerSize(); i++) {
						if (player.getInventory().getItem(i).is(item)) {
							have += player.getInventory().getItem(i).getCount();
						}
					}
				}
				ct.putInt("have", have);
			}
			if (c.kind == Contract.Kind.EXPLORE && c.located) {
				double dx = c.locX - player.getX();
				double dz = c.locZ - player.getZ();
				ct.putInt("far", (int) (Math.round(Math.sqrt(dx * dx + dz * dz) / 50.0) * 50));
				// 0 north, clockwise in eighths
				double yaw = Math.toDegrees(Math.atan2(dx, -dz));
				ct.putInt("dir", (int) Math.floorMod(Math.round(yaw / 45.0), 8));
			}
			active.add(ct);
		}
		tag.put("active", active);
		ListTag wares = new ListTag();
		int open = Progression.openTiers(player);
		for (Shop.Ware w : Shop.CATALOG) {
			boolean today = w.kind() == Shop.Kind.SERVICE || a.stock.contains(w.id());
			if (!today) {
				continue;
			}
			CompoundTag wt = new CompoundTag();
			wt.putString("id", w.id());
			wt.putString("cat", w.category().id());
			wt.putBoolean("service", w.kind() == Shop.Kind.SERVICE);
			wt.putInt("tier", w.tier());
			wt.putInt("price", Shop.price(player, a, w));
			wt.putInt("base", w.price());
			wt.putBoolean("deal", a.deals.contains(w.id()));
			wt.putBoolean("soldOut", Shop.soldOut(a, w));
			wt.putBoolean("locked", !Shop.tierOpen(player, w.tier()));
			wt.putBoolean("new", w.tier() > Math.max(1, a.seenTier) && w.tier() <= open);
			wt.putInt("left", w.dailyMax() > 0 ? w.dailyMax() - a.bought.getOrDefault(w.id(), 0) : -1);
			wt.put("icon", w.icon().get().saveOptional(player.registryAccess()));
			wares.add(wt);
		}
		tag.put("wares", wares);
		return tag;
	}

	private static CompoundTag contractView(ServerPlayer player, Account a, Contract c) {
		CompoundTag t = c.save();
		int[] pay = Contracts.pay(player, a, c, true);
		t.putInt("payXp", pay[0]);
		t.putInt("payTokens", pay[1]);
		t.putBoolean("hunted", c.kind == Contract.Kind.HUNT);
		return t;
	}

	/** What the book asked for (the server checks everything again), then the fresh view. */
	public static void act(ServerPlayer player, int action, int id, String ware) {
		if (!is(player)) {
			return;
		}
		Account a = account(player);
		Contracts.rotate(player, a);
		switch (action) {
			case 1 -> Contracts.accept(player, a, id);
			case 2 -> Contracts.abandon(player, a, id);
			case 3 -> Contracts.deliver(player, a, id);
			case 4 -> Contracts.reroll(player, a);
			case 5 -> Shop.buy(player, a, ware);
			case 6 -> {
				// The book closed: the door swings shut; the wares seen are no longer new
				a.seenTier = Math.max(a.seenTier, Progression.openTiers(player));
				save(player, a);
				Fx.doorClose(player.serverLevel(), player);
				player.level().playSound(null, player.blockPosition(), SoundEvents.WOODEN_DOOR_CLOSE, SoundSource.PLAYERS, 1, 0.6f);
				return;
			}
			case 7 -> {
				// Pin: this contract first (the HUD and the bar follow the first)
				Contract c = a.activeById(id);
				if (c != null) {
					a.active.remove(c);
					a.active.add(0, c);
				}
			}
			default -> {
			}
		}
		save(player, a);
		RaceState.sync(player);
		PacketDistributor.sendToPlayer(player, new MercViewPayload(view(player, a)));
	}

	// ---- for the harness and the admin command ----

	/** Charges of this Mercenary's brand on a creature (0: none). */
	public static int brandCharges(Entity target, ServerPlayer owner) {
		Brand b = brandOn(target, owner.getUUID());
		return b == null || b.until <= now(owner) ? 0 : b.charges;
	}

	/** Ticks left of this Mercenary's brand on a creature. */
	public static long brandLeft(Entity target, ServerPlayer owner) {
		Brand b = brandOn(target, owner.getUUID());
		return b == null ? 0 : Math.max(0, b.until - now(owner));
	}

	/** The bolt's landing, at once (no flight). */
	public static void brandNow(ServerPlayer owner, LivingEntity target) {
		landBrand(owner, target);
	}

	/** A charge as a blow would add it (the gap between charges still holds). */
	public static void chargeNow(ServerPlayer owner, LivingEntity target) {
		charge(owner, target, false);
	}

	/** How much the banner has been lengthened by kills, in ticks (-1: no banner). */
	public static int bannerExtended(ServerPlayer player) {
		Banner b = BANNERS.get(player.getUUID());
		return b == null ? -1 : b.extended;
	}

	public static boolean hooking(Player player) {
		return busy(player);
	}

	/** For the tests: tokens set straight (the admin command). */
	public static void setTokens(ServerPlayer player, int tokens) {
		Account a = account(player);
		a.tokens = Math.max(0, tokens);
		save(player, a);
	}
}
