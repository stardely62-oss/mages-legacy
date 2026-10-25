package euf.mageslegacy.client.screen;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.resource.language.I18n;
import net.minecraft.text.Text;

/** Numbered lines from the language files: key.1, key.2, ... as many as exist. */
final class Lines {
	private Lines() {
	}

	static List<Text> of(String key) {
		List<Text> lines = new ArrayList<>();
		for (int i = 1; I18n.hasTranslation(key + "." + i); i++) {
			lines.add(Text.translatable(key + "." + i));
		}
		return lines;
	}
}
