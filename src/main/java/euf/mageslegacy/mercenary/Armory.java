package euf.mageslegacy.mercenary;

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import euf.mageslegacy.MagesLegacy;
import euf.mageslegacy.race.Race;
import euf.mageslegacy.race.RaceState;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import net.minecraft.ChatFormatting;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.SimpleJsonResourceReloadListener;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.profiling.ProfilerFiller;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.AddReloadListenerEvent;

/**
 * The guild's armory, a room of the Mage's Shop of its own: weapons and armour made for a race, bought with tokens. Not
 * a daily stock but a standing catalogue: every piece for the buyer's race is shown, the ones above their race level
 * locked, some sold a limited number of times per player.
 * <p>
 * The catalogue is data: one JSON file per piece in a data pack, {@code data/<namespace>/merc_armory/<id>.json}:
 * <pre>
 * {"item": "mages_legacy:guild_blade", "races": ["mercenary"], "kind": "weapon", "level": 10, "price": 40,
 *  "rarity": "rare", "limit": 1, "count": 1}
 * </pre>
 * kind is weapon or armor; limit 0 (or none) sells it without end. A piece whose item doesn't exist (its mod missing)
 * is left out, so pieces of other mods can be listed safely. Code can add pieces too ({@link #put}).
 */
public final class Armory {
	public enum Kind {
		WEAPON, ARMOR;

		public String id() {
			return name().toLowerCase(Locale.ROOT);
		}
	}

	public record Piece(String id, ResourceLocation item, Set<Race> races, Kind kind, int level, int price, Shop.Rarity rarity, int limit, int count) {
		public Optional<Item> resolve() {
			return BuiltInRegistries.ITEM.getOptional(item).filter(i -> i != Items.AIR);
		}

		public ItemStack stack() {
			return resolve().map(i -> new ItemStack(i, Math.max(1, count))).orElse(ItemStack.EMPTY);
		}
	}

	/** Pieces from the data packs (replaced on every reload) and from code (kept). */
	private static final Map<String, Piece> DATA = new LinkedHashMap<>();
	private static final Map<String, Piece> CODE = new LinkedHashMap<>();
	private static final Gson GSON = new Gson();

	private Armory() {
	}

	public static void register() {
		NeoForge.EVENT_BUS.addListener((AddReloadListenerEvent event) -> event.addListener(new Loader()));
	}

	/** Adds (or replaces) a piece from code: the mod's own race gear, the tests. */
	public static void put(Piece piece) {
		CODE.put(piece.id(), piece);
	}

	public static void remove(String id) {
		CODE.remove(id);
	}

	public static List<Piece> all() {
		Map<String, Piece> out = new LinkedHashMap<>(DATA);
		out.putAll(CODE);
		return new ArrayList<>(out.values());
	}

	public static Optional<Piece> piece(String id) {
		Piece p = CODE.get(id);
		return Optional.ofNullable(p != null ? p : DATA.get(id));
	}

	/** The pieces this player's race may buy, existing items only: weapons first, then armour, each by level. */
	public static List<Piece> forPlayer(ServerPlayer player) {
		Race race = RaceState.get(player).orElse(null);
		List<Piece> out = new ArrayList<>();
		for (Piece p : all()) {
			if (race != null && p.races().contains(race) && p.resolve().isPresent()) {
				out.add(p);
			}
		}
		out.sort((a, b) -> a.kind() != b.kind() ? a.kind().compareTo(b.kind()) : a.level() != b.level() ? Integer.compare(a.level(), b.level())
				: Integer.compare(a.price(), b.price()));
		return out;
	}

	public static int left(Account a, Piece p) {
		return p.limit() <= 0 ? -1 : Math.max(0, p.limit() - a.armory.getOrDefault(p.id(), 0));
	}

