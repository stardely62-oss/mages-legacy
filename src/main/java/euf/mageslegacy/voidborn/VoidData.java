package euf.mageslegacy.voidborn;

import net.minecraft.nbt.NbtCompound;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;

/** A Voidborn's own: which room of the pocket dimension is theirs, and where they stepped in from. */
public class VoidData {
	/** The room's number (it lies at x = number * Pocket.SPACING); -1 until the first visit. */
	public int room = -1;
	/** How far the room's island has been laid from its middle (0: not yet). */
	public int builtHalf;
	/** Where to come back out to: the world, the spot and the facing. Null when not in the pocket. */
	public RegistryKey<World> returnWorld;
	public Vec3d returnPos;
	public float returnYaw;
	public float returnPitch;

	public NbtCompound toNbt() {
		NbtCompound nbt = new NbtCompound();
		nbt.putInt("room", room);
		nbt.putInt("builtHalf", builtHalf);
		if (returnWorld != null && returnPos != null) {
			nbt.putString("returnWorld", returnWorld.getValue().toString());
			nbt.putDouble("rx", returnPos.x);
			nbt.putDouble("ry", returnPos.y);
			nbt.putDouble("rz", returnPos.z);
			nbt.putFloat("ryaw", returnYaw);
			nbt.putFloat("rpitch", returnPitch);
		}
		return nbt;
	}

	public void readNbt(NbtCompound nbt) {
		room = nbt.contains("room") ? nbt.getInt("room") : -1;
		builtHalf = nbt.getInt("builtHalf");
		if (nbt.contains("returnWorld")) {
			Identifier id = Identifier.tryParse(nbt.getString("returnWorld"));
			returnWorld = id == null ? null : RegistryKey.of(RegistryKeys.WORLD, id);
			returnPos = new Vec3d(nbt.getDouble("rx"), nbt.getDouble("ry"), nbt.getDouble("rz"));
			returnYaw = nbt.getFloat("ryaw");
			returnPitch = nbt.getFloat("rpitch");
		}
	}
}
