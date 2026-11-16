package rustcraft.progression;

import java.util.*;
import net.minecraft.entity.player.*;
import net.minecraft.inventory.container.*;
import net.minecraft.item.*;
import net.minecraft.util.math.BlockPos;

/** The menu itself is the session token. No action trusts client costs, unlocks or bench tier. */
public class ProgressionMenu extends Container {
    public final int mode;
    public final BlockPos station;
    private long lastActionTick=-1;
    public ProgressionMenu(int id,PlayerInventory inv,int mode,BlockPos pos) {
        super(ProgressionModule.MENU.get(),id);this.mode=mode;station=pos.toImmutable();
        for(int r=0;r<3;r++)for(int c=0;c<9;c++)addSlot(new Slot(inv,c+r*9+9,78+c*18,158+r*18));
        for(int c=0;c<9;c++)addSlot(new Slot(inv,c,78+c*18,216));
    }
    @Override public boolean canInteractWith(PlayerEntity p) {
        if(!p.isAlive())return false;
        if(mode==0||mode==5)return true;
        if(p.getDistanceSq(station.getX()+.5,station.getY()+.5,station.getZ()+.5)>36)return false;
        net.minecraft.block.Block b=p.world.getBlockState(station).getBlock();return b instanceof ProgressionModule.Station&&((ProgressionModule.Station)b).mode==mode;
    }
    @Override public ItemStack transferStackInSlot(PlayerEntity p,int i) {if(i<0||i>=inventorySlots.size())return ItemStack.EMPTY;Slot s=inventorySlots.get(i);if(!s.getHasStack())return ItemStack.EMPTY;ItemStack stack=s.getStack(),old=stack.copy();if(!mergeItemStack(stack,i<27?27:0,i<27?36:27,false))return ItemStack.EMPTY;if(stack.isEmpty())s.putStack(ItemStack.EMPTY);else s.onSlotChanged();s.onTake(p,stack);return old;}
    public void action(ServerPlayerEntity p,String id,boolean research) {
        if(!validAction(p))return;
        Catalog.Recipe r=Catalog.BY_ID.get(id);if(r==null||r.output().isEmpty())return;
        ProgressionData data=ProgressionData.get(p);ProgressionData.Record rec=data.record(p.getUniqueID());
        boolean creative=p.abilities.isCreativeMode;
        if(research) {
            if(mode!=4||r.research<=0){reply(p,"research_table_required");return;}
            if(rec.learned.contains(id)){reply(p,"already_learned");return;}
            Map<String,Integer> costs=new LinkedHashMap<>();costs.put("rustcraft:scrap",scaled(r.research,ProgressionModule.RESEARCH_COST.get()));costs.put(id,1);
            if(!creative&&!transaction(p,costs,Collections.emptyList()))return;
            rec.learned.add(id);data.markDirty();reply(p,"learned");
        } else {
            if(mode==4||mode==5)return;
            if(!creative&&ProgressionModule.nearbyTier(p)<r.tier){reply(p,"bench_required");return;}
            if(!creative&&r.research>0&&!rec.learned.contains(id)){reply(p,"blueprint_required");return;}
            Map<String,Integer> costs=new LinkedHashMap<>();r.costs.forEach((key,n)->costs.put(key,scaled(n,ProgressionModule.COST.get())));
            if(!transaction(p,creative?Collections.emptyMap():costs,Collections.singletonList(r.output())))return;
            reply(p,"crafted");
        }
        detectAndSendChanges();
    }
    public void claimKit(ServerPlayerEntity p,String id) {
        if(mode!=5||!validAction(p))return;List<ItemStack> items=Catalog.kit(id);if(items.isEmpty())return;
        ProgressionData data=ProgressionData.get(p);ProgressionData.Record rec=data.record(p.getUniqueID());long now=System.currentTimeMillis();
        if(!p.abilities.isCreativeMode&&now<rec.kitReady){reply(p,"cooldown");return;}
        if(!transaction(p,Collections.emptyMap(),items))return;
        if(!p.abilities.isCreativeMode){rec.kitReady=now+ProgressionModule.KIT_MINUTES.get()*60000L;data.markDirty();}
        reply(p,"kit_received");detectAndSendChanges();
    }
    private boolean validAction(ServerPlayerEntity p) {if(p.openContainer!=this||!canInteractWith(p)||p.isSpectator())return false;long tick=p.world.getGameTime();if(tick==lastActionTick)return false;lastActionTick=tick;return true;}
    private void reply(ServerPlayerEntity p,String key) {ProgressionNetwork.sync(p,"message.rustcraft.progression."+key);}
    public static int scaled(int n,double multiplier) {return Math.max(1,(int)Math.ceil(n*multiplier));}
    /** Copy, remove, insert, commit. A full inventory or missing item leaves every original slot intact. */
    private boolean transaction(ServerPlayerEntity p,Map<String,Integer> costs,List<ItemStack> output) {
        List<ItemStack> copy=new ArrayList<>();for(ItemStack s:p.inventory.mainInventory)copy.add(s.copy());
        for(Map.Entry<String,Integer> c:costs.entrySet()) {Item item=Catalog.item(c.getKey());int left=c.getValue();for(ItemStack s:copy)if(s.getItem()==item){int used=Math.min(left,s.getCount());s.shrink(used);left-=used;if(left==0)break;}if(left>0){reply(p,"materials_missing");return false;}}
        for(ItemStack raw:output) {ItemStack in=raw.copy();if(in.isEmpty()){reply(p,"materials_missing");return false;}
            for(ItemStack s:copy)if(!s.isEmpty()&&ItemStack.areItemsEqual(s,in)&&ItemStack.areItemStackTagsEqual(s,in)){int n=Math.min(in.getCount(),Math.max(0,Math.min(64,s.getMaxStackSize())-s.getCount()));s.grow(n);in.shrink(n);}
            for(int i=0;i<copy.size()&&!in.isEmpty();i++)if(copy.get(i).isEmpty()){int n=Math.min(in.getCount(),Math.min(64,in.getMaxStackSize()));ItemStack s=in.copy();s.setCount(n);copy.set(i,s);in.shrink(n);}
            if(!in.isEmpty()){reply(p,"inventory_full");return false;}
        }
        for(int i=0;i<copy.size();i++)p.inventory.mainInventory.set(i,copy.get(i));p.inventory.markDirty();return true;
    }
}

