package euf.mageslegacy.mixin;

import euf.mageslegacy.masked.Masked;
import euf.mageslegacy.voidborn.Voidborn;
import net.minecraft.entity.mob.EndermanEntity;
import net.minecraft.entity.player.PlayerEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Endermen take a Voidborn's look (or a Masked in an enderman's face) for one of their own: meeting its eyes doesn't anger them. */
@Mixin(EndermanEntity.class)
public class EndermanEntityMixin {
	@Inject(method = "isPlayerStaring", at = @At("HEAD"), cancellable = true)
	private void mages_legacy$kin(PlayerEntity player, CallbackInfoReturnable<Boolean> cir) {
		if (!player.getWorld().isClient() && (Voidborn.kin(player) || Masked.kin(player))) {
			cir.setReturnValue(false);
		}
	}
}
