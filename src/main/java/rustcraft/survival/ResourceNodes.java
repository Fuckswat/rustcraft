package rustcraft.survival;

import javax.annotation.Nullable;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import net.minecraftforge.registries.ForgeRegistries;

/** Точки интеграции генератора карты и взаимодействия с кронами. */
public final class ResourceNodes {
    private ResourceNodes() {}
    @Nullable public static BlockPos root(World world, BlockPos pos) {
        Block b = world.getBlockState(pos).getBlock();
        if (b instanceof ResourceNodeBlock) return pos;
        if (b instanceof TreeCanopyBlock) {
            BlockPos p = pos.down(world.getBlockState(pos).get(TreeCanopyBlock.LEVEL));
            if (world.getBlockState(p).getBlock() == SurvivalModule.TREE.get()) return p;
        }
        return null;
    }
    public static boolean place(World world, BlockPos pos, String id) {
        Block b = ForgeRegistries.BLOCKS.getValue(new ResourceLocation("rustcraft", id));
        return b instanceof ResourceNodeBlock && world.setBlockState(pos, b.getDefaultState(), 3);
    }
    static void createCanopy(World world, BlockPos root) {
        for (int i = 1; i <= 3; i++) {
            BlockPos p = root.up(i);
            if (world.isAirBlock(p) || world.getBlockState(p).getMaterial().isReplaceable())
                world.setBlockState(p, SurvivalModule.CANOPY.get().getDefaultState().with(TreeCanopyBlock.LEVEL, i), 3);
        }
    }
    static void removeCanopy(World world, BlockPos root) {
        for (int i = 1; i <= 3; i++) if (world.getBlockState(root.up(i)).getBlock() == SurvivalModule.CANOPY.get())
            world.removeBlock(root.up(i), false);
    }
}
