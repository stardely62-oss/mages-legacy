package euf.mageslegacy;

import euf.mageslegacy.block.MyceliumNodeBlock;
import euf.mageslegacy.mycelid.Mycelid;
import euf.mageslegacy.net.RacePackets;
import euf.mageslegacy.race.ClientRaceView;
import euf.mageslegacy.race.Race;
import euf.mageslegacy.race.RaceCommand;
import euf.mageslegacy.race.RaceData;
import euf.mageslegacy.race.RaceState;
import euf.mageslegacy.race.Talents;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.entity.event.v1.ServerEntityCombatEvents;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.entity.event.v1.ServerPlayerEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.event.player.UseItemCallback;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.object.builder.v1.block.FabricBlockSettings;
import net.minecraft.block.Block;
import net.minecraft.block.Blocks;
import net.minecraft.block.piston.PistonBehavior;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.sound.BlockSoundGroup;
import net.minecraft.util.Identifier;
import net.minecraft.util.TypedActionResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class MagesLegacy implements ModInitializer {
	public static final String MOD_ID = "mages_legacy";
	public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

	/** Grown by the Mycelids' third ability; drops nothing and has no item. */
	public static final Block MYCELIUM_NODE = Registry.register(Registries.BLOCK, id("mycelium_node"),
			new MyceliumNodeBlock(FabricBlockSettings.copyOf(Blocks.RED_MUSHROOM_BLOCK).nonOpaque().noCollision()
					.strength(0.5f).luminance(state -> 7).sounds(BlockSoundGroup.FUNGUS).pistonBehavior(PistonBehavior.DESTROY)));

	public static Identifier id(String path) {
		return new Identifier(MOD_ID, path);
	}

	@Override
	public void onInitialize() {
		Talents.init();
		RacePackets.registerServerReceivers();
		CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> RaceCommand.register(dispatcher));
		ServerTickEvents.END_SERVER_TICK.register(MagesLegacy::tick);
		ServerLifecycleEvents.SERVER_STOPPED.register(server -> Mycelid.reset());

		// A fresh session: send everything again, and the legend with the choice of race if there is no race yet
		ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> server.execute(() -> {
			RaceData data = RaceState.of(handler.player);
			if (data != null) {
				data.prompted = false;
				data.dirty = true;
			}
		}));
		ServerPlayerEvents.AFTER_RESPAWN.register((oldPlayer, newPlayer, alive) -> {
			RaceData data = RaceState.of(newPlayer);
			if (data != null && !alive) {
				data.tastes.clear();
				RacePackets.sync(newPlayer, data);
			}
		});
		// Mycelids eat raw mushrooms and fungi (right click into the air)
		UseItemCallback.EVENT.register((player, world, hand) -> {
			ItemStack stack = player.getStackInHand(hand);
			if (!Mycelid.DIET.containsKey(stack.getItem())) {
				return TypedActionResult.pass(stack);
			}
			if (world.isClient) {
				return ClientRaceView.isMycelid(player) ? TypedActionResult.success(stack) : TypedActionResult.pass(stack);
			}
			RaceData data = RaceState.of(player);
			if (data == null || data.race != Race.MYCELID) {
				return TypedActionResult.pass(stack);
			}
			return Mycelid.eat((ServerPlayerEntity) player, data, stack) ? TypedActionResult.success(stack) : TypedActionResult.fail(stack);
		});
		ServerLivingEntityEvents.AFTER_DEATH.register((entity, source) -> Mycelid.onDeath(entity));
		ServerLivingEntityEvents.ALLOW_DEATH.register((entity, source, amount) ->
				!(entity instanceof ServerPlayerEntity player) || Mycelid.allowDeath(player));
		ServerEntityCombatEvents.AFTER_KILLED_OTHER_ENTITY.register((world, killer, victim) -> {
			if (killer instanceof ServerPlayerEntity player) {
				Mycelid.onKill(player, victim);
			}
		});
		LOGGER.info("Mage's Legacy loaded");
	}

	private static void tick(MinecraftServer server) {
		Mycelid.tick(server);
		int ticks = server.getTicks();
		if (ticks % 10 != 0) {
			return;
		}
		RaceState state = RaceState.get(server);
		for (ServerPlayerEntity player : server.getPlayerManager().getPlayerList()) {
			RaceData data = state.data(player.getUuid());
			if (data.race == Race.MYCELID && ticks % 20 == 0 && player.isAlive() && !player.isSpectator()) {
				Mycelid.tickSecond(player, data);
			}
			// The client registers our channels a moment after joining, so the prompt waits for the tick
			if (data.race == null && !data.prompted) {
				data.prompted = RacePackets.openChoice(player);
			}
			// Only the ability slots show on the client; talent timers (Rebirth, Fed Network) don't need a sync every tick
			boolean coolingDown = data.readyAt.entrySet().stream().anyMatch(e -> e.getKey() < RacePackets.SLOTS && e.getValue() > ticks)
					|| data.chainLeft > 0;
			if (data.dirty || coolingDown) {
				RacePackets.sync(player, data);
			}
		}
	}
}
