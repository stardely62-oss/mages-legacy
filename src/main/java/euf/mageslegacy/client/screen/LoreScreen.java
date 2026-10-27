package euf.mageslegacy.client.screen;

import euf.mageslegacy.race.Race;
import java.util.List;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.text.OrderedText;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.Identifier;
import net.minecraft.util.Util;

/**
 * The legend of the nameless mage, told as a short book before the choice of race: a page at a time, each with its
 * picture and a line or two that write themselves out. The third page lights the eight emblems one by one, each with
 * what the mage gave that people. A click finishes the writing, the next click turns the page.
 */
public class LoreScreen extends Screen {
	/** Pictures per page; a page whose picture isn't there yet shows the mage's. */
	private static final String[] PICTURES = {"lore_people", "lore_mage", null, "lore_sleep", "lore_wake"};
	private static final Identifier FALLBACK = LegacyUi.gui("lore_mage");
	private static final int PAGES = PICTURES.length;
	private static final int DIVISION = 2;
	/** Letters written per second, and how long the page takes to fade in. */
	private static final float LETTERS = 45;
	private static final float FADE = 0.5f;
	/** On the division page, one emblem every this many seconds. */
	private static final float EMBLEM_EVERY = 0.45f;

	private int page;
	private long pageAt;
	private boolean revealed;
	private LegacyButton backButton;
	private LegacyButton nextButton;

	public LoreScreen() {
		super(Text.translatable("mages_legacy.lore.intro.title"));
	}

	@Override
	protected void init() {
		if (pageAt == 0) {
			pageAt = Util.getMeasuringTimeMs();
		}
		backButton = addDrawableChild(new LegacyButton(width / 2 - 150, height - 28, 90, 20, Text.translatable("mages_legacy.screen.back"), b -> turn(-1)));
		nextButton = addDrawableChild(new LegacyButton(width / 2 + 60, height - 28, 90, 20, Text.translatable("mages_legacy.screen.next"), b -> advance())
				.primary());
	}

	/** Straight to the choice of race (the harness, and the last page's button). */
	public void next() {
		client.setScreen(new RaceSelectScreen());
	}

	/** Finishes writing the page at once; also used by the harness before its screenshot. */
	public void revealAll() {
		revealed = true;
	}

	/** Opens a page already written out (the harness photographs them). */
	public void showPage(int index) {
		turn(index - page);
		revealAll();
	}

	private void advance() {
		if (!done()) {
			revealAll();
		} else if (page == PAGES - 1) {
			next();
		} else {
			turn(1);
		}
	}

	private void turn(int by) {
		page = Math.max(0, Math.min(PAGES - 1, page + by));
		pageAt = Util.getMeasuringTimeMs();
		revealed = false;
	}

	private float elapsed() {
		return revealed ? 1000 : (Util.getMeasuringTimeMs() - pageAt) / 1000f;
	}

	private Text text() {
		return Text.translatable("mages_legacy.lore.page." + (page + 1));
	}

	/** Whether the page has finished writing itself out. */
	private boolean done() {
		float t = elapsed();
		if (page == DIVISION) {
			return t >= FADE + Race.values().length * EMBLEM_EVERY;
		}
		return t >= FADE + text().getString().length() / LETTERS;
	}

	@Override
	public boolean mouseClicked(double mouseX, double mouseY, int button) {
		if (super.mouseClicked(mouseX, mouseY, button)) {
			return true;
		}
		advance();
		return true;
	}

	@Override
	public void render(DrawContext ctx, int mouseX, int mouseY, float delta) {
		LegacyUi.background(ctx, width, height, null);
		float t = elapsed();
		float fade = Math.min(1, t / FADE);
		LegacyUi.heading(ctx, title.copy().formatted(Formatting.BOLD), width / 2, 10, LegacyUi.GOLD);
		if (page == DIVISION) {
			renderDivision(ctx, t, fade);
		} else {
			renderPage(ctx, t, fade);
		}
		// Where we are in the book
		int dots = PAGES * 12;
		for (int i = 0; i < PAGES; i++) {
			LegacyUi.diamond(ctx, width / 2 - dots / 2 + i * 12 + 6, height - 18, i == page ? 4 : 3, i == page ? LegacyUi.GOLD : LegacyUi.RIM);
		}
		backButton.visible = page > 0;
		nextButton.setMessage(Text.translatable(page == PAGES - 1 && done() ? "mages_legacy.lore.choose" : "mages_legacy.screen.next"));
		super.render(ctx, mouseX, mouseY, delta);
	}

