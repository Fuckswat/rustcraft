package rustcraft.world.map;

import net.minecraft.block.BedBlock;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.block.RespawnAnchorBlock;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.player.ServerPlayerEntity;
import net.minecraft.nbt.CompoundNBT;
import net.minecraft.stats.Stats;
import net.minecraft.util.Direction;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.gen.Heightmap;
import net.minecraft.world.server.ServerWorld;
import javax.annotation.Nullable;

/** Safe arrivals inspect actual loaded-world ground, including maps saved before layout v2. */
public final class SafeSpawns {
    private static final String ARRIVED="RustCraftIslandArrivalV2";
    private SafeSpawns(){}

    public static void firstArrival(ServerPlayerEntity p,ServerWorld w){
        CompoundNBT saved=p.getPersistentData().getCompound(PlayerEntity.PERSISTED_NBT_TAG);
        if(!saved.getBoolean(ARRIVED)&&p.getStats().getValue(Stats.CUSTOM.get(Stats.PLAY_ONE_MINUTE))<200&&randomize(p,w)){
            saved.putBoolean(ARRIVED,true);p.getPersistentData().put(PlayerEntity.PERSISTED_NBT_TAG,saved);
        }
    }

    public static boolean hasWorkingBed(ServerPlayerEntity p){
        BlockPos bed=p.func_241140_K_();
        if(bed==null||p.getServer()==null)return false;
        ServerWorld w=p.getServer().getWorld(p.func_241141_L_());
        if(w==null||w!=p.getServerWorld()||p.getDistanceSq(bed.getX()+0.5,bed.getY()+0.5,bed.getZ()+0.5)>100)return false;
        BlockState s=w.getBlockState(bed);
        return s.getBlock() instanceof BedBlock||s.getBlock() instanceof RespawnAnchorBlock;
    }

    public static boolean randomize(ServerPlayerEntity p,ServerWorld w){
        MapData d=MapData.get(w);
        if(!d.built)return false;
        BlockPos safe=findSafe(w,d.center,d.version>=2,true);
        if(safe==null)return false;
        move(p,w,safe);return true;
    }

    /** Called after login/respawn and periodically. Never pulls a player out of a real bunker. */
    public static boolean recoverUnderMap(ServerPlayerEntity p,ServerWorld w){
        MapData d=MapData.get(w);
        if(!d.built||p.isSpectator())return false;
        BlockPos at=p.getPosition();
        if(d.monumentAt(at)!=null||d.monumentAt(at.up())!=null)return false;
        Region r=regionAt(d,at);
        if(r==null)return false;
        int expected=r.modern?MapLayout.surfaceHeight(at.getX()-r.center.getX(),at.getZ()-r.center.getZ()):r.center.getY();
        // Surface detection is checked as well: a formula alone must never become a teleport Y.
        int actual=w.getHeight(Heightmap.Type.WORLD_SURFACE,at.getX(),at.getZ());
        boolean buried=p.getPosY()<expected+0.9&&!w.getBlockState(at).getCollisionShape(w,at).isEmpty();
        if(!buried&&!isBelowIsland(p.getPosY(),expected,actual))return false;
        BlockPos safe=findSafe(w,r.center,r.modern,true);
        if(safe==null)return false;
        move(p,w,safe);return true;
    }

    /** Pure threshold used by focused QA and the runtime recovery guard. */
    public static boolean isBelowIsland(double feetY,int expectedGroundY,int actualFirstAirY){
        return feetY<expectedGroundY-1 && (feetY<actualFirstAirY-2 || feetY<expectedGroundY-8);
    }

    /** Both automatic and operator-built islands need a vanilla fallback above the real surface. */
    public static boolean ensureWorldSpawn(ServerWorld w){
        MapData d=MapData.get(w);
        if(!d.built)return false;
        BlockPos safe=findSafe(w,d.center,d.version>=2,false);
        if(safe==null)return false;
        w.func_241124_a__(safe,0F);return true;
    }

