package euf.mageslegacy.mycelid;

import euf.mageslegacy.MagesLegacy;
import euf.mageslegacy.fx.Fx;
import euf.mageslegacy.net.NetworkViewPayload;
import euf.mageslegacy.net.StepPayload;
import euf.mageslegacy.race.Abilities;
import euf.mageslegacy.race.Progression;
import euf.mageslegacy.race.Race;
import euf.mageslegacy.race.RaceState;
import euf.mageslegacy.race.Talent;
import euf.mageslegacy.race.Talents;
import euf.mageslegacy.voidborn.Arena;
import euf.mageslegacy.voidborn.Pocket;
import euf.mageslegacy.voidborn.Voidborn;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.ItemTags;
import net.minecraft.tags.TagKey;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.OwnableEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.food.FoodProperties;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.common.Tags;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;
import net.neoforged.neoforge.event.entity.player.AttackEntityEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.event.level.BlockEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import net.neoforged.neoforge.network.PacketDistributor;

/**
 * The Mycelium Network: living nodes Mycelids grow across the world, joined by threads, used by everyone on the
 * server. Close nodes of one grower join by themselves; far ones need a thread grown for a while and fed. Travelling,
 * the body comes apart into spores at one node and is gathered again at another. Nutrients (fed to any node: mushrooms,
 * flesh, bone meal, food) pay for new nodes and threads; a built network costs nothing to keep. Saved with the world.
 */
public final class MyceliumNetwork extends SavedData {
	private static final String NAME = "mages_legacy_mycelium";
	/** Nutrients a new grower starts with: enough for a few nodes and a first far thread. */
	private static final int START_NUTRIENTS = 60;
	private static final int NODE_COST = 20;
	private static final int BASE_NODES = 4;
	private static final double AUTO_RANGE = 128;
	private static final double FAR_RANGE = 2000;
	/** A far thread: a nutrient per 20 blocks, a second of growth per 25 blocks. */
	private static final double COST_PER_BLOCK = 1 / 20.0;
	private static final double TICKS_PER_BLOCK = 20 / 25.0;
	private static final int TRAVEL_BASE = 30;
	private static final int TRAVEL_PER_HOP = 6;
	private static final int TRAVEL_MAX = 70;
	private static final int RECALL_COOLDOWN = 10 * 60 * 20;
	/** Food for the network; a datapack can add to it. */
	public static final TagKey<net.minecraft.world.item.Item> FUNGAL_FOOD = TagKey.create(Registries.ITEM, MagesLegacy.id("fungal_food"));

	/** A node: where it stands, whose it is, what it is called, whether everyone may use it. */
	public static final class Node {
		final String key;
		final ResourceKey<Level> dim;
		final BlockPos pos;
		final UUID owner;
		String name;
		boolean open = true;
		final boolean mother;

		Node(String key, ResourceKey<Level> dim, BlockPos pos, UUID owner, String name, boolean mother) {
			this.key = key;
			this.dim = dim;
			this.pos = pos;
			this.owner = owner;
			this.name = name;
			this.mother = mother;
		}
	}

	private final Map<String, Node> nodes = new LinkedHashMap<>();
	/** Threads between two nodes ("a|b", sorted): the game time they are grown by (0 once grown). */
	private final Map<String, Long> links = new HashMap<>();
	private final Map<UUID, Integer> nutrients = new HashMap<>();
	/** Reserve bodies (Mycelial Rebirth): whose, at which node, grown by when. One per Mycelid. */
	private final Map<UUID, String> bodies = new HashMap<>();
	private final Map<UUID, Long> bodyReady = new HashMap<>();
	private static final int BODY_COST = 40;
	private static final int BODY_TICKS = 3 * 60 * 20;
	/** The talents of each grower as last seen online (a network works while its grower is away). */
	private final Map<UUID, Set<String>> growerTalents = new HashMap<>();

	/** Bodies on their way through the network now. */
	private record Journey(UUID player, ResourceKey<Level> dim, Vec3 to, String toKey, UUID grower, int ticks, List<UUID> pets) {
	}

	private static final Map<UUID, Journey> JOURNEYS = new HashMap<>();
	private static final Map<UUID, Integer> AGES = new HashMap<>();

	public static void register() {
		NeoForge.EVENT_BUS.addListener(MyceliumNetwork::onServerTick);
		NeoForge.EVENT_BUS.addListener(MyceliumNetwork::onBreak);
		NeoForge.EVENT_BUS.addListener(MyceliumNetwork::onIncomingDamage);
		NeoForge.EVENT_BUS.addListener(MyceliumNetwork::onAttack);
		NeoForge.EVENT_BUS.addListener(MyceliumNetwork::onInteract);
		NeoForge.EVENT_BUS.addListener(MyceliumNetwork::onLogout);
	}

	public static MyceliumNetwork get(MinecraftServer server) {
		return server.overworld().getDataStorage().computeIfAbsent(new SavedData.Factory<>(MyceliumNetwork::new, MyceliumNetwork::load, null), NAME);
	}

	public static String key(ResourceKey<Level> dim, BlockPos pos) {
		return dim.location() + "@" + pos.getX() + "," + pos.getY() + "," + pos.getZ();
	}

	private static String link(String a, String b) {
		return a.compareTo(b) < 0 ? a + "|" + b : b + "|" + a;
	}

	public Node node(String key) {
		return nodes.get(key);
	}

	public List<Node> nodesOf(UUID owner) {
		List<Node> list = new ArrayList<>();
		for (Node n : nodes.values()) {
			if (n.owner.equals(owner)) {
				list.add(n);
			}
		}
		return list;
	}

