package rustcraft.survival;

import net.minecraft.block.AbstractBlock;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.SoundType;
import net.minecraft.block.material.Material;
import net.minecraft.state.IntegerProperty;
import net.minecraft.state.StateContainer;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.shapes.ISelectionContext;
import net.minecraft.util.math.shapes.VoxelShape;
import net.minecraft.util.math.shapes.VoxelShapes;
import net.minecraft.world.IBlockReader;
import net.minecraft.world.World;
import net.minecraft.entity.player.PlayerEntity;

/** Ствол и крона занимают реальные блоки: удары сверху попадают в корневой узел. */
public class TreeCanopyBlock extends Block {
    public static final IntegerProperty LEVEL = IntegerProperty.create("level", 1, 3);
    public TreeCanopyBlock() {
        super(AbstractBlock.Properties.create(Material.WOOD).hardnessAndResistance(-1, 3600000).notSolid().noDrops().sound(SoundType.WOOD));
        setDefaultState(stateContainer.getBaseState().with(LEVEL, 1));
    }
    @Override protected void fillStateContainer(StateContainer.Builder<Block, BlockState> b) { b.add(LEVEL); }
    @Override public VoxelShape getShape(BlockState s, IBlockReader w, BlockPos p, ISelectionContext c) {
        return s.get(LEVEL) == 1 ? Block.makeCuboidShape(4, 0, 4, 12, 16, 12) :
                VoxelShapes.or(Block.makeCuboidShape(5, 0, 5, 11, 16, 11), Block.makeCuboidShape(-8, 2, -8, 24, 14, 24));
    }
    @Override public VoxelShape getCollisionShape(BlockState s, IBlockReader w, BlockPos p, ISelectionContext c) {
        return Block.makeCuboidShape(5, 0, 5, 11, 16, 11);
    }
    @Override public void onBlockHarvested(World w, BlockPos p, BlockState s, PlayerEntity player) {
        if (!w.isRemote && player.isCreative()) {
            BlockPos root = ResourceNodes.root(w, p);
            if (root != null) w.removeBlock(root, false);
        }
        super.onBlockHarvested(w, p, s, player);
    }
}
