package euf.mageslegacy.mixin;

import euf.mageslegacy.mycelid.Mycelid;
import euf.mageslegacy.race.Race;
import euf.mageslegacy.race.RaceState;
import euf.mageslegacy.shepherd.Shepherd;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.server.network.ServerPlayerEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Race rules that change damage: Mycelids burn half again as badly; Shepherds hit softer, their creatures don't hurt
 * each other or their Shepherd, and the Voice and Pack talents shift who takes what.
 */
@Mixin(LivingEntity.class)
public abstract class LivingEntityMixin {
	@ModifyVariable(method = "damage", at = @At("HEAD"), argsOnly = true, ordinal = 0)
	private float mages_legacy$raceDamage(float amount, DamageSource source) {
		LivingEntity self = (LivingEntity) (Object) this;
		if (self.getWorld().isClient()) {
			return amount;
		}
		if (self instanceof ServerPlayerEntity player && RaceState.raceOf(player).orElse(null) == Race.MYCELID) {
			amount = Mycelid.modifyDamage(player, source, amount);
		}
		return Shepherd.modifyDamage(self, source, amount);
	}

	/** No friendly fire: such a blow doesn't land at all (no knockback, no hurt flash). */
	@Inject(method = "damage", at = @At("HEAD"), cancellable = true)
	private void mages_legacy$friendlyFire(DamageSource source, float amount, CallbackInfoReturnable<Boolean> cir) {
		LivingEntity self = (LivingEntity) (Object) this;
		if (!self.getWorld().isClient() && Shepherd.friendlyFire(self, source)) {
			cir.setReturnValue(false);
		}
	}
}
