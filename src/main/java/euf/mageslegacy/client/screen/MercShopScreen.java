package euf.mageslegacy.client.screen;

import euf.mageslegacy.client.ClientRaceState;
import euf.mageslegacy.client.MagesLegacyClient;
import euf.mageslegacy.mercenary.Contract;
import euf.mageslegacy.mercenary.Shop;
import euf.mageslegacy.race.Race;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.entity.EntityType;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.item.SpawnEggItem;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtElement;
import net.minecraft.nbt.NbtList;
import net.minecraft.registry.Registries;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.Identifier;

/**
 * The Mage's Shop (the Mercenary's H): the board of contracts (offers on the left, those in hand on the right) and
 * the shop, where the tokens the contracts pay are spent. Tokens exist only here. Everything is drawn from the
 * Mercenary's sync; every button asks the server, which checks again.
 */
public class MercShopScreen extends Screen {
	private static final String[] ROMAN = {"I", "II", "III", "IV"};
	private static final int CARD_H = 32;
	private static final int WARE_H = 18;
	private static boolean onShop;
	/** The sync this layout was built from: when it changes the buttons are laid out again. */
	private NbtCompound built;
	private final List<Tip> tips = new ArrayList<>();

	private record Tip(int x0, int y0, int x1, int y1, List<Text> lines) {}

	public MercShopScreen() {
		super(Text.translatable("mages_legacy.merc.title"));
	}

	private static NbtCompound merc() {
		return ClientRaceState.extra;
	}

	/** The sync without its countdown, which changes every time. */
	private static NbtCompound layoutKey() {
		NbtCompound key = merc().copy();
		key.remove("nextDay");
		return key;
	}

	private int color() {
		return LegacyUi.argb(Race.MERCENARY.color);
	}

	private int colW() {
		return Math.min(260, (width - 36) / 2);
	}

	private int left() {
		return width / 2 - colW() - 6;
	}

	private int right() {
		return width / 2 + 6;
	}

	private static final int TOP = 52;

	@Override
	protected void init() {
		built = layoutKey();
		int tabW = 110;
		// The open tab is the lit one
		LegacyButton board = new LegacyButton(width / 2 - tabW - 3, 26, tabW, 18, Text.translatable("mages_legacy.merc.tab.contracts"), b -> showShop(false))
				.accent(color());
		LegacyButton shop = new LegacyButton(width / 2 + 3, 26, tabW, 18, Text.translatable("mages_legacy.merc.tab.shop"), b -> showShop(true)).accent(color());
		addDrawableChild(onShop ? shop.primary() : board.primary());
		addDrawableChild(onShop ? board : shop);
		if (onShop) {
			initShop();
		} else {
			initBoard();
		}
	}

	private void initBoard() {
		NbtList offers = merc().getList("offers", NbtElement.COMPOUND_TYPE);
		NbtList active = merc().getList("active", NbtElement.COMPOUND_TYPE);
		boolean full = active.size() >= merc().getInt("slots");
		for (int i = 0; i < offers.size(); i++) {
			int index = i;
			int y = TOP + 14 + i * (CARD_H + 3);
			addDrawableChild(new LegacyButton(left() + colW() - 54, y + CARD_H - 18, 50, 14, Text.translatable("mages_legacy.merc.take"),
					b -> MagesLegacyClient.sendMerc(0, index, ""))).active = !full;
		}
		for (int i = 0; i < active.size(); i++) {
			int index = i;
			int y = TOP + 14 + i * (CARD_H + 3);
			Contract c = Contract.fromNbt(active.getCompound(i));
			addDrawableChild(new LegacyButton(right() + colW() - 24, y + 4, 20, 14, Text.literal("✕"),
					b -> MagesLegacyClient.sendMerc(1, index, "")));
			if (c.kind == Contract.Kind.GATHER) {
				addDrawableChild(new LegacyButton(right() + colW() - 54, y + CARD_H - 18, 50, 14, Text.translatable("mages_legacy.merc.deliver"),
						b -> MagesLegacyClient.sendMerc(2, index, "")).primary().accent(color()));
			}
		}
		int price = merc().getInt("rerollPrice");
		addDrawableChild(new LegacyButton(left(), height - 26, colW(), 18,
				price == 0 ? Text.translatable("mages_legacy.merc.reroll_free") : Text.translatable("mages_legacy.merc.reroll", price),
				b -> MagesLegacyClient.sendMerc(3, 0, ""))).active = merc().getInt("tokens") >= price;
	}

