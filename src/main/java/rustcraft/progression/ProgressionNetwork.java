package rustcraft.progression;

import java.util.*;
import java.util.function.Supplier;
import net.minecraft.entity.player.ServerPlayerEntity;
import net.minecraft.network.PacketBuffer;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.fml.network.*;
import net.minecraftforge.fml.network.simple.SimpleChannel;

public final class ProgressionNetwork {
    private ProgressionNetwork() {}
    public static final SimpleChannel CHANNEL=NetworkRegistry.newSimpleChannel(new ResourceLocation("rustcraft","progression"),()->"1","1"::equals,"1"::equals);
    public static void register() {CHANNEL.registerMessage(0,Action.class,Action::encode,Action::decode,Action::handle,Optional.of(NetworkDirection.PLAY_TO_SERVER));CHANNEL.registerMessage(1,State.class,State::encode,State::decode,State::handle,Optional.of(NetworkDirection.PLAY_TO_CLIENT));CHANNEL.registerMessage(2,Open.class,(m,b)->{},b->new Open(),Open::handle,Optional.of(NetworkDirection.PLAY_TO_SERVER));}
    public static void sync(ServerPlayerEntity p,String message) {ProgressionData.Record r=ProgressionData.get(p).record(p.getUniqueID());CHANNEL.send(PacketDistributor.PLAYER.with(()->p),new State(r.learned,Math.max(0,r.kitReady-System.currentTimeMillis()),ProgressionModule.COST.get(),ProgressionModule.RESEARCH_COST.get(),message));}
    public static final class Action {
        public final int window,kind;public final String id;
        public Action(int window,int kind,String id) {this.window=window;this.kind=kind;this.id=id;}
        static void encode(Action m,PacketBuffer b) {b.writeVarInt(m.window);b.writeVarInt(m.kind);b.writeString(m.id,96);}
        static Action decode(PacketBuffer b) {return new Action(b.readVarInt(),b.readVarInt(),b.readString(96));}
        static void handle(Action m,Supplier<NetworkEvent.Context> c) {ServerPlayerEntity p=c.get().getSender();c.get().enqueueWork(()->{if(p!=null&&p.openContainer instanceof ProgressionMenu&&p.openContainer.windowId==m.window) {ProgressionMenu menu=(ProgressionMenu)p.openContainer;if(m.kind==2)menu.claimKit(p,m.id);else if(m.kind==0||m.kind==1)menu.action(p,m.id,m.kind==1);}});c.get().setPacketHandled(true);}
    }
    public static final class Open {
        static void handle(Open m,Supplier<NetworkEvent.Context> c) {ServerPlayerEntity p=c.get().getSender();c.get().enqueueWork(()->{if(p!=null&&p.isAlive()&&!p.isSpectator()&&p.openContainer==p.container)ProgressionModule.open(p,0,p.getPosition());});c.get().setPacketHandled(true);}
    }
    public static final class State {
        public final Set<String> learned;public final long cooldown;public final double craftCost,researchCost;public final String message;
        State(Set<String> learned,long cooldown,double craftCost,double researchCost,String message) {this.learned=new HashSet<>(learned);this.cooldown=cooldown;this.craftCost=craftCost;this.researchCost=researchCost;this.message=message;}
        static void encode(State m,PacketBuffer b) {b.writeVarInt(m.learned.size());for(String id:m.learned)b.writeString(id,96);b.writeLong(m.cooldown);b.writeDouble(m.craftCost);b.writeDouble(m.researchCost);b.writeString(m.message,128);}
        static State decode(PacketBuffer b) {int n=b.readVarInt();if(n<0||n>1024)throw new IllegalArgumentException("Invalid blueprint count");Set<String> ids=new HashSet<>();for(int i=0;i<n;i++)ids.add(b.readString(96));return new State(ids,b.readLong(),b.readDouble(),b.readDouble(),b.readString(128));}
        static void handle(State m,Supplier<NetworkEvent.Context> c) {c.get().enqueueWork(()->DistExecutor.unsafeRunWhenOn(Dist.CLIENT,()->()->rustcraft.progression.client.ProgressionClient.accept(m)));c.get().setPacketHandled(true);}
    }
}
