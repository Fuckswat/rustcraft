package rustcraft.bots.net;

import java.util.Optional;

import net.minecraft.entity.Entity;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.fml.network.NetworkDirection;
import net.minecraftforge.fml.network.NetworkRegistry;
import net.minecraftforge.fml.network.PacketDistributor;
import net.minecraftforge.fml.network.simple.SimpleChannel;
import rustcraft.RustCraft;

/** Отдельный канал модуля bots (rustcraft:bots), чтобы не пересекаться с другими модулями. */
public final class BotNetwork {
    private BotNetwork() {}

    private static final String PROTOCOL = "1";
    private static SimpleChannel channel;

    public static synchronized void register() {
        if (channel != null) {
            return;
        }
        channel = NetworkRegistry.newSimpleChannel(new ResourceLocation(RustCraft.MODID, "bots"),
                () -> PROTOCOL, PROTOCOL::equals, PROTOCOL::equals);
        channel.registerMessage(0, BotShotPacket.class, BotShotPacket::encode, BotShotPacket::decode,
                BotShotPacket::handle, Optional.of(NetworkDirection.PLAY_TO_CLIENT));
    }

    public static void sendShot(Entity shooter, BotShotPacket msg) {
        if (channel != null) {
            channel.send(PacketDistributor.TRACKING_ENTITY.with(() -> shooter), msg);
        }
    }
}
