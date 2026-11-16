package rustcraft.guns;

import java.util.Optional;
import java.util.function.Supplier;

import net.minecraft.entity.player.ServerPlayerEntity;
import net.minecraft.network.PacketBuffer;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.fml.network.NetworkDirection;
import net.minecraftforge.fml.network.NetworkEvent;
import net.minecraftforge.fml.network.NetworkRegistry;
import net.minecraftforge.fml.network.simple.SimpleChannel;

/** Канал rustcraft:guns. C2S: выстрел, перезарядка. S2C: эффекты выстрела (трассеры, попадания). */
public final class GunNetwork {
    private GunNetwork() {}

    private static final String PROTOCOL = "1";
    public static final SimpleChannel CHANNEL = NetworkRegistry.newSimpleChannel(
            new ResourceLocation("rustcraft", "guns"), () -> PROTOCOL, PROTOCOL::equals, PROTOCOL::equals);

    public static void register() {
        int id = 0;
        CHANNEL.registerMessage(id++, FireMsg.class, FireMsg::encode, FireMsg::decode, FireMsg::handle,
                Optional.of(NetworkDirection.PLAY_TO_SERVER));
        CHANNEL.registerMessage(id++, ReloadMsg.class, ReloadMsg::encode, ReloadMsg::decode, ReloadMsg::handle,
                Optional.of(NetworkDirection.PLAY_TO_SERVER));
        CHANNEL.registerMessage(id++, ShotFxMsg.class, ShotFxMsg::encode, ShotFxMsg::decode, ShotFxMsg::handle,
                Optional.of(NetworkDirection.PLAY_TO_CLIENT));
    }

    /** Клиент хочет выстрелить. Передаёт свой взгляд (сервер сверяет с серверным) и флаг прицеливания. */
    public static class FireMsg {
        public final float yaw, pitch;
        public final boolean aiming;

        public FireMsg(float yaw, float pitch, boolean aiming) {
            this.yaw = yaw; this.pitch = pitch; this.aiming = aiming;
        }

        static void encode(FireMsg m, PacketBuffer buf) {
            buf.writeFloat(m.yaw);
            buf.writeFloat(m.pitch);
            buf.writeBoolean(m.aiming);
        }

        static FireMsg decode(PacketBuffer buf) {
            return new FireMsg(buf.readFloat(), buf.readFloat(), buf.readBoolean());
        }

        static void handle(FireMsg m, Supplier<NetworkEvent.Context> ctx) {
            final ServerPlayerEntity player = ctx.get().getSender();
            ctx.get().enqueueWork(() -> {
                if (player != null) GunServer.handleFire(player, m.yaw, m.pitch, m.aiming);
            });
            ctx.get().setPacketHandled(true);
        }
    }

    public static class ReloadMsg {
        static void encode(ReloadMsg m, PacketBuffer buf) {}

        static ReloadMsg decode(PacketBuffer buf) {
            return new ReloadMsg();
        }

        static void handle(ReloadMsg m, Supplier<NetworkEvent.Context> ctx) {
            final ServerPlayerEntity player = ctx.get().getSender();
            ctx.get().enqueueWork(() -> {
                if (player != null) GunServer.handleReload(player);
            });
            ctx.get().setPacketHandled(true);
        }
    }

    /** Эффекты выстрела для клиентов рядом: точки попадания каждой пули/дробины + тип попадания. */
    public static class ShotFxMsg {
        public static final byte HIT_NONE = 0, HIT_BLOCK = 1, HIT_ENTITY = 2, HIT_HEAD = 3;

        public final int shooterId;
        public final String gunId;
        public final double sx, sy, sz;
        public final double[] ends; // x,y,z тройками
        public final byte[] types;

        public ShotFxMsg(int shooterId, String gunId, double sx, double sy, double sz, double[] ends, byte[] types) {
            this.shooterId = shooterId; this.gunId = gunId; this.sx = sx; this.sy = sy; this.sz = sz;
            this.ends = ends; this.types = types;
        }

        static void encode(ShotFxMsg m, PacketBuffer buf) {
            buf.writeVarInt(m.shooterId);
            buf.writeString(m.gunId, 32);
            buf.writeDouble(m.sx); buf.writeDouble(m.sy); buf.writeDouble(m.sz);
            buf.writeVarInt(m.types.length);
            for (int i = 0; i < m.types.length; i++) {
                buf.writeFloat((float) (m.ends[i * 3] - m.sx));
                buf.writeFloat((float) (m.ends[i * 3 + 1] - m.sy));
                buf.writeFloat((float) (m.ends[i * 3 + 2] - m.sz));
                buf.writeByte(m.types[i]);
            }
        }

        static ShotFxMsg decode(PacketBuffer buf) {
            int shooter = buf.readVarInt();
            String gun = buf.readString(32);
            double sx = buf.readDouble(), sy = buf.readDouble(), sz = buf.readDouble();
            int n = Math.min(buf.readVarInt(), 64);
            double[] ends = new double[n * 3];
            byte[] types = new byte[n];
            for (int i = 0; i < n; i++) {
                ends[i * 3] = sx + buf.readFloat();
                ends[i * 3 + 1] = sy + buf.readFloat();
                ends[i * 3 + 2] = sz + buf.readFloat();
                types[i] = buf.readByte();
            }
            return new ShotFxMsg(shooter, gun, sx, sy, sz, ends, types);
        }

        static void handle(ShotFxMsg m, Supplier<NetworkEvent.Context> ctx) {
            ctx.get().enqueueWork(() ->
                    DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> rustcraft.guns.client.GunClientFx.onShot(m)));
            ctx.get().setPacketHandled(true);
        }
    }
}
