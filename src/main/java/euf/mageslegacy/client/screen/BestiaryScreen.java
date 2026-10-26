package euf.mageslegacy.client.screen;

import euf.mageslegacy.client.ClientRaceState;
import euf.mageslegacy.client.MagesLegacyClient;
import euf.mageslegacy.client.RaceHud;
import euf.mageslegacy.race.Race;
import euf.mageslegacy.race.Talents;
import euf.mageslegacy.shepherd.BeastKind;
import euf.mageslegacy.shepherd.Bonds;
import euf.mageslegacy.shepherd.EchoType;
import euf.mageslegacy.shepherd.Shepherd;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtElement;
import net.minecraft.nbt.NbtList;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.Language;
import net.minecraft.util.math.MathHelper;

/**
 * The Shepherd's bestiary. Left: three squads of echoes on tabs (the Call brings the active one all at once), the
 * active squad's make-up and price, then every echo with its progress: left click adds one to the squad, right click
 * takes one away. Right: the bonded beasts, the beasts listening to a Song right now, and every beast that can be
 * bonded with its own way in the tooltip.
 */
public class BestiaryScreen extends Screen {
	private static final int CARD_H = 22;
	private static final int SQUAD_H = 40;
	private static final int SPIRIT = 0xFF9FE8E0;

	private int echoScroll;
	/** The bonded beast being renamed, or -1; the field sits over its row. */
	private int renaming = -1;
	private TextFieldWidget nameField;

	public BestiaryScreen() {
		super(Text.translatable("mages_legacy.bestiary.title"));
	}

	@Override
	protected void init() {
		nameField = addDrawableChild(new TextFieldWidget(textRenderer, 0, 0, 100, 10, Text.translatable("mages_legacy.bestiary.name")));
		nameField.setMaxLength(24);
		nameField.setDrawsBackground(false);
		nameField.setEditableColor(LegacyUi.TEXT);
		nameField.visible = false;
		renaming = -1;
	}

	private void finishRename() {
		if (renaming >= 0) {
			MagesLegacyClient.renameBeast(renaming, nameField.getText());
		}
		renaming = -1;
		nameField.visible = false;
		nameField.setFocused(false);
		setFocused(null);
	}

