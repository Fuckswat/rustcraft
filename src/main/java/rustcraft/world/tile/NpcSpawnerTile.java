package rustcraft.world.tile;

import net.minecraft.block.BlockState;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.MobEntity;
import net.minecraft.entity.player.ServerPlayerEntity;
import net.minecraft.nbt.CompoundNBT;
import net.minecraft.tileentity.ITickableTileEntity;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.math.AxisAlignedBB;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.server.ServerWorld;
import rustcraft.world.NpcUtil;
import rustcraft.world.WorldConfig;
import rustcraft.world.WorldRegistry;

import java.util.List;

/**
 * Поддерживает Count живых NPC типа Entity в радиусе Radius, пока рядом (Range) есть игрок.
 * Убитые восполняются через Delay тиков (по умолчанию из конфига). Тип отсутствует -> ничего не делает
 * (или берётся fallbackNpc из конфига).
 */
public class NpcSpawnerTile extends TileEntity implements ITickableTileEntity {
    public static final String TAG = "rustcraftSpawner";

    private String entityId = "rustcraft:scientist";
    private int count = 2;
    private int radius = 8;
    private int delay = -1;
    private int range = 48;
    private int cooldown;
    private boolean primed;
    private boolean waiting;

    public NpcSpawnerTile() {
        super(WorldRegistry.NPC_SPAWNER_TILE.get());
    }

    public void configure(String entityId, int count, int radius) {
        this.entityId = entityId;
        this.count = count;
        this.radius = radius;
        markDirty();
    }

    public String describe() {
        return "Спавнер: " + entityId + ", количество: " + count + ", радиус: " + radius + ", восстановление: " + (effectiveDelay()/20) + " с, активация: " + range
                + (NpcUtil.lookup(entityId) == null ? " (используется запасной тип: " + WorldConfig.FALLBACK_NPC.get() + ")" : "");
    }

    private int effectiveDelay() {
        long base=delay>=0 ? delay : WorldConfig.NPC_RESPAWN_SECONDS.get()*20L;
        return (int)Math.min(Integer.MAX_VALUE,base*WorldConfig.NPC_RESPAWN_MULTIPLIER.get());
    }

    @Override
    public void tick() {
        if (world == null || world.isRemote || (world.getGameTime() + pos.toLong()) % 20 != 0) return;
        ServerWorld sw = (ServerWorld) world;
        if (!playerNearby(sw)) return;
        if (cooldown > 0) {
            cooldown -= 20;
            return;
        }
        EntityType<?> type = NpcUtil.resolve(entityId);
        if (type == null) return;
        int alive = countAlive(sw);
        if (alive >= count) {
            primed = true;
            waiting = false;
            return;
        }
        int toSpawn;
        if (!primed) {
            // Первый визит — сразу весь отряд.
            toSpawn = count - alive;
        } else if (!waiting) {
            // Кого-то убили — ждём задержку респавна.
            waiting = true;
            cooldown = effectiveDelay();
            markDirty();
            return;
        } else {
            toSpawn = 1;
            waiting = false;
        }
        final long myPos = pos.toLong();
        for (int i = 0; i < toSpawn; i++) {
            BlockPos p = NpcUtil.findSpot(sw, pos.up(), 0, radius, 2, 3, sw.rand);
            if (p == null) continue;
            NpcUtil.spawnAt(sw, type, p, e -> {
                e.getPersistentData().putLong(TAG, myPos);
                if (e instanceof MobEntity) ((MobEntity) e).setHomePosAndDistance(pos, Math.max(8, radius + 4));
            });
        }
        primed = true;
        markDirty();
    }

    private boolean playerNearby(ServerWorld sw) {
        double r2 = (double) range * range;
        for (ServerPlayerEntity p : sw.getPlayers()) {
            if (!p.isSpectator() && p.getDistanceSq(pos.getX() + 0.5D, pos.getY() + 0.5D, pos.getZ() + 0.5D) <= r2) return true;
        }
        return false;
    }

    private int countAlive(ServerWorld sw) {
        final long myPos = pos.toLong();
        AxisAlignedBB box = new AxisAlignedBB(pos).grow(64.0D);
        List<Entity> list = sw.getEntitiesWithinAABB(Entity.class, box,
                e -> e.isAlive() && e.getPersistentData().contains(TAG) && e.getPersistentData().getLong(TAG) == myPos);
        return list.size();
    }

    @Override
    public void read(BlockState state, CompoundNBT nbt) {
        super.read(state, nbt);
        if (nbt.contains("Entity")) entityId = nbt.getString("Entity");
        if (nbt.contains("Count")) count = nbt.getInt("Count");
        if (nbt.contains("Radius")) radius = nbt.getInt("Radius");
        if (nbt.contains("Delay")) delay = nbt.getInt("Delay");
        if (nbt.contains("Range")) range = nbt.getInt("Range");
        cooldown = nbt.getInt("Cooldown");
        primed = nbt.getBoolean("Primed");
        waiting = nbt.getBoolean("Waiting");
    }

    @Override
    public CompoundNBT write(CompoundNBT nbt) {
        super.write(nbt);
        nbt.putString("Entity", entityId);
        nbt.putInt("Count", count);
        nbt.putInt("Radius", radius);
        nbt.putInt("Delay", delay);
        nbt.putInt("Range", range);
        nbt.putInt("Cooldown", cooldown);
        nbt.putBoolean("Primed", primed);
        nbt.putBoolean("Waiting", waiting);
        return nbt;
    }
}
