package euf.mageslegacy.client.fx;

import com.lowdragmc.photon.client.gameobject.emitter.IParticleEmitter;
import com.lowdragmc.photon.client.gameobject.emitter.data.shape.IShape;
import com.lowdragmc.photon.client.gameobject.particle.TileParticle;
import java.util.List;
import org.joml.Vector3f;

/**
 * A Photon emitter shape that lays its particles on given points, one after another, so a drawing (a rift's lens, a
 * crack in the ground) can be built out of particles. Each point may carry its own starting velocity.
 */
final class PointsShape implements IShape {
	private final List<Vector3f> points;
	private final List<Vector3f> velocities;
	private int next;

	PointsShape(List<Vector3f> points, List<Vector3f> velocities) {
		this.points = points;
		this.velocities = velocities;
	}

	@Override
	public void nextPosVel(TileParticle particle, IParticleEmitter emitter, Vector3f position, Vector3f rotation, Vector3f scale) {
		if (points.isEmpty()) {
			return;
		}
		int i = next++ % points.size();
		particle.setSimPos(new Vector3f(position).add(particle.getSimPosWithoutNoise()).add(points.get(i)), true);
		particle.setInternalVelocity(velocities == null ? new Vector3f() : new Vector3f(velocities.get(i)));
	}
}