    @Nullable
    public static BlockPos findSafe(ServerWorld w,BlockPos center,boolean modern,boolean random){
        MapData d=MapData.get(w);
        int radius=modern?108:45;
        if(!random){BlockPos central=surfaceAt(w,d,center.getX(),center.getZ());if(central!=null)return central;}
        for(int attempt=0;attempt<192;attempt++){
            int x=w.rand.nextInt(radius*2+1)-radius,z=w.rand.nextInt(radius*2+1)-radius;
            if(modern&&(!MapLayout.isLand(x,z)||MapLayout.pad(x,z)))continue;
            BlockPos p=surfaceAt(w,d,center.getX()+x,center.getZ()+z);
            if(p!=null)return p;
        }
        // A bounded deterministic scan also works when the surrounding RNG samples player structures.
        for(int radiusStep=0;radiusStep<=radius;radiusStep+=3){
            for(int x=-radiusStep;x<=radiusStep;x+=3)for(int sign:new int[]{-1,1}){
                int z=sign*radiusStep;
                BlockPos p=surfaceAt(w,d,center.getX()+x,center.getZ()+z);if(p!=null)return p;
                p=surfaceAt(w,d,center.getX()+z,center.getZ()+x);if(p!=null)return p;
            }
        }
        return null;
    }

    @Nullable
    private static BlockPos surfaceAt(ServerWorld w,MapData data,int x,int z){
        for(MapData.Monument m:data.monuments)if(x>=m.box.minX&&x<=m.box.maxX&&z>=m.box.minZ&&z<=m.box.maxZ)return null;
        int firstAir=w.getHeight(Heightmap.Type.WORLD_SURFACE,x,z);
        if(firstAir<2||firstAir>253)return null;
        BlockPos feet=new BlockPos(x,firstAir,z);
        if(!w.isAirBlock(feet)||!w.isAirBlock(feet.up()))return null;
        BlockState ground=w.getBlockState(feet.down());
        Block b=ground.getBlock();
        if(!naturalGround(b)||!ground.getFluidState().isEmpty()||!ground.isSolidSide(w,feet.down(),Direction.UP))return null;
        return feet;
    }

    private static boolean naturalGround(Block b){
        return b==Blocks.GRASS_BLOCK||b==Blocks.SAND||b==Blocks.GRAVEL||b==Blocks.SNOW_BLOCK
            ||b==Blocks.DIRT||b==Blocks.COARSE_DIRT||b==Blocks.PODZOL||b==Blocks.STONE||b==Blocks.SMOOTH_STONE;
    }
    private static void move(ServerPlayerEntity p,ServerWorld w,BlockPos feet){
        MapBuilder.teleport(p,w,feet);p.setMotion(0,0,0);p.fallDistance=0;
    }
    private static final class Region {
        final BlockPos center;final boolean modern;
        Region(BlockPos center,boolean modern){this.center=center;this.modern=modern;}
        boolean contains(BlockPos p){
            int x=p.getX()-center.getX(),z=p.getZ()-center.getZ();
            return modern?MapLayout.isLand(x,z):Math.abs(x)<=52&&Math.abs(z)<=52;
        }
    }
    @Nullable
    private static Region regionAt(MapData d,BlockPos p){
        Region current=new Region(d.center,d.version>=2);
        if(current.contains(p))return current;
        for(MapData.Monument m:d.monuments)if(m.id.equals("legacy_silo")){
            Region old=new Region(new BlockPos(m.box.minX-7,m.box.minY+35,m.box.minZ+43),false);
            if(old.contains(p))return old;
        }
        for(MapData.Monument m:d.monuments)if(m.id.equals("legacy_oilrig")){
            Region old=new Region(new BlockPos(m.box.minX-80,m.box.minY+9,m.box.minZ+25),false);
            if(old.contains(p))return old;
        }
        return null;
    }
}
