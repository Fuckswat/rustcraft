package rustcraft.vehicles;

import net.minecraft.entity.EntityClassification;
import net.minecraft.entity.EntityType;
import net.minecraft.item.Item;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.fml.RegistryObject;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import rustcraft.RustCraft;
import rustcraft.vehicles.entity.MinicopterEntity;
import rustcraft.vehicles.entity.RhibEntity;
import rustcraft.vehicles.entity.ScrapHeliEntity;
import rustcraft.vehicles.item.VehicleItem;
import rustcraft.vehicles.network.VehicleNetwork;

/**
 * Транспорт: миникоптер, транспортный вертолёт, лодка RHIB.
 * Всё регистрируется здесь; клиентская часть (рендер, клавиши, HUD, звук) — в пакете client.
 */
public final class VehiclesModule {
    private VehiclesModule() {}

    public static final DeferredRegister<EntityType<?>> ENTITIES =
            DeferredRegister.create(ForgeRegistries.ENTITIES, RustCraft.MODID);
    public static final DeferredRegister<Item> ITEMS =
            DeferredRegister.create(ForgeRegistries.ITEMS, RustCraft.MODID);

    public static final RegistryObject<EntityType<MinicopterEntity>> MINICOPTER = ENTITIES.register("minicopter",
            () -> EntityType.Builder.<MinicopterEntity>create(MinicopterEntity::new, EntityClassification.MISC)
                    .size(2.5F, 2.2F).trackingRange(10).setUpdateInterval(1)
                    .setShouldReceiveVelocityUpdates(false).build("rustcraft:minicopter"));

    public static final RegistryObject<EntityType<ScrapHeliEntity>> SCRAP_HELI = ENTITIES.register("scrap_heli",
            () -> EntityType.Builder.<ScrapHeliEntity>create(ScrapHeliEntity::new, EntityClassification.MISC)
                    .size(3.5F, 2.4F).trackingRange(12).setUpdateInterval(1)
                    .setShouldReceiveVelocityUpdates(false).build("rustcraft:scrap_heli"));

    public static final RegistryObject<EntityType<RhibEntity>> RHIB = ENTITIES.register("rhib",
            () -> EntityType.Builder.<RhibEntity>create(RhibEntity::new, EntityClassification.MISC)
                    .size(2.4F, 1.0F).trackingRange(10).setUpdateInterval(1)
                    .setShouldReceiveVelocityUpdates(false).build("rustcraft:rhib"));

    public static final RegistryObject<Item> MINICOPTER_ITEM = ITEMS.register("minicopter_item",
            () -> new VehicleItem(new Item.Properties().group(RustCraft.TAB).maxStackSize(1), MINICOPTER::get, false));
    public static final RegistryObject<Item> SCRAP_HELI_ITEM = ITEMS.register("scrap_heli_item",
            () -> new VehicleItem(new Item.Properties().group(RustCraft.TAB).maxStackSize(1), SCRAP_HELI::get, false));
    public static final RegistryObject<Item> RHIB_ITEM = ITEMS.register("rhib_item",
            () -> new VehicleItem(new Item.Properties().group(RustCraft.TAB).maxStackSize(1), RHIB::get, true));

    /** Вызывается из конструктора RustCraft. */
    public static void init(IEventBus modBus) {
        ENTITIES.register(modBus);
        ITEMS.register(modBus);
        VehicleNetwork.register();
        DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> rustcraft.vehicles.client.VehiclesClient.init(modBus));
    }
}
