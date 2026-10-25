package euf.mageslegacy.client;

import euf.mageslegacy.MagesLegacy;
import euf.mageslegacy.client.screen.LoreScreen;
import euf.mageslegacy.client.screen.RaceSelectScreen;
import euf.mageslegacy.client.screen.TalentScreen;
import euf.mageslegacy.net.RacePackets;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.blockrenderlayer.v1.BlockRenderLayerMap;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.fabricmc.fabric.api.networking.v1.PacketByteBufs;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.util.InputUtil;
import net.minecraft.network.PacketByteBuf;
import org.lwjgl.glfw.GLFW;

public class MagesLegacyClient implements ClientModInitializer {
	public static final String CATEGORY = "category.mages_legacy";
	public static final KeyBinding[] ABILITY_KEYS = {
			key("ability_1", GLFW.GLFW_KEY_R), key("ability_2", GLFW.GLFW_KEY_V), key("ability_3", GLFW.GLFW_KEY_G)};
	public static final KeyBinding TREE_KEY = key("talents", GLFW.GLFW_KEY_K);

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
		ClientPlayNetworking.registerGlobalReceiver(RacePackets.OPEN_CHOICE, (client, handler, buf, sender) ->
				client.execute(() -> client.setScreen(new LoreScreen())));
		ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> client.execute(ClientRaceState::clear));
		HudRenderCallback.EVENT.register(RaceHud::render);
		ClientTickEvents.END_CLIENT_TICK.register(MagesLegacyClient::tick);
	}

	private static void tick(MinecraftClient client) {
		ClientRaceState.tick();
		if (client.player == null) {
			return;
		}
		for (int slot = 0; slot < ABILITY_KEYS.length; slot++) {
			while (ABILITY_KEYS[slot].wasPressed()) {
				useAbility(slot);
			}
		}
		while (TREE_KEY.wasPressed()) {
			client.setScreen(ClientRaceState.race == null ? new LoreScreen() : new TalentScreen());
		}
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

	public static void sendUnlock(String talent) {
		PacketByteBuf buf = PacketByteBufs.create();
		buf.writeString(talent);
		ClientPlayNetworking.send(RacePackets.UNLOCK, buf);
	}
}
