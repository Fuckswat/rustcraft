package rustcraft.world;

import net.minecraft.block.AbstractBlock;
import net.minecraft.block.Block;
import net.minecraft.block.SoundType;
import net.minecraft.block.material.Material;
import net.minecraft.block.material.MaterialColor;
import net.minecraft.inventory.container.ContainerType;
import net.minecraft.item.BlockItem;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Rarity;
import net.minecraft.item.TallBlockItem;
import net.minecraft.tileentity.TileEntityType;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.common.ToolType;
import net.minecraftforge.common.extensions.IForgeContainerType;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.RegistryObject;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import rustcraft.RustCraft;
import rustcraft.world.block.KeycardDoorBlock;
import rustcraft.world.block.KeycardReaderBlock;
import rustcraft.world.block.LockedCrateBlock;
import rustcraft.world.block.LootBarrelBlock;
import rustcraft.world.block.LootCrateBlock;
import rustcraft.world.block.NpcSpawnerBlock;
import rustcraft.world.block.RefineryBlock;
import rustcraft.world.item.KeycardItem;
import rustcraft.world.menu.RefineryContainer;
import rustcraft.world.tile.LockedCrateTile;
import rustcraft.world.tile.LootCrateTile;
import rustcraft.world.tile.NpcSpawnerTile;
import rustcraft.world.tile.RefineryTile;

import java.util.function.Supplier;

public final class WorldRegistry {
    private WorldRegistry() {}

    public static final DeferredRegister<Block> BLOCKS = DeferredRegister.create(ForgeRegistries.BLOCKS, RustCraft.MODID);
    public static final DeferredRegister<Item> ITEMS = DeferredRegister.create(ForgeRegistries.ITEMS, RustCraft.MODID);
    public static final DeferredRegister<TileEntityType<?>> TILES = DeferredRegister.create(ForgeRegistries.TILE_ENTITIES, RustCraft.MODID);
    public static final DeferredRegister<ContainerType<?>> CONTAINERS = DeferredRegister.create(ForgeRegistries.CONTAINERS, RustCraft.MODID);

    public static ResourceLocation loc(String path) {
        return new ResourceLocation(RustCraft.MODID, path);
    }

    private static Item.Properties props() {
        return new Item.Properties().group(RustCraft.TAB);
    }

    // ресурсы
    public static final RegistryObject<Item> SCRAP = ITEMS.register("scrap", () -> new Item(props()));
    public static final RegistryObject<Item> METAL_FRAGMENTS = ITEMS.register("metal_fragments", () -> new Item(props()));
    public static final RegistryObject<Item> HQM = ITEMS.register("hqm", () -> new Item(props().rarity(Rarity.UNCOMMON)));
    public static final RegistryObject<Item> CRUDE_OIL = ITEMS.register("crude_oil", () -> new Item(props()));
    public static final RegistryObject<Item> LOW_GRADE_FUEL = ITEMS.register("low_grade_fuel", () -> new Item(props()) {
        @Override
        public int getBurnTime(ItemStack stack) {
            return 800;
        }
    });
    public static final RegistryObject<Item> SULFUR = ITEMS.register("sulfur", () -> new Item(props()));

    // ключ-карты
    public static final RegistryObject<Item> KEYCARD_GREEN = ITEMS.register("keycard_green", () -> new KeycardItem(1, props().maxDamage(4)));
    public static final RegistryObject<Item> KEYCARD_BLUE = ITEMS.register("keycard_blue", () -> new KeycardItem(2, props().maxDamage(4).rarity(Rarity.UNCOMMON)));
    public static final RegistryObject<Item> KEYCARD_RED = ITEMS.register("keycard_red", () -> new KeycardItem(3, props().maxDamage(4).rarity(Rarity.RARE)));

    // блоки
    public static final RegistryObject<Block> SULFUR_ORE = block("sulfur_ore", () -> new Block(AbstractBlock.Properties
            .create(Material.ROCK, MaterialColor.YELLOW).hardnessAndResistance(3.0F, 3.0F)
            .harvestTool(ToolType.PICKAXE).harvestLevel(1).setRequiresTool()));

    public static final RegistryObject<Block> REFINERY = block("refinery", () -> new RefineryBlock(AbstractBlock.Properties
            .create(Material.IRON, MaterialColor.GRAY).hardnessAndResistance(3.5F, 6.0F).sound(SoundType.METAL)
            .harvestTool(ToolType.PICKAXE).setRequiresTool()
            .setLightLevel(s -> s.get(RefineryBlock.LIT) ? 12 : 0)));

    public static final RegistryObject<Block> CRATE_BASIC = block("crate_basic", () -> new LootCrateBlock(loc("world/crate_basic"), crateProps(MaterialColor.WOOD)));
    public static final RegistryObject<Block> CRATE_MILITARY = block("crate_military", () -> new LootCrateBlock(loc("world/crate_military"), crateProps(MaterialColor.GREEN)));
    public static final RegistryObject<Block> CRATE_ELITE = block("crate_elite", () -> new LootCrateBlock(loc("world/crate_elite"), crateProps(MaterialColor.BLUE)));

