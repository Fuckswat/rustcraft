package rustcraft.guns;

import net.minecraft.inventory.EquipmentSlotType;
import net.minecraft.item.ArmorItem;
import net.minecraft.item.Item;
import net.minecraftforge.fml.RegistryObject;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import rustcraft.RustCraft;

public final class GunsItems {
    private GunsItems() {}

    public static final DeferredRegister<Item> ITEMS = DeferredRegister.create(ForgeRegistries.ITEMS, RustCraft.MODID);

    // ---- оружие ----
    public static final RegistryObject<Item> REVOLVER = gun(GunStats.REVOLVER);
    public static final RegistryObject<Item> SEMI_PISTOL = gun(GunStats.SEMI_PISTOL);
    public static final RegistryObject<Item> THOMPSON = gun(GunStats.THOMPSON);
    public static final RegistryObject<Item> MP5 = gun(GunStats.MP5);
    public static final RegistryObject<Item> AK47 = gun(GunStats.AK47);
    public static final RegistryObject<Item> BOLT_RIFLE = gun(GunStats.BOLT_RIFLE);
    public static final RegistryObject<Item> PUMP_SHOTGUN = gun(GunStats.PUMP_SHOTGUN);
    public static final RegistryObject<Item> ROCKET_LAUNCHER = gun(GunStats.ROCKET_LAUNCHER);

    // ---- патроны ----
    public static final RegistryObject<Item> AMMO_PISTOL = simple("ammo_pistol", 64);
    public static final RegistryObject<Item> AMMO_RIFLE = simple("ammo_rifle", 64);
    public static final RegistryObject<Item> AMMO_SHOTGUN = simple("ammo_shotgun", 32);
    public static final RegistryObject<Item> AMMO_ROCKET = simple("ammo_rocket", 4);

    // ---- медицина ----
    public static final RegistryObject<Item> BANDAGE = ITEMS.register("bandage", () -> new MedItem(MedItem.Kind.BANDAGE, 16));
    public static final RegistryObject<Item> SYRINGE = ITEMS.register("syringe", () -> new MedItem(MedItem.Kind.SYRINGE, 8));
    public static final RegistryObject<Item> MEDKIT = ITEMS.register("medkit", () -> new MedItem(MedItem.Kind.MEDKIT, 4));

    // ---- броня ----
    public static final RegistryObject<Item> CLOTH_HOOD = armor("cloth_hood", RustArmorMaterial.RUST_CLOTH, EquipmentSlotType.HEAD);
    public static final RegistryObject<Item> CLOTH_SHIRT = armor("cloth_shirt", RustArmorMaterial.RUST_CLOTH, EquipmentSlotType.CHEST);
    public static final RegistryObject<Item> CLOTH_PANTS = armor("cloth_pants", RustArmorMaterial.RUST_CLOTH, EquipmentSlotType.LEGS);
    public static final RegistryObject<Item> CLOTH_BOOTS = armor("cloth_boots", RustArmorMaterial.RUST_CLOTH, EquipmentSlotType.FEET);
    public static final RegistryObject<Item> WOOD_HELMET = armor("wood_helmet", RustArmorMaterial.RUST_WOOD, EquipmentSlotType.HEAD);
    public static final RegistryObject<Item> WOOD_CHESTPLATE = armor("wood_chestplate", RustArmorMaterial.RUST_WOOD, EquipmentSlotType.CHEST);
    public static final RegistryObject<Item> WOOD_LEGGINGS = armor("wood_leggings", RustArmorMaterial.RUST_WOOD, EquipmentSlotType.LEGS);
    public static final RegistryObject<Item> ROADSIGN_HELMET = armor("roadsign_helmet", RustArmorMaterial.RUST_ROADSIGN, EquipmentSlotType.HEAD);
    public static final RegistryObject<Item> METAL_FACEMASK = armor("metal_facemask", RustArmorMaterial.RUST_METAL, EquipmentSlotType.HEAD);
    public static final RegistryObject<Item> METAL_CHESTPLATE = armor("metal_chestplate", RustArmorMaterial.RUST_METAL, EquipmentSlotType.CHEST);
    public static final RegistryObject<Item> ROADSIGN_JACKET = armor("roadsign_jacket", RustArmorMaterial.RUST_ROADSIGN, EquipmentSlotType.CHEST);
    public static final RegistryObject<Item> ROADSIGN_KILT = armor("roadsign_kilt", RustArmorMaterial.RUST_ROADSIGN, EquipmentSlotType.LEGS);

    private static RegistryObject<Item> gun(GunStats stats) {
        return ITEMS.register(stats.id, () -> new GunItem(stats));
    }

    private static RegistryObject<Item> simple(String id, int stack) {
        return ITEMS.register(id, () -> new Item(new Item.Properties().group(RustCraft.TAB).maxStackSize(stack)));
    }

    private static RegistryObject<Item> armor(String id, RustArmorMaterial mat, EquipmentSlotType slot) {
        return ITEMS.register(id, () -> new RustArmorItem(mat, slot));
    }

    public static String ammoIdFor(GunStats stats) {
        switch (stats.ammo) {
            case PISTOL: return "ammo_pistol";
            case RIFLE: return "ammo_rifle";
            case SHOTGUN: return "ammo_shotgun";
            default: return "ammo_rocket";
        }
    }

    public static Item ammoItemFor(GunStats stats) {
        switch (stats.ammo) {
            case PISTOL: return AMMO_PISTOL.get();
            case RIFLE: return AMMO_RIFLE.get();
            case SHOTGUN: return AMMO_SHOTGUN.get();
            default: return AMMO_ROCKET.get();
        }
    }
}
