package rustcraft.progression;

import net.minecraft.block.*;
import net.minecraft.block.material.Material;
import net.minecraft.command.Commands;
import net.minecraft.entity.player.*;
import net.minecraft.inventory.container.*;
import net.minecraft.item.*;
import net.minecraft.util.*;
import net.minecraft.util.math.*;
import net.minecraft.util.text.*;
import net.minecraft.world.World;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.common.*;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.*;
import net.minecraftforge.fml.*;
import net.minecraftforge.fml.config.ModConfig;
import net.minecraftforge.fml.network.NetworkHooks;
import net.minecraftforge.registries.*;
import rustcraft.RustCraft;

public final class ProgressionModule {
    private ProgressionModule() {}
    public static final DeferredRegister<Block> BLOCKS=DeferredRegister.create(ForgeRegistries.BLOCKS,"rustcraft");
    public static final DeferredRegister<Item> ITEMS=DeferredRegister.create(ForgeRegistries.ITEMS,"rustcraft");
    public static final DeferredRegister<ContainerType<?>> MENUS=DeferredRegister.create(ForgeRegistries.CONTAINERS,"rustcraft");
    public static final RegistryObject<Block> BENCH1=station("workbench_tier1",1),BENCH2=station("workbench_tier2",2),BENCH3=station("workbench_tier3",3),RESEARCH=station("research_table",4);
    public static final RegistryObject<ContainerType<ProgressionMenu>> MENU=MENUS.register("progression",()->net.minecraftforge.common.extensions.IForgeContainerType.create((id,inv,b)->new ProgressionMenu(id,inv,b.readVarInt(),b.readBlockPos())));
    public static final ForgeConfigSpec SPEC;
    public static final ForgeConfigSpec.DoubleValue COST,RESEARCH_COST;
    public static final ForgeConfigSpec.IntValue KIT_MINUTES;
    static {ForgeConfigSpec.Builder b=new ForgeConfigSpec.Builder();COST=b.comment("Multiplier on already reduced solo crafting costs").defineInRange("craftCostMultiplier",1D,0.1D,10D);RESEARCH_COST=b.defineInRange("researchCostMultiplier",1D,0.1D,10D);KIT_MINUTES=b.comment("One shared real-time cooldown for all survival kits").defineInRange("kitCooldownMinutes",30,1,1440);SPEC=b.build();}
    private static RegistryObject<Block> station(String id,int mode) {RegistryObject<Block> block=BLOCKS.register(id,()->new Station(mode));ITEMS.register(id,()->new BlockItem(block.get(),new Item.Properties().group(RustCraft.TAB)));return block;}
    public static void init(IEventBus bus) {BLOCKS.register(bus);ITEMS.register(bus);MENUS.register(bus);ProgressionNetwork.register();ModLoadingContext.get().registerConfig(ModConfig.Type.COMMON,SPEC,"rustcraft-progression.toml");MinecraftForge.EVENT_BUS.register(ProgressionModule.class);DistExecutor.unsafeRunWhenOn(Dist.CLIENT,()->()->rustcraft.progression.client.ProgressionClient.init(bus));}
    public static void open(ServerPlayerEntity p,int mode,BlockPos pos) {ProgressionNetwork.sync(p,"");NetworkHooks.openGui(p,new SimpleNamedContainerProvider((id,inv,user)->new ProgressionMenu(id,inv,mode,pos),new TranslationTextComponent(mode==5?"gui.rustcraft.kits":mode==4?"block.rustcraft.research_table":"gui.rustcraft.crafting")),buf->{buf.writeVarInt(mode);buf.writeBlockPos(pos);});}
    @SubscribeEvent public static void commands(RegisterCommandsEvent e) {
        e.getDispatcher().register(Commands.literal("craft").executes(c->{ServerPlayerEntity p=c.getSource().asPlayer();open(p,0,p.getPosition());return 1;}));
        com.mojang.brigadier.builder.LiteralArgumentBuilder<net.minecraft.command.CommandSource> kits=Commands.literal("kit").executes(c->{ServerPlayerEntity p=c.getSource().asPlayer();open(p,5,p.getPosition());return 1;});
        for(String id:Catalog.KIT_IDS)kits.then(Commands.literal(id).executes(c->{ServerPlayerEntity p=c.getSource().asPlayer();open(p,5,p.getPosition());((ProgressionMenu)p.openContainer).claimKit(p,id);return 1;}));
        e.getDispatcher().register(kits);
    }
    @SubscribeEvent public static void login(PlayerEvent.PlayerLoggedInEvent e) {if(e.getPlayer() instanceof ServerPlayerEntity)ProgressionNetwork.sync((ServerPlayerEntity)e.getPlayer(),"");}
    @SubscribeEvent public static void respawn(PlayerEvent.PlayerRespawnEvent e) {if(e.getPlayer() instanceof ServerPlayerEntity)ProgressionNetwork.sync((ServerPlayerEntity)e.getPlayer(),"");}
    public static int tierAt(World w,BlockPos p) {Block b=w.getBlockState(p).getBlock();return b instanceof Station&&((Station)b).mode<4?((Station)b).mode:0;}
    public static int nearbyTier(PlayerEntity p) {int result=0;BlockPos center=p.getPosition();for(BlockPos q:BlockPos.getAllInBoxMutable(center.add(-4,-3,-4),center.add(4,3,4)))if(p.getDistanceSq(q.getX()+.5,q.getY()+.5,q.getZ()+.5)<=36)result=Math.max(result,tierAt(p.world,q));return result;}
    public static final class Station extends Block {
        public final int mode;
        private static final net.minecraft.util.math.shapes.VoxelShape SHAPE=net.minecraft.util.math.shapes.VoxelShapes.or(Block.makeCuboidShape(0,11,0,16,16,16),Block.makeCuboidShape(1,0,1,4,11,4),Block.makeCuboidShape(12,0,1,15,11,4),Block.makeCuboidShape(1,0,12,4,11,15),Block.makeCuboidShape(12,0,12,15,11,15));
        Station(int mode) {super(AbstractBlock.Properties.create(mode==1||mode==4?Material.WOOD:Material.IRON).hardnessAndResistance(3.5F,8F).notSolid().sound(mode==1||mode==4?SoundType.WOOD:SoundType.METAL));this.mode=mode;}
        @Override public net.minecraft.util.math.shapes.VoxelShape getShape(BlockState s,net.minecraft.world.IBlockReader world,BlockPos p,net.minecraft.util.math.shapes.ISelectionContext context) {return SHAPE;}
        @Override public ActionResultType onBlockActivated(BlockState s,World w,BlockPos pos,PlayerEntity p,Hand hand,BlockRayTraceResult hit) {if(!w.isRemote&&p instanceof ServerPlayerEntity)open((ServerPlayerEntity)p,mode,pos);return ActionResultType.func_233537_a_(w.isRemote);}
    }
}
