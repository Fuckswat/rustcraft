package rustcraft.world.map;

import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.server.ServerWorld;
import net.minecraft.world.gen.Heightmap;
import net.minecraftforge.registries.ForgeRegistries;

/** Streams one column at a time. No island-sized HashMap and no asynchronous world mutations. */
public final class TerrainTask implements BuildStep {
    private static final int BOTTOM=27, TOP=255;
    private final BlockPos center;
    private final long seed;
    private int column, y=BOTTOM;
    private int prepared=-1,x,z,h,ceiling;
    private BlockState surface;
    private final BlockPos.Mutable pos=new BlockPos.Mutable();
    public TerrainTask(BlockPos center,long seed,int[] cursor) {
        this.center=center;
        this.seed=seed;
        if(cursor.length>=2){column=Math.max(0,cursor[0]);y=Math.max(BOTTOM,Math.min(TOP+1,cursor[1]));}
    }
    private static int count(){return (MapLayout.MAX_X-MapLayout.MIN_X+1)*(MapLayout.MAX_Z-MapLayout.MIN_Z+1);}
    public boolean tick(ServerWorld world,long deadline) {
        int operations=0;
        while(column<count()&&System.nanoTime()<deadline&&operations++<4000) {
            if(prepared!=column)prepareColumn(world);
            if(y>ceiling) { column++;y=BOTTOM;continue; }
            BlockState state;
            if(y>h) state=y<=MapLayout.SEA?Blocks.WATER.getDefaultState():Blocks.AIR.getDefaultState();
            else if(y==h) state=surface;
            else state=(y>=h-3?Blocks.DIRT:Blocks.STONE).getDefaultState();
            pos.setPos(center.getX()+x,y,center.getZ()+z);
            if(world.getBlockState(pos)!=state)world.setBlockState(pos,state,2);
            y++;
        }
        return column>=count();
    }
    private void prepareColumn(ServerWorld world){
        prepared=column;
        x=MapLayout.MIN_X+column%(MapLayout.MAX_X-MapLayout.MIN_X+1);
        z=MapLayout.MIN_Z+column/(MapLayout.MAX_X-MapLayout.MIN_X+1);
        h=MapLayout.surfaceHeight(x,z);
        ceiling=Math.min(TOP,Math.max(MapLayout.SEA,Math.max(h,world.getHeight(Heightmap.Type.WORLD_SURFACE,center.getX()+x,center.getZ()+z))));
        Block top;
        if(!MapLayout.isLand(x,z)||MapLayout.edge(x,z)>0.93)top=Blocks.SAND;
        else if(MapLayout.road(x,z))top=Blocks.GRAVEL;
        else if(MapLayout.pad(x,z))top=Blocks.GRASS_BLOCK;
        else if(h>=96)top=Blocks.SNOW_BLOCK;
        else if(z>75&&x<15)top=Blocks.SAND;
        else top=Blocks.GRASS_BLOCK;
        surface=top.getDefaultState();
    }
    static void decorate(ServerWorld world,BlockPos center,long seed,int x,int z,int h) {
        if(!MapLayout.isLand(x,z)||MapLayout.edge(x,z)>0.90||MapLayout.pad(x,z)||MapLayout.buildingPlot(x,z)||MapLayout.road(x,z)||h>=100)return;
        long hash=mix(seed ^ (x*341873128712L) ^ (z*132897987541L));
        // Spaced cells keep resource colliders separate and leave ordinary walking room.
        String id=null;
        if(Math.floorMod(x,9)==0&&Math.floorMod(z,9)==0&&Math.floorMod(hash,4)!=0)id="resource_tree";
        else if(Math.floorMod(x,7)==3&&Math.floorMod(z,7)==3&&Math.floorMod(hash,3)==0){
            int type=(int)Math.floorMod(hash>>7,4);
            id=type==0?"sulfur_node":type==1?"metal_node":"stone_node";
        }else if(Math.floorMod(hash,367)==0)id="hemp";
        if(id==null)return;
        ResourceLocation key=new ResourceLocation("rustcraft",id);
        Block block=ForgeRegistries.BLOCKS.getValue(key);
        if(block==null||block==Blocks.AIR)return;
        world.setBlockState(center.add(x,h+1-center.getY(),z),block.getDefaultState(),2);
    }
    private static long mix(long n){n=(n^(n>>>30))*0xbf58476d1ce4e5b9L;n=(n^(n>>>27))*0x94d049bb133111ebL;return n^(n>>>31);}
    public int percent(){return Math.min(99,column*100/count());}
    public int[] cursor(){return new int[]{column,y};}
}
