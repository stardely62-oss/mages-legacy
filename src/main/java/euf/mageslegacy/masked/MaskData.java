package euf.mageslegacy.masked;

import java.util.LinkedHashMap;
import java.util.Map;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtElement;
import net.minecraft.nbt.NbtList;

/** A Masked's gallery: the faces taken, each with how fresh it still is (100 to 0), and which one is worn. */
public class MaskData {
	/** Face -> freshness, in the order they were taken. */
	public final Map<Face, Float> gallery = new LinkedHashMap<>();
	/** The face worn now, or null: faceless. */
	public Face worn;

	public void clear() {
		gallery.clear();
		worn = null;
	}

	public NbtCompound toNbt() {
		NbtCompound nbt = new NbtCompound();
		// A list, not a compound: the order faces were taken is the order Z goes through them
		NbtList faces = new NbtList();
		gallery.forEach((face, fresh) -> {
			NbtCompound entry = new NbtCompound();
			entry.putString("id", face.id);
			entry.putFloat("fresh", fresh);
			faces.add(entry);
		});
		nbt.put("gallery", faces);
		if (worn != null) {
			nbt.putString("worn", worn.id);
		}
		return nbt;
	}

	public void readNbt(NbtCompound nbt) {
		clear();
		for (NbtElement e : nbt.getList("gallery", NbtElement.COMPOUND_TYPE)) {
			NbtCompound entry = (NbtCompound) e;
			Face.byId(entry.getString("id")).ifPresent(face -> gallery.put(face, entry.getFloat("fresh")));
		}
		worn = Face.byId(nbt.getString("worn")).filter(gallery::containsKey).orElse(null);
	}
}
