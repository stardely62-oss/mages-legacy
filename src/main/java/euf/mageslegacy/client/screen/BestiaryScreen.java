package euf.mageslegacy.client.screen;

import euf.mageslegacy.client.ClientRaceState;
import euf.mageslegacy.client.MagesLegacyClient;
import euf.mageslegacy.race.Race;
import euf.mageslegacy.race.Talents;
import euf.mageslegacy.shepherd.BeastKind;
import euf.mageslegacy.shepherd.EchoType;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtElement;
import net.minecraft.nbt.NbtList;
import net.minecraft.text.OrderedText;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.Language;

/**
 * The Shepherd's bestiary: on the left every echo with how many kills it still needs (or what the Call costs once
 * learned; click one to make it the Call's echo); on the right the bonded beasts with their level, skill and rest,
 * and the steps of the bond ritual.
 */
public class BestiaryScreen extends Screen {
	private static final int CARD_H = 26;

	public BestiaryScreen() {
		super(Text.translatable("mages_legacy.bestiary.title"));
	}

	private NbtCompound extra() {
		return ClientRaceState.extra;
	}

	private boolean memory() {
		return ClientRaceState.talents.contains(Talents.LEGION_THRESHOLDS);
	}

	private boolean learned(EchoType type) {
		return extra().getCompound("kills").getInt(type.id()) >= type.killsNeeded(memory());
	}

	private int colWidth() {
		return Math.min(250, (width - 40) / 2);
	}

	private int leftX() {
		return width / 2 - colWidth() - 8;
	}

	private int top() {
		return 44;
	}

	private int cardW() {
		return (colWidth() - 6) / 2;
	}

	private int[] cardPos(int index) {
		return new int[] {leftX() + (index % 2) * (cardW() + 6), top() + 14 + (index / 2) * (CARD_H + 4)};
	}

	@Override
	public boolean mouseClicked(double mouseX, double mouseY, int button) {
		for (int i = 0; i < EchoType.ALL.size(); i++) {
			int[] p = cardPos(i);
			if (mouseX >= p[0] && mouseX < p[0] + cardW() && mouseY >= p[1] && mouseY < p[1] + CARD_H) {
				EchoType type = EchoType.ALL.get(i);
				if (learned(type)) {
					select(type.id());
				}
				return true;
			}
		}
		return super.mouseClicked(mouseX, mouseY, button);
	}

	/** Makes this echo the Call's (also used by the harness). */
	public void select(String id) {
		MagesLegacyClient.sendSelectEcho(id);
		extra().putString("selected", id);
	}

	@Override
	public void render(DrawContext ctx, int mouseX, int mouseY, float delta) {
		int color = LegacyUi.argb(Race.SHEPHERD.color);
		LegacyUi.background(ctx, width, height, Race.SHEPHERD);
		LegacyUi.heading(ctx, title.copy().formatted(Formatting.BOLD), width / 2, 10, LegacyUi.GOLD);
		int used = extra().getInt("capUsed");
		int max = extra().getInt("capMax");
		Text cap = Text.translatable("mages_legacy.bestiary.capacity", used, max);
		ctx.drawCenteredTextWithShadow(textRenderer, cap, width / 2, 24, used >= max ? LegacyUi.RED : LegacyUi.GOLD);

		EchoType hovered = drawEchoes(ctx, color, mouseX, mouseY);
		List<Text> beastTooltip = drawBeasts(ctx, color, mouseX, mouseY);
		super.render(ctx, mouseX, mouseY, delta);
		if (hovered != null) {
			ctx.drawTooltip(textRenderer, echoTooltip(hovered), mouseX, mouseY);
		} else if (beastTooltip != null) {
			ctx.drawTooltip(textRenderer, beastTooltip, mouseX, mouseY);
		}
	}

