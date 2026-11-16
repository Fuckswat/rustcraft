package rustcraft.vehicles.network;

import net.minecraft.network.PacketBuffer;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.fml.network.NetworkEvent;

import java.util.function.Supplier;

/** S2C: сервер отклонил движение — вернуть транспорт пилота в серверную позицию. */
public class CorrectionPacket {
    public final int entityId;
    public final double x, y, z;
    public final float yaw;

    public CorrectionPacket(int entityId, double x, double y, double z, float yaw) {
        this.entityId = entityId;
        this.x = x;
        this.y = y;
        this.z = z;
        this.yaw = yaw;
    }

    public static void encode(CorrectionPacket p, PacketBuffer buf) {
        buf.writeVarInt(p.entityId);
        buf.writeDouble(p.x);
        buf.writeDouble(p.y);
        buf.writeDouble(p.z);
        buf.writeFloat(p.yaw);
    }

    public static CorrectionPacket decode(PacketBuffer buf) {
        return new CorrectionPacket(buf.readVarInt(), buf.readDouble(), buf.readDouble(), buf.readDouble(), buf.readFloat());
    }

    public static void handle(CorrectionPacket p, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() ->
                DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> rustcraft.vehicles.client.ClientPacketHandler.handleCorrection(p)));
        ctx.get().setPacketHandled(true);
    }
}
