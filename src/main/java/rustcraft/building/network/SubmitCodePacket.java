package rustcraft.building.network;

import net.minecraft.entity.player.ServerPlayerEntity;
import net.minecraft.network.PacketBuffer;
import net.minecraft.util.math.BlockPos;
import net.minecraftforge.fml.network.NetworkEvent;
import rustcraft.building.DoorLogic;

import java.util.function.Supplier;

/** Клиент → сервер: введённый (или новый) 4-значный код. Сервер всё перепроверяет. */
public class SubmitCodePacket {
    public final BlockPos pos;
    public final String code;
    public final boolean setMode;

    public SubmitCodePacket(BlockPos pos, String code, boolean setMode) {
        this.pos = pos;
        this.code = code;
        this.setMode = setMode;
    }

    public static void encode(SubmitCodePacket msg, PacketBuffer buf) {
        buf.writeBlockPos(msg.pos);
        buf.writeString(msg.code, 8);
        buf.writeBoolean(msg.setMode);
    }

    public static SubmitCodePacket decode(PacketBuffer buf) {
        return new SubmitCodePacket(buf.readBlockPos(), buf.readString(8), buf.readBoolean());
    }

    public static void handle(SubmitCodePacket msg, Supplier<NetworkEvent.Context> ctx) {
        ServerPlayerEntity player = ctx.get().getSender();
        if (player != null) {
            ctx.get().enqueueWork(() -> DoorLogic.handleCode(player, msg.pos, msg.code, msg.setMode));
        }
        ctx.get().setPacketHandled(true);
    }
}
