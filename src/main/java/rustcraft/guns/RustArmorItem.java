package rustcraft.guns;

import java.util.List;
import net.minecraft.entity.LivingEntity;
import net.minecraft.inventory.EquipmentSlotType;
import net.minecraft.item.ArmorItem;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.client.util.ITooltipFlag;
import net.minecraft.client.renderer.entity.model.BipedModel;
import net.minecraft.util.text.ITextComponent;
import net.minecraft.util.text.TextFormatting;
import net.minecraft.util.text.TranslationTextComponent;
import net.minecraft.world.World;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import rustcraft.RustCraft;

/** Four readable armor tiers, with localized protection and fitted plate models. */
public class RustArmorItem extends ArmorItem {
    public final RustArmorMaterial rustMaterial;

    public RustArmorItem(RustArmorMaterial material, EquipmentSlotType slot) {
        super(material, slot, new Item.Properties().group(RustCraft.TAB));
        rustMaterial = material;
    }

    public float bulletProtection() {
        switch (rustMaterial) {
            case RUST_CLOTH: return 0.10F;
            case RUST_WOOD: return 0.20F;
            case RUST_ROADSIGN: return 0.35F;
            default: return 0.50F;
        }
    }

    @Override
    @OnlyIn(Dist.CLIENT)
    public void addInformation(ItemStack stack, World world, List<ITextComponent> lines, ITooltipFlag flag) {
        lines.add(new TranslationTextComponent("tooltip.rustcraft.armor.bullets", Math.round(bulletProtection() * 100))
                .mergeStyle(TextFormatting.AQUA));
        lines.add(new TranslationTextComponent("tooltip.rustcraft.armor.location")
                .mergeStyle(TextFormatting.GRAY));
        lines.add(new TranslationTextComponent("tooltip.rustcraft.armor.durability", stack.getMaxDamage()-stack.getDamage(),stack.getMaxDamage())
                .mergeStyle(TextFormatting.DARK_GRAY));
    }

    @Override
    @OnlyIn(Dist.CLIENT)
    @SuppressWarnings("unchecked")
    public <A extends BipedModel<?>> A getArmorModel(LivingEntity entity, ItemStack stack,
                                                     EquipmentSlotType slot, A original) {
        return (A) rustcraft.guns.client.RustArmorModels.get(this, slot, original);
    }
}