	private EchoType drawEchoes(DrawContext ctx, int color, int mouseX, int mouseY) {
		int x = leftX();
		int w = colWidth();
		int bottom = cardPos(EchoType.ALL.size() - 1)[1] + CARD_H + 8;
		LegacyUi.panel(ctx, x - 6, top() - 6, x + w + 6, Math.max(bottom, height - 12));
		ctx.drawTextWithShadow(textRenderer, Text.translatable("mages_legacy.bestiary.echoes").formatted(Formatting.BOLD), x, top(), 0xFF9FE8E0);
		String selected = extra().getString("selected");
		EchoType hovered = null;
		for (int i = 0; i < EchoType.ALL.size(); i++) {
			EchoType type = EchoType.ALL.get(i);
			int[] p = cardPos(i);
			boolean known = learned(type);
			boolean picked = type.id().equals(selected);
			boolean hot = mouseX >= p[0] && mouseX < p[0] + cardW() && mouseY >= p[1] && mouseY < p[1] + CARD_H;
			if (hot) {
				hovered = type;
			}
			if (picked) {
				LegacyUi.glow(ctx, p[0] + 11, p[1] + CARD_H / 2, 14, 0xFF9FE8E0, 0.6f + 0.3f * (float) Math.sin(LegacyUi.time() * 3));
			}
			int rim = picked ? 0xFF9FE8E0 : hot && known ? color : known ? LegacyUi.RIM : LegacyUi.RIM_DARK;
			LegacyUi.panel(ctx, p[0], p[1], p[0] + cardW(), p[1] + CARD_H, rim);
			ctx.drawItem(new ItemStack(type.icon()), p[0] + 4, p[1] + 5);
			if (!known) {
				ctx.getMatrices().push();
				ctx.getMatrices().translate(0, 0, 200);
				ctx.fill(p[0] + 4, p[1] + 5, p[0] + 20, p[1] + 21, 0xA0000000);
				ctx.getMatrices().pop();
			}
			int tx = p[0] + 24;
			int tw = cardW() - 28;
			int kills = Math.min(extra().getCompound("kills").getInt(type.id()), type.killsNeeded(memory()));
			ctx.drawTextWithShadow(textRenderer, textRenderer.trimToWidth(type.type().getName().getString(), tw), tx, p[1] + 4,
					known ? LegacyUi.TEXT : LegacyUi.MUTED);
			if (known) {
				String cost = type.voiceCost(ClientRaceState.talents.contains(Talents.LEGION_CHEAP)) + "  ◆" + type.capacity();
				ctx.drawTextWithShadow(textRenderer, cost, tx, p[1] + 15, 0xFF9FE8E0);
			} else {
				// Progress towards learning it: the count, and a thin bar beside it that fills with kills
				int need = type.killsNeeded(memory());
				String count = kills + "/" + need;
				ctx.drawTextWithShadow(textRenderer, count, tx, p[1] + 15, LegacyUi.MUTED);
				int bx = tx + textRenderer.getWidth(count) + 4;
				int bw = Math.max(4, tx + tw - bx);
				ctx.fill(bx, p[1] + 17, bx + bw, p[1] + 20, LegacyUi.BLACK);
				ctx.fill(bx, p[1] + 17, bx + bw * kills / need, p[1] + 20, LegacyUi.mix(color, 0xFF9FE8E0, 0.5f));
			}
		}
		return hovered;
	}

