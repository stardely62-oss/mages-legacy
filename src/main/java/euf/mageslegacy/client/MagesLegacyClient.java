package euf.mageslegacy.client;

import euf.mageslegacy.MagesLegacy;
import euf.mageslegacy.client.screen.BestiaryScreen;
import euf.mageslegacy.client.screen.LoreScreen;
import euf.mageslegacy.client.screen.RaceSelectScreen;
import euf.mageslegacy.client.screen.TalentScreen;
import euf.mageslegacy.net.RacePackets;
import euf.mageslegacy.race.Race;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.blockrenderlayer.v1.BlockRenderLayerMap;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderEvents;
import net.fabricmc.fabric.api.networking.v1.PacketByteBufs;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.util.InputUtil;
import net.minecraft.network.PacketByteBuf;
import java.util.Locale;
import org.lwjgl.glfw.GLFW;

public class MagesLegacyClient implements ClientModInitializer {
	public static final String CATEGORY = "category.mages_legacy";
	public static final KeyBinding[] ABILITY_KEYS = {
			key("ability_1", GLFW.GLFW_KEY_R), key("ability_2", GLFW.GLFW_KEY_V), key("ability_3", GLFW.GLFW_KEY_G), key("ability_4", GLFW.GLFW_KEY_H)};
	public static final KeyBinding TREE_KEY = key("talents", GLFW.GLFW_KEY_K);
	public static final KeyBinding BESTIARY_KEY = key("bestiary", GLFW.GLFW_KEY_B);
	public static final KeyBinding SQUAD_KEY = key("next_squad", GLFW.GLFW_KEY_N);

	/**
	 * The key as printed on an English keyboard (R, V, G, H), whatever layout the system has switched to: GLFW names
	 * letter keys by the active layout, which turned R into К. Other keys keep their localized names.
	 */
	public static String keyLabel(KeyBinding binding) {
		String key = binding.getBoundKeyTranslationKey();
		String prefix = "key.keyboard.";
		if (key.startsWith(prefix) && key.length() == prefix.length() + 1) {
			return key.substring(prefix.length()).toUpperCase(Locale.ROOT);
		}
		return binding.getBoundKeyLocalizedText().getString();
	}

	private static KeyBinding key(String name, int code) {
		return KeyBindingHelper.registerKeyBinding(new KeyBinding("key.mages_legacy." + name, InputUtil.Type.KEYSYM, code, CATEGORY));
	}

