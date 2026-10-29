package euf.mageslegacy.client.screen;

import com.mojang.math.Axis;
import euf.mageslegacy.client.ClientRace;
import euf.mageslegacy.net.LearnPayload;
import euf.mageslegacy.net.SyncPayload;
import euf.mageslegacy.race.Race;
import euf.mageslegacy.race.Talent;
import euf.mageslegacy.race.Talents;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.resources.language.I18n;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.FormattedCharSequence;
import net.neoforged.neoforge.network.PacketDistributor;

/**
 * The race's talent tree, working as in the first version (tiers opened by race level, a header with the emblem,
 * level, experience bar and free points, the "?" guide to experience, points and tiers, the traits book, the ability
 * bar along the bottom) but laid out as one radial web around the core instead of three columns: drag to move,
 * scroll to zoom. Links glow where the path is learnt, learnable nodes breathe in gold; a node's tooltip tells all.
 */
public class TalentScreen extends Screen {
	private static final int RING = 46;
	private static final String[] ROMAN = {"I", "II", "III", "IV"};
	/** Whether the guide was opened this session; until then its button glows. */
	private static boolean guideSeen;
	private float zoom = 1;
	private double panX;
	private double panY;
	private boolean showGuide;
	private boolean showTraits;
	private int pageScroll;
	private int pageMax;

	public TalentScreen() {
		super(Component.translatable("mages_legacy.screen.talents"));
	}

	private enum State {
		OWNED, AVAILABLE, LOCKED
	}

	private static List<String> learnt() {
		SyncPayload state = ClientRace.state();
		return state == null ? List.of() : state.talents();
	}

	private static int level() {
		SyncPayload state = ClientRace.state();
		return state == null ? 1 : state.level();
	}

	private static boolean owned(Talent t) {
		return t.automatic() ? level() >= t.autoLevel : learnt().contains(t.id());
	}

	private static int[] progress() {
		SyncPayload state = ClientRace.state();
		return state == null || state.progress().length < 5 ? new int[5] : state.progress();
	}

	private static int openTiers() {
		return Math.max(1, progress()[4]);
	}

	private static int freePoints() {
		return Talent.pointsAt(level()) - Talents.spent(learnt());
	}

	private static State state(Talent t) {
		if (owned(t)) {
			return State.OWNED;
		}
		String why = refusal(t);
		// The second Awakening's trial is started from its node
		return why == null || why.equals("mages_legacy.talent.state.superlate_trial") ? State.AVAILABLE : State.LOCKED;
	}

	private static net.minecraft.nbt.CompoundTag progressTag() {
		SyncPayload state = ClientRace.state();
		int[] p = progress();
		net.minecraft.nbt.CompoundTag tag = new net.minecraft.nbt.CompoundTag();
		tag.putInt(Talents.POCKET_TICKS, p[0]);
		tag.putInt(Talents.STEP_BLOCKS, p[1]);
		tag.putInt(Talents.GRAVITY_KILLS, p[2]);
		tag.putInt(Talents.SUPERLATE, p[3]);
		return tag;
	}

	/** Same rules as the server's {@link Talents#refusal}, for what the screen shows. */
	private static String refusal(Talent t) {
		if (t.automatic()) {
			return "mages_legacy.talent.state.automatic";
		}
		if (t.tier() > openTiers()) {
			return "mages_legacy.talent.state.tier_" + t.tier();
		}
		boolean linked = t.requires().isEmpty();
		for (Talent near : t.requires()) {
			linked |= owned(near);
		}
		if (!linked) {
			return "mages_legacy.talent.state.needs";
		}
		if (t.kind == Talent.Kind.AWAKENING) {
			String why = Talents.awakeningRefusal(learnt(), level(), t, progressTag());
			if (why != null) {
				return why;
			}
		}
		return freePoints() < t.cost() ? "mages_legacy.talent.state.no_points" : null;
	}

	private int cx() {
		return (int) (width / 2 + panX);
	}

	private int cy() {
		return (int) (36 + (height - 36 - 34) / 2 + panY);
	}

	private float scale() {
		// The whole web (out to the Awakenings' own talents) fits between the header and the ability bar at zoom 1
		float fit = Math.min(width, height - 70) / (2 * 6.4f * RING);
		return Math.max(0.5f, Math.min(1.3f, fit)) * zoom;
	}

