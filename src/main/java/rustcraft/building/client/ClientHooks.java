package rustcraft.building.client;

import net.minecraft.client.Minecraft;
import net.minecraft.util.math.BlockPos;

/** Клиентские действия, вызываемые из пакетов (только через DistExecutor). */
public final class ClientHooks {
    private ClientHooks() {}

    public static void openCodeLock(BlockPos pos, boolean setMode) {
        Minecraft.getInstance().displayGuiScreen(new CodeLockScreen(pos, setMode));
    }
}
