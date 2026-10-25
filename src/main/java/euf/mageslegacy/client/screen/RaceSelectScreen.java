package euf.mageslegacy.client.screen;

import euf.mageslegacy.client.MagesLegacyClient;
import euf.mageslegacy.client.RaceHud;
import euf.mageslegacy.race.Race;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.resource.language.I18n;
import net.minecraft.text.OrderedText;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

/**
 * Five race cards with their emblems on top; below, the chosen race: emblem, name and legend on the left, strengths,
 * weaknesses and ability tiles on the right. What an ability does is in its tooltip.
 */
public class RaceSelectScreen extends Screen {
	private static final int CARD_Y = 24;
	private static final int CARD_H = 52;
	private static final int TILE_H = 38;

	private Race selected = Race.MYCELID;
	private LegacyButton chooseButton;

	public RaceSelectScreen() {
		super(Text.translatable("mages_legacy.screen.choose_title"));
	}

	@Override
	protected void init() {
		chooseButton = addDrawableChild(new LegacyButton(width / 2 + 4, height - 26, 150, 20, Text.empty(), b -> choose()).primary());
		addDrawableChild(new LegacyButton(width / 2 - 154, height - 26, 150, 20, Text.translatable("mages_legacy.screen.back_to_legend"),
				b -> client.setScreen(new LoreScreen())));
		updateButton();
	}

	private void updateButton() {
		chooseButton.active = selected.playable;
		chooseButton.accent(LegacyUi.argb(selected.color));
		chooseButton.setMessage(selected.playable
				? Text.translatable("mages_legacy.screen.choose", Text.translatable(selected.key()))
				: Text.translatable("mages_legacy.screen.soon"));
	}

	public void select(Race race) {
		selected = race;
		updateButton();
	}

	public Race selected() {
		return selected;
	}

	public void choose() {
		if (selected.playable) {
			MagesLegacyClient.sendChoice(selected.id());
		}
	}

	private int cardWidth() {
		return Math.min(110, (width - 24 - 4 * 8) / 5);
	}

