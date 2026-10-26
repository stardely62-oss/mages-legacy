package euf.mageslegacy.mixin.client;

import euf.mageslegacy.client.HeartbeatSense;
import net.minecraft.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** The outline of a heard heart is blood red (the outline takes its colour from the team colour). */
@Mixin(Entity.class)
public abstract class EntityOutlineMixin {
	@Inject(method = "getTeamColorValue", at = @At("HEAD"), cancellable = true)
	private void mages_legacy$bloodRed(CallbackInfoReturnable<Integer> cir) {
		Entity self = (Entity) (Object) this;
		if (self.getWorld().isClient() && self.getScoreboardTeam() == null && HeartbeatSense.hears(self)) {
			cir.setReturnValue(HeartbeatSense.OUTLINE_RGB);
		}
	}
}
