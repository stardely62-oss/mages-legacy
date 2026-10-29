package euf.mageslegacy.client.screen;

import euf.mageslegacy.net.NetworkActionPayload;
import euf.mageslegacy.race.Race;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.neoforged.neoforge.network.PacketDistributor;

/**
 * The Mycelium Network as one sees it. At a node: where it leads (a click sets out), and for its grower its name,
 * whether everyone may use it, and threads to grow to their other nodes. Away from nodes (a Mycelid's H): the colony,
 * its nutrients and nodes, and growing a node where one stands.
 */
public class MyceliumScreen extends Screen {
	private static final int ROW = 20;
	private final CompoundTag view;
	private EditBox nameBox;
	private int scrollTo;
	private int scrollOwn;

	public MyceliumScreen(CompoundTag view) {
		super(Component.translatable(view.contains("here") ? "mages_legacy.network.title_node" : "mages_legacy.network.title_colony"));
		this.view = view;
	}

	private boolean atNode() {
		return view.contains("here");
	}

	private boolean owner() {
		return view.getBoolean("owner");
	}

	private int green() {
		return LegacyUi.argb(Race.MYCELID.color);
	}

	private int panelW() {
		return Math.min(220, (width - 40) / 2);
	}

	@Override
	protected void init() {
		int cx = width / 2;
		if (atNode() && owner()) {
			nameBox = new EditBox(font, cx + 10, 46, panelW() - 64, 16, Component.empty());
			nameBox.setMaxLength(24);
			nameBox.setValue(view.getString("name"));
			addRenderableWidget(nameBox);
			addRenderableWidget(new LegacyButton(cx + 14 + panelW() - 64, 45, 50, 18, Component.translatable("mages_legacy.network.save"),
					b -> send("rename", view.getString("here"), nameBox.getValue())).accent(green()));
			addRenderableWidget(new LegacyButton(cx + 10, 68, panelW(), 18, Component.translatable(view.getBoolean("open")
					? "mages_legacy.network.public" : "mages_legacy.network.private"), b -> send("open", view.getString("here"), "")).accent(green()));
		}
		if (!atNode() && view.getBoolean("mycelid")) {
			Component label = Component.translatable("mages_legacy.network.grow", view.getInt("nodeCost"));
			LegacyButton grow = new LegacyButton(cx - 110, height - 40, 220, 20, label, b -> send("grow", "", ""));
			grow.primary().accent(green());
			grow.active = view.getInt("nodes") < view.getInt("max") && view.getInt("nutrients") >= view.getInt("nodeCost");
			addRenderableWidget(grow);
		}
	}

	private void send(String action, String a, String b) {
		PacketDistributor.sendToServer(new NetworkActionPayload(action, a, b));
		if ("travel".equals(action)) {
			onClose();
		}
	}

	@Override
	public void renderBackground(GuiGraphics graphics, int mouseX, int mouseY, float delta) {
		LegacyUi.background(graphics, width, height);
	}

	@Override
	public void render(GuiGraphics graphics, int mouseX, int mouseY, float delta) {
		super.render(graphics, mouseX, mouseY, delta);
		int cx = width / 2;
		int green = green();
		Component title = atNode() ? Component.literal(view.getString("name")) : Component.translatable("mages_legacy.network.title_colony");
		LegacyUi.heading(graphics, title, cx, 12, green);
		Component sub = atNode() ? Component.translatable("mages_legacy.network.grower", view.getString("grower"))
				: Component.translatable("mages_legacy.network.colony_info", view.getInt("nodes"), view.getInt("max"));
		graphics.drawCenteredString(font, sub, cx, 26, LegacyUi.MUTED);
		if (view.getBoolean("mycelid")) {
			graphics.drawString(font, Component.translatable("mages_legacy.network.nutrients", view.getInt("nutrients")), 10, 10, LegacyUi.GREEN);
			if (view.contains("body")) {
				Component body = view.getInt("bodySecs") > 0
						? Component.translatable("mages_legacy.network.body_growing", view.getString("body"), view.getInt("bodySecs"))
						: Component.translatable("mages_legacy.network.body_ready", view.getString("body"));
				graphics.drawString(font, body, 10, 22, LegacyUi.GOLD);
			}
		}
		if (atNode()) {
			drawDestinations(graphics, mouseX, mouseY);
			if (owner()) {
				drawThreads(graphics, mouseX, mouseY, 94);
			}
		} else {
			drawThreads(graphics, mouseX, mouseY, 46);
		}
	}

	/** Left: where the network leads from here; a click sets out. */
	private void drawDestinations(GuiGraphics graphics, int mouseX, int mouseY) {
		int w = owner() ? panelW() : 260;
		int x0 = owner() ? width / 2 - 10 - w : width / 2 - w / 2;
		int y0 = 46;
		List<CompoundTag> to = list("to");
		graphics.drawString(font, Component.translatable("mages_legacy.network.destinations"), x0, y0 - 2, LegacyUi.GOLD);
		int rows = Math.max(1, (height - y0 - 30) / ROW);
		LegacyUi.panel(graphics, x0 - 4, y0 + 10, x0 + w + 4, y0 + 14 + rows * ROW);
		if (to.isEmpty()) {
			graphics.drawCenteredString(font, Component.translatable("mages_legacy.network.no_routes"), x0 + w / 2, y0 + 22, LegacyUi.MUTED);
		}
		scrollTo = Math.max(0, Math.min(scrollTo, Math.max(0, to.size() - rows)));
		for (int i = 0; i < rows && scrollTo + i < to.size(); i++) {
			CompoundTag d = to.get(scrollTo + i);
			int y = y0 + 13 + i * ROW;
			boolean hot = mouseX >= x0 && mouseX < x0 + w && mouseY >= y && mouseY < y + ROW - 2;
			LegacyUi.button(graphics, x0, y, w, ROW - 2, true, hot, false, green());
			graphics.drawString(font, font.plainSubstrByWidth(d.getString("name"), w - 70), x0 + 6, y + 5, hot ? 0xFFFFFFFF : LegacyUi.TEXT);
			String info = d.getInt("dist") + " m";
			graphics.drawString(font, info, x0 + w - 6 - font.width(info), y + 5, LegacyUi.MUTED);
		}
	}

