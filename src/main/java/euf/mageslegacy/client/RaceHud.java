package euf.mageslegacy.client;

import euf.mageslegacy.client.screen.LegacyUi;
import euf.mageslegacy.net.SyncPayload;
import euf.mageslegacy.race.Ability;
import euf.mageslegacy.race.Race;
import euf.mageslegacy.race.RaceState;
import java.util.List;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;

/**
 * Left of the hotbar: the race's abilities as stone slots with their keys (dark with the level they open at while
 * locked, a falling shade while recovering) and the Void bar beneath. While walking through the void the screen
 * sinks into violet dark, which also hides the inside of the walls being crossed.
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
		if (ClientRace.stepping()) {
			veil(graphics, width, height, ClientRace.stepLeft() - delta.getGameTimeDeltaPartialTick(false));
		}
		List<Ability> abilities = Ability.bySlot(race);
		if (abilities.isEmpty()) {
			return;
		}
		Font font = mc.font;
		int color = LegacyUi.argb(race.color);
		int total = abilities.size() * (SLOT + 3) - 3;
		int x0 = width / 2 - 91 - 10 - total;
		int y0 = height - SLOT - 9;
		for (int i = 0; i < abilities.size(); i++) {
			slot(graphics, font, abilities.get(i), state, x0 + i * (SLOT + 3), y0, color);
		}
		// The Void beneath the slots
		int barY = height - 6;
		float fill = state.resource() / RaceState.MAX_RESOURCE;
		graphics.fill(x0 - 1, barY - 1, x0 + total + 1, barY + 3, LegacyUi.BLACK);
		graphics.fill(x0, barY, x0 + total, barY + 2, 0xFF1A1220);
		graphics.fill(x0, barY, x0 + Math.round(total * fill), barY + 2, color);
		String amount = String.valueOf((int) state.resource());
		graphics.drawString(font, amount, x0 - font.width(amount) - 3, barY - 3, color);
	}

	private static void slot(GuiGraphics graphics, Font font, Ability ability, SyncPayload state, int x, int y, int color) {
		boolean locked = state.level() < ability.level;
		int cooldown = state.cooldowns()[ability.slot];
		int total = Math.max(1, state.cooldownTotals()[ability.slot]);
		boolean affordable = state.resource() >= ability.cost;
		LegacyUi.panel(graphics, x, y, x + SLOT, y + SLOT, locked ? LegacyUi.RIM_DARK : cooldown > 0 ? LegacyUi.RIM : color);
		var icon = LegacyUi.gui("icon/ability_" + ability.id());
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
		LegacyUi.keyCap(graphics, Ability.KEY_NAMES[ability.slot], x + SLOT + 1, y + SLOT + 2, cooldown > 0 ? LegacyUi.MUTED : LegacyUi.GOLD);
	}

	/** The in-between: violet dark closing in at the start, deepest in the middle of the walk, opening at the end. */
	private static void veil(GuiGraphics graphics, int width, int height, float left) {
		float t = 1 - left / 25f;
		// Dark at once on entering, nearly black in the middle (the inside of walls is never seen), lifting at the end
		float depth = (float) Math.min(1, Math.sin(Math.PI * Math.min(1, Math.max(0, t))) * 1.6);
		int core = LegacyUi.alpha(0xFF0A0418, 0.7f + 0.28f * depth);
		graphics.fill(0, 0, width, height, core);
		int edge = LegacyUi.alpha(0xFF6A3AB0, 0.35f * depth);
		int band = Math.max(8, height / 6);
		graphics.fillGradient(0, 0, width, band, edge, 0x00000000);
		graphics.fillGradient(0, height - band, width, height, 0x00000000, edge);
	}

	static Component keyName(int slot) {
		return MagesLegacyClient.ABILITY_KEYS[slot].getTranslatedKeyMessage();
	}
}
