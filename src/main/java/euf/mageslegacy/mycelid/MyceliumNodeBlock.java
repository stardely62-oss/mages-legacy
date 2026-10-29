package euf.mageslegacy.mycelid;

import euf.mageslegacy.MagesLegacy;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.level.material.PushReaction;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.neoforge.registries.DeferredRegister;

/**
 * A node of the Mycelium Network: a glowing mushroom grown by a Mycelid, the Mother Mycelium for the first one. Anyone
 * may use it (a click shows where the network leads) and feed it (a click with something organic); only its grower
 * (or an operator) can tear it out. Pistons can't move it and explosions don't break it; it drops nothing.
 */
public class MyceliumNodeBlock extends Block {
	private static final DeferredRegister.Blocks BLOCKS = DeferredRegister.createBlocks(MagesLegacy.MOD_ID);
	public static final BooleanProperty MOTHER = BooleanProperty.create("mother");
	public static final DeferredBlock<MyceliumNodeBlock> NODE = BLOCKS.register("mycelium_node",
			() -> new MyceliumNodeBlock(BlockBehaviour.Properties.of().mapColor(MapColor.COLOR_LIGHT_GREEN).strength(1.5f, 3_600_000f)
					.sound(SoundType.FUNGUS).lightLevel(state -> state.getValue(MOTHER) ? 12 : 9).noOcclusion().pushReaction(PushReaction.BLOCK)));
	private static final VoxelShape SHAPE = Block.box(2, 0, 2, 14, 13, 14);
	private static final VoxelShape MOTHER_SHAPE = Block.box(0, 0, 0, 16, 16, 16);

	public MyceliumNodeBlock(BlockBehaviour.Properties properties) {
		super(properties);
		registerDefaultState(stateDefinition.any().setValue(MOTHER, false));
	}

	public static void register(IEventBus modBus) {
		BLOCKS.register(modBus);
	}

	@Override
	protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
		builder.add(MOTHER);
	}

	@Override
	protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
		return state.getValue(MOTHER) ? MOTHER_SHAPE : SHAPE;
	}

	@Override
	protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
		if (player instanceof ServerPlayer sp && level instanceof ServerLevel server) {
			MyceliumNetwork.openNode(sp, server, pos);
		}
		return InteractionResult.sidedSuccess(level.isClientSide);
	}

	@Override
	protected ItemInteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand,
			BlockHitResult hit) {
		if (MyceliumNetwork.nutritionOf(stack) <= 0) {
			return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
		}
		if (player instanceof ServerPlayer sp && level instanceof ServerLevel server) {
			MyceliumNetwork.feed(sp, server, pos, stack);
		}
		return ItemInteractionResult.sidedSuccess(level.isClientSide);
	}

	@Override
	protected void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean moved) {
		if (!newState.is(this) && level instanceof ServerLevel server) {
			MyceliumNetwork.removed(server, pos);
		}
		super.onRemove(state, level, pos, newState, moved);
	}
}
