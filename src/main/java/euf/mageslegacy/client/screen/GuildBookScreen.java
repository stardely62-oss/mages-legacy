package euf.mageslegacy.client.screen;

import euf.mageslegacy.net.MercActionPayload;
import java.util.ArrayList;
import java.util.Comparator;
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
 * The Mage's Shop as the Mercenary walks into it: a room of dark planks under a hanging sign; shelves of today's goods,
 * each with its price tag and the glow of its rarity; a cork board of the services pinned up as notes; the counter with
 * the lantern, the hooded shopkeeper behind it, the pouch of tokens, and the book of orders lying open. A click on the
 * book opens it on two pages: the board of offered jobs and the jobs in hand. Everything shown (prices, pay, what is in
 * the inventory) is what the server sent; every press only asks, and the server answers with a fresh view.
 */
public class GuildBookScreen extends Screen {
	private static final int INK = 0xFF3B2A1A;
	private static final int INK_SOFT = 0xFF6E5638;
	private static final int COPPER = 0xFFA0602A;
	private static final int GOLD_INK = 0xFF8C6418;
	private static final int RED_INK = 0xFFA4321F;
	private static final int GREEN_INK = 0xFF3D6A28;
	private static final String[] TIERS = {"I", "II", "III", "IV"};
	private static final String[] COMPASS = {"n", "ne", "e", "se", "s", "sw", "w", "nw"};
	private static final String[] RARITIES = {"common", "uncommon", "rare", "epic", "legendary"};
	private static final int[] RARITY_COLORS = {0xFFC8C8C8, 0xFF74D46A, 0xFF5FA8FF, 0xFFC27CFF, 0xFFFFB43C};
	private static final ResourceLocation SIGN = LegacyUi.gui("merc_shop/sign");
	private static final ResourceLocation BOOK = LegacyUi.gui("merc_shop/book");
	private static final ResourceLocation LANTERN = LegacyUi.gui("merc_shop/lantern");
	private static final ResourceLocation KEEPER = LegacyUi.gui("merc_shop/keeper");
	private static final ResourceLocation POUCH = LegacyUi.gui("merc_shop/pouch");
	private static final ResourceLocation TAG = LegacyUi.gui("merc_shop/tag");
	private static final ResourceLocation NOTE = LegacyUi.gui("merc_shop/note");

	/** 0: the book of orders, 1: the shop itself. */
	private int mode = 1;
	private CompoundTag view;
	private final int[] scroll = new int[2];
	private final int[] scrollMax = new int[2];
	private int turned;
	private int ticks;
	private int waiting;
	private final List<Hot> hots = new ArrayList<>();
	private boolean closedSent;

	/** A place that does something when clicked, and what it says under the mouse. */
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

	/** For the harness: 0 the book of orders, 1 the shop. */
	public void showTab(int tab) {
		this.mode = tab;
		this.turned = ticks;
	}

