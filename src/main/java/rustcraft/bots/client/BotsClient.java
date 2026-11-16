package rustcraft.bots.client;

import net.minecraft.item.SpawnEggItem;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import net.minecraftforge.client.event.ColorHandlerEvent;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.client.registry.RenderingRegistry;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;
import rustcraft.bots.BotsModule;

/** Клиентская часть модуля bots (рендер). Загружается только на клиенте через DistExecutor. */
@OnlyIn(Dist.CLIENT)
public final class BotsClient {
    private BotsClient() {}

    public static void init(IEventBus modBus) {
        modBus.addListener(BotsClient::clientSetup);
        modBus.addListener(BotsClient::itemColors);
    }

    private static void clientSetup(FMLClientSetupEvent event) {
        RenderingRegistry.registerEntityRenderingHandler(BotsModule.SCIENTIST_TYPE,
                m -> new ScientistRenderer<>(m, ScientistRenderer.SCIENTIST_TEX, 1.0F));
        RenderingRegistry.registerEntityRenderingHandler(BotsModule.HEAVY_SCIENTIST_TYPE,
                m -> new ScientistRenderer<>(m, ScientistRenderer.HEAVY_TEX, 1.05F));
    }

    private static void itemColors(ColorHandlerEvent.Item event) {
        // Яйца уже окрашиваются ванилью (SpawnEggItem.getEggs), регистрируем на всякий случай явно.
        final SpawnEggItem a = (SpawnEggItem) BotsModule.SCIENTIST_SPAWN_EGG.get();
        final SpawnEggItem b = (SpawnEggItem) BotsModule.HEAVY_SCIENTIST_SPAWN_EGG.get();
        event.getItemColors().register((stack, tint) -> a.getColor(tint), a);
        event.getItemColors().register((stack, tint) -> b.getColor(tint), b);
    }
}
