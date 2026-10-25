package euf.mageslegacy.client;

import euf.mageslegacy.client.screen.LegacyUi;
import euf.mageslegacy.mycelid.Mycelid;
import euf.mageslegacy.race.Race;
import euf.mageslegacy.race.Talents;
import euf.mageslegacy.shepherd.EchoType;
import euf.mageslegacy.shepherd.Shepherd;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.util.Identifier;
import org.jetbrains.annotations.Nullable;

/**
 * The race resource as a glowing vial left of the hotbar (its icon beside it), and the three abilities in framed
 * slots right of it, with key caps, a cooldown veil with the seconds left, and a red tint when the resource is short.
 */
public final class RaceHud {
	/** Item icons, the fallback while the Codex icons are missing. */
	public static final Item[] MYCELID_ICONS = {Items.RED_MUSHROOM, Items.SPORE_BLOSSOM, Items.MYCELIUM};
	public static final String[] MYCELID_ABILITY_ICONS = {"ability_spore_jump", "ability_spore_cloud", "ability_network"};
	public static final int[] MYCELID_COSTS = {15, 30, 40};
	public static final Item[] SHEPHERD_ICONS = {Items.BONE, Items.GOAT_HORN, Items.ENDER_PEARL};
	public static final String[] SHEPHERD_ABILITY_ICONS = {"ability_call", "ability_rally", "ability_swap"};

	private RaceHud() {
	}

	public static boolean hasAbilities(Race race) {
		return race == Race.MYCELID || race == Race.SHEPHERD;
	}

	/** The Codex icon of an ability slot (0..2), or null if the race has none yet. */
	@Nullable
	public static String abilityIcon(Race race, int slot) {
		return switch (race) {
			case MYCELID -> MYCELID_ABILITY_ICONS[slot];
			case SHEPHERD -> SHEPHERD_ABILITY_ICONS[slot];
			default -> null;
		};
	}

	/**
	 * What an ability costs this player now: Light Spore makes the jump cheaper, the Call costs what the picked echo
	 * does (-1 while none is picked), Shadow for Shadow makes the swap cheaper.
	 */
	public static int cost(Race race, int slot) {
		if (race == Race.SHEPHERD) {
			return switch (slot) {
				case 0 -> EchoType.byId(ClientRaceState.extra.getString("selected"))
						.map(t -> t.voiceCost(ClientRaceState.talents.contains(Talents.LEGION_CHEAP))).orElse(-1);
				case 1 -> Shepherd.RALLY_COST;
				default -> ClientRaceState.talents.contains(Talents.VOICE_SWAP) ? Shepherd.SWAP_COST_CHEAP : Shepherd.SWAP_COST;
			};
		}
		if (slot == 0 && ClientRaceState.talents.contains(Talents.NET_CHEAP)) {
			return Mycelid.JUMP_COST_CHEAP;
		}
		return MYCELID_COSTS[slot];
	}

	/** The race resource's icon, or null. */
	@Nullable
	public static String resourceIcon(Race race) {
		return switch (race) {
			case MYCELID -> "res_spores";
			case SHEPHERD -> "res_voice";
			default -> null;
		};
	}

	/** An ability's icon: the Codex texture if it ships, the item otherwise. */
	public static void drawAbility(DrawContext ctx, Race race, int slot, int x, int y, boolean bright) {
		String icon = abilityIcon(race, slot);
		Identifier texture = icon == null ? null : LegacyUi.icon(icon);
		if (texture != null && LegacyUi.exists(texture)) {
			LegacyUi.icon(ctx, texture, x, y, 16, bright);
		} else if (race == Race.MYCELID) {
			ctx.drawItem(new ItemStack(MYCELID_ICONS[slot]), x, y);
		} else if (race == Race.SHEPHERD) {
			// The Call shows the echo it would bring
			EchoType echo = slot == 0 ? EchoType.byId(ClientRaceState.extra.getString("selected")).orElse(null) : null;
			ctx.drawItem(new ItemStack(echo != null ? echo.icon() : SHEPHERD_ICONS[slot]), x, y);
		}
	}

