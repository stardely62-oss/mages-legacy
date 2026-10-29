package euf.mageslegacy.client.screen;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.network.chat.Component;

/** A button in the Mage's Legacy style ({@link LegacyUi#button}): a centred label. */
public class LegacyButton extends Button {
	private boolean primary;
	private int accent = LegacyUi.GOLD;

	public LegacyButton(int x, int y, int width, int height, Component message, OnPress onPress) {
		super(x, y, width, height, message, onPress, DEFAULT_NARRATION);
	}

	public LegacyButton primary() {
		this.primary = true;
		return this;
	}

	public LegacyButton accent(int accent) {
		this.accent = accent;
		return this;
	}

	@Override
	protected void renderWidget(GuiGraphics graphics, int mouseX, int mouseY, float delta) {
		boolean hot = active && isHoveredOrFocused();
		LegacyUi.button(graphics, getX(), getY(), width, height, active, hot, primary, accent);
		Font font = Minecraft.getInstance().font;
		Component message = getMessage();
		int color = !active ? 0xFF6A6068 : hot ? 0xFFFFFFFF : LegacyUi.TEXT;
		graphics.drawString(font, message, getX() + (width - font.width(message)) / 2, getY() + (height - 8) / 2, color);
	}
}
