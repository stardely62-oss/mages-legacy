package euf.mageslegacy.net;

import euf.mageslegacy.MagesLegacy;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/** Client to server: the player pressed the ability key of this slot. */
public record AbilityPayload(int slot) implements CustomPacketPayload {
	public static final Type<AbilityPayload> TYPE = new Type<>(MagesLegacy.id("ability"));
	public static final StreamCodec<ByteBuf, AbilityPayload> STREAM_CODEC = StreamCodec.composite(ByteBufCodecs.VAR_INT, AbilityPayload::slot,
			AbilityPayload::new);

	@Override
	public Type<AbilityPayload> type() {
		return TYPE;
	}
}
