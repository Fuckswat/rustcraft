package rustcraft.building;

import net.minecraft.block.Block;
import net.minecraft.entity.EntityClassification;
import net.minecraft.entity.EntityType;
import net.minecraft.item.BlockItem;
import net.minecraft.item.Item;
import net.minecraft.item.TallBlockItem;
import net.minecraft.tileentity.TileEntityType;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.RegistryObject;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import rustcraft.RustCraft;

/** Все регистрации модуля building. */
public final class BRegistry {
    private BRegistry() {}

    public static final DeferredRegister<Block> BLOCKS = DeferredRegister.create(ForgeRegistries.BLOCKS, RustCraft.MODID);
    public static final DeferredRegister<Item> ITEMS = DeferredRegister.create(ForgeRegistries.ITEMS, RustCraft.MODID);
    public static final DeferredRegister<TileEntityType<?>> TILES = DeferredRegister.create(ForgeRegistries.TILE_ENTITIES, RustCraft.MODID);
    public static final DeferredRegister<EntityType<?>> ENTITIES = DeferredRegister.create(ForgeRegistries.ENTITIES, RustCraft.MODID);

    // ---- стены ----
    public static final RegistryObject<Block> WOOD_WALL = BLOCKS.register("wood_wall", () -> new RustWallBlock(StructureKind.WOOD_WALL));
    public static final RegistryObject<Block> STONE_WALL = BLOCKS.register("stone_wall", () -> new RustWallBlock(StructureKind.STONE_WALL));
    public static final RegistryObject<Block> METAL_WALL = BLOCKS.register("metal_wall", () -> new RustWallBlock(StructureKind.METAL_WALL));
    public static final RegistryObject<Block> ARMORED_WALL = BLOCKS.register("armored_wall", () -> new RustWallBlock(StructureKind.ARMORED_WALL));

    // ---- двери ----
    public static final RegistryObject<Block> WOOD_DOOR = BLOCKS.register("wood_door", () -> new RustDoorBlock(StructureKind.WOOD_DOOR));
    public static final RegistryObject<Block> METAL_DOOR = BLOCKS.register("metal_door", () -> new RustDoorBlock(StructureKind.METAL_DOOR));
    public static final RegistryObject<Block> GARAGE_DOOR = BLOCKS.register("garage_door", () -> new RustDoorBlock(StructureKind.GARAGE_DOOR));
    public static final RegistryObject<Block> ARMORED_DOOR = BLOCKS.register("armored_door", () -> new RustDoorBlock(StructureKind.ARMORED_DOOR));

    // ---- шкаф с инструментами ----
    public static final RegistryObject<Block> TOOL_CUPBOARD = BLOCKS.register("tool_cupboard", ToolCupboardBlock::new);

    // ---- предметы-блоки ----
    public static final RegistryObject<Item> WOOD_WALL_ITEM = ITEMS.register("wood_wall", () -> new BlockItem(WOOD_WALL.get(), props()));
    public static final RegistryObject<Item> STONE_WALL_ITEM = ITEMS.register("stone_wall", () -> new BlockItem(STONE_WALL.get(), props()));
    public static final RegistryObject<Item> METAL_WALL_ITEM = ITEMS.register("metal_wall", () -> new BlockItem(METAL_WALL.get(), props()));
    public static final RegistryObject<Item> ARMORED_WALL_ITEM = ITEMS.register("armored_wall", () -> new BlockItem(ARMORED_WALL.get(), props()));
    public static final RegistryObject<Item> WOOD_DOOR_ITEM = ITEMS.register("wood_door", () -> new TallBlockItem(WOOD_DOOR.get(), props().maxStackSize(16)));
    public static final RegistryObject<Item> METAL_DOOR_ITEM = ITEMS.register("metal_door", () -> new TallBlockItem(METAL_DOOR.get(), props().maxStackSize(16)));
    public static final RegistryObject<Item> GARAGE_DOOR_ITEM = ITEMS.register("garage_door", () -> new TallBlockItem(GARAGE_DOOR.get(), props().maxStackSize(16)));
    public static final RegistryObject<Item> ARMORED_DOOR_ITEM = ITEMS.register("armored_door", () -> new TallBlockItem(ARMORED_DOOR.get(), props().maxStackSize(16)));
    public static final RegistryObject<Item> TOOL_CUPBOARD_ITEM = ITEMS.register("tool_cupboard", () -> new BlockItem(TOOL_CUPBOARD.get(), props().maxStackSize(1)));