	public int nutrients(UUID owner) {
		return nutrients.getOrDefault(owner, START_NUTRIENTS);
	}

	void addNutrients(UUID owner, int amount) {
		nutrients.put(owner, Math.max(0, nutrients(owner) + amount));
		setDirty();
	}

	/** Whether a grower had this talent when last seen. */
	boolean growerHas(UUID owner, Talent talent) {
		Set<String> set = growerTalents.get(owner);
		return set != null && set.contains(talent.id());
	}

	private void remember(ServerPlayer player) {
		Set<String> now = new HashSet<>(Talents.owned(player));
		if (!now.equals(growerTalents.get(player.getUUID()))) {
			growerTalents.put(player.getUUID(), now);
			setDirty();
		}
	}

	// ---- What a grower may do --------------------------------------------------------------------------------

	public static int maxNodes(ServerPlayer player) {
		int n = BASE_NODES;
		if (Talents.has(player, Talent.NET_NODES_1)) {
			n += 2;
		}
		if (Talents.has(player, Talent.NET_NODES_2)) {
			n += 3;
		}
		if (Talents.has(player, Talent.NET_NODES_3)) {
			n += 4;
		}
		return n;
	}

	private static boolean far(ServerPlayer player) {
		return Talents.has(player, Talent.NET_REACH);
	}

	static double autoRange(boolean reach) {
		return reach ? AUTO_RANGE * 2 : AUTO_RANGE;
	}

	static double farRange(boolean reach) {
		return reach ? FAR_RANGE * 2 : FAR_RANGE;
	}

	static int nodeCost(ServerPlayer player) {
		return Talents.has(player, Talent.NET_THRIFT) ? NODE_COST * 6 / 10 : NODE_COST;
	}

	static int linkCost(ServerPlayer player, double distance) {
		double cost = Math.ceil(distance * COST_PER_BLOCK);
		return (int) (Talents.has(player, Talent.NET_THRIFT) ? Math.ceil(cost * 0.6) : cost);
	}

	static int linkTicks(ServerPlayer player, double distance) {
		double ticks = distance * TICKS_PER_BLOCK;
		return (int) (Talents.has(player, Talent.NET_GROWTH) ? ticks / 2 : ticks);
	}

	/** The H key: at one of one's own nodes, that node; anywhere else, the colony (and growing a node where one stands). */
	public static Abilities.Refusal openColony(ServerPlayer player) {
		MyceliumNetwork net = get(player.server);
		net.remember(player);
		// Shift with Call of the Colony: back to the Mother Mycelium through the network, from anywhere
		if (player.isShiftKeyDown() && Talents.has(player, Talent.NET_RECALL)) {
			return net.recall(player);
		}
		for (Node n : net.nodesOf(player.getUUID())) {
			if (n.dim == player.level().dimension() && Vec3.atCenterOf(n.pos).distanceTo(player.position()) <= 4) {
				openNode(player, player.serverLevel(), n.pos);
				return null;
			}
		}
		PacketDistributor.sendToPlayer(player, new NetworkViewPayload(net.view(player, null)));
		return null;
	}

	/** Grows a node where the grower stands. */
	public static void grow(ServerPlayer player) {
		if (!RaceState.is(player, Race.MYCELID)) {
			return;
		}
		MyceliumNetwork net = get(player.server);
		ServerLevel level = player.serverLevel();
		BlockPos pos = player.blockPosition();
		List<Node> own = net.nodesOf(player.getUUID());
		boolean first = own.isEmpty();
		int cost = first ? 0 : nodeCost(player);
		String why = null;
		if (Pocket.isPocket(level) || Arena.isArena(level)) {
			why = "mages_legacy.refuse.node_here";
		} else if (own.size() >= maxNodes(player)) {
			why = "mages_legacy.refuse.node_limit";
		} else if (net.nutrients(player.getUUID()) < cost) {
			why = "mages_legacy.refuse.nutrients";
		} else if (!level.getBlockState(pos).canBeReplaced() || !level.getBlockState(pos.below()).isFaceSturdy(level, pos.below(), Direction.UP)) {
			why = "mages_legacy.refuse.node_ground";
		} else if (!level.mayInteract(player, pos)) {
			why = "mages_legacy.refuse.protected";
		}
		if (why != null) {
			Abilities.refuse(player, Component.translatable(why));
			return;
		}
		level.setBlockAndUpdate(pos, MyceliumNodeBlock.NODE.get().defaultBlockState().setValue(MyceliumNodeBlock.MOTHER, first));
		String key = key(level.dimension(), pos);
		String name = first ? Component.translatable("mages_legacy.network.mother").getString()
				: Component.translatable("mages_legacy.network.default_name", own.size() + 1).getString();
		Node node = new Node(key, level.dimension(), pos, player.getUUID(), name, first);
		net.nodes.put(key, node);
		net.addNutrients(player.getUUID(), -cost);
		net.remember(player);
		net.joinNear(node);
		net.setDirty();
		// The Great Bloom's trial counts the biggest network the Mycelid has had
		int count = net.nodesOf(player.getUUID()).size();
		if (count > Talents.progress(player, Mycelid.NODES)) {
			Talents.addProgress(player, Mycelid.NODES, count - Talents.progress(player, Mycelid.NODES));
		}
		level.sendParticles(ParticleTypes.HAPPY_VILLAGER, pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5, 12, 0.6, 0.4, 0.6, 0);
		level.playSound(null, pos, SoundEvents.FUNGUS_PLACE, SoundSource.BLOCKS, 1f, 0.7f);
		level.playSound(null, pos, SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.BLOCKS, 0.6f, 0.6f);
		player.displayClientMessage(Component.translatable(first ? "mages_legacy.message.mother_grown" : "mages_legacy.message.node_grown", name)
				.withStyle(ChatFormatting.GREEN), true);
		Progression.addXp(player, "network", 5, 30);
		RaceState.sync(player);
	}

