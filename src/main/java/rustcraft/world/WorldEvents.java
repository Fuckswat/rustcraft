package rustcraft.world;
import com.mojang.brigadier.arguments.StringArgumentType;
import net.minecraft.command.Commands;
import net.minecraft.entity.player.ServerPlayerEntity;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.text.TranslationTextComponent;
import net.minecraft.world.World;
import net.minecraft.world.server.ServerWorld;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.world.BlockEvent;
import net.minecraftforge.event.world.ExplosionEvent;
import net.minecraftforge.event.world.WorldEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import rustcraft.world.block.LootBarrelBlock;
import rustcraft.world.map.*;
public final class WorldEvents {
 private final java.util.Set<ServerWorld> newWorlds=java.util.Collections.newSetFromMap(new java.util.IdentityHashMap<ServerWorld,Boolean>());
 private final java.util.Set<ServerWorld> safeWorldSpawns=java.util.Collections.newSetFromMap(new java.util.IdentityHashMap<ServerWorld,Boolean>());
 private final java.util.Map<java.util.UUID,long[]> pendingSpawnChecks=new java.util.HashMap<>();
 @SubscribeEvent public void newSpawn(WorldEvent.CreateSpawnPosition e){
  if(e.getWorld() instanceof ServerWorld)newWorlds.add((ServerWorld)e.getWorld());
 }
 @SubscribeEvent public void tick(TickEvent.WorldTickEvent e){
  if(e.phase!=TickEvent.Phase.END||!(e.world instanceof ServerWorld)||e.world.getDimensionKey()!=World.OVERWORLD)return;
  ServerWorld w=(ServerWorld)e.world;MapData d=MapData.get(w);
  boolean fresh=newWorlds.remove(w);
  if(!d.built&&!MapBuilder.active(w)){
   if(d.started&&d.version>=MapLayout.VERSION)MapBuilder.resume(w);
   else if(WorldConfig.AUTO_MAP.get()&&fresh)MapBuilder.start(w,true);
  }
  MapBuilder.tick(w);
  if(!d.built)safeWorldSpawns.remove(w);
  if(d.built&&!safeWorldSpawns.contains(w)&&SafeSpawns.ensureWorldSpawn(w))safeWorldSpawns.add(w);
  if(w.getGameTime()%20==0)d.tickRespawns(w);
 }
 @SubscribeEvent public void unload(WorldEvent.Unload e){if(e.getWorld() instanceof ServerWorld){MapBuilder.forget((ServerWorld)e.getWorld());newWorlds.remove((ServerWorld)e.getWorld());safeWorldSpawns.remove((ServerWorld)e.getWorld());}}
 @SubscribeEvent public void login(PlayerEvent.PlayerLoggedInEvent e){
  if(e.getPlayer() instanceof ServerPlayerEntity){
   ServerPlayerEntity p=(ServerPlayerEntity)e.getPlayer();p.sendStatusMessage(new TranslationTextComponent("rustcraft.world.map.v2.welcome"),false);
   if(p.getServerWorld().getDimensionKey()==World.OVERWORLD){
    SafeSpawns.firstArrival(p,p.getServerWorld());SafeSpawns.recoverUnderMap(p,p.getServerWorld());scheduleSpawnChecks(p,false);
   }
  }
 }
 @SubscribeEvent public void respawn(PlayerEvent.PlayerRespawnEvent e){
  if(e.isEndConquered()||!(e.getPlayer() instanceof ServerPlayerEntity))return;
  ServerPlayerEntity p=(ServerPlayerEntity)e.getPlayer();
  if(p.getServerWorld().getDimensionKey()==World.OVERWORLD){
   if(!SafeSpawns.hasWorkingBed(p))SafeSpawns.randomize(p,p.getServerWorld());
   scheduleSpawnChecks(p,true);
  }
 }
 private void scheduleSpawnChecks(ServerPlayerEntity p,boolean random){
  long now=p.getServerWorld().getGameTime();pendingSpawnChecks.put(p.getUniqueID(),new long[]{now+5,now+40,random?1:0});
 }
 @SubscribeEvent public void playerTick(TickEvent.PlayerTickEvent e){
  if(e.phase!=TickEvent.Phase.END||!(e.player instanceof ServerPlayerEntity))return;
  ServerPlayerEntity p=(ServerPlayerEntity)e.player;ServerWorld w=p.getServerWorld();
  if(w.getDimensionKey()!=World.OVERWORLD)return;
  long[] pending=pendingSpawnChecks.get(p.getUniqueID());long now=w.getGameTime();
  if(pending!=null){
   if(pending[0]>=0&&now>=pending[0]){
    if(pending[2]==1&&!SafeSpawns.hasWorkingBed(p))SafeSpawns.randomize(p,w);else SafeSpawns.recoverUnderMap(p,w);
    pending[0]=-1;
   }
   if(now>=pending[1]){SafeSpawns.recoverUnderMap(p,w);pendingSpawnChecks.remove(p.getUniqueID());}
  }
  if(p.ticksExisted%20==0&&!p.abilities.isFlying)SafeSpawns.recoverUnderMap(p,w);
 }
 @SubscribeEvent public void logout(PlayerEvent.PlayerLoggedOutEvent e){pendingSpawnChecks.remove(e.getPlayer().getUniqueID());}
 @SubscribeEvent public void commands(RegisterCommandsEvent e){
  e.getDispatcher().register(Commands.literal("rustmap")
   .executes(c->{info(c.getSource());return 1;})
   .then(Commands.literal("info").executes(c->{info(c.getSource());return 1;}))
   .then(Commands.literal("status").executes(c->{info(c.getSource());return 1;}))
   .then(Commands.literal("build").requires(s->s.hasPermissionLevel(2)).executes(c->build(c.getSource()))
    .then(Commands.literal("new").executes(c->build(c.getSource()))))
   .then(Commands.literal("repair").requires(s->s.hasPermissionLevel(2)).executes(c->{
    ServerWorld w=c.getSource().getWorld();boolean ok=MapBuilder.repair(w);
    c.getSource().sendFeedback(new TranslationTextComponent(ok?"rustcraft.world.map.repair_started":"rustcraft.world.map.repair_unavailable"),true);return ok?1:0;
   }))
   .then(Commands.literal("tp").requires(s->s.hasPermissionLevel(2)).then(Commands.argument("monument",StringArgumentType.word())
    .suggests((c,b)->{b.suggest("spawn");for(MapData.Monument m:MapData.get(c.getSource().getWorld()).monuments)b.suggest(m.id);return b.buildFuture();})
    .executes(c->{ServerWorld w=c.getSource().getWorld();MapData d=MapData.get(w);
     if(!d.built){c.getSource().sendErrorMessage(new TranslationTextComponent("rustcraft.world.map.notready"));return 0;}
     String id=StringArgumentType.getString(c,"monument");MapData.Monument m=d.find(id);BlockPos p=id.equals("spawn")?d.center.up():(m==null?null:m.tp);
     if(p==null){c.getSource().sendErrorMessage(new TranslationTextComponent("rustcraft.world.map.unknown"));return 0;}
     if(id.equals("spawn"))return SafeSpawns.randomize(c.getSource().asPlayer(),w)?1:0;
     MapBuilder.teleport(c.getSource().asPlayer(),w,p);return 1;
    }))));
 }
 private static int build(net.minecraft.command.CommandSource s){
  ServerWorld w=s.getWorld();if(w.getDimensionKey()!=World.OVERWORLD){s.sendErrorMessage(new TranslationTextComponent("rustcraft.world.map.overworld"));return 0;}
  boolean ok=MapBuilder.start(w,false);s.sendFeedback(new TranslationTextComponent(ok?"rustcraft.world.map.v2.started":"rustcraft.world.map.exists"),true);return ok?1:0;
 }
 private static void info(net.minecraft.command.CommandSource s){
  ServerWorld w=s.getWorld();MapData d=MapData.get(w);s.sendFeedback(new TranslationTextComponent("rustcraft.world.map.info",MapBuilder.progress(w),d.center.getX(),d.center.getY(),d.center.getZ()),false);
  int width=d.version>=2?257:109;
  s.sendFeedback(new TranslationTextComponent("rustcraft.world.map.layout",d.version,width,width),false);
  for(MapData.Monument m:d.monuments){
   boolean old=m.id.startsWith("legacy_");String id=old?m.id.substring(7):m.id;
   net.minecraft.util.text.ITextComponent name=new TranslationTextComponent("rustcraft.world.monument."+id);
   if(old)name=new TranslationTextComponent("rustcraft.world.map.legacy",name);
   s.sendFeedback(new TranslationTextComponent("rustcraft.world.map.monument",name,m.tp.getX(),m.tp.getY(),m.tp.getZ()),false);
  }
 }
 @SubscribeEvent public void breakBlock(BlockEvent.BreakEvent e){
  if(!WorldConfig.MONUMENT_PROTECTION.get()||e.getPlayer().isCreative()||!(e.getWorld() instanceof ServerWorld)||e.getState().getBlock() instanceof LootBarrelBlock)return;
  if(MapData.get((ServerWorld)e.getWorld()).monumentAt(e.getPos())!=null){e.setCanceled(true);e.getPlayer().sendStatusMessage(new TranslationTextComponent("rustcraft.world.map.protected"),true);}
 }
 @SubscribeEvent public void place(BlockEvent.EntityPlaceEvent e){
  if(!WorldConfig.MONUMENT_PROTECTION.get()||!(e.getWorld() instanceof ServerWorld)||!(e.getEntity() instanceof net.minecraft.entity.player.PlayerEntity))return;
  net.minecraft.entity.player.PlayerEntity p=(net.minecraft.entity.player.PlayerEntity)e.getEntity();
  if(!p.isCreative()&&MapData.get((ServerWorld)e.getWorld()).monumentAt(e.getPos())!=null){e.setCanceled(true);p.sendStatusMessage(new TranslationTextComponent("rustcraft.world.map.protected"),true);}
 }
 @SubscribeEvent public void explosion(ExplosionEvent.Detonate e){
  if(WorldConfig.MONUMENT_PROTECTION.get()&&e.getWorld() instanceof ServerWorld){MapData d=MapData.get((ServerWorld)e.getWorld());e.getAffectedBlocks().removeIf(p->d.monumentAt(p)!=null);}
 }
}