	private int nodeX(Talent t) {
		return cx() + (int) Math.round(Math.cos(Math.toRadians(t.angle)) * t.ring * RING * scale());
	}

	private int nodeY(Talent t) {
		return cy() + (int) Math.round(Math.sin(Math.toRadians(t.angle)) * t.ring * RING * scale());
	}

	private static int size(Talent t) {
		return switch (t.kind) {
			case CORE -> 30;
			case BASIC, AWAKENING -> 26;
			default -> 20;
		};
	}

	@Override
	public void renderBackground(GuiGraphics graphics, int mouseX, int mouseY, float delta) {
		LegacyUi.background(graphics, width, height);
	}

	@Override
	public void render(GuiGraphics graphics, int mouseX, int mouseY, float delta) {
		super.render(graphics, mouseX, mouseY, delta);
		Race race = ClientRace.race();
		if (race == null) {
			return;
		}
		int color = LegacyUi.argb(race.color);
		List<Talent> tree = Talent.of(race);
		// The directions' names, out past their Awakenings
		branchName(graphics, "rift", -90, color);
		branchName(graphics, "worlds", 150, color);
		branchName(graphics, "gravity", 30, color);
		// Links first, under the nodes
		for (Talent t : tree) {
			for (Talent near : t.requires()) {
				boolean lit = owned(t) && owned(near);
				boolean open = owned(near);
				int c = lit ? color : open ? LegacyUi.alpha(LegacyUi.GOLD, 0.55f) : 0x803A2E22;
				line(graphics, nodeX(near), nodeY(near), nodeX(t), nodeY(t), lit ? 2 : 1, c);
			}
		}
		// Two Awakenings joined by an arc of light across the core: the mark of a superlate Voidborn
		List<Talent> awakened = euf.mageslegacy.race.Awakenings.owned(learnt());
		if (awakened.size() == 2) {
			float pulse = 0.6f + 0.4f * (float) Math.sin(LegacyUi.time() * 2);
			Talent a = awakened.get(0);
			Talent b = awakened.get(1);
			int steps = 40;
			for (int i = 0; i < steps; i++) {
				double t0 = (double) i / steps;
				double t1 = (double) (i + 1) / steps;
				// A curve bowing out through the core
				int x0 = (int) bezier(nodeX(a), cx(), nodeX(b), t0);
				int y0 = (int) bezier(nodeY(a), cy(), nodeY(b), t0);
				int x1 = (int) bezier(nodeX(a), cx(), nodeX(b), t1);
				int y1 = (int) bezier(nodeY(a), cy(), nodeY(b), t1);
				line(graphics, x0, y0, x1, y1, 4, LegacyUi.alpha(color, 0.25f * pulse));
				line(graphics, x0, y0, x1, y1, 2, LegacyUi.alpha(0xFFF2E8FF, 0.8f * pulse));
			}
		}
		Talent hovered = null;
		for (Talent t : tree) {
			if (drawNode(graphics, t, mouseX, mouseY, color)) {
				hovered = t;
			}
		}
		List<Component> headTip = drawHeader(graphics, race, color, mouseX, mouseY);
		drawRoundButton(graphics, guideX(), "?", !guideSeen, color, mouseX, mouseY);
		drawRoundButton(graphics, traitsX(), "\u2261", false, color, mouseX, mouseY);
		euf.mageslegacy.race.Ability ability = drawAbilities(graphics, race, color, mouseX, mouseY);
		if (showGuide || showTraits) {
			drawPage(graphics, race, color);
			return;
		}
		if (hovered != null) {
			graphics.renderComponentTooltip(font, tooltip(hovered), mouseX, mouseY);
		} else if (headTip != null) {
			graphics.renderComponentTooltip(font, headTip, mouseX, mouseY);
		} else if (ability != null) {
			graphics.renderComponentTooltip(font, abilityTooltip(ability), mouseX, mouseY);
		}
	}

	// ---- The header ----------------------------------------------------------------------------------------------

	private int headerX0() {
		return (width - 260) / 2;
	}

	private int guideX() {
		return headerX0() + 260 + 6;
	}

	private int traitsX() {
		return headerX0() - 26;
	}

