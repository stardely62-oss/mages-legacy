package euf.mageslegacy.net;

import euf.mageslegacy.MagesLegacy;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/** Client to server: something asked on the Mycelium Network's screen (grow, travel, link, rename, open) and its two nodes or text. */
public record NetworkActionPayload(String action, String a, String b) implements CustomPacketPayload {
	public static final Type<NetworkActionPayload> TYPE = new Type<>(MagesLegacy.id("network_action"));
	public static final StreamCodec<ByteBuf, NetworkActionPayload> STREAM_CODEC = StreamCodec.composite(ByteBufCodecs.STRING_UTF8,
			NetworkActionPayload::action, ByteBufCodecs.STRING_UTF8, NetworkActionPayload::a, ByteBufCodecs.STRING_UTF8, NetworkActionPayload::b,
			NetworkActionPayload::new);

	@Override
	public Type<NetworkActionPayload> type() {
		return TYPE;
	}
}
