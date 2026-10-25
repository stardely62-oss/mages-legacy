package euf.mageslegacy.client.screen;

import euf.mageslegacy.client.ClientRaceState;
import euf.mageslegacy.client.MagesLegacyClient;
import euf.mageslegacy.client.RaceHud;
import euf.mageslegacy.race.Race;
import euf.mageslegacy.race.Talent;
import euf.mageslegacy.race.Talents;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.item.ItemStack;
import net.minecraft.text.OrderedText;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

/** The race's talent tree: three branches in columns, tiers in rows; the abilities along the bottom. */
public class TalentScreen extends Screen {
	private static final int NODE = 22;
	private static final int ROW_GAP = 40;
	private static final String[] ROMAN = {"I", "II", "III", "IV"};

	public enum NodeState { OWNED, AVAILABLE, NO_POINTS, NEEDS_TALENT, TIER_CLOSED }

	private final Map<String, int[]> positions = new HashMap<>();

	public TalentScreen() {
		super(Text.translatable("mages_legacy.screen.talents"));
	}

	private Race race() {
		return ClientRaceState.race;
	}

	private int columnWidth() {
		return Math.min(190, (width - 60) / 3);
	}

	private int columnX(int branch) {
		int w = columnWidth();
		return (width - 3 * w - 2 * 10) / 2 + branch * (w + 10);
	}

	private int rowY(int tier) {
		return 74 + (tier - 1) * ROW_GAP;
	}

	private void layout() {
		positions.clear();
		Race race = race();
		if (race == null) {
			return;
		}
		Map<String, List<Talent>> cells = new HashMap<>();
		for (Talent t : Talents.of(race)) {
			cells.computeIfAbsent(t.branch() + ":" + t.tier(), k -> new ArrayList<>()).add(t);
		}
		cells.forEach((cell, list) -> {
			for (int i = 0; i < list.size(); i++) {
				Talent t = list.get(i);
				int center = columnX(t.branch()) + columnWidth() / 2;
				int x = center - (list.size() * (NODE + 14) - 14) / 2 + i * (NODE + 14);
				positions.put(t.id(), new int[] {x, rowY(t.tier())});
			}
		});
	}

	@Override
	protected void init() {
		layout();
	}

	public static NodeState state(Talent t) {
		if (ClientRaceState.talents.contains(t.id())) {
			return NodeState.OWNED;
		}
		if (t.tier() > ClientRaceState.openTiers) {
			return NodeState.TIER_CLOSED;
		}
		if (t.requires() != null && !ClientRaceState.talents.contains(t.requires())) {
			return NodeState.NEEDS_TALENT;
		}
		return ClientRaceState.freePoints >= t.cost() ? NodeState.AVAILABLE : NodeState.NO_POINTS;
	}

	private static int borderColor(NodeState state) {
		return switch (state) {
			case OWNED -> 0xFFE8C060;
			case AVAILABLE -> 0xFF7CD46A;
			case NO_POINTS -> 0xFF6A8A60;
			case NEEDS_TALENT -> 0xFF505058;
			case TIER_CLOSED -> 0xFF6A2A2A;
		};
	}

	public boolean click(String talentId) {
		Talent t = Talents.byId(talentId).orElse(null);
		if (t == null || state(t) != NodeState.AVAILABLE) {
			return false;
		}
		MagesLegacyClient.sendUnlock(talentId);
		return true;
	}

	@Override
	public boolean mouseClicked(double mouseX, double mouseY, int button) {
		for (Map.Entry<String, int[]> e : positions.entrySet()) {
			int[] p = e.getValue();
			if (mouseX >= p[0] && mouseX < p[0] + NODE && mouseY >= p[1] && mouseY < p[1] + NODE) {
				click(e.getKey());
				return true;
			}
		}
		return super.mouseClicked(mouseX, mouseY, button);
	}

