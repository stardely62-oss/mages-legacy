package euf.mageslegacy.client.screen;

import euf.mageslegacy.net.MercActionPayload;
import euf.mageslegacy.race.Race;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.SpawnEggItem;
import net.neoforged.neoforge.network.PacketDistributor;

/**
 * The Mage's Shop as the Mercenary sees it: the guild's book, bound in leather, open on two parchment pages, with two
 * ribbons to turn to: the Contracts (the board on the left, the jobs in hand on the right) and the Shop (the services
 * on the left, today's goods on the right, sorted by kind). Everything it shows (prices, pay, what is in the
 * inventory) is what the server sent; every press only asks, and the server answers with a fresh view.
 */
public class GuildBookScreen extends Screen {
	private static final int INK = 0xFF3B2A1A;
	private static final int INK_SOFT = 0xFF6E5638;
	private static final int COPPER = 0xFFA0602A;
	private static final int GOLD_INK = 0xFF8C6418;
	private static final int RED_INK = 0xFFA4321F;
	private static final int GREEN_INK = 0xFF3D6A28;
	private static final String[] TIERS = {"I", "II", "III", "IV"};
	private static final String[] CATEGORIES = {"all", "supplies", "materials", "potions", "books", "rare"};
	private static final String[] COMPASS = {"n", "ne", "e", "se", "s", "sw", "w", "nw"};

	private CompoundTag view;
	private int tab;
	private String category = "all";
	private final int[] scroll = new int[4];
	private final int[] scrollMax = new int[4];
	private int turned;
	private int ticks;
	/** After a press, the book waits for the server's answer before taking another (no double buying). */
	private int waiting;
	private final List<Hot> hots = new ArrayList<>();
	private boolean closedSent;

	/** A place on the page that does something when clicked, and what it says under the mouse. */
	private record Hot(int x0, int y0, int x1, int y1, Runnable action, List<Component> tip, ItemStack item) {
		boolean in(double x, double y) {
			return x >= x0 && x < x1 && y >= y0 && y < y1;
		}
	}

	public GuildBookScreen(CompoundTag view) {
		super(Component.translatable("mages_legacy.merc.book.title"));
		this.view = view;
	}

	public void update(CompoundTag view) {
		this.view = view;
		this.waiting = 0;
	}

	/** For the harness: which tab is open (0 contracts, 1 shop). */
	public void showTab(int tab) {
		this.tab = tab;
		this.turned = ticks;
	}

	public CompoundTag view() {
		return view;
	}

	@Override
	public boolean isPauseScreen() {
		return false;
	}

	@Override
	public void tick() {
		ticks++;
		if (waiting > 0) {
			waiting--;
		}
	}

	@Override
	public void removed() {
		if (!closedSent) {
			closedSent = true;
			PacketDistributor.sendToServer(new MercActionPayload(6, 0, ""));
		}
	}

