package rustcraft.world.block;

import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.HorizontalBlock;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.BlockItemUseContext;
import net.minecraft.item.ItemStack;
import net.minecraft.state.BooleanProperty;
import net.minecraft.state.StateContainer;
import net.minecraft.state.properties.BlockStateProperties;
import net.minecraft.util.ActionResultType;
import net.minecraft.util.Direction;
import net.minecraft.util.Hand;
import net.minecraft.util.SoundCategory;
import net.minecraft.util.SoundEvents;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.BlockRayTraceResult;
import net.minecraft.util.text.TextFormatting;
import net.minecraft.util.text.TranslationTextComponent;
import net.minecraft.world.World;
import net.minecraft.world.server.ServerWorld;
import rustcraft.world.item.KeycardItem;

import java.util.Random;

/**
 * Считыватель ключ-карт. Карта нужного цвета -> открывает ключ-двери (keycard_door) в радиусе 3 блоков
 * на {@link #OPEN_TICKS} тиков, карта теряет 1 прочность.
 */
public class KeycardReaderBlock extends HorizontalBlock {
    public static final BooleanProperty POWERED = BlockStateProperties.POWERED;
    public static final int OPEN_TICKS = 200;
    private static final String[] COLORS = {"", "green", "blue", "red"};

    private final int level;

    public KeycardReaderBlock(int level, Properties props) {
        super(props);
        this.level = level;
        setDefaultState(getStateContainer().getBaseState().with(HORIZONTAL_FACING, Direction.NORTH).with(POWERED, false));
    }

    public int getLevel() {
        return level;
    }

    @Override
    protected void fillStateContainer(StateContainer.Builder<Block, BlockState> builder) {
        builder.add(HORIZONTAL_FACING, POWERED);
    }

    @Override
    public BlockState getStateForPlacement(BlockItemUseContext ctx) {
        return getDefaultState().with(HORIZONTAL_FACING, ctx.getPlacementHorizontalFacing().getOpposite());
    }

    @Override
    public ActionResultType onBlockActivated(BlockState state, World world, BlockPos pos, PlayerEntity player, Hand hand, BlockRayTraceResult hit) {
        ItemStack stack = player.getHeldItem(hand);
        if (!(stack.getItem() instanceof KeycardItem)) {
            if (hand == Hand.MAIN_HAND && !world.isRemote) {
                player.sendStatusMessage(new TranslationTextComponent("rustcraft.world.reader.need",
                        new TranslationTextComponent("item.rustcraft.keycard_" + COLORS[level])).mergeStyle(TextFormatting.YELLOW), true);
            }
            return hand == Hand.MAIN_HAND ? ActionResultType.func_233537_a_(world.isRemote) : ActionResultType.PASS;
        }
        if (world.isRemote) return ActionResultType.SUCCESS;
        KeycardItem card = (KeycardItem) stack.getItem();
        if (card.getLevel() != level) {
            world.playSound(null, pos, SoundEvents.BLOCK_NOTE_BLOCK_BASS, SoundCategory.BLOCKS, 1.0F, 0.5F);
            player.sendStatusMessage(new TranslationTextComponent("rustcraft.world.reader.wrong").mergeStyle(TextFormatting.RED), true);
            return ActionResultType.CONSUME;
        }
        if (state.get(POWERED)) {
            return ActionResultType.CONSUME;
        }
        if (!player.isCreative()) {
            stack.damageItem(1, player, p -> p.sendBreakAnimation(hand));
        }
        world.setBlockState(pos, state.with(POWERED, true), 3);
        setDoors(world, pos, true);
        world.playSound(null, pos, SoundEvents.BLOCK_NOTE_BLOCK_CHIME, SoundCategory.BLOCKS, 1.0F, 1.5F);
        world.getPendingBlockTicks().scheduleTick(pos, this, OPEN_TICKS);
        player.sendStatusMessage(new TranslationTextComponent("rustcraft.world.reader.ok").mergeStyle(TextFormatting.GREEN), true);
        return ActionResultType.CONSUME;
    }

    @Override
    public void tick(BlockState state, ServerWorld world, BlockPos pos, Random rand) {
        if (state.get(POWERED)) {
            world.setBlockState(pos, state.with(POWERED, false), 3);
            setDoors(world, pos, false);
            world.playSound(null, pos, SoundEvents.BLOCK_NOTE_BLOCK_BASS, SoundCategory.BLOCKS, 0.6F, 1.0F);
        }
    }

    private static void setDoors(World world, BlockPos center, boolean open) {
        for (BlockPos p : BlockPos.getAllInBoxMutable(center.add(-3, -2, -3), center.add(3, 2, 3))) {
            BlockState s = world.getBlockState(p);
            if (s.getBlock() instanceof KeycardDoorBlock) {
                ((KeycardDoorBlock) s.getBlock()).setOpenByCard(world, s, p.toImmutable(), open);
            }
        }
    }
}