	/** A new node joins the grower's nodes close by (and, with Shared Mycelium, other growers' open nodes close by). */
	private void joinNear(Node node) {
		boolean reach = growerHas(node.owner, Talent.NET_REACH);
		boolean shared = growerHas(node.owner, Talent.NET_SHARED);
		for (Node other : nodes.values()) {
			if (other == node || other.dim != node.dim) {
				continue;
			}
			boolean mine = other.owner.equals(node.owner);
			boolean share = !mine && other.open && node.open && (shared || growerHas(other.owner, Talent.NET_SHARED));
			double range = autoRange(reach || !mine && growerHas(other.owner, Talent.NET_REACH));
			if ((mine || share) && Math.sqrt(other.pos.distSqr(node.pos)) <= range) {
				links.put(link(node.key, other.key), 0L);
			}
		}
	}

	/** A far thread between two of one's own nodes in one world: paid now, grown over time. */
	private void growLink(ServerPlayer player, Node a, Node b) {
		String why = null;
		double distance = b == null ? 0 : Math.sqrt(a.pos.distSqr(b.pos));
		if (b == null || a == b || !a.owner.equals(player.getUUID()) || !b.owner.equals(player.getUUID())) {
			why = "mages_legacy.refuse.not_your_node";
		} else if (a.dim != b.dim) {
			why = "mages_legacy.refuse.link_world";
		} else if (links.containsKey(link(a.key, b.key))) {
			why = "mages_legacy.refuse.link_exists";
		} else if (distance > farRange(far(player))) {
			why = "mages_legacy.refuse.link_far";
		} else if (nutrients(player.getUUID()) < linkCost(player, distance)) {
			why = "mages_legacy.refuse.nutrients";
		}
		if (why != null) {
			Abilities.refuse(player, Component.translatable(why));
			return;
		}
		addNutrients(player.getUUID(), -linkCost(player, distance));
		long ready = player.serverLevel().getGameTime() + linkTicks(player, distance);
		links.put(link(a.key, b.key), ready);
		setDirty();
		player.displayClientMessage(Component.translatable("mages_legacy.message.link_growing", a.name, b.name, linkTicks(player, distance) / 20)
				.withStyle(ChatFormatting.GREEN), true);
	}

	private boolean grown(String a, String b, long now) {
		Long ready = links.get(link(a, b));
		return ready != null && ready <= now;
	}

	/** Who may set out from or arrive at a node: anyone at an open one; only its grower (and operators) at a closed one. */
	private static boolean mayUse(ServerPlayer player, Node node) {
		return node.open || node.owner.equals(player.getUUID()) || player.hasPermissions(2);
	}

	/** Every node reachable from this one along grown threads, with the number of hops. */
	private Map<Node, Integer> reachable(Node from, long now) {
		Map<Node, Integer> hops = new LinkedHashMap<>();
		ArrayDeque<Node> queue = new ArrayDeque<>();
		hops.put(from, 0);
		queue.add(from);
		while (!queue.isEmpty()) {
			Node at = queue.poll();
			for (Map.Entry<String, Long> e : links.entrySet()) {
				if (e.getValue() > now) {
					continue;
				}
				String[] ends = e.getKey().split("\\|");
				String next = ends[0].equals(at.key) ? ends[1] : ends[1].equals(at.key) ? ends[0] : null;
				Node n = next == null ? null : nodes.get(next);
				if (n != null && !hops.containsKey(n)) {
					hops.put(n, hops.get(at) + 1);
					queue.add(n);
				}
			}
		}
		hops.remove(from);
		return hops;
	}

	// ---- Reserve bodies (Mycelial Rebirth) ----------------------------------------------------------------------

	/** Grows a reserve body at the own node the Mycelid stands by; with one already, says how it is. */
	public static Abilities.Refusal growBody(ServerPlayer player) {
		MyceliumNetwork net = get(player.server);
		long now = player.serverLevel().getGameTime();
		String had = net.bodies.get(player.getUUID());
		if (had != null && net.nodes.containsKey(had)) {
			long left = net.bodyReady.getOrDefault(player.getUUID(), 0L) - now;
			player.displayClientMessage(left > 0 ? Component.translatable("mages_legacy.message.body_growing", net.nodes.get(had).name, left / 20 + 1)
					: Component.translatable("mages_legacy.message.body_ready", net.nodes.get(had).name), true);
			return null;
		}
		Node at = null;
		for (Node n : net.nodesOf(player.getUUID())) {
			if (n.dim == player.level().dimension() && Vec3.atCenterOf(n.pos).distanceTo(player.position()) <= 6) {
				at = n;
			}
		}
		if (at == null) {
			return new Abilities.Refusal("mages_legacy.refuse.body_node");
		}
		int cost = Talents.has(player, Talent.REBIRTH_CHEAP) ? BODY_COST / 2 : BODY_COST;
		if (net.nutrients(player.getUUID()) < cost) {
			return new Abilities.Refusal("mages_legacy.refuse.nutrients");
		}
		int ticks = Talents.has(player, Talent.REBIRTH_FAST) ? BODY_TICKS / 2 : BODY_TICKS;
		net.addNutrients(player.getUUID(), -cost);
		net.bodies.put(player.getUUID(), at.key);
		net.bodyReady.put(player.getUUID(), now + ticks);
		net.setDirty();
		ServerLevel level = player.serverLevel();
		Fx.body(level, bodySpot(level, at.pos), Vec3.atBottomCenterOf(at.pos), ticks, ticks);
		level.playSound(null, at.pos, SoundEvents.ROOTED_DIRT_PLACE, SoundSource.BLOCKS, 1f, 0.6f);
		player.displayClientMessage(Component.translatable("mages_legacy.message.body_started", at.name, ticks / 20).withStyle(ChatFormatting.GREEN), true);
		return null;
	}

