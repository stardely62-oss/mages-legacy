package euf.mageslegacy.client.screen;

import euf.mageslegacy.client.MagesLegacyClient;
import euf.mageslegacy.race.Ability;
import euf.mageslegacy.race.Race;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.resources.language.I18n;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;

/**
 * A card for every race with its emblem on top; below, the chosen race: emblem, name and legend on the left, what the
 * mage gave it and, for an awakened race, its strengths, weaknesses and abilities (what each does is in its tooltip),
 * for a sleeping one its sealed legacy, on the right. The legend scrolls with the wheel when a large GUI scale leaves it
 * too little room.
 */
public class RaceSelectScreen extends Screen {
	private static final int CARD_Y = 24;
	private static final int CARD_H = 52;

	private Race selected = Race.MYCELID;
	private LegacyButton chooseButton;
	/** Scroll offset of the legend, and how far it can go (set while drawing). */
	private int legendScroll;
	private int legendMax;
	private int traitsScroll;
	private int traitsMax;

	public RaceSelectScreen() {
		super(Component.translatable("mages_legacy.screen.choose_title"));
	}

	@Override
	protected void init() {
		chooseButton = addRenderableWidget(new LegacyButton(width / 2 + 4, height - 26, 150, 20, Component.empty(), b -> choose()).primary());
		addRenderableWidget(new LegacyButton(width / 2 - 154, height - 26, 150, 20, Component.translatable("mages_legacy.screen.back_to_legend"),
				b -> minecraft.setScreen(new LoreScreen())));
		updateButton();
	}

	private void updateButton() {
		chooseButton.active = selected.playable;
		chooseButton.accent(LegacyUi.argb(selected.color));
		chooseButton.setMessage(selected.playable
				? Component.translatable("mages_legacy.screen.choose", Component.translatable(selected.key()))
				: Component.translatable("mages_legacy.screen.soon"));
	}

	public void select(Race race) {
		selected = race;
		legendScroll = 0;
		traitsScroll = 0;
		updateButton();
	}

	public Race selected() {
		return selected;
	}

	/** Whether the choose button can be pressed (the harness checks that every race still sleeps). */
	public boolean canChoose() {
		return chooseButton.active;
	}

	public void choose() {
		if (selected.playable) {
			MagesLegacyClient.sendChoice(selected.id());
			onClose();
		}
	}

