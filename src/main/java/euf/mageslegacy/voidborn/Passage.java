package euf.mageslegacy.voidborn;

import euf.mageslegacy.fx.Fx;
import euf.mageslegacy.net.OpenPassagePayload;
import euf.mageslegacy.race.Abilities;
import euf.mageslegacy.race.Ability;
import euf.mageslegacy.race.Awakenings;
import euf.mageslegacy.race.RaceState;
import euf.mageslegacy.race.Talent;
import euf.mageslegacy.race.Talents;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.OwnableEntity;
import net.minecraft.world.entity.animal.Animal;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import net.neoforged.neoforge.network.PacketDistributor;

/**
 * The Awakening "Through Worlds": the Voidborn feels space so finely that they see the faint ripples every other
 * soul leaves in it, and can open the way to any of them. The key shows who is in the world now; once one is
 * chosen, a rift opens before the Voidborn and another before the one chosen, joined for 15 seconds, however far
 * apart and in whatever world. Players go through both ways, and their pets; with Caravan any animal and mount.
 * The ability holds up to three charges (two of them from talents), each recovering on its own.
 */
public final class Passage {
	private static final int TICKS = 15 * 20;
	/** An entity that just went through waits this long before it can be taken back. */
	private static final int REST = 30;
	public static final int MAX_CHARGES = 3;

	private record Gate(UUID owner, ResourceKey<Level> levelA, Vec3 a, Vec3 aOut, ResourceKey<Level> levelB, Vec3 b, Vec3 bOut, long until) {
	}

	private static final List<Gate> GATES = new ArrayList<>();
	private static final Map<UUID, Long> RESTING = new HashMap<>();

	private Passage() {
	}

	public static void register() {
		NeoForge.EVENT_BUS.addListener(Passage::onServerTick);
	}

	/** How many charges this Voidborn has: one, a second and a third from the talents. */
	public static int charges(ServerPlayer player) {
		int n = 1;
		if (Talents.has(player, Talent.WORLDS_CHARGE_2)) {
			n++;
			if (Talents.has(player, Talent.WORLDS_CHARGE_3)) {
				n++;
			}
		}
		return n;
	}

	/** How many charges are ready now. */
	public static int ready(ServerPlayer player) {
		long now = player.serverLevel().getGameTime();
		long[] at = Voidborn.state(player).passageReady;
		int n = 0;
		for (int i = 0; i < charges(player); i++) {
			if (now >= at[i]) {
				n++;
			}
		}
		return n;
	}

	/** Ticks until the next charge comes back (0 if one is ready). */
	public static int nextIn(ServerPlayer player) {
		long now = player.serverLevel().getGameTime();
		long[] at = Voidborn.state(player).passageReady;
		long soonest = Long.MAX_VALUE;
		for (int i = 0; i < charges(player); i++) {
			if (now >= at[i]) {
				return 0;
			}
			soonest = Math.min(soonest, at[i] - now);
		}
		return soonest == Long.MAX_VALUE ? 0 : (int) soonest;
	}

	/** Why the passage can't be opened from here now, or null. */
	private static Abilities.Refusal check(ServerPlayer player) {
		if (Awakenings.slotOf(Talents.owned(player), Ability.PASSAGE) < 0) {
			return new Abilities.Refusal("mages_legacy.refuse.no_awakening");
		}
		if (!open(player.level())) {
			return new Abilities.Refusal("mages_legacy.refuse.passage_here");
		}
		if (BlackHole.holding(player) || Voidborn.stepping(player)) {
			return new Abilities.Refusal("mages_legacy.refuse.busy");
		}
		if (ready(player) == 0) {
			return new Abilities.Refusal("mages_legacy.refuse.no_charge");
		}
		if (RaceState.resource(player) < Abilities.cost(player, Ability.PASSAGE)) {
			return new Abilities.Refusal("mages_legacy.refuse.resource.voidborn");
		}
		return null;
	}

	/** Pockets and arenas are closed worlds: no passage leads into or out of them. */
	private static boolean open(Level level) {
		return !Pocket.isPocket(level) && !Arena.isArena(level);
	}

