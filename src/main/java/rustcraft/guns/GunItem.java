package rustcraft.guns;

import java.util.List;

import net.minecraft.block.BlockState;
import net.minecraft.client.util.ITooltipFlag;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Rarity;
import net.minecraft.item.UseAction;
import net.minecraft.nbt.CompoundNBT;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.text.ITextComponent;
import net.minecraft.util.text.TextFormatting;
import net.minecraft.util.text.TranslationTextComponent;
import net.minecraft.world.World;
import rustcraft.RustCraft;

/**
 * Огнестрельное оружие. Магазин и состояние перезарядки хранятся в NBT стака:
 * Ammo (int), ReloadStart/ReloadEnd (long, игровое время мира).
 * Стрельба/перезарядка инициируются клиентом пакетами и полностью проверяются на сервере (GunServer).
 */
public class GunItem extends Item {
    public static final String TAG_AMMO = "Ammo";
    public static final String TAG_RELOAD_START = "ReloadStart";
    public static final String TAG_RELOAD_END = "ReloadEnd";

    private final GunStats stats;

    public GunItem(GunStats stats) {
        super(new Item.Properties().group(RustCraft.TAB).maxStackSize(1)
                .rarity(stats.isRocket() || stats == GunStats.AK47 || stats == GunStats.BOLT_RIFLE ? Rarity.RARE : Rarity.UNCOMMON));
        this.stats = stats;
    }

    public GunStats getStats() {
        return stats;
    }

    public static int getAmmo(ItemStack stack) {
        CompoundNBT tag = stack.getTag();
        return tag == null ? 0 : tag.getInt(TAG_AMMO);
    }

    public static void setAmmo(ItemStack stack, int ammo) {
        stack.getOrCreateTag().putInt(TAG_AMMO, Math.max(0, ammo));
    }

    public static long getReloadEnd(ItemStack stack) {
        CompoundNBT tag = stack.getTag();
        return tag == null ? 0L : tag.getLong(TAG_RELOAD_END);
    }

    public static long getReloadStart(ItemStack stack) {
        CompoundNBT tag = stack.getTag();
        return tag == null ? 0L : tag.getLong(TAG_RELOAD_START);
    }

    public static void setReload(ItemStack stack, long start, long end) {
        CompoundNBT tag = stack.getOrCreateTag();
        tag.putLong(TAG_RELOAD_START, start);
        tag.putLong(TAG_RELOAD_END, end);
    }

    public static void clearReload(ItemStack stack) {
        CompoundNBT tag = stack.getTag();
        if (tag != null) {
            tag.remove(TAG_RELOAD_START);
            tag.remove(TAG_RELOAD_END);
        }
    }

    public static boolean isReloading(ItemStack stack) {
        return getReloadEnd(stack) > 0L;
    }

    // ---- ванильное поведение предмета: оружие не ломает блоки, не бьёт и не «используется» ----

    @Override
    public ActionResult<ItemStack> onItemRightClick(World world, PlayerEntity player, Hand hand) {
        return ActionResult.resultPass(player.getHeldItem(hand));
    }

    @Override
    public UseAction getUseAction(ItemStack stack) {
        return UseAction.NONE;
    }

    @Override
    public boolean canPlayerBreakBlockWhileHolding(BlockState state, World world, BlockPos pos, PlayerEntity player) {
        return false;
    }

    @Override
    public boolean onLeftClickEntity(ItemStack stack, PlayerEntity player, Entity entity) {
        return true; // никакого удара прикладом: ЛКМ — это выстрел
    }

    @Override
    public boolean onEntitySwing(ItemStack stack, LivingEntity entity) {
        return true;
    }

    @Override
    public boolean shouldCauseReequipAnimation(ItemStack oldStack, ItemStack newStack, boolean slotChanged) {
        // NBT меняется при каждом выстреле — не дёргаем модель в руке
        return slotChanged || oldStack.getItem() != newStack.getItem();
    }

    @Override
    public boolean shouldCauseBlockBreakReset(ItemStack oldStack, ItemStack newStack) {
        return oldStack.getItem() != newStack.getItem();
    }

    @Override
    public void addInformation(ItemStack stack, World world, List<ITextComponent> tooltip, ITooltipFlag flag) {
        tooltip.add(new TranslationTextComponent("tooltip.rustcraft.guns.ammo", getAmmo(stack), stats.magSize,
                new TranslationTextComponent("item.rustcraft." + GunsItems.ammoIdFor(stats))).mergeStyle(TextFormatting.GRAY));
        if (!stats.isRocket()) {
            float dmg = stats.damage * stats.pellets * 5.0F;
            tooltip.add(new TranslationTextComponent("tooltip.rustcraft.guns.damage", Math.round(dmg)).mergeStyle(TextFormatting.DARK_GRAY));
            tooltip.add(new TranslationTextComponent("tooltip.rustcraft.guns.rpm", Math.round(1200.0F / stats.fireInterval)).mergeStyle(TextFormatting.DARK_GRAY));
        }
        tooltip.add(new TranslationTextComponent(stats.auto ? "tooltip.rustcraft.guns.auto" : "tooltip.rustcraft.guns.semi").mergeStyle(TextFormatting.DARK_GRAY));
        tooltip.add(new TranslationTextComponent("tooltip.rustcraft.guns.controls").mergeStyle(TextFormatting.DARK_AQUA));
    }
}
