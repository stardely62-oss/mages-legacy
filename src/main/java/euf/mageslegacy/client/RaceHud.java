package euf.mageslegacy.client;

import euf.mageslegacy.client.screen.LegacyUi;
import euf.mageslegacy.net.SyncPayload;
import euf.mageslegacy.race.Ability;
import euf.mageslegacy.race.Awakenings;
import euf.mageslegacy.race.Talent;
import euf.mageslegacy.race.Race;
import euf.mageslegacy.race.RaceState;
import java.util.List;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;

/**
 * Right of the hotbar: the race's abilities as stone slots with their keys (dark with the level they open at while
 * locked, a falling shade while recovering) and the Void bar beneath.
 */
public final class RaceHud {
	private static final int SLOT = 22;

	private RaceHud() {
	}

	public static void render(GuiGraphics graphics, DeltaTracker delta) {
		Minecraft mc = Minecraft.getInstance();
		SyncPayload state = ClientRace.state();
		Race race = ClientRace.race();
		if (state == null || race == null || mc.options.hideGui || mc.player == null) {
			return;
		}
		int width = graphics.guiWidth();
		int height = graphics.guiHeight();
		// The fixed keys, then the Awakenings on Z and Alt in the order they were learnt
		List<Ability> abilities = new java.util.ArrayList<>(Ability.bySlot(race));
		List<Integer> keys = new java.util.ArrayList<>();
		for (Ability a : abilities) {
			keys.add(a.slot);
		}
		for (int key : new int[] {Awakenings.FIRST_SLOT, Awakenings.SECOND_SLOT}) {
			Awakenings.at(state.talents(), key).ifPresent(a -> {
				abilities.add(a);
				keys.add(key);
			});
		}
		if (abilities.isEmpty()) {
			return;
		}
		Font font = mc.font;
		int color = LegacyUi.argb(race.color);
		int total = abilities.size() * (SLOT + 3) - 3;
		int x0 = width / 2 + 91 + 12;
		int y0 = height - SLOT - 9;
		for (int i = 0; i < abilities.size(); i++) {
			slot(graphics, font, abilities.get(i), keys.get(i), state, x0 + i * (SLOT + 3), y0, color);
		}
		// The Void beneath the slots
		int barY = height - 6;
		float fill = state.resource() / RaceState.MAX_RESOURCE;
		graphics.fill(x0 - 1, barY - 1, x0 + total + 1, barY + 3, LegacyUi.BLACK);
		graphics.fill(x0, barY, x0 + total, barY + 2, 0xFF1A1220);
		graphics.fill(x0, barY, x0 + Math.round(total * fill), barY + 2, color);
		String amount = String.valueOf((int) state.resource());
		graphics.drawString(font, amount, x0 + total + 4, barY - 3, color);
	}

	private static void slot(GuiGraphics graphics, Font font, Ability ability, int key, SyncPayload state, int x, int y, int color) {
		boolean locked = !ability.awakening() && state.level() < ability.level;
		int cooldown = state.cooldowns()[key];
		int total = Math.max(1, state.cooldownTotals()[key]);
		boolean affordable = state.resource() >= ability.cost;
		LegacyUi.panel(graphics, x, y, x + SLOT, y + SLOT, locked ? LegacyUi.RIM_DARK : cooldown > 0 ? LegacyUi.RIM : color);
		var icon = ability.awakening() ? awakeningIcon(ability) : LegacyUi.gui("icon/ability_" + ability.id());
		if (LegacyUi.exists(icon)) {
			LegacyUi.icon(graphics, icon, x + 3, y + 3, 16, !locked && affordable);
		}
		if (locked) {
			graphics.fill(x + 1, y + 1, x + SLOT - 1, y + SLOT - 1, 0xB0000000);
			String level = String.valueOf(ability.level);
			graphics.drawString(font, level, x + (SLOT - font.width(level)) / 2, y + 7, LegacyUi.MUTED);
			return;
		}
		if (cooldown > 0) {
			int shade = Math.round((SLOT - 2) * cooldown / (float) total);
			graphics.fill(x + 1, y + SLOT - 1 - shade, x + SLOT - 1, y + SLOT - 1, 0xA0000000);
			String secs = String.valueOf((cooldown + 19) / 20);
			graphics.drawString(font, secs, x + (SLOT - font.width(secs)) / 2, y + 7, 0xFFFFFFFF);
		}
		LegacyUi.keyCap(graphics, Ability.KEY_NAMES[key], x + SLOT + 1, y + SLOT + 2, cooldown > 0 ? LegacyUi.MUTED : LegacyUi.GOLD);
		// Through Worlds' charges: a small gem for each, lit while ready
		if (ability == Ability.PASSAGE && state.chargesMax() > 1) {
			for (int i = 0; i < state.chargesMax(); i++) {
				int gx = x + 3 + i * 5;
				LegacyUi.diamond(graphics, gx, y + SLOT - 3, 2, i < state.charges() ? 0xFFE8DEFF : 0xFF3A2E48);
			}
		}
		if (ability.awakening()) {
			// An Awakening's slot wears the crown's gems
			LegacyUi.diamond(graphics, x + SLOT / 2, y - 1, 2, color);
		}
	}

	private static net.minecraft.resources.ResourceLocation awakeningIcon(Ability ability) {
		Talent talent = Awakenings.talentOf(ability);
		var own = LegacyUi.gui("talent/" + talent.id());
		return LegacyUi.exists(own) ? own : LegacyUi.gui("talent/" + talent.icon);
	}

	static Component keyName(int slot) {
		return MagesLegacyClient.ABILITY_KEYS[slot].getTranslatedKeyMessage();
	}
}
