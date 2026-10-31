package euf.mageslegacy.race;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.Optional;
import net.minecraft.advancements.critereon.ContextAwarePredicate;
import net.minecraft.advancements.critereon.EntityPredicate;
import net.minecraft.advancements.critereon.SimpleCriterionTrigger;
import net.minecraft.server.level.ServerPlayer;

/**
 * The criterion {@code mages_legacy:deed}: met when the server confirms a deed of Mage's Legacy by its name (see
 * {@link Deeds}). A datapack can hang its own advancements on the same deeds: {@code {"trigger": "mages_legacy:deed",
 * "conditions": {"deed": "void_walls"}}}.
 */
public final class DeedTrigger extends SimpleCriterionTrigger<DeedTrigger.Instance> {
	@Override
	public Codec<Instance> codec() {
		return Instance.CODEC;
	}

	public void trigger(ServerPlayer player, String deed) {
		trigger(player, instance -> instance.deed().equals(deed));
	}

	public record Instance(Optional<ContextAwarePredicate> player, String deed) implements SimpleCriterionTrigger.SimpleInstance {
		public static final Codec<Instance> CODEC = RecordCodecBuilder.create(i -> i.group(
				EntityPredicate.ADVANCEMENT_CODEC.optionalFieldOf("player").forGetter(Instance::player),
				Codec.STRING.fieldOf("deed").forGetter(Instance::deed)).apply(i, Instance::new));
	}
}
