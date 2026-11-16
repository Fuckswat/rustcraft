package rustcraft.building;

import net.minecraft.block.BlockState;
import net.minecraft.client.util.ITooltipFlag;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.ItemUseContext;
import net.minecraft.util.ActionResultType;
import net.minecraft.util.text.ITextComponent;
import net.minecraft.util.text.TextFormatting;
import net.minecraft.util.text.TranslationTextComponent;
import net.minecraft.world.World;

import javax.annotation.Nullable;
import java.util.List;

/**
 * Молоток: ПКМ — улучшить стену, Shift+ПКМ — починить, Shift+ЛКМ — разобрать.
 * При взгляде на постройку показывает её HP в action-bar (сервер, BuildingEvents).
 */
public class HammerItem extends Item {
    public HammerItem(Properties props) {
        super(props);
    }

    @Override
    public ActionResultType onItemUse(ItemUseContext ctx) {
        World world = ctx.getWorld();
        BlockState state = world.getBlockState(ctx.getPos());
        if (!(state.getBlock() instanceof IRustStructure)) return ActionResultType.PASS;
        PlayerEntity player = ctx.getPlayer();
        if (!world.isRemote && player != null) {
            if (player.isSneaking()) {
                BuildingLogic.tryRepair(world, ctx.getPos(), player);
            } else {
                BuildingLogic.tryUpgrade(world, ctx.getPos(), player);
            }
            player.getCooldownTracker().setCooldown(this, 8);
        }
        return ActionResultType.func_233537_a_(world.isRemote);
    }

    @Override
    public void addInformation(ItemStack stack, @Nullable World world, List<ITextComponent> tooltip, ITooltipFlag flag) {
        tooltip.add(new TranslationTextComponent("tooltip.rustcraft.hammer.1").mergeStyle(TextFormatting.GRAY));
        tooltip.add(new TranslationTextComponent("tooltip.rustcraft.hammer.2").mergeStyle(TextFormatting.GRAY));
        tooltip.add(new TranslationTextComponent("tooltip.rustcraft.hammer.3").mergeStyle(TextFormatting.GRAY));
    }
}