	/** Where a reserve body stands beside its node: the first open side, else on top of it. */
	static Vec3 bodySpot(ServerLevel level, BlockPos node) {
		for (net.minecraft.core.Direction d : net.minecraft.core.Direction.Plane.HORIZONTAL) {
			BlockPos p = node.relative(d);
			if (level.getBlockState(p).getCollisionShape(level, p).isEmpty() && level.getBlockState(p.above()).getCollisionShape(level, p.above()).isEmpty()) {
				return Vec3.atBottomCenterOf(p);
			}
		}
		return Vec3.atBottomCenterOf(node.above());
	}

	/**
	 * Shows every reserve body standing by its node to the players near it, again every 5 s: so it stands there until
	 * used, seen after a relog or a walk up to it.
	 */
	private static void showBodies(MinecraftServer server, MyceliumNetwork net) {
		for (Map.Entry<UUID, String> e : net.bodies.entrySet()) {
			Node node = net.nodes.get(e.getValue());
			ServerLevel level = node == null ? null : server.getLevel(node.dim);
			if (level == null || !level.isLoaded(node.pos)) {
				continue;
			}
			long left = Math.max(0, net.bodyReady.getOrDefault(e.getKey(), 0L) - level.getGameTime());
			int total = net.growerHas(e.getKey(), Talent.REBIRTH_FAST) ? BODY_TICKS / 2 : BODY_TICKS;
			Fx.body(level, bodySpot(level, node.pos), Vec3.atBottomCenterOf(node.pos), (int) left, total);
		}
	}

	/** Where a woken reserve body stands. */
	public record Waking(ServerLevel level, Vec3 at) {
	}

	/** Takes the Mycelid's grown reserve body (if there is one at a node still alive) and says where it wakes. */
	public static Waking takeBody(ServerPlayer player) {
		MyceliumNetwork net = get(player.server);
		String key = net.bodies.get(player.getUUID());
		Node node = key == null ? null : net.nodes.get(key);
		long ready = net.bodyReady.getOrDefault(player.getUUID(), Long.MAX_VALUE);
		if (node == null || ready > player.serverLevel().getGameTime()) {
			return null;
		}
		ServerLevel level = player.server.getLevel(node.dim);
		Vec3 at = level == null ? null : arrival(level, player, node.pos);
		if (at == null) {
			return null;
		}
		net.bodies.remove(player.getUUID());
		net.bodyReady.remove(player.getUUID());
		net.setDirty();
		Fx.body(level, at, Vec3.atBottomCenterOf(node.pos), -1, 0);
		return new Waking(level, at);
	}

	/** For the harness: the reserve body is grown at once. */
	public static void ripenBody(ServerPlayer player) {
		MyceliumNetwork net = get(player.server);
		if (net.bodies.containsKey(player.getUUID())) {
			net.bodyReady.put(player.getUUID(), 0L);
		}
	}

	// ---- The screen ------------------------------------------------------------------------------------------

	/** A click on a node: where it leads, and for its grower its threads and settings. */
	public static void openNode(ServerPlayer player, ServerLevel level, BlockPos pos) {
		MyceliumNetwork net = get(player.server);
		Node node = net.nodes.get(key(level.dimension(), pos));
		if (node == null) {
			return;
		}
		if (RaceState.is(player, Race.MYCELID)) {
			net.remember(player);
		}
		PacketDistributor.sendToPlayer(player, new NetworkViewPayload(net.view(player, node)));
	}

