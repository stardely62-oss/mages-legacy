package euf.mageslegacy.client.screen;

import euf.mageslegacy.client.ClientRace;
import euf.mageslegacy.net.PassageTargetPayload;
import euf.mageslegacy.race.Race;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.PlayerFaceRenderer;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.network.chat.Component;
import net.neoforged.neoforge.network.PacketDistributor;

/**
 * Through Worlds: everyone in the world now, as ripples the Voidborn can feel, one stone row each with their face.
 * A click opens the passage to them (the server checks again and spends the charge); Esc opens nothing.
 */
public class PassageScreen extends Screen {
	private static final int ROW = 24;
	private static final int WIDTH = 220;
	private static final int VISIBLE = 7;
	private int scroll;

	public PassageScreen() {
		super(Component.translatable("mages_legacy.passage.title"));
	}

	private List<PlayerInfo> others() {
		List<PlayerInfo> list = new ArrayList<>();
		var connection = Minecraft.getInstance().getConnection();
		var self = Minecraft.getInstance().player;
		if (connection == null || self == null) {
			return list;
		}
		for (PlayerInfo info : connection.getOnlinePlayers()) {
			if (!info.getProfile().getId().equals(self.getUUID())) {
				list.add(info);
			}
		}
		list.sort(Comparator.comparing(info -> info.getProfile().getName(), String.CASE_INSENSITIVE_ORDER));
		return list;
	}

	private int top() {
		return height / 2 - (VISIBLE * ROW) / 2;
	}

	private int left() {
		return width / 2 - WIDTH / 2;
	}

	@Override
	public void renderBackground(GuiGraphics graphics, int mouseX, int mouseY, float delta) {
		LegacyUi.background(graphics, width, height);
	}

	@Override
	public void render(GuiGraphics graphics, int mouseX, int mouseY, float delta) {
		super.render(graphics, mouseX, mouseY, delta);
		int violet = LegacyUi.argb(Race.VOIDBORN.color);
		int x0 = left();
		int y0 = top();
		List<PlayerInfo> list = others();
		LegacyUi.heading(graphics, title, width / 2, y0 - 34, violet);
		graphics.drawCenteredString(font, Component.translatable("mages_legacy.passage.hint"), width / 2, y0 - 20, LegacyUi.MUTED);
		LegacyUi.panel(graphics, x0 - 6, y0 - 6, x0 + WIDTH + 6, y0 + VISIBLE * ROW + 4);
		if (list.isEmpty()) {
			graphics.drawCenteredString(font, Component.translatable("mages_legacy.refuse.no_one"), width / 2, y0 + VISIBLE * ROW / 2 - 4, LegacyUi.MUTED);
		}
		scroll = Math.max(0, Math.min(scroll, Math.max(0, list.size() - VISIBLE)));
		for (int i = 0; i < VISIBLE && scroll + i < list.size(); i++) {
			PlayerInfo info = list.get(scroll + i);
			int y = y0 + i * ROW;
			boolean hot = mouseX >= x0 && mouseX < x0 + WIDTH && mouseY >= y && mouseY < y + ROW - 2;
			LegacyUi.button(graphics, x0, y, WIDTH, ROW - 2, true, hot, false, violet);
			PlayerFaceRenderer.draw(graphics, info.getSkin(), x0 + 4, y + 3, 16);
			graphics.drawString(font, info.getProfile().getName(), x0 + 26, y + 7, hot ? 0xFFFFFFFF : LegacyUi.TEXT);
			// A ripple beside each name: how the Voidborn feels them
			float t = LegacyUi.time() * 2 + i;
			for (int k = 0; k < 3; k++) {
				float wave = (float) Math.sin(t - k * 0.8f) * 0.5f + 0.5f;
				LegacyUi.diamond(graphics, x0 + WIDTH - 12 - k * 7, y + ROW / 2 - 1, 2, LegacyUi.alpha(violet, 0.25f + 0.75f * wave));
			}
		}
		var state = ClientRace.state();
		if (state != null && state.chargesMax() > 0) {
			graphics.drawCenteredString(font, Component.translatable("mages_legacy.passage.charges", state.charges(), state.chargesMax()), width / 2,
					y0 + VISIBLE * ROW + 12, LegacyUi.GOLD);
		}
	}

	@Override
	public boolean mouseClicked(double mouseX, double mouseY, int button) {
		List<PlayerInfo> list = others();
		int x0 = left();
		int y0 = top();
		if (button == 0 && mouseX >= x0 && mouseX < x0 + WIDTH && mouseY >= y0) {
			int i = (int) ((mouseY - y0) / ROW);
			if (i < VISIBLE && scroll + i < list.size()) {
				choose(list.get(scroll + i));
				return true;
			}
		}
		return super.mouseClicked(mouseX, mouseY, button);
	}

	/** Opens the passage to this player (the harness calls it too). */
	public void choose(PlayerInfo info) {
		PacketDistributor.sendToServer(new PassageTargetPayload(info.getProfile().getId()));
		onClose();
	}

	@Override
	public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
		scroll -= (int) Math.signum(scrollY);
		return true;
	}

	@Override
	public boolean isPauseScreen() {
		return false;
	}
}
