package rustcraft.vehicles.client;

import net.minecraft.client.Minecraft;
import net.minecraft.entity.Entity;
import rustcraft.vehicles.entity.AbstractVehicleEntity;
import rustcraft.vehicles.network.CorrectionPacket;

/** Клиентская обработка S2C-пакетов (отдельный класс, чтобы не грузиться на сервере). */
public final class ClientPacketHandler {
    private ClientPacketHandler() {}

    public static void handleCorrection(CorrectionPacket p) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.world == null) return;
        Entity e = mc.world.getEntityByID(p.entityId);
        if (e instanceof AbstractVehicleEntity) {
            ((AbstractVehicleEntity) e).forceClientPosition(p.x, p.y, p.z, p.yaw);
        }
    }
}