	/** The bonded beasts and the ritual; returns the tooltip of the beast under the mouse, if any. */
	private List<Text> drawBeasts(DrawContext ctx, int color, int mouseX, int mouseY) {
		List<Text> tooltip = null;
		int x = width / 2 + 8;
		int w = colWidth();
		LegacyUi.panel(ctx, x - 6, top() - 6, x + w + 6, height - 12);
		ctx.drawTextWithShadow(textRenderer, Text.translatable("mages_legacy.bestiary.beasts").formatted(Formatting.BOLD), x, top(), color);
		int y = top() + 14;
		NbtList bonds = extra().getList("bonds", NbtElement.COMPOUND_TYPE);
		for (int i = 0; i < bonds.size() && y + 22 < height - 60; i++) {
			NbtCompound b = bonds.getCompound(i);
			BeastKind kind = BeastKind.byId(b.getString("kind")).orElse(null);
			if (kind == null) {
				continue;
			}
			boolean hot = mouseX >= x && mouseX < x + w && mouseY >= y && mouseY < y + 22;
			LegacyUi.panel(ctx, x, y, x + w, y + 22, hot ? color : LegacyUi.RIM_DARK);
			ctx.drawItem(new ItemStack(kind.icon()), x + 3, y + 3);
			String name = b.getString("name").isEmpty() ? Text.translatable("mages_legacy.beast." + kind.id()).getString() : b.getString("name");
			ctx.drawTextWithShadow(textRenderer, name, x + 22, y + 3, LegacyUi.TEXT);
			Text level = Text.translatable("mages_legacy.screen.level", b.getInt("level"));
			ctx.drawTextWithShadow(textRenderer, level, x + w - 4 - textRenderer.getWidth(level), y + 3, LegacyUi.GOLD);
			int rest = b.getInt("restSeconds");
			Text line = rest > 0 ? Text.translatable("mages_legacy.bestiary.resting", (rest + 59) / 60).formatted(Formatting.YELLOW)
					: Text.translatable("mages_legacy.beast." + kind.id() + ".skill").formatted(Formatting.GRAY);
			ctx.drawTextWithShadow(textRenderer, Language.getInstance().reorder(textRenderer.trimToWidth(line, w - 26)), x + 22, y + 12, 0xFFFFFFFF);
			if (hot) {
				tooltip = new ArrayList<>();
				tooltip.add(Text.literal(name).formatted(Formatting.GOLD, Formatting.BOLD));
				tooltip.add(Text.translatable("mages_legacy.beast." + kind.id() + ".skill").formatted(Formatting.YELLOW));
				TalentScreen.wrapInto(tooltip, Text.translatable("mages_legacy.beast." + kind.id() + ".skill_desc").formatted(Formatting.GRAY));
				tooltip.add(Text.translatable("mages_legacy.bestiary.beast_level", b.getInt("level"), b.getInt("xp"), 30 * b.getInt("level"))
						.formatted(Formatting.GREEN));
			}
			y += 26;
		}
		if (bonds.isEmpty()) {
			ctx.drawTextWithShadow(textRenderer, Text.translatable("mages_legacy.bestiary.no_beasts").formatted(Formatting.ITALIC), x, y, LegacyUi.MUTED);
			y += 14;
		}
		// The ritual, step by step, at the bottom of the panel
		List<Text> steps = Lines.of("mages_legacy.bestiary.ritual");
		int ritualH = 8;
		for (Text step : steps) {
			ritualH += textRenderer.wrapLines(step, w - 10).size() * 10 + 2;
		}
		y = Math.max(y + 4, height - 16 - ritualH);
		LegacyUi.divider(ctx, x, x + w, y, color);
		y += 8;
		for (Text step : steps) {
			LegacyUi.diamond(ctx, x + 3, y + 3, 2, color);
			for (OrderedText row : textRenderer.wrapLines(step, w - 10)) {
				ctx.drawText(textRenderer, row, x + 10, y, LegacyUi.PARCHMENT, true);
				y += 10;
			}
			y += 2;
		}
		return tooltip;
	}

	private List<Text> echoTooltip(EchoType type) {
		List<Text> lines = new ArrayList<>();
		lines.add(Text.translatable("mages_legacy.echo.name", type.type().getName()).formatted(Formatting.AQUA, Formatting.BOLD));
		int kills = extra().getCompound("kills").getInt(type.id());
		int need = type.killsNeeded(memory());
		if (kills >= need) {
			lines.add(Text.translatable("mages_legacy.bestiary.cost", type.voiceCost(ClientRaceState.talents.contains(Talents.LEGION_CHEAP)),
					type.capacity()).formatted(Formatting.GRAY));
			lines.add(Text.translatable(type.id().equals(extra().getString("selected")) ? "mages_legacy.bestiary.picked"
					: "mages_legacy.bestiary.pick").formatted(Formatting.GREEN));
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
