package rustcraft.world.item;

import net.minecraft.client.util.ITooltipFlag;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.util.text.ITextComponent;
import net.minecraft.util.text.TextFormatting;
import net.minecraft.util.text.TranslationTextComponent;
import net.minecraft.world.World;

import javax.annotation.Nullable;
import java.util.List;

/** Ключ-карта: уровень 1 (зелёная), 2 (синяя), 3 (красная). Тратит прочность при каждом использовании. */
public class KeycardItem extends Item {
    private final int level;

    public KeycardItem(int level, Properties props) {
        super(props);
        this.level = level;
    }

    public int getLevel() {
        return level;
    }

    @Override
    public void addInformation(ItemStack stack, @Nullable World world, List<ITextComponent> tooltip, ITooltipFlag flag) {
        tooltip.add(new TranslationTextComponent("item.rustcraft.keycard.tooltip").mergeStyle(TextFormatting.GRAY));
        int left = stack.getMaxDamage() - stack.getDamage();
        tooltip.add(new TranslationTextComponent("item.rustcraft.keycard.uses", left).mergeStyle(TextFormatting.DARK_GRAY));
    }
}
