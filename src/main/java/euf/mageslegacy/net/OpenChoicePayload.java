package euf.mageslegacy.net;

import euf.mageslegacy.MagesLegacy;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/** Server to client: open the legend and the choice of race. */
public record OpenChoicePayload() implements CustomPacketPayload {
	public static final Type<OpenChoicePayload> TYPE = new Type<>(MagesLegacy.id("open_choice"));
	public static final StreamCodec<ByteBuf, OpenChoicePayload> STREAM_CODEC = StreamCodec.unit(new OpenChoicePayload());

	@Override
	public Type<OpenChoicePayload> type() {
		return TYPE;
	}
}