    // ---- инструменты и замки ----
    public static final RegistryObject<Item> HAMMER = ITEMS.register("hammer", () -> new HammerItem(props().maxStackSize(1)));
    public static final RegistryObject<Item> KEY_LOCK = ITEMS.register("key_lock", () -> new LockItem(false, props().maxStackSize(16)));
    public static final RegistryObject<Item> CODE_LOCK = ITEMS.register("code_lock", () -> new LockItem(true, props().maxStackSize(16)));
    public static final RegistryObject<Item> DOOR_KEY = ITEMS.register("door_key", () -> new DoorKeyItem(props().maxStackSize(1)));

    // ---- взрывчатка ----
    public static final RegistryObject<Item> TIMED_EXPLOSIVE = ITEMS.register("timed_explosive", () -> new ExplosiveItem(ExplosiveKind.C4, props().maxStackSize(10)));
    public static final RegistryObject<Item> SATCHEL_CHARGE = ITEMS.register("satchel_charge", () -> new ExplosiveItem(ExplosiveKind.SATCHEL, props().maxStackSize(10)));
    public static final RegistryObject<Item> BEANCAN_GRENADE = ITEMS.register("beancan_grenade", () -> new ExplosiveItem(ExplosiveKind.BEANCAN, props().maxStackSize(16)));

    // ---- тайл-энтити ----
    static {
        for(StructureKind kind:new StructureKind[]{StructureKind.WOOD_WALL,StructureKind.STONE_WALL,StructureKind.METAL_WALL,StructureKind.ARMORED_WALL}) {
            for(String piece:new String[]{"foundation","floor","roof","stairs","window"}) {
                String id=kind.mat.name().toLowerCase(java.util.Locale.ROOT)+"_"+piece;
                RegistryObject<Block> block=BLOCKS.register(id,()->new BuildingPieceBlock(kind,piece));
                ITEMS.register(id,()->new BlockItem(block.get(),props()));
            }
        }
    }

    @SuppressWarnings("ConstantConditions")
    public static final RegistryObject<TileEntityType<StructureTileEntity>> STRUCTURE_TE = TILES.register("building_structure",
            () -> TileEntityType.Builder.create(StructureTileEntity::new,
                    BLOCKS.getEntries().stream().map(RegistryObject::get).filter(b->b instanceof IRustStructure).toArray(Block[]::new)).build(null));
    @SuppressWarnings("ConstantConditions")
    public static final RegistryObject<TileEntityType<ToolCupboardTileEntity>> TOOL_CUPBOARD_TE = TILES.register("tool_cupboard",
            () -> TileEntityType.Builder.create(ToolCupboardTileEntity::new, TOOL_CUPBOARD.get()).build(null));

    // ---- брошенная взрывчатка ----
    public static final RegistryObject<EntityType<ThrownExplosiveEntity>> THROWN_C4 = ENTITIES.register("thrown_c4", () -> thrown("thrown_c4"));
    public static final RegistryObject<EntityType<ThrownExplosiveEntity>> THROWN_SATCHEL = ENTITIES.register("thrown_satchel", () -> thrown("thrown_satchel"));
    public static final RegistryObject<EntityType<ThrownExplosiveEntity>> THROWN_BEANCAN = ENTITIES.register("thrown_beancan", () -> thrown("thrown_beancan"));

    private static EntityType<ThrownExplosiveEntity> thrown(String name) {
        return EntityType.Builder.<ThrownExplosiveEntity>create(ThrownExplosiveEntity::new, EntityClassification.MISC)
                .size(0.3F, 0.3F)
                .trackingRange(6)
                .func_233608_b_(5)
                .build(RustCraft.MODID + ":" + name);
    }

    private static Item.Properties props() {
        return new Item.Properties().group(RustCraft.TAB);
    }

    public static void register(IEventBus modBus) {
        BLOCKS.register(modBus);
        ITEMS.register(modBus);
        TILES.register(modBus);
        ENTITIES.register(modBus);
    }
}