	/** The key: if a passage may be opened, the Voidborn is shown who they can open it to. */
	public static Abilities.Refusal choose(ServerPlayer player) {
		Abilities.Refusal why = check(player);
		if (why != null) {
			return why;
		}
		if (player.server.getPlayerList().getPlayerCount() < 2) {
			return new Abilities.Refusal("mages_legacy.refuse.no_one");
		}
		PacketDistributor.sendToPlayer(player, new OpenPassagePayload());
		return null;
	}

	/** The choice made on the screen: opens the passage to that player, if it still may be. */
	public static void openTo(ServerPlayer player, UUID targetId) {
		Abilities.Refusal why = check(player);
		ServerPlayer target = player.server.getPlayerList().getPlayer(targetId);
		if (why == null && (target == null || target == player || !target.isAlive() || target.isSpectator())) {
			why = new Abilities.Refusal("mages_legacy.refuse.no_one");
		}
		if (why == null && !open(target.level())) {
			why = new Abilities.Refusal("mages_legacy.refuse.passage_there");
		}
		if (why != null) {
			Abilities.refuse(player, Component.translatable(why.key()));
			return;
		}
		ServerLevel here = player.serverLevel();
		ServerLevel there = target.serverLevel();
		// One charge spent, the one that has rested longest
		long now = here.getGameTime();
		long[] at = Voidborn.state(player).passageReady;
		int pick = -1;
		for (int i = 0; i < charges(player); i++) {
			if (now >= at[i] && (pick < 0 || at[i] < at[pick])) {
				pick = i;
			}
		}
		at[pick] = now + Abilities.cooldown(player, Ability.PASSAGE);
		RaceState.setResource(player, RaceState.resource(player) - Abilities.cost(player, Ability.PASSAGE));

		Vec3 look = flatLook(player);
		Vec3 a = before(here, player, look);
		Vec3 aOut = a.add(look.scale(-1.3));
		Vec3 bLook = flatLook(target);
		Vec3 b = before(there, target, bLook);
		Vec3 bOut = b.add(bLook.scale(-1.3));
		int ticks = Talents.has(player, Talent.WORLDS_LONG) ? TICKS * 2 : TICKS;
		GATES.add(new Gate(player.getUUID(), here.dimension(), a, aOut, there.dimension(), b, bOut, now + ticks));
		Fx.rift(here, a.add(0, 1.4, 0), 2.8f, 1.6f, ticks);
		Fx.rift(there, b.add(0, 1.4, 0), 2.8f, 1.6f, ticks);
		here.playSound(null, a.x, a.y, a.z, SoundEvents.END_PORTAL_SPAWN, SoundSource.PLAYERS, 0.6f, 1.4f);
		there.playSound(null, b.x, b.y, b.z, SoundEvents.END_PORTAL_SPAWN, SoundSource.PLAYERS, 0.6f, 1.4f);
		player.displayClientMessage(Component.translatable("mages_legacy.message.passage_opened", target.getDisplayName())
				.withStyle(ChatFormatting.LIGHT_PURPLE), true);
		target.displayClientMessage(Component.translatable("mages_legacy.message.passage_to_you", player.getDisplayName(), ticks / 20)
				.withStyle(ChatFormatting.LIGHT_PURPLE), false);
		RaceState.sync(player);
	}

	private static Vec3 flatLook(ServerPlayer player) {
		Vec3 look = player.getLookAngle().multiply(1, 0, 1);
		return look.lengthSqr() < 1.0E-4 ? new Vec3(0, 0, 1) : look.normalize();
	}

	/** Where a rift stands before someone: two blocks ahead where a body fits, closer if not, else where they stand. */
	private static Vec3 before(ServerLevel level, ServerPlayer player, Vec3 look) {
		for (double d : new double[] {2, 2.5, 1.5}) {
			Vec3 at = player.position().add(look.scale(d));
			for (int dy : new int[] {0, 1, -1}) {
				Vec3 c = at.add(0, dy, 0);
				if (Voidborn.fits(level, player, c)) {
					return c;
				}
			}
		}
		return player.position();
	}

	/** Closes every passage touching a world that is going away (an arena ending). */
	public static void closeIn(ResourceKey<Level> level) {
		GATES.removeIf(g -> g.levelA() == level || g.levelB() == level);
	}

