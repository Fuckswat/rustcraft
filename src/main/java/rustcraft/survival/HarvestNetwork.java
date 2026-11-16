package rustcraft.survival;

import java.util.Optional;
import java.util.function.Supplier;
import net.minecraft.entity.player.ServerPlayerEntity;
import net.minecraft.network.PacketBuffer;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.BlockRayTraceResult;
import net.minecraft.util.math.RayTraceContext;
import net.minecraft.util.math.RayTraceResult;
import net.minecraft.util.math.vector.Vector3d;
import net.minecraftforge.fml.network.NetworkDirection;
import net.minecraftforge.fml.network.NetworkEvent;
import net.minecraftforge.fml.network.NetworkRegistry;
import net.minecraftforge.fml.network.simple.SimpleChannel;

/** Пакет сообщает только намерение ударить. Сервер сам выбирает цель лучом из глаз игрока. */
public final class HarvestNetwork {
    private HarvestNetwork() {}
    public static SimpleChannel CHANNEL;
    public static void init() {
        CHANNEL = NetworkRegistry.newSimpleChannel(new ResourceLocation("rustcraft", "harvest"), () -> "1", "1"::equals, "1"::equals);
        CHANNEL.registerMessage(0, Hit.class, Hit::encode, Hit::decode, Hit::handle, Optional.of(NetworkDirection.PLAY_TO_SERVER));
    }
    public static class Hit {
        public static void encode(Hit m, PacketBuffer b) {}
        public static Hit decode(PacketBuffer b) { return new Hit(); }
        public static void handle(Hit m, Supplier<NetworkEvent.Context> c) {
            NetworkEvent.Context ctx = c.get();
            ctx.enqueueWork(() -> strike(ctx.getSender())); ctx.setPacketHandled(true);
        }
    }
    static void strike(ServerPlayerEntity p) {
        if (p == null || !p.isAlive() || p.isCreative() || p.isSpectator() || p.isHandActive()) return;
        long now = p.world.getGameTime();
        long next = p.getPersistentData().getLong("RustCraftNextHarvest");
        if (now < next && next - now < 200) return;
        int interval = p.getHeldItemMainhand().getItem() instanceof HarvestToolItem ? ((HarvestToolItem)p.getHeldItemMainhand().getItem()).interval : 12;
        p.getPersistentData().putLong("RustCraftNextHarvest", now + interval);
        Vector3d eye = p.getEyePosition(1.0F);
        BlockRayTraceResult hit = p.world.rayTraceBlocks(new RayTraceContext(eye, eye.add(p.getLook(1.0F).scale(4.5D)),
                RayTraceContext.BlockMode.OUTLINE, RayTraceContext.FluidMode.NONE, p));
        if (hit.getType() != RayTraceResult.Type.BLOCK) return;
        BlockPos root = ResourceNodes.root(p.world, hit.getPos());
        if (root == null || !p.world.isBlockLoaded(root)) return;
        if (!p.world.isBlockModifiable(p, root)) return;
        TileEntity tile = p.world.getTileEntity(root);
        if (tile instanceof ResourceNodeTile) ((ResourceNodeTile)tile).hit(p);
    }
}
