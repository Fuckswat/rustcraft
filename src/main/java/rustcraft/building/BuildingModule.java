package rustcraft.building;

import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.eventbus.api.IEventBus;
import rustcraft.building.network.BuildingNetwork;

/**
 * Модуль building: стены с HP и уровнями, двери с замками, шкаф с инструментами,
 * молоток и рейдовая взрывчатка (C4, сумка, бобовка).
 */
public final class BuildingModule {
    private BuildingModule() {}

    /** Вызывается из конструктора RustCraft. */
    public static void init(IEventBus modBus) {
        BRegistry.register(modBus);
        BuildingNetwork.register();
        MinecraftForge.EVENT_BUS.register(BuildingEvents.class);
    }
}
