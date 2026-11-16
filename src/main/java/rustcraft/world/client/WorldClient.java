package rustcraft.world.client;

import net.minecraft.client.gui.ScreenManager;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.RenderTypeLookup;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;
import rustcraft.RustCraft;
import rustcraft.world.WorldRegistry;

/** Клиентская часть модуля world (экраны, слои рендера). Загружается только на клиенте. */
@Mod.EventBusSubscriber(modid = RustCraft.MODID, bus = Mod.EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public final class WorldClient {
    private WorldClient() {}

    @SubscribeEvent
    public static void onClientSetup(FMLClientSetupEvent event) {
        event.enqueueWork(() -> {
            ScreenManager.registerFactory(WorldRegistry.REFINERY_CONTAINER.get(), RefineryScreen::new);
            RenderTypeLookup.setRenderLayer(WorldRegistry.KEYCARD_DOOR.get(), RenderType.getCutout());
        });
    }
}
