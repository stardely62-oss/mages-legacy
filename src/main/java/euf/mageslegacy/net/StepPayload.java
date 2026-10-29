package euf.mageslegacy.net;

import euf.mageslegacy.MagesLegacy;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.world.phys.Vec3;

/**
 * Server to client: the player starts a walk through the void along this line, over these ticks. The server carries
 * the body tick by tick; the client lays the same eased line under its camera, so the walk is smooth to the eye.
 */
public record StepPayload(Vec3 from, Vec3 to, int ticks) implements CustomPacketPayload {
	public static final Type<StepPayload> TYPE = new Type<>(MagesLegacy.id("step"));
	public static final StreamCodec<FriendlyByteBuf, StepPayload> STREAM_CODEC = CustomPacketPayload.codec(StepPayload::write, StepPayload::read);

	private void write(FriendlyByteBuf buf) {
		buf.writeVec3(from);
		buf.writeVec3(to);
		buf.writeVarInt(ticks);
	}

	private static StepPayload read(FriendlyByteBuf buf) {
		return new StepPayload(buf.readVec3(), buf.readVec3(), buf.readVarInt());
	}

	@Override
	public Type<StepPayload> type() {
		return TYPE;
	}
}
