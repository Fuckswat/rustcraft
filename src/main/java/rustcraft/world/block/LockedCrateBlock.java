package rustcraft.world.block;

import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.HorizontalBlock;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.player.ServerPlayerEntity;
import net.minecraft.item.BlockItemUseContext;
import net.minecraft.state.BooleanProperty;
import net.minecraft.state.StateContainer;
import net.minecraft.state.properties.BlockStateProperties;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.ActionResultType;
import net.minecraft.util.Direction;
import net.minecraft.util.Hand;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.BlockRayTraceResult;
import net.minecraft.world.IBlockReader;
import net.minecraft.world.World;
import rustcraft.world.tile.LockedCrateTile;

/** Запертый ящик: ПКМ запускает взлом (по умолчанию 120 с), во время взлома приходят волны учёных. */
public class LockedCrateBlock extends HorizontalBlock {
    /** true — взломан и открыт. */
    public static final BooleanProperty OPEN = BlockStateProperties.OPEN;

    public LockedCrateBlock(Properties props) {
        super(props);
        setDefaultState(getStateContainer().getBaseState().with(HORIZONTAL_FACING, Direction.NORTH).with(OPEN, false));
    }

    @Override
    protected void fillStateContainer(StateContainer.Builder<Block, BlockState> builder) {
        builder.add(HORIZONTAL_FACING, OPEN);
    }

    @Override
    public BlockState getStateForPlacement(BlockItemUseContext ctx) {
        return getDefaultState().with(HORIZONTAL_FACING, ctx.getPlacementHorizontalFacing().getOpposite());
    }

    @Override
    public boolean hasTileEntity(BlockState state) {
        return true;
    }

    @Override
    public TileEntity createTileEntity(BlockState state, IBlockReader world) {
        return new LockedCrateTile();
    }

    @Override
    public ActionResultType onBlockActivated(BlockState state, World world, BlockPos pos, PlayerEntity player, Hand hand, BlockRayTraceResult hit) {
        if (!world.isRemote && hand == Hand.MAIN_HAND) {
            TileEntity te = world.getTileEntity(pos);
            if (te instanceof LockedCrateTile && player instanceof ServerPlayerEntity) {
                ((LockedCrateTile) te).interact((ServerPlayerEntity) player);
            }
        }
        return ActionResultType.func_233537_a_(world.isRemote);
    }

    @Override
    public void onReplaced(BlockState state, World world, BlockPos pos, BlockState newState, boolean isMoving) {
        if (!state.isIn(newState.getBlock())) {
            TileEntity te = world.getTileEntity(pos);
            if (te instanceof LockedCrateTile) {
                ((LockedCrateTile) te).clearBossBar();
            }
        }
        super.onReplaced(state, world, pos, newState, isMoving);
    }
}
