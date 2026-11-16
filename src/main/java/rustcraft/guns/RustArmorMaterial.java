package rustcraft.guns;

import java.util.function.Supplier;

import net.minecraft.inventory.EquipmentSlotType;
import net.minecraft.item.IArmorMaterial;
import net.minecraft.item.Item;
import net.minecraft.item.Items;
import net.minecraft.item.crafting.Ingredient;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.SoundEvent;
import net.minecraft.util.SoundEvents;
import net.minecraftforge.registries.ForgeRegistries;

/**
 * Броня Rust. Имя материала "rustcraft:rust_metal" -> текстуры
 * assets/rustcraft/textures/models/armor/rust_metal_layer_1.png / _layer_2.png.
 */
public enum RustArmorMaterial implements IArmorMaterial {
    // порядок слотов: FEET, LEGS, CHEST, HEAD
    RUST_CLOTH("rustcraft:rust_cloth", 10, new int[]{1, 2, 3, 1}, 0, SoundEvents.ITEM_ARMOR_EQUIP_LEATHER, 0.0F, 0.0F,
            () -> repair("cloth", Items.LEATHER)),
    RUST_WOOD("rustcraft:rust_wood", 16, new int[]{1, 3, 4, 2}, 0, SoundEvents.ITEM_ARMOR_EQUIP_LEATHER, 0.0F, 0.0F,
            () -> repair("wood", Items.OAK_PLANKS)),
    RUST_METAL("rustcraft:rust_metal", 30, new int[]{2, 5, 7, 4}, 8, SoundEvents.ITEM_ARMOR_EQUIP_IRON, 2.0F, 0.1F,
            () -> repair("hqm", Items.IRON_INGOT)),
    RUST_ROADSIGN("rustcraft:rust_roadsign", 20, new int[]{2, 5, 6, 2}, 6, SoundEvents.ITEM_ARMOR_EQUIP_CHAIN, 1.0F, 0.0F,
            () -> repair("metal_fragments", Items.IRON_INGOT));

    private static final int[] BASE_DURABILITY = {13, 15, 16, 11};

    private final String name;
    private final int durabilityFactor;
    private final int[] reduction;
    private final int enchantability;
    private final SoundEvent sound;
    private final float toughness;
    private final float knockbackRes;
    private final Supplier<Ingredient> repair;

    RustArmorMaterial(String name, int durabilityFactor, int[] reduction, int enchantability, SoundEvent sound,
                      float toughness, float knockbackRes, Supplier<Ingredient> repair) {
        this.name = name;
        this.durabilityFactor = durabilityFactor;
        this.reduction = reduction;
        this.enchantability = enchantability;
        this.sound = sound;
        this.toughness = toughness;
        this.knockbackRes = knockbackRes;
        this.repair = repair;
    }

    /** Предмет мира (rustcraft:hqm и т.п.) по строковому ID, иначе ванильный запасной. */
    private static Ingredient repair(String worldId, Item fallback) {
        Item item = ForgeRegistries.ITEMS.getValue(new ResourceLocation("rustcraft", worldId));
        if (item == null || item == Items.AIR) item = fallback;
        return Ingredient.fromItems(item);
    }

    @Override
    public int getDurability(EquipmentSlotType slot) {
        return BASE_DURABILITY[slot.getIndex()] * durabilityFactor;
    }

    @Override
    public int getDamageReductionAmount(EquipmentSlotType slot) {
        return reduction[slot.getIndex()];
    }

    @Override
    public int getEnchantability() {
        return enchantability;
    }

    @Override
    public SoundEvent getSoundEvent() {
        return sound;
    }

    @Override
    public Ingredient getRepairMaterial() {
        return repair.get();
    }

    @Override
    public String getName() {
        return name;
    }

    @Override
    public float getToughness() {
        return toughness;
    }

    @Override
    public float getKnockbackResistance() {
        return knockbackRes;
    }
}
