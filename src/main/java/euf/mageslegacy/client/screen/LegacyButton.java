package euf.mageslegacy.client.screen;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import org.jetbrains.annotations.Nullable;

/** A button in the Mage's Legacy style ({@link LegacyUi#button}): an optional icon and a label, centred. */
public class LegacyButton extends ButtonWidget {
	@Nullable
	private Identifier icon;
	private boolean primary;
	private int accent = LegacyUi.GOLD;

	public LegacyButton(int x, int y, int width, int height, Text message, PressAction onPress) {
		super(x, y, width, height, message, onPress, DEFAULT_NARRATION_SUPPLIER);
	}

	public LegacyButton primary() {
		this.primary = true;
		return this;
	}

	public LegacyButton icon(@Nullable Identifier icon) {
		this.icon = icon;
		return this;
	}

	public LegacyButton accent(int accent) {
		this.accent = accent;
		return this;
	}

	@Override
	protected void renderButton(DrawContext context, int mouseX, int mouseY, float delta) {
		boolean hot = active && isSelected();
		LegacyUi.button(context, getX(), getY(), width, height, active, hot, primary, accent);
		TextRenderer tr = MinecraftClient.getInstance().textRenderer;
		Text message = getMessage();
		int textW = tr.getWidth(message);
		int iconW = icon == null || !LegacyUi.exists(icon) ? 0 : 16;
		int gap = iconW > 0 && textW > 0 ? 4 : 0;
		int x = getX() + (width - iconW - gap - textW) / 2;
		if (iconW > 0) {
			LegacyUi.icon(context, icon, x, getY() + (height - 16) / 2, 16, active);
			x += iconW + gap;
		}
		int color = !active ? 0xFF6A6068 : hot ? 0xFFFFFFFF : LegacyUi.TEXT;
		context.drawTextWithShadow(tr, message, x, getY() + (height - 8) / 2, color);
	}
}
