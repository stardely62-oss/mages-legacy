package euf.mageslegacy.mixin.client;

import euf.mageslegacy.voidborn.Voidborn;
import net.minecraft.client.network.ClientPlayerEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** During a Void Step this client's player passes through blocks: it is not pushed out of them. */
@Mixin(ClientPlayerEntity.class)
public class ClientPlayerEntityMixin {
	@Inject(method = "tickMovement", at = @At("HEAD"))
	private void mages_legacy$phase(CallbackInfo ci) {
		ClientPlayerEntity self = (ClientPlayerEntity) (Object) this;
		if (Voidborn.phasing(self)) {
			self.noClip = true;
		}
	}
}