	private void initShop() {
		NbtCompound prices = merc().getCompound("prices");
		int tokens = merc().getInt("tokens");
		List<Shop.Ware> services = Shop.CATALOG.stream().filter(w -> w.kind() == Shop.Kind.SERVICE).toList();
		List<Shop.Ware> goods = todaysGoods();
		for (int column = 0; column < 2; column++) {
			List<Shop.Ware> wares = column == 0 ? services : goods;
			int x = column == 0 ? left() : right();
			for (int i = 0; i < wares.size(); i++) {
				Shop.Ware ware = wares.get(i);
				int price = prices.getInt(ware.id());
				int y = wareY(i);
				addDrawableChild(new LegacyButton(x + colW() - 54, y + 2, 50, 13,
						price == -1 ? Text.translatable("mages_legacy.merc.sold_short") : price == -2 ? Text.translatable("mages_legacy.merc.locked_short")
								: Text.literal(price + " ◆"),
						b -> MagesLegacyClient.sendMerc(4, 0, ware.id())).accent(color())).active = price >= 0 && tokens >= price;
			}
		}
	}

	/** The goods the shop has out today, in the order the server rolled them. */
	private List<Shop.Ware> todaysGoods() {
		List<Shop.Ware> goods = new ArrayList<>();
		for (net.minecraft.nbt.NbtElement e : merc().getList("stock", net.minecraft.nbt.NbtElement.STRING_TYPE)) {
			Shop.ware(e.asString()).ifPresent(goods::add);
		}
		return goods;
	}

	/** Ware rows are tight, so the whole catalogue fits a GUI scale of 3. */
	private static int wareY(int i) {
		return TOP + 13 + i * WARE_H;
	}

	@Override
	public void render(DrawContext ctx, int mouseX, int mouseY, float delta) {
		if (!built.equals(layoutKey())) {
			clearAndInit();
		}
		tips.clear();
		LegacyUi.background(ctx, width, height, Race.MERCENARY);
		LegacyUi.heading(ctx, title, width / 2, 9, LegacyUi.GOLD);
		// The account: tokens live here and nowhere else
		Text tokens = Text.translatable("mages_legacy.merc.tokens", merc().getInt("tokens"));
		int tw = textRenderer.getWidth(tokens);
		Identifier token = LegacyUi.icon("res_token");
		int tx = width - tw - 12;
		if (LegacyUi.exists(token)) {
			LegacyUi.icon(ctx, token, tx - 18, 7, 16, true);
		} else {
			ctx.drawItem(new ItemStack(Items.GOLD_NUGGET), tx - 18, 7);
		}
		ctx.drawTextWithShadow(textRenderer, tokens, tx, 11, LegacyUi.GOLD);
		if (onShop) {
			renderShop(ctx);
		} else {
			renderBoard(ctx);
		}
		super.render(ctx, mouseX, mouseY, delta);
		for (Tip tip : tips) {
			if (mouseX >= tip.x0() && mouseX < tip.x1() && mouseY >= tip.y0() && mouseY < tip.y1()) {
				ctx.drawTooltip(textRenderer, tip.lines(), mouseX, mouseY);
			}
		}
	}

