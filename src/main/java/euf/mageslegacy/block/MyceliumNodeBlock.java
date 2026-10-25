package euf.mageslegacy.block;

import euf.mageslegacy.mycelid.Mycelid;
import euf.mageslegacy.race.RaceState;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.ShapeContext;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.random.Random;
import net.minecraft.util.shape.VoxelShape;
import net.minecraft.util.shape.VoxelShapes;
import net.minecraft.world.BlockView;
import net.minecraft.world.World;

/** A glowing mushroom a Mycelid grows; right click carries anyone to the next node of the same network. */
public class MyceliumNodeBlock extends Block {
	private static final VoxelShape SHAPE = VoxelShapes.union(
			Block.createCuboidShape(6, 0, 6, 10, 7, 10),
			Block.createCuboidShape(2, 7, 2, 14, 13, 14));

	public MyceliumNodeBlock(Settings settings) {
		super(settings);
	}

	@Override
	public VoxelShape getOutlineShape(BlockState state, BlockView world, BlockPos pos, ShapeContext context) {
		return SHAPE;
	}

	@Override
	public ActionResult onUse(BlockState state, World world, BlockPos pos, PlayerEntity player, Hand hand, BlockHitResult hit) {
		if (world.isClient) {
			return ActionResult.SUCCESS;
		}
		return Mycelid.travel((ServerPlayerEntity) player, pos) ? ActionResult.CONSUME : ActionResult.PASS;
	}

	@Override
	public void onStateReplaced(BlockState state, World world, BlockPos pos, BlockState newState, boolean moved) {
		if (!state.isOf(newState.getBlock()) && world instanceof ServerWorld serverWorld) {
			RaceState.get(serverWorld.getServer()).removeNode(world.getRegistryKey(), pos);
		}
		super.onStateReplaced(state, world, pos, newState, moved);
	}

	@Override
	public void randomDisplayTick(BlockState state, World world, BlockPos pos, Random random) {
		if (random.nextInt(3) == 0) {
			world.addParticle(ParticleTypes.SPORE_BLOSSOM_AIR, pos.getX() + random.nextDouble(), pos.getY() + 0.8,
					pos.getZ() + random.nextDouble(), 0, 0.01, 0);
		}
	}
}
