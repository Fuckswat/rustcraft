package rustcraft.world.block;

import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.ActionResultType;
import net.minecraft.util.Hand;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.BlockRayTraceResult;
import net.minecraft.util.text.StringTextComponent;
import net.minecraft.world.IBlockReader;
import net.minecraft.world.World;
import rustcraft.world.tile.NpcSpawnerTile;

/**
 * Маркер-спавнер NPC (выглядит как решётка в полу, неразрушим в выживании).
 * Настройки в NBT тайла: Entity, Count, Radius, Delay, Range. В творческом ПКМ показывает настройки.
 */
public class NpcSpawnerBlock extends Block {
    public NpcSpawnerBlock(Properties props) {
        super(props);
    }

    @Override
    public boolean hasTileEntity(BlockState state) {
        return true;
    }

    @Override
    public TileEntity createTileEntity(BlockState state, IBlockReader world) {
        return new NpcSpawnerTile();
    }

    @Override
    public ActionResultType onBlockActivated(BlockState state, World world, BlockPos pos, PlayerEntity player, Hand hand, BlockRayTraceResult hit) {
        if (!player.isCreative()) return ActionResultType.PASS;
        if (!world.isRemote && hand == Hand.MAIN_HAND) {
            TileEntity te = world.getTileEntity(pos);
            if (te instanceof NpcSpawnerTile) {
                player.sendStatusMessage(new StringTextComponent(((NpcSpawnerTile) te).describe()), false);
            }
        }
        return ActionResultType.func_233537_a_(world.isRemote);
    }
}
