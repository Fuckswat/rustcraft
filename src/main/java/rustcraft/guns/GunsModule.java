package rustcraft.guns;

import net.minecraft.entity.EntityClassification;
import net.minecraft.entity.EntityType;
import net.minecraft.potion.Effect;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.fml.RegistryObject;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import rustcraft.RustCraft;

/**
 * Модуль guns: огнестрел, патроны, медицина, броня, кровотечение, HUD здоровья 0–100.
 * Управление: ЛКМ — огонь (зажать для автомата), ПКМ — прицел, R — перезарядка.
 */
public final class GunsModule {
    private GunsModule() {}

    public static final DeferredRegister<Effect> EFFECTS = DeferredRegister.create(ForgeRegistries.POTIONS, RustCraft.MODID);
    public static final DeferredRegister<EntityType<?>> ENTITIES = DeferredRegister.create(ForgeRegistries.ENTITIES, RustCraft.MODID);

    public static final RegistryObject<Effect> BLEEDING = EFFECTS.register("bleeding", BleedingEffect::new);

    public static final RegistryObject<EntityType<RocketEntity>> ROCKET = ENTITIES.register("rocket",
            () -> EntityType.Builder.<RocketEntity>create(RocketEntity::new, EntityClassification.MISC)
                    .size(0.3F, 0.3F)
                    .trackingRange(8)
                    .func_233608_b_(2)
                    .setShouldReceiveVelocityUpdates(true)
                    .build("rustcraft:rocket"));

    /** Вызывается из конструктора RustCraft. */
    public static void init(IEventBus modBus) {
        GunsItems.ITEMS.register(modBus);
        EFFECTS.register(modBus);
        ENTITIES.register(modBus);
        GunNetwork.register();
        MinecraftForge.EVENT_BUS.register(GunServer.class);
        DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> rustcraft.guns.client.GunsClient.init(modBus));
    }
}
