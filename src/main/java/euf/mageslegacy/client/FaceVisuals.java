package euf.mageslegacy.client;

import euf.mageslegacy.masked.Face;
import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.entity.EntityRenderer;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.network.PacketByteBuf;

/**
 * Which players wear which face, as the server told it, and the stand-in bodies they are drawn as: a monster of the
 * face's kind, never added to the world, that copies the wearer's place, turn and stride every frame.
 */
public final class FaceVisuals {
	private static final Int2ObjectMap<Face> FACES = new Int2ObjectOpenHashMap<>();
	private static final Int2ObjectMap<LivingEntity> BODIES = new Int2ObjectOpenHashMap<>();

	private FaceVisuals() {
	}

	public static void read(PacketByteBuf buf) {
		FACES.clear();
		int count = buf.readVarInt();
		Face[] faces = Face.values();
		for (int i = 0; i < count; i++) {
			int id = buf.readVarInt();
			int face = buf.readVarInt();
			if (face >= 0 && face < faces.length) {
				FACES.put(id, faces[face]);
			}
		}
		// A body whose wearer changed faces (or took it off) is made anew
		BODIES.int2ObjectEntrySet().removeIf(e -> FACES.get(e.getIntKey()) == null || !FACES.get(e.getIntKey()).look().equals(e.getValue().getType()));
	}

	/** The face this entity is drawn in, or null. */
	public static Face of(int entityId) {
		return FACES.get(entityId);
	}

	public static void clear() {
		FACES.clear();
		BODIES.clear();
	}

	/** Walks the stand-ins in step with their wearers, once a client tick. */
	public static void tick(MinecraftClient client) {
		if (client.world == null) {
			return;
		}
		for (Int2ObjectMap.Entry<LivingEntity> e : BODIES.int2ObjectEntrySet()) {
			if (client.world.getEntityById(e.getIntKey()) instanceof PlayerEntity player) {
				LivingEntity body = e.getValue();
				body.limbAnimator.updateLimbs(player.limbAnimator.getSpeed(), 1.0f);
				body.age = player.age;
			}
		}
	}

	/** Draws a wearer as its face's monster; false when it wears none and should be drawn as itself. */
	public static boolean render(PlayerEntity player, float yaw, float tickDelta, MatrixStack matrices, VertexConsumerProvider vertices, int light) {
		Face face = FACES.get(player.getId());
		if (face == null || player.isInvisible()) {
			return false;
		}
		LivingEntity body = BODIES.get(player.getId());
		if (body == null) {
			Entity made = face.look().create(player.getWorld());
			if (!(made instanceof LivingEntity living)) {
				return false;
			}
			body = living;
			BODIES.put(player.getId(), body);
		}
		body.copyPositionAndRotation(player);
		body.prevX = player.prevX;
		body.prevY = player.prevY;
		body.prevZ = player.prevZ;
		body.prevYaw = player.prevYaw;
		body.prevPitch = player.prevPitch;
		body.bodyYaw = player.bodyYaw;
		body.prevBodyYaw = player.prevBodyYaw;
		body.headYaw = player.headYaw;
		body.prevHeadYaw = player.prevHeadYaw;
		body.handSwingProgress = player.handSwingProgress;
		body.lastHandSwingProgress = player.lastHandSwingProgress;
		body.hurtTime = player.hurtTime;
		body.setOnGround(player.isOnGround());
		body.setSneaking(player.isSneaking());
		// Other players see whose face it is; the wearer's own third-person view stays clean
		boolean named = player != MinecraftClient.getInstance().getCameraEntity();
		body.setCustomName(named ? player.getDisplayName() : null);
		body.setCustomNameVisible(named);
		EntityRenderer<? super LivingEntity> renderer = MinecraftClient.getInstance().getEntityRenderDispatcher().getRenderer(body);
		renderer.render(body, yaw, tickDelta, matrices, vertices, light);
		return true;
	}
}
