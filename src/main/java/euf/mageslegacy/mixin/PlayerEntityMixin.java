package euf.mageslegacy.mixin;

import euf.mageslegacy.bloodkin.Bloodkin;
import euf.mageslegacy.mycelid.Mycelid;
import euf.mageslegacy.race.Race;
import euf.mageslegacy.race.RaceData;
import euf.mageslegacy.race.RaceState;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.FoodComponent;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.world.World;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Lets races react to the food a player eats (Mycelids: stews feed spores, meat feeds half). */
@Mixin(PlayerEntity.class)
public abstract class PlayerEntityMixin {
	@Unique
	private Item mages_legacy$eating;

	@Inject(method = "eatFood", at = @At("HEAD"))
	private void mages_legacy$beforeEat(World world, ItemStack stack, CallbackInfoReturnable<ItemStack> cir) {
		// Remember the item now: eating shrinks the stack, and a stew turns into a bowl
		mages_legacy$eating = stack.isFood() ? stack.getItem() : null;
	}

	@Inject(method = "eatFood", at = @At("RETURN"))
	private void mages_legacy$afterEat(World world, ItemStack stack, CallbackInfoReturnable<ItemStack> cir) {
		Item item = mages_legacy$eating;
		mages_legacy$eating = null;
		if (item == null || !((Object) this instanceof ServerPlayerEntity player)) {
			return;
		}
		RaceData data = RaceState.of(player);
		FoodComponent food = item.getFoodComponent();
		if (data != null && data.race == Race.MYCELID && food != null) {
			Mycelid.afterEating(player, data, item, food);
		}
		if (data != null && data.race == Race.BLOODKIN && food != null) {
			Bloodkin.afterEating(player, food);
		}
	}
}
