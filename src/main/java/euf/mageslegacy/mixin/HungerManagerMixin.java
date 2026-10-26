package euf.mageslegacy.mixin;

import euf.mageslegacy.frozen.Frozen;
import net.minecraft.entity.player.HungerManager;
import net.minecraft.entity.player.PlayerEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/** The Frozen heal from a full stomach only half as fast: their bodies are stuck in a moment. */
@Mixin(HungerManager.class)
public abstract class HungerManagerMixin {
	@Redirect(method = "update", at = @At(value = "INVOKE", target = "Lnet/minecraft/entity/player/PlayerEntity;heal(F)V"))
	private void mages_legacy$slowHealing(PlayerEntity player, float amount) {
		player.heal(amount * Frozen.naturalHealing(player));
	}
}
