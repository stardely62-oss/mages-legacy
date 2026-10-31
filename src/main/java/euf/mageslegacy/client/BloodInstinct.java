package euf.mageslegacy.client;

import euf.mageslegacy.bloodkin.Bloodkin;
import euf.mageslegacy.client.screen.LegacyUi;
import euf.mageslegacy.race.Race;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.OwnableEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;

/**
 * The Bloodkin's innate Call of the Wounded, all on the client: the nearest living creature with blood under half its
 * health within 12 blocks is felt as a muffled heartbeat from its side, quicker and louder as it nears, and a faint red
 * pulse at that edge of the screen. No outline, no place, nothing through the walls to see: Blood Sense (the talent)
 * still is the one that shows hearts. Looked for twice a second among the creatures this client already has, never
 * every frame.
 */
public final class BloodInstinct {
	public static final double RANGE = 12;
	private static final int SCAN_EVERY = 10;
	/** The one felt now (null: none), its distance and the direction to it, as last scanned. */
	private static LivingEntity target;
	private static double distance;
	private static long nextBeat;
	/** When the last beat sounded (ms), for the pulse at the screen's edge. */
	private static long beatMs;

	private BloodInstinct() {
	}

	public static LivingEntity target() {
		return target;
	}

	/** Whether this one can be felt: alive, with blood, wounded below half, not the player, not one's own pet. */
	public static boolean feels(LivingEntity e, Player self) {
		if (e == self || !e.isAlive() || e.isSpectator() || !Bloodkin.bloodful(e) || e.getHealth() >= e.getMaxHealth() * 0.5f) {
			return false;
		}
		if (e instanceof OwnableEntity owned && self.getUUID().equals(owned.getOwnerUUID())) {
			return false;
		}
		return !(e instanceof Player p && p.isCreative());
	}

	/** The nearest one felt around a player, or null. */
	public static LivingEntity scan(LocalPlayer player) {
		LivingEntity best = null;
		double bestSq = RANGE * RANGE;
		for (LivingEntity e : player.level().getEntitiesOfClass(LivingEntity.class, player.getBoundingBox().inflate(RANGE), e -> feels(e, player))) {
			double d = e.distanceToSqr(player);
			if (d <= bestSq) {
				bestSq = d;
				best = e;
			}
		}
		return best;
	}

	static void tick() {
		Minecraft mc = Minecraft.getInstance();
		LocalPlayer player = mc.player;
		if (player == null || ClientRace.race() != Race.BLOODKIN || !player.isAlive()) {
			target = null;
			return;
		}
		if (player.tickCount % SCAN_EVERY == 0 || target != null && (!target.isAlive() || target.isRemoved())) {
			target = scan(player);
		}
		if (target == null) {
			return;
		}
		distance = target.distanceTo(player);
		long now = player.level().getGameTime();
		if (now < nextBeat) {
			return;
		}
		// A beat every 1.8 s at the edge of the sense, every 0.6 s up close; heard from the creature's side, never from it
		float near = (float) Mth.clamp(1 - distance / RANGE, 0, 1);
		nextBeat = now + Math.round(Mth.lerp(near, 36, 12));
		Vec3 eye = player.getEyePosition();
		Vec3 dir = target.position().add(0, target.getBbHeight() * 0.5, 0).subtract(eye);
		Vec3 at = dir.lengthSqr() < 1.0E-4 ? eye : eye.add(dir.normalize().scale(2));
		player.level().playLocalSound(at.x, at.y, at.z, SoundEvents.WARDEN_HEARTBEAT, SoundSource.PLAYERS, 0.15f + 0.45f * near, 1.15f, false);
		beatMs = System.currentTimeMillis();
	}

	/** The pulse at the screen's edge, on the creature's side (the top when ahead, the bottom when behind). */
	public static void render(GuiGraphics graphics, DeltaTracker delta) {
		Minecraft mc = Minecraft.getInstance();
		if (target == null || mc.player == null || mc.options.hideGui) {
			return;
		}
		float age = (System.currentTimeMillis() - beatMs) / 600f;
		if (age > 1) {
			return;
		}
		float near = (float) Mth.clamp(1 - distance / RANGE, 0, 1);
		float strength = (1 - age) * (0.35f + 0.45f * near);
		Vec3 to = target.position().subtract(mc.player.position());
		double yawTo = Math.toDegrees(Math.atan2(-to.x, to.z));
		double rel = Math.toRadians(Mth.wrapDegrees(yawTo - mc.player.getYRot()));
		int w = graphics.guiWidth();
		int h = graphics.guiHeight();
		// On an ellipse just inside the screen's edge: 0 is straight up (ahead), a quarter turn the right side
		int x = w / 2 + (int) Math.round(Math.sin(rel) * (w / 2.0 - 6));
		int y = h / 2 - (int) Math.round(Math.cos(rel) * (h / 2.0 - 6));
		LegacyUi.glow(graphics, x, y, Math.max(24, h / 5), 0xFF8E0F1C, strength);
	}
}
