package euf.mageslegacy.mixin;

import net.minecraft.entity.ai.goal.GoalSelector;
import net.minecraft.entity.mob.MobEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** Echoes and bonded beasts get new goals: follow and defend the Shepherd instead of hunting players. */
@Mixin(MobEntity.class)
public interface MobEntityAccessor {
	@Accessor("goalSelector")
	GoalSelector mages_legacy$goals();

	@Accessor("targetSelector")
	GoalSelector mages_legacy$targets();
}
