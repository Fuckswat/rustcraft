package rustcraft.building.network;

import net.minecraft.util.ResourceLocation;
import net.minecraftforge.fml.network.NetworkDirection;
import net.minecraftforge.fml.network.NetworkRegistry;
import net.minecraftforge.fml.network.simple.SimpleChannel;

import java.util.Optional;

/** Канал rustcraft:building — только кодовый замок. */
public final class BuildingNetwork {
    private BuildingNetwork() {}

    private static final String VERSION = "1";

    public static final SimpleChannel CHANNEL = NetworkRegistry.newSimpleChannel(
            new ResourceLocation("rustcraft", "building"), () -> VERSION, VERSION::equals, VERSION::equals);

    public static void register() {
        CHANNEL.registerMessage(0, OpenCodeLockPacket.class, OpenCodeLockPacket::encode, OpenCodeLockPacket::decode,
                OpenCodeLockPacket::handle, Optional.of(NetworkDirection.PLAY_TO_CLIENT));
        CHANNEL.registerMessage(1, SubmitCodePacket.class, SubmitCodePacket::encode, SubmitCodePacket::decode,
                SubmitCodePacket::handle, Optional.of(NetworkDirection.PLAY_TO_SERVER));
    }
}