	@Override
	public void render(DrawContext ctx, int mouseX, int mouseY, float delta) {
		ctx.fillGradient(0, 0, width, height, 0xF0100C14, 0xF0060408);
		Race race = race();
		if (race == null) {
			super.render(ctx, mouseX, mouseY, delta);
			return;
		}
		if (positions.isEmpty()) {
			layout();
		}
		// Header: race, level, experience, free points
		ctx.drawCenteredTextWithShadow(textRenderer, Text.translatable("mages_legacy.screen.tree_header", Text.translatable(race.key()),
				ClientRaceState.level, ClientRaceState.xp, ClientRaceState.xpNext), width / 2, 6, race.color);
		ctx.drawCenteredTextWithShadow(textRenderer, Text.translatable("mages_legacy.screen.points", ClientRaceState.freePoints)
				.formatted(ClientRaceState.freePoints > 0 ? Formatting.GREEN : Formatting.GRAY), width / 2, 17, 0xFFFFFF);
		int barW = 160;
		int barX = width / 2 - barW / 2;
		ctx.fill(barX, 28, barX + barW, 31, 0xFF2A2430);
		ctx.fill(barX, 28, barX + barW * ClientRaceState.xp / Math.max(1, ClientRaceState.xpNext), 31, 0xFF000000 | race.color);

		// Branch titles and their one-line legends
		for (int branch = 0; branch < 3; branch++) {
			int x = columnX(branch);
			int w = columnWidth();
			String key = "mages_legacy.branch." + race.id() + "." + branch;
			ctx.fill(x, 36, x + w, rowY(3) + NODE + 8, 0x50000000);
			ctx.drawCenteredTextWithShadow(textRenderer, Text.translatable(key).formatted(Formatting.GOLD, Formatting.BOLD), x + w / 2, 40, 0xFFFFFF);
			List<OrderedText> lore = textRenderer.wrapLines(Text.translatable(key + ".lore").formatted(Formatting.ITALIC), w - 8);
			for (int i = 0; i < Math.min(2, lore.size()); i++) {
				ctx.drawCenteredTextWithShadow(textRenderer, lore.get(i), x + w / 2, 51 + i * 9, 0x9A9088);
			}
		}
		// Tier numerals at the left edge, red while the milestone is still ahead
		for (int tier = 1; tier <= 3; tier++) {
			int color = tier <= ClientRaceState.openTiers ? 0xC8C0B0 : 0x9A4040;
			ctx.drawText(textRenderer, ROMAN[tier - 1], columnX(0) - 16, rowY(tier) + 7, color, true);
		}

		// Links from each talent to the one it needs
		for (Talent t : Talents.of(race)) {
			int[] p = positions.get(t.id());
			int[] q = t.requires() == null ? null : positions.get(t.requires());
			if (p == null || q == null) {
				continue;
			}
			int color = ClientRaceState.talents.contains(t.id()) ? 0xFFE8C060 : 0xFF4A4450;
			int fromX = q[0] + NODE / 2;
			int toX = p[0] + NODE / 2;
			int midY = q[1] + NODE + (p[1] - q[1] - NODE) / 2;
			ctx.fill(fromX, q[1] + NODE, fromX + 1, midY + 1, color);
			ctx.fill(Math.min(fromX, toX), midY, Math.max(fromX, toX) + 1, midY + 1, color);
			ctx.fill(toX, midY, toX + 1, p[1], color);
		}

		Talent hovered = null;
		for (Talent t : Talents.of(race)) {
			int[] p = positions.get(t.id());
			NodeState state = state(t);
			ctx.fill(p[0], p[1], p[0] + NODE, p[1] + NODE, state == NodeState.OWNED ? 0xFF3A3020 : 0xFF18141C);
			ctx.drawBorder(p[0], p[1], NODE, NODE, borderColor(state));
			ctx.drawItem(new ItemStack(t.icon()), p[0] + 3, p[1] + 3);
			if (state == NodeState.TIER_CLOSED || state == NodeState.NEEDS_TALENT) {
				ctx.getMatrices().push();
				ctx.getMatrices().translate(0, 0, 200);
				ctx.fill(p[0] + 1, p[1] + 1, p[0] + NODE - 1, p[1] + NODE - 1, 0x90000000);
				ctx.getMatrices().pop();
			}
			if (mouseX >= p[0] && mouseX < p[0] + NODE && mouseY >= p[1] && mouseY < p[1] + NODE) {
				hovered = t;
			}
		}

		// Abilities along the bottom
		int abilityY = height - 30;
		int abilityHovered = -1;
		if (race == Race.MYCELID) {
			int total = 3 * 150;
			for (int slot = 0; slot < 3; slot++) {
				int x = (width - total) / 2 + slot * 150;
				ctx.fill(x, abilityY, x + 144, abilityY + 24, 0x80000000);
				ctx.drawItem(new ItemStack(RaceHud.MYCELID_ICONS[slot]), x + 4, abilityY + 4);
				String key = "mages_legacy.ability.mycelid." + (slot + 1);
				ctx.drawText(textRenderer, Text.translatable(key + ".name").formatted(Formatting.GOLD), x + 24, abilityY + 3, 0xFFFFFF, true);
				ctx.drawText(textRenderer, Text.translatable("mages_legacy.screen.ability_line",
						MagesLegacyClient.ABILITY_KEYS[slot].getBoundKeyLocalizedText(), RaceHud.MYCELID_COSTS[slot]), x + 24, abilityY + 13, 0xA0A0A0, true);
				if (mouseX >= x && mouseX < x + 144 && mouseY >= abilityY && mouseY < abilityY + 24) {
					abilityHovered = slot;
				}
			}
		}

		super.render(ctx, mouseX, mouseY, delta);
		if (hovered != null) {
			ctx.drawTooltip(textRenderer, tooltip(hovered), mouseX, mouseY);
		} else if (abilityHovered >= 0) {
			String key = "mages_legacy.ability.mycelid." + (abilityHovered + 1);
			List<Text> lines = new ArrayList<>();
			lines.add(Text.translatable(key + ".name").formatted(Formatting.GOLD));
			wrapInto(lines, Text.translatable(key + ".lore").formatted(Formatting.GRAY, Formatting.ITALIC));
			wrapInto(lines, Text.translatable(key + ".desc").formatted(Formatting.WHITE));
			ctx.drawTooltip(textRenderer, lines, mouseX, mouseY);
		}
	}

