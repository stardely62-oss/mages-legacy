package euf.mageslegacy.client.screen;

import euf.mageslegacy.client.MagesLegacyClient;
import euf.mageslegacy.race.Race;
import java.util.List;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.resource.language.I18n;
import net.minecraft.text.MutableText;
import net.minecraft.text.OrderedText;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

/** Five race cards on top; below, the chosen one's legend, strengths, weaknesses and abilities. */
public class RaceSelectScreen extends Screen {
	private static final int CARD_H = 34;
	private Race selected = Race.MYCELID;
	private ButtonWidget chooseButton;

	public RaceSelectScreen() {
		super(Text.translatable("mages_legacy.screen.choose_title"));
	}

	@Override
	protected void init() {
		chooseButton = addDrawableChild(ButtonWidget.builder(Text.empty(), b -> choose())
				.dimensions(width / 2 + 4, height - 26, 150, 20).build());
		addDrawableChild(ButtonWidget.builder(Text.translatable("mages_legacy.screen.back_to_legend"), b -> client.setScreen(new LoreScreen()))
				.dimensions(width / 2 - 154, height - 26, 150, 20).build());
		updateButton();
	}

	private void updateButton() {
		chooseButton.active = selected.playable;
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
		return Math.min(118, (width - 24 - 4 * 6) / 5);
	}

	private int cardX(int index) {
		int total = 5 * cardWidth() + 4 * 6;
		return (width - total) / 2 + index * (cardWidth() + 6);
	}

	@Override
	public boolean mouseClicked(double mouseX, double mouseY, int button) {
		for (Race race : Race.values()) {
			int x = cardX(race.ordinal());
			if (mouseX >= x && mouseX < x + cardWidth() && mouseY >= 22 && mouseY < 22 + CARD_H) {
				select(race);
				return true;
			}
		}
		return super.mouseClicked(mouseX, mouseY, button);
	}

	@Override
	public void render(DrawContext ctx, int mouseX, int mouseY, float delta) {
		ctx.fillGradient(0, 0, width, height, 0xF0100C14, 0xF0060408);
		ctx.drawCenteredTextWithShadow(textRenderer, title.copy().formatted(Formatting.GOLD), width / 2, 8, 0xFFFFFF);

		for (Race race : Race.values()) {
			int x = cardX(race.ordinal());
			int w = cardWidth();
			boolean isSelected = race == selected;
			ctx.fill(x, 22, x + w, 22 + CARD_H, isSelected ? 0xE0302838 : 0xC01A1620);
			ctx.fill(x, 22, x + w, 24, 0xFF000000 | race.color);
			if (isSelected) {
				ctx.drawBorder(x, 22, w, CARD_H, 0xFF000000 | race.color);
			}
			ctx.drawCenteredTextWithShadow(textRenderer, Text.translatable(race.key()), x + w / 2, 28,
					race.playable ? race.color : 0x8A8490);
			MutableText tagline = race.playable ? Text.translatable(race.key() + ".tagline")
					: Text.translatable("mages_legacy.screen.soon_short");
			List<OrderedText> tag = textRenderer.wrapLines(tagline.formatted(Formatting.GRAY), w - 6);
			for (int i = 0; i < Math.min(2, tag.size()); i++) {
				ctx.drawCenteredTextWithShadow(textRenderer, tag.get(i), x + w / 2, 39 + i * 9, 0xA0A0A0);
			}
		}

		// Details of the selected race: legend on the left, strengths/weaknesses/abilities on the right
		int top = 22 + CARD_H + 10;
		int bottom = height - 32;
		int colW = Math.min(300, (width - 36) / 2);
		int leftX = width / 2 - colW - 6;
		int rightX = width / 2 + 6;
		ctx.fill(leftX - 4, top - 4, rightX + colW + 4, bottom, 0x80000000);

		int y = top;
		y = heading(ctx, Text.translatable("mages_legacy.screen.legend"), leftX, y, selected.color);
		for (Text line : Lines.of(selected.key() + ".lore")) {
			y = wrapped(ctx, line.copy().formatted(Formatting.ITALIC), leftX, y, colW, 0xD8CFC0, bottom);
			y += 3;
		}

		y = top;
		y = heading(ctx, Text.translatable("mages_legacy.screen.strengths"), rightX, y, 0x8FD18A);
		for (Text line : Lines.of(selected.key() + ".perk")) {
			y = wrapped(ctx, Text.literal("+ ").append(line), rightX, y, colW, 0xC8E6C3, bottom);
		}
		y += 4;
		y = heading(ctx, Text.translatable("mages_legacy.screen.weaknesses"), rightX, y, 0xE07A7A);
		for (Text line : Lines.of(selected.key() + ".weak")) {
			y = wrapped(ctx, Text.literal("- ").append(line), rightX, y, colW, 0xE6C3C3, bottom);
		}
		y += 4;
		y = heading(ctx, Text.translatable("mages_legacy.screen.abilities"), rightX, y, 0xE0C080);
		for (int slot = 1; slot <= 3; slot++) {
			String key = "mages_legacy.ability." + selected.id() + "." + slot;
			if (I18n.hasTranslation(key + ".name")) {
				y = wrapped(ctx, Text.translatable(key + ".name").formatted(Formatting.GOLD).append(Text.literal(": "))
						.append(Text.translatable(key + ".desc").formatted(Formatting.WHITE)), rightX, y, colW, 0xFFFFFF, bottom);
			}
		}
		super.render(ctx, mouseX, mouseY, delta);
	}

	private int heading(DrawContext ctx, Text text, int x, int y, int color) {
		ctx.drawText(textRenderer, text.copy().formatted(Formatting.BOLD), x, y, color, true);
		return y + 12;
	}

	private int wrapped(DrawContext ctx, Text text, int x, int y, int width, int color, int bottom) {
		for (OrderedText line : textRenderer.wrapLines(text, width)) {
			if (y + 9 > bottom) {
				return y;
			}
			ctx.drawText(textRenderer, line, x, y, color, true);
			y += 10;
		}
		return y;
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
