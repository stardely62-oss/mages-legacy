package euf.mageslegacy.mixin;

import euf.mageslegacy.mycelid.FungalDiet;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.UseAnim;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Raw mushrooms are eaten like food when someone eats them: the eating animation, crumbs and sounds, and how long it
 * takes. This only describes the eating; whether anyone may start (only a Mycelid) is decided in {@link FungalDiet}, so
 * for everyone else a mushroom stays what it was.
 */
@Mixin(Item.class)
public abstract class ItemMixin {
	@Inject(method = "getUseAnimation", at = @At("HEAD"), cancellable = true)
	private void mages_legacy$eatMushroom(ItemStack stack, CallbackInfoReturnable<UseAnim> cir) {
		if (FungalDiet.raw(stack)) {
			cir.setReturnValue(UseAnim.EAT);
		}
	}

	@Inject(method = "getUseDuration", at = @At("HEAD"), cancellable = true)
	private void mages_legacy$eatMushroomTime(ItemStack stack, LivingEntity entity, CallbackInfoReturnable<Integer> cir) {
		if (FungalDiet.raw(stack)) {
			cir.setReturnValue(FungalDiet.EAT_TICKS);
		}
	}
}
