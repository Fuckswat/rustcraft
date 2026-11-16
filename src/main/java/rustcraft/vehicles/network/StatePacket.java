package rustcraft.vehicles.network;

import net.minecraft.entity.Entity;
import net.minecraft.entity.player.ServerPlayerEntity;
import net.minecraft.network.PacketBuffer;
import net.minecraftforge.fml.network.NetworkEvent;
import rustcraft.vehicles.entity.AbstractVehicleEntity;

import java.util.function.Supplier;

/** C2S: состояние транспорта, просимулированное клиентом пилота (каждый тик). */
public class StatePacket {
    public final int entityId;
    public final double x, y, z;
    public final float yaw, pitch, roll;
    public final double mx, my, mz;
    public final float throttle, impact;

    public StatePacket(int entityId, double x, double y, double z, float yaw, float pitch, float roll,
                       double mx, double my, double mz, float throttle, float impact) {
        this.entityId = entityId;
        this.x = x;
        this.y = y;
        this.z = z;
        this.yaw = yaw;
        this.pitch = pitch;
        this.roll = roll;
        this.mx = mx;
        this.my = my;
        this.mz = mz;
        this.throttle = throttle;
        this.impact = impact;
    }

    public static void encode(StatePacket p, PacketBuffer buf) {
        buf.writeVarInt(p.entityId);
        buf.writeDouble(p.x);
        buf.writeDouble(p.y);
        buf.writeDouble(p.z);
        buf.writeFloat(p.yaw);
        buf.writeFloat(p.pitch);
        buf.writeFloat(p.roll);
        buf.writeFloat((float) p.mx);
        buf.writeFloat((float) p.my);
        buf.writeFloat((float) p.mz);
        buf.writeFloat(p.throttle);
        buf.writeFloat(p.impact);
    }

    public static StatePacket decode(PacketBuffer buf) {
        return new StatePacket(buf.readVarInt(), buf.readDouble(), buf.readDouble(), buf.readDouble(),
                buf.readFloat(), buf.readFloat(), buf.readFloat(),
                buf.readFloat(), buf.readFloat(), buf.readFloat(), buf.readFloat(), buf.readFloat());
    }

    public static void handle(StatePacket p, Supplier<NetworkEvent.Context> ctx) {
        final ServerPlayerEntity sender = ctx.get().getSender();
        if (sender != null) {
            ctx.get().enqueueWork(() -> {
                Entity e = sender.world.getEntityByID(p.entityId);
                if (e instanceof AbstractVehicleEntity) {
                    ((AbstractVehicleEntity) e).acceptClientState(sender, p);
                }
            });
        }
        ctx.get().setPacketHandled(true);
    }
}
