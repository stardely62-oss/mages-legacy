package euf.mageslegacy.client;

import euf.mageslegacy.client.screen.LegacyUi;
import euf.mageslegacy.net.SyncPayload;
import euf.mageslegacy.race.Ability;
import euf.mageslegacy.race.Awakenings;
import euf.mageslegacy.race.Talent;
import euf.mageslegacy.race.Race;
import euf.mageslegacy.race.RaceState;
import java.util.List;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;

/**
 * Right of the hotbar: the race's abilities as stone slots with their keys (dark with the level they open at while
 * locked, a falling shade while recovering) and the Void bar beneath.
 */
public final class RaceHud {
	private static final int SLOT = 22;

	private RaceHud() {
	}

	public static void render(GuiGraphics graphics, DeltaTracker delta) {
		Minecraft mc = Minecraft.getInstance();
		SyncPayload state = ClientRace.state();
		Race race = ClientRace.race();
		if (state == null || race == null || mc.options.hideGui || mc.player == null) {
			return;
		}
		int width = graphics.guiWidth();
		int height = graphics.guiHeight();
		// The fixed keys, then the Awakenings on Z and Alt in the order they were learnt
		List<Ability> abilities = new java.util.ArrayList<>(Ability.bySlot(race));
		List<Integer> keys = new java.util.ArrayList<>();
		for (Ability a : abilities) {
			keys.add(a.slot);
		}
		for (int key : new int[] {Awakenings.FIRST_SLOT, Awakenings.SECOND_SLOT}) {
			Awakenings.at(state.talents(), key).ifPresent(a -> {
				abilities.add(a);
				keys.add(key);
			});
		}
		if (abilities.isEmpty()) {
			return;
		}
		Font font = mc.font;
		int color = LegacyUi.argb(race.color);
		int total = abilities.size() * (SLOT + 3) - 3;
		int x0 = width / 2 + 91 + 12;
		int y0 = height - SLOT - 9;
		for (int i = 0; i < abilities.size(); i++) {
			slot(graphics, font, abilities.get(i), keys.get(i), state, x0 + i * (SLOT + 3), y0, color);
		}
		// The Void beneath the slots
		int barY = height - 6;
		float fill = state.resource() / RaceState.MAX_RESOURCE;
		graphics.fill(x0 - 1, barY - 1, x0 + total + 1, barY + 3, LegacyUi.BLACK);
		graphics.fill(x0, barY, x0 + total, barY + 2, 0xFF1A1220);
		graphics.fill(x0, barY, x0 + Math.round(total * fill), barY + 2, color);
		String amount = String.valueOf((int) state.resource());
		if (race == Race.MERCENARY) {
			// No mana: the bar is the pinned contract; the number is the tokens, with a coin
			amount = String.valueOf(state.merc().length > 0 ? state.merc()[0] : 0);
			graphics.drawString(font, amount, x0 + total + 4, barY - 3, 0xFFE2B04A);
			mercenary(graphics, font, state, abilities, keys, x0, y0, total);
		} else {
			graphics.drawString(font, amount, x0 + total + 4, barY - 3, color);
		}
	}

