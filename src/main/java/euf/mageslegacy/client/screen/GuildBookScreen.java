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
	private static final ResourceLocation BG = LegacyUi.gui("merc_shop/bg");
	private static final ResourceLocation SHELF = LegacyUi.gui("merc_shop/shelf");
	private static final ResourceLocation COUNTER = LegacyUi.gui("merc_shop/counter");
	private static final ResourceLocation BOARD = LegacyUi.gui("merc_shop/board");
	private static final ResourceLocation DOOR_LEAF = LegacyUi.gui("merc_shop/door");
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

	/** A picture's own size in pixels, read once from the file (the Codex props come in their own shapes). */
	private static final java.util.Map<ResourceLocation, int[]> SIZES = new java.util.HashMap<>();

	private static int[] size(ResourceLocation texture) {
		return SIZES.computeIfAbsent(texture, t -> {
			try (var in = Minecraft.getInstance().getResourceManager().open(t)) {
				var image = com.mojang.blaze3d.platform.NativeImage.read(in);
				int[] wh = {image.getWidth(), image.getHeight()};
				image.close();
				return wh;
			} catch (Exception e) {
				return new int[] {16, 16};
			}
		});
	}

	/** A picture drawn whole into a box (stretched to it). */
	private static void picture(GuiGraphics g, ResourceLocation texture, int x, int y, int w, int h) {
		int[] wh = size(texture);
		g.blit(texture, x, y, w, h, 0, 0, wh[0], wh[1], wh[0], wh[1]);
	}

	/** A picture drawn at a width, its height following its shape; returns the height. */
	private static int pictureW(GuiGraphics g, ResourceLocation texture, int x, int y, int w) {
		int[] wh = size(texture);
		int h = Math.round(w * wh[1] / (float) wh[0]);
		g.blit(texture, x, y, w, h, 0, 0, wh[0], wh[1], wh[0], wh[1]);
		return h;
	}

	private static int heightAt(ResourceLocation texture, int w) {
		int[] wh = size(texture);
		return Math.round(w * wh[1] / (float) wh[0]);
	}

	/** The scene's box: 16:9, as large as fits (up to 480 wide). */
	private int sw() {
		int w = Math.min(width - 10, 480);
		return Math.min(w, (height - 10) * 16 / 9);
	}

	private int sh() {
		return sw() * 9 / 16;
	}

	/** Ticks since the doors began to open (the first thing seen on coming in). */
	private int opened = -1;
	private static final int DOORS = 22;

	private void shop(GuiGraphics g, int mouseX, int mouseY, float partial) {
		int sw = sw();
		int sh = sh();
		int sx = (width - sw) / 2;
		int sy = (height - sh) / 2;
		g.fill(sx - 2, sy - 2, sx + sw + 2, sy + sh + 2, 0xFF0C0806);
		picture(g, BG, sx, sy, sw, sh);
		// The sign, hung from the beam, the shop's name painted on it
		int signW = sw * 34 / 100;
		int signX = sx + (sw - signW) / 2;
		int signH = pictureW(g, SIGN, signX, sy, signW);
		Component name = Component.translatable("mages_legacy.merc.shop.name");
		int nameY = sy + signH * 64 / 100 - 4;
		g.drawString(font, name, signX + (signW - font.width(name)) / 2, nameY, 0xFFFFDA8A, true);
		int floor = sy + sh - 12;
		// The shelving on the left, in its own shape, today's goods on it
		int shelfY = sy + signH + 2;
		int shelfH = floor - shelfY;
		int[] shelfSize = size(SHELF);
		int shelfW = Math.min(sw * 56 / 100, Math.round(shelfH * shelfSize[0] / (float) shelfSize[1] * 1.25f));
		int shelfX = sx + sw * 30 / 100 - shelfW / 2;
		picture(g, SHELF, shelfX, shelfY, shelfW, shelfH);
		shelves(g, shelfX, shelfY, shelfW, shelfH, mouseX, mouseY);
		// The services' board on the right, the counter below it with the keeper behind
		int rx = sx + sw * 60 / 100;
		int rw = sw * 37 / 100;
		int boardY = sy + sh * 18 / 100;
		int boardH = Math.min(heightAt(BOARD, rw), sh * 34 / 100);
		picture(g, BOARD, rx, boardY, rw, boardH);
		services(g, rx, boardY, rw, boardH, mouseX, mouseY);
		counter(g, rx, boardY + boardH, rw, floor, mouseX, mouseY, partial);
		footer(g, sx + 6, sy + sh - 10, sx + sw - 6);
		doors(g, sx, sy, sw, sh, partial);
	}

	/**
	 * Coming in: the shop's front stands dark before the eyes with its double door of oak and copper in the middle; the
	 * leaves swing open inwards (each narrowing on its hinge and darkening as it turns), warm light pours through the
	 * widening gap, and the front fades as one steps in.
	 */
	private void doors(GuiGraphics g, int sx, int sy, int sw, int sh, float partial) {
		if (opened < 0) {
			opened = ticks;
		}
		float t = (ticks - opened + partial) / DOORS;
		if (t >= 1) {
			return;
		}
		float swing = smoothstep((t - 0.12f) / 0.6f);
		float front = 1 - smoothstep((t - 0.6f) / 0.4f);
		// Over everything in the room, the wares' item models too (they draw high)
		g.pose().pushPose();
		g.pose().translate(0, 0, 400);
		try {
			doorLeaves(g, sx, sy, sw, sh, swing, front);
		} finally {
			g.pose().popPose();
		}
	}

	private void doorLeaves(GuiGraphics g, int sx, int sy, int sw, int sh, float swing, float front) {
		// The front: the room behind it darkened to a wall, the doorway framed in oak
		g.fill(sx, sy, sx + sw, sy + sh, LegacyUi.alpha(0xFF120B07, 0.92f * front));
		int[] wh = size(DOOR_LEAF);
		int leafH = sh * 86 / 100;
		int leafW = Math.round(leafH * wh[0] / (float) wh[1]);
		int cx = sx + sw / 2;
		int top = sy + sh - leafH - sh * 4 / 100;
		g.fill(cx - leafW - 4, top - 4, cx + leafW + 4, sy + sh - sh * 4 / 100, LegacyUi.alpha(0xFF3A2414, front));
		// The light coming through the gap
		double angle = Math.PI / 2 * swing;
		int leaf = (int) Math.round(leafW * Math.cos(angle));
		if (leafW - leaf > 0) {
			g.fillGradient(cx - leafW + leaf, top, cx + leafW - leaf, top + leafH, LegacyUi.alpha(0xFFFFD088, 0.7f * front), LegacyUi.alpha(0xFFE08A3A, 0.5f * front));
		}
		if (leaf <= 0 || front <= 0) {
			return;
		}
		float shade = (0.5f + 0.5f * (float) Math.cos(angle)) * front;
		com.mojang.blaze3d.systems.RenderSystem.enableBlend();
		com.mojang.blaze3d.systems.RenderSystem.setShaderColor(shade, shade, shade, front);
		// The left leaf on its hinge at the doorway's left post; the right one mirrored on the right post
		g.blit(DOOR_LEAF, cx - leafW, top, leaf, leafH, 0, 0, wh[0], wh[1], wh[0], wh[1]);
		// Mirrored by its texture coordinates running backwards (a flipped quad would be culled)
		g.blit(DOOR_LEAF, cx + leafW - leaf, top, leaf, leafH, wh[0], 0, -wh[0], wh[1], wh[0], wh[1]);
		com.mojang.blaze3d.systems.RenderSystem.setShaderColor(1, 1, 1, 1);
	}

	private static float smoothstep(float t) {
		t = Math.max(0, Math.min(1, t));
		return t * t * (3 - 2 * t);
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

	/** Where the shelf picture's three boards are (their top edges, as shares of its height), and its inner sides. */
	static final float[] SHELF_BOARDS = {0.307f, 0.564f, 0.822f};
	static final float SHELF_INNER = 0.1f;

	/** Today's goods standing on the three shelves of the picture, their tags hanging below each board. */
	private void shelves(GuiGraphics g, int x, int y, int w, int h, int mouseX, int mouseY) {
		List<CompoundTag> goods = wares(false);
		int rows = SHELF_BOARDS.length;
		int cols = Math.max(4, (goods.size() + rows - 1) / rows);
		int inner = Math.round(w * SHELF_INNER);
		int cellW = (w - 2 * inner) / cols;
		int tokens = view.getInt("tokens");
		for (int r = 0; r < rows; r++) {
			int plankY = y + Math.round(h * SHELF_BOARDS[r]);
			for (int c = 0; c < cols; c++) {
				int i = r * cols + c;
				if (i >= goods.size()) {
					continue;
				}
				int cx = x + inner + c * cellW + cellW / 2;
				ware(g, goods.get(i), cx, plankY, cellW, tokens, mouseX, mouseY);
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
		// A cushion of the rarity's colour under the ware (darker for the plain ones); a legendary one twinkles
		int cushion = LegacyUi.mix(0xFF2A1A10, color, 0.25f + 0.12f * rar);
		g.fill(cx - size / 2 - 1, plankY - 4, cx + size / 2 + 1, plankY, cushion);
		g.fill(cx - size / 2 - 1, plankY - 4, cx + size / 2 + 1, plankY - 3, LegacyUi.mix(cushion, 0xFFFFFFFF, 0.15f));
		if (rar == 4) {
			for (int k = 0; k < 3; k++) {
				float ph = (LegacyUi.time() * 1.3f + k * 0.37f + cx * 0.01f) % 1;
				int px = cx - 10 + (int) (LegacyUi.hash(k, cx) * 20);
				int py = iy + 2 + (int) (LegacyUi.hash(k + 5, cx) * 18);
				g.fill(px, py, px + 1, py + 1, LegacyUi.alpha(0xFFFFE6A0, (float) Math.sin(Math.PI * ph)));
			}
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
			LegacyUi.outline(g, cx - cellW / 2 + 1, iy - 4, cx + cellW / 2 - 1, plankY + 18, 0xA0E8C060);
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

	/** The services' board (the picture), today's services pinned to it as notes, a pin of each one's rarity. */
	private void services(GuiGraphics g, int bx, int by, int bw, int bh, int mouseX, int mouseY) {
		int padX = bw * 9 / 100;
		int padY = bh * 14 / 100;
		int x0 = bx + padX;
		int y0 = by + padY;
		int iw = bw - 2 * padX;
		int ih = bh - 2 * padY;
		Component title = Component.translatable("mages_legacy.merc.shop.services");
		int tw = (int) (font.width(title) * 0.75f);
		g.fill(bx + (bw - tw) / 2 - 3, by - 9, bx + (bw + tw) / 2 + 3, by - 1, 0xC0201208);
		small(g, title.getString(), bx + (bw - tw) / 2, by - 7, 0xFFFFE0A8);
		List<CompoundTag> services = wares(true);
		int cols = 2;
		int cellW = iw / cols;
		int cellH = ih / 2;
		int tokens = view.getInt("tokens");
		for (int i = 0; i < Math.min(4, services.size()); i++) {
			CompoundTag s = services.get(i);
			int nx = x0 + (i % cols) * cellW + 1;
			int ny = y0 + (i / cols) * cellH + 1;
			int nw = cellW - 2;
			int nh = cellH - 2;
			int rar = rarity(s);
			boolean hot = mouseX >= nx && mouseX < nx + nw && mouseY >= ny && mouseY < ny + nh;
			picture(g, NOTE, nx, ny - (hot ? 1 : 0), nw, nh);
			LegacyUi.diamond(g, nx + nw / 2, ny + 2, 2, RARITY_COLORS[rar]);
			ItemStack stack = ItemStack.parseOptional(Minecraft.getInstance().level.registryAccess(), s.getCompound("icon"));
			g.pose().pushPose();
			g.pose().translate(nx + 3, ny + nh / 2 - 6, 0);
			g.pose().scale(0.75f, 0.75f, 1);
			g.renderItem(stack, 0, 0);
			g.pose().popPose();
			String id = s.getString("id");
			Component name = Component.translatable("mages_legacy.shop." + id);
			List<net.minecraft.util.FormattedCharSequence> lines = font.split(name, (int) ((nw - 20) / 0.65f));
			for (int k = 0; k < Math.min(2, lines.size()); k++) {
				g.pose().pushPose();
				g.pose().translate(nx + 17, ny + 5 + k * 6, 0);
				g.pose().scale(0.65f, 0.65f, 1);
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

	/** Under the board: the keeper behind the counter, the lantern, the book of orders open on the counter, the pouch. */
	private void counter(GuiGraphics g, int x, int y, int w, int floor, int mouseX, int mouseY, float partial) {
		int counterH = Math.min(heightAt(COUNTER, w), (floor - y) * 62 / 100);
		int topY = floor - counterH;
		// The keeper, half hidden by the counter; the eyes blink now and then
		int kw = w * 46 / 100;
		int kh = heightAt(KEEPER, kw);
		int kx = x + w - kw - w * 4 / 100;
		int ky = topY - kh + kh * 38 / 100;
		picture(g, KEEPER, kx, ky, kw, kh);
		float t = LegacyUi.time();
		// The lantern hanging on the left, its light flickering on the wall
		int lw = Math.max(10, w * 8 / 100);
		int lx = x + w * 3 / 100;
		int ly = y + 2;
		float flicker = 0.55f + 0.12f * (float) Math.sin(t * 7) + 0.08f * (float) Math.sin(t * 13.7);
		LegacyUi.glow(g, lx + lw / 2, ly + heightAt(LANTERN, lw) * 2 / 3, 26, 0xFFFFA040, flicker);
		pictureW(g, LANTERN, lx, ly, lw);
		picture(g, COUNTER, x, topY, w, counterH);
		// The book of orders lying open on the counter top
		int bw = w * 46 / 100;
		int bh = heightAt(BOOK, bw);
		int bx = x + w * 6 / 100;
		int by = topY - bh * 55 / 100;
		boolean hot = mouseX >= bx && mouseX < bx + bw && mouseY >= by && mouseY < by + bh;
		if (hot) {
			LegacyUi.glow(g, bx + bw / 2, by + bh / 2, bw / 2 + 2, 0xFFFFD27A, 0.5f);
		}
		picture(g, BOOK, bx, by - (hot ? 2 : 0), bw, bh);
		int inHand = view.getList("active", Tag.TAG_COMPOUND).size();
		Component label = Component.translatable("mages_legacy.merc.shop.orders", inHand, view.getInt("hand"));
		int lwidth = (int) (font.width(label) * 0.75f);
		int ly0 = topY + counterH * 55 / 100;
		g.fill(bx + (bw - lwidth) / 2 - 3, ly0 - 2, bx + (bw + lwidth) / 2 + 3, ly0 + 7, 0xC0201208);
		small(g, label.getString(), bx + (bw - lwidth) / 2, ly0, 0xFFFFE0A8);
		hots.add(new Hot(bx, by - 2, bx + bw, by + bh, () -> turn(0), List.of(Component.translatable("mages_legacy.merc.shop.orders_tip")
				.withStyle(ChatFormatting.GRAY)), null));
		// The pouch on the counter: the balance
		String tokens = String.valueOf(view.getInt("tokens"));
		int pw = Math.max(14, w * 11 / 100);
		int ph = heightAt(POUCH, pw);
		int px = bx + bw + w * 4 / 100;
		int py = topY - ph * 70 / 100;
		pictureW(g, POUCH, px, py, pw);
		g.drawString(font, tokens, px + pw + 2, py + ph / 2 - 3, 0xFFFFD27A, true);
	}

	/** Under the floorboards' edge: when the stock turns over, the odds of each rarity at this level, bought luck. */
	private void footer(GuiGraphics g, int x0, int y, int x1) {
		g.fill(x0 - 6, y - 2, x1 + 6, y + 9, 0xB0140C08);
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
