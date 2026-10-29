package euf.mageslegacy.voidborn;

import euf.mageslegacy.fx.Fx;
import euf.mageslegacy.race.Abilities;
import euf.mageslegacy.race.RaceState;
import euf.mageslegacy.race.Talent;
import euf.mageslegacy.race.Talents;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.minecraft.ChatFormatting;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.core.registries.Registries;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
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

/**
 * The Awakening "Through Worlds": a remembered point of the world (sneak + the key) and, on the key, a rift beside the
 * Voidborn joined to a rift at that point for a while. Players go through both ways, and their pets; with Caravan
 * any animal and mount does. Both ends must be in the same world and in reach: no endless leaps between dimensions.
 */
public final class Passage {
	private static final int TICKS = 15 * 20;
	private static final double REACH = 1000;
	/** An entity that just went through waits this long before it can be taken back. */
	private static final int REST = 30;

	private record Gate(UUID owner, ResourceKey<Level> level, Vec3 a, Vec3 aOut, Vec3 b, Vec3 bOut, long until) {
	}

	private static final List<Gate> GATES = new ArrayList<>();
	private static final Map<UUID, Long> RESTING = new HashMap<>();

	private Passage() {
	}

	public static void register() {
		NeoForge.EVENT_BUS.addListener(Passage::onServerTick);
	}

	/** Sneaking with the key remembers where the Voidborn stands (and looks); free. Returns whether it did. */
	public static boolean tryRemember(ServerPlayer player) {
		if (!player.isShiftKeyDown()) {
			return false;
		}
		CompoundTag tag = new CompoundTag();
		tag.putString("dim", player.level().dimension().location().toString());
		tag.putDouble("x", player.getX());
		tag.putDouble("y", player.getY());
		tag.putDouble("z", player.getZ());
		tag.putFloat("yaw", player.getYRot());
		player.setData(RaceState.PASSAGE_POINT, tag);
		Fx.rift(player.serverLevel(), player.position().add(0, 1.3, 0), 2f, 1f, 24);
		player.level().playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.AMETHYST_BLOCK_RESONATE, SoundSource.PLAYERS, 1f, 0.5f);
		player.displayClientMessage(Component.translatable("mages_legacy.message.point_set").withStyle(ChatFormatting.LIGHT_PURPLE), true);
		return true;
	}

	public static Abilities.Refusal open(ServerPlayer player) {
		CompoundTag point = player.getData(RaceState.PASSAGE_POINT);
		if (!point.contains("dim")) {
			return new Abilities.Refusal("mages_legacy.refuse.no_point");
		}
		ServerLevel level = player.serverLevel();
		if (!level.dimension().location().equals(ResourceLocation.parse(point.getString("dim")))) {
			return new Abilities.Refusal("mages_legacy.refuse.point_other_world");
		}
		Vec3 b = new Vec3(point.getDouble("x"), point.getDouble("y"), point.getDouble("z"));
		double reach = Talents.has(player, Talent.WORLDS_FAR) ? REACH * 2 : REACH;
		if (b.distanceTo(player.position()) > reach) {
			return new Abilities.Refusal("mages_legacy.refuse.point_far");
		}
		Vec3 look = player.getLookAngle().multiply(1, 0, 1);
		look = look.lengthSqr() < 1.0E-4 ? new Vec3(0, 0, 1) : look.normalize();
		Vec3 a = player.position().add(look.scale(2));
		Vec3 bLook = Vec3.directionFromRotation(0, point.getFloat("yaw"));
		// Coming out, you step forward out of the rift, the way you faced when you remembered the place
		Vec3 aOut = a.add(look.scale(-1.3));
		Vec3 bOut = b.add(bLook.scale(1.3));
		int ticks = Talents.has(player, Talent.WORLDS_LONG) ? TICKS * 2 : TICKS;
		level.getChunk(net.minecraft.core.BlockPos.containing(b));
		GATES.add(new Gate(player.getUUID(), level.dimension(), a, aOut, b, bOut, level.getGameTime() + ticks));
		Fx.rift(level, a.add(0, 1.4, 0), 2.8f, 1.6f, ticks);
		Fx.rift(level, b.add(0, 1.4, 0), 2.8f, 1.6f, ticks);
		Fx.ribbon(level, a, b.subtract(a).length() > 48 ? a.add(b.subtract(a).normalize().scale(48)) : b, 30, 1.4f);
		Fx.shake(player, 0.8f, 10);
		level.playSound(null, a.x, a.y, a.z, SoundEvents.END_PORTAL_SPAWN, SoundSource.PLAYERS, 0.6f, 1.4f);
		level.playSound(null, b.x, b.y, b.z, SoundEvents.END_PORTAL_SPAWN, SoundSource.PLAYERS, 0.6f, 1.4f);
		return null;
	}

	/** Closes every passage in a world that is going away (an arena ending). */
	public static void closeIn(ResourceKey<Level> level) {
		GATES.removeIf(g -> g.level() == level);
	}

	private static void onServerTick(ServerTickEvent.Post event) {
		if (GATES.isEmpty()) {
			return;
		}
		MinecraftServer server = event.getServer();
		for (Iterator<Gate> it = GATES.iterator(); it.hasNext(); ) {
			Gate gate = it.next();
			ServerLevel level = server.getLevel(gate.level());
			if (level == null || level.getGameTime() > gate.until()) {
				it.remove();
				continue;
			}
			ServerPlayer owner = server.getPlayerList().getPlayer(gate.owner());
			boolean caravan = owner != null && Talents.has(owner, Talent.WORLDS_TRAIN);
			through(level, gate.a(), gate.bOut(), caravan);
			through(level, gate.b(), gate.aOut(), caravan);
		}
		long now = server.overworld().getGameTime();
		RESTING.values().removeIf(t -> t < now);
	}

	private static void through(ServerLevel level, Vec3 rift, Vec3 out, boolean caravan) {
		long now = level.getServer().overworld().getGameTime();
		for (Entity e : level.getEntities((Entity) null, new AABB(rift, rift).inflate(0.8, 2, 0.8), e -> e.isAlive() && !e.isPassenger())) {
			if (RESTING.containsKey(e.getUUID()) || !mayPass(e, caravan)) {
				continue;
			}
			Entity rider = e.getFirstPassenger();
			if (rider != null) {
				rider.stopRiding();
			}
			e.teleportTo(out.x, out.y, out.z);
			e.fallDistance = 0;
			RESTING.put(e.getUUID(), now + REST);
			if (rider != null) {
				rider.teleportTo(out.x, out.y, out.z);
				RESTING.put(rider.getUUID(), now + REST);
				rider.startRiding(e, true);
			}
			if (e instanceof ServerPlayer player) {
				Fx.voidScreen(player, 10);
			}
			Fx.burst(level, out.add(0, 1, 0), 1f, 6, false);
			level.playSound(null, out.x, out.y, out.z, SoundEvents.ENDERMAN_TELEPORT, SoundSource.PLAYERS, 0.6f, 0.8f);
		}
	}

	/** Players and their pets always; with Caravan any animal or mount as well. */
	private static boolean mayPass(Entity e, boolean caravan) {
		if (e instanceof Player p) {
			return !p.isSpectator();
		}
		if (e instanceof OwnableEntity owned && owned.getOwnerUUID() != null) {
			return true;
		}
		return caravan && (e instanceof Animal || e instanceof LivingEntity living && living.isVehicle());
	}
}
