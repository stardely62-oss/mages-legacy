package euf.mageslegacy.client.screen;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.resources.language.I18n;
import net.minecraft.network.chat.Component;

/** Numbered lines from the language files: key.1, key.2, ... as many as exist. */
final class Lines {
	private Lines() {
	}

	static List<Component> of(String key) {
		List<Component> lines = new ArrayList<>();
		for (int i = 1; I18n.exists(key + "." + i); i++) {
			lines.add(Component.translatable(key + "." + i));
		}
		return lines;
	}
}
