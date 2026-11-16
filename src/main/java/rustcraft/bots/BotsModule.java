package rustcraft.bots;

import net.minecraft.entity.EntityClassification;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.ai.attributes.GlobalEntityTypeAttributes;
import net.minecraft.item.Item;
import net.minecraft.item.SpawnEggItem;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.fml.RegistryObject;
import net.minecraftforge.fml.event.lifecycle.FMLCommonSetupEvent;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import rustcraft.RustCraft;
import rustcraft.bots.net.BotNetwork;

/**
 * Модуль «bots»: вооружённые NPC-учёные, охраняющие монументы (нефтяная вышка, ракетная шахта).
 *
 * Сущности: rustcraft:scientist, rustcraft:heavy_scientist.
 * Предметы: rustcraft:scientist_spawn_egg, rustcraft:heavy_scientist_spawn_egg.
 */
public final class BotsModule {
    private BotsModule() {}

    public static final DeferredRegister<EntityType<?>> ENTITIES =
            DeferredRegister.create(ForgeRegistries.ENTITIES, RustCraft.MODID);
    public static final DeferredRegister<Item> ITEMS =
            DeferredRegister.create(ForgeRegistries.ITEMS, RustCraft.MODID);

    /*
     * Типы сущностей создаются заранее (статически), потому что в 1.16 SpawnEggItem требует
     * готовый EntityType в конструкторе, а предметы регистрируются раньше сущностей.
     */
    public static final EntityType<ScientistEntity> SCIENTIST_TYPE =
            EntityType.Builder.<ScientistEntity>create(ScientistEntity::new, EntityClassification.MONSTER)
                    .size(0.6F, 1.95F)
                    .trackingRange(10)
                    .build(RustCraft.MODID + ":scientist");

    public static final EntityType<HeavyScientistEntity> HEAVY_SCIENTIST_TYPE =
            EntityType.Builder.<HeavyScientistEntity>create(HeavyScientistEntity::new, EntityClassification.MONSTER)
                    .size(0.7F, 2.05F)
                    .trackingRange(10)
                    .build(RustCraft.MODID + ":heavy_scientist");

    public static final RegistryObject<EntityType<ScientistEntity>> SCIENTIST =
            ENTITIES.register("scientist", () -> SCIENTIST_TYPE);
    public static final RegistryObject<EntityType<HeavyScientistEntity>> HEAVY_SCIENTIST =
            ENTITIES.register("heavy_scientist", () -> HEAVY_SCIENTIST_TYPE);

    public static final int SCIENTIST_EGG_PRIMARY = 0x2C8DAA;
    public static final int SCIENTIST_EGG_SECONDARY = 0x1B1F22;
    public static final int HEAVY_EGG_PRIMARY = 0x3A3D38;
    public static final int HEAVY_EGG_SECONDARY = 0xD08A2A;

    public static final RegistryObject<Item> SCIENTIST_SPAWN_EGG = ITEMS.register("scientist_spawn_egg",
            () -> new SpawnEggItem(SCIENTIST_TYPE, SCIENTIST_EGG_PRIMARY, SCIENTIST_EGG_SECONDARY,
                    new Item.Properties().group(RustCraft.TAB)));
    public static final RegistryObject<Item> HEAVY_SCIENTIST_SPAWN_EGG = ITEMS.register("heavy_scientist_spawn_egg",
            () -> new SpawnEggItem(HEAVY_SCIENTIST_TYPE, HEAVY_EGG_PRIMARY, HEAVY_EGG_SECONDARY,
                    new Item.Properties().group(RustCraft.TAB)));

    /** Вызывается из конструктора RustCraft. */
    public static void init(IEventBus modBus) {
        ENTITIES.register(modBus);
        ITEMS.register(modBus);
        BotNetwork.register();
        modBus.addListener(BotsModule::commonSetup);
        DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> rustcraft.bots.client.BotsClient.init(modBus));
    }

    private static void commonSetup(FMLCommonSetupEvent event) {
        event.enqueueWork(() -> {
            // В Forge 34.1.42 ещё нет EntityAttributeCreationEvent.
            GlobalEntityTypeAttributes.put(SCIENTIST_TYPE, ScientistEntity.createAttributes().create());
            GlobalEntityTypeAttributes.put(HEAVY_SCIENTIST_TYPE, HeavyScientistEntity.createAttributes().create());
        });
    }
}
