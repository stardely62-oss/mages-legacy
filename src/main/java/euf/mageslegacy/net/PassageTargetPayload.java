package euf.mageslegacy.net;

import euf.mageslegacy.MagesLegacy;
import io.netty.buffer.ByteBuf;
import java.util.UUID;
import net.minecraft.core.UUIDUtil;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/** Client to server: open Through Worlds to this player. */
public record PassageTargetPayload(UUID target) implements CustomPacketPayload {
	public static final Type<PassageTargetPayload> TYPE = new Type<>(MagesLegacy.id("passage_target"));
	public static final StreamCodec<ByteBuf, PassageTargetPayload> STREAM_CODEC = StreamCodec.composite(UUIDUtil.STREAM_CODEC,
			PassageTargetPayload::target, PassageTargetPayload::new);

	@Override
	public Type<PassageTargetPayload> type() {
		return TYPE;
	}
}
