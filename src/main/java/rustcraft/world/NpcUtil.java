package rustcraft.world;

import net.minecraft.block.BlockState;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.MobEntity;
import net.minecraft.entity.SpawnReason;
import net.minecraft.util.Direction;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.server.ServerWorld;
import net.minecraftforge.registries.ForgeRegistries;

import javax.annotation.Nullable;
import java.util.Random;
import java.util.function.Consumer;

/** Поиск типов NPC по id (безопасно к отсутствию модуля bots) и спавн вокруг точки. */
public final class NpcUtil {
    private NpcUtil() {}

    @Nullable
    public static EntityType<?> lookup(@Nullable String id) {
        if (id == null || id.isEmpty()) return null;
        ResourceLocation rl = ResourceLocation.tryCreate(id);
        if (rl == null || !ForgeRegistries.ENTITIES.containsKey(rl)) return null;
        return ForgeRegistries.ENTITIES.getValue(rl);
    }

    /** Тип по id, иначе запасной из конфига, иначе null. */
    @Nullable
    public static EntityType<?> resolve(String id) {
        EntityType<?> t = lookup(id);
        if (t == null) t = lookup(WorldConfig.FALLBACK_NPC.get());
        return t;
    }

    public static boolean canStand(ServerWorld w, BlockPos p) {
        if (!w.isBlockLoaded(p)) return false;
        BlockState below = w.getBlockState(p.down());
        if (!below.isSolidSide(w, p.down(), Direction.UP)) return false;
        BlockState a = w.getBlockState(p);
        BlockState b = w.getBlockState(p.up());
        return a.getCollisionShape(w, p).isEmpty() && b.getCollisionShape(w, p.up()).isEmpty()
                && a.getFluidState().isEmpty() && b.getFluidState().isEmpty();
    }

    @Nullable
    public static BlockPos findSpot(ServerWorld w, BlockPos center, int minR, int maxR, int dyUp, int dyDown, Random rand) {
        for (int attempt = 0; attempt < 30; attempt++) {
            double ang = rand.nextDouble() * Math.PI * 2.0D;
            double dist = minR + rand.nextDouble() * Math.max(0, maxR - minR);
            int x = center.getX() + (int) Math.round(Math.cos(ang) * dist);
            int z = center.getZ() + (int) Math.round(Math.sin(ang) * dist);
            for (int dy = dyUp; dy >= -dyDown; dy--) {
                BlockPos p = new BlockPos(x, center.getY() + dy, z);
                if (canStand(w, p)) return p;
            }
        }
        return null;
    }

    @Nullable
    public static Entity spawnAt(ServerWorld w, EntityType<?> type, BlockPos p, @Nullable Consumer<Entity> init) {
        Entity e = type.create(w);
        if (e == null) return null;
        e.setLocationAndAngles(p.getX() + 0.5D, p.getY(), p.getZ() + 0.5D, w.rand.nextFloat() * 360.0F, 0.0F);
        if (e instanceof MobEntity) {
            MobEntity mob = (MobEntity) e;
            mob.onInitialSpawn(w, w.getDifficultyForLocation(p), SpawnReason.SPAWNER, null, null);
            mob.enablePersistence();
        }
        CompoundNBTWithHome(e, p);
        if (init != null) init.accept(e);
        w.addEntity(e);
        return e;
    }

    private static void CompoundNBTWithHome(Entity e, BlockPos pos) {
        net.minecraft.nbt.CompoundNBT tag = e.writeWithoutTypeId(new net.minecraft.nbt.CompoundNBT());
        tag.putLong("BotHome", pos.toLong());
        e.read(tag);
    }

    /** @return сколько реально заспавнено */
    public static int spawnAround(ServerWorld w, BlockPos center, String id, int count, int minR, int maxR, @Nullable Consumer<Entity> init) {
        EntityType<?> type = resolve(id);
        if (type == null) return 0;
        int n = 0;
        for (int i = 0; i < count; i++) {
            BlockPos p = findSpot(w, center, minR, maxR, 4, 4, w.rand);
            if (p == null) continue;
            if (spawnAt(w, type, p, init) != null) n++;
        }
        return n;
    }
}
