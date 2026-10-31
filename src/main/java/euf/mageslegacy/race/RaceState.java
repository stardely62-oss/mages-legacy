package euf.mageslegacy.race;

import com.mojang.serialization.Codec;
import euf.mageslegacy.MagesLegacy;
import euf.mageslegacy.net.OpenChoicePayload;
import euf.mageslegacy.net.SyncPayload;
import euf.mageslegacy.voidborn.Pocket;
import euf.mageslegacy.voidborn.Voidborn;
import java.util.List;
import java.util.Optional;
import java.util.function.Supplier;
import net.minecraft.ChatFormatting;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.attachment.AttachmentType;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.NeoForgeRegistries;

/** A player's race and its progress, kept on the player (an empty id = not chosen yet) and through death. */
public final class RaceState {
	private static final DeferredRegister<AttachmentType<?>> ATTACHMENTS = DeferredRegister.create(NeoForgeRegistries.ATTACHMENT_TYPES,
			MagesLegacy.MOD_ID);
	public static final Supplier<AttachmentType<String>> RACE = ATTACHMENTS.register("race",
			() -> AttachmentType.builder(() -> "").serialize(Codec.STRING).copyOnDeath().build());
	public static final Supplier<AttachmentType<Integer>> LEVEL = ATTACHMENTS.register("level",
			() -> AttachmentType.builder(() -> 1).serialize(Codec.INT).copyOnDeath().build());
	public static final Supplier<AttachmentType<Integer>> XP = ATTACHMENTS.register("xp",
			() -> AttachmentType.builder(() -> 0).serialize(Codec.INT).copyOnDeath().build());
	/** The race's own resource (the Voidborn's Void). */
	public static final Supplier<AttachmentType<Float>> RESOURCE = ATTACHMENTS.register("resource",
			() -> AttachmentType.builder(() -> 100f).serialize(Codec.FLOAT).copyOnDeath().build());
	/** Where a player who went into a Pocket came from, and whose Pocket it is. */
	public static final Supplier<AttachmentType<CompoundTag>> POCKET_RETURN = ATTACHMENTS.register("pocket_return",
			() -> AttachmentType.builder(() -> new CompoundTag()).serialize(CompoundTag.CODEC).build());

	/** Two Doors: the place the Voidborn last went into their Pocket from (the second door out). */
	public static final Supplier<AttachmentType<CompoundTag>> POCKET_LAST_ENTRY = ATTACHMENTS.register("pocket_last_entry",
			() -> AttachmentType.builder(() -> new CompoundTag()).serialize(CompoundTag.CODEC).copyOnDeath().build());

	/**
	 * Cooldowns left when the player last left the server (key slots, Through Worlds' charges, Saving Rift), so that
	 * leaving and coming back, or a restart, never resets them. Offline time doesn't count down.
	 */
	public static final Supplier<AttachmentType<CompoundTag>> COOLDOWNS = ATTACHMENTS.register("cooldowns",
			() -> AttachmentType.builder(() -> new CompoundTag()).serialize(CompoundTag.CODEC).copyOnDeath().build());

	/** Where a player taken onto an arena came from. */
	public static final Supplier<AttachmentType<CompoundTag>> ARENA_RETURN = ATTACHMENTS.register("arena_return",
			() -> AttachmentType.builder(() -> new CompoundTag()).serialize(CompoundTag.CODEC).build());
	/** The point remembered for Through Worlds. */
	public static final Supplier<AttachmentType<CompoundTag>> PASSAGE_POINT = ATTACHMENTS.register("passage_point",
			() -> AttachmentType.builder(() -> new CompoundTag()).serialize(CompoundTag.CODEC).copyOnDeath().build());
	/** Learnt talent ids (the core and basic abilities aren't listed: they come with the level). */
	public static final Supplier<AttachmentType<List<String>>> TALENTS = ATTACHMENTS.register("talents",
			() -> AttachmentType.builder(() -> (List<String>) List.<String>of()).serialize(Codec.STRING.listOf()).copyOnDeath().build());
	/** What the Awakening trials have counted so far. */
	public static final Supplier<AttachmentType<CompoundTag>> PROGRESS = ATTACHMENTS.register("progress",
			() -> AttachmentType.builder(() -> new CompoundTag()).serialize(CompoundTag.CODEC).copyOnDeath().build());

	public static final float MAX_RESOURCE = 100;
	/** How often the client hears about its race, in ticks (and at once after anything changes). */
	private static final int SYNC_EVERY = 5;

	private RaceState() {
	}

	public static void register(IEventBus modBus) {
		ATTACHMENTS.register(modBus);
		NeoForge.EVENT_BUS.addListener(RaceState::onLogin);
		NeoForge.EVENT_BUS.addListener(RaceState::onTick);
	}

	public static Optional<Race> get(ServerPlayer player) {
		return Race.byId(player.getData(RACE));
	}

	public static boolean is(ServerPlayer player, Race race) {
		return get(player).orElse(null) == race;
	}

	public static int level(ServerPlayer player) {
		return player.getData(LEVEL);
	}

	public static float resource(ServerPlayer player) {
		return player.getData(RESOURCE);
	}

	public static void setResource(ServerPlayer player, float value) {
		player.setData(RESOURCE, Math.max(0, Math.min(MAX_RESOURCE, value)));
	}

	/** A player without a race is shown the legend and the choice on every join. */
	private static void onLogin(PlayerEvent.PlayerLoggedInEvent event) {
		if (event.getEntity() instanceof ServerPlayer player) {
			if (get(player).isEmpty()) {
				PacketDistributor.sendToPlayer(player, new OpenChoicePayload());
			}
			sync(player);
		}
	}

	private static void onTick(PlayerTickEvent.Post event) {
		if (event.getEntity() instanceof ServerPlayer player && player.tickCount % SYNC_EVERY == 0) {
			sync(player);
		}
	}

	/** The client's choice: only a playable race, and only once. */
	public static void choose(ServerPlayer player, String id) {
		Optional<Race> race = Race.byId(id);
		if (race.isEmpty() || !race.get().playable || get(player).isPresent()) {
			return;
		}
		set(player, race.get());
		player.displayClientMessage(Component.translatable("mages_legacy.message.became", Component.translatable(race.get().key()))
				.withStyle(ChatFormatting.GOLD), false);
		MagesLegacy.LOGGER.info("{} became {}", player.getGameProfile().getName(), race.get().id());
	}

	/** Gives a race from the start (null takes it away), as the choice and the admin command do. */
	public static void set(ServerPlayer player, Race race) {
		Pocket.leaveIfInside(player);
		Voidborn.reset(player);
		euf.mageslegacy.mycelid.Mycelid.reset(player);
		player.setData(RACE, race == null ? "" : race.id());
		player.setData(LEVEL, 1);
		player.setData(XP, 0);
		player.setData(RESOURCE, MAX_RESOURCE);
		player.setData(TALENTS, List.of());
		player.setData(PROGRESS, new CompoundTag());
		player.setData(PASSAGE_POINT, new CompoundTag());
		Pocket.refresh(player);
		Deeds.check(player);
		sync(player);
	}

	public static void sync(ServerPlayer player) {
		PacketDistributor.sendToPlayer(player, SyncPayload.of(player));
	}
}
