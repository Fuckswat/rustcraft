package rustcraft.world.map;

import net.minecraft.util.math.BlockPos;
import net.minecraft.world.server.ServerWorld;

/** Runs after all terrain clearing, preserving generated resource-tree canopies across columns. */
final class ResourceTask implements BuildStep {
    private final BlockPos center;
    private final long seed;
    private int column;
    ResourceTask(BlockPos center,long seed,int[] cursor){this.center=center;this.seed=seed;if(cursor.length>0)column=Math.max(0,cursor[0]);}
    private static int count(){return (MapLayout.MAX_X-MapLayout.MIN_X+1)*(MapLayout.MAX_Z-MapLayout.MIN_Z+1);}
    public boolean tick(ServerWorld world,long deadline){
        int operations=0;
        while(column<count()&&System.nanoTime()<deadline&&operations++<512){
            int x=MapLayout.MIN_X+column%(MapLayout.MAX_X-MapLayout.MIN_X+1);
            int z=MapLayout.MIN_Z+column/(MapLayout.MAX_X-MapLayout.MIN_X+1);
            TerrainTask.decorate(world,center,seed,x,z,MapLayout.surfaceHeight(x,z));column++;
        }
        return column>=count();
    }
    public int percent(){return Math.min(99,column*100/count());}
    public int[] cursor(){return new int[]{column};}
}
