package rustcraft.survival;

import net.minecraft.block.Block;
import net.minecraft.item.BlockItem;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.tileentity.TileEntityType;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.common.ForgeConfigSpec;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.fml.ModLoadingContext;
import net.minecraftforge.fml.RegistryObject;
import net.minecraftforge.fml.config.ModConfig;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import rustcraft.RustCraft;

/** Ресурсы, инструменты и добыча узлов. Количества настраиваются на сервере. */
public final class SurvivalModule {
    private SurvivalModule() {}
    public static final DeferredRegister<Item> ITEMS = DeferredRegister.create(ForgeRegistries.ITEMS, RustCraft.MODID);
    public static final DeferredRegister<Block> BLOCKS = DeferredRegister.create(ForgeRegistries.BLOCKS, RustCraft.MODID);
    public static final DeferredRegister<TileEntityType<?>> TILES = DeferredRegister.create(ForgeRegistries.TILE_ENTITIES, RustCraft.MODID);
    public static final ForgeConfigSpec SPEC;
    public static final ForgeConfigSpec.DoubleValue RATE;
    public static final ForgeConfigSpec.IntValue RESPAWN;
    public static final ForgeConfigSpec.DoubleValue NATURAL_SPAWN_CHANCE;
    static {
        ForgeConfigSpec.Builder b = new ForgeConfigSpec.Builder();
        RATE = b.comment("Множитель ресурсов для одиночной игры (Rust x2 по умолчанию).")
                .defineInRange("gatherRate", 2.0D, 0.25D, 10.0D);
        RESPAWN = b.comment("Восстановление дерева, руды и конопли, минут игрового времени.")
                .defineInRange("nodeRespawnMinutes", 8, 1, 120);
        NATURAL_SPAWN_CHANCE = b.comment("Доля разрешённых естественных попыток спавна мобов (0.25 = в четыре раза реже). Яйца и команды не затронуты.")
                .defineInRange("naturalSpawnChance", 0.25D, 0.0D, 1.0D);
        SPEC = b.build();
    }
    private static Item.Properties props() { return new Item.Properties().group(RustCraft.TAB); }
    public static final RegistryObject<Item> WOOD = ITEMS.register("wood", () -> new Item(props()) {
        @Override public int getBurnTime(ItemStack stack) { return 200; }
    });
    public static final RegistryObject<Item> STONES = ITEMS.register("stones", () -> new Item(props()));
    public static final RegistryObject<Item> METAL_ORE = ITEMS.register("metal_ore", () -> new Item(props()));
    public static final RegistryObject<Item> CLOTH = ITEMS.register("cloth", () -> new Item(props()));
    public static final RegistryObject<Item> ANIMAL_FAT = ITEMS.register("animal_fat", () -> new Item(props()));
    public static final RegistryObject<Item> ROCK = ITEMS.register("rock", () -> new HarvestToolItem(0, false, 20, 16, 1000));
    public static final RegistryObject<Item> STONE_HATCHET = ITEMS.register("stone_hatchet", () -> new HarvestToolItem(1, true, 50, 12, 800));
    public static final RegistryObject<Item> STONE_PICKAXE = ITEMS.register("stone_pickaxe", () -> new HarvestToolItem(1, false, 50, 12, 800));
    public static final RegistryObject<Item> SALVAGED_AXE = ITEMS.register("salvaged_axe", () -> new HarvestToolItem(2, true, 100, 9, 1500));
    public static final RegistryObject<Item> SALVAGED_PICKAXE = ITEMS.register("salvaged_pickaxe", () -> new HarvestToolItem(2, false, 100, 9, 1500));
    public static final RegistryObject<Block> TREE = node("resource_tree", ResourceNodeBlock.Kind.TREE);
    public static final RegistryObject<Block> STONE = node("stone_node", ResourceNodeBlock.Kind.STONE);
    public static final RegistryObject<Block> METAL = node("metal_node", ResourceNodeBlock.Kind.METAL);
    public static final RegistryObject<Block> SULFUR = node("sulfur_node", ResourceNodeBlock.Kind.SULFUR);
    public static final RegistryObject<Block> HEMP = node("hemp", ResourceNodeBlock.Kind.HEMP);
    public static final RegistryObject<Block> CANOPY = BLOCKS.register("resource_tree_canopy", TreeCanopyBlock::new);
    public static final RegistryObject<TileEntityType<ResourceNodeTile>> NODE_TILE = TILES.register("resource_node",
            () -> TileEntityType.Builder.create(ResourceNodeTile::new, TREE.get(), STONE.get(), METAL.get(), SULFUR.get(), HEMP.get()).build(null));
    private static RegistryObject<Block> node(String id, ResourceNodeBlock.Kind kind) {
        RegistryObject<Block> b = BLOCKS.register(id, () -> new ResourceNodeBlock(kind));
        ITEMS.register(id, () -> new BlockItem(b.get(), props()));
        return b;
    }
    public static void init(IEventBus bus) {
        ITEMS.register(bus); BLOCKS.register(bus); TILES.register(bus);
        ModLoadingContext.get().registerConfig(ModConfig.Type.SERVER, SPEC, "rustcraft-survival.toml");
        HarvestNetwork.init();
        MinecraftForge.EVENT_BUS.register(SurvivalEvents.class);
        MinecraftForge.EVENT_BUS.register(TerrainProtection.class);
        DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> SurvivalClient.init(bus));
    }
}
