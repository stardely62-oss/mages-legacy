package euf.mageslegacy.client;

import com.mojang.blaze3d.platform.InputConstants;
import euf.mageslegacy.MagesLegacy;
import euf.mageslegacy.client.fx.VoidFx;
import euf.mageslegacy.client.screen.LoreScreen;
import euf.mageslegacy.net.AbilityPayload;
import euf.mageslegacy.net.ChooseRacePayload;
import euf.mageslegacy.net.FxPayload;
import euf.mageslegacy.net.SyncPayload;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.ReceivingLevelScreen;
import net.minecraft.network.chat.Component;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.InputEvent;
import net.neoforged.neoforge.client.event.RegisterGuiLayersEvent;
import net.neoforged.neoforge.client.event.RegisterDimensionSpecialEffectsEvent;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;
import net.neoforged.neoforge.client.event.RenderBlockScreenEffectEvent;
import net.neoforged.neoforge.client.gui.VanillaGuiLayers;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.network.PacketDistributor;
import org.lwjgl.glfw.GLFW;

@Mod(value = MagesLegacy.MOD_ID, dist = Dist.CLIENT)
public class MagesLegacyClient {
	private static final String CATEGORY = "key.categories.mages_legacy";
	/** Ability keys by slot: R, V, Z, H, left Alt. */
	public static final KeyMapping[] ABILITY_KEYS = {
			new KeyMapping("key.mages_legacy.ability_r", InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_R, CATEGORY),
			new KeyMapping("key.mages_legacy.ability_v", InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_V, CATEGORY),
			new KeyMapping("key.mages_legacy.ability_z", InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_Z, CATEGORY),
			new KeyMapping("key.mages_legacy.ability_h", InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_H, CATEGORY),
			new KeyMapping("key.mages_legacy.ability_alt", InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_LEFT_ALT, CATEGORY)};
	/** Opens the legend and the choice while there's no race (later: the talent tree). */
	public static final KeyMapping LEGACY_KEY = new KeyMapping("key.mages_legacy.legacy", InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_K, CATEGORY);

	/** The server asked for the choice; it opens once the world has finished loading. */
	private static boolean choicePending;

	public MagesLegacyClient(IEventBus modBus) {
		modBus.addListener(MagesLegacyClient::onRegisterKeys);
		modBus.addListener(MagesLegacyClient::onRegisterLayers);
		NeoForge.EVENT_BUS.addListener(MagesLegacyClient::onTick);
		NeoForge.EVENT_BUS.addListener(MagesLegacyClient::onInteract);
		NeoForge.EVENT_BUS.addListener(MagesLegacyClient::onBlockOverlay);
		NeoForge.EVENT_BUS.addListener(ScreenFx::onAngles);
		NeoForge.EVENT_BUS.addListener(ScreenFx::onFov);
		modBus.addListener(MagesLegacyClient::onDimensionEffects);
		if (Boolean.getBoolean("mages_legacy.harness")) {
			Harness.start();
		}
	}

	private static void onRegisterKeys(RegisterKeyMappingsEvent event) {
		for (KeyMapping key : ABILITY_KEYS) {
			event.register(key);
		}
		event.register(LEGACY_KEY);
	}

	private static void onRegisterLayers(RegisterGuiLayersEvent event) {
		event.registerAbove(VanillaGuiLayers.HOTBAR, MagesLegacy.id("race_hud"), RaceHud::render);
		event.registerAboveAll(MagesLegacy.id("void_screen"), ScreenFx::render);
	}

	public static void openChoice() {
		choicePending = true;
	}

	public static void onSync(SyncPayload payload) {
		ClientRace.update(payload);
	}

	public static void onFx(FxPayload payload) {
		VoidFx.play(payload);
	}

	public static void sendChoice(String race) {
		PacketDistributor.sendToServer(new ChooseRacePayload(race));
	}

	public static void useAbility(int slot) {
		PacketDistributor.sendToServer(new AbilityPayload(slot));
	}

	private static void onTick(ClientTickEvent.Post event) {
		Minecraft client = Minecraft.getInstance();
		ClientRace.tick();
		ScreenFx.tick();
		// The loading screen closes itself when the terrain arrives, so the legend waits for it instead of being closed with it
		if (choicePending && client.player != null && !(client.screen instanceof ReceivingLevelScreen) && client.getOverlay() == null) {
			choicePending = false;
			client.setScreen(new LoreScreen());
		}
		if (client.player == null || client.screen != null) {
			return;
		}
		for (int slot = 0; slot < ABILITY_KEYS.length; slot++) {
			while (ABILITY_KEYS[slot].consumeClick()) {
				if (ClientRace.race() != null) {
					useAbility(slot);
				}
			}
		}
		while (LEGACY_KEY.consumeClick()) {
			if (ClientRace.race() == null) {
				client.setScreen(new LoreScreen());
			} else {
				client.player.displayClientMessage(Component.translatable("mages_legacy.message.tree_soon"), true);
			}
		}
	}

	/** Walking through a wall shows the void, not the wall's texture pressed against the eyes. */
	private static void onBlockOverlay(RenderBlockScreenEffectEvent event) {
		if (ClientRace.stepping() || ScreenFx.inVoid()) {
			event.setCanceled(true);
		}
	}

	private static void onDimensionEffects(RegisterDimensionSpecialEffectsEvent event) {
		event.register(MagesLegacy.id("pocket"), new PocketSky());
	}

	/** In the in-between the hands can't reach anything. */
	private static void onInteract(InputEvent.InteractionKeyMappingTriggered event) {
		if (ClientRace.stepping()) {
			event.setCanceled(true);
			event.setSwingHand(false);
		}
	}
}