	@Override
	public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
		if (mouseY >= detailTop() && mouseY < detailBottom()) {
			if (mouseX < width / 2.0) {
				legendScroll = Math.max(0, Math.min(legendMax, legendScroll - (int) (scrollY * 12)));
			} else {
				traitsScroll = Math.max(0, Math.min(traitsMax, traitsScroll - (int) (scrollY * 12)));
			}
			return true;
		}
		return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
	}

	/** A thin scrollbar along the right edge of a scrolled area. */
	private void scrollbar(GuiGraphics graphics, int x, int top, int bottom, int scroll, int max, int color) {
		if (max <= 0) {
			return;
		}
		int track = bottom - top;
		int thumb = Math.max(10, track * track / (track + max));
		int ty = top + (track - thumb) * scroll / max;
		graphics.fill(x, top, x + 2, bottom, LegacyUi.BLACK);
		graphics.fill(x, ty, x + 2, ty + thumb, color);
	}

	private static int cards() {
		return Race.values().length;
	}

	private int cardWidth() {
		return Math.min(110, (width - 24 - (cards() - 1) * 6) / cards());
	}

	private int cardX(int index) {
		int total = cards() * cardWidth() + (cards() - 1) * 6;
		return (width - total) / 2 + index * (cardWidth() + 6);
	}

	private int colWidth() {
		return Math.min(290, (width - 52) / 2);
	}

	private int detailTop() {
		return CARD_Y + CARD_H + 12;
	}

	private int detailBottom() {
		return height - 34;
	}

	@Override
	public boolean mouseClicked(double mouseX, double mouseY, int button) {
		for (Race race : Race.values()) {
			int x = cardX(race.ordinal());
			if (mouseX >= x && mouseX < x + cardWidth() && mouseY >= CARD_Y && mouseY < CARD_Y + CARD_H) {
				select(race);
				return true;
			}
		}
		return super.mouseClicked(mouseX, mouseY, button);
	}

	@Override
	public void renderBackground(GuiGraphics graphics, int mouseX, int mouseY, float delta) {
		LegacyUi.background(graphics, width, height);
	}

	@Override
	public void render(GuiGraphics graphics, int mouseX, int mouseY, float delta) {
		super.render(graphics, mouseX, mouseY, delta);
		LegacyUi.heading(graphics, title, width / 2, 9, LegacyUi.GOLD);
		for (Race race : Race.values()) {
			drawCard(graphics, race, mouseX, mouseY);
		}
		drawLegend(graphics, width / 2 - colWidth() - 8);
		Ability hovered = drawLegacy(graphics, width / 2 + 8, mouseX, mouseY);
		if (hovered != null) {
			graphics.renderComponentTooltip(font, abilityTooltip(hovered), mouseX, mouseY);
		}
	}

	private void drawCard(GuiGraphics graphics, Race race, int mouseX, int mouseY) {
		int x = cardX(race.ordinal());
		int w = cardWidth();
		int color = LegacyUi.argb(race.color);
		boolean isSelected = race == selected;
		boolean hot = mouseX >= x && mouseX < x + w && mouseY >= CARD_Y && mouseY < CARD_Y + CARD_H;
		int lift = isSelected ? 2 : 0;
		int y = CARD_Y - lift;
		LegacyUi.panel(graphics, x, y, x + w, y + CARD_H, isSelected ? color : hot ? LegacyUi.mix(LegacyUi.RIM, color, 0.6f) : LegacyUi.RIM_DARK);
		int cx = x + w / 2;
		if (isSelected) {
			float pulse = 0.75f + 0.25f * (float) Math.sin(LegacyUi.time() * 2.5);
			LegacyUi.glow(graphics, cx, y + 20, 22, color, pulse);
			graphics.fill(x + 1, y + CARD_H - 3, x + w - 1, y + CARD_H - 1, color);
		}
		if (LegacyUi.exists(LegacyUi.emblem(race))) {
			LegacyUi.emblem(graphics, race, cx - 16, y + 4, 32, race.playable || isSelected || hot);
		} else {
			LegacyUi.diamond(graphics, cx, y + 20, 10, color);
		}
		if (!race.playable) {
			LegacyUi.lock(graphics, x + w - 8, y + 9, isSelected || hot ? color : 0xFF6A6068);
		}
		String name = Component.translatable(race.key()).getString();
		int nameColor = race.playable ? (isSelected ? 0xFFFFFFFF : color) : (isSelected ? color : 0xFF7A7078);
		graphics.drawCenteredString(font, font.plainSubstrByWidth(name, w - 4), cx, y + 39, nameColor);
	}

	/** Left column: the emblem with a glow, the race's name and tagline, then its legend. */
	private void drawLegend(GuiGraphics graphics, int x) {
		int w = colWidth();
		int top = detailTop();
		int bottom = detailBottom();
		int color = LegacyUi.argb(selected.color);
		LegacyUi.panel(graphics, x - 6, top - 6, x + w + 6, bottom);

		int emblem = 40;
		LegacyUi.glow(graphics, x + emblem / 2, top + emblem / 2, 30, color, 0.8f);
		if (LegacyUi.exists(LegacyUi.emblem(selected))) {
			LegacyUi.emblem(graphics, selected, x, top, emblem, true);
		}
		graphics.pose().pushPose();
		graphics.pose().translate(x + emblem + 8, top + 6, 0);
		graphics.pose().scale(1.5f, 1.5f, 1f);
		graphics.drawString(font, Component.translatable(selected.key()), 0, 0, color);
		graphics.pose().popPose();
		graphics.drawString(font, Component.translatable(selected.key() + ".tagline").withStyle(ChatFormatting.ITALIC), x + emblem + 8, top + 24,
				LegacyUi.MUTED);

		int y = top + emblem + 6;
		LegacyUi.divider(graphics, x, x + w, y, color);
		// The legend scrolls inside the rest of the column
		int areaTop = y + 8;
		int areaBottom = bottom - 4;
		graphics.enableScissor(x - 2, areaTop, x + w + 2, areaBottom);
		y = areaTop - legendScroll;
		for (Component line : Lines.of(selected.key() + ".lore")) {
			for (FormattedCharSequence row : font.split(line.copy().withStyle(ChatFormatting.ITALIC), w - 4)) {
				graphics.drawString(font, row, x, y, LegacyUi.PARCHMENT, true);
				y += 10;
			}
			y += 4;
		}
		graphics.disableScissor();
		legendMax = Math.max(0, y + legendScroll - areaBottom);
		legendScroll = Math.min(legendScroll, legendMax);
		scrollbar(graphics, x + w + 2, areaTop, areaBottom, legendScroll, legendMax, color);
	}

	/**
	 * Right column: what the mage gave this people, then its legacy. While the race sleeps, the legacy is a sealed
	 * emblem breathing in the dark behind a lock.
	 */
	private Ability drawLegacy(GuiGraphics graphics, int x, int mouseX, int mouseY) {
		int w = colWidth();
		int top = detailTop();
		int bottom = detailBottom();
		int color = LegacyUi.argb(selected.color);
		LegacyUi.panel(graphics, x - 6, top - 6, x + w + 6, bottom);

		graphics.drawString(font, Component.translatable("mages_legacy.screen.gift").withStyle(ChatFormatting.BOLD), x, top, LegacyUi.GOLD);
		int y = top + 12;
		for (FormattedCharSequence row : font.split(Component.translatable(selected.key() + ".gift").withStyle(ChatFormatting.ITALIC), w - 4)) {
			graphics.drawString(font, row, x, y, LegacyUi.PARCHMENT, true);
			y += 10;
		}
		y += 4;
		LegacyUi.divider(graphics, x, x + w, y, color);
		if (selected.playable) {
			return drawTraits(graphics, x, y + 8, mouseX, mouseY);
		}

		// The sealed legacy, centred in the rest of the column
		List<FormattedCharSequence> desc = font.split(Component.translatable("mages_legacy.screen.asleep_desc").withStyle(ChatFormatting.ITALIC),
				w - 20);
		int size = Math.max(24, Math.min(56, bottom - y - 40 - desc.size() * 10));
		int block = size + 16 + 12 + desc.size() * 10;
		int cy = y + Math.max(6, (bottom - y - block) / 2);
		int cx = x + w / 2;
		float breath = 0.5f + 0.2f * (float) Math.sin(LegacyUi.time() * 1.4f);
		LegacyUi.glow(graphics, cx, cy + size / 2, size * 3 / 4, color, breath);
		if (LegacyUi.exists(LegacyUi.emblem(selected))) {
			LegacyUi.emblem(graphics, selected, cx - size / 2, cy, size, 0.3f, 0.7f);
		}
		LegacyUi.lock(graphics, cx, cy + size / 2 - 2, color);
		int ty = cy + size + 8;
		graphics.drawCenteredString(font, Component.translatable("mages_legacy.screen.asleep").withStyle(ChatFormatting.BOLD), cx, ty, color);
		ty += 14;
		for (FormattedCharSequence row : desc) {
			graphics.drawCenteredString(font, row, cx, ty, LegacyUi.MUTED);
			ty += 10;
		}
		return null;
	}

	private static final int TILE_H = 38;
	private static final int TILES_PER_ROW = 3;

	/** Strengths and weaknesses as gem lists (scrolling), the ability tiles below them. Returns the hovered ability. */
	private Ability drawTraits(GuiGraphics graphics, int x, int top, int mouseX, int mouseY) {
		int w = colWidth();
		int bottom = detailBottom();
		int color = LegacyUi.argb(selected.color);
		List<Ability> abilities = Ability.of(selected);
		int rows = (abilities.size() + TILES_PER_ROW - 1) / TILES_PER_ROW;
		int tilesH = rows * TILE_H + Math.max(0, rows - 1) * 4;
		int listBottom = bottom - (abilities.isEmpty() ? 6 : tilesH + 18);

		graphics.enableScissor(x - 2, top, x + w + 2, listBottom);
		int y = top - traitsScroll;
		y = section(graphics, Component.translatable("mages_legacy.screen.strengths"), x, y, LegacyUi.GREEN);
		y = gems(graphics, Lines.of(selected.key() + ".perk"), x, y, w - 4, LegacyUi.GREEN, 0xFFD6EBD0);
		y += 4;
		y = section(graphics, Component.translatable("mages_legacy.screen.weaknesses"), x, y, LegacyUi.RED);
		y = gems(graphics, Lines.of(selected.key() + ".weak"), x, y, w - 4, LegacyUi.RED, 0xFFEBD0D0);
		graphics.disableScissor();
		traitsMax = Math.max(0, y + traitsScroll - listBottom);
		traitsScroll = Math.min(traitsScroll, traitsMax);
		scrollbar(graphics, x + w + 2, top, listBottom, traitsScroll, traitsMax, color);
		if (abilities.isEmpty()) {
			return null;
		}

		int tilesTop = bottom - tilesH - 6;
		LegacyUi.divider(graphics, x, x + w, tilesTop - 8, color);
		int tileW = (w - 4 * (TILES_PER_ROW - 1)) / TILES_PER_ROW;
		Ability hovered = null;
		for (int i = 0; i < abilities.size(); i++) {
			Ability ability = abilities.get(i);
			int row = i / TILES_PER_ROW;
			int col = i % TILES_PER_ROW;
			// A shorter last row sits in the middle
			int inRow = Math.min(TILES_PER_ROW, abilities.size() - row * TILES_PER_ROW);
			int tx = x + (w - inRow * tileW - (inRow - 1) * 4) / 2 + col * (tileW + 4);
			int ty = tilesTop + row * (TILE_H + 4);
			boolean hot = mouseX >= tx && mouseX < tx + tileW && mouseY >= ty && mouseY < ty + TILE_H;
			if (hot) {
				hovered = ability;
			}
			LegacyUi.panel(graphics, tx, ty, tx + tileW, ty + TILE_H, hot ? color : LegacyUi.RIM_DARK);
			int cx = tx + tileW / 2;
			var icon = euf.mageslegacy.client.RaceHud.abilityIcon(ability);
			if (LegacyUi.exists(icon)) {
				LegacyUi.icon(graphics, icon, cx - 8, ty + 4, 16, true);
			}
			LegacyUi.keyCap(graphics, Ability.KEY_NAMES[ability.slot], tx + tileW - 2, ty + 13, LegacyUi.GOLD);
			String name = Component.translatable(ability.key() + ".name").getString();
			graphics.drawCenteredString(font, font.plainSubstrByWidth(name, tileW - 4), cx, ty + 23, LegacyUi.GOLD);
		}
		return hovered;
	}

	private int section(GuiGraphics graphics, Component title, int x, int y, int color) {
		graphics.drawString(font, title.copy().withStyle(ChatFormatting.BOLD), x, y, color);
		return y + 12;
	}

	private int gems(GuiGraphics graphics, List<Component> lines, int x, int y, int w, int gem, int color) {
		for (Component line : lines) {
			LegacyUi.diamond(graphics, x + 3, y + 3, 2, gem);
			for (FormattedCharSequence row : font.split(line, w - 10)) {
				graphics.drawString(font, row, x + 10, y, color, true);
				y += 10;
			}
			y += 1;
		}
		return y;
	}

	/** Name, the level it opens at and its key, the legend line, then what it does. */
	private List<Component> abilityTooltip(Ability ability) {
		List<Component> lines = new ArrayList<>();
		lines.add(Component.translatable(ability.key() + ".name").withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD));
		lines.add(Component.translatable("mages_legacy.screen.ability_meta", ability.level, Ability.KEY_NAMES[ability.slot], ability.cost,
				ability.cooldown / 20).withStyle(ChatFormatting.DARK_PURPLE));
		if (I18n.exists(ability.key() + ".lore")) {
			wrapInto(lines, Component.translatable(ability.key() + ".lore").withStyle(ChatFormatting.GRAY, ChatFormatting.ITALIC));
		}
		wrapInto(lines, Component.translatable(ability.key() + ".desc").withStyle(ChatFormatting.WHITE));
		return lines;
	}

	/** Tooltips don't wrap by themselves: long lines are cut to a readable width, keeping their style. */
	private void wrapInto(List<Component> lines, Component text) {
		for (var part : font.getSplitter().splitLines(text, 220, text.getStyle())) {
			lines.add(Component.literal(part.getString()).withStyle(text.getStyle()));
		}
	}

	/** Without a race the choice can't be put off (Esc does nothing); a player with a race reading it again may leave. */
	@Override
	public boolean shouldCloseOnEsc() {
		return euf.mageslegacy.client.ClientRace.race() != null;
	}

	@Override
	public boolean isPauseScreen() {
		return false;
	}
}