	private void renderBoard(DrawContext ctx) {
		NbtList offers = merc().getList("offers", NbtElement.COMPOUND_TYPE);
		NbtList active = merc().getList("active", NbtElement.COMPOUND_TYPE);
		ctx.drawTextWithShadow(textRenderer, Text.translatable("mages_legacy.merc.board").formatted(Formatting.BOLD), left(), TOP, LegacyUi.GOLD);
		ctx.drawTextWithShadow(textRenderer, Text.translatable("mages_legacy.merc.in_hand", active.size(), merc().getInt("slots")).formatted(Formatting.BOLD),
				right(), TOP, LegacyUi.GOLD);
		if (offers.isEmpty()) {
			ctx.drawTextWithShadow(textRenderer, Text.translatable("mages_legacy.merc.board_empty").formatted(Formatting.ITALIC), left(), TOP + 18, LegacyUi.MUTED);
		}
		for (int i = 0; i < offers.size(); i++) {
			card(ctx, Contract.fromNbt(offers.getCompound(i)), left(), TOP + 14 + i * (CARD_H + 3), false);
		}
		if (active.isEmpty()) {
			ctx.drawTextWithShadow(textRenderer, Text.translatable("mages_legacy.merc.idle").formatted(Formatting.ITALIC), right(), TOP + 18, LegacyUi.MUTED);
		}
		for (int i = 0; i < active.size(); i++) {
			card(ctx, Contract.fromNbt(active.getCompound(i)), right(), TOP + 14 + i * (CARD_H + 3), true);
		}
		long ticks = merc().getLong("nextDay");
		Text day = Text.translatable("mages_legacy.merc.next_day", ticks / 1200, String.format("%02d", ticks / 20 % 60));
		ctx.drawTextWithShadow(textRenderer, day, right(), height - 21, LegacyUi.MUTED);
	}

	/** One contract: what to do, how far along, what it pays. */
	private void card(DrawContext ctx, Contract c, int x, int y, boolean inHand) {
		int w = colW();
		LegacyUi.panel(ctx, x, y, x + w, y + CARD_H, c.elite ? LegacyUi.GOLD : inHand ? color() : LegacyUi.RIM_DARK);
		ctx.drawItem(icon(c), x + 5, y + 5);
		Text task = Text.translatable("mages_legacy.merc.kind." + c.kind.name().toLowerCase(java.util.Locale.ROOT), targetName(c),
				c.kind == Contract.Kind.EXPLORE ? "" : "×" + c.amount);
		int textW = w - 30 - (inHand ? 28 : 4);
		ctx.drawTextWithShadow(textRenderer, textRenderer.trimToWidth(task.getString(), textW), x + 25, y + 5, LegacyUi.TEXT);
		String tier = (c.elite ? "★ " : "") + ROMAN[Math.max(0, Math.min(3, c.tier - 1))];
		Text pay = Text.translatable("mages_legacy.merc.pay", c.xp, c.tokens);
		ctx.drawTextWithShadow(textRenderer, Text.literal(tier), x + 5, y + 22, c.elite ? LegacyUi.GOLD : LegacyUi.MUTED);
		ctx.drawTextWithShadow(textRenderer, pay, x + 25, y + 17, LegacyUi.GREEN);
		if (inHand && c.kind != Contract.Kind.EXPLORE) {
			int barX = x + 25;
			int barW = w - 30 - (c.kind == Contract.Kind.GATHER ? 58 : 4);
			int barY = y + 26;
			ctx.fill(barX, barY, barX + barW, barY + 4, LegacyUi.BLACK);
			ctx.fill(barX, barY, barX + barW * c.progress / Math.max(1, c.amount), barY + 4, color());
			ctx.drawTextWithShadow(textRenderer, c.progress + "/" + c.amount, barX + barW - textRenderer.getWidth(c.progress + "/" + c.amount), barY - 9,
					LegacyUi.PARCHMENT);
		}
		tips.add(new Tip(x, y, x + w - 56, y + CARD_H, List.of(task.copy().formatted(Formatting.GOLD),
				Text.translatable("mages_legacy.merc.kind." + c.kind.name().toLowerCase(java.util.Locale.ROOT) + ".hint").formatted(Formatting.GRAY))));
	}

	private static ItemStack icon(Contract c) {
		Identifier id = Identifier.tryParse(c.target);
		if (id == null) {
			return new ItemStack(Items.PAPER);
		}
		return switch (c.kind) {
			case HUNT -> {
				EntityType<?> type = Registries.ENTITY_TYPE.get(id);
				SpawnEggItem egg = SpawnEggItem.forEntity(type);
				yield new ItemStack(egg != null ? egg : Items.IRON_SWORD);
			}
			case GATHER -> new ItemStack(Registries.ITEM.get(id));
			case EXPLORE -> new ItemStack(Items.FILLED_MAP);
		};
	}

