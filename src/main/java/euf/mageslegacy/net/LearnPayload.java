package euf.mageslegacy.net;

import euf.mageslegacy.MagesLegacy;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/** Client to server: learn this talent. */
public record LearnPayload(String talent) implements CustomPacketPayload {
	public static final Type<LearnPayload> TYPE = new Type<>(MagesLegacy.id("learn"));
	public static final StreamCodec<ByteBuf, LearnPayload> STREAM_CODEC = StreamCodec.composite(ByteBufCodecs.STRING_UTF8, LearnPayload::talent,
			LearnPayload::new);

	@Override
	public Type<LearnPayload> type() {
		return TYPE;
	}
}
