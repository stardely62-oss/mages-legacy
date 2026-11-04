package euf.mageslegacy.net;

import euf.mageslegacy.MagesLegacy;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/**
 * Client to server: something asked in the Mage's Shop's book (1 take, 2 give up, 3 deliver, 4 a fresh board, 5 buy,
 * 6 the book closed, 7 pin), the contract's id and the ware's id. The server checks all of it again.
 */
public record MercActionPayload(int action, int id, String ware) implements CustomPacketPayload {
	public static final Type<MercActionPayload> TYPE = new Type<>(MagesLegacy.id("merc_action"));
	public static final StreamCodec<ByteBuf, MercActionPayload> STREAM_CODEC = StreamCodec.composite(ByteBufCodecs.VAR_INT, MercActionPayload::action,
			ByteBufCodecs.VAR_INT, MercActionPayload::id, ByteBufCodecs.STRING_UTF8, MercActionPayload::ware, MercActionPayload::new);

	@Override
	public Type<MercActionPayload> type() {
		return TYPE;
	}
}
