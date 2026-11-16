package rustcraft.vehicles.network;

import net.minecraft.entity.Entity;
import net.minecraft.entity.player.ServerPlayerEntity;
import net.minecraft.network.PacketBuffer;
import net.minecraftforge.fml.network.NetworkEvent;
import rustcraft.vehicles.entity.AbstractVehicleEntity;

import java.util.function.Supplier;

/** C2S: пилот нажал кнопку двигателя (вкл/выкл или автозапуск). */
public class ActionPacket {
    public final int entityId;
    public final int action;

    public ActionPacket(int entityId, int action) {
        this.entityId = entityId;
        this.action = action;
    }

    public static void encode(ActionPacket p, PacketBuffer buf) {
        buf.writeVarInt(p.entityId);
        buf.writeByte(p.action);
    }

    public static ActionPacket decode(PacketBuffer buf) {
        return new ActionPacket(buf.readVarInt(), buf.readByte());
    }

    public static void handle(ActionPacket p, Supplier<NetworkEvent.Context> ctx) {
        final ServerPlayerEntity sender = ctx.get().getSender();
        if (sender != null) {
            ctx.get().enqueueWork(() -> {
                Entity e = sender.world.getEntityByID(p.entityId);
                if (e instanceof AbstractVehicleEntity) {
                    ((AbstractVehicleEntity) e).handleAction(sender, p.action);
                }
            });
        }
        ctx.get().setPacketHandled(true);
    }
}
