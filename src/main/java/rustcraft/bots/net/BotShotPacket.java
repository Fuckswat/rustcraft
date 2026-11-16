package rustcraft.bots.net;

import java.util.function.Supplier;

import net.minecraft.network.PacketBuffer;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.fml.network.NetworkEvent;

/** Сервер → клиент: выстрел бота (вспышка, трассер, попадание). Звук идёт отдельно, с сервера. */
public class BotShotPacket {
    public static final byte HIT_NONE = 0;
    public static final byte HIT_BLOCK = 1;
    public static final byte HIT_ENTITY = 2;

    public final int shooterId;
    public final double endX;
    public final double endY;
    public final double endZ;
    public final byte hitType;
    public final boolean tracer;

    public BotShotPacket(int shooterId, double endX, double endY, double endZ, byte hitType, boolean tracer) {
        this.shooterId = shooterId;
        this.endX = endX;
        this.endY = endY;
        this.endZ = endZ;
        this.hitType = hitType;
        this.tracer = tracer;
    }

    public static void encode(BotShotPacket msg, PacketBuffer buf) {
        buf.writeVarInt(msg.shooterId);
        buf.writeDouble(msg.endX);
        buf.writeDouble(msg.endY);
        buf.writeDouble(msg.endZ);
        buf.writeByte(msg.hitType);
        buf.writeBoolean(msg.tracer);
    }

    public static BotShotPacket decode(PacketBuffer buf) {
        return new BotShotPacket(buf.readVarInt(), buf.readDouble(), buf.readDouble(), buf.readDouble(),
                buf.readByte(), buf.readBoolean());
    }

    public static void handle(final BotShotPacket msg, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() ->
                DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> rustcraft.bots.client.BotShotFx.onShot(msg)));
        ctx.get().setPacketHandled(true);
    }
}