	private void ask(int action, int id, String ware) {
		if (waiting > 0) {
			return;
		}
		waiting = 20;
		PacketDistributor.sendToServer(new MercActionPayload(action, id, ware));
		Minecraft.getInstance().getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.BOOK_PAGE_TURN, 1.2f, 0.6f));
	}

	// ---- layout ---------------------------------------------------------------------------------------------------

	private int bookW() {
		return Math.min(width - 12, 460);
	}

	private int bookH() {
		return Math.min(height - 30, 270);
	}

	private int bookX() {
		return (width - bookW()) / 2;
	}

	private int bookY() {
		return (height - bookH()) / 2 + 10;
	}

	@Override
	public void render(GuiGraphics g, int mouseX, int mouseY, float partial) {
		LegacyUi.background(g, width, height);
		hots.clear();
		int bx = bookX();
		int by = bookY();
		int bw = bookW();
		int bh = bookH();
		ribbons(g, bx, by, mouseX, mouseY);
		cover(g, bx, by, bw, bh);
		int px = bx + 10;
		int py = by + 8;
		int pw = bw / 2 - 14;
		int ph = bh - 16;
		int qx = bx + bw / 2 + 4;
		page(g, px, py, pw, ph, false);
		page(g, qx, py, pw, ph, true);
		header(g, px, qx, py, pw);
		// A page turned in fades in
		float fade = Math.min(1, (ticks - turned + partial) / 6f);
		int top = py + 22;
		int bottom = py + ph - 6;
		if (tab == 0) {
			board(g, px, top, pw, bottom, mouseX, mouseY, fade);
			hand(g, qx, top, pw, bottom, mouseX, mouseY, fade);
		} else {
			services(g, px, top, pw, bottom, mouseX, mouseY, fade);
			goods(g, qx, top, pw, bottom, mouseX, mouseY, fade);
		}
		for (Hot h : hots) {
			if (h.in(mouseX, mouseY)) {
				if (h.item() != null && !h.item().isEmpty()) {
					List<Component> lines = new ArrayList<>(Screen.getTooltipFromItem(Minecraft.getInstance(), h.item()));
					lines.addAll(h.tip());
					g.renderComponentTooltip(font, lines, mouseX, mouseY);
				} else if (!h.tip().isEmpty()) {
					g.renderComponentTooltip(font, h.tip(), mouseX, mouseY);
				}
				break;
			}
		}
	}

	@Override
	public void renderBackground(GuiGraphics g, int mouseX, int mouseY, float partial) {
	}

	private void ribbons(GuiGraphics g, int bx, int by, int mouseX, int mouseY) {
		String[] names = {"contracts", "shop"};
		for (int i = 0; i < 2; i++) {
			int x = bx + 28 + i * 104;
			int w = 96;
			boolean on = tab == i;
			boolean hot = mouseX >= x && mouseX < x + w && mouseY >= by - 18 && mouseY < by + 2;
			int y0 = by - (on ? 18 : 14);
			int body = on ? 0xFF8E2A1E : hot ? 0xFF6E2418 : 0xFF4E1C14;
			g.fill(x - 1, y0 - 1, x + w + 1, by + 6, LegacyUi.BLACK);
			g.fillGradient(x, y0, x + w, by + 6, body, LegacyUi.mix(body, 0xFF000000, 0.35f));
			g.fill(x, y0, x + w, y0 + 1, on ? LegacyUi.GOLD : 0xFF8A6A3A);
			Component label = Component.translatable("mages_legacy.merc.book.tab." + names[i]);
			g.drawString(font, label, x + (w - font.width(label)) / 2, y0 + 4, on ? 0xFFFFE8B0 : 0xFFD8C0A0, true);
			int tabIndex = i;
			hots.add(new Hot(x, y0, x + w, by + 2, () -> {
				if (tab != tabIndex) {
					tab = tabIndex;
					turned = ticks;
					Minecraft.getInstance().getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.BOOK_PAGE_TURN, 1, 0.8f));
				}
			}, List.of(), null));
		}
	}

	private void cover(GuiGraphics g, int bx, int by, int bw, int bh) {
		g.fill(bx - 2, by - 2, bx + bw + 2, by + bh + 2, LegacyUi.BLACK);
		g.fillGradient(bx, by, bx + bw, by + bh, 0xFF4A2C1A, 0xFF2A180E);
		LegacyUi.outline(g, bx + 2, by + 2, bx + bw - 2, by + bh - 2, 0xFF9A7434);
		LegacyUi.outline(g, bx + 4, by + 4, bx + bw - 4, by + bh - 4, 0x60D9A441);
		for (int[] c : new int[][] {{bx + 3, by + 3}, {bx + bw - 4, by + 3}, {bx + 3, by + bh - 4}, {bx + bw - 4, by + bh - 4}}) {
			LegacyUi.diamond(g, c[0], c[1], 3, 0xFFD9A441);
		}
	}

	private void page(GuiGraphics g, int x, int y, int w, int h, boolean right) {
		g.fillGradient(x, y, x + w, y + h, 0xFFEADFC6, 0xFFD9C8A4);
		// The spine's shadow and the outer edge's darkening
		for (int i = 0; i < 10; i++) {
			int a = (int) (70 * (1 - i / 10f));
			int sx = right ? x + i : x + w - 1 - i;
			g.fill(sx, y, sx + 1, y + h, a << 24);
		}
		g.fill(right ? x + w - 1 : x, y, right ? x + w : x + 1, y + h, 0x40000000);
		LegacyUi.outline(g, x, y, x + w, y + h, 0x30000000);
	}

	private void header(GuiGraphics g, int px, int qx, int py, int pw) {
		Component title = Component.translatable(tab == 0 ? "mages_legacy.merc.book.board" : "mages_legacy.merc.book.services");
		g.drawString(font, title, px + 10, py + 7, COPPER, false);
		Component right = Component.translatable(tab == 0 ? "mages_legacy.merc.book.hand" : "mages_legacy.merc.book.goods",
				tab == 0 ? view.getList("active", Tag.TAG_COMPOUND).size() + "/" + view.getInt("hand") : "");
		g.drawString(font, right, qx + 14, py + 7, COPPER, false);
		// The balance, top right: a coin and the number
		String tokens = String.valueOf(view.getInt("tokens"));
		int tw = font.width(tokens);
		int cx = qx + pw - 12 - tw;
		coin(g, cx - 12, py + 6);
		g.drawString(font, tokens, cx, py + 7, GOLD_INK, false);
		divider(g, px + 8, px + pw - 8, py + 18);
		divider(g, qx + 8, qx + pw - 8, py + 18);
	}

	private void divider(GuiGraphics g, int x0, int x1, int y) {
		for (int x = x0; x < x1; x++) {
			float t = Math.abs((x - x0) / (float) (x1 - x0) - 0.5f) * 2;
			g.fill(x, y, x + 1, y + 1, LegacyUi.alpha(0xFF8A6A3A, 1 - t * t));
		}
		LegacyUi.diamond(g, (x0 + x1) / 2, y, 2, 0xFFB8862E);
	}

	/** A small coin: a gold disc with a bright rim and a dark mark. */
	private void coin(GuiGraphics g, int x, int y) {
		g.fill(x + 2, y, x + 7, y + 9, 0xFF6E4A14);
		g.fill(x + 1, y + 1, x + 8, y + 8, 0xFF6E4A14);
		g.fill(x + 2, y + 1, x + 7, y + 8, 0xFFE2B04A);
		g.fill(x + 1, y + 2, x + 8, y + 7, 0xFFE2B04A);
		g.fill(x + 3, y + 2, x + 4, y + 7, 0xFFFFF2C4);
		g.fill(x + 5, y + 3, x + 6, y + 6, 0xFF9A6A1E);
		float glint = (float) Math.sin(LegacyUi.time() * 2.2);
		if (glint > 0.85f) {
			g.fill(x + 6, y, x + 7, y + 1, 0xFFFFFFFF);
		}
	}

	private int alpha(int color, float fade) {
		return LegacyUi.alpha(color, fade * ((color >>> 24) / 255f));
	}

	// ---- the Contracts ------------------------------------------------------------------------------------------------

	private void board(GuiGraphics g, int x, int top, int w, int bottom, int mouseX, int mouseY, float fade) {
		List<CompoundTag> offers = list("offers");
		int footer = 20;
		int listBottom = bottom - footer;
		boolean handFull = list("active").size() >= view.getInt("hand");
		int y = top - scroll[0];
		int cardH = 40;
		g.enableScissor(x, top, x + w, listBottom);
		if (offers.isEmpty()) {
			g.drawWordWrap(font, Component.translatable("mages_legacy.merc.book.empty_board"), x + 10, top + 4, w - 20, alpha(INK_SOFT, fade));
		}
		for (CompoundTag c : offers) {
			if (y + cardH > top && y < listBottom) {
				contractCard(g, c, x + 6, y, w - 12, cardH, mouseX, mouseY, fade, false);
				int bw2 = Math.max(46, font.width(Component.translatable("mages_legacy.merc.book.take")) + 10);
				int bx = x + w - 12 - bw2;
				int by = y + cardH - 16;
				boolean hot = !handFull && inside(mouseX, mouseY, bx, by, bw2, 13) && mouseY < listBottom;
				LegacyUi.button(g, bx, by, bw2, 13, !handFull && waiting == 0, hot, true, 0xFFC8823C);
				Component take = Component.translatable("mages_legacy.merc.book.take");
				g.drawString(font, take, bx + (bw2 - font.width(take)) / 2, by + 3, handFull ? 0xFF7A6A60 : 0xFFFFF2DC, false);
				int id = c.getInt("id");
				if (by >= top && by + 13 <= listBottom) {
					hots.add(new Hot(bx, by, bx + bw2, by + 13, () -> {
						if (!handFull) {
							ask(1, id, "");
						}
					}, handFull ? List.of(Component.translatable("mages_legacy.merc.book.hand_full").withStyle(ChatFormatting.RED)) : List.of(), null));
				}
			}
			y += cardH + 4;
		}
		g.disableScissor();
		scrollMax[0] = Math.max(0, y + scroll[0] - listBottom);
		// A fresh board for tokens
		int price = view.getInt("reroll");
		Component label = price <= 0 ? Component.translatable("mages_legacy.merc.book.reroll_free")
				: Component.translatable("mages_legacy.merc.book.reroll", price);
		int rw = font.width(label) + 14;
		int rx = x + (w - rw) / 2;
		int ry = bottom - 15;
		boolean afford = view.getInt("tokens") >= price;
		LegacyUi.button(g, rx, ry, rw, 13, afford && waiting == 0, inside(mouseX, mouseY, rx, ry, rw, 13), false, 0xFFC8823C);
		g.drawString(font, label, rx + 7, ry + 3, afford ? LegacyUi.TEXT : 0xFF8A7A70, false);
		hots.add(new Hot(rx, ry, rx + rw, ry + 13, () -> {
			if (afford) {
				ask(4, 0, "");
			}
		}, List.of(Component.translatable("mages_legacy.merc.book.reroll_tip").withStyle(ChatFormatting.GRAY)), null));
	}

	private void hand(GuiGraphics g, int x, int top, int w, int bottom, int mouseX, int mouseY, float fade) {
		List<CompoundTag> active = list("active");
		int footer = 22;
		int listBottom = bottom - footer;
		int y = top - scroll[1];
		int cardH = 52;
		g.enableScissor(x, top, x + w, listBottom);
		if (active.isEmpty()) {
			g.drawWordWrap(font, Component.translatable("mages_legacy.merc.book.empty_hand"), x + 14, top + 4, w - 24, alpha(INK_SOFT, fade));
		}
		for (int i = 0; i < active.size(); i++) {
			CompoundTag c = active.get(i);
			if (y + cardH > top && y < listBottom) {
				contractCard(g, c, x + 10, y, w - 18, cardH, mouseX, mouseY, fade, true);
				int id = c.getInt("id");
				int bx = x + w - 10;
				int by = y + cardH - 15;
				// Give up (a cross), pin (a star) and, for a delivery, hand over
				bx -= 14;
				iconButton(g, bx, by, "✕", RED_INK, mouseX, mouseY, () -> ask(2, id, ""),
						List.of(Component.translatable("mages_legacy.merc.book.abandon")), top, listBottom);
				if (i > 0) {
					bx -= 16;
					iconButton(g, bx, by, "★", GOLD_INK, mouseX, mouseY, () -> ask(7, id, ""),
							List.of(Component.translatable("mages_legacy.merc.book.pin")), top, listBottom);
				}
				if ("deliver".equals(c.getString("kind"))) {
					int have = c.getInt("have");
					Component give = Component.translatable("mages_legacy.merc.book.deliver", have);
					int dw = font.width(give) + 10;
					bx -= dw + 4;
					boolean can = have > 0;
					boolean hot = can && inside(mouseX, mouseY, bx, by, dw, 13);
					LegacyUi.button(g, bx, by, dw, 13, can && waiting == 0, hot, true, 0xFF6E9A3A);
					g.drawString(font, give, bx + 5, by + 3, can ? 0xFFFFF2DC : 0xFF7A6A60, false);
					if (by >= top && by + 13 <= listBottom) {
						int fx = bx;
						hots.add(new Hot(fx, by, fx + dw, by + 13, () -> {
							if (can) {
								ask(3, id, "");
							}
						}, List.of(Component.translatable("mages_legacy.merc.book.deliver_tip").withStyle(ChatFormatting.GRAY)), null));
					}
				}
			}
			y += cardH + 4;
		}
		g.disableScissor();
		scrollMax[1] = Math.max(0, y + scroll[1] - listBottom);
		// Footer: when the board and the stock turn over, the streak
		long next = view.getLong("nextDay");
		Component when = Component.translatable("mages_legacy.merc.book.next_day", clock(next));
		g.drawString(font, when, x + 14, bottom - 18, alpha(INK_SOFT, fade), false);
		int streak = view.getInt("streak");
		if (streak > 0) {
			g.drawString(font, Component.translatable("mages_legacy.merc.book.streak", Math.min(3, streak) * 10), x + 14, bottom - 8, alpha(GOLD_INK, fade),
					false);
		}
	}

	private void iconButton(GuiGraphics g, int x, int y, String glyph, int color, int mouseX, int mouseY, Runnable action, List<Component> tip, int top,
			int listBottom) {
		boolean hot = inside(mouseX, mouseY, x, y, 13, 13);
		g.fill(x, y, x + 13, y + 13, hot ? 0x40000000 : 0x20000000);
		LegacyUi.outline(g, x, y, x + 13, y + 13, hot ? color : 0x60000000);
		g.drawString(font, glyph, x + (13 - font.width(glyph)) / 2 + 1, y + 3, color, false);
		if (y >= top && y + 13 <= listBottom) {
			hots.add(new Hot(x, y, x + 13, y + 13, action, tip, null));
		}
	}

	/** One contract on parchment: its kind's picture, what to do, its tier and pay; in hand, a bar of how far along it is. */
	private void contractCard(GuiGraphics g, CompoundTag c, int x, int y, int w, int h, int mouseX, int mouseY, float fade, boolean inHand) {
		boolean elite = c.getBoolean("elite");
		boolean hot = inside(mouseX, mouseY, x, y, w, h);
		int frame = elite ? 0xFFB8862E : 0x50000000;
		g.fill(x, y, x + w, y + h, hot ? 0x22000000 : 0x14000000);
		LegacyUi.outline(g, x, y, x + w, y + h, alpha(frame, fade));
		if (elite) {
			// A golden corner and a wax seal for the elite ones
			LegacyUi.diamond(g, x + w - 6, y + 6, 4, 0xFF8E2A1E);
			LegacyUi.diamond(g, x + w - 6, y + 6, 2, 0xFFD9A441);
		}
		String kind = c.getString("kind");
		g.renderItem(icon(c), x + 4, y + 4);
		Component what = describe(c);
		int textX = x + 24;
		int textW = w - 30 - (elite ? 10 : 0);
		g.drawString(font, font.plainSubstrByWidth(what.getString(), textW), textX, y + 4, alpha(INK, fade), false);
		int tier = Math.max(1, Math.min(4, c.getInt("tier")));
		Component sub = Component.translatable("mages_legacy.merc.kind." + kind).append(" · ").append(Component.translatable("mages_legacy.merc.book.tier",
				TIERS[tier - 1]));
		if (elite) {
			sub = sub.copy().append(" · ").append(Component.translatable("mages_legacy.merc.book.elite"));
		}
		g.drawString(font, font.plainSubstrByWidth(sub.getString(), textW), textX, y + 14, alpha(elite ? GOLD_INK : INK_SOFT, fade), false);
		// The pay
		int payY = inHand ? y + 37 : y + 26;
		String xp = "+" + c.getInt("payXp");
		g.drawString(font, Component.translatable("mages_legacy.merc.book.pay_xp", xp), x + 6, payY, alpha(GREEN_INK, fade), false);
		int after = x + 10 + font.width(Component.translatable("mages_legacy.merc.book.pay_xp", xp));
		coin(g, after, payY - 1);
		g.drawString(font, String.valueOf(c.getInt("payTokens")), after + 11, payY, alpha(GOLD_INK, fade), false);
		if (inHand) {
			int progress = c.getInt("progress");
			int amount = Math.max(1, c.getInt("amount"));
			int barX = x + 6;
			int barW = w - 12;
			int barY = y + 26;
			g.fill(barX, barY, barX + barW, barY + 6, 0x40000000);
			int fill = Math.round(barW * Math.min(1, progress / (float) amount));
			g.fillGradient(barX, barY, barX + fill, barY + 6, 0xFFE2B04A, 0xFFA0602A);
			String count = progress + "/" + amount;
			Component extra = Component.empty();
			if ("explore".equals(kind)) {
				count = "";
				extra = c.contains("far") ? Component.translatable("mages_legacy.merc.book.guide", c.getInt("far"),
						Component.translatable("mages_legacy.merc.dir." + COMPASS[Math.floorMod(c.getInt("dir"), 8)]))
						: Component.translatable(c.getBoolean("armed") ? "mages_legacy.merc.book.explore_armed" : "mages_legacy.merc.book.explore_leave");
			}
			if (!count.isEmpty()) {
				g.drawString(font, count, barX + barW - font.width(count), barY - 9 + 0, alpha(INK_SOFT, fade), false);
			}
			if (!extra.getString().isEmpty()) {
				g.drawString(font, font.plainSubstrByWidth(extra.getString(), barW), barX, barY + 1, alpha(INK, fade * 0.9f), false);
			}
		}
		List<Component> tip = new ArrayList<>();
		tip.add(what.copy().withStyle(ChatFormatting.GOLD));
		tip.add(Component.translatable("mages_legacy.merc.kind." + kind + ".tip").withStyle(ChatFormatting.GRAY));
		if (elite) {
			tip.add(Component.translatable("mages_legacy.merc.book.elite_tip").withStyle(ChatFormatting.YELLOW));
		}
		hots.add(new Hot(x, y, x + w, y + h - 16, () -> {
		}, tip, null));
	}

	/** What a contract asks, in words: "Zombie ×10", "Iron Ingot ×12", "Desert". */
	public static Component describe(CompoundTag c) {
		String kind = c.getString("kind");
		ResourceLocation id = ResourceLocation.tryParse(c.getString("target"));
		int amount = c.getInt("amount");
		if (id == null) {
			return Component.literal(c.getString("target"));
		}
		return switch (kind) {
			case "hunt" -> EntityType.byString(id.toString()).map(t -> t.getDescription().copy()).orElse(Component.literal(id.getPath()))
					.append(" ×" + amount);
			case "deliver" -> BuiltInRegistries.ITEM.get(id).getDescription().copy().append(" ×" + amount);
			default -> Component.translatable("biome." + id.getNamespace() + "." + id.getPath());
		};
	}

	private static ItemStack icon(CompoundTag c) {
		ResourceLocation id = ResourceLocation.tryParse(c.getString("target"));
		if (id == null) {
			return new ItemStack(Items.PAPER);
		}
		return switch (c.getString("kind")) {
			case "hunt" -> {
				Optional<EntityType<?>> type = EntityType.byString(id.toString());
				SpawnEggItem egg = type.map(SpawnEggItem::byId).orElse(null);
				yield egg != null ? new ItemStack(egg) : new ItemStack(Items.IRON_SWORD);
			}
			case "deliver" -> new ItemStack(BuiltInRegistries.ITEM.get(id));
			default -> new ItemStack(Items.FILLED_MAP);
		};
	}

	// ---- the Shop ---------------------------------------------------------------------------------------------------

	private void services(GuiGraphics g, int x, int top, int w, int bottom, int mouseX, int mouseY, float fade) {
		int y = top - scroll[2];
		g.enableScissor(x, top, x + w, bottom);
		for (CompoundTag ware : list("wares")) {
			if (ware.getBoolean("service")) {
				wareRow(g, ware, x + 6, y, w - 14, mouseX, mouseY, fade, top, bottom);
				y += 26;
			}
		}
		// The legend: what the marks mean
		y += 6;
		g.drawWordWrap(font, Component.translatable("mages_legacy.merc.book.shop_note"), x + 10, y, w - 20, alpha(INK_SOFT, fade));
		y += font.split(Component.translatable("mages_legacy.merc.book.shop_note"), w - 20).size() * 9 + 4;
		g.disableScissor();
		scrollMax[2] = Math.max(0, y + scroll[2] - bottom);
	}

	private void goods(GuiGraphics g, int x, int top, int w, int bottom, int mouseX, int mouseY, float fade) {
		// Kinds of wares as small chips; a dot on a kind that has something new
		int cx = x + 12;
		int cy = top;
		for (String cat : CATEGORIES) {
			Component name = Component.translatable("mages_legacy.merc.cat." + cat);
			int cw = font.width(name) + 8;
			if (cx + cw > x + w - 8) {
				cx = x + 12;
				cy += 13;
			}
			boolean on = category.equals(cat);
			boolean hot = inside(mouseX, mouseY, cx, cy, cw, 11);
			g.fill(cx, cy, cx + cw, cy + 11, on ? 0xFFA0602A : hot ? 0x30000000 : 0x18000000);
			g.drawString(font, name, cx + 4, cy + 2, on ? 0xFFFFF2DC : INK, false);
			if (hasNew(cat)) {
				LegacyUi.diamond(g, cx + cw - 1, cy, 2, 0xFFD03A1E);
			}
			String pick = cat;
			hots.add(new Hot(cx, cy, cx + cw, cy + 11, () -> {
				category = pick;
				scroll[3] = 0;
			}, List.of(), null));
			cx += cw + 3;
		}
		int listTop = cy + 15;
		int y = listTop - scroll[3];
		g.enableScissor(x, listTop, x + w, bottom);
		int shown = 0;
		for (CompoundTag ware : list("wares")) {
			if (ware.getBoolean("service") || !"all".equals(category) && !category.equals(ware.getString("cat"))) {
				continue;
			}
			wareRow(g, ware, x + 10, y, w - 18, mouseX, mouseY, fade, listTop, bottom);
			y += 26;
			shown++;
		}
		if (shown == 0) {
			g.drawWordWrap(font, Component.translatable("mages_legacy.merc.book.no_goods"), x + 14, listTop + 4, w - 24, alpha(INK_SOFT, fade));
		}
		g.disableScissor();
		scrollMax[3] = Math.max(0, y + scroll[3] - bottom);
	}

	private boolean hasNew(String cat) {
		for (CompoundTag ware : list("wares")) {
			if (ware.getBoolean("new") && ("all".equals(cat) || cat.equals(ware.getString("cat")))) {
				return true;
			}
		}
		return false;
	}

	/** One ware: its picture, its name, its price (struck out and new for a deal), why it can't be bought if it can't. */
	private void wareRow(GuiGraphics g, CompoundTag ware, int x, int y, int w, int mouseX, int mouseY, float fade, int top, int bottom) {
		String id = ware.getString("id");
		boolean service = ware.getBoolean("service");
		ItemStack stack = ItemStack.parseOptional(Minecraft.getInstance().level.registryAccess(), ware.getCompound("icon"));
		int price = ware.getInt("price");
		boolean locked = ware.getBoolean("locked");
		boolean soldOut = ware.getBoolean("soldOut");
		boolean afford = view.getInt("tokens") >= price;
		boolean can = !locked && !soldOut && afford;
		boolean hot = inside(mouseX, mouseY, x, y, w, 23) && mouseY >= top && mouseY < bottom;
		g.fill(x, y, x + w, y + 23, hot && can ? 0x2A8A5A20 : 0x10000000);
		LegacyUi.outline(g, x, y, x + w, y + 23, hot ? 0x80A0602A : 0x30000000);
		g.renderItem(stack, x + 3, y + 3);
		if (!service) {
			g.renderItemDecorations(font, stack, x + 3, y + 3);
		}
		Component name = service ? Component.translatable("mages_legacy.shop." + id) : stack.getHoverName();
		int nameW = w - 60;
		g.drawString(font, font.plainSubstrByWidth(name.getString(), nameW), x + 23, y + 3, alpha(locked ? INK_SOFT : INK, fade), false);
		Component state = Component.empty();
		int stateColor = INK_SOFT;
		if (locked) {
			state = Component.translatable("mages_legacy.merc.book.locked", TIERS[Math.max(1, Math.min(4, ware.getInt("tier"))) - 1]);
		} else if (soldOut) {
			state = Component.translatable("mages_legacy.merc.book.sold_out");
		} else if (ware.getBoolean("deal")) {
			state = Component.translatable("mages_legacy.merc.book.deal");
			stateColor = RED_INK;
		} else if (ware.getInt("left") > 0) {
			state = Component.translatable("mages_legacy.merc.book.left", ware.getInt("left"));
		}
		if (ware.getBoolean("new")) {
			state = Component.translatable("mages_legacy.merc.book.new").append(state.getString().isEmpty() ? "" : " · " + state.getString());
			stateColor = 0xFFC03A1E;
		}
		g.drawString(font, state, x + 23, y + 13, alpha(stateColor, fade), false);
		// The price on the right
		String p = String.valueOf(price);
		int pw = font.width(p);
		int px = x + w - 6 - pw;
		g.drawString(font, p, px, y + 8, alpha(afford ? GOLD_INK : RED_INK, fade), false);
		coin(g, px - 11, y + 7);
		if (ware.getBoolean("deal") && ware.getInt("base") != price) {
			String base = String.valueOf(ware.getInt("base"));
			int bx = px - 14 - font.width(base);
			g.drawString(font, base, bx, y + 8, alpha(INK_SOFT, fade), false);
			g.fill(bx - 1, y + 12, bx + font.width(base) + 1, y + 13, alpha(RED_INK, fade));
		}
		if (y + 23 > top && y < bottom) {
			List<Component> tip = new ArrayList<>();
			if (service) {
				tip.add(name.copy().withStyle(ChatFormatting.GOLD));
				tip.add(Component.translatable("mages_legacy.shop." + id + ".desc").withStyle(ChatFormatting.GRAY));
			}
			tip.add(Component.translatable(locked ? "mages_legacy.merc.locked_ware" : soldOut ? "mages_legacy.merc.sold_out" : !afford
					? "mages_legacy.merc.no_tokens" : "mages_legacy.merc.book.buy", price).withStyle(can ? ChatFormatting.GREEN : ChatFormatting.RED));
			hots.add(new Hot(x, Math.max(y, top), x + w, Math.min(y + 23, bottom), () -> {
				if (can) {
					ask(5, 0, id);
				} else {
					Minecraft.getInstance().getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.VILLAGER_NO, 1, 0.5f));
				}
			}, tip, service ? null : stack));
		}
	}

	// ---- input ------------------------------------------------------------------------------------------------------

	@Override
	public boolean mouseClicked(double mouseX, double mouseY, int button) {
		if (button == 0) {
			for (Hot h : List.copyOf(hots)) {
				if (h.in(mouseX, mouseY)) {
					h.action().run();
					return true;
				}
			}
		}
		return super.mouseClicked(mouseX, mouseY, button);
	}

	@Override
	public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
		boolean right = mouseX >= width / 2.0;
		int i = tab * 2 + (right ? 1 : 0);
		scroll[i] = (int) Math.max(0, Math.min(scrollMax[i] + scroll[i], scroll[i] - scrollY * 14));
		return true;
	}

	@Override
	public boolean keyPressed(int key, int scan, int modifiers) {
		// The Shop's own key closes the book again
		if (euf.mageslegacy.client.MagesLegacyClient.ABILITY_KEYS[3].matches(key, scan)) {
			onClose();
			return true;
		}
		return super.keyPressed(key, scan, modifiers);
	}

	private List<CompoundTag> list(String key) {
		List<CompoundTag> out = new ArrayList<>();
		for (Tag t : view.getList(key, Tag.TAG_COMPOUND)) {
			out.add((CompoundTag) t);
		}
		return out;
	}

	private static boolean inside(double mx, double my, int x, int y, int w, int h) {
		return mx >= x && mx < x + w && my >= y && my < y + h;
	}

	/** Game ticks as minutes and seconds. */
	private static String clock(long ticks) {
		long s = Math.max(0, ticks / 20);
		return String.format("%d:%02d", s / 60, s % 60);
	}

	public static int accent() {
		return LegacyUi.argb(Race.MERCENARY.color);
	}
}
