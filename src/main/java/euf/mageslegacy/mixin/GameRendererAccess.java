package euf.mageslegacy.mixin;

import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.PostChain;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** The menus' blur pass, so the Mage's Shop can blur its room by a radius of its own while its doors swing open. */
@Mixin(GameRenderer.class)
public interface GameRendererAccess {
	@Accessor("blurEffect")
	PostChain mages_legacy$blurEffect();
}
