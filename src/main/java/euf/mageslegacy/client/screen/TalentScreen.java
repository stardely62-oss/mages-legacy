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
import net.neoforged.neoforge.network.PacketDistributor;

/**
 * The race's talent tree as one radial web around its core: links glow where the path is learnt, learnable nodes
 * breathe in gold, the three directions are named at their ends and the Awakenings sit on the outer edge. Drag to
 * move, scroll to zoom, click a breathing node to learn it; everything a node does is in its tooltip.
 */
public class TalentScreen extends Screen {
	private static final int RING = 46;
	private float zoom = 1;
	private double panX;
	private double panY;

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
		int[] p = state == null || state.progress().length < 4 ? new int[4] : state.progress();
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
		if (level() < t.levelNeeded()) {
			return "mages_legacy.talent.state.level";
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
		return freePoints() < t.cost ? "mages_legacy.talent.state.no_points" : null;
	}

	private int cx() {
		return (int) (width / 2 + panX);
	}

	private int cy() {
		return (int) (height / 2 + 12 + panY);
	}

	private float scale() {
		// The whole web (out to the Awakenings' own talents) fits the screen at zoom 1
		float fit = Math.min(width, height - 50) / (2 * 5.9f * RING);
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
		// The header: race, level, free points
		Component head = Component.translatable("mages_legacy.screen.tree_header", Component.translatable(race.key()), level(), freePoints());
		LegacyUi.heading(graphics, head, width / 2, 8, LegacyUi.GOLD);
		graphics.drawCenteredString(font, Component.translatable("mages_legacy.screen.tree_hint").withStyle(ChatFormatting.ITALIC), width / 2,
				height - 12, LegacyUi.MUTED);
		if (hovered != null) {
			graphics.renderComponentTooltip(font, tooltip(hovered), mouseX, mouseY);
		}
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
				: Component.translatable("mages_legacy.talent.meta", kind, t.cost).withStyle(ChatFormatting.DARK_PURPLE));
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
		panX += dragX;
		panY += dragY;
		return true;
	}

	@Override
	public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
		zoom = Math.max(0.6f, Math.min(2f, zoom + (float) scrollY * 0.1f));
		return true;
	}

	@Override
	public boolean isPauseScreen() {
		return false;
	}
}