	private static Text targetName(Contract c) {
		Identifier id = Identifier.tryParse(c.target);
		if (id == null) {
			return Text.literal(c.target);
		}
		return switch (c.kind) {
			case HUNT -> Registries.ENTITY_TYPE.get(id).getName();
			case GATHER -> Registries.ITEM.get(id).getName();
			case EXPLORE -> Text.translatable("biome." + id.getNamespace() + "." + id.getPath());
		};
	}

	private void renderShop(DrawContext ctx) {
		ctx.drawTextWithShadow(textRenderer, Text.translatable("mages_legacy.merc.services").formatted(Formatting.BOLD), left(), TOP, LegacyUi.GOLD);
		ctx.drawTextWithShadow(textRenderer, Text.translatable("mages_legacy.merc.goods").formatted(Formatting.BOLD), right(), TOP, LegacyUi.GOLD);
		List<Shop.Ware> services = Shop.CATALOG.stream().filter(w -> w.kind() == Shop.Kind.SERVICE).toList();
		List<Shop.Ware> goods = todaysGoods();
		for (int column = 0; column < 2; column++) {
			List<Shop.Ware> wares = column == 0 ? services : goods;
			int x = column == 0 ? left() : right();
			for (int i = 0; i < wares.size(); i++) {
				Shop.Ware ware = wares.get(i);
				int y = wareY(i);
				LegacyUi.panel(ctx, x, y, x + colW(), y + WARE_H - 1, LegacyUi.RIM_DARK);
				ItemStack stack = ware.icon().get();
				ctx.drawItem(stack, x + 1, y);
				ctx.drawItemInSlot(textRenderer, stack, x + 1, y);
				Text name = Text.translatable("mages_legacy.shop." + ware.id());
				boolean deal = ware.id().equals(merc().getString("deal"));
				ctx.drawTextWithShadow(textRenderer, textRenderer.trimToWidth(name.getString(), colW() - 80), x + 21, y + 5, deal ? LegacyUi.GOLD : LegacyUi.TEXT);
				List<Text> lines = new ArrayList<>();
				lines.add(name.copy().formatted(Formatting.GOLD));
				TalentScreen.wrapInto(lines, Text.translatable("mages_legacy.shop." + ware.id() + ".desc").formatted(Formatting.GRAY));
				if (deal) {
					lines.add(Text.translatable("mages_legacy.merc.deal").formatted(Formatting.YELLOW));
				}
				if (ware.dailyMax() > 0) {
					lines.add(Text.translatable("mages_legacy.merc.daily", ware.dailyMax()).formatted(Formatting.DARK_GRAY));
				}
				if (ware.tier() > 1) {
					lines.add(Text.translatable("mages_legacy.merc.needs_tier", ROMAN[ware.tier() - 1]).formatted(Formatting.DARK_GRAY));
				}
				tips.add(new Tip(x, y, x + colW() - 56, y + WARE_H - 1, lines));
			}
		}
		// Room kept for the race's own artifacts and armour
		int y = wareY(services.size()) + 6;
		LegacyUi.panel(ctx, left(), y, left() + colW(), y + 34, LegacyUi.RIM_DARK);
		ctx.drawTextWithShadow(textRenderer, Text.translatable("mages_legacy.merc.armory").formatted(Formatting.BOLD), left() + 6, y + 6, LegacyUi.MUTED);
		ctx.drawTextWithShadow(textRenderer, Text.translatable("mages_legacy.merc.armory_soon").formatted(Formatting.ITALIC), left() + 6, y + 19,
				LegacyUi.MUTED);
	}

	/** Turns to the shop ({@code true}) or the board. */
	public void showShop(boolean shop) {
		onShop = shop;
		clearAndInit();
	}

	@Override
	public boolean shouldPause() {
		return false;
	}
}
