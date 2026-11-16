package rustcraft.world.map;

import net.minecraft.entity.player.ServerPlayerEntity;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.text.TranslationTextComponent;
import net.minecraft.world.server.ServerWorld;
import rustcraft.world.WorldConfig;
import java.util.*;
import java.util.function.Supplier;

/** Server-thread, bounded map construction with persisted progress and an explicit layout version. */
public final class MapBuilder {
 private static final Map<ServerWorld,MapBuilder> JOBS=new IdentityHashMap<>();
 private static final Map<ServerWorld,List<BlueprintTask>> REPAIRS=new IdentityHashMap<>();
 private final List<BuildStep> tasks=new ArrayList<>();
 private int stage; private long lastProgress;
 private final boolean automatic;
 private final ServerWorld world;

 private MapBuilder(ServerWorld w){
  world=w;MapData d=MapData.get(w);automatic=d.automatic;stage=d.buildStage;BlockPos c=d.center;
  tasks.add(new TerrainTask(c,d.terrainSeed,stage==0?d.buildCursor:new int[0]));
  tasks.add(new ResourceTask(c,d.terrainSeed,stage==1?d.buildCursor:new int[0]));
  add("oilrig",()->Monuments.oilrig(c.add(196,8,0)),c.add(196,-1,-20));
  add("silo",()->Monuments.silo(c.add(50,0,-54)),c.add(50,1,-35));
  add("gasstation",()->Monuments.small(c.add(-52,0,40),true),c.add(-52,1,47));
  add("supermarket",()->Monuments.small(c.add(-55,0,-43),false),c.add(-55,1,-36));
  add("radtown",()->Monuments.tower(c.add(48,0,49)),c.add(53,1,49));
  add("harbor",()->Monuments.harbor(c.add(113,0,0)),c.add(111,1,0));
  add("refinery",()->Monuments.refinery(c.add(96,0,39)),c.add(96,1,51));
  add(null,()->Monuments.roadLoot(c),c);
  if(stage<0||stage>=tasks.size())stage=0;
  for(int i=0;i<stage;i++)tasks.set(i,null);
 }
 private void add(String id,Supplier<Blueprint> factory,BlockPos tp){
  tasks.add(new LazyBlueprintStep(id,factory,tp,stage==tasks.size()?MapData.get(world).buildCursor:new int[0]));
 }
 private final class LazyBlueprintStep implements BuildStep {
  private Supplier<Blueprint> factory;
  private final String id;private final BlockPos tp;private final int[] restored;
  private BlueprintTask task;
  LazyBlueprintStep(String id,Supplier<Blueprint> factory,BlockPos tp,int[] restored){this.id=id;this.factory=factory;this.tp=tp;this.restored=restored;}
  public boolean tick(ServerWorld w,long deadline){
   if(task==null){
    Blueprint bp=factory.get();factory=null;
    if(id!=null)MapData.get(w).putMonument(id,bp.worldBox(),tp);
    task=new BlueprintTask(bp,restored);
   }
   return task.tick(w,deadline);
  }
  public int percent(){return task==null?0:task.percent();}
  public int[] cursor(){return task==null?restored:task.cursor();}
 }
 public static boolean active(ServerWorld w){return JOBS.containsKey(w);}
 /** Explicit build: v1 is preserved and v2 is built in a separate region. V2 never repeats. */
 public static boolean start(ServerWorld w,boolean auto){
  MapData d=MapData.get(w);
  if(active(w)||(d.built&&d.version>=MapLayout.VERSION))return false;
  if(d.started&&d.version>=MapLayout.VERSION)return resume(w);
  if(auto&&(d.started||d.built))return false;
  boolean legacy=d.started||d.built||!d.monuments.isEmpty();
  if(legacy){
   d.center=new BlockPos(d.center.getX()+1024,64,d.center.getZ());
   for(int i=0;i<d.monuments.size();i++){
    MapData.Monument m=d.monuments.get(i);
    d.monuments.set(i,new MapData.Monument("legacy_"+m.id,m.box,m.tp));
   }
  }else d.center=new BlockPos(w.getSpawnPoint().getX()+256,64,w.getSpawnPoint().getZ());
  d.version=MapLayout.VERSION;d.terrainSeed=w.getSeed();d.automatic=auto;
  d.started=true;d.built=false;d.buildStage=0;d.buildCursor=new int[0];d.markDirty();
  JOBS.put(w,new MapBuilder(w));return true;
 }
 public static boolean resume(ServerWorld w){
  MapData d=MapData.get(w);
  if(active(w)||d.built||!d.started||d.version!=MapLayout.VERSION)return false;
  JOBS.put(w,new MapBuilder(w));return true;
 }
 public static void forget(ServerWorld w){JOBS.remove(w);REPAIRS.remove(w);}
 public static boolean repair(ServerWorld w){
  if(active(w)||REPAIRS.containsKey(w))return false;
  List<BlueprintTask> repairs=new ArrayList<>();
  for(MapData.Monument m:MapData.get(w).monuments)if(m.id.equals("oilrig")||m.id.endsWith("_oilrig")){
   BlockPos origin=new BlockPos(m.box.minX+25,m.box.minY+18,m.box.minZ+25);
   Blueprint bp=new Blueprint(origin);Monuments.oilrigAccess(bp);
   bp.fill(-14,1,-2,-14,15,-2,net.minecraft.block.Blocks.IRON_BLOCK);
   bp.ladderColumn(-13,-2,1,15,net.minecraft.util.Direction.EAST);
   bp.fill(-10,14,0,-10,22,0,net.minecraft.block.Blocks.IRON_BLOCK);
   bp.ladderColumn(-9,0,15,22,net.minecraft.util.Direction.EAST);
   repairs.add(new BlueprintTask(bp));
  }
  if(repairs.isEmpty())return false;
  REPAIRS.put(w,repairs);return true;
 }
 public static int progress(ServerWorld w){
  MapBuilder b=JOBS.get(w);MapData d=MapData.get(w);
  return b==null?(d.built?100:0):Math.min(99,b.stage==0?b.tasks.get(0).percent()*85/100:85+((b.stage-1)*100+b.tasks.get(b.stage).percent())*15/((b.tasks.size()-1)*100));
 }
 public static void tick(ServerWorld w){
  long deadline=System.nanoTime()+Math.min(10,WorldConfig.BUILD_BUDGET_MS.get())*1000000L;
  List<BlueprintTask> repairs=REPAIRS.get(w);
  if(repairs!=null){
   while(!repairs.isEmpty()&&System.nanoTime()<deadline){if(repairs.get(0).tick(w,deadline))repairs.remove(0);else break;}
   if(repairs.isEmpty()){
    REPAIRS.remove(w);for(ServerPlayerEntity p:w.getPlayers())p.sendStatusMessage(new TranslationTextComponent("rustcraft.world.map.repaired"),false);
   }
  }
  MapBuilder b=JOBS.get(w);if(b==null||System.nanoTime()>=deadline)return;
  MapData d=MapData.get(w);
  boolean stageDone=b.tasks.get(b.stage).tick(w,deadline);
  if(stageDone){b.tasks.set(b.stage,null);b.stage++;}
  d.buildStage=b.stage;d.buildCursor=b.stage<b.tasks.size()?b.tasks.get(b.stage).cursor():new int[0];d.markDirty();
  if(b.stage>=b.tasks.size()){
   // Persist placed chunks before the durable "built" marker; a crash must not
   // leave an incomplete island which the next startup considers finished.
   w.save(null,true,false);
   d.built=true;d.markDirty();JOBS.remove(w);
   w.getSavedData().save();
   if(b.automatic)w.func_241124_a__(d.center.up(),0F);
   for(ServerPlayerEntity pl:w.getPlayers()){
    pl.sendStatusMessage(new TranslationTextComponent("rustcraft.world.map.v2.done"),false);
    if(b.automatic&&pl.ticksExisted<20000)SafeSpawns.randomize(pl,w);
   }
   org.apache.logging.log4j.LogManager.getLogger().info("RustCraft map v{} ready at {}",d.version,d.center);
  }else if(w.getGameTime()-b.lastProgress>=100){
   b.lastProgress=w.getGameTime();for(ServerPlayerEntity pl:w.getPlayers())pl.sendStatusMessage(new TranslationTextComponent("rustcraft.world.map.progress",progress(w)),true);
  }
  if(stageDone&&!d.built)w.getSavedData().save();
 }
 public static void teleport(ServerPlayerEntity p,ServerWorld w,BlockPos pos){p.teleport(w,pos.getX()+0.5D,pos.getY(),pos.getZ()+0.5D,0F,0F);}
}
