package euf.mageslegacy.net;

import euf.mageslegacy.MagesLegacy;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/** Server to client: Through Worlds may be opened now; show whom to open it to. */
public record OpenPassagePayload() implements CustomPacketPayload {
	public static final Type<OpenPassagePayload> TYPE = new Type<>(MagesLegacy.id("open_passage"));
	public static final StreamCodec<ByteBuf, OpenPassagePayload> STREAM_CODEC = StreamCodec.unit(new OpenPassagePayload());

	@Override
	public Type<OpenPassagePayload> type() {
		return TYPE;
	}
}
