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

/** Ключевой или кодовый замок: ПКМ по своей двери — установить. */
public class LockItem extends Item {
    private final boolean code;

    public LockItem(boolean code, Properties props) {
        super(props);
        this.code = code;
    }

    @Override
    public ActionResultType onItemUse(ItemUseContext ctx) {
        World world = ctx.getWorld();
        BlockState state = world.getBlockState(ctx.getPos());
        if (!(state.getBlock() instanceof RustDoorBlock)) return ActionResultType.PASS;
        PlayerEntity player = ctx.getPlayer();
        if (!world.isRemote && player != null) {
            DoorLogic.installLock(world, ctx.getPos(), player, ctx.getItem(), code);
        }
        return ActionResultType.func_233537_a_(world.isRemote);
    }

    @Override
    public void addInformation(ItemStack stack, @Nullable World world, List<ITextComponent> tooltip, ITooltipFlag flag) {
        tooltip.add(new TranslationTextComponent(code ? "tooltip.rustcraft.code_lock" : "tooltip.rustcraft.key_lock").mergeStyle(TextFormatting.GRAY));
    }
}
