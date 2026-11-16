package rustcraft.building;

import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.item.BlockItemUseContext;
import net.minecraft.state.DirectionProperty;
import net.minecraft.state.StateContainer;
import net.minecraft.util.Direction;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.shapes.ISelectionContext;
import net.minecraft.util.math.shapes.VoxelShape;
import net.minecraft.util.math.shapes.VoxelShapes;
import net.minecraft.world.IBlockReader;
import net.minecraftforge.registries.ForgeRegistries;

/** Raid-compatible home pieces, preserving their shape and facing when upgraded. */
public final class BuildingPieceBlock extends RustWallBlock {
    public static final DirectionProperty FACING=DirectionProperty.create("facing",Direction.Plane.HORIZONTAL);
    private final String piece;

    public BuildingPieceBlock(StructureKind kind,String piece) {
        super(kind);this.piece=piece;
        setDefaultState(getDefaultState().with(FACING,Direction.NORTH));
    }

    @Override protected void fillStateContainer(StateContainer.Builder<Block,BlockState> builder) {
        super.fillStateContainer(builder);builder.add(FACING);
    }

    @Override public BlockState getStateForPlacement(BlockItemUseContext context) {
        return getDefaultState().with(FACING,context.getPlacementHorizontalFacing());
    }

    public BlockState upgraded(StructureKind next,BlockState old) {
        Block b=ForgeRegistries.BLOCKS.getValue(new ResourceLocation("rustcraft",next.mat.name().toLowerCase(java.util.Locale.ROOT)+"_"+piece));
        if(!(b instanceof BuildingPieceBlock))return old;
        return b.getDefaultState().with(FACING,old.get(FACING));
    }

    @Override public VoxelShape getShape(BlockState s,IBlockReader world,BlockPos p,ISelectionContext c) {
        Direction d=s.get(FACING);
        if(piece.equals("floor"))return Block.makeCuboidShape(0,0,0,16,3,16);
        if(piece.equals("roof"))return Block.makeCuboidShape(0,13,0,16,16,16);
        if(piece.equals("window")) {
            if(d.getAxis()==Direction.Axis.Z)return VoxelShapes.or(Block.makeCuboidShape(0,0,6,16,2,10),Block.makeCuboidShape(0,14,6,16,16,10),Block.makeCuboidShape(0,2,6,2,14,10),Block.makeCuboidShape(14,2,6,16,14,10));
            return VoxelShapes.or(Block.makeCuboidShape(6,0,0,10,2,16),Block.makeCuboidShape(6,14,0,10,16,16),Block.makeCuboidShape(6,2,0,10,14,2),Block.makeCuboidShape(6,2,14,10,14,16));
        }
        if(piece.equals("stairs")) {
            VoxelShape upper=d==Direction.NORTH?Block.makeCuboidShape(0,8,0,16,16,8):d==Direction.SOUTH?Block.makeCuboidShape(0,8,8,16,16,16):d==Direction.EAST?Block.makeCuboidShape(8,8,0,16,16,16):Block.makeCuboidShape(0,8,0,8,16,16);
            return VoxelShapes.or(Block.makeCuboidShape(0,0,0,16,8,16),upper);
        }
        return VoxelShapes.fullCube();
    }

    @Override public VoxelShape getCollisionShape(BlockState s,IBlockReader world,BlockPos p,ISelectionContext c) {
        return getShape(s,world,p,c);
    }
}
