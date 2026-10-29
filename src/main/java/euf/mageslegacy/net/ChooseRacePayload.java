package euf.mageslegacy.net;

import euf.mageslegacy.MagesLegacy;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/** Client to server: the race the player picked. */
public record ChooseRacePayload(String race) implements CustomPacketPayload {
	public static final Type<ChooseRacePayload> TYPE = new Type<>(MagesLegacy.id("choose_race"));
	public static final StreamCodec<ByteBuf, ChooseRacePayload> STREAM_CODEC = StreamCodec.composite(ByteBufCodecs.STRING_UTF8,
			ChooseRacePayload::race, ChooseRacePayload::new);

	@Override
	public Type<ChooseRacePayload> type() {
		return TYPE;
	}
}
