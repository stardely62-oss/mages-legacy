package euf.mageslegacy.client.fx;

import com.mojang.blaze3d.vertex.PoseStack;
import euf.mageslegacy.MagesLegacy;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.world.phys.Vec3;
import software.bernie.geckolib.animatable.GeoAnimatable;
import software.bernie.geckolib.animatable.instance.AnimatableInstanceCache;
import software.bernie.geckolib.animation.AnimatableManager;
import software.bernie.geckolib.animation.AnimationController;
import software.bernie.geckolib.animation.RawAnimation;
import software.bernie.geckolib.model.DefaultedGeoModel;
import software.bernie.geckolib.renderer.GeoObjectRenderer;
import software.bernie.geckolib.util.GeckoLibUtil;

/**
 * Shards of obsidian torn loose with a big rift, wheeling round it and bobbing (a GeckoLib model with its own
 * animation: geo/fx/void_shards.geo.json, animations/fx/void_shards.animation.json). They grow in as the rift opens
 * and sink back as it seals.
 */
final class VoidShards implements GeoAnimatable {
	private static final RawAnimation SPIN = RawAnimation.begin().thenLoop("animation.void_shards.spin");
	private static final List<VoidShards> ACTIVE = new ArrayList<>();
	private static GeoObjectRenderer<VoidShards> renderer;

	private final AnimatableInstanceCache cache = GeckoLibUtil.createInstanceCache(this);
	private final Vec3 at;
	private final float scale;
	private final int ticks;
	private int age;

	private VoidShards(Vec3 at, float scale, int ticks) {
		this.at = at;
		this.scale = scale;
		this.ticks = ticks;
	}

	static void spawn(Vec3 at, float height, int ticks) {
		ACTIVE.add(new VoidShards(at, height / 2.8f, ticks));
	}

	static void tick() {
		ACTIVE.removeIf(s -> ++s.age >= s.ticks);
	}

	static void render(PoseStack poses, Camera camera, float partial) {
		if (ACTIVE.isEmpty()) {
			return;
		}
		if (renderer == null) {
			renderer = new GeoObjectRenderer<>(new DefaultedGeoModel<VoidShards>(MagesLegacy.id("void_shards")) {
				@Override
				protected String subtype() {
					return "fx";
				}
			});
		}
		var buffers = Minecraft.getInstance().renderBuffers().bufferSource();
		Vec3 cam = camera.getPosition();
		for (VoidShards shards : ACTIVE) {
			float life = shards.age + partial;
			float grow = VoidRender.easeOutBack(Math.min(1, life / 8f));
			float sink = Math.max(0, Math.min(1, (shards.ticks - life) / 8f));
			float s = shards.scale * Math.max(0.01f, grow * sink);
			poses.pushPose();
			poses.translate(shards.at.x - cam.x, shards.at.y - cam.y, shards.at.z - cam.z);
			poses.scale(s, s, s);
			renderer.render(poses, shards, buffers, null, null, LightTexture.FULL_BRIGHT, partial);
			poses.popPose();
		}
		buffers.endBatch();
	}

	@Override
	public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {
		controllers.add(new AnimationController<>(this, "spin", 0, state -> state.setAndContinue(SPIN)));
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
