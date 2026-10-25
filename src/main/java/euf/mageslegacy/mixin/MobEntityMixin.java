package euf.mageslegacy.mixin;

import euf.mageslegacy.shepherd.Echoes;
import net.minecraft.entity.mob.MobEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Echoes are spirits: zombie and skeleton echoes don't catch fire in the sun. */
@Mixin(MobEntity.class)
public abstract class MobEntityMixin {
	@Inject(method = "isAffectedByDaylight", at = @At("HEAD"), cancellable = true)
	private void mages_legacy$echoesIgnoreTheSun(CallbackInfoReturnable<Boolean> cir) {
		if (Echoes.isEcho((MobEntity) (Object) this)) {
			cir.setReturnValue(false);
		}
	}
}
