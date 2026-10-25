package euf.mageslegacy.client;

import euf.mageslegacy.race.Race;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Item;
import net.minecraft.item.Items;

/** The race resource as a vertical bar left of the hotbar, and the three abilities with their keys right of it. */
public final class RaceHud {
	public static final Item[] MYCELID_ICONS = {Items.RED_MUSHROOM, Items.SPORE_BLOSSOM, Items.MYCELIUM};
	public static final int[] MYCELID_COSTS = {15, 30, 40};

	private RaceHud() {
	}

	public static void render(DrawContext ctx, float tickDelta) {
		MinecraftClient client = MinecraftClient.getInstance();
		Race race = ClientRaceState.race;
		if (race == null || client.options.hudHidden || client.player == null || client.player.isSpectator()) {
			return;
		}
		int w = ctx.getScaledWindowWidth();
		int h = ctx.getScaledWindowHeight();
		int color = 0xFF000000 | race.color;

		// Resource bar, clear of the offhand slot
		int barX = w / 2 - 91 - 42;
		int barTop = h - 24;
		int barH = 22;
		ctx.fill(barX - 1, barTop - 1, barX + 7, barTop + barH + 1, 0xC0101010);
		int filled = Math.round(barH * ClientRaceState.resource / 100f);
		ctx.fill(barX, barTop + barH - filled, barX + 6, barTop + barH, color);
		ctx.fill(barX, barTop + barH - filled, barX + 1, barTop + barH, 0x40FFFFFF);
		String amount = String.valueOf((int) ClientRaceState.resource);
		ctx.drawText(client.textRenderer, amount, barX + 3 - client.textRenderer.getWidth(amount) / 2, barTop - 10, color, true);

		if (race != Race.MYCELID) {
			return;
		}
		// Ability slots with key letters; a dark veil shows the cooldown, red marks a lack of spores
		int x = w / 2 + 91 + 6;
		for (int slot = 0; slot < MYCELID_ICONS.length; slot++) {
			int sx = x + slot * 21;
			int sy = h - 21;
			ctx.fill(sx - 1, sy - 1, sx + 19, sy + 19, 0xC0101010);
			ctx.drawItem(new ItemStack(MYCELID_ICONS[slot]), sx + 1, sy + 1);
			int cooldown = ClientRaceState.cooldown(slot);
			boolean chain = slot == 0 && ClientRaceState.chainLeft > 0;
			if (cooldown > 0 && !chain) {
				ctx.fill(sx, sy, sx + 18, sy + 18, 0xA0000000);
			} else if (ClientRaceState.resource < (chain ? 5 : MYCELID_COSTS[slot])) {
				ctx.fill(sx, sy, sx + 18, sy + 18, 0x60800000);
			}
			String key = MagesLegacyClient.ABILITY_KEYS[slot].getBoundKeyLocalizedText().getString();
			ctx.getMatrices().push();
			ctx.getMatrices().translate(0, 0, 200);
			ctx.drawText(client.textRenderer, key, sx + 18 - client.textRenderer.getWidth(key), sy + 10, 0xFFFFFF, true);
			if (chain) {
				ctx.drawText(client.textRenderer, "x" + ClientRaceState.chainLeft, sx + 1, sy + 1, 0x9CFF8A, true);
			}
			ctx.getMatrices().pop();
		}
	}
}