	/** Everything the screen shows, as a tag: the node here (or none), its destinations, the grower's own nodes. */
	private CompoundTag view(ServerPlayer player, Node here) {
		long now = player.serverLevel().getGameTime();
		CompoundTag tag = new CompoundTag();
		boolean mycelid = RaceState.is(player, Race.MYCELID);
		tag.putBoolean("mycelid", mycelid);
		tag.putInt("nutrients", nutrients(player.getUUID()));
		tag.putInt("nodes", nodesOf(player.getUUID()).size());
		tag.putInt("max", mycelid ? maxNodes(player) : 0);
		tag.putInt("nodeCost", nodesOf(player.getUUID()).isEmpty() ? 0 : mycelid ? nodeCost(player) : NODE_COST);
		if (here != null) {
			tag.putString("here", here.key);
			tag.putString("name", here.name);
			tag.putBoolean("open", here.open);
			tag.putBoolean("owner", here.owner.equals(player.getUUID()));
			String ownerName = player.server.getProfileCache() == null ? "?"
					: player.server.getProfileCache().get(here.owner).map(p -> p.getName()).orElse("?");
			tag.putString("grower", ownerName);
			ListTag to = new ListTag();
			if (mayUse(player, here)) {
				for (Map.Entry<Node, Integer> e : reachable(here, now).entrySet()) {
					Node n = e.getKey();
					if (!mayUse(player, n)) {
						continue;
					}
					CompoundTag d = new CompoundTag();
					d.putString("key", n.key);
					d.putString("name", n.name);
					d.putInt("dist", (int) Math.sqrt(n.pos.distSqr(here.pos)));
					d.putInt("hops", e.getValue());
					to.add(d);
				}
			}
			tag.put("to", to);
		}
		// The grower's own nodes: in the colony view all of them, at an own node the threads it could grow
		ListTag own = new ListTag();
		if (mycelid) {
			for (Node n : nodesOf(player.getUUID())) {
				CompoundTag d = new CompoundTag();
				d.putString("key", n.key);
				d.putString("name", n.name);
				d.putString("where", n.dim.location().getPath() + " " + n.pos.getX() + " " + n.pos.getY() + " " + n.pos.getZ());
				d.putBoolean("open", n.open);
				d.putBoolean("mother", n.mother);
				if (here != null && here.owner.equals(player.getUUID()) && n != here) {
					Long ready = links.get(link(here.key, n.key));
					double distance = Math.sqrt(n.pos.distSqr(here.pos));
					d.putInt("dist", (int) distance);
					if (n.dim != here.dim) {
						d.putString("state", "world");
					} else if (ready == null) {
						boolean tooFar = distance > farRange(far(player));
						d.putString("state", tooFar ? "far" : "none");
						d.putInt("cost", linkCost(player, distance));
						d.putInt("secs", linkTicks(player, distance) / 20);
					} else if (ready > now) {
						d.putString("state", "growing");
						d.putInt("secs", (int) ((ready - now) / 20));
					} else {
						d.putString("state", "linked");
					}
				}
				own.add(d);
			}
		}
		tag.put("own", own);
		String body = bodies.get(player.getUUID());
		if (body != null && nodes.containsKey(body)) {
			tag.putString("body", nodes.get(body).name);
			tag.putInt("bodySecs", (int) Math.max(0, (bodyReady.getOrDefault(player.getUUID(), 0L) - now) / 20));
		}
		return tag;
	}

	/** What the screen asks for. */
	public static void act(ServerPlayer player, String action, String a, String b) {
		MyceliumNetwork net = get(player.server);
		Node na = net.nodes.get(a);
		switch (action) {
			case "grow" -> grow(player);
			case "travel" -> net.travel(player, na, net.nodes.get(b));
			case "link" -> {
				if (na != null) {
					net.growLink(player, na, net.nodes.get(b));
				}
			}
			case "rename" -> {
				if (na != null && na.owner.equals(player.getUUID())) {
					String name = b.strip();
					na.name = name.isEmpty() ? na.name : name.length() > 24 ? name.substring(0, 24) : name;
					net.setDirty();
				}
			}
			case "open" -> {
				if (na != null && na.owner.equals(player.getUUID())) {
					na.open = !na.open;
					net.setDirty();
				}
			}
			default -> {
			}
		}
		// The screen shows the result
		if (na != null && !"travel".equals(action) && player.level().dimension() == na.dim) {
			openNode(player, player.serverLevel(), na.pos);
		} else if ("grow".equals(action)) {
			PacketDistributor.sendToPlayer(player, new NetworkViewPayload(net.view(player, null)));
		}
	}

	// ---- Feeding ---------------------------------------------------------------------------------------------

	/** How much a thing feeds the network: mushrooms and fungi most, then bone meal, flesh and any food. */
	public static int nutritionOf(ItemStack stack) {
		if (stack.isEmpty()) {
			return 0;
		}
		if (stack.is(Items.BROWN_MUSHROOM) || stack.is(Items.RED_MUSHROOM) || stack.is(Items.CRIMSON_FUNGUS) || stack.is(Items.WARPED_FUNGUS)
				|| stack.is(Tags.Items.MUSHROOMS) || stack.is(FUNGAL_FOOD)) {
			return 4;
		}
		if (stack.is(Items.BONE_MEAL) || stack.is(Items.ROTTEN_FLESH) || stack.is(ItemTags.SAPLINGS) || stack.is(Items.MOSS_BLOCK)) {
			return 2;
		}
		FoodProperties food = stack.getFoodProperties(null);
		return food == null ? 0 : Math.max(1, food.nutrition() / 2);
	}

	/** A click on a node with something organic: one of it (a sneak, the whole stack) goes to the node's grower. */
	public static void feed(ServerPlayer player, ServerLevel level, BlockPos pos, ItemStack stack) {
		MyceliumNetwork net = get(player.server);
		Node node = net.nodes.get(key(level.dimension(), pos));
		if (node == null) {
			return;
		}
		int count = player.isShiftKeyDown() ? stack.getCount() : 1;
		int gained = nutritionOf(stack) * count;
		if (!player.getAbilities().instabuild) {
			stack.shrink(count);
		}
		net.addNutrients(node.owner, gained);
		level.sendParticles(ParticleTypes.HAPPY_VILLAGER, pos.getX() + 0.5, pos.getY() + 0.9, pos.getZ() + 0.5, 6 + count, 0.4, 0.3, 0.4, 0);
		level.playSound(null, pos, SoundEvents.COMPOSTER_FILL_SUCCESS, SoundSource.BLOCKS, 0.8f, 0.8f);
		player.displayClientMessage(Component.translatable("mages_legacy.message.fed", gained, net.nutrients(node.owner)).withStyle(ChatFormatting.GREEN),
				true);
	}

	// ---- Travelling ------------------------------------------------------------------------------------------

	public static boolean travelling(Player player) {
		return JOURNEYS.containsKey(player.getUUID());
	}