	@Override
	public void onInitializeClient() {
		BlockRenderLayerMap.INSTANCE.putBlock(MagesLegacy.MYCELIUM_NODE, RenderLayer.getCutout());
		ClientPlayNetworking.registerGlobalReceiver(RacePackets.SYNC, (client, handler, buf, sender) -> {
			PacketByteBuf copy = PacketByteBufs.copy(buf);
			client.execute(() -> {
				ClientRaceState.read(copy);
				// The choice was made (here or by an operator): the lore and choice screens are done
				if (ClientRaceState.race != null && (client.currentScreen instanceof LoreScreen || client.currentScreen instanceof RaceSelectScreen)) {
					client.setScreen(null);
				}
			});
		});
		ClientPlayNetworking.registerGlobalReceiver(RacePackets.ECHOES, (client, handler, buf, sender) -> {
			PacketByteBuf copy = PacketByteBufs.copy(buf);
			client.execute(() -> EchoVisuals.read(copy));
		});
		ClientPlayNetworking.registerGlobalReceiver(RacePackets.QUICKENED, (client, handler, buf, sender) -> {
			PacketByteBuf copy = PacketByteBufs.copy(buf);
			client.execute(() -> QuickenVisuals.read(copy));
		});
		ClientPlayNetworking.registerGlobalReceiver(RacePackets.OPEN_CHOICE, (client, handler, buf, sender) ->
				client.execute(() -> client.setScreen(new LoreScreen())));
		ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> client.execute(() -> {
			ClientRaceState.clear();
			EchoVisuals.clear();
			QuickenVisuals.clear();
		}));
		HudRenderCallback.EVENT.register(RaceHud::render);
		WorldRenderEvents.AFTER_ENTITIES.register(QuickenVisuals::render);
		ClientTickEvents.END_CLIENT_TICK.register(MagesLegacyClient::tick);
	}

	private static void tick(MinecraftClient client) {
		ClientRaceState.tick();
		if (client.player == null) {
			return;
		}
		for (int slot = 0; slot < ABILITY_KEYS.length; slot++) {
			// The fourth key is the Shepherd's Song; other races have three abilities
			if (pressed(client, ABILITY_KEYS[slot], slot) && ClientRaceState.race != null && slot < RaceHud.abilityCount(ClientRaceState.race)) {
				useAbility(slot);
			}
		}
		if (pressed(client, TREE_KEY, ABILITY_KEYS.length)) {
			client.setScreen(ClientRaceState.race == null ? new LoreScreen() : new TalentScreen());
		}
		if (pressed(client, BESTIARY_KEY, ABILITY_KEYS.length + 1) && ClientRaceState.race == Race.SHEPHERD) {
			client.setScreen(new BestiaryScreen());
		}
		if (pressed(client, SQUAD_KEY, ABILITY_KEYS.length + 2) && ClientRaceState.race == Race.SHEPHERD) {
			selectSquad(ClientRaceState.extra.getInt("activePreset") + 1);
		}
	}

	/** Whether each of our keys was down last tick, by the physical key (see {@link #pressed}). */
	private static final boolean[] DOWN = new boolean[ABILITY_KEYS.length + 3];

	/**
	 * Whether one of our keys was pressed this tick. Minecraft gives a key to one binding only, so when another mod
	 * (JourneyMap, say) binds the same key, ours never hears it: then the physical key itself is watched, and both mods
	 * act on it. Not while a screen is open, so typing in the chat doesn't cast anything.
	 */
	private static boolean pressed(MinecraftClient client, KeyBinding binding, int index) {
		boolean fired = false;
		while (binding.wasPressed()) {
			fired = true;
		}
		InputUtil.Key key = KeyBindingHelper.getBoundKeyOf(binding);
		boolean down = key.getCategory() == InputUtil.Type.KEYSYM && key.getCode() != InputUtil.UNKNOWN_KEY.getCode()
				&& InputUtil.isKeyPressed(client.getWindow().getHandle(), key.getCode());
		boolean edge = down && !DOWN[index];
		DOWN[index] = down;
		return fired || (edge && client.currentScreen == null);
	}

	public static void useAbility(int slot) {
		PacketByteBuf buf = PacketByteBufs.create();
		buf.writeVarInt(slot);
		ClientPlayNetworking.send(RacePackets.ABILITY, buf);
	}

	public static void sendChoice(String raceId) {
		PacketByteBuf buf = PacketByteBufs.create();
		buf.writeString(raceId);
		ClientPlayNetworking.send(RacePackets.CHOOSE, buf);
	}

	/** Makes squad {@code index} (wrapping around) the one the Call brings. */
	public static void selectSquad(int index) {
		int wrapped = Math.floorMod(index, 3);
		ClientRaceState.extra.putInt("activePreset", wrapped);
		PacketByteBuf buf = PacketByteBufs.create();
		buf.writeVarInt(0);
		buf.writeVarInt(wrapped);
		ClientPlayNetworking.send(RacePackets.PRESET, buf);
	}

	public static void renameBeast(int index, String name) {
		PacketByteBuf buf = PacketByteBufs.create();
		buf.writeVarInt(0);
		buf.writeVarInt(index);
		buf.writeString(name);
		ClientPlayNetworking.send(RacePackets.BEAST, buf);
	}

	public static void toggleLeader(int index) {
		PacketByteBuf buf = PacketByteBufs.create();
		buf.writeVarInt(1);
		buf.writeVarInt(index);
		ClientPlayNetworking.send(RacePackets.BEAST, buf);
	}

	/** One more (+1) or one less (-1) echo of a kind in a squad. */
	public static void editSquad(int index, String echo, int delta) {
		PacketByteBuf buf = PacketByteBufs.create();
		buf.writeVarInt(1);
		buf.writeVarInt(index);
		buf.writeString(echo);
		buf.writeVarInt(delta);
		ClientPlayNetworking.send(RacePackets.PRESET, buf);
	}

	public static void sendUnlock(String talent) {
		PacketByteBuf buf = PacketByteBufs.create();
		buf.writeString(talent);
		ClientPlayNetworking.send(RacePackets.UNLOCK, buf);
	}
}