	/**
	 * The Mercenary's extras: the brand's charges over R, the Verdict's blows, the Life Debt (owed, seconds, what its end
	 * would cost) and the banner's time over their slots, the Last Ransom's readiness, the pinned contract above the row,
	 * and a Nose for Profit's glint at the screen's edge.
	 */
	private static void mercenary(GuiGraphics graphics, Font font, SyncPayload state, List<Ability> abilities, List<Integer> keys, int x0, int y0, int total) {
		int[] m = state.merc();
		if (m.length < euf.mageslegacy.mercenary.Mercenary.HUD_SIZE) {
			return;
		}
		for (int i = 0; i < abilities.size(); i++) {
			Ability a = abilities.get(i);
			int x = x0 + i * (SLOT + 3);
			if (a == Ability.HUNTER_BRAND && m[1] > 0) {
				for (int k = 0; k < m[2]; k++) {
					int gx = x + 4 + k * 5;
					LegacyUi.diamond(graphics, gx, y0 - 3, 2, k < m[1] ? 0xFFFFD27A : 0xFF4A3420);
				}
			}
			if (a == Ability.GUILD_VERDICT && m[8] > 0) {
				small(graphics, font, m[7] + "/6", x + 1, y0 - 6, 0xFFFFD27A);
			}
			if (a == Ability.GUILD_LEGEND && m[9] > 0) {
				small(graphics, font, (m[9] + 19) / 20 + "s", x + 1, y0 - 6, 0xFFFFD27A);
			}
			if (a == Ability.LIFE_DEBT) {
				boolean ready = m[10] <= 0 && m[0] >= euf.mageslegacy.mercenary.Mercenary.RANSOM_TOKENS;
				LegacyUi.diamond(graphics, x + 4, y0 + SLOT - 4, 2, ready ? 0xFFFFD27A : 0xFF4A3420);
				if (m[10] > 0) {
					small(graphics, font, (m[10] + 59) / 60 + "m", x + 1, y0 + 1, 0xFFC8823C);
				}
				if (m[5] > 0) {
					// The Debt running: owed and what its end would cost, warned in red
					String line = String.format("%.1f", m[4] / 10f) + " · " + (m[5] + 19) / 20 + "s";
					String due = "-" + String.format("%.1f", m[6] / 10f);
					small(graphics, font, line, x - 2, y0 - 12, 0xFFFFD27A);
					small(graphics, font, due, x - 2, y0 - 6, 0xFFFF6A50);
				}
			}
		}
		// The pinned contract, small, above the row
		String note = state.note();
		if (note != null && !note.isEmpty()) {
			String[] p = note.split("\\|");
			if (p.length >= 5) {
				net.minecraft.nbt.CompoundTag c = new net.minecraft.nbt.CompoundTag();
				c.putString("kind", p[0]);
				c.putString("target", p[1]);
				c.putInt("amount", Integer.parseInt(p[3]));
				c.putString("job", p[4]);
				String what = euf.mageslegacy.client.screen.GuildBookScreen.describe(c).getString();
				String text;
				if (p.length >= 7) {
					// A mark to reach: how far, which way
					String[] dirs = {"n", "ne", "e", "se", "s", "sw", "w", "nw"};
					text = what + " · " + net.minecraft.network.chat.Component.translatable("mages_legacy.merc.book.guide", p[5],
							net.minecraft.network.chat.Component.translatable("mages_legacy.merc.dir." + dirs[Math.floorMod(Integer.parseInt(p[6]), 8)]))
							.getString();
				} else if ("hunt".equals(p[0]) || "deliver".equals(p[0])) {
					text = what.replaceAll(" ×\\d+$", "") + " " + p[2] + "/" + p[3];
				} else {
					text = what;
				}
				graphics.pose().pushPose();
				graphics.pose().translate(x0, y0 - 20, 0);
				graphics.pose().scale(0.75f, 0.75f, 1);
				graphics.drawString(font, text, 0, 0, 0xFFE2B04A);
				graphics.pose().popPose();
			}
		}
		// A Nose for Profit: a golden glint at the edge, on the quarry's side
		var ping = euf.mageslegacy.client.fx.MercFx.pingAt;
		int left = euf.mageslegacy.client.fx.MercFx.pingLeft;
		Minecraft mc = Minecraft.getInstance();
		if (ping != null && left > 0 && mc.player != null) {
			net.minecraft.world.phys.Vec3 d = ping.subtract(mc.player.getEyePosition());
			double yaw = Math.toDegrees(Math.atan2(-d.x, d.z));
			double rel = net.minecraft.util.Mth.wrapDegrees(yaw - mc.player.getYRot());
			int w = graphics.guiWidth();
			int h = graphics.guiHeight();
			double ang = Math.toRadians(rel);
			int cx = w / 2 + (int) (Math.sin(ang) * (w / 2 - 14));
			int cy = h / 2 - (int) (Math.cos(ang) * (h / 2 - 14));
			float a = Math.min(1, left / 10f);
			LegacyUi.glow(graphics, cx, cy, 12, 0xFFE2B04A, a);
			LegacyUi.diamond(graphics, cx, cy, 3, LegacyUi.alpha(0xFFFFE8A0, a));
		}
	}

	private static void small(GuiGraphics graphics, Font font, String text, int x, int y, int color) {
		graphics.pose().pushPose();
		graphics.pose().translate(x, y, 0);
		graphics.pose().scale(0.5f, 0.5f, 1);
		graphics.drawString(font, text, 0, 0, color);
		graphics.pose().popPose();
	}