	/** Emblem, race name and level, the experience bar and the free points gem. Returns a tooltip if hovered. */
	private List<Component> drawHeader(GuiGraphics graphics, Race race, int color, int mouseX, int mouseY) {
		SyncPayload state = ClientRace.state();
		int x0 = headerX0();
		int x1 = x0 + 260;
		LegacyUi.panel(graphics, x0, 4, x1, 32);
		LegacyUi.glow(graphics, x0 + 16, 18, 16, color, 0.7f);
		if (LegacyUi.exists(LegacyUi.emblem(race))) {
			LegacyUi.emblem(graphics, race, x0 + 4, 6, 24, true);
		}
		Component name = Component.translatable(race.key()).withStyle(ChatFormatting.BOLD);
		graphics.drawString(font, name, x0 + 32, 8, color);
		graphics.drawString(font, Component.translatable("mages_legacy.screen.level", level()), x0 + 32 + font.width(name) + 6, 8, LegacyUi.TEXT);
		// Experience bar with a light running along it
		int bx0 = x0 + 32;
		int bx1 = x1 - 40;
		int by = 21;
		int xp = state == null ? 0 : state.xp();
		int next = state == null ? 1 : Math.max(1, state.xpNext());
		graphics.fill(bx0 - 1, by - 1, bx1 + 1, by + 5, LegacyUi.BLACK);
		graphics.fill(bx0, by, bx1, by + 4, 0xFF221C24);
		int fill = (bx1 - bx0) * Math.min(xp, next) / next;
		if (fill > 0) {
			graphics.fillGradient(bx0, by, bx0 + fill, by + 4, LegacyUi.mix(color, 0xFFFFFFFF, 0.3f), LegacyUi.mix(color, 0xFF000000, 0.3f));
			int shine = bx0 + (int) ((LegacyUi.time() * 40) % Math.max(1, fill + 20)) - 10;
			graphics.enableScissor(bx0, by, bx0 + fill, by + 4);
			graphics.fillGradient(shine, by, shine + 8, by + 4, 0x70FFFFFF, 0x20FFFFFF);
			graphics.disableScissor();
		}
		// Free points: a gem with the number, glowing when something can be learnt
		int gx = x1 - 22;
		int gy = 18;
		int points = freePoints();
		boolean any = points > 0;
		if (any) {
			LegacyUi.glow(graphics, gx, gy, 12, LegacyUi.GREEN, 0.6f + 0.4f * (float) Math.sin(LegacyUi.time() * 3));
		}
		LegacyUi.diamond(graphics, gx, gy, 9, LegacyUi.BLACK);
		LegacyUi.diamond(graphics, gx, gy, 8, any ? 0xFF3C6A34 : 0xFF2A2328);
		LegacyUi.diamond(graphics, gx, gy, 6, any ? 0xFF243E20 : 0xFF181418);
		String text = String.valueOf(points);
		graphics.drawString(font, text, gx - font.width(text) / 2 + 1, gy - 3, any ? LegacyUi.GREEN : LegacyUi.MUTED, true);
		if (mouseY >= 4 && mouseY < 32 && mouseX >= x0 && mouseX < x1) {
			return List.of(Component.translatable("mages_legacy.screen.tree_header", Component.translatable(race.key()), level(), xp, next),
					Component.translatable("mages_legacy.screen.points", points).withStyle(any ? ChatFormatting.GREEN : ChatFormatting.GRAY));
		}
		return null;
	}

	/** A round stone button beside the header: the guide's "?" (glowing until first opened) or the traits book. */
	private void drawRoundButton(GuiGraphics graphics, int x, String label, boolean beckon, int color, int mouseX, int mouseY) {
		boolean hot = mouseX >= x && mouseX < x + 20 && mouseY >= 8 && mouseY < 28;
		if (beckon) {
			LegacyUi.glow(graphics, x + 10, 18, 16, LegacyUi.GOLD, 0.5f + 0.5f * (float) Math.sin(LegacyUi.time() * 3));
		}
		LegacyUi.panel(graphics, x, 8, x + 20, 28, hot ? color : LegacyUi.RIM);
		graphics.drawCenteredString(font, label, x + 10, 14, hot ? 0xFFFFFFFF : LegacyUi.GOLD);
	}

	// ---- The ability bar -----------------------------------------------------------------------------------------

