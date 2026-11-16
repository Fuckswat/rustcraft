package rustcraft;

import net.minecraft.item.ItemGroup;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import rustcraft.bots.BotsModule;
import rustcraft.building.BuildingModule;
import rustcraft.guns.GunsModule;
import rustcraft.vehicles.VehiclesModule;
import rustcraft.world.WorldModule;
import rustcraft.survival.SurvivalModule;
import rustcraft.progression.ProgressionModule;

/**
 * Главный класс мода. Каждый модуль сам регистрирует свои предметы/блоки/сущности
 * через собственные DeferredRegister в своём пакете — этот файл НЕ редактировать в модулях.
 */
@Mod(RustCraft.MODID)
public class RustCraft {
    public static final String MODID = "rustcraft";

    /** Общая вкладка творческого режима для всех модулей. */
    public static final ItemGroup TAB = new ItemGroup(MODID) {
        @Override
        public ItemStack createIcon() {
            return new ItemStack(Items.GUNPOWDER);
        }
    };

    public RustCraft() {
        IEventBus modBus = FMLJavaModLoadingContext.get().getModEventBus();
        GunsModule.init(modBus);
        BuildingModule.init(modBus);
        WorldModule.init(modBus);
        BotsModule.init(modBus);
        VehiclesModule.init(modBus);
        SurvivalModule.init(modBus);
        ProgressionModule.init(modBus);
    }
}