	@Override
	public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
		if (renaming >= 0 && (keyCode == 257 || keyCode == 335)) { // Enter
			finishRename();
			return true;
		}
		if (renaming >= 0 && keyCode == 256) { // Escape cancels the rename, not the screen
			renaming = -1;
			nameField.visible = false;
			return true;
		}
		return super.keyPressed(keyCode, scanCode, modifiers);
	}

	@Override
	public void tick() {
		nameField.tick();
	}

	/** Beast rows on the right, as {x, y, w} for row {@code index}. */
	private int[] beastRow(int index) {
		return new int[] {rightX(), top() + 12 + index * 23, beastWidth()};
	}

	private NbtCompound extra() {
		return ClientRaceState.extra;
	}

	private boolean quiet() {
		return ClientRaceState.talents.contains(Talents.LEGION_CHEAP);
	}

	private boolean learned(EchoType type) {
		return extra().getCompound("kills").getInt(type.id()) >= type.killsNeeded();
	}

	private int active() {
		return Math.floorMod(extra().getInt("activePreset"), 3);
	}

	/** The echoes get the wider column (their names are long); the beasts and the ritual the rest. */
	private int colWidth() {
		return Math.min(300, (width - 40) * 58 / 100);
	}

	private int beastWidth() {
		return Math.min(260, width - 40 - colWidth());
	}

	private int leftX() {
		return (width - colWidth() - beastWidth() - 16) / 2;
	}

	private int rightX() {
		return leftX() + colWidth() + 16;
	}

	private int top() {
		return 40;
	}

	private int cardW() {
		return (colWidth() - 6) / 2;
	}

	/** Where the echo cards start and end on screen; they scroll when there are more than fit. */
	private int cardsTop() {
		return top() + 12 + SQUAD_H + 8;
	}

	private int cardsBottom() {
		return height - 14;
	}

	private int maxScroll() {
		int rows = (EchoType.ALL.size() + 1) / 2;
		return Math.max(0, rows * (CARD_H + 3) - (cardsBottom() - cardsTop()));
	}

	private int[] cardPos(int index) {
		return new int[] {leftX() + (index % 2) * (cardW() + 6), cardsTop() + (index / 2) * (CARD_H + 3) - echoScroll};
	}

	private int[] tabPos(int index) {
		int w = (colWidth() - 8) / 3;
		return new int[] {leftX() + index * (w + 4), top() + 12, w};
	}

	@Override
	public boolean mouseClicked(double mouseX, double mouseY, int button) {
		if (renaming >= 0 && nameField.isMouseOver(mouseX, mouseY)) {
			return super.mouseClicked(mouseX, mouseY, button);
		}
		finishRename();
		// A bonded beast: left click names it, right click makes it the leader
		NbtList bonds = extra().getList("bonds", NbtElement.COMPOUND_TYPE);
		for (int i = 0; i < bonds.size(); i++) {
			int[] r = beastRow(i);
			if (mouseX >= r[0] && mouseX < r[0] + r[2] && mouseY >= r[1] && mouseY < r[1] + 20) {
				if (button == 1) {
					MagesLegacyClient.toggleLeader(i);
				} else {
					renaming = i;
					nameField.setX(r[0] + 21);
					nameField.setY(r[1] + 3);
					nameField.setWidth(r[2] - 60);
					nameField.setText(bonds.getCompound(i).getString("name"));
					nameField.visible = true;
					setFocused(nameField);
					nameField.setFocused(true);
				}
				return true;
			}
		}
		for (int i = 0; i < 3; i++) {
			int[] t = tabPos(i);
			if (mouseX >= t[0] && mouseX < t[0] + t[2] && mouseY >= t[1] && mouseY < t[1] + 13) {
				MagesLegacyClient.selectSquad(i);
				return true;
			}
		}
		if (mouseY >= cardsTop() && mouseY < cardsBottom()) {
			for (int i = 0; i < EchoType.ALL.size(); i++) {
				int[] p = cardPos(i);
				if (mouseX >= p[0] && mouseX < p[0] + cardW() && mouseY >= p[1] && mouseY < p[1] + CARD_H) {
					EchoType type = EchoType.ALL.get(i);
					if (button == 1) {
						edit(type.id(), -1);
					} else if (learned(type)) {
						edit(type.id(), 1);
					}
					return true;
				}
			}
		}
		return super.mouseClicked(mouseX, mouseY, button);
	}

	@Override
	public boolean mouseScrolled(double mouseX, double mouseY, double amount) {
		if (mouseX < leftX() + colWidth()) {
			echoScroll = MathHelper.clamp(echoScroll - (int) (amount * 20), 0, maxScroll());
			return true;
		}
		return super.mouseScrolled(mouseX, mouseY, amount);
	}

	/** One more (+1) or one less (-1) of this echo in the active squad (also used by the harness). */
	public void edit(String id, int delta) {
		MagesLegacyClient.editSquad(active(), id, delta);
	}

	@Override
	public void render(DrawContext ctx, int mouseX, int mouseY, float delta) {
		int color = LegacyUi.argb(Race.SHEPHERD.color);
		LegacyUi.background(ctx, width, height, Race.SHEPHERD);
		LegacyUi.heading(ctx, title.copy().formatted(Formatting.BOLD), width / 2, 8, LegacyUi.GOLD);
		int used = extra().getInt("capUsed");
		int max = extra().getInt("capMax");
		Text cap = Text.translatable("mages_legacy.bestiary.capacity", used, max);
		ctx.drawCenteredTextWithShadow(textRenderer, cap, width / 2, 21, used >= max ? LegacyUi.RED : LegacyUi.GOLD);

		echoScroll = MathHelper.clamp(echoScroll, 0, maxScroll());
		List<Text> tooltip = drawEchoes(ctx, color, mouseX, mouseY);
		List<Text> beastTooltip = drawBeasts(ctx, color, mouseX, mouseY);
		super.render(ctx, mouseX, mouseY, delta);
		if (tooltip != null) {
			ctx.drawTooltip(textRenderer, tooltip, mouseX, mouseY);
		} else if (beastTooltip != null) {
			ctx.drawTooltip(textRenderer, beastTooltip, mouseX, mouseY);
		}
	}

	private List<Text> drawEchoes(DrawContext ctx, int color, int mouseX, int mouseY) {
		int x = leftX();
		int w = colWidth();
		LegacyUi.panel(ctx, x - 6, top() - 6, x + w + 6, height - 8);
		ctx.drawTextWithShadow(textRenderer, Text.translatable("mages_legacy.bestiary.echoes").formatted(Formatting.BOLD), x, top(), SPIRIT);
		Text hint = Text.translatable("mages_legacy.bestiary.squad_hint");
		ctx.drawTextWithShadow(textRenderer, hint, x + w - textRenderer.getWidth(hint), top(), LegacyUi.MUTED);
		List<Text> tooltip = null;

		// Squad tabs, then the active squad's make-up and price
		for (int i = 0; i < 3; i++) {
			int[] t = tabPos(i);
			boolean on = i == active();
			boolean hot = mouseX >= t[0] && mouseX < t[0] + t[2] && mouseY >= t[1] && mouseY < t[1] + 13;
			LegacyUi.button(ctx, t[0], t[1], t[2], 13, true, hot, on, SPIRIT);
			ctx.drawCenteredTextWithShadow(textRenderer, Text.translatable("mages_legacy.bestiary.squad", Shepherd.ROMAN[i]), t[0] + t[2] / 2, t[1] + 3,
					on ? 0xFFFFFFFF : LegacyUi.MUTED);
		}
		int sy = top() + 30;
		Map<String, Integer> squad = RaceHud.activeSquad();
		NbtCompound alive = extra().getCompound("alive");
		int ix = x;
		int voice = 0;
		int slots = 0;
		if (squad.isEmpty()) {
			ctx.drawTextWithShadow(textRenderer, Text.translatable("mages_legacy.bestiary.squad_empty").formatted(Formatting.ITALIC), x, sy + 4,
					LegacyUi.MUTED);
		}
		for (Map.Entry<String, Integer> e : squad.entrySet()) {
			EchoType type = EchoType.byId(e.getKey()).orElse(null);
			if (type == null) {
				continue;
			}
			voice += type.voiceCost(quiet()) * e.getValue();
			slots += type.capacity() * e.getValue();
			if (ix + 34 > x + w - 70) {
				continue;
			}
			LegacyUi.portrait(ctx, "echo", type.path(), type.icon(), ix, sy, true);
			String count = "×" + e.getValue();
			boolean all = type.units(alive.getInt(e.getKey())) >= e.getValue();
			ctx.drawTextWithShadow(textRenderer, count, ix + 16, sy + 5, all ? LegacyUi.GREEN : 0xFFFFFFFF);
			ix += 20 + textRenderer.getWidth(count);
		}
		if (!squad.isEmpty()) {
			String price = voice + "  ◆" + slots;
			ctx.drawTextWithShadow(textRenderer, price, x + w - textRenderer.getWidth(price), sy + 5, slots > extra().getInt("capMax") ? LegacyUi.RED : SPIRIT);
		}

		// Every echo; left click adds to the squad, right click takes away
		ctx.enableScissor(x - 4, cardsTop() - 2, x + w + 4, cardsBottom());
		for (int i = 0; i < EchoType.ALL.size(); i++) {
			EchoType type = EchoType.ALL.get(i);
			int[] p = cardPos(i);
			if (p[1] + CARD_H < cardsTop() || p[1] > cardsBottom()) {
				continue;
			}
			boolean known = learned(type);
			int inSquad = squad.getOrDefault(type.id(), 0);
			boolean hot = mouseY >= cardsTop() && mouseY < cardsBottom() && mouseX >= p[0] && mouseX < p[0] + cardW() && mouseY >= p[1]
					&& mouseY < p[1] + CARD_H;
			if (hot) {
				tooltip = echoTooltip(type, inSquad);
			}
			int rim = inSquad > 0 ? SPIRIT : hot && known ? color : known ? LegacyUi.RIM : LegacyUi.RIM_DARK;
			LegacyUi.panel(ctx, p[0], p[1], p[0] + cardW(), p[1] + CARD_H, rim);
			LegacyUi.portrait(ctx, "echo", type.path(), type.icon(), p[0] + 3, p[1] + 3, known);
			// A portrait dims by itself; only a spawn egg standing in for one needs the veil
			if (!known && !LegacyUi.exists(LegacyUi.gui("echo/" + type.path()))) {
				ctx.getMatrices().push();
				ctx.getMatrices().translate(0, 0, 200);
				ctx.fill(p[0] + 3, p[1] + 3, p[0] + 19, p[1] + 19, 0xA0000000);
				ctx.getMatrices().pop();
			}
			int tx = p[0] + 22;
			int tw = cardW() - 25;
			String badge = inSquad > 0 ? "×" + inSquad : "";
			int nameW = tw - (badge.isEmpty() ? 0 : textRenderer.getWidth(badge) + 3);
			ctx.drawTextWithShadow(textRenderer, textRenderer.trimToWidth(type.type().getName().getString(), nameW), tx, p[1] + 3,
					known ? LegacyUi.TEXT : LegacyUi.MUTED);
			if (!badge.isEmpty()) {
				ctx.drawTextWithShadow(textRenderer, badge, tx + tw - textRenderer.getWidth(badge), p[1] + 3, SPIRIT);
			}
			int kills = Math.min(extra().getCompound("kills").getInt(type.id()), type.killsNeeded());
			if (known) {
				String price = type.voiceCost(quiet()) + "  ◆" + type.capacity() + (type.group() > 1 ? "  (" + type.group() + ")" : "");
				ctx.drawTextWithShadow(textRenderer, price, tx, p[1] + 12, SPIRIT);
			} else {
				// Progress towards learning it: the count, and a thin bar beside it that fills with kills
				int need = type.killsNeeded();
				String count = kills + "/" + need;
				ctx.drawTextWithShadow(textRenderer, count, tx, p[1] + 12, LegacyUi.MUTED);
				int bx = tx + textRenderer.getWidth(count) + 4;
				int bw = Math.max(4, tx + tw - bx);
				ctx.fill(bx, p[1] + 14, bx + bw, p[1] + 17, LegacyUi.BLACK);
				ctx.fill(bx, p[1] + 14, bx + bw * kills / need, p[1] + 17, LegacyUi.mix(color, SPIRIT, 0.5f));
			}
		}
		ctx.disableScissor();
		if (maxScroll() > 0) {
			int track = cardsBottom() - cardsTop();
			int thumb = Math.max(12, track * track / (track + maxScroll()));
			int ty = cardsTop() + (track - thumb) * echoScroll / maxScroll();
			ctx.fill(x + w + 2, cardsTop(), x + w + 5, cardsBottom(), LegacyUi.BLACK);
			ctx.fill(x + w + 2, ty, x + w + 5, ty + thumb, SPIRIT);
		}
		return tooltip;
	}

	/** The bonded beasts, those listening to a Song, and who can be bonded; returns the tooltip under the mouse. */
	private List<Text> drawBeasts(DrawContext ctx, int color, int mouseX, int mouseY) {
		List<Text> tooltip = null;
		int x = rightX();
		int w = beastWidth();
		LegacyUi.panel(ctx, x - 6, top() - 6, x + w + 6, height - 8);
		ctx.drawTextWithShadow(textRenderer, Text.translatable("mages_legacy.bestiary.beasts").formatted(Formatting.BOLD), x, top(), color);
		// How to bond and care, behind a "?" in the corner: the column keeps its room for the beasts themselves
		int qx = x + w - 11;
		boolean helpHot = mouseX >= qx && mouseX < qx + 11 && mouseY >= top() - 2 && mouseY < top() + 9;
		LegacyUi.button(ctx, qx, top() - 2, 11, 11, true, helpHot, false, color);
		ctx.drawCenteredTextWithShadow(textRenderer, "?", qx + 6, top(), helpHot ? 0xFFFFFFFF : LegacyUi.GOLD);
		if (helpHot) {
			tooltip = new ArrayList<>();
			tooltip.add(Text.translatable("mages_legacy.bestiary.help").formatted(Formatting.GOLD, Formatting.BOLD));
			for (Text step : Lines.of("mages_legacy.bestiary.ritual", MagesLegacyClient.keyLabel(MagesLegacyClient.ABILITY_KEYS[Bonds.SONG_SLOT]))) {
				TalentScreen.wrapInto(tooltip, step.copy().formatted(Formatting.WHITE));
			}
		}
		int y = top() + 12;

		// The bottom block: every beast that can be bonded
		int perRow = Math.max(1, (w + 2) / 19);
		int iconRows = (BeastKind.ALL.size() + perRow - 1) / perRow;
		int bottomH = 8 + 12 + iconRows * 19;
		int bottomY = height - 14 - bottomH;

		NbtList bonds = extra().getList("bonds", NbtElement.COMPOUND_TYPE);
		boolean alphaCrown = ClientRaceState.talents.contains(Talents.PACK_ALPHA);
		for (int i = 0; i < bonds.size() && y + 20 < bottomY; i++) {
			NbtCompound b = bonds.getCompound(i);
			BeastKind kind = BeastKind.byId(b.getString("kind")).orElse(null);
			if (kind == null) {
				continue;
			}
			y = beastRow(i)[1];
			boolean hot = mouseX >= x && mouseX < x + w && mouseY >= y && mouseY < y + 20;
			boolean leader = b.getBoolean("leader");
			LegacyUi.panel(ctx, x, y, x + w, y + 20, leader ? LegacyUi.GOLD : hot ? color : LegacyUi.RIM_DARK);
			LegacyUi.portrait(ctx, "beast", kind.id(), kind.icon(), x + 2, y + 2, true);
			if (leader) {
				// A small crown over the leader's icon
				ctx.getMatrices().push();
				ctx.getMatrices().translate(0, 0, 250);
				ctx.drawText(textRenderer, "♛", x + 1, y - 2, LegacyUi.GOLD, true);
				ctx.getMatrices().pop();
			}
			String name = b.getString("name").isEmpty() ? Text.translatable("mages_legacy.beast." + kind.id()).getString() : b.getString("name");
			Text level = Text.translatable("mages_legacy.screen.level", b.getInt("level"));
			if (renaming != i) {
				ctx.drawTextWithShadow(textRenderer, textRenderer.trimToWidth(name, w - 26 - textRenderer.getWidth(level)), x + 21, y + 2, LegacyUi.TEXT);
			} else {
				ctx.fill(x + 19, y + 1, x + w - 36, y + 12, LegacyUi.BLACK);
			}
			ctx.drawTextWithShadow(textRenderer, level, x + w - 3 - textRenderer.getWidth(level), y + 2, LegacyUi.GOLD);
			int rest = b.getInt("restSeconds");
			boolean staying = b.getBoolean("staying");
			Text line = rest > 0 ? Text.translatable("mages_legacy.bestiary.resting", (rest + 59) / 60).formatted(Formatting.YELLOW)
					: staying ? Text.translatable("mages_legacy.bestiary.staying").formatted(Formatting.AQUA)
					: Text.translatable("mages_legacy.beast." + kind.id() + ".skill").formatted(Formatting.GRAY);
			// Care marks on the right of the second line: a steaming bowl while fed, a heart while it wants petting
			int fed = b.getInt("fedSeconds");
			String fedMark = fed > 0 ? "♨" : "";
			String petMark = b.getBoolean("petReady") && rest == 0 ? "❤" : "";
			String marks = fedMark + petMark;
			int marksW = marks.isEmpty() ? 0 : textRenderer.getWidth(marks) + 3;
			ctx.drawTextWithShadow(textRenderer, Language.getInstance().reorder(textRenderer.trimToWidth(line, w - 24 - marksW)), x + 21, y + 11, 0xFFFFFFFF);
			int mx = x + w - 3 - textRenderer.getWidth(marks);
			ctx.drawTextWithShadow(textRenderer, fedMark, mx, y + 11, LegacyUi.GREEN);
			ctx.drawTextWithShadow(textRenderer, petMark, mx + textRenderer.getWidth(fedMark), y + 11, 0xFFFF7EB6);
			if (hot) {
				tooltip = new ArrayList<>();
				tooltip.add(Text.literal(name).formatted(Formatting.GOLD, Formatting.BOLD));
				tooltip.add(Text.translatable("mages_legacy.beast." + kind.id() + ".skill").formatted(Formatting.YELLOW));
				TalentScreen.wrapInto(tooltip, Text.translatable("mages_legacy.beast." + kind.id() + ".skill_desc").formatted(Formatting.GRAY));
				tooltip.add(Text.translatable("mages_legacy.bestiary.beast_level", b.getInt("level"), b.getInt("xp"), Bonds.xpForNext(b.getInt("level")))
						.formatted(Formatting.GREEN));
				int power = b.getInt("level") / 5 + (ClientRaceState.talents.contains(Talents.PACK_SKILL) ? 1 : 0) + (leader && alphaCrown ? 2 : 0);
				tooltip.add(Text.translatable("mages_legacy.bestiary.power", power).formatted(Formatting.AQUA));
				if (leader) {
					tooltip.add(Text.translatable(alphaCrown ? "mages_legacy.bestiary.leader" : "mages_legacy.bestiary.leader_no_crown")
							.formatted(Formatting.GOLD));
				}
				// Care: fed or hungry (and what it eats), whether it would like petting, whether it waits somewhere
				tooltip.add(fed > 0 ? Text.translatable("mages_legacy.bestiary.fed", (fed + 59) / 60).formatted(Formatting.GREEN)
						: Text.translatable("mages_legacy.bestiary.hungry", Text.translatable("mages_legacy.beast." + kind.id() + ".treat"))
								.formatted(Formatting.YELLOW));
				if (b.getBoolean("petReady") && rest == 0) {
					tooltip.add(Text.translatable("mages_legacy.bestiary.wants_pet").formatted(Formatting.LIGHT_PURPLE));
				}
				if (staying) {
					tooltip.add(Text.translatable("mages_legacy.bestiary.staying").formatted(Formatting.AQUA));
				}
				tooltip.add(Text.translatable("mages_legacy.bestiary.beast_hint").formatted(Formatting.DARK_GRAY));
			}
			y += 23;
		}
		if (bonds.isEmpty()) {
			ctx.drawTextWithShadow(textRenderer, Text.translatable("mages_legacy.bestiary.no_beasts").formatted(Formatting.ITALIC), x, y, LegacyUi.MUTED);
			y += 12;
		}

		// Beasts listening to a Song right now, with how far their ritual has come
		NbtList rituals = extra().getList("rituals", NbtElement.COMPOUND_TYPE);
		if (!rituals.isEmpty() && y + 26 < bottomY) {
			y += 4;
			ctx.drawTextWithShadow(textRenderer, Text.translatable("mages_legacy.bestiary.listening").formatted(Formatting.BOLD), x, y, LegacyUi.GOLD);
			y += 11;
			for (int i = 0; i < rituals.size() && y + 18 < bottomY; i++) {
				NbtCompound r = rituals.getCompound(i);
				BeastKind kind = BeastKind.byId(r.getString("kind")).orElse(null);
				if (kind == null) {
					continue;
				}
				boolean hot = mouseX >= x && mouseX < x + w && mouseY >= y && mouseY < y + 18;
				LegacyUi.portrait(ctx, "beast", kind.id(), kind.icon(), x, y, true);
				Text name = Text.translatable("mages_legacy.beast." + kind.id());
				String progress = r.getInt("progress") + "/" + Bonds.needed(kind);
				ctx.drawTextWithShadow(textRenderer, name, x + 19, y + 4, LegacyUi.TEXT);
				int bx = x + 19 + textRenderer.getWidth(name) + 6;
				ctx.drawTextWithShadow(textRenderer, progress, bx, y + 4, LegacyUi.GREEN);
				Text left = Text.translatable("mages_legacy.bestiary.listen_left", r.getInt("minutes"));
				ctx.drawTextWithShadow(textRenderer, left, x + w - textRenderer.getWidth(left), y + 4, LegacyUi.MUTED);
				if (hot) {
					tooltip = beastKindTooltip(kind);
				}
				y += 18;
			}
		}

		y = Math.max(y + 4, bottomY);
		LegacyUi.divider(ctx, x, x + w, y, color);
		y += 8;
		ctx.drawTextWithShadow(textRenderer, Text.translatable("mages_legacy.bestiary.tameable").formatted(Formatting.BOLD), x, y + 2, color);
		y += 13;
		for (int i = 0; i < BeastKind.ALL.size(); i++) {
			BeastKind kind = BeastKind.ALL.get(i);
			int ix = x + (i % perRow) * 19;
			int iy = y + (i / perRow) * 19;
			boolean hot = mouseX >= ix && mouseX < ix + 18 && mouseY >= iy && mouseY < iy + 18;
			ctx.fill(ix, iy, ix + 18, iy + 18, LegacyUi.BLACK);
			LegacyUi.outline(ctx, ix, iy, ix + 18, iy + 18, hot ? color : LegacyUi.RIM_DARK);
			LegacyUi.portrait(ctx, "beast", kind.id(), kind.icon(), ix + 1, iy + 1, true);
			if (hot) {
				tooltip = beastKindTooltip(kind);
			}
		}
		return tooltip;
	}

	private List<Text> beastKindTooltip(BeastKind kind) {
		List<Text> lines = new ArrayList<>();
		String id = "mages_legacy.beast." + kind.id();
		lines.add(Text.translatable(id).formatted(Formatting.GOLD, Formatting.BOLD));
		lines.add(Text.translatable(id + ".skill").formatted(Formatting.YELLOW));
		TalentScreen.wrapInto(lines, Text.translatable(id + ".skill_desc").formatted(Formatting.GRAY));
		int needed = Bonds.needed(kind);
		TalentScreen.wrapInto(lines, Text.translatable("mages_legacy.bestiary.taming", Text.translatable(id + ".taming", needed).getString())
				.formatted(Formatting.WHITE));
		lines.add(Text.translatable("mages_legacy.bestiary.beast_cost", Shepherd.beastCost(kind, ClientRaceState.talents.contains(Talents.PACK_LIGHT)))
				.formatted(Formatting.AQUA));
		lines.add(Text.translatable("mages_legacy.bestiary.treat", Text.translatable(id + ".treat")).formatted(Formatting.GRAY));
		return lines;
	}

	private List<Text> echoTooltip(EchoType type, int inSquad) {
		List<Text> lines = new ArrayList<>();
		lines.add(Text.translatable("mages_legacy.echo.name", type.type().getName()).formatted(Formatting.AQUA, Formatting.BOLD));
		TalentScreen.wrapInto(lines, Text.translatable("mages_legacy.echo.trait." + type.path())
				.formatted(Formatting.YELLOW));
		if (type.group() > 1) {
			lines.add(Text.translatable("mages_legacy.bestiary.group", type.group()).formatted(Formatting.GRAY));
		}
		int kills = extra().getCompound("kills").getInt(type.id());
		int need = type.killsNeeded();
		if (kills >= need) {
			lines.add(Text.translatable("mages_legacy.bestiary.cost", type.voiceCost(quiet()), type.capacity()).formatted(Formatting.GRAY));
			lines.add(Text.translatable("mages_legacy.bestiary.in_squad", inSquad).formatted(Formatting.AQUA));
			lines.add(Text.translatable("mages_legacy.bestiary.pick").formatted(Formatting.GREEN));
		} else {
			lines.add(Text.translatable("mages_legacy.bestiary.need", need - kills).formatted(Formatting.GRAY));
		}
		return lines;
	}

	@Override
	public boolean shouldPause() {
		return false;
	}
}
