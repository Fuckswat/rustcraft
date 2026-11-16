package rustcraft.building;

import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.HorizontalBlock;
import net.minecraft.block.SoundType;
import net.minecraft.block.material.Material;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.BlockItemUseContext;
import net.minecraft.item.ItemStack;
import net.minecraft.state.StateContainer;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.ActionResultType;
import net.minecraft.util.Hand;
import net.minecraft.util.SoundCategory;
import net.minecraft.util.SoundEvents;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.BlockRayTraceResult;
import net.minecraft.util.text.StringTextComponent;
import net.minecraft.util.text.TextFormatting;
import net.minecraft.util.text.TranslationTextComponent;
import net.minecraft.world.IBlockReader;
import net.minecraft.world.World;

/**
 * Шкаф с инструментами. ПКМ — авторизоваться (повторно — список),
 * Shift+ПКМ авторизованным — очистить список. Даёт право строительства в радиусе 16 блоков.
 */
public class ToolCupboardBlock extends HorizontalBlock {

    public ToolCupboardBlock() {
        super(Properties.create(Material.WOOD).hardnessAndResistance(3.0F, 3600000.0F).sound(SoundType.WOOD));
        setDefaultState(getStateContainer().getBaseState().with(HORIZONTAL_FACING, net.minecraft.util.Direction.NORTH));
    }

    @Override
    protected void fillStateContainer(StateContainer.Builder<Block, BlockState> builder) {
        builder.add(HORIZONTAL_FACING);
    }

    @Override
    public BlockState getStateForPlacement(BlockItemUseContext context) {
        return getDefaultState().with(HORIZONTAL_FACING, context.getPlacementHorizontalFacing().getOpposite());
    }

    @Override
    public boolean hasTileEntity(BlockState state) {
        return true;
    }

    @Override
    public TileEntity createTileEntity(BlockState state, IBlockReader world) {
        return new ToolCupboardTileEntity();
    }

    @Override
    public void onBlockPlacedBy(World world, BlockPos pos, BlockState state, LivingEntity placer, ItemStack stack) {
        super.onBlockPlacedBy(world, pos, state, placer, stack);
        if (!world.isRemote && placer instanceof PlayerEntity) {
            TileEntity te = world.getTileEntity(pos);
            if (te instanceof ToolCupboardTileEntity) {
                ((ToolCupboardTileEntity) te).authorize((PlayerEntity) placer);
            }
        }
    }

    @Override
    @SuppressWarnings("deprecation")
    public ActionResultType onBlockActivated(BlockState state, World world, BlockPos pos, PlayerEntity player, Hand hand, BlockRayTraceResult hit) {
        if (hand != Hand.MAIN_HAND) return ActionResultType.PASS;
        if (world.isRemote) return ActionResultType.SUCCESS;
        TileEntity te = world.getTileEntity(pos);
        if (!(te instanceof ToolCupboardTileEntity)) return ActionResultType.CONSUME;
        ToolCupboardTileEntity tc = (ToolCupboardTileEntity) te;
        if (player.isSneaking()) {
            if (tc.isAuthorized(player) || player.isCreative()) {
                tc.clear();
                BuildingLogic.notify(player, new TranslationTextComponent("msg.rustcraft.building.tc_cleared").mergeStyle(TextFormatting.YELLOW));
                world.playSound(null, pos, SoundEvents.BLOCK_WOODEN_TRAPDOOR_CLOSE, SoundCategory.BLOCKS, 1.0F, 0.7F);
            } else {
                BuildingLogic.notify(player, new TranslationTextComponent("msg.rustcraft.building.tc_not_authorized").mergeStyle(TextFormatting.RED));
            }
        } else if (!tc.isAuthorized(player)) {
            tc.authorize(player);
            BuildingLogic.notify(player, new TranslationTextComponent("msg.rustcraft.building.tc_authorized").mergeStyle(TextFormatting.GREEN));
            world.playSound(null, pos, SoundEvents.BLOCK_WOODEN_TRAPDOOR_OPEN, SoundCategory.BLOCKS, 1.0F, 1.2F);
        } else {
            StringBuilder sb = new StringBuilder();
            for (String n : tc.names()) {
                if (sb.length() > 0) sb.append(", ");
                sb.append(n);
            }
            player.sendMessage(new TranslationTextComponent("msg.rustcraft.building.tc_list",
                    new StringTextComponent(sb.toString()).mergeStyle(TextFormatting.WHITE)).mergeStyle(TextFormatting.GRAY), player.getUniqueID());
            player.sendMessage(new TranslationTextComponent("msg.rustcraft.building.tc_hint").mergeStyle(TextFormatting.DARK_GRAY), player.getUniqueID());
        }
        return ActionResultType.CONSUME;
    }

    @Override
    public boolean canEntityDestroy(BlockState state, IBlockReader world, BlockPos pos, Entity entity) {
        return entity instanceof PlayerEntity;
    }
}
