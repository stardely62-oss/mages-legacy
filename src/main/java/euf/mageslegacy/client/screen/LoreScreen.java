package euf.mageslegacy.client.screen;

import java.util.List;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.text.OrderedText;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

/** The legend of the nameless mage, shown before the choice of race. */
public class LoreScreen extends Screen {
	public LoreScreen() {
		super(Text.translatable("mages_legacy.lore.intro.title"));
	}

	@Override
	protected void init() {
		addDrawableChild(ButtonWidget.builder(Text.translatable("mages_legacy.screen.next"), b -> next())
				.dimensions(width / 2 - 60, height - 30, 120, 20).build());
	}

	public void next() {
		client.setScreen(new RaceSelectScreen());
	}

	@Override
	public void render(DrawContext ctx, int mouseX, int mouseY, float delta) {
		ctx.fillGradient(0, 0, width, height, 0xF0100C14, 0xF0060408);
		ctx.drawCenteredTextWithShadow(textRenderer, title.copy().formatted(Formatting.GOLD, Formatting.BOLD), width / 2, 16, 0xFFFFFF);
		int textWidth = Math.min(360, width - 40);
		int x = (width - textWidth) / 2;
		int y = 38;
		for (Text paragraph : Lines.of("mages_legacy.lore.intro")) {
			List<OrderedText> wrapped = textRenderer.wrapLines(paragraph.copy().formatted(Formatting.ITALIC), textWidth);
			for (OrderedText line : wrapped) {
				ctx.drawText(textRenderer, line, x, y, 0xD8CFC0, true);
				y += 10;
			}
			y += 5;
		}
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