	/** The abilities along the bottom: icon, name, cost and key; Awakenings on their keys. Returns the hovered one. */
	private euf.mageslegacy.race.Ability drawAbilities(GuiGraphics graphics, Race race, int color, int mouseX, int mouseY) {
		List<euf.mageslegacy.race.Ability> list = new ArrayList<>(euf.mageslegacy.race.Ability.bySlot(race));
		List<Integer> keys = new ArrayList<>();
		for (var a : list) {
			keys.add(a.slot);
		}
		for (int key : new int[] {euf.mageslegacy.race.Awakenings.FIRST_SLOT, euf.mageslegacy.race.Awakenings.SECOND_SLOT}) {
			euf.mageslegacy.race.Awakenings.at(learnt(), key).ifPresent(a -> {
				list.add(a);
				keys.add(key);
			});
		}
		if (list.isEmpty()) {
			return null;
		}
		int count = list.size();
		int tileW = Math.min(130, (width - 40 - (count - 1) * 6) / count);
		int total = count * tileW + (count - 1) * 6;
		int y = height - 28;
		euf.mageslegacy.race.Ability hovered = null;
		for (int i = 0; i < count; i++) {
			var a = list.get(i);
			int x = (width - total) / 2 + i * (tileW + 6);
			boolean hot = mouseX >= x && mouseX < x + tileW && mouseY >= y && mouseY < y + 24;
			if (hot) {
				hovered = a;
			}
			boolean locked = !a.awakening() && level() < a.level;
			LegacyUi.panel(graphics, x, y, x + tileW, y + 24, hot ? color : LegacyUi.RIM_DARK);
			ResourceLocation icon = a.awakening() ? icon(euf.mageslegacy.race.Awakenings.talentOf(a)) : LegacyUi.gui("icon/ability_" + a.id());
			if (icon != null && LegacyUi.exists(icon)) {
				LegacyUi.icon(graphics, icon, x + 4, y + 4, 16, !locked);
			}
			String name = Component.translatable(a.awakening() ? euf.mageslegacy.race.Awakenings.talentOf(a).key() + ".name" : a.key() + ".name").getString();
			int room = tileW - 40;
			float scaleText = Math.max(0.7f, Math.min(1, room / (float) Math.max(1, font.width(name))));
			graphics.pose().pushPose();
			graphics.pose().translate(x + 24, y + 3 + (1 - scaleText) * 4, 0);
			graphics.pose().scale(scaleText, scaleText, 1);
			graphics.drawString(font, font.plainSubstrByWidth(name, (int) (room / scaleText)), 0, 0, LegacyUi.GOLD);
			graphics.pose().popPose();
			if (locked) {
				graphics.drawString(font, Component.translatable("mages_legacy.screen.opens_at", a.level), x + 24, y + 13, LegacyUi.RED);
			} else {
				ResourceLocation res = LegacyUi.gui("icon/res_" + race.id());
				int cx = x + 24;
				if (LegacyUi.exists(res)) {
					LegacyUi.icon(graphics, res, cx - 1, y + 13, 9, true);
					cx += 10;
				}
				graphics.drawString(font, String.valueOf(a.cost), cx, y + 13, LegacyUi.mix(color, 0xFFFFFFFF, 0.3f));
			}
			LegacyUi.keyCap(graphics, euf.mageslegacy.race.Ability.KEY_NAMES[keys.get(i)], x + tileW - 4, y + 20, 0xFFFFFFFF);
		}
		return hovered;
	}

