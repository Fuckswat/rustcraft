package rustcraft.world.map;
import net.minecraft.block.BlockState;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.server.ServerWorld;
import java.util.*;
import java.util.function.Consumer;
import net.minecraft.tileentity.TileEntity;
/** Bounded server-thread placement; attachment blocks follow their supports. */
public final class BlueprintTask implements BuildStep {
    private final Blueprint blueprint;
    private final long[] positions;
    private final BlockState[] states;
    private int index, pillarIndex, postIndex, pillarY = -1;
    public BlueprintTask(Blueprint bp) { this(bp,new int[0]); }
    public BlueprintTask(Blueprint bp,int[] cursor) {
        blueprint=bp; List<Map.Entry<Long,BlockState>> ordered=new ArrayList<>(bp.blocks.entrySet());
        ordered.sort(Comparator.<Map.Entry<Long,BlockState>>comparingInt(e -> Blueprint.isAttachable(e.getValue()) ? 1 : 0)
            .thenComparingInt(e -> (int)(e.getKey()<<52>>52)).thenComparingLong(Map.Entry::getKey));
        positions=new long[ordered.size()];states=new BlockState[ordered.size()];
        for(int i=0;i<ordered.size();i++){Map.Entry<Long,BlockState> e=ordered.get(i);positions[i]=e.getKey();states[i]=e.getValue();}
        // Drop both the hash nodes and their large backing table. Keep only compact placement arrays.
        bp.blocks=new HashMap<>();ordered.clear();
        if(cursor.length>=5){index=Math.max(0,Math.min(states.length,cursor[0]));pillarIndex=Math.max(0,Math.min(bp.pillars.size(),cursor[1]));postIndex=Math.max(0,Math.min(bp.post.size(),cursor[2]));pillarY=cursor[3];}
        Arrays.fill(states,0,index,null);
        for(int i=0;i<pillarIndex;i++)bp.pillars.set(i,null);
        for(int i=0;i<postIndex;i++)bp.post.set(i,null);
    }
    @SuppressWarnings("unchecked")
    public boolean tick(ServerWorld world, long deadline) {
        int operations=0;
        while(System.nanoTime()<deadline && operations++<1500) {
            if (pillarIndex<blueprint.pillars.size()) {
                long[] a=blueprint.pillars.get(pillarIndex); BlockPos top=BlockPos.fromLong(a[0]);
                if(pillarY<0) pillarY=top.getY();
                BlockPos pos=new BlockPos(top.getX(),pillarY,top.getZ());
                BlockState old=world.getBlockState(pos);
                if(pillarY<1 || (!old.isAir() && old.getMaterial().isSolid() && old.getFluidState().isEmpty())) {
                    blueprint.pillars.set(pillarIndex,null);pillarIndex++;pillarY=-1;continue;
                }
                world.setBlockState(pos,blueprint.pillarStates.get((int)a[1]),2);pillarY--; continue;
            }
            if(index<states.length) {
                BlockPos pos=BlockPos.fromLong(positions[index]);BlockState state=states[index];
                world.setBlockState(pos,state,2);states[index]=null;positions[index]=0;index++;continue;
            }
            if(postIndex<blueprint.post.size()) {
                Object[] e=blueprint.post.get(postIndex);blueprint.post.set(postIndex++,null);TileEntity tile=world.getTileEntity((BlockPos)e[0]);
                if(tile!=null)((Consumer<TileEntity>)e[1]).accept(tile);continue;
            }
            return true;
        }
        return false;
    }
    public int percent(){return states.length==0?100:Math.min(99,index*100/states.length);}
    public int[] cursor(){return new int[]{index,pillarIndex,postIndex,pillarY,1};}
}
