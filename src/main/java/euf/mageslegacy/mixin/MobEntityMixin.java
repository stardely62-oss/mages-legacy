package euf.mageslegacy.mixin;

import euf.mageslegacy.masked.Masked;
import euf.mageslegacy.shepherd.Echoes;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.mob.MobEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import net.minecraft.entity.player.PlayerEntity;

/**
 * Echoes are spirits: zombie and skeleton echoes don't catch fire in the sun. A Masked's own kind doesn't take it for
 * prey, and a Masked's retinue never turns on a player.
 */
@Mixin(MobEntity.class)
public abstract class MobEntityMixin {
	@Inject(method = "isAffectedByDaylight", at = @At("HEAD"), cancellable = true)
	private void mages_legacy$echoesIgnoreTheSun(CallbackInfoReturnable<Boolean> cir) {
		if (Echoes.isEcho((MobEntity) (Object) this)) {
			cir.setReturnValue(false);
		}
	}

	@Inject(method = "setTarget", at = @At("HEAD"), cancellable = true)
	private void mages_legacy$kin(LivingEntity target, CallbackInfo ci) {
		MobEntity self = (MobEntity) (Object) this;
		if (target instanceof PlayerEntity player && !self.getWorld().isClient() && (Masked.inRetinue(self) || Masked.hidesFrom(self, player))) {
			ci.cancel();
		}
	}
}