	private static void onServerTick(ServerTickEvent.Post event) {
		if (GATES.isEmpty()) {
			return;
		}
		MinecraftServer server = event.getServer();
		long now = server.overworld().getGameTime();
		for (Iterator<Gate> it = GATES.iterator(); it.hasNext(); ) {
			Gate gate = it.next();
			ServerLevel levelA = server.getLevel(gate.levelA());
			ServerLevel levelB = server.getLevel(gate.levelB());
			if (levelA == null || levelB == null || levelA.getGameTime() > gate.until()) {
				it.remove();
				continue;
			}
			ServerPlayer owner = server.getPlayerList().getPlayer(gate.owner());
			boolean caravan = owner != null && Talents.has(owner, Talent.WORLDS_TRAIN);
			boolean cleanse = owner != null && Talents.has(owner, Talent.WORLDS_CLEANSE);
			boolean carried = through(levelA, gate.a(), levelB, gate.bOut(), caravan, cleanse, now);
			carried |= through(levelB, gate.b(), levelA, gate.aOut(), caravan, cleanse, now);
			// Across Any Distance: a player went through the Voidborn's passage
			if (carried && owner != null) {
				euf.mageslegacy.race.Deeds.grant(owner, "void_passage");
			}
		}
		RESTING.values().removeIf(t -> t < now);
	}

	/** Carries whoever stands in a rift to the other side; true if a player went through. */
	private static boolean through(ServerLevel level, Vec3 rift, ServerLevel to, Vec3 out, boolean caravan, boolean cleanse, long now) {
		boolean player = false;
		for (Entity e : level.getEntities((Entity) null, new AABB(rift, rift).inflate(0.8, 2, 0.8), e -> e.isAlive() && !e.isPassenger())) {
			if (RESTING.containsKey(e.getUUID()) || !mayPass(e, caravan)) {
				continue;
			}
			Entity rider = e.getFirstPassenger();
			if (rider != null) {
				rider.stopRiding();
			}
			Entity moved = carry(e, to, out);
			// Soft Landing: whoever comes out floats down gently and stands firm for a few seconds
			if (cleanse && moved instanceof LivingEntity living) {
				living.addEffect(new MobEffectInstance(MobEffects.SLOW_FALLING, 60, 0, true, false, true));
				Voidborn.steady(living, 60);
			}
			RESTING.put(e.getUUID(), now + REST);
			if (rider != null) {
				Entity carried = carry(rider, to, out);
				RESTING.put(rider.getUUID(), now + REST);
				if (moved != null && carried != null) {
					carried.startRiding(moved, true);
				}
			}
			if (moved instanceof ServerPlayer traveller) {
				Fx.voidScreen(traveller, 10);
				player = true;
			}
			Fx.burst(to, out.add(0, 1, 0), 1f, 6, false);
			to.playSound(null, out.x, out.y, out.z, SoundEvents.ENDERMAN_TELEPORT, SoundSource.PLAYERS, 0.6f, 0.8f);
		}
		return player;
	}

	/** Takes a body to that point of that world; returns it as it is there (a body crossing worlds is made anew). */
	private static Entity carry(Entity e, ServerLevel to, Vec3 out) {
		e.fallDistance = 0;
		if (e.level() == to) {
			e.teleportTo(out.x, out.y, out.z);
			return e;
		}
		if (e instanceof ServerPlayer player) {
			player.teleportTo(to, out.x, out.y, out.z, player.getYRot(), player.getXRot());
			return player;
		}
		return e.teleportTo(to, out.x, out.y, out.z, Set.of(), e.getYRot(), e.getXRot()) ? findIn(to, e) : null;
	}

	private static Entity findIn(ServerLevel level, Entity e) {
		Entity there = level.getEntity(e.getUUID());
		return there != null ? there : e;
	}

	/** Players and their pets always; with Caravan any animal or mount as well. */
	private static boolean mayPass(Entity e, boolean caravan) {
		if (e instanceof Player p) {
			// The heart of a singularity and a walker in the void stay where they are
			return !p.isSpectator() && !(p instanceof ServerPlayer sp && (BlackHole.holding(sp) || Voidborn.stepping(sp)));
		}
		if (e instanceof OwnableEntity owned && owned.getOwnerUUID() != null) {
			return true;
		}
		return caravan && (e instanceof Animal || e instanceof LivingEntity living && living.isVehicle());
	}
}
