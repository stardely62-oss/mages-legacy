package euf.mageslegacy.net;

import euf.mageslegacy.MagesLegacy;
import io.netty.buffer.ByteBuf;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/** Server to client: the Mage's Shop's book with what it shows (see Mercenary#view); opens it, or refreshes it if open. */
public record MercViewPayload(CompoundTag view) implements CustomPacketPayload {
	public static final Type<MercViewPayload> TYPE = new Type<>(MagesLegacy.id("merc_view"));
	public static final StreamCodec<ByteBuf, MercViewPayload> STREAM_CODEC = StreamCodec.composite(ByteBufCodecs.COMPOUND_TAG,
			MercViewPayload::view, MercViewPayload::new);

	@Override
	public Type<MercViewPayload> type() {
		return TYPE;
	}
}