	/** Why this piece can't be bought now (a translation key), or null. */
	public static String refusal(ServerPlayer player, Account a, Piece p) {
		Race race = RaceState.get(player).orElse(null);
		if (race == null || !p.races().contains(race) || p.resolve().isEmpty()) {
			return "mages_legacy.merc.armory.not_yours";
		}
		if (RaceState.level(player) < p.level()) {
			return "mages_legacy.merc.armory.too_low";
		}
		if (left(a, p) == 0) {
			return "mages_legacy.merc.armory.sold";
		}
		if (a.tokens < Shop.discounted(player, p.price())) {
			return "mages_legacy.merc.no_tokens";
		}
		return null;
	}

	public static boolean buy(ServerPlayer player, Account a, String id) {
		Piece p = piece(id).orElse(null);
		if (p == null) {
			return false;
		}
		String why = refusal(player, a, p);
		if (why != null) {
			player.displayClientMessage(Component.translatable(why, p.level()).withStyle(ChatFormatting.RED), true);
			return false;
		}
		int price = Shop.discounted(player, p.price());
		a.tokens -= price;
		a.spent += price;
		a.armory.merge(p.id(), 1, Integer::sum);
		Mercenary.save(player, a);
		ItemStack stack = p.stack();
		if (!player.getInventory().add(stack)) {
			player.drop(stack, false);
		}
		Mercenary.spent(player, Mercenary.account(player), price);
		player.level().playSound(null, player.blockPosition(), SoundEvents.ARMOR_EQUIP_NETHERITE.value(), SoundSource.PLAYERS, 1, 0.9f);
		player.level().playSound(null, player.blockPosition(), SoundEvents.VILLAGER_TRADE, SoundSource.PLAYERS, 0.7f, 0.8f);
		return true;
	}

	/** The armory as the shop's screen sees it. */
	public static ListTag view(ServerPlayer player, Account a) {
		ListTag list = new ListTag();
		int level = RaceState.level(player);
		for (Piece p : forPlayer(player)) {
			CompoundTag t = new CompoundTag();
			t.putString("id", p.id());
			t.putString("kind", p.kind().id());
			t.putInt("level", p.level());
			t.putBoolean("locked", level < p.level());
			t.putInt("price", Shop.discounted(player, p.price()));
			t.putString("rarity", p.rarity().id());
			t.putInt("left", left(a, p));
			t.put("icon", p.stack().saveOptional(player.registryAccess()));
			list.add(t);
		}
		return list;
	}

	static Piece parse(String id, JsonObject o) {
		ResourceLocation item = ResourceLocation.parse(o.get("item").getAsString());
		Set<Race> races = EnumSet.noneOf(Race.class);
		if (o.has("races")) {
			for (JsonElement r : o.getAsJsonArray("races")) {
				Race.byId(r.getAsString()).ifPresent(races::add);
			}
		}
		Kind kind = o.has("kind") && "armor".equals(o.get("kind").getAsString()) ? Kind.ARMOR : Kind.WEAPON;
		Shop.Rarity rarity = Shop.Rarity.COMMON;
		if (o.has("rarity")) {
			for (Shop.Rarity r : Shop.Rarity.values()) {
				if (r.id().equals(o.get("rarity").getAsString())) {
					rarity = r;
				}
			}
		}
		return new Piece(id, item, races, kind, o.has("level") ? o.get("level").getAsInt() : 1, o.has("price") ? o.get("price").getAsInt() : 10, rarity,
				o.has("limit") ? o.get("limit").getAsInt() : 0, o.has("count") ? o.get("count").getAsInt() : 1);
	}

	/** Reads data/<namespace>/merc_armory/*.json on every data reload. */
	private static final class Loader extends SimpleJsonResourceReloadListener {
		Loader() {
			super(GSON, "merc_armory");
		}

		@Override
		protected void apply(Map<ResourceLocation, JsonElement> files, ResourceManager manager, ProfilerFiller profiler) {
			DATA.clear();
			files.forEach((file, json) -> {
				try {
					String id = file.getNamespace() + ":" + file.getPath();
					DATA.put(id, parse(id, json.getAsJsonObject()));
				} catch (RuntimeException e) {
					MagesLegacy.LOGGER.warn("Bad armory piece {}: {}", file, e.getMessage());
				}
			});
			MagesLegacy.LOGGER.info("Guild armory: {} pieces", DATA.size());
		}
	}
}