	/** A picture as large as the screen allows above, the page's words writing themselves out below. */
	private void renderPage(DrawContext ctx, float t, float fade) {
		Text text = text();
		int textWidth = Math.min(320, width - 60);
		List<OrderedText> lines = textRenderer.wrapLines(text.copy().formatted(Formatting.ITALIC), textWidth);
		int textHeight = lines.size() * 11;
		int top = 28;
		int room = height - top - textHeight - 70;
		int imageH = Math.max(0, Math.min(room, Math.min(192, (width - 80) * 2 / 3)));
		int y = top;
		if (imageH >= 48) {
			int imageW = imageH * 3 / 2;
			int ix = (width - imageW) / 2;
			Identifier picture = PICTURES[page] == null ? FALLBACK : LegacyUi.gui(PICTURES[page]);
			if (!LegacyUi.exists(picture)) {
				picture = FALLBACK;
			}
			LegacyUi.panel(ctx, ix - 4, y - 4, ix + imageW + 4, y + imageH + 4);
			if (LegacyUi.exists(picture)) {
				ctx.drawTexture(picture, ix, y, imageW, imageH, 0, 0, 144, 96, 144, 96);
			}
			// The picture comes up out of the dark
			if (fade < 1) {
				ctx.fill(ix, y, ix + imageW, y + imageH, LegacyUi.alpha(0xFF000000, 1 - fade));
			}
			y += imageH + 14;
		}
		// The words, letter by letter, the newest letters still fading in
		int shown = (int) Math.max(0, (t - FADE) * LETTERS);
		int x = (width - textWidth) / 2;
		int seen = 0;
		for (OrderedText line : lines) {
			StringBuilder sb = new StringBuilder();
			line.accept((index, style, codePoint) -> {
				sb.appendCodePoint(codePoint);
				return true;
			});
			String full = sb.toString();
			int take = Math.max(0, Math.min(full.length(), shown - seen));
			if (take > 0) {
				int lineWidth = textRenderer.getWidth(full);
				Text part = Text.literal(full.substring(0, take)).formatted(Formatting.ITALIC);
				ctx.drawText(textRenderer, part, (width - lineWidth) / 2, y, LegacyUi.PARCHMENT, true);
			}
			seen += full.length();
			y += 11;
		}
	}

	/** The division: the eight peoples' emblems lighting up in turn, each with what the mage gave it. */
	private void renderDivision(DrawContext ctx, float t, float fade) {
		Text head = text();
		ctx.drawCenteredTextWithShadow(textRenderer, head.copy().formatted(Formatting.ITALIC), width / 2, 30, LegacyUi.alpha(LegacyUi.PARCHMENT, fade));
		Race[] races = Race.values();
		int cols = 4;
		int cellW = Math.min(150, (width - 40) / cols);
		int cellH = Math.min(96, (height - 120) / 2);
		int x0 = (width - cols * cellW) / 2;
		int y0 = 48 + Math.max(0, (height - 120 - 2 * cellH) / 2);
		int size = Math.min(48, cellH - 34);
		for (Race race : races) {
			int i = race.ordinal();
			float a = Math.min(1, Math.max(0, (t - FADE - i * EMBLEM_EVERY) / 0.5f));
			int cx = x0 + (i % cols) * cellW + cellW / 2;
			int cy = y0 + (i / cols) * cellH;
			int color = LegacyUi.argb(race.color);
			if (a > 0) {
				LegacyUi.glow(ctx, cx, cy + size / 2, size, color, a * 0.8f);
			}
			if (LegacyUi.exists(LegacyUi.emblem(race))) {
				LegacyUi.emblem(ctx, race, cx - size / 2, cy, size, a > 0.5f);
				if (a < 1) {
					ctx.fill(cx - size / 2, cy, cx + size / 2, cy + size, LegacyUi.alpha(0xFF000000, 0.75f * (1 - a)));
				}
			} else {
				LegacyUi.diamond(ctx, cx, cy + size / 2, size / 4, LegacyUi.alpha(color, 0.3f + 0.7f * a));
			}
			if (a > 0) {
				ctx.drawCenteredTextWithShadow(textRenderer, Text.translatable("mages_legacy.race." + race.id()), cx, cy + size + 4, LegacyUi.alpha(color, a));
				List<OrderedText> gift = textRenderer.wrapLines(Text.translatable("mages_legacy.race." + race.id() + ".gift").formatted(Formatting.ITALIC), cellW - 8);
				int gy = cy + size + 15;
				for (OrderedText line : gift) {
					ctx.drawCenteredTextWithShadow(textRenderer, line, cx, gy, LegacyUi.alpha(LegacyUi.PARCHMENT, a));
					gy += 10;
				}
			}
		}
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