	public static List<Text> tooltip(Talent t) {
		List<Text> lines = new ArrayList<>();
		lines.add(Text.translatable(t.key()).formatted(Formatting.GOLD, Formatting.BOLD));
		wrapInto(lines, Text.translatable(t.key() + ".lore").formatted(Formatting.GRAY, Formatting.ITALIC));
		wrapInto(lines, Text.translatable(t.key() + ".desc").formatted(Formatting.WHITE));
		lines.add(Text.translatable("mages_legacy.screen.cost", t.cost()).formatted(Formatting.YELLOW));
		NodeState state = state(t);
		switch (state) {
			case OWNED -> lines.add(Text.translatable("mages_legacy.screen.state.owned").formatted(Formatting.GOLD));
			case AVAILABLE -> lines.add(Text.translatable("mages_legacy.screen.state.available").formatted(Formatting.GREEN));
			case NO_POINTS -> lines.add(Text.translatable("mages_legacy.screen.state.no_points").formatted(Formatting.RED));
			case NEEDS_TALENT -> lines.add(Text.translatable("mages_legacy.screen.state.needs",
					Text.translatable("mages_legacy.talent." + t.requires())).formatted(Formatting.RED));
			case TIER_CLOSED -> lines.add(Text.translatable("mages_legacy.screen.state.tier_" + t.tier()).formatted(Formatting.DARK_RED));
		}
		return lines;
	}

	private static void wrapInto(List<Text> lines, Text text) {
		// Tooltips do not wrap by themselves; split long lines at about 40 characters on spaces
		String plain = text.getString();
		StringBuilder line = new StringBuilder();
		for (String word : plain.split(" ")) {
			if (line.length() + word.length() > 40 && line.length() > 0) {
				lines.add(Text.literal(line.toString()).setStyle(text.getStyle()));
				line.setLength(0);
			}
			if (line.length() > 0) {
				line.append(' ');
			}
			line.append(word);
		}
		if (line.length() > 0) {
			lines.add(Text.literal(line.toString()).setStyle(text.getStyle()));
		}
	}

	@Override
	public boolean shouldPause() {
		return false;
	}
}