    public static final RegistryObject<Block> LOOT_BARREL = block("loot_barrel", () -> new LootBarrelBlock(AbstractBlock.Properties
            .create(Material.IRON, MaterialColor.BLUE).hardnessAndResistance(1.0F, 2.0F).sound(SoundType.METAL).notSolid()));
    public static final RegistryObject<Block> OIL_BARREL = block("oil_barrel", () -> new LootBarrelBlock(AbstractBlock.Properties
            .create(Material.IRON, MaterialColor.BLACK).hardnessAndResistance(1.0F, 2.0F).sound(SoundType.METAL).notSolid()));

    public static final RegistryObject<Block> LOCKED_CRATE = block("locked_crate", () -> new LockedCrateBlock(AbstractBlock.Properties
            .create(Material.IRON, MaterialColor.GRAY).hardnessAndResistance(-1.0F, 3600000.0F).sound(SoundType.METAL).noDrops()));

    public static final RegistryObject<Block> KEYCARD_READER = block("keycard_reader", () -> new KeycardReaderBlock(1, readerProps()));
    public static final RegistryObject<Block> KEYCARD_READER_BLUE = block("keycard_reader_blue", () -> new KeycardReaderBlock(2, readerProps()));
    public static final RegistryObject<Block> KEYCARD_READER_RED = block("keycard_reader_red", () -> new KeycardReaderBlock(3, readerProps()));

    public static final RegistryObject<Block> KEYCARD_DOOR = BLOCKS.register("keycard_door", () -> new KeycardDoorBlock(AbstractBlock.Properties
            .create(Material.IRON, MaterialColor.IRON).hardnessAndResistance(-1.0F, 3600000.0F).sound(SoundType.METAL).notSolid().noDrops()));
    public static final RegistryObject<Item> KEYCARD_DOOR_ITEM = ITEMS.register("keycard_door", () -> new TallBlockItem(KEYCARD_DOOR.get(), props()));

    public static final RegistryObject<Block> NPC_SPAWNER = block("npc_spawner", () -> new NpcSpawnerBlock(AbstractBlock.Properties
            .create(Material.IRON, MaterialColor.GRAY).hardnessAndResistance(-1.0F, 3600000.0F).sound(SoundType.METAL).noDrops()));

    public static final RegistryObject<TileEntityType<RefineryTile>> REFINERY_TILE = TILES.register("refinery",
            () -> TileEntityType.Builder.create(RefineryTile::new, REFINERY.get()).build(null));
    public static final RegistryObject<TileEntityType<LootCrateTile>> CRATE_TILE = TILES.register("loot_crate",
            () -> TileEntityType.Builder.create(LootCrateTile::new, CRATE_BASIC.get(), CRATE_MILITARY.get(), CRATE_ELITE.get()).build(null));
    public static final RegistryObject<TileEntityType<LockedCrateTile>> LOCKED_CRATE_TILE = TILES.register("locked_crate",
            () -> TileEntityType.Builder.create(LockedCrateTile::new, LOCKED_CRATE.get()).build(null));
    public static final RegistryObject<TileEntityType<NpcSpawnerTile>> NPC_SPAWNER_TILE = TILES.register("npc_spawner",
            () -> TileEntityType.Builder.create(NpcSpawnerTile::new, NPC_SPAWNER.get()).build(null));

    // контейнеры
    public static final RegistryObject<ContainerType<RefineryContainer>> REFINERY_CONTAINER = CONTAINERS.register("refinery",
            () -> IForgeContainerType.create((id, inv, buf) -> new RefineryContainer(id, inv)));

    private static AbstractBlock.Properties crateProps(MaterialColor color) {
        return AbstractBlock.Properties.create(Material.WOOD, color).hardnessAndResistance(2.5F, 6.0F)
                .sound(SoundType.WOOD).harvestTool(ToolType.AXE).noDrops();
    }

    private static AbstractBlock.Properties readerProps() {
        return AbstractBlock.Properties.create(Material.IRON, MaterialColor.GRAY).hardnessAndResistance(-1.0F, 3600000.0F)
                .sound(SoundType.METAL).noDrops().setLightLevel(s -> s.get(KeycardReaderBlock.POWERED) ? 7 : 2);
    }

    private static RegistryObject<Block> block(String name, Supplier<Block> sup) {
        RegistryObject<Block> ro = BLOCKS.register(name, sup);
        ITEMS.register(name, () -> new BlockItem(ro.get(), props()));
        return ro;
    }

    public static void register(IEventBus modBus) {
        BLOCKS.register(modBus);
        ITEMS.register(modBus);
        TILES.register(modBus);
        CONTAINERS.register(modBus);
    }
}