	private int cardX(int index) {
		int total = 5 * cardWidth() + 4 * 8;
		return (width - total) / 2 + index * (cardWidth() + 8);
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
	public void render(DrawContext ctx, int mouseX, int mouseY, float delta) {
		LegacyUi.background(ctx, width, height, null);
		LegacyUi.heading(ctx, title, width / 2, 9, LegacyUi.GOLD);
		for (Race race : Race.values()) {
			drawCard(ctx, race, mouseX, mouseY);
		}
		int leftX = width / 2 - colWidth() - 8;
		int rightX = width / 2 + 8;
		drawLegend(ctx, leftX);
		int hoveredSlot = drawTraits(ctx, rightX, mouseX, mouseY);
		super.render(ctx, mouseX, mouseY, delta);
		if (hoveredSlot > 0) {
			ctx.drawTooltip(textRenderer, abilityTooltip(selected, hoveredSlot), mouseX, mouseY);
		}
	}

	private void drawCard(DrawContext ctx, Race race, int mouseX, int mouseY) {
		int x = cardX(race.ordinal());
		int w = cardWidth();
		int color = LegacyUi.argb(race.color);
		boolean isSelected = race == selected;
		boolean hot = mouseX >= x && mouseX < x + w && mouseY >= CARD_Y && mouseY < CARD_Y + CARD_H;
		int lift = isSelected ? 2 : 0;
		int y = CARD_Y - lift;
		LegacyUi.panel(ctx, x, y, x + w, y + CARD_H, isSelected ? color : hot ? LegacyUi.mix(LegacyUi.RIM, color, 0.6f) : LegacyUi.RIM_DARK);
		int cx = x + w / 2;
		if (isSelected) {
			float pulse = 0.75f + 0.25f * (float) Math.sin(LegacyUi.time() * 2.5);
			LegacyUi.glow(ctx, cx, y + 20, 22, color, pulse);
			ctx.fill(x + 1, y + CARD_H - 3, x + w - 1, y + CARD_H - 1, color);
		}
		if (LegacyUi.exists(LegacyUi.emblem(race))) {
			LegacyUi.emblem(ctx, race, cx - 16, y + 4, 32, race.playable || isSelected || hot);
		} else {
			LegacyUi.diamond(ctx, cx, y + 20, 10, color);
		}
		Text name = Text.translatable(race.key());
		int nameColor = race.playable ? (isSelected ? 0xFFFFFFFF : color) : (isSelected ? color : 0xFF7A7078);
		ctx.drawCenteredTextWithShadow(textRenderer, textRenderer.trimToWidth(name.getString(), w - 4), cx, y + 39, nameColor);
	}

	/** Left column: the emblem with a glow, the race's name and tagline, then its legend. */
	private void drawLegend(DrawContext ctx, int x) {
		int w = colWidth();
		int top = detailTop();
		int bottom = detailBottom();
		int color = LegacyUi.argb(selected.color);
		LegacyUi.panel(ctx, x - 6, top - 6, x + w + 6, bottom);

		int emblem = 40;
		LegacyUi.glow(ctx, x + emblem / 2, top + emblem / 2, 30, color, 0.8f);
		if (LegacyUi.exists(LegacyUi.emblem(selected))) {
			LegacyUi.emblem(ctx, selected, x, top, emblem, true);
		}
		ctx.getMatrices().push();
		ctx.getMatrices().translate(x + emblem + 8, top + 6, 0);
		ctx.getMatrices().scale(1.5f, 1.5f, 1f);
		ctx.drawTextWithShadow(textRenderer, Text.translatable(selected.key()), 0, 0, color);
		ctx.getMatrices().pop();
		ctx.drawTextWithShadow(textRenderer, Text.translatable(selected.key() + ".tagline").formatted(Formatting.ITALIC), x + emblem + 8, top + 24,
				LegacyUi.MUTED);

		int y = top + emblem + 6;
		LegacyUi.divider(ctx, x, x + w, y, color);
		y += 8;
		for (Text line : Lines.of(selected.key() + ".lore")) {
			for (OrderedText row : textRenderer.wrapLines(line.copy().formatted(Formatting.ITALIC), w)) {
				if (y + 9 > bottom - 4) {
					return;
				}
				ctx.drawText(textRenderer, row, x, y, LegacyUi.PARCHMENT, true);
				y += 10;
			}
			y += 4;
		}
	}

	/** Right column: strengths and weaknesses as gem lists, then the ability tiles. Returns the hovered ability slot. */
	private int drawTraits(DrawContext ctx, int x, int mouseX, int mouseY) {
		int w = colWidth();
		int top = detailTop();
		int bottom = detailBottom();
		LegacyUi.panel(ctx, x - 6, top - 6, x + w + 6, bottom);
		boolean abilities = I18n.hasTranslation("mages_legacy.ability." + selected.id() + ".1.name");
		int listBottom = bottom - (abilities ? TILE_H + 18 : 22);

		int y = top;
		y = section(ctx, Text.translatable("mages_legacy.screen.strengths"), x, y, LegacyUi.GREEN);
		y = gems(ctx, Lines.of(selected.key() + ".perk"), x, y, w, LegacyUi.GREEN, 0xFFD6EBD0, listBottom);
		y += 4;
		y = section(ctx, Text.translatable("mages_legacy.screen.weaknesses"), x, y, LegacyUi.RED);
		gems(ctx, Lines.of(selected.key() + ".weak"), x, y, w, LegacyUi.RED, 0xFFEBD0D0, listBottom);

		if (!abilities) {
			// A race not awakened yet: a sleeping seal instead of abilities
			Text soon = Text.translatable("mages_legacy.screen.soon").formatted(Formatting.ITALIC);
			int cy = bottom - 16;
			LegacyUi.divider(ctx, x, x + w, cy - 6, LegacyUi.argb(selected.color));
			ctx.drawCenteredTextWithShadow(textRenderer, soon, x + w / 2, cy, LegacyUi.MUTED);
			return 0;
		}
		int ty = bottom - TILE_H - 6;
		LegacyUi.divider(ctx, x, x + w, ty - 8, LegacyUi.argb(selected.color));
		int tileW = (w - 8) / 3;
		int hovered = 0;
		for (int slot = 1; slot <= 3; slot++) {
			int tx = x + (slot - 1) * (tileW + 4);
			boolean hot = mouseX >= tx && mouseX < tx + tileW && mouseY >= ty && mouseY < ty + TILE_H;
			if (hot) {
				hovered = slot;
			}
			LegacyUi.panel(ctx, tx, ty, tx + tileW, ty + TILE_H, hot ? LegacyUi.argb(selected.color) : LegacyUi.RIM_DARK);
			String key = "mages_legacy.ability." + selected.id() + "." + slot;
			int cx = tx + tileW / 2;
			String icon = RaceHud.abilityIcon(selected, slot - 1);
			if (icon != null && LegacyUi.exists(LegacyUi.icon(icon))) {
				LegacyUi.icon(ctx, LegacyUi.icon(icon), cx - 8, ty + 4, 16, true);
			}
			Text name = Text.translatable(key + ".name");
			ctx.drawCenteredTextWithShadow(textRenderer, textRenderer.trimToWidth(name.getString(), tileW - 4), cx, ty + 23, LegacyUi.GOLD);
		}
		return hovered;
	}

	private int section(DrawContext ctx, Text title, int x, int y, int color) {
		ctx.drawTextWithShadow(textRenderer, title.copy().formatted(Formatting.BOLD), x, y, color);
		return y + 12;
	}

	private int gems(DrawContext ctx, List<Text> lines, int x, int y, int w, int gem, int color, int bottom) {
		for (Text line : lines) {
			List<OrderedText> rows = textRenderer.wrapLines(line, w - 10);
			if (y + 9 > bottom) {
				return y;
			}
			LegacyUi.diamond(ctx, x + 3, y + 3, 2, gem);
			for (OrderedText row : rows) {
				if (y + 9 > bottom) {
					return y;
				}
				ctx.drawText(textRenderer, row, x + 10, y, color, true);
				y += 10;
			}
			y += 1;
		}
		return y;
	}

	private static List<Text> abilityTooltip(Race race, int slot) {
		String key = "mages_legacy.ability." + race.id() + "." + slot;
		List<Text> lines = new ArrayList<>();
		lines.add(Text.translatable(key + ".name").formatted(Formatting.GOLD, Formatting.BOLD));
		if (I18n.hasTranslation(key + ".lore")) {
			TalentScreen.wrapInto(lines, Text.translatable(key + ".lore").formatted(Formatting.GRAY, Formatting.ITALIC));
		}
		TalentScreen.wrapInto(lines, Text.translatable(key + ".desc").formatted(Formatting.WHITE));
		return lines;
	}

	@Override
	public boolean shouldCloseOnEsc() {
		return false;
	}

	@Override
	public boolean shouldPause() {
		return false;
	}
}
