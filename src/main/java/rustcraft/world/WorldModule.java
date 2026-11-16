package rustcraft.world;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.ModLoadingContext;
import net.minecraftforge.fml.config.ModConfig;
public final class WorldModule {
    private WorldModule() {}
    public static void init(IEventBus modBus) {
        WorldRegistry.register(modBus);
        ModLoadingContext.get().registerConfig(ModConfig.Type.COMMON, WorldConfig.SPEC, "rustcraft-world.toml");
        MinecraftForge.EVENT_BUS.register(new WorldEvents());
    }
}
