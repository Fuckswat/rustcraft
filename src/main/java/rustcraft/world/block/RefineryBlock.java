package rustcraft.world.block;

import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.HorizontalBlock;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.player.ServerPlayerEntity;
import net.minecraft.inventory.InventoryHelper;
import net.minecraft.item.BlockItemUseContext;
import net.minecraft.particles.ParticleTypes;
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
import net.minecraftforge.fml.network.NetworkHooks;
import rustcraft.world.tile.RefineryTile;

import java.util.Random;

/** Мини-нефтеперегонка: сырая нефть + топливо (уголь/дерево) -> низкосортное топливо. */
public class RefineryBlock extends HorizontalBlock {
    public static final BooleanProperty LIT = BlockStateProperties.LIT;

    public RefineryBlock(Properties props) {
        super(props);
        setDefaultState(getStateContainer().getBaseState().with(HORIZONTAL_FACING, Direction.NORTH).with(LIT, false));
    }

    @Override
    protected void fillStateContainer(StateContainer.Builder<Block, BlockState> builder) {
        builder.add(HORIZONTAL_FACING, LIT);
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
        return new RefineryTile();
    }

    @Override
    public ActionResultType onBlockActivated(BlockState state, World world, BlockPos pos, PlayerEntity player, Hand hand, BlockRayTraceResult hit) {
        if (!world.isRemote) {
            TileEntity te = world.getTileEntity(pos);
            if (te instanceof RefineryTile && player instanceof ServerPlayerEntity) {
                NetworkHooks.openGui((ServerPlayerEntity) player, (RefineryTile) te, pos);
            }
        }
        return ActionResultType.func_233537_a_(world.isRemote);
    }

    @Override
    public void onReplaced(BlockState state, World world, BlockPos pos, BlockState newState, boolean isMoving) {
        if (!state.isIn(newState.getBlock())) {
            TileEntity te = world.getTileEntity(pos);
            if (te instanceof RefineryTile) {
                InventoryHelper.dropInventoryItems(world, pos, (RefineryTile) te);
            }
        }
        super.onReplaced(state, world, pos, newState, isMoving);
    }

    @Override
    public void animateTick(BlockState state, World world, BlockPos pos, Random rand) {
        if (state.get(LIT)) {
            double x = pos.getX() + 0.5D;
            double y = pos.getY() + 1.05D;
            double z = pos.getZ() + 0.5D;
            world.addParticle(ParticleTypes.SMOKE, x + (rand.nextDouble() - 0.5D) * 0.4D, y, z + (rand.nextDouble() - 0.5D) * 0.4D, 0.0D, 0.03D, 0.0D);
            if (rand.nextInt(6) == 0) {
                world.addParticle(ParticleTypes.LARGE_SMOKE, x, y + 0.1D, z, 0.0D, 0.05D, 0.0D);
            }
        }
    }
}