	private static void slot(GuiGraphics graphics, Font font, Ability ability, int key, SyncPayload state, int x, int y, int color) {
		boolean locked = !ability.awakening() && state.level() < ability.level;
		int cooldown = state.cooldowns()[key];
		int total = Math.max(1, state.cooldownTotals()[key]);
		boolean affordable = state.resource() >= ability.cost;
		LegacyUi.panel(graphics, x, y, x + SLOT, y + SLOT, locked ? LegacyUi.RIM_DARK : cooldown > 0 ? LegacyUi.RIM : color);
		var icon = ability.awakening() ? awakeningIcon(ability) : abilityIcon(ability);
		if (LegacyUi.exists(icon)) {
			LegacyUi.icon(graphics, icon, x + 3, y + 3, 16, !locked && affordable);
		}
		if (locked) {
			graphics.fill(x + 1, y + 1, x + SLOT - 1, y + SLOT - 1, 0xB0000000);
			String level = String.valueOf(ability.level);
			graphics.drawString(font, level, x + (SLOT - font.width(level)) / 2, y + 7, LegacyUi.MUTED);
			return;
		}
		if (cooldown > 0) {
			int shade = Math.round((SLOT - 2) * cooldown / (float) total);
			graphics.fill(x + 1, y + SLOT - 1 - shade, x + SLOT - 1, y + SLOT - 1, 0xA0000000);
			String secs = String.valueOf((cooldown + 19) / 20);
			graphics.drawString(font, secs, x + (SLOT - font.width(secs)) / 2, y + 7, 0xFFFFFFFF);
		}
		LegacyUi.keyCap(graphics, keyShort(key), x + SLOT + 1, y + SLOT + 2, cooldown > 0 ? LegacyUi.MUTED : LegacyUi.GOLD);
		// Through Worlds' charges: a small gem for each, lit while ready
		if (ability == Ability.PASSAGE && state.chargesMax() > 1) {
			for (int i = 0; i < state.chargesMax(); i++) {
				int gx = x + 3 + i * 5;
				LegacyUi.diamond(graphics, gx, y + SLOT - 3, 2, i < state.charges() ? 0xFFE8DEFF : 0xFF3A2E48);
			}
		}
		// The Prince of the Night: the Last Breath's own readiness, apart from the swarm's cooldown: a heart, lit when it can save
		if (ability == Ability.NIGHT_PRINCE && state.progress().length > 21) {
			boolean ready = state.progress()[21] <= 0;
			LegacyUi.diamond(graphics, x + 4, y + SLOT - 4, 2, ready ? 0xFFE0283C : 0xFF3A1418);
			if (!ready) {
				String mins = (state.progress()[21] + 59) / 60 + "m";
				graphics.pose().pushPose();
				graphics.pose().translate(x + 1, y + 1, 0);
				graphics.pose().scale(0.5f, 0.5f, 1);
				graphics.drawString(font, mins, 0, 0, 0xFFB01226);
				graphics.pose().popPose();
			}
		}
		if (ability.awakening()) {
			// An Awakening's slot wears the crown's gems
			LegacyUi.diamond(graphics, x + SLOT / 2, y - 1, 2, color);
		}
	}

	/** An ability wears its talent's painted icon, so the bar and the tree always show the same picture. */
	public static net.minecraft.resources.ResourceLocation abilityIcon(Ability ability) {
		var own = LegacyUi.gui("talent/" + ability.id());
		return LegacyUi.exists(own) ? own : LegacyUi.gui("icon/ability_" + ability.id());
	}

	private static net.minecraft.resources.ResourceLocation awakeningIcon(Ability ability) {
		Talent talent = Awakenings.talentOf(ability);
		var own = LegacyUi.gui("talent/" + talent.id());
		return LegacyUi.exists(own) ? own : LegacyUi.gui("talent/" + talent.icon);
	}

	static Component keyName(int slot) {
		return MagesLegacyClient.ABILITY_KEYS[slot].getTranslatedKeyMessage();
	}

	/**
	 * The key actually bound to a slot, short enough for a key cap: a letter as it is, "Left Alt" as "Alt", a mouse
	 * button as M4; unbound, a dash.
	 */
	public static String keyShort(int slot) {
		var mapping = MagesLegacyClient.ABILITY_KEYS[slot];
		if (mapping.isUnbound()) {
			return "-";
		}
		String name = mapping.getTranslatedKeyMessage().getString().trim();
		if (name.length() <= 3) {
			return name;
		}
		String[] words = name.split(" ");
		String last = words[words.length - 1];
		if (last.matches("\\d+")) {
			return "M" + last;
		}
		return last.length() <= 4 ? last : last.substring(0, 3);
	}
}
