package euf.mageslegacy.mixin;

import euf.mageslegacy.mycelid.Mycelid;
import euf.mageslegacy.race.Race;
import euf.mageslegacy.race.RaceState;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.server.network.ServerPlayerEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

/** Race weaknesses that change incoming damage (Mycelids burn half again as badly). */
@Mixin(LivingEntity.class)
public abstract class LivingEntityMixin {
	@ModifyVariable(method = "damage", at = @At("HEAD"), argsOnly = true, ordinal = 0)
	private float mages_legacy$raceDamage(float amount, DamageSource source) {
		if ((Object) this instanceof ServerPlayerEntity player && RaceState.raceOf(player).orElse(null) == Race.MYCELID) {
			return Mycelid.modifyDamage(player, source, amount);
		}
		return amount;
	}
}
