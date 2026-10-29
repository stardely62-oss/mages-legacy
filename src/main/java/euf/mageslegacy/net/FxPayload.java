package euf.mageslegacy.net;

import euf.mageslegacy.MagesLegacy;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.world.phys.Vec3;

/**
 * Server to client: play a visual effect. {@code entity} is the entity it follows (-1 for none), {@code from} where it
 * plays, {@code to} a second point (the end of a path), {@code ticks} how long it lasts.
 */
public record FxPayload(int kind, int entity, Vec3 from, Vec3 to, int ticks) implements CustomPacketPayload {
	public static final Type<FxPayload> TYPE = new Type<>(MagesLegacy.id("fx"));
	public static final StreamCodec<FriendlyByteBuf, FxPayload> STREAM_CODEC = CustomPacketPayload.codec(FxPayload::write, FxPayload::read);

	private void write(FriendlyByteBuf buf) {
		buf.writeVarInt(kind);
		buf.writeVarInt(entity);
		buf.writeVec3(from);
		buf.writeVec3(to);
		buf.writeVarInt(ticks);
	}

	private static FxPayload read(FriendlyByteBuf buf) {
		return new FxPayload(buf.readVarInt(), buf.readVarInt(), buf.readVec3(), buf.readVec3(), buf.readVarInt());
	}

	@Override
	public Type<FxPayload> type() {
		return TYPE;
	}
}