	public static void render(DrawContext ctx, float tickDelta) {
		MinecraftClient client = MinecraftClient.getInstance();
		Race race = ClientRaceState.race;
		if (race == null || client.options.hudHidden || client.player == null || client.player.isSpectator()) {
			return;
		}
		TextRenderer tr = client.textRenderer;
		int w = ctx.getScaledWindowWidth();
		int h = ctx.getScaledWindowHeight();
		int color = LegacyUi.argb(race.color);

		// The vial, clear of the offhand slot
		int barX = w / 2 - 91 - 40;
		int barTop = h - 25;
		int barH = 23;
		ctx.fill(barX - 2, barTop - 2, barX + 8, barTop + barH + 2, LegacyUi.BLACK);
		ctx.fill(barX - 1, barTop - 1, barX + 7, barTop + barH + 1, LegacyUi.RIM);
		ctx.fill(barX, barTop, barX + 6, barTop + barH, 0xFF0E0B10);
		float fraction = Math.max(0, Math.min(1, ClientRaceState.resource / 100f));
		int filled = Math.round(barH * fraction);
		if (filled > 0) {
			int top = barTop + barH - filled;
			ctx.fillGradient(barX, top, barX + 6, barTop + barH, LegacyUi.mix(color, 0xFFFFFFFF, 0.25f), LegacyUi.mix(color, 0xFF000000, 0.35f));
			ctx.fill(barX + 1, top, barX + 2, barTop + barH, 0x50FFFFFF);
			// A shimmer rising through the liquid
			int shimmer = barTop + barH - 1 - (int) ((LegacyUi.time() * 14) % Math.max(1, filled));
			ctx.fill(barX, shimmer, barX + 6, shimmer + 1, 0x60FFFFFF);
			ctx.fill(barX, top, barX + 6, top + 1, LegacyUi.mix(color, 0xFFFFFFFF, 0.6f));
		}
		String icon = resourceIcon(race);
		if (icon != null && LegacyUi.exists(LegacyUi.icon(icon))) {
			LegacyUi.icon(ctx, LegacyUi.icon(icon), barX - 18, h - 19, 16, true);
		}
		String amount = String.valueOf((int) ClientRaceState.resource);
		ctx.drawText(tr, amount, barX + 3 - tr.getWidth(amount) / 2, barTop - 11, color, true);
		if (race == Race.SHEPHERD) {
			// Bond slots in use, over the voice icon; red when full
			int used = ClientRaceState.extra.getInt("capUsed");
			int max = ClientRaceState.extra.getInt("capMax");
			String cap = used + "/" + max;
			ctx.drawText(tr, cap, barX - 10 - tr.getWidth(cap) / 2, barTop - 11, used >= max ? LegacyUi.RED : LegacyUi.GOLD, true);
		}

		if (!hasAbilities(race)) {
			return;
		}
		int x = w / 2 + 91 + 8;
		for (int slot = 0; slot < 3; slot++) {
			int sx = x + slot * 23;
			int sy = h - 22;
			int cooldown = ClientRaceState.cooldown(slot);
			boolean chain = race == Race.MYCELID && slot == 0 && ClientRaceState.chainLeft > 0;
			boolean cooling = cooldown > 0 && !chain;
			int cost = chain ? Mycelid.CHAIN_COST : cost(race, slot);
			boolean short_ = cost < 0 || ClientRaceState.resource < cost;
			boolean ready = !cooling && !short_;
			ctx.fill(sx - 2, sy - 2, sx + 20, sy + 20, LegacyUi.BLACK);
			LegacyUi.outline(ctx, sx - 1, sy - 1, sx + 19, sy + 19, ready ? LegacyUi.mix(LegacyUi.RIM, color, 0.5f) : LegacyUi.RIM_DARK);
			ctx.fill(sx, sy, sx + 18, sy + 18, 0xD0141016);
			drawAbility(ctx, race, slot, sx + 1, sy + 1, ready);
			if (race == Race.SHEPHERD && slot == 0) {
				// The echo the Call brings, small in the corner
				EchoType.byId(ClientRaceState.extra.getString("selected")).ifPresent(echo -> {
					ctx.getMatrices().push();
					ctx.getMatrices().translate(sx - 2, sy - 3, 150);
					ctx.getMatrices().scale(0.5f, 0.5f, 1f);
					ctx.drawItem(new ItemStack(echo.icon()), 0, 0);
					ctx.getMatrices().pop();
				});
			}
			ctx.getMatrices().push();
			ctx.getMatrices().translate(0, 0, 200);
			if (cooling) {
				ctx.fill(sx, sy, sx + 18, sy + 18, 0x90000000);
				String secs = String.valueOf((cooldown + 19) / 20);
				ctx.drawText(tr, secs, sx + 9 - tr.getWidth(secs) / 2, sy + 5, 0xFFFFFFFF, true);
			} else if (short_) {
				ctx.fill(sx, sy, sx + 18, sy + 18, 0x50A01818);
			}
			String key = MagesLegacyClient.keyLabel(MagesLegacyClient.ABILITY_KEYS[slot]);
			LegacyUi.keyCap(ctx, key, sx + 21, sy + 22, ready ? 0xFFFFFFFF : 0xFF9A9088);
			if (chain) {
				ctx.drawText(tr, "x" + ClientRaceState.chainLeft, sx + 1, sy + 1, LegacyUi.mix(color, 0xFFFFFFFF, 0.4f), true);
			}
			ctx.getMatrices().pop();
		}
	}
}
