package rustcraft.building;

import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import net.minecraft.world.chunk.Chunk;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/** Право строительства в зоне шкафа с инструментами (Tool Cupboard). */
public final class BuildPrivilege {
    private BuildPrivilege() {}

    /** Радиус зоны шкафа (по каждой оси). */
    public static final int RADIUS = 16;

    /** Все загруженные шкафы, в зону которых попадает pos. */
    public static List<ToolCupboardTileEntity> cupboardsAround(World world, BlockPos pos) {
        List<ToolCupboardTileEntity> result = new ArrayList<ToolCupboardTileEntity>();
        int minCx = (pos.getX() - RADIUS) >> 4;
        int maxCx = (pos.getX() + RADIUS) >> 4;
        int minCz = (pos.getZ() - RADIUS) >> 4;
        int maxCz = (pos.getZ() + RADIUS) >> 4;
        for (int cx = minCx; cx <= maxCx; cx++) {
            for (int cz = minCz; cz <= maxCz; cz++) {
                Chunk chunk = world.getChunkProvider().getChunkNow(cx, cz);
                if (chunk == null) continue;
                for (TileEntity te : chunk.getTileEntityMap().values()) {
                    if (te instanceof ToolCupboardTileEntity && !te.isRemoved()) {
                        BlockPos p = te.getPos();
                        if (Math.abs(p.getX() - pos.getX()) <= RADIUS
                                && Math.abs(p.getY() - pos.getY()) <= RADIUS
                                && Math.abs(p.getZ() - pos.getZ()) <= RADIUS) {
                            result.add((ToolCupboardTileEntity) te);
                        }
                    }
                }
            }
        }
        return result;
    }

    /** Можно ли игроку строить/ломать здесь: вне зон шкафов — да; в зоне — только если авторизован во всех. */
    public static boolean hasPrivilege(PlayerEntity player, World world, BlockPos pos) {
        if (player.isCreative()) return true;
        for (ToolCupboardTileEntity tc : cupboardsAround(world, pos)) {
            if (!tc.isAuthorized(player)) return false;
        }
        return true;
    }

    /**
     * Можно ли изменять конкретную постройку (улучшать, разбирать, ставить замок):
     * в зоне шкафа — если авторизован, вне зоны — только владельцу.
     */
    public static boolean canModify(PlayerEntity player, World world, BlockPos pos, UUID owner) {
        if (player.isCreative()) return true;
        List<ToolCupboardTileEntity> tcs = cupboardsAround(world, pos);
        if (tcs.isEmpty()) {
            return owner == null || owner.equals(player.getUniqueID());
        }
        for (ToolCupboardTileEntity tc : tcs) {
            if (!tc.isAuthorized(player)) return false;
        }
        return true;
    }
}
