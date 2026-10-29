package euf.mageslegacy.client.fx;

import com.mojang.blaze3d.vertex.PoseStack;
import euf.mageslegacy.MagesLegacy;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import software.bernie.geckolib.animatable.GeoAnimatable;
import software.bernie.geckolib.animatable.instance.AnimatableInstanceCache;
import software.bernie.geckolib.animation.AnimatableManager;
import software.bernie.geckolib.animation.AnimationController;
import software.bernie.geckolib.animation.RawAnimation;
import software.bernie.geckolib.model.DefaultedGeoModel;
import software.bernie.geckolib.renderer.GeoObjectRenderer;
import software.bernie.geckolib.renderer.layer.AutoGlowingGeoLayer;
import software.bernie.geckolib.util.GeckoLibUtil;

/**
 * The Great Bloom: the Mycelid grown into a giant mushroom (a GeckoLib model: geo/fx/great_bloom.geo.json with its
 * grow and idle animations, its spots and gills glowing through a glow mask). It rises out of the ground at the
 * Mycelid's feet, sways while the Bloom lasts and withers back into the ground when it ends.
 */
final class GreatBloom implements GeoAnimatable {
	private static final RawAnimation LIFE = RawAnimation.begin().thenPlay("animation.great_bloom.grow").thenLoop("animation.great_bloom.idle");
	private static final List<GreatBloom> ACTIVE = new ArrayList<>();
	/** How long the mushroom takes to wither back into the ground. */
	private static final int WITHER = 16;
	private static GeoObjectRenderer<GreatBloom> renderer;

	private final AnimatableInstanceCache cache = GeckoLibUtil.createInstanceCache(this);
	private final Entity body;
	private final int id;
	private int ticks;
	private int age;
	private int withering = -1;

	private GreatBloom(Entity body, int id, int ticks) {
		this.body = body;
		this.id = id;
		this.ticks = ticks;
	}

	/** A Bloom on this body for so long; 0 (or less) withers the one there now. */
	static void play(Entity body, int id, int ticks) {
		for (GreatBloom bloom : ACTIVE) {
			if (bloom.id == id && bloom.withering < 0) {
				if (ticks <= 0) {
					bloom.withering = 0;
				} else {
					bloom.ticks = bloom.age + ticks;
				}
				return;
			}
		}
		if (ticks > 0 && body != null) {
			ACTIVE.add(new GreatBloom(body, id, ticks));
		}
	}

	static void tick() {
		for (Iterator<GreatBloom> it = ACTIVE.iterator(); it.hasNext(); ) {
			GreatBloom bloom = it.next();
			bloom.age++;
			if (bloom.withering < 0 && bloom.age > 20) {
				SporeFx.breathe(bloom.body.position(), 1.3f);
			}
			if (bloom.withering < 0 && (bloom.age >= bloom.ticks || bloom.body.isRemoved())) {
				bloom.withering = 0;
			}
			if (bloom.withering >= 0 && ++bloom.withering > WITHER) {
				it.remove();
			}
		}
	}

	static void render(PoseStack poses, Camera camera, float partial) {
		if (ACTIVE.isEmpty()) {
			return;
		}
		if (renderer == null) {
			renderer = new GeoObjectRenderer<>(new DefaultedGeoModel<GreatBloom>(MagesLegacy.id("great_bloom")) {
				@Override
				protected String subtype() {
					return "fx";
				}
			});
			renderer.addRenderLayer(new AutoGlowingGeoLayer<>(renderer));
		}
		var buffers = Minecraft.getInstance().renderBuffers().bufferSource();
		Vec3 cam = camera.getPosition();
		for (GreatBloom bloom : ACTIVE) {
			Vec3 at = bloom.body.getPosition(partial);
			// Withering: the mushroom sinks and shrinks back into the ground
			float wither = bloom.withering < 0 ? 1 : Math.max(0, 1 - (bloom.withering + partial) / WITHER);
			float s = 1.3f * (0.15f + 0.85f * wither);
			poses.pushPose();
			poses.translate(at.x - cam.x, at.y - cam.y - (1 - wither) * 1.5, at.z - cam.z);
			poses.scale(s, s * wither, s);
			renderer.render(poses, bloom, buffers, null, null, LightTexture.FULL_BRIGHT, partial);
			poses.popPose();
		}
		buffers.endBatch();
	}

	/**
	 * The view from outside while the player is the mushroom: drawn back far enough to see the whole of it, and
	 * eased back in as it withers.
	 */
	static void onCameraDistance(net.neoforged.neoforge.client.event.CalculateDetachedCameraDistanceEvent event) {
		var player = Minecraft.getInstance().player;
		if (player == null) {
			return;
		}
		for (GreatBloom bloom : ACTIVE) {
			if (bloom.id == player.getId()) {
				float grow = Math.min(1, bloom.age / 20f);
				float wither = bloom.withering < 0 ? 1 : Math.max(0, 1 - bloom.withering / (float) WITHER);
				float far = 11 * grow * wither;
				event.setDistance(Math.max(event.getDistance(), event.getDistance() + (far - event.getDistance()) * Math.min(grow, wither)));
			}
		}
	}

	@Override
	public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {
		controllers.add(new AnimationController<>(this, "life", 0, state -> state.setAndContinue(LIFE)));
	}

	@Override
	public AnimatableInstanceCache getAnimatableInstanceCache() {
		return cache;
	}

	@Override
	public double getTick(Object object) {
		return age;
	}
}
