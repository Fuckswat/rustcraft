package rustcraft.survival;

import java.util.List;
import javax.annotation.Nullable;
import net.minecraft.block.BlockState;
import net.minecraft.client.util.ITooltipFlag;
import net.minecraft.entity.LivingEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.util.text.ITextComponent;
import net.minecraft.util.text.TranslationTextComponent;
import net.minecraft.world.World;
import rustcraft.RustCraft;

/** Инструменты Rust добывают узлы; не превращают грунт в Minecraft-шахту. */
public class HarvestToolItem extends Item {
    public final int tier, damage, interval;
    public final boolean axe;
    public HarvestToolItem(int tier, boolean axe, int damage, int interval, int durability) {
        super(new Item.Properties().group(RustCraft.TAB).maxDamage(durability));
        this.tier = tier; this.axe = axe; this.damage = damage; this.interval = interval;
    }
    @Override public float getDestroySpeed(ItemStack stack, BlockState state) { return 1.0F; }
    @Override public boolean hitEntity(ItemStack stack, LivingEntity victim, LivingEntity attacker) {
        stack.damageItem(1, attacker, e -> e.sendBreakAnimation(net.minecraft.util.Hand.MAIN_HAND));
        return true;
    }
    @Override public void addInformation(ItemStack stack, @Nullable World world, List<ITextComponent> text, ITooltipFlag flag) {
        text.add(new TranslationTextComponent("tooltip.rustcraft.harvest.hold"));
        text.add(new TranslationTextComponent("tooltip.rustcraft.harvest." + (tier == 0 ? "rock" : axe ? "axe" : "pick")));
    }
}