	private void travel(ServerPlayer player, Node from, Node to) {
		long now = player.serverLevel().getGameTime();
		String why = null;
		if (from == null || to == null || from == to) {
			why = "mages_legacy.refuse.no_route";
		} else if (travelling(player) || Voidborn.stepping(player)) {
			why = "mages_legacy.refuse.busy";
		} else if (from.dim != player.level().dimension() || Vec3.atCenterOf(from.pos).distanceTo(player.position()) > 4) {
			why = "mages_legacy.refuse.node_close";
		} else if (!mayUse(player, from) || !mayUse(player, to) || !reachable(from, now).containsKey(to)) {
			why = "mages_legacy.refuse.no_route";
		}
		ServerLevel level = why == null ? player.server.getLevel(to.dim) : null;
		Vec3 out = level == null ? null : arrival(level, player, to.pos);
		if (why == null && out == null) {
			why = "mages_legacy.refuse.no_exit";
		}
		if (why != null) {
			Abilities.refuse(player, Component.translatable(why));
			return;
		}
		int hops = reachable(from, now).get(to);
		int ticks = Math.min(TRAVEL_MAX, TRAVEL_BASE + TRAVEL_PER_HOP * hops);
		if (growerHas(from.owner, Talent.NET_SWIFT)) {
			ticks = ticks * 6 / 10;
		}
		start(player, level, out, to.key, from.owner, ticks, growerHas(from.owner, Talent.NET_TRAIN));
	}

	/** Call of the Colony: from anywhere back to one's Mother Mycelium, once every 10 minutes. */
	private Abilities.Refusal recall(ServerPlayer player) {
		Node mother = null;
		for (Node n : nodesOf(player.getUUID())) {
			if (n.mother) {
				mother = n;
			}
		}
		if (mother == null) {
			return new Abilities.Refusal("mages_legacy.refuse.no_mother");
		}
		Voidborn.State state = Voidborn.state(player);
		long now = player.serverLevel().getGameTime();
		if (now < state.recallReady) {
			return new Abilities.Refusal("mages_legacy.refuse.recall_wait");
		}
		if (travelling(player) || Pocket.isPocket(player.level()) || Arena.isArena(player.level())) {
			return new Abilities.Refusal("mages_legacy.refuse.busy");
		}
		ServerLevel level = player.server.getLevel(mother.dim);
		Vec3 out = level == null ? null : arrival(level, player, mother.pos);
		if (out == null) {
			return new Abilities.Refusal("mages_legacy.refuse.no_exit");
		}
		state.recallReady = now + RECALL_COOLDOWN;
		start(player, level, out, mother.key, player.getUUID(), TRAVEL_MAX, Talents.has(player, Talent.NET_TRAIN));
		return null;
	}

	/** Beside a node where a body fits: around it, then on top. */
	private static Vec3 arrival(ServerLevel level, ServerPlayer player, BlockPos node) {
		level.getChunk(node);
		for (BlockPos p : new BlockPos[] {node.north(), node.south(), node.east(), node.west(), node.north().east(), node.south().west(),
				node.above()}) {
			for (int dy : new int[] {0, 1, -1}) {
				Vec3 c = Vec3.atBottomCenterOf(p.above(dy));
				if (Voidborn.fits(level, player, c)) {
					return c;
				}
			}
		}
		return null;
	}