	/** Right (or the whole screen in the colony view): the grower's nodes, and at an own node the threads to them. */
	private void drawThreads(GuiGraphics graphics, int mouseX, int mouseY, int y0) {
		int w = atNode() ? panelW() : 300;
		int x0 = atNode() ? width / 2 + 10 : width / 2 - w / 2;
		List<CompoundTag> own = list("own");
		graphics.drawString(font, Component.translatable(atNode() ? "mages_legacy.network.threads" : "mages_legacy.network.your_nodes"), x0, y0 - 2,
				LegacyUi.GOLD);
		int bottom = atNode() ? height - 10 : height - 48;
		int rows = Math.max(1, (bottom - y0 - 14) / ROW);
		LegacyUi.panel(graphics, x0 - 4, y0 + 10, x0 + w + 4, y0 + 14 + rows * ROW);
		if (own.isEmpty()) {
			graphics.drawCenteredString(font, Component.translatable("mages_legacy.network.no_nodes"), x0 + w / 2, y0 + 22, LegacyUi.MUTED);
		}
		scrollOwn = Math.max(0, Math.min(scrollOwn, Math.max(0, own.size() - rows)));
		for (int i = 0; i < rows && scrollOwn + i < own.size(); i++) {
			CompoundTag d = own.get(scrollOwn + i);
			int y = y0 + 13 + i * ROW;
			String state = d.getString("state");
			boolean linkable = atNode() && "none".equals(state);
			boolean hot = linkable && mouseX >= x0 && mouseX < x0 + w && mouseY >= y && mouseY < y + ROW - 2;
			LegacyUi.button(graphics, x0, y, w, ROW - 2, linkable, hot, false, green());
			String name = (d.getBoolean("mother") ? "❀ " : "") + d.getString("name");
			graphics.drawString(font, font.plainSubstrByWidth(name, w / 2), x0 + 6, y + 5, LegacyUi.TEXT);
			Component right;
			if (!atNode()) {
				right = Component.literal(d.getString("where"));
			} else if (state.isEmpty()) {
				right = Component.translatable("mages_legacy.network.here");
			} else {
				right = Component.translatable("mages_legacy.network.state." + state, d.getInt("cost"), d.getInt("secs"), d.getInt("dist"));
			}
			int color = "linked".equals(state) ? LegacyUi.GREEN : linkable ? LegacyUi.GOLD : LegacyUi.MUTED;
			String text = font.plainSubstrByWidth(right.getString(), w / 2 - 8);
			graphics.drawString(font, text, x0 + w - 6 - font.width(text), y + 5, color);
		}
	}

	private List<CompoundTag> list(String key) {
		List<CompoundTag> out = new ArrayList<>();
		ListTag tags = view.getList(key, Tag.TAG_COMPOUND);
		for (Tag t : tags) {
			out.add((CompoundTag) t);
		}
		return out;
	}

	@Override
	public boolean mouseClicked(double mouseX, double mouseY, int button) {
		if (super.mouseClicked(mouseX, mouseY, button)) {
			return true;
		}
		if (button != 0 || !atNode()) {
			return false;
		}
		// A destination: set out
		int w = owner() ? panelW() : 260;
		int x0 = owner() ? width / 2 - 10 - w : width / 2 - w / 2;
		List<CompoundTag> to = list("to");
		int i = (int) Math.floor((mouseY - 59) / ROW);
		if (mouseX >= x0 && mouseX < x0 + w && mouseY >= 59 && i >= 0 && scrollTo + i < to.size()) {
			send("travel", view.getString("here"), to.get(scrollTo + i).getString("key"));
			return true;
		}
		// A thread to grow
		if (owner()) {
			int ox = width / 2 + 10;
			List<CompoundTag> own = list("own");
			int j = (int) Math.floor((mouseY - 107) / ROW);
			if (mouseX >= ox && mouseX < ox + panelW() && mouseY >= 107 && j >= 0 && scrollOwn + j < own.size()
					&& "none".equals(own.get(scrollOwn + j).getString("state"))) {
				send("link", view.getString("here"), own.get(scrollOwn + j).getString("key"));
				return true;
			}
		}
		return false;
	}

	@Override
	public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
		if (mouseX < width / 2.0) {
			scrollTo -= (int) Math.signum(scrollY);
		} else {
			scrollOwn -= (int) Math.signum(scrollY);
		}
		return true;
	}

	@Override
	public boolean isPauseScreen() {
		return false;
	}

	/** The harness's way to set out to the first destination. */
	public void travelFirst() {
		List<CompoundTag> to = list("to");
		if (!to.isEmpty()) {
			send("travel", view.getString("here"), to.get(0).getString("key"));
		}
	}
}
