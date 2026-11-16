package rustcraft.building.network;

import net.minecraft.network.PacketBuffer;
import net.minecraft.util.math.BlockPos;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.fml.network.NetworkEvent;

import java.util.function.Supplier;

/** Сервер → клиент: открыть экран кодового замка (ввод или установка кода). */
public class OpenCodeLockPacket {
    public final BlockPos pos;
    public final boolean setMode;

    public OpenCodeLockPacket(BlockPos pos, boolean setMode) {
        this.pos = pos;
        this.setMode = setMode;
    }

    public static void encode(OpenCodeLockPacket msg, PacketBuffer buf) {
        buf.writeBlockPos(msg.pos);
        buf.writeBoolean(msg.setMode);
    }

    public static OpenCodeLockPacket decode(PacketBuffer buf) {
        return new OpenCodeLockPacket(buf.readBlockPos(), buf.readBoolean());
    }

    public static void handle(OpenCodeLockPacket msg, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT,
                () -> () -> rustcraft.building.client.ClientHooks.openCodeLock(msg.pos, msg.setMode)));
        ctx.get().setPacketHandled(true);
    }
}
