package rustcraft.vehicles;

import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.registries.ForgeRegistries;

import javax.annotation.Nullable;

/** Межмодульные ссылки только по строковым ID, null-safe. */
public final class VehicleUtil {
    private VehicleUtil() {}

    /** Одна единица предмета топлива = столько "единиц" в баке. */
    public static final float UNITS_PER_FUEL_ITEM = 100.0F;

    @Nullable
    public static Item item(String id) {
        ResourceLocation rl = ResourceLocation.tryCreate(id);
        if (rl == null || !ForgeRegistries.ITEMS.containsKey(rl)) return null;
        Item item = ForgeRegistries.ITEMS.getValue(rl);
        return item == Items.AIR ? null : item;
    }

    /** Предмет-топливо: rustcraft:low_grade_fuel, если его нет — уголь/древесный уголь. */
    public static boolean isFuel(ItemStack stack) {
        if (stack == null || stack.isEmpty()) return false;
        Item lgf = item("rustcraft:low_grade_fuel");
        if (lgf != null) return stack.getItem() == lgf;
        return stack.getItem() == Items.COAL || stack.getItem() == Items.CHARCOAL;
    }

    /** Предмет-дроп по ID, иначе запасной ванильный. */
    public static Item itemOr(String id, Item fallback) {
        Item it = item(id);
        return it != null ? it : fallback;
    }

    public static boolean finite(double... values) {
        for (double v : values) {
            if (Double.isNaN(v) || Double.isInfinite(v)) return false;
        }
        return true;
    }

    public static double clamp(double v, double min, double max) {
        return v < min ? min : (v > max ? max : v);
    }

    public static float clamp(float v, float min, float max) {
        return v < min ? min : (v > max ? max : v);
    }
}
