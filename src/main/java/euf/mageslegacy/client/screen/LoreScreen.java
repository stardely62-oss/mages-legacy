package euf.mageslegacy.client.screen;

import euf.mageslegacy.race.Race;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.text.OrderedText;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.Identifier;
import net.minecraft.util.Util;

/**
 * The legend of the nameless mage, shown before the choice of race: the Codex illustration in a carved frame, the
 * paragraphs fading in one after another like a story being told, and the five emblems lighting up in turn below.
 * A click shows everything at once.
 */
public class LoreScreen extends Screen {
	private static final Identifier ILLUSTRATION = LegacyUi.gui("lore_mage");
	private static final float PARAGRAPH_DELAY = 0.9f;
	private static final float FADE = 0.7f;

	private long openedAt;
	private boolean revealed;
	private LegacyButton nextButton;

	public LoreScreen() {
		super(Text.translatable("mages_legacy.lore.intro.title"));
	}

	@Override
	protected void init() {
		if (openedAt == 0) {
			openedAt = Util.getMeasuringTimeMs();
		}
		nextButton = addDrawableChild(new LegacyButton(width / 2 - 70, height - 28, 140, 20, Text.translatable("mages_legacy.screen.next"),
				b -> next()).primary());
	}

	public void next() {
		client.setScreen(new RaceSelectScreen());
	}

	/** Skips the fade-in; also used by the harness before its screenshot. */
	public void revealAll() {
		revealed = true;
	}

	private float elapsed() {
		return revealed ? 1000 : (Util.getMeasuringTimeMs() - openedAt) / 1000f;
	}

	@Override
	public boolean mouseClicked(double mouseX, double mouseY, int button) {
		if (super.mouseClicked(mouseX, mouseY, button)) {
			return true;
		}
		revealAll();
		return true;
	}

	@Override
	public void render(DrawContext ctx, int mouseX, int mouseY, float delta) {
		LegacyUi.background(ctx, width, height, null);
		float t = elapsed();

		LegacyUi.heading(ctx, title.copy().formatted(Formatting.BOLD), width / 2, 10, LegacyUi.GOLD);
		int y = 26;

		// The illustration, if there is room for it
		int textWidth = Math.min(340, width - 60);
		List<Text> paragraphs = Lines.of("mages_legacy.lore.intro");
		List<List<OrderedText>> wrapped = new ArrayList<>();
		int textHeight = 0;
		for (Text paragraph : paragraphs) {
			List<OrderedText> lines = textRenderer.wrapLines(paragraph.copy().formatted(Formatting.ITALIC), textWidth);
			wrapped.add(lines);
			textHeight += lines.size() * 10 + 5;
		}
		int free = height - 26 - textHeight - 34 - 30 - 20;
		int imageH = Math.min(96, free);
		if (imageH >= 40 && LegacyUi.exists(ILLUSTRATION)) {
			int imageW = imageH * 3 / 2;
			int ix = (width - imageW) / 2;
			LegacyUi.panel(ctx, ix - 3, y - 3, ix + imageW + 3, y + imageH + 3);
			ctx.drawTexture(ILLUSTRATION, ix, y, imageW, imageH, 0, 0, 144, 96, 144, 96);
			// The picture fades in from the dark first
			float shown = Math.min(1, t / 1.2f);
			if (shown < 1) {
				ctx.fill(ix, y, ix + imageW, y + imageH, LegacyUi.alpha(0xFF000000, 1 - shown));
			}
			y += imageH + 10;
		}

		// The story
		int x = (width - textWidth) / 2;
		LegacyUi.panel(ctx, x - 12, y - 6, x + textWidth + 12, y + textHeight + 2);
		for (int i = 0; i < wrapped.size(); i++) {
			float a = Math.min(1, Math.max(0, (t - 0.6f - i * PARAGRAPH_DELAY) / FADE));
			int lift = (int) ((1 - a) * 4);
			for (OrderedText line : wrapped.get(i)) {
				if (a > 0.05f) {
					ctx.drawText(textRenderer, line, x, y + lift, LegacyUi.alpha(LegacyUi.PARCHMENT, a), true);
				}
				y += 10;
			}
			y += 5;
		}

		// Five emblems, one per people, lighting up as the story names them
		int size = 20;
		int gap = 10;
		int ex = (width - 5 * size - 4 * gap) / 2;
		int ey = Math.min(height - 54, y + 6);
		for (Race race : Race.values()) {
			float a = Math.min(1, Math.max(0, (t - 0.6f - 2 * PARAGRAPH_DELAY - race.ordinal() * 0.25f) / FADE));
			int cx = ex + size / 2;
			if (a > 0) {
				LegacyUi.glow(ctx, cx, ey + size / 2, 14, LegacyUi.argb(race.color), a);
			}
			if (LegacyUi.exists(LegacyUi.emblem(race))) {
				LegacyUi.emblem(ctx, race, ex, ey, size, a > 0.5f);
			} else {
				LegacyUi.diamond(ctx, cx, ey + size / 2, 5, LegacyUi.alpha(LegacyUi.argb(race.color), 0.3f + 0.7f * a));
			}
			ex += size + gap;
		}

		nextButton.active = t > 0.6f + (paragraphs.size() - 1) * PARAGRAPH_DELAY + FADE;
		super.render(ctx, mouseX, mouseY, delta);
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