	public int mode() {
		return mode;
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

	private void turn(int to) {
		mode = to;
		turned = ticks;
		Minecraft.getInstance().getSoundManager().play(SimpleSoundInstance.forUI(to == 0 ? SoundEvents.BOOK_PAGE_TURN : SoundEvents.WOODEN_DOOR_OPEN, 1, 0.8f));
	}

	@Override
	public void renderBackground(GuiGraphics g, int mouseX, int mouseY, float partial) {
	}

	@Override
	public void render(GuiGraphics g, int mouseX, int mouseY, float partial) {
		LegacyUi.background(g, width, height);
		hots.clear();
		float fade = Math.min(1, (ticks - turned + partial) / 6f);
		if (mode == 1) {
			shop(g, mouseX, mouseY, partial);
		} else {
			book(g, mouseX, mouseY, fade);
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

	// ---- the shop ---------------------------------------------------------------------------------------------------

	private int sw() {
		return Math.min(width - 10, 470);
	}

	private int sh() {
		return Math.min(height - 10, 266);
	}

	private void shop(GuiGraphics g, int mouseX, int mouseY, float partial) {
		int sw = sw();
		int sh = sh();
		int sx = (width - sw) / 2;
		int sy = (height - sh) / 2;
		wall(g, sx, sy, sw, sh);
		// The sign on two chains, the shop's name on it
		int signW = Math.min(192, sw * 2 / 5);
		int signH = signW * 26 / 96;
		int signX = sx + (sw - signW) / 2;
		int signY = sy + 6;
		g.fill(signX + signW * 10 / 96, sy, signX + signW * 10 / 96 + 1, signY + 2, 0xFF6A6A70);
		g.fill(signX + signW * 85 / 96, sy, signX + signW * 85 / 96 + 1, signY + 2, 0xFF6A6A70);
		g.blit(SIGN, signX, signY, signW, signH, 0, 0, 96, 26, 96, 26);
		Component name = Component.translatable("mages_legacy.merc.shop.name");
		g.drawString(font, name, signX + (signW - font.width(name)) / 2, signY + signH / 2 - 3, 0xFFFFD98A, true);
		int top = signY + signH + 6;
		int floor = sy + sh - 16;
		int shelfRight = sx + sw * 3 / 5;
		shelves(g, sx + 8, top, shelfRight, floor - 2, mouseX, mouseY);
		int rx0 = shelfRight + 8;
		int rx1 = sx + sw - 8;
		int boardBottom = top + (floor - top) * 9 / 20;
		services(g, rx0, top, rx1, boardBottom, mouseX, mouseY);
		counter(g, rx0, boardBottom + 4, rx1, floor, mouseX, mouseY, partial);
		footer(g, sx + 8, floor + 3, sx + sw - 8);
	}

	/** Dark planks, a beam along the top, the floorboards. */
	private void wall(GuiGraphics g, int x, int y, int w, int h) {
		g.fill(x - 2, y - 2, x + w + 2, y + h + 2, 0xFF0C0806);
		int plank = 14;
		for (int px = x, i = 0; px < x + w; px += plank, i++) {
			int c = (i % 3 == 0) ? 0xFF3A2416 : (i % 3 == 1) ? 0xFF432A1A : 0xFF3E2718;
			g.fill(px, y, Math.min(x + w, px + plank), y + h, c);
			g.fill(px, y, px + 1, y + h, 0xFF24160E);
			// knots and grain, fixed per plank
			int knot = y + (int) (LegacyUi.hash(i, 7) * (h - 20)) + 10;
			g.fill(px + 5, knot, px + 8, knot + 2, 0xFF2C1B10);
		}
		g.fill(x, y, x + w, y + 4, 0xFF24160E);
		g.fillGradient(x, y + 4, x + w, y + 40, 0x50000000, 0x00000000);
		g.fill(x, y + h - 14, x + w, y + h, 0xFF2A1A10);
		for (int px = x; px < x + w; px += 22) {
			g.fill(px, y + h - 14, px + 1, y + h, 0xFF1A100A);
		}
		g.fill(x, y + h - 14, x + w, y + h - 13, 0xFF5A3A22);
		LegacyUi.outline(g, x, y, x + w, y + h, 0xFF8A6A3A);
	}

	private List<CompoundTag> wares(boolean services) {
		List<CompoundTag> out = new ArrayList<>();
		for (CompoundTag w : list("wares")) {
			if (w.getBoolean("service") == services) {
				out.add(w);
			}
		}
		// Shelves sorted by kind, then the rarest first within it
		out.sort(Comparator.comparing((CompoundTag w) -> w.getString("cat")).thenComparing(w -> -rarity(w)));
		return out;
	}

	private static int rarity(CompoundTag ware) {
		String r = ware.getString("rarity");
		for (int i = 0; i < RARITIES.length; i++) {
			if (RARITIES[i].equals(r)) {
				return i;
			}
		}
		return 0;
	}

	/** The shelving: a frame, three shelves, today's goods standing on them with their tags hanging below. */
	private void shelves(GuiGraphics g, int x0, int y0, int x1, int y1, int mouseX, int mouseY) {
		g.fill(x0 - 1, y0 - 1, x1 + 1, y1 + 1, 0xFF120A06);
		g.fill(x0, y0, x1, y1, 0xFF22150D);
		g.fill(x0, y0, x0 + 4, y1, 0xFF5C3A22);
		g.fill(x1 - 4, y0, x1, y1, 0xFF5C3A22);
		g.fill(x0, y0, x1, y0 + 4, 0xFF5C3A22);
		List<CompoundTag> goods = wares(false);
		int rows = 3;
		int cols = Math.max(4, (goods.size() + rows - 1) / rows);
		int rowH = (y1 - y0 - 4) / rows;
		int cellW = (x1 - x0 - 8) / cols;
		int tokens = view.getInt("tokens");
		for (int r = 0; r < rows; r++) {
			int plankY = y0 + 4 + rowH * (r + 1) - 12;
			g.fill(x0 + 4, plankY, x1 - 4, plankY + 4, 0xFF7A5030);
			g.fill(x0 + 4, plankY, x1 - 4, plankY + 1, 0xFF9A6A40);
			g.fill(x0 + 4, plankY + 4, x1 - 4, plankY + 5, 0xFF1A100A);
			for (int c = 0; c < cols; c++) {
				int i = r * cols + c;
				if (i >= goods.size()) {
					continue;
				}
				CompoundTag ware = goods.get(i);
				int cx = x0 + 4 + c * cellW + cellW / 2;
				ware(g, ware, cx, plankY, cellW, tokens, mouseX, mouseY);
			}
		}
	}

	/** One ware on its shelf: the glow of its rarity, the thing itself, the tag with its price; sold out, a deal. */
	private void ware(GuiGraphics g, CompoundTag ware, int cx, int plankY, int cellW, int tokens, int mouseX, int mouseY) {
		int rar = rarity(ware);
		int color = RARITY_COLORS[rar];
		ItemStack stack = ItemStack.parseOptional(Minecraft.getInstance().level.registryAccess(), ware.getCompound("icon"));
		int price = ware.getInt("price");
		int left = ware.getInt("left");
		boolean afford = tokens >= price;
		boolean can = left > 0 && afford;
		int size = 24;
		int ix = cx - size / 2;
		int iy = plankY - size;
		boolean hot = mouseX >= cx - cellW / 2 && mouseX < cx + cellW / 2 && mouseY >= iy - 4 && mouseY < plankY + 16;
		if (rar >= 2) {
			float pulse = 0.55f + 0.25f * (float) Math.sin(LegacyUi.time() * 2 + cx);
			LegacyUi.glow(g, cx, iy + size / 2, 14 + rar * 2, color, pulse);
		}
		if (hot) {
			LegacyUi.glow(g, cx, iy + size / 2, 16, 0xFFFFE8A0, 0.6f);
		}
		g.pose().pushPose();
		g.pose().translate(ix + (hot ? 0 : 0), iy - (hot ? 1 : 0), 0);
		g.pose().scale(1.5f, 1.5f, 1);
		g.renderItem(stack, 0, 0);
		g.pose().popPose();
		if (stack.getCount() > 1) {
			String n = "×" + stack.getCount();
			small(g, n, ix + size - (int) (font.width(n) * 0.75f) + 2, plankY - 7, 0xFFFFE8B0);
		}
		if (left <= 0) {
			g.fill(ix - 2, iy - 2, ix + size + 2, iy + size, 0xA0100808);
			Component sold = Component.translatable("mages_legacy.merc.book.sold_out");
			g.drawString(font, sold, cx - font.width(sold) / 2, iy + 8, 0xFFE07060, true);
		}
		if (ware.getBoolean("deal")) {
			g.fill(ix + size - 8, iy - 4, ix + size + 6, iy + 4, 0xFFA4321F);
			small(g, "-30%", ix + size - 7, iy - 2, 0xFFFFE0C0);
		}
		// The tag, hanging under the shelf: a gem of the rarity, the price
		int tagX = cx - 12;
		int tagY = plankY + 6;
		g.blit(TAG, tagX, tagY, 24, 11, 0, 0, 24, 11, 24, 11);
		LegacyUi.diamond(g, tagX + 7, tagY + 5, 2, color);
		String p = String.valueOf(price);
		small(g, p, tagX + 11, tagY + 3, afford ? 0xFF5A3A10 : RED_INK);
		if (hot) {
			LegacyUi.outline(g, cx - cellW / 2 + 1, iy - 4, cx + cellW / 2 - 1, plankY + 18, 0x80FFE0A0);
		}
		List<Component> tip = new ArrayList<>();
		tip.add(Component.translatable("mages_legacy.merc.rarity." + RARITIES[rar]).withStyle(s -> s.withColor(color & 0xFFFFFF)));
		tip.add(Component.translatable("mages_legacy.merc.cat." + ware.getString("cat")).withStyle(ChatFormatting.DARK_GRAY));
		if ("books".equals(ware.getString("cat"))) {
			tip.add(Component.translatable("mages_legacy.merc.shop.book_tip." + RARITIES[rar]).withStyle(ChatFormatting.GRAY));
		}
		tip.add(Component.translatable("mages_legacy.merc.shop.left", left, ware.getInt("stock")).withStyle(ChatFormatting.GRAY));
		tip.add(Component.translatable(left <= 0 ? "mages_legacy.merc.sold_out" : !afford ? "mages_legacy.merc.no_tokens" : "mages_legacy.merc.book.buy",
				price).withStyle(can ? ChatFormatting.GREEN : ChatFormatting.RED));
		String id = ware.getString("id");
		hots.add(new Hot(cx - cellW / 2, iy - 4, cx + cellW / 2, plankY + 18, () -> buy(id, can), tip, stack));
	}

	private void buy(String id, boolean can) {
		if (can) {
			ask(5, 0, id);
		} else {
			Minecraft.getInstance().getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.VILLAGER_NO, 1, 0.5f));
		}
	}

	/** The services' board: cork in a frame, today's services pinned up as notes, a pin of each one's rarity. */
	private void services(GuiGraphics g, int x0, int y0, int x1, int y1, int mouseX, int mouseY) {
		g.fill(x0 - 1, y0 - 1, x1 + 1, y1 + 1, 0xFF120A06);
		g.fill(x0, y0, x1, y1, 0xFF5C3A22);
		g.fill(x0 + 3, y0 + 3, x1 - 3, y1 - 3, 0xFF8A6238);
		for (int i = 0; i < 90; i++) {
			int px = x0 + 4 + (int) (LegacyUi.hash(i, 3) * (x1 - x0 - 8));
			int py = y0 + 4 + (int) (LegacyUi.hash(i, 4) * (y1 - y0 - 8));
			g.fill(px, py, px + 1, py + 1, 0xFF6E4C2C);
		}
		Component title = Component.translatable("mages_legacy.merc.shop.services");
		small(g, title.getString(), x0 + 6, y0 + 5, 0xFFFFE0A8);
		List<CompoundTag> services = wares(true);
		int cols = 2;
		int top = y0 + 12;
		int cellW = (x1 - x0 - 8) / cols;
		int cellH = (y1 - top - 4) / 2;
		int tokens = view.getInt("tokens");
		for (int i = 0; i < Math.min(4, services.size()); i++) {
			CompoundTag s = services.get(i);
			int nx = x0 + 4 + (i % cols) * cellW + 2;
			int ny = top + (i / cols) * cellH + 2;
			int nw = cellW - 4;
			int nh = cellH - 4;
			int rar = rarity(s);
			boolean hot = mouseX >= nx && mouseX < nx + nw && mouseY >= ny && mouseY < ny + nh;
			g.blit(NOTE, nx, ny, nw, nh, 0, 0, 48, 34, 48, 34);
			if (hot) {
				g.fill(nx, ny, nx + nw, ny + nh, 0x20FFF0C0);
			}
			LegacyUi.diamond(g, nx + nw / 2, ny + 2, 2, RARITY_COLORS[rar]);
			ItemStack stack = ItemStack.parseOptional(Minecraft.getInstance().level.registryAccess(), s.getCompound("icon"));
			g.renderItem(stack, nx + 3, ny + 5);
			String id = s.getString("id");
			Component name = Component.translatable("mages_legacy.shop." + id);
			List<net.minecraft.util.FormattedCharSequence> lines = font.split(name, (nw - 24) * 4 / 3);
			for (int k = 0; k < Math.min(2, lines.size()); k++) {
				g.pose().pushPose();
				g.pose().translate(nx + 21, ny + 6 + k * 7, 0);
				g.pose().scale(0.75f, 0.75f, 1);
				g.drawString(font, lines.get(k), 0, 0, INK, false);
				g.pose().popPose();
			}
			int price = s.getInt("price");
			int left = s.getInt("left");
			boolean afford = tokens >= price;
			String p = left <= 0 ? Component.translatable("mages_legacy.merc.book.sold_out").getString() : price + "";
			small(g, p, nx + nw - 4 - (int) (font.width(p) * 0.75f), ny + nh - 8, left <= 0 ? INK_SOFT : afford ? GOLD_INK : RED_INK);
			List<Component> tip = new ArrayList<>();
			tip.add(name.copy().withStyle(s2 -> s2.withColor(RARITY_COLORS[rar] & 0xFFFFFF)));
			tip.add(Component.translatable("mages_legacy.merc.rarity." + RARITIES[rar]).withStyle(s2 -> s2.withColor(RARITY_COLORS[rar] & 0xFFFFFF)));
			tip.add(Component.translatable("mages_legacy.shop." + id + ".desc").withStyle(ChatFormatting.GRAY));
			boolean can = left > 0 && afford;
			tip.add(Component.translatable(left <= 0 ? "mages_legacy.merc.sold_out" : !afford ? "mages_legacy.merc.no_tokens" : "mages_legacy.merc.book.buy",
					price).withStyle(can ? ChatFormatting.GREEN : ChatFormatting.RED));
			hots.add(new Hot(nx, ny, nx + nw, ny + nh, () -> buy(id, can), tip, null));
		}
	}

	/** The counter: the lantern, the keeper behind it, the book of orders lying open, the pouch of tokens. */
	private void counter(GuiGraphics g, int x0, int y0, int x1, int y1, int mouseX, int mouseY, float partial) {
		int w = x1 - x0;
		int topY = y0 + (y1 - y0) * 11 / 20;
		// The keeper, half hidden by the counter; the eyes blink now and then
		int kw = Math.min(80, w * 2 / 5);
		int kh = kw * 46 / 40;
		int kx = x0 + w - kw - 6;
		int ky = topY - kh * 3 / 4;
		g.blit(KEEPER, kx, ky, kw, kh, 0, 0, 40, 46, 40, 46);
		float t = LegacyUi.time();
		if ((t % 5) > 4.85f) {
			g.fill(kx + kw * 14 / 40, ky + kh * 15 / 46, kx + kw * 26 / 40, ky + kh * 17 / 46, 0xFF0C080A);
		}
		// The lantern on its hook, the light flickering round it
		int lx = x0 + 10;
		int ly = y0;
		float flicker = 0.7f + 0.15f * (float) Math.sin(t * 7) + 0.1f * (float) Math.sin(t * 13.7);
		LegacyUi.glow(g, lx + 6, ly + 18, 30, 0xFFFFB050, flicker);
		g.blit(LANTERN, lx, ly, 12, 26, 0, 0, 12, 26, 12, 26);
		// The counter top and its front
		g.fill(x0, topY, x1, y1, 0xFF4A2E1A);
		g.fill(x0, topY, x1, topY + 4, 0xFF8A5A34);
		g.fill(x0, topY, x1, topY + 1, 0xFFB07A48);
		g.fill(x0, topY + 4, x1, topY + 5, 0xFF1A100A);
		for (int px = x0 + 8; px < x1; px += 24) {
			g.fill(px, topY + 8, px + 16, y1 - 4, 0xFF3E2616);
			LegacyUi.outline(g, px, topY + 8, px + 16, y1 - 4, 0xFF2A180E);
		}
		g.fill(x0, topY + 6, x1, topY + 7, 0xFFC8823C);
		// The book of orders lying open on the counter
		int bw = Math.min(96, w / 2);
		int bh = bw * 40 / 64;
		int bx = x0 + 16;
		int by = topY - bh + 6;
		boolean hot = mouseX >= bx && mouseX < bx + bw && mouseY >= by && mouseY < by + bh;
		LegacyUi.glow(g, bx + bw / 2, by + bh / 2, bw / 2 + 4, 0xFFFFD27A, hot ? 0.9f : 0.35f + 0.15f * (float) Math.sin(t * 2));
		g.blit(BOOK, bx, by - (hot ? 2 : 0), bw, bh, 0, 0, 64, 40, 64, 40);
		int inHand = view.getList("active", Tag.TAG_COMPOUND).size();
		Component label = Component.translatable("mages_legacy.merc.shop.orders", inHand, view.getInt("hand"));
		g.drawString(font, label, bx + (bw - font.width(label)) / 2, y1 - 11, 0xFFFFE0A8, true);
		hots.add(new Hot(bx, by - 2, bx + bw, by + bh, () -> turn(0), List.of(Component.translatable("mages_legacy.merc.shop.orders_tip")
				.withStyle(ChatFormatting.GRAY)), null));
		// The pouch: the balance
		String tokens = String.valueOf(view.getInt("tokens"));
		int px = x0 + 16 + Math.min(96, w / 2) + 4;
		int py = topY - 15;
		g.blit(POUCH, px, py, 16, 16, 0, 0, 16, 16, 16, 16);
		g.drawString(font, tokens, px + 18, py + 5, 0xFFFFD27A, true);
	}

	/** Under the floorboards' edge: when the stock turns over, the odds of each rarity at this level, bought luck. */
	private void footer(GuiGraphics g, int x0, int y, int x1) {
		Component when = Component.translatable("mages_legacy.merc.book.next_day", clock(view.getLong("nextDay")));
		small(g, when.getString(), x0, y + 1, 0xFFD8C0A0);
		int[] odds = view.getIntArray("odds");
		int x = x0 + (int) (font.width(when) * 0.75f) + 10;
		small(g, Component.translatable("mages_legacy.merc.shop.odds").getString(), x, y + 1, 0xFFD8C0A0);
		x += (int) (font.width(Component.translatable("mages_legacy.merc.shop.odds")) * 0.75f) + 4;
		for (int i = 0; i < Math.min(odds.length, RARITIES.length); i++) {
			String s = Component.translatable("mages_legacy.merc.rarity.short." + RARITIES[i]).getString() + " " + odds[i] + "%";
			small(g, s, x, y + 1, RARITY_COLORS[i]);
			x += (int) (font.width(s) * 0.75f) + 6;
		}
		int fortune = view.getInt("fortune");
		int lucky = view.getInt("lucky");
		if (fortune + lucky > 0) {
			Component luck = Component.translatable("mages_legacy.merc.shop.luck", fortune, lucky);
			small(g, luck.getString(), x1 - (int) (font.width(luck) * 0.75f), y + 1, 0xFFFFD27A);
		}
	}

	private void small(GuiGraphics g, String text, int x, int y, int color) {
		g.pose().pushPose();
		g.pose().translate(x, y, 0);
		g.pose().scale(0.75f, 0.75f, 1);
		g.drawString(font, text, 0, 0, color, false);
		g.pose().popPose();
	}

	// ---- the book of orders -----------------------------------------------------------------------------------------

	private void book(GuiGraphics g, int mouseX, int mouseY, float fade) {
		int bw = Math.min(width - 12, 470);
		int bh = Math.min(height - 30, 270);
		int bx = (width - bw) / 2;
		int by = (height - bh) / 2 + 10;
		// The ribbon back to the shop
		Component back = Component.translatable("mages_legacy.merc.book.back");
		int rw = font.width(back) + 16;
		boolean hotBack = mouseX >= bx + 24 && mouseX < bx + 24 + rw && mouseY >= by - 18 && mouseY < by + 2;
		g.fill(bx + 23, by - 19, bx + 25 + rw, by + 6, LegacyUi.BLACK);
		g.fillGradient(bx + 24, by - 18, bx + 24 + rw, by + 6, hotBack ? 0xFF8E2A1E : 0xFF6E2418, 0xFF4E1C14);
		g.drawString(font, back, bx + 32, by - 14, 0xFFFFE8B0, true);
		hots.add(new Hot(bx + 24, by - 18, bx + 24 + rw, by + 2, () -> turn(1), List.of(), null));
		g.fill(bx - 2, by - 2, bx + bw + 2, by + bh + 2, LegacyUi.BLACK);
		g.fillGradient(bx, by, bx + bw, by + bh, 0xFF5A1E14, 0xFF34100A);
		LegacyUi.outline(g, bx + 2, by + 2, bx + bw - 2, by + bh - 2, 0xFF9A7434);
		int px = bx + 10;
		int py = by + 8;
		int pw = bw / 2 - 14;
		int ph = bh - 16;
		int qx = bx + bw / 2 + 4;
		page(g, px, py, pw, ph, false);
		page(g, qx, py, pw, ph, true);
		g.drawString(font, Component.translatable("mages_legacy.merc.book.board"), px + 10, py + 7, COPPER, false);
		g.drawString(font, Component.translatable("mages_legacy.merc.book.hand", list("active").size() + "/" + view.getInt("hand")), qx + 14, py + 7,
				COPPER, false);
		String tokens = String.valueOf(view.getInt("tokens"));
		int cx = qx + pw - 12 - font.width(tokens);
		coin(g, cx - 12, py + 6);
		g.drawString(font, tokens, cx, py + 7, GOLD_INK, false);
		divider(g, px + 8, px + pw - 8, py + 18);
		divider(g, qx + 8, qx + pw - 8, py + 18);
		int top = py + 22;
		int bottom = py + ph - 6;
		board(g, px, top, pw, bottom, mouseX, mouseY, fade);
		hand(g, qx, top, pw, bottom, mouseX, mouseY, fade);
	}

	private void page(GuiGraphics g, int x, int y, int w, int h, boolean right) {
		g.fillGradient(x, y, x + w, y + h, 0xFFEADFC6, 0xFFD9C8A4);
		for (int i = 0; i < 10; i++) {
			int a = (int) (70 * (1 - i / 10f));
			int sx = right ? x + i : x + w - 1 - i;
			g.fill(sx, y, sx + 1, y + h, a << 24);
		}
		LegacyUi.outline(g, x, y, x + w, y + h, 0x30000000);
	}

	private void divider(GuiGraphics g, int x0, int x1, int y) {
		for (int x = x0; x < x1; x++) {
			float t = Math.abs((x - x0) / (float) (x1 - x0) - 0.5f) * 2;
			g.fill(x, y, x + 1, y + 1, LegacyUi.alpha(0xFF8A6A3A, 1 - t * t));
		}
		LegacyUi.diamond(g, (x0 + x1) / 2, y, 2, 0xFFB8862E);
	}

	private void coin(GuiGraphics g, int x, int y) {
		g.fill(x + 2, y, x + 7, y + 9, 0xFF6E4A14);
		g.fill(x + 1, y + 1, x + 8, y + 8, 0xFF6E4A14);
		g.fill(x + 2, y + 1, x + 7, y + 8, 0xFFE2B04A);
		g.fill(x + 1, y + 2, x + 8, y + 7, 0xFFE2B04A);
		g.fill(x + 3, y + 2, x + 4, y + 7, 0xFFFFF2C4);
		g.fill(x + 5, y + 3, x + 6, y + 6, 0xFF9A6A1E);
	}

	private int alpha(int color, float fade) {
		return LegacyUi.alpha(color, fade * ((color >>> 24) / 255f));
	}

	private void board(GuiGraphics g, int x, int top, int w, int bottom, int mouseX, int mouseY, float fade) {
		List<CompoundTag> offers = list("offers");
		int listBottom = bottom - 20;
		boolean handFull = list("active").size() >= view.getInt("hand");
		int y = top - scroll[0];
		int cardH = 56;
		g.enableScissor(x, top, x + w, listBottom);
		if (offers.isEmpty()) {
			g.drawWordWrap(font, Component.translatable("mages_legacy.merc.book.empty_board"), x + 10, top + 4, w - 20, alpha(INK_SOFT, fade));
		}
		for (CompoundTag c : offers) {
			if (y + cardH > top && y < listBottom) {
				card(g, c, x + 6, y, w - 12, cardH, mouseX, mouseY, fade, false, top, listBottom);
				Component take = Component.translatable("mages_legacy.merc.book.take");
				int bw2 = Math.max(46, font.width(take) + 10);
				int bx = x + w - 12 - bw2;
				int by = y + cardH - 15;
				boolean hot = !handFull && inside(mouseX, mouseY, bx, by, bw2, 13) && mouseY < listBottom;
				LegacyUi.button(g, bx, by, bw2, 13, !handFull && waiting == 0, hot, true, 0xFFC8823C);
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
		int price = view.getInt("reroll");
		Component label = price <= 0 ? Component.translatable("mages_legacy.merc.book.reroll_free") : Component.translatable("mages_legacy.merc.book.reroll", price);
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
		int listBottom = bottom - 14;
		int y = top - scroll[1];
		int cardH = 64;
		g.enableScissor(x, top, x + w, listBottom);
		if (active.isEmpty()) {
			g.drawWordWrap(font, Component.translatable("mages_legacy.merc.book.empty_hand"), x + 14, top + 4, w - 24, alpha(INK_SOFT, fade));
		}
		for (int i = 0; i < active.size(); i++) {
			CompoundTag c = active.get(i);
			if (y + cardH > top && y < listBottom) {
				card(g, c, x + 10, y, w - 18, cardH, mouseX, mouseY, fade, true, top, listBottom);
				int id = c.getInt("id");
				int bx = x + w - 24;
				int by = y + cardH - 15;
				iconButton(g, bx, by, "✕", RED_INK, mouseX, mouseY, () -> ask(2, id, ""), List.of(Component.translatable("mages_legacy.merc.book.abandon")), top,
						listBottom);
				if (i > 0) {
					bx -= 16;
					iconButton(g, bx, by, "★", GOLD_INK, mouseX, mouseY, () -> ask(7, id, ""), List.of(Component.translatable("mages_legacy.merc.book.pin")), top,
							listBottom);
				}
				if ("deliver".equals(c.getString("kind"))) {
					int have = c.getInt("have");
					Component give = Component.translatable("mages_legacy.merc.book.deliver", have);
					int dw = font.width(give) + 10;
					bx -= dw + 4;
					boolean can = have > 0;
					LegacyUi.button(g, bx, by, dw, 13, can && waiting == 0, can && inside(mouseX, mouseY, bx, by, dw, 13), true, 0xFF6E9A3A);
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
		small(g, Component.translatable("mages_legacy.merc.book.next_day", clock(view.getLong("nextDay"))).getString(), x + 14, bottom - 8,
				alpha(INK_SOFT, fade));
		int streak = view.getInt("streak");
		if (streak > 0) {
			Component s = Component.translatable("mages_legacy.merc.book.streak", Math.min(3, streak) * 10);
			small(g, s.getString(), x + w - 12 - (int) (font.width(s) * 0.75f), bottom - 8, alpha(GOLD_INK, fade));
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

	/**
	 * A job on parchment: its picture, what to do, who asks and why, its tier, condition and pay; how long it stays
	 * quick; in hand, how far along it is (or which way its mark lies).
	 */
	private void card(GuiGraphics g, CompoundTag c, int x, int y, int w, int h, int mouseX, int mouseY, float fade, boolean inHand, int top, int bottom) {
		boolean elite = c.getBoolean("elite");
		String kind = c.getString("kind");
		boolean bounty = "bounty".equals(kind);
		boolean hot = inside(mouseX, mouseY, x, y, w, h);
		int frame = bounty ? 0xFFA4321F : elite ? 0xFFB8862E : 0x50000000;
		g.fill(x, y, x + w, y + h, hot ? 0x22000000 : 0x14000000);
		LegacyUi.outline(g, x, y, x + w, y + h, alpha(frame, fade));
		if (elite || bounty) {
			LegacyUi.diamond(g, x + w - 6, y + 6, 4, 0xFF8E2A1E);
			LegacyUi.diamond(g, x + w - 6, y + 6, 2, bounty ? 0xFF1A0A0A : 0xFFD9A441);
		}
		g.renderItem(icon(c), x + 4, y + 4);
		Component what = describe(c);
		int textX = x + 24;
		int textW = w - 32;
		g.drawString(font, font.plainSubstrByWidth(what.getString(), textW), textX, y + 3, alpha(bounty ? RED_INK : INK, fade), false);
		int tier = Math.max(1, Math.min(4, c.getInt("tier")));
		String sub = Component.translatable("mages_legacy.merc.client." + c.getString("client")).getString() + " · "
				+ Component.translatable("mages_legacy.merc.book.tier", TIERS[tier - 1]).getString()
				+ (elite ? " · " + Component.translatable("mages_legacy.merc.book.elite").getString() : "");
		small(g, sub, textX, y + 13, alpha(elite ? GOLD_INK : INK_SOFT, fade));
		// The client's own words, one line (all of it under the mouse)
		Component line = Component.translatable("mages_legacy.merc.job." + c.getString("job"));
		var words = font.split(line, (int) ((w - 10) / 0.75f));
		if (!words.isEmpty()) {
			g.pose().pushPose();
			g.pose().translate(x + 5, y + 22, 0);
			g.pose().scale(0.75f, 0.75f, 1);
			g.drawString(font, words.get(0), 0, 0, alpha(INK_SOFT, fade), false);
			if (words.size() > 1) {
				g.drawString(font, words.get(1), 0, 9, alpha(INK_SOFT, fade), false);
			}
			g.pose().popPose();
		}
		int infoY = y + 37;
		// A hard condition, the quick bonus (time left in hand, the promise on the board)
		String rule = c.getString("rule");
		int ix = x + 5;
		if (!rule.isEmpty() && !"none".equals(rule)) {
			String r = Component.translatable("mages_legacy.merc.rule." + rule).getString();
			small(g, r, ix, infoY, alpha(RED_INK, fade));
			ix += (int) (font.width(r) * 0.75f) + 6;
		}
		if (inHand && c.contains("quickLeft")) {
			small(g, Component.translatable("mages_legacy.merc.book.quick_left", clock(c.getLong("quickLeft"))).getString(), ix, infoY, alpha(GREEN_INK, fade));
		} else if (!inHand && c.contains("quickTokens")) {
			small(g, Component.translatable("mages_legacy.merc.book.quick_offer", c.getInt("quickTokens")).getString(), ix, infoY, alpha(GREEN_INK, fade));
		}
		int payY = y + h - 12;
		if (inHand) {
			int barY = y + 46;
			int barW = w - 10;
			if ("hunt".equals(kind) || "deliver".equals(kind)) {
				int amount = Math.max(1, c.getInt("amount"));
				g.fill(x + 5, barY, x + 5 + barW, barY + 4, 0x40000000);
				int fill = Math.round(barW * Math.min(1, c.getInt("progress") / (float) amount));
				g.fillGradient(x + 5, barY, x + 5 + fill, barY + 4, 0xFFE2B04A, 0xFFA0602A);
				String count = c.getInt("progress") + "/" + amount;
				small(g, count, x + 5 + barW - (int) (font.width(count) * 0.75f), barY - 7, alpha(INK_SOFT, fade));
			} else {
				Component where;
				if (c.getBoolean("otherWorld")) {
					where = Component.translatable("mages_legacy.merc.book.other_world");
				} else if (c.contains("far")) {
					where = Component.translatable("mages_legacy.merc.book.guide", c.getInt("far"),
							Component.translatable("mages_legacy.merc.dir." + COMPASS[Math.floorMod(c.getInt("dir"), 8)]));
				} else {
					where = Component.translatable(c.getBoolean("armed") ? "mages_legacy.merc.book.explore_armed" : "mages_legacy.merc.book.explore_leave");
				}
				small(g, where.getString(), x + 5, barY - 2, alpha(INK, fade));
			}
		}
		String xp = "+" + c.getInt("payXp");
		small(g, Component.translatable("mages_legacy.merc.book.pay_xp", xp).getString(), x + 5, payY, alpha(GREEN_INK, fade));
		int after = x + 9 + (int) (font.width(Component.translatable("mages_legacy.merc.book.pay_xp", xp)) * 0.75f);
		coin(g, after, payY - 2);
		small(g, String.valueOf(c.getInt("payTokens")), after + 11, payY, alpha(GOLD_INK, fade));
		List<Component> tip = new ArrayList<>();
		tip.add(what.copy().withStyle(bounty ? ChatFormatting.RED : ChatFormatting.GOLD));
		tip.add(Component.translatable("mages_legacy.merc.client." + c.getString("client")).withStyle(ChatFormatting.YELLOW));
		tip.add(line.copy().withStyle(ChatFormatting.GRAY, ChatFormatting.ITALIC));
		tip.add(Component.translatable("mages_legacy.merc.kind." + kind + ".tip").withStyle(ChatFormatting.GRAY));
		if (!rule.isEmpty() && !"none".equals(rule)) {
			tip.add(Component.translatable("mages_legacy.merc.rule." + rule + ".tip").withStyle(ChatFormatting.RED));
		}
		tip.add(Component.translatable("mages_legacy.merc.book.quick_tip").withStyle(ChatFormatting.GREEN));
		if (elite) {
			tip.add(Component.translatable("mages_legacy.merc.book.elite_tip").withStyle(ChatFormatting.YELLOW));
		}
		if (y >= top - h / 2 && y + 30 <= bottom) {
			hots.add(new Hot(x, Math.max(y, top), x + w, Math.min(y + h - 16, bottom), () -> {
			}, tip, null));
		}
	}

	/** What a contract asks, in words: "Zombie ×8", "Iron Ingot ×8", "Desert", "A marked place", the bounty's name. */
	public static Component describe(CompoundTag c) {
		String kind = c.getString("kind");
		if ("scout".equals(kind)) {
			return Component.translatable("mages_legacy.merc.book.scout_target");
		}
		if ("bounty".equals(kind)) {
			return Component.translatable("mages_legacy.merc.book.bounty_target", Component.translatable("mages_legacy.merc.bounty." + c.getString("job")));
		}
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
		String kind = c.getString("kind");
		if ("scout".equals(kind)) {
			return new ItemStack(Items.COMPASS);
		}
		if (id == null) {
			return new ItemStack(Items.PAPER);
		}
		return switch (kind) {
			case "hunt", "bounty" -> {
				Optional<EntityType<?>> type = EntityType.byString(id.toString());
				SpawnEggItem egg = type.map(SpawnEggItem::byId).orElse(null);
				yield egg != null ? new ItemStack(egg) : new ItemStack(Items.IRON_SWORD);
			}
			case "deliver" -> new ItemStack(BuiltInRegistries.ITEM.get(id));
			default -> new ItemStack(Items.FILLED_MAP);
		};
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
		if (mode == 0) {
			int i = mouseX >= width / 2.0 ? 1 : 0;
			scroll[i] = (int) Math.max(0, Math.min(scrollMax[i] + scroll[i], scroll[i] - scrollY * 14));
		}
		return true;
	}

	@Override
	public boolean keyPressed(int key, int scan, int modifiers) {
		if (euf.mageslegacy.client.MagesLegacyClient.ABILITY_KEYS[3].matches(key, scan)) {
			onClose();
			return true;
		}
		// Esc in the book goes back to the shop first
		if (key == org.lwjgl.glfw.GLFW.GLFW_KEY_ESCAPE && mode == 0) {
			turn(1);
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

	private static String clock(long ticks) {
		long s = Math.max(0, ticks / 20);
		return String.format("%d:%02d", s / 60, s % 60);
	}
}
