package rustcraft.building.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.RenderTypeLookup;
import net.minecraft.client.renderer.entity.SpriteRenderer;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.client.registry.RenderingRegistry;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;
import rustcraft.RustCraft;
import rustcraft.building.BRegistry;

/** Клиентская настройка модуля: слои рендера дверей, рендер брошенной взрывчатки. */
@Mod.EventBusSubscriber(modid = RustCraft.MODID, bus = Mod.EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public final class BuildingClient {
    private BuildingClient() {}

    @SubscribeEvent
    public static void onClientSetup(FMLClientSetupEvent event) {
        event.enqueueWork(() -> {
            RenderTypeLookup.setRenderLayer(BRegistry.WOOD_DOOR.get(), RenderType.getCutout());
            RenderTypeLookup.setRenderLayer(BRegistry.METAL_DOOR.get(), RenderType.getCutout());
            RenderTypeLookup.setRenderLayer(BRegistry.GARAGE_DOOR.get(), RenderType.getCutout());
            RenderTypeLookup.setRenderLayer(BRegistry.ARMORED_DOOR.get(), RenderType.getCutout());
        });
        RenderingRegistry.registerEntityRenderingHandler(BRegistry.THROWN_C4.get(),
                m -> new SpriteRenderer<>(m, Minecraft.getInstance().getItemRenderer(), 0.8F, false));
        RenderingRegistry.registerEntityRenderingHandler(BRegistry.THROWN_SATCHEL.get(),
                m -> new SpriteRenderer<>(m, Minecraft.getInstance().getItemRenderer(), 0.8F, false));
        RenderingRegistry.registerEntityRenderingHandler(BRegistry.THROWN_BEANCAN.get(),
                m -> new SpriteRenderer<>(m, Minecraft.getInstance().getItemRenderer(), 0.7F, false));
    }
}