	/** The body comes apart into spores; its pets (with Living Caravan) go with it. */
	private void start(ServerPlayer player, ServerLevel to, Vec3 out, String toKey, UUID grower, int ticks, boolean caravan) {
		ServerLevel level = player.serverLevel();
		List<UUID> pets = new ArrayList<>();
		if (caravan) {
			for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class, player.getBoundingBox().inflate(6),
					e -> e instanceof OwnableEntity o && player.getUUID().equals(o.getOwnerUUID()) || e instanceof Mob m && m.getLeashHolder() == player)) {
				pets.add(e.getUUID());
			}
		}
		JOURNEYS.put(player.getUUID(), new Journey(player.getUUID(), to.dimension(), out, toKey, grower, ticks, pets));
		AGES.put(player.getUUID(), 0);
		player.setNoGravity(true);
		player.setDeltaMovement(Vec3.ZERO);
		player.addEffect(new MobEffectInstance(MobEffects.INVISIBILITY, ticks + 4, 0, true, false, false));
		Fx.hide(level, player, ticks);
		Fx.muffle(player, ticks + 4);
		// The walker's view: through the living network, glowing threads streaming past
		PacketDistributor.sendToPlayer(player, new StepPayload(player.position(), level == to ? out : player.position().add(player.getLookAngle().scale(10)),
				ticks, true));
		dissolve(level, player.position());
		level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.FUNGUS_BREAK, SoundSource.PLAYERS, 1f, 0.6f);
		level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.SCULK_BLOCK_SPREAD, SoundSource.PLAYERS, 1f, 0.8f);
	}

	private static final net.minecraft.core.particles.DustParticleOptions DISSOLVE = new net.minecraft.core.particles.DustParticleOptions(
			new org.joml.Vector3f(0.55f, 0.95f, 0.65f), 1.1f);

	/** A body coming apart into spores and threads (or gathered from them). */
	static void dissolve(ServerLevel level, Vec3 at) {
		level.sendParticles(DISSOLVE, at.x, at.y + 1, at.z, 30, 0.35, 0.8, 0.35, 0);
		level.sendParticles(ParticleTypes.MYCELIUM, at.x, at.y + 0.2, at.z, 16, 0.6, 0.1, 0.6, 0.02);
		level.sendParticles(ParticleTypes.GLOW, at.x, at.y + 1, at.z, 8, 0.4, 0.8, 0.4, 0.02);
	}

	private static void onServerTick(ServerTickEvent.Post event) {
		MinecraftServer server = event.getServer();
		MyceliumNetwork net = get(server);
		if (server.getTickCount() % 100 == 0) {
			showBodies(server, net);
		}
		for (Iterator<Map.Entry<UUID, Journey>> it = JOURNEYS.entrySet().iterator(); it.hasNext(); ) {
			Journey j = it.next().getValue();
			ServerPlayer player = server.getPlayerList().getPlayer(j.player());
			if (player == null) {
				it.remove();
				continue;
			}
			int age = AGES.merge(j.player(), 1, Integer::sum);
			player.setDeltaMovement(Vec3.ZERO);
			player.fallDistance = 0;
			if (age < j.ticks()) {
				continue;
			}
			it.remove();
			AGES.remove(j.player());
			net.arrive(player, j);
		}
		if (server.getTickCount() % 40 == 0) {
			net.pulse(server);
		}
	}

	private void arrive(ServerPlayer player, Journey j) {
		ServerLevel from = player.serverLevel();
		ServerLevel to = player.server.getLevel(j.dim());
		player.setNoGravity(false);
		player.removeEffect(MobEffects.INVISIBILITY);
		if (to == null) {
			return;
		}
		List<Entity> pets = new ArrayList<>();
		for (UUID id : j.pets()) {
			Entity pet = from.getEntity(id);
			if (pet != null && pet.isAlive()) {
				pets.add(pet);
			}
		}
		player.teleportTo(to, j.to().x, j.to().y, j.to().z, player.getYRot(), player.getXRot());
		player.fallDistance = 0;
		for (Entity pet : pets) {
			if (pet.level() == to) {
				pet.teleportTo(j.to().x, j.to().y, j.to().z);
			} else {
				pet.teleportTo(to, j.to().x, j.to().y, j.to().z, Set.of(), pet.getYRot(), pet.getXRot());
			}
		}
		dissolve(to, j.to());
		to.playSound(null, j.to().x, j.to().y, j.to().z, SoundEvents.FUNGUS_PLACE, SoundSource.PLAYERS, 1f, 0.8f);
		to.playSound(null, j.to().x, j.to().y, j.to().z, SoundEvents.AMETHYST_BLOCK_RESONATE, SoundSource.PLAYERS, 0.7f, 0.7f);
		// Every journey teaches the grower's race; Gift of Travellers feeds them for others' journeys
		ServerPlayer grower = player.server.getPlayerList().getPlayer(j.grower());
		if (!j.grower().equals(player.getUUID())) {
			if (growerHas(j.grower(), Talent.NET_TOLL)) {
				addNutrients(j.grower(), 2);
			}
			if (grower != null) {
				Progression.addXp(grower, "network", 2, 30);
			}
		}
		if (grower != null) {
			Talents.addProgress(grower, Mycelid.JOURNEYS, 1);
		}
		// Glowing Paths: the network feeds whoever it carries
		if (growerHas(j.grower(), Talent.NET_GLOW)) {
			player.getFoodData().eat(4, 0.4f);
		}
		// Spore Trail: a Mycelid who travels comes out in a cloud of neurospores
		if (Talents.has(player, Talent.SPORE_TRAVEL)) {
			Mycelid.cloudAt(player, to, j.to(), 4, 4 * 20);
		}
		RaceState.sync(player);
	}

	/** Every two seconds the nodes of growers who are online act on what is around them (their talents). */
	private void pulse(MinecraftServer server) {
		for (Node n : nodes.values()) {
			ServerLevel level = server.getLevel(n.dim);
			if (level == null || !level.isLoaded(n.pos)) {
				continue;
			}
			ServerPlayer grower = server.getPlayerList().getPlayer(n.owner);
			if (grower == null) {
				continue;
			}
			Vec3 c = Vec3.atCenterOf(n.pos);
			boolean sense = Talents.has(grower, Talent.NET_SENSE);
			boolean spores = Talents.has(grower, Talent.SPORE_NODES);
			if (sense || spores) {
				for (Mob mob : level.getEntitiesOfClass(Mob.class, new AABB(c, c).inflate(8), m -> m.isAlive() && m instanceof net.minecraft.world.entity.monster.Enemy)) {
					if (sense) {
						mob.addEffect(new MobEffectInstance(MobEffects.GLOWING, 50, 0, false, false));
					}
					if (spores && mob.distanceToSqr(c) <= 36 && !mob.getType().is(Tags.EntityTypes.BOSSES)) {
						mob.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, 60, 0, false, true));
						level.sendParticles(ParticleTypes.HAPPY_VILLAGER, mob.getX(), mob.getY() + 1, mob.getZ(), 4, 0.3, 0.4, 0.3, 0);
					}
				}
			}
			boolean heal = Talents.has(grower, Talent.HEAL_NODES);
			boolean food = Talents.has(grower, Talent.NODE_FOOD);
			if (heal || food) {
				for (ServerPlayer p : level.getEntitiesOfClass(ServerPlayer.class, new AABB(c, c).inflate(8), p -> p.isAlive() && !p.isSpectator())) {
					if (heal) {
						p.addEffect(new MobEffectInstance(MobEffects.REGENERATION, 50, 0, true, true));
					}
					if (food && p.distanceToSqr(c) <= 36) {
						p.getFoodData().eat(1, 0.2f);
					}
				}
			}
			if (bodies.containsValue(n.key)) {
				level.sendParticles(ParticleTypes.GLOW, c.x, c.y + 0.9, c.z, 4, 0.3, 0.5, 0.3, 0.01);
			}
			if (server.getTickCount() % 200 == 0) {
				level.sendParticles(ParticleTypes.GLOW, c.x, c.y + 0.6, c.z, 3, 0.4, 0.3, 0.4, 0);
			}
		}
	}

	// ---- Guarding nodes and travellers -----------------------------------------------------------------------

	/** Only a node's grower (or an operator) can tear it out. */
	private static void onBreak(BlockEvent.BreakEvent event) {
		if (event.getState().is(MyceliumNodeBlock.NODE.get()) && event.getPlayer() instanceof ServerPlayer player
				&& event.getLevel() instanceof ServerLevel level) {
			Node n = get(level.getServer()).nodes.get(key(level.dimension(), event.getPos()));
			if (n != null && !n.owner.equals(player.getUUID()) && !player.hasPermissions(2)) {
				event.setCanceled(true);
				Abilities.refuse(player, Component.translatable("mages_legacy.refuse.not_your_node"));
			}
		}
	}

	/** A node gone: its threads go with it. */
	static void removed(ServerLevel level, BlockPos pos) {
		MyceliumNetwork net = get(level.getServer());
		String key = key(level.dimension(), pos);
		if (net.nodes.remove(key) != null) {
			net.links.keySet().removeIf(l -> l.startsWith(key + "|") || l.endsWith("|" + key));
			net.setDirty();
		}
	}

	/** A body in the threads is out of reach. */
	private static void onIncomingDamage(LivingIncomingDamageEvent event) {
		if (event.getEntity() instanceof Player p && travelling(p)) {
			event.setCanceled(true);
		}
	}

	private static void onAttack(AttackEntityEvent event) {
		if (travelling(event.getEntity())) {
			event.setCanceled(true);
		}
	}

	private static void onInteract(PlayerInteractEvent.RightClickItem event) {
		if (travelling(event.getEntity())) {
			event.setCanceled(true);
		}
	}

	/** Leaving mid-journey: the body is gathered at the far node at once, never lost in the threads. */
	private static void onLogout(PlayerEvent.PlayerLoggedOutEvent event) {
		if (event.getEntity() instanceof ServerPlayer player) {
			Journey j = JOURNEYS.remove(player.getUUID());
			AGES.remove(player.getUUID());
			if (j != null) {
				get(player.server).arrive(player, j);
			}
		}
	}

	// ---- Saving ----------------------------------------------------------------------------------------------

	@Override
	public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
		ListTag list = new ListTag();
		for (Node n : nodes.values()) {
			CompoundTag t = new CompoundTag();
			t.putString("dim", n.dim.location().toString());
			t.putLong("pos", n.pos.asLong());
			t.putUUID("owner", n.owner);
			t.putString("name", n.name);
			t.putBoolean("open", n.open);
			t.putBoolean("mother", n.mother);
			list.add(t);
		}
		tag.put("nodes", list);
		CompoundTag l = new CompoundTag();
		links.forEach(l::putLong);
		tag.put("links", l);
		CompoundTag food = new CompoundTag();
		nutrients.forEach((k, v) -> food.putInt(k.toString(), v));
		tag.put("nutrients", food);
		CompoundTag known = new CompoundTag();
		growerTalents.forEach((k, v) -> {
			ListTag ids = new ListTag();
			v.forEach(id -> ids.add(net.minecraft.nbt.StringTag.valueOf(id)));
			known.put(k.toString(), ids);
		});
		tag.put("talents", known);
		CompoundTag reserve = new CompoundTag();
		bodies.forEach((k, v) -> {
			CompoundTag b = new CompoundTag();
			b.putString("node", v);
			b.putLong("ready", bodyReady.getOrDefault(k, 0L));
			reserve.put(k.toString(), b);
		});
		tag.put("bodies", reserve);
		return tag;
	}

	private static MyceliumNetwork load(CompoundTag tag, HolderLookup.Provider registries) {
		MyceliumNetwork net = new MyceliumNetwork();
		for (Tag t : tag.getList("nodes", Tag.TAG_COMPOUND)) {
			CompoundTag c = (CompoundTag) t;
			ResourceKey<Level> dim = ResourceKey.create(Registries.DIMENSION, ResourceLocation.parse(c.getString("dim")));
			BlockPos pos = BlockPos.of(c.getLong("pos"));
			Node n = new Node(key(dim, pos), dim, pos, c.getUUID("owner"), c.getString("name"), c.getBoolean("mother"));
			n.open = c.getBoolean("open");
			net.nodes.put(n.key, n);
		}
		CompoundTag l = tag.getCompound("links");
		for (String k : l.getAllKeys()) {
			net.links.put(k, l.getLong(k));
		}
		CompoundTag food = tag.getCompound("nutrients");
		for (String k : food.getAllKeys()) {
			net.nutrients.put(UUID.fromString(k), food.getInt(k));
		}
		CompoundTag known = tag.getCompound("talents");
		for (String k : known.getAllKeys()) {
			Set<String> ids = new HashSet<>();
			for (Tag id : known.getList(k, Tag.TAG_STRING)) {
				ids.add(id.getAsString());
			}
			net.growerTalents.put(UUID.fromString(k), ids);
		}
		CompoundTag reserve = tag.getCompound("bodies");
		for (String k : reserve.getAllKeys()) {
			net.bodies.put(UUID.fromString(k), reserve.getCompound(k).getString("node"));
			net.bodyReady.put(UUID.fromString(k), reserve.getCompound(k).getLong("ready"));
		}
		return net;
	}
}
