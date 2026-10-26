package euf.mageslegacy.client;

import java.util.HashMap;
import java.util.Map;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderContext;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.render.Camera;
import net.minecraft.client.render.LightmapTextureManager;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import org.joml.Matrix4f;

/**
 * The blocks a Frozen quickened, as the server told this client: each shows a small hourglass with the seconds left
 * above it, like a name tag, to anyone close by.
 */
public final class QuickenVisuals {
	private static final int GOLD = 0xFFE8C98A;
	private static final double SHOW_WITHIN = 24;
	/** Block -> the client tick its quickening ends. */
	private static final Map<BlockPos, Long> UNTIL = new HashMap<>();

	private QuickenVisuals() {
	}

	public static void read(PacketByteBuf buf) {
		long ticks = ClientRaceState.clientTicks();
		UNTIL.clear();
		int count = buf.readVarInt();
		for (int i = 0; i < count; i++) {
			BlockPos pos = buf.readBlockPos();
			UNTIL.put(pos, ticks + buf.readVarInt());
		}
	}

	public static void clear() {
		UNTIL.clear();
	}

	public static void render(WorldRenderContext context) {
		if (UNTIL.isEmpty() || context.consumers() == null) {
			return;
		}
		MinecraftClient client = MinecraftClient.getInstance();
		long now = ClientRaceState.clientTicks();
		UNTIL.values().removeIf(until -> until <= now);
		Camera camera = context.camera();
		Vec3d cam = camera.getPos();
		MatrixStack matrices = context.matrixStack();
		VertexConsumerProvider consumers = context.consumers();
		TextRenderer font = client.textRenderer;
		for (Map.Entry<BlockPos, Long> e : UNTIL.entrySet()) {
			Vec3d at = Vec3d.ofCenter(e.getKey()).add(0, 0.95, 0);
			if (at.squaredDistanceTo(cam) > SHOW_WITHIN * SHOW_WITHIN) {
				continue;
			}
			long seconds = (e.getValue() - now + 19) / 20;
			String label = "⌛ " + seconds / 60 + ":" + String.format("%02d", seconds % 60);
			matrices.push();
			matrices.translate(at.x - cam.x, at.y - cam.y, at.z - cam.z);
			matrices.multiply(camera.getRotation());
			matrices.scale(-0.025f, -0.025f, 0.025f);
			Matrix4f matrix = matrices.peek().getPositionMatrix();
			float x = -font.getWidth(label) / 2f;
			font.draw(label, x, 0, GOLD, false, matrix, consumers, TextRenderer.TextLayerType.NORMAL, 0x60000000,
					LightmapTextureManager.MAX_LIGHT_COORDINATE);
			matrices.pop();
		}
	}
}
