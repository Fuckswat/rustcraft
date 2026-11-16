package rustcraft.guns;

import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.inventory.EquipmentSlotType;
import net.minecraft.item.ArmorItem;
import net.minecraft.item.ItemStack;
import net.minecraft.util.math.vector.Vector3d;

/** Location-based protection for RustCraft bullets. Armor is charged once per hit. */
public final class ArmorProtection {
    private ArmorProtection() {}

    public static EquipmentSlotType slot(LivingEntity target, Vector3d hit) {
        double h = (hit.y-target.getPosY())/Math.max(0.1F,target.getHeight());
        return h >= 0.78 ? EquipmentSlotType.HEAD : h < 0.16 ? EquipmentSlotType.FEET
                : h < 0.5 ? EquipmentSlotType.LEGS : EquipmentSlotType.CHEST;
    }

    public static float protection(ItemStack armor) {
        if (armor.getItem() instanceof RustArmorItem) return ((RustArmorItem)armor.getItem()).bulletProtection();
        if (armor.getItem() instanceof ArmorItem) return Math.min(0.4F,((ArmorItem)armor.getItem()).getDamageReduceAmount()*0.06F);
        return 0F;
    }

    public static float absorb(LivingEntity target, Vector3d hit, float amount) {
        if (!(target instanceof PlayerEntity)) return amount;
        EquipmentSlotType slot = slot(target,hit);
        ItemStack armor = target.getItemStackFromSlot(slot);
        float reduction = protection(armor);
        if (reduction > 0F && !((PlayerEntity)target).isCreative()) {
            armor.damageItem(Math.max(1,(int)Math.ceil(amount)),target,e -> e.sendBreakAnimation(slot));
        }
        return amount*(1F-reduction);
    }
}
