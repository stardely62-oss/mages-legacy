package euf.mageslegacy.client;

import it.unimi.dsi.fastutil.ints.IntOpenHashSet;
import it.unimi.dsi.fastutil.ints.IntSet;
import net.minecraft.entity.Entity;
import net.minecraft.network.PacketByteBuf;

/** Which entities in the client's world are echoes, as the server told it; the renderer draws those as spirits. */
public final class EchoVisuals {
	private static final IntSet IDS = new IntOpenHashSet();

	private EchoVisuals() {
	}

	public static boolean isEcho(Entity entity) {
		return IDS.contains(entity.getId());
	}

	public static void read(PacketByteBuf buf) {
		IDS.clear();
		int count = buf.readVarInt();
		for (int i = 0; i < count; i++) {
			IDS.add(buf.readVarInt());
		}
	}

	public static void clear() {
		IDS.clear();
	}
}
