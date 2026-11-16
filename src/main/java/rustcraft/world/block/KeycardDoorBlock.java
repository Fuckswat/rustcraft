package rustcraft.world.block;

import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.DoorBlock;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.state.properties.DoubleBlockHalf;
import net.minecraft.util.ActionResultType;
import net.minecraft.util.Hand;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.BlockRayTraceResult;
import net.minecraft.util.text.TextFormatting;
import net.minecraft.util.text.TranslationTextComponent;
import net.minecraft.world.World;

/** Бронированная дверь, которую открывает только соседний считыватель ключ-карт (редстоун игнорируется). */
public class KeycardDoorBlock extends DoorBlock {
    public KeycardDoorBlock(Properties props) {
        super(props);
    }

    public void setOpenByCard(World world, BlockState state, BlockPos pos, boolean open) {
        if (state.get(HALF) != DoubleBlockHalf.LOWER) return;
        openDoor(world, state, pos, open);
    }

    @Override
    public ActionResultType onBlockActivated(BlockState state, World world, BlockPos pos, PlayerEntity player, Hand hand, BlockRayTraceResult hit) {
        if (!world.isRemote && hand == Hand.MAIN_HAND && !state.get(OPEN)) {
            player.sendStatusMessage(new TranslationTextComponent("rustcraft.world.door.locked").mergeStyle(TextFormatting.GRAY), true);
        }
        return ActionResultType.PASS;
    }

    @Override
    public void neighborChanged(BlockState state, World world, BlockPos pos, Block block, BlockPos fromPos, boolean isMoving) {
        // Редстоун не открывает ключ-дверь.
    }
}
