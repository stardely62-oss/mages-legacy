package euf.mageslegacy.net;

import euf.mageslegacy.MagesLegacy;
import io.netty.buffer.ByteBuf;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/** Server to client: open the Mycelium Network's screen with what it shows (see MyceliumNetwork#view). */
public record NetworkViewPayload(CompoundTag view) implements CustomPacketPayload {
	public static final Type<NetworkViewPayload> TYPE = new Type<>(MagesLegacy.id("network_view"));
	public static final StreamCodec<ByteBuf, NetworkViewPayload> STREAM_CODEC = StreamCodec.composite(ByteBufCodecs.COMPOUND_TAG,
			NetworkViewPayload::view, NetworkViewPayload::new);

	@Override
	public Type<NetworkViewPayload> type() {
		return TYPE;
	}
}
