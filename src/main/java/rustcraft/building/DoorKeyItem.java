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

/** Ключ от двери. NBT LockId — номер замка. Пустой ключ привязывается ПКМ по двери владельцем замка. */
public class DoorKeyItem extends Item {
    public DoorKeyItem(Properties props) {
        super(props);
    }

    public static int getLockId(ItemStack stack) {
        return stack.hasTag() ? stack.getTag().getInt("LockId") : 0;
    }

    public static boolean isBound(ItemStack stack) {
        return getLockId(stack) != 0;
    }

    public static void setLockId(ItemStack stack, int id) {
        stack.getOrCreateTag().putInt("LockId", id);
    }

    public static ItemStack bound(int id) {
        ItemStack s = new ItemStack(BRegistry.DOOR_KEY.get());
        setLockId(s, id);
        return s;
    }

    @Override
    public ActionResultType onItemUse(ItemUseContext ctx) {
        World world = ctx.getWorld();
        BlockState state = world.getBlockState(ctx.getPos());
        if (!(state.getBlock() instanceof RustDoorBlock)) return ActionResultType.PASS;
        PlayerEntity player = ctx.getPlayer();
        if (!world.isRemote && player != null) {
            if (isBound(ctx.getItem())) {
                // Shift+ПКМ привязанным ключом — тоже открыть дверь.
                DoorLogic.interact(world, ((RustDoorBlock) state.getBlock()).getMainPos(state, ctx.getPos()), player);
            } else {
                DoorLogic.bindKey(world, ctx.getPos(), player, ctx.getItem());
            }
        }
        return ActionResultType.func_233537_a_(world.isRemote);
    }

    @Override
    public void addInformation(ItemStack stack, @Nullable World world, List<ITextComponent> tooltip, ITooltipFlag flag) {
        int id = getLockId(stack);
        if (id != 0) {
            tooltip.add(new TranslationTextComponent("tooltip.rustcraft.door_key.bound", id).mergeStyle(TextFormatting.GOLD));
        } else {
            tooltip.add(new TranslationTextComponent("tooltip.rustcraft.door_key.blank").mergeStyle(TextFormatting.GRAY));
        }
    }
}
