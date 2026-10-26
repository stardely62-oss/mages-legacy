package euf.mageslegacy.mixin.client;

import euf.mageslegacy.client.FxRenderer;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayNetworkHandler;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.network.packet.s2c.play.PlayerPositionLookS2CPacket;
import net.minecraft.util.math.Vec3d;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * A Rewind carries the player along its path in server steps. Vanilla snaps the client to each step (it sets the
 * previous position to the new one), which looks like a string of jumps; during such a flight the previous position
 * is kept, so the frames in between glide from step to step.
 */
@Mixin(ClientPlayNetworkHandler.class)
public abstract class ClientPlayNetworkHandlerMixin {
	@Unique
	private Vec3d mages_legacy$before;

	@Inject(method = "onPlayerPositionLook", at = @At("HEAD"))
	private void mages_legacy$rememberStep(PlayerPositionLookS2CPacket packet, CallbackInfo ci) {
		MinecraftClient client = MinecraftClient.getInstance();
		// The handler first runs on the network thread only to hop over to the main one
		mages_legacy$before = client.isOnThread() && client.player != null && FxRenderer.inFlight() ? client.player.getPos() : null;
	}

	@Inject(method = "onPlayerPositionLook", at = @At("TAIL"))
	private void mages_legacy$glide(PlayerPositionLookS2CPacket packet, CallbackInfo ci) {
		PlayerEntity player = MinecraftClient.getInstance().player;
		if (mages_legacy$before == null || player == null) {
			return;
		}
		player.prevX = mages_legacy$before.x;
		player.prevY = mages_legacy$before.y;
		player.prevZ = mages_legacy$before.z;
		player.lastRenderX = mages_legacy$before.x;
		player.lastRenderY = mages_legacy$before.y;
		player.lastRenderZ = mages_legacy$before.z;
		mages_legacy$before = null;
	}
}
