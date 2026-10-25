package euf.mageslegacy.mixin;

import java.util.UUID;
import net.minecraft.entity.passive.FoxEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

/** A bonded fox trusts its Shepherd, so it no longer runs away. */
@Mixin(FoxEntity.class)
public interface FoxEntityInvoker {
	@Invoker("addTrustedUuid")
	void mages_legacy$trust(UUID uuid);
}
