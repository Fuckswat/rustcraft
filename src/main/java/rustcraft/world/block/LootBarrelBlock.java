package rustcraft.world.block;

import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.shapes.ISelectionContext;
import net.minecraft.util.math.shapes.VoxelShape;
import net.minecraft.world.IBlockReader;
import net.minecraft.world.World;
import net.minecraft.world.server.ServerWorld;
import rustcraft.world.map.MapData;

/**
 * Бочка: ломается и выбрасывает лут из таблицы блока (loot_tables/blocks/&lt;id&gt;.json).
 * Внутри монументов восстанавливается через N минут.
 */
public class LootBarrelBlock extends Block {
    private static final VoxelShape SHAPE = Block.makeCuboidShape(2.0D, 0.0D, 2.0D, 14.0D, 15.0D, 14.0D);

    public LootBarrelBlock(Properties props) {
        super(props);
    }

    @Override
    public VoxelShape getShape(BlockState state, IBlockReader world, BlockPos pos, ISelectionContext ctx) {
        return SHAPE;
    }

    @Override
    public void onReplaced(BlockState state, World world, BlockPos pos, BlockState newState, boolean isMoving) {
        if (!state.isIn(newState.getBlock()) && world instanceof ServerWorld && newState.isAir()) {
            MapData.get((ServerWorld) world).scheduleBarrelRespawn((ServerWorld) world, pos, this);
        }
        super.onReplaced(state, world, pos, newState, isMoving);
    }
}
