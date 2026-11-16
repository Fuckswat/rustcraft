package rustcraft.world.map;

import net.minecraft.world.server.ServerWorld;

/** A bounded, resumable server-thread build operation. */
interface BuildStep {
    boolean tick(ServerWorld world, long deadline);
    int percent();
    int[] cursor();
}
