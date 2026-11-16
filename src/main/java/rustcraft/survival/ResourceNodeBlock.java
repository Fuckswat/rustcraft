package rustcraft.survival;

import javax.annotation.Nullable;
import net.minecraft.block.AbstractBlock;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.SoundType;
import net.minecraft.block.material.Material;
import net.minecraft.state.BooleanProperty;
import net.minecraft.state.StateContainer;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.shapes.ISelectionContext;
import net.minecraft.util.math.shapes.VoxelShape;
import net.minecraft.util.math.shapes.VoxelShapes;
import net.minecraft.world.IBlockReader;
import net.minecraft.world.World;

public class ResourceNodeBlock extends Block {
    public enum Kind { TREE, STONE, METAL, SULFUR, HEMP }
    public static final BooleanProperty SPENT = BooleanProperty.create("spent");
    public final Kind kind;
    private static final VoxelShape TRUNK = Block.makeCuboidShape(4, 0, 4, 12, 16, 12);
    private static final VoxelShape STUMP = Block.makeCuboidShape(4, 0, 4, 12, 3, 12);
    private static final VoxelShape ROCK = VoxelShapes.or(Block.makeCuboidShape(0, 0, 2, 13, 10, 15), Block.makeCuboidShape(4, 2, 0, 16, 14, 12));
    private static final VoxelShape PLANT = Block.makeCuboidShape(3, 0, 3, 13, 15, 13);
    public ResourceNodeBlock(Kind kind) {
        super(AbstractBlock.Properties.create(kind == Kind.TREE || kind == Kind.HEMP ? Material.WOOD : Material.ROCK)
                .hardnessAndResistance(-1.0F, 3600000.0F).notSolid().noDrops()
                .sound(kind == Kind.TREE ? SoundType.WOOD : kind == Kind.HEMP ? SoundType.PLANT : SoundType.STONE));
        this.kind = kind;
        setDefaultState(stateContainer.getBaseState().with(SPENT, false));
    }
    @Override protected void fillStateContainer(StateContainer.Builder<Block, BlockState> b) { b.add(SPENT); }
    @Override public boolean hasTileEntity(BlockState state) { return true; }
    @Nullable @Override public TileEntity createTileEntity(BlockState state, IBlockReader world) { return new ResourceNodeTile(); }
    @Override public VoxelShape getShape(BlockState s, IBlockReader w, BlockPos p, ISelectionContext c) {
        if (s.get(SPENT)) return kind == Kind.TREE ? STUMP : Block.makeCuboidShape(4, 0, 4, 12, 1, 12);
        return kind == Kind.TREE ? TRUNK : kind == Kind.HEMP ? PLANT : ROCK;
    }
    @Override public VoxelShape getCollisionShape(BlockState s, IBlockReader w, BlockPos p, ISelectionContext c) {
        return kind == Kind.HEMP || s.get(SPENT) ? VoxelShapes.empty() : getShape(s, w, p, c);
    }
    @Override public void onReplaced(BlockState s, World w, BlockPos p, BlockState n, boolean moving) {
        if (kind == Kind.TREE && s.getBlock() != n.getBlock()) ResourceNodes.removeCanopy(w, p);
        super.onReplaced(s, w, p, n, moving);
    }
}