	private List<Component> abilityTooltip(euf.mageslegacy.race.Ability a) {
		List<Component> lines = new ArrayList<>();
		String key = a.awakening() ? euf.mageslegacy.race.Awakenings.talentOf(a).key() : a.key();
		lines.add(Component.translatable(key + ".name").withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD));
		if (I18n.exists(key + ".lore")) {
			wrapInto(lines, Component.translatable(key + ".lore").withStyle(ChatFormatting.GRAY, ChatFormatting.ITALIC));
		}
		wrapInto(lines, Component.translatable(key + ".desc").withStyle(ChatFormatting.WHITE));
		return lines;
	}

	// ---- The guide and the traits book --------------------------------------------------------------------------

	/** One of the two pages over the tree, scrolling inside its panel when taller than the screen. */
	private void drawPage(GuiGraphics graphics, Race race, int color) {
		graphics.pose().pushPose();
		graphics.pose().translate(0, 0, 300);
		graphics.fill(0, 0, width, height, 0xA0000000);
		int w = Math.min(340, width - 30);
		int x0 = (width - w) / 2;
		int h = showGuide ? guide(graphics, race, color, x0, 0, w, true) : traits(graphics, race, color, x0, 0, w, true);
		int shown = Math.min(h, height - 8);
		pageMax = h - shown;
		pageScroll = Math.max(0, Math.min(pageMax, pageScroll));
		int y0 = (height - shown) / 2;
		LegacyUi.panel(graphics, x0, y0, x0 + w, y0 + shown);
		graphics.enableScissor(x0 + 1, y0 + 2, x0 + w - 1, y0 + shown - 2);
		if (showGuide) {
			guide(graphics, race, color, x0, y0 - pageScroll, w, false);
		} else {
			traits(graphics, race, color, x0, y0 - pageScroll, w, false);
		}
		graphics.disableScissor();
		graphics.pose().popPose();
	}

	/**
	 * The guide: what gives race experience (and how much), how points come with levels, which race level opens each
	 * tier, and the two Awakenings. Lays out (and unless {@code measure}, draws); returns its height.
	 */
	private int guide(GuiGraphics graphics, Race race, int color, int x0, int y0, int w, boolean measure) {
		int x = x0 + 12;
		int right = x0 + w - 12;
		int y = y0 + 8;
		if (!measure) {
			LegacyUi.heading(graphics, Component.translatable("mages_legacy.guide.title").withStyle(ChatFormatting.BOLD), x0 + w / 2, y, LegacyUi.GOLD);
		}
		y += 20;
		y = title(graphics, Component.translatable("mages_legacy.guide.xp"), x, y, color, measure);
		List<Component> xp = Lines.of("mages_legacy.xp." + race.id());
		for (int i = 0; i < xp.size(); i++) {
			if (!measure) {
				LegacyUi.diamond(graphics, x + 3, y + 3, 2, color);
				String amount = Component.translatable("mages_legacy.xp." + race.id() + "." + (i + 1) + ".amount").getString();
				int aw = font.width(amount);
				graphics.drawString(font, font.plainSubstrByWidth(xp.get(i).getString(), right - x - 16 - aw), x + 10, y, LegacyUi.TEXT);
				graphics.drawString(font, amount, right - aw, y, LegacyUi.GREEN);
			}
			y += 11;
		}
		y = divider(graphics, x, right, y, color, measure);
		y = title(graphics, Component.translatable("mages_legacy.guide.points_title"), x, y, LegacyUi.GREEN, measure);
		y = gems(graphics, Lines.of("mages_legacy.guide.points"), x, right, y, LegacyUi.GREEN, LegacyUi.PARCHMENT, measure);
		y = divider(graphics, x, right, y, color, measure);
		y = title(graphics, Component.translatable("mages_legacy.guide.tiers"), x, y, LegacyUi.GOLD, measure);
		int open = openTiers();
		for (int tier = 1; tier <= Talent.MAX_TIER; tier++) {
			boolean isOpen = tier <= open;
			if (!measure) {
				graphics.drawString(font, ROMAN[tier - 1], x + 2, y, isOpen ? LegacyUi.GOLD : 0xFF9A4040);
				graphics.drawString(font, Component.translatable("mages_legacy.guide.tier." + tier), x + 24, y, isOpen ? LegacyUi.TEXT : LegacyUi.MUTED);
				if (isOpen) {
					String done = "\u2714";
					graphics.drawString(font, done, right - font.width(done), y, LegacyUi.GREEN);
				}
			}
			y += 12;
		}
		y = divider(graphics, x, right, y, color, measure);
		y = title(graphics, Component.translatable("mages_legacy.guide.awakenings"), x, y, color, measure);
		y = gems(graphics, Lines.of("mages_legacy.guide.awakening"), x, right, y, color, LegacyUi.PARCHMENT, measure);
		return y - y0 + 6;
	}

	/** The traits book: the race's strengths and weaknesses, then what every learnt talent gives. Returns its height. */
	private int traits(GuiGraphics graphics, Race race, int color, int x0, int y0, int w, boolean measure) {
		int x = x0 + 12;
		int right = x0 + w - 12;
		int y = y0 + 8;
		if (!measure) {
			LegacyUi.heading(graphics, Component.translatable("mages_legacy.traits.title").withStyle(ChatFormatting.BOLD), x0 + w / 2, y, LegacyUi.GOLD);
		}
		y += 20;
		y = title(graphics, Component.translatable("mages_legacy.screen.strengths"), x, y, LegacyUi.GREEN, measure);
		y = gems(graphics, Lines.of(race.key() + ".perk"), x, right, y, LegacyUi.GREEN, 0xFFD6EBD0, measure);
		y = divider(graphics, x, right, y, color, measure);
		y = title(graphics, Component.translatable("mages_legacy.screen.weaknesses"), x, y, LegacyUi.RED, measure);
		y = gems(graphics, Lines.of(race.key() + ".weak"), x, right, y, LegacyUi.RED, 0xFFEBD0D0, measure);
		y = divider(graphics, x, right, y, color, measure);
		y = title(graphics, Component.translatable("mages_legacy.traits.talents"), x, y, color, measure);
		List<Component> owned = new ArrayList<>();
		for (Talent t : Talent.of(race)) {
			if (!t.automatic() && owned(t)) {
				owned.add(Component.translatable(t.key() + ".name").withStyle(ChatFormatting.GOLD).append(Component.literal(" — ").withStyle(ChatFormatting.GRAY))
						.append(Component.translatable(t.key() + ".desc").withStyle(ChatFormatting.WHITE)));
			}
		}
		if (owned.isEmpty()) {
			owned.add(Component.translatable("mages_legacy.traits.none").withStyle(ChatFormatting.ITALIC));
		}
		y = gems(graphics, owned, x, right, y, color, LegacyUi.PARCHMENT, measure);
		return y - y0 + 6;
	}

	private int title(GuiGraphics graphics, Component text, int x, int y, int color, boolean measure) {
		if (!measure) {
			graphics.drawString(font, text.copy().withStyle(ChatFormatting.BOLD), x, y, color);
		}
		return y + 14;
	}

	private int divider(GuiGraphics graphics, int x, int right, int y, int color, boolean measure) {
		if (!measure) {
			LegacyUi.divider(graphics, x, right, y + 4, color);
		}
		return y + 12;
	}

	private int gems(GuiGraphics graphics, List<Component> lines, int x, int right, int y, int gem, int textColor, boolean measure) {
		for (Component line : lines) {
			if (!measure) {
				LegacyUi.diamond(graphics, x + 3, y + 3, 2, gem);
			}
			for (FormattedCharSequence row : font.split(line, right - x - 10)) {
				if (!measure) {
					graphics.drawString(font, row, x + 10, y, textColor, true);
				}
				y += 10;
			}
			y += 2;
		}
		return y;
	}

	private void branchName(GuiGraphics graphics, String branch, float angle, int color) {
		double r = 6.3 * RING * scale();
		int x = cx() + (int) (Math.cos(Math.toRadians(angle)) * r);
		int y = cy() + (int) (Math.sin(Math.toRadians(angle)) * r) - 4;
		Component name = Component.translatable("mages_legacy.talent.branch." + branch).withStyle(ChatFormatting.BOLD);
		graphics.drawCenteredString(font, name, x, y, LegacyUi.alpha(color, 0.9f));
	}

	private static double bezier(double a, double c, double b, double t) {
		return (1 - t) * (1 - t) * a + 2 * (1 - t) * t * c + t * t * b;
	}

	/** A straight line of the given thickness between two points (drawn as a turned thin box). */
	private static void line(GuiGraphics graphics, int x0, int y0, int x1, int y1, int thickness, int color) {
		double dx = x1 - x0;
		double dy = y1 - y0;
		float length = (float) Math.sqrt(dx * dx + dy * dy);
		if (length < 1) {
			return;
		}
		graphics.pose().pushPose();
		graphics.pose().translate(x0, y0, 0);
		graphics.pose().mulPose(Axis.ZP.rotation((float) Math.atan2(dy, dx)));
		graphics.fill(0, -thickness / 2, Math.round(length), thickness - thickness / 2, color);
		graphics.pose().popPose();
	}

	/** Draws a node; returns whether the mouse is over it. */
	private boolean drawNode(GuiGraphics graphics, Talent t, int mouseX, int mouseY, int color) {
		int s = Math.max(12, Math.round(size(t) * Math.min(1.2f, scale())));
		int x = nodeX(t) - s / 2;
		int y = nodeY(t) - s / 2;
		boolean hot = mouseX >= x && mouseX < x + s && mouseY >= y && mouseY < y + s;
		State state = state(t);
		int rim = switch (state) {
			case OWNED -> color;
			case AVAILABLE -> LegacyUi.mix(LegacyUi.RIM, LegacyUi.GOLD, 0.5f + 0.5f * (float) Math.sin(LegacyUi.time() * 3));
			case LOCKED -> LegacyUi.RIM_DARK;
		};
		if (state == State.OWNED) {
			LegacyUi.glow(graphics, x + s / 2, y + s / 2, s, color, t.kind == Talent.Kind.AWAKENING || t.kind == Talent.Kind.CORE ? 0.9f : 0.5f);
		}
		LegacyUi.panel(graphics, x, y, x + s, y + s, hot ? LegacyUi.GOLD : rim);
		if (t.kind == Talent.Kind.AWAKENING) {
			// An Awakening wears a crown of four gems
			for (int[] c : new int[][] {{x + s / 2, y - 3}, {x + s / 2, y + s + 2}, {x - 3, y + s / 2}, {x + s + 2, y + s / 2}}) {
				LegacyUi.diamond(graphics, c[0], c[1], 3, LegacyUi.BLACK);
				LegacyUi.diamond(graphics, c[0], c[1], 2, state == State.OWNED ? color : LegacyUi.RIM);
			}
		} else if (t.kind == Talent.Kind.CROSS) {
			LegacyUi.diamond(graphics, x + s - 2, y + 2, 2, LegacyUi.alpha(color, 0.9f));
		} else if (t.kind == Talent.Kind.TRANSFORM) {
			LegacyUi.outline(graphics, x + 2, y + 2, x + s - 2, y + s - 2, LegacyUi.alpha(rim, 0.6f));
		}
		ResourceLocation icon = icon(t);
		int inner = s - 6;
		if (icon != null) {
			LegacyUi.icon(graphics, icon, x + 3, y + 3, inner, state != State.LOCKED);
		}
		if (state == State.LOCKED && !t.automatic()) {
			graphics.fill(x + 1, y + 1, x + s - 1, y + s - 1, 0x66000000);
		}
		return hot;
	}

	/** The node's own Codex icon (textures/gui/talent/<id>), else the shared one its definition names. */
	private static ResourceLocation icon(Talent t) {
		ResourceLocation own = LegacyUi.gui("talent/" + t.id());
		if (LegacyUi.exists(own)) {
			return own;
		}
		ResourceLocation talent = LegacyUi.gui("talent/" + t.icon);
		if (LegacyUi.exists(talent)) {
			return talent;
		}
		ResourceLocation ability = LegacyUi.gui("icon/" + t.icon);
		return LegacyUi.exists(ability) ? ability : null;
	}

	private List<Component> tooltip(Talent t) {
		List<Component> lines = new ArrayList<>();
		lines.add(Component.translatable(t.key() + ".name").withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD));
		Component kind = Component.translatable(t.kind.key());
		lines.add(t.automatic()
				? Component.translatable("mages_legacy.talent.meta_free", kind, t.autoLevel).withStyle(ChatFormatting.DARK_PURPLE)
				: Component.translatable("mages_legacy.talent.meta", kind, ROMAN[t.tier() - 1], t.cost()).withStyle(ChatFormatting.DARK_PURPLE));
		if (I18n.exists(t.key() + ".lore")) {
			wrapInto(lines, Component.translatable(t.key() + ".lore").withStyle(ChatFormatting.GRAY, ChatFormatting.ITALIC));
		}
		wrapInto(lines, Component.translatable(t.key() + ".desc").withStyle(ChatFormatting.WHITE));
		if (t.kind == Talent.Kind.AWAKENING) {
			lines.add(Component.empty());
			wrapInto(lines, Component.translatable(t.key() + ".trial", trialProgress(t)).withStyle(ChatFormatting.LIGHT_PURPLE));
			wrapInto(lines, Component.translatable("mages_legacy.talent.two_of_three").withStyle(ChatFormatting.DARK_PURPLE));
		}
		lines.add(Component.empty());
		String why = owned(t) ? "mages_legacy.talent.state.owned" : refusal(t);
		if (why == null) {
			lines.add(Component.translatable("mages_legacy.talent.state.available").withStyle(ChatFormatting.GREEN));
		} else if (why.equals("mages_legacy.talent.state.superlate_trial")) {
			wrapInto(lines, Component.translatable(why).withStyle(ChatFormatting.GOLD));
		} else {
			Object arg = switch (why) {
				case "mages_legacy.talent.state.level" -> t.levelNeeded();
				case "mages_legacy.talent.state.superlate_level" -> euf.mageslegacy.race.Awakenings.SUPERLATE_LEVEL;
				case "mages_legacy.talent.state.automatic" -> t.autoLevel;
				case "mages_legacy.talent.state.needs" -> needsNames(t);
				default -> "";
			};
			wrapInto(lines, Component.translatable(why, arg).withStyle(owned(t) ? ChatFormatting.AQUA : ChatFormatting.RED));
		}
		return lines;
	}

	/** What the Awakening's trial has counted so far, in its own units. */
	private static String trialProgress(Talent t) {
		SyncPayload state = ClientRace.state();
		int[] p = state == null || state.progress().length < 3 ? new int[3] : state.progress();
		return switch (t) {
			case AWAKENING_RIFT -> String.valueOf(p[0] / 20 / 60);
			case AWAKENING_WORLDS -> String.valueOf(p[1]);
			case AWAKENING_HOLE -> String.valueOf(p[2]);
			default -> "";
		};
	}

	private static String needsNames(Talent t) {
		List<String> names = new ArrayList<>();
		for (Talent near : t.requires()) {
			names.add(Component.translatable(near.key() + ".name").getString());
		}
		return String.join(" / ", names);
	}

	private void wrapInto(List<Component> lines, Component text) {
		for (var part : font.getSplitter().splitLines(text, 240, text.getStyle())) {
			lines.add(Component.literal(part.getString()).withStyle(text.getStyle()));
		}
	}

	@Override
	public boolean mouseClicked(double mouseX, double mouseY, int button) {
		if (mouseY >= 8 && mouseY < 28 && mouseX >= guideX() && mouseX < guideX() + 20) {
			showTraits = false;
			showGuide = !showGuide;
			guideSeen = true;
			pageScroll = 0;
			return true;
		}
		if (mouseY >= 8 && mouseY < 28 && mouseX >= traitsX() && mouseX < traitsX() + 20) {
			showGuide = false;
			showTraits = !showTraits;
			pageScroll = 0;
			return true;
		}
		if (showGuide || showTraits) {
			showGuide = false;
			showTraits = false;
			return true;
		}
		Race race = ClientRace.race();
		if (race != null && button == 0) {
			for (Talent t : Talent.of(race)) {
				int s = Math.max(12, Math.round(size(t) * Math.min(1.2f, scale())));
				int x = nodeX(t) - s / 2;
				int y = nodeY(t) - s / 2;
				if (mouseX >= x && mouseX < x + s && mouseY >= y && mouseY < y + s) {
					if (state(t) == State.AVAILABLE) {
						learn(t);
					} else {
						minecraft.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.NOTE_BLOCK_BASS.value(), 0.6f));
					}
					return true;
				}
			}
		}
		return super.mouseClicked(mouseX, mouseY, button);
	}

	/** Sends the choice to the server (the harness uses it too). */
	public void learn(Talent t) {
		PacketDistributor.sendToServer(new LearnPayload(t.id()));
		minecraft.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, 1f));
	}

	@Override
	public boolean mouseDragged(double mouseX, double mouseY, int button, double dragX, double dragY) {
		if (showGuide || showTraits) {
			return true;
		}
		panX += dragX;
		panY += dragY;
		return true;
	}

	@Override
	public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
		if (showGuide || showTraits) {
			pageScroll = Math.max(0, Math.min(pageMax, pageScroll - (int) (scrollY * 12)));
			return true;
		}
		zoom = Math.max(0.6f, Math.min(2f, zoom + (float) scrollY * 0.1f));
		return true;
	}

	@Override
	public boolean isPauseScreen() {
		return false;
	}
}
