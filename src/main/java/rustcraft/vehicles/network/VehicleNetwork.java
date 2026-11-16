package rustcraft.vehicles.network;

import net.minecraft.util.ResourceLocation;
import net.minecraftforge.fml.network.NetworkRegistry;
import net.minecraftforge.fml.network.NetworkDirection;
import java.util.Optional;
import net.minecraftforge.fml.network.simple.SimpleChannel;

/** Канал rustcraft:vehicles — ввод пилота / состояние транспорта. */
public final class VehicleNetwork {
    private VehicleNetwork() {}

    private static final String VERSION = "1";
    public static final SimpleChannel CHANNEL = NetworkRegistry.newSimpleChannel(
            new ResourceLocation("rustcraft", "vehicles"), () -> VERSION, VERSION::equals, VERSION::equals);

    private static boolean registered;

    public static void register() {
        if (registered) return;
        registered = true;
        int id = 0;
        CHANNEL.registerMessage(id++, StatePacket.class, StatePacket::encode, StatePacket::decode, StatePacket::handle, Optional.of(NetworkDirection.PLAY_TO_SERVER));
        CHANNEL.registerMessage(id++, ActionPacket.class, ActionPacket::encode, ActionPacket::decode, ActionPacket::handle, Optional.of(NetworkDirection.PLAY_TO_SERVER));
        CHANNEL.registerMessage(id++, CorrectionPacket.class, CorrectionPacket::encode, CorrectionPacket::decode, CorrectionPacket::handle, Optional.of(NetworkDirection.PLAY_TO_CLIENT));
    }
}
