package rustcraft.world.map;

import net.minecraft.block.Block;
import net.minecraft.nbt.CompoundNBT;
import net.minecraft.nbt.ListNBT;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.MutableBoundingBox;
import net.minecraft.world.server.ServerWorld;
import net.minecraft.world.storage.WorldSavedData;
import net.minecraftforge.common.util.Constants;
import net.minecraftforge.registries.ForgeRegistries;
import rustcraft.world.WorldConfig;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/** Сохраняемое состояние карты: построена ли, где монументы, очередь восстановления бочек. */
public class MapData extends WorldSavedData {
    public static final String NAME = "rustcraft_map";

    public static final class Monument {
        public final String id;
        public final MutableBoundingBox box;
        public final BlockPos tp;

        public Monument(String id, MutableBoundingBox box, BlockPos tp) {
            this.id = id;
            this.box = box;
            this.tp = tp;
        }
    }

    private static final class Respawn {
        final long pos;
        final String block;
        final long time;

        Respawn(long pos, String block, long time) {
            this.pos = pos;
            this.block = block;
            this.time = time;
        }
    }

    public boolean built;
    public boolean started;
    public boolean automatic;
    public int version;
    public long terrainSeed;
    public int buildStage;
    public int[] buildCursor = new int[0];
    public BlockPos center = BlockPos.ZERO;
    public final List<Monument> monuments = new ArrayList<>();
    private final List<Respawn> respawns = new ArrayList<>();

    public MapData() {
        super(NAME);
    }

    public static MapData get(ServerWorld world) {
        return world.getSavedData().getOrCreate(MapData::new, NAME);
    }

    public void putMonument(String id, MutableBoundingBox box, BlockPos tp) {
        monuments.removeIf(m -> m.id.equals(id));
        monuments.add(new Monument(id, box, tp));
        markDirty();
    }

    @Nullable
    public Monument find(String id) {
        for (Monument m : monuments) if (m.id.equals(id)) return m;
        return null;
    }

    @Nullable
    public Monument monumentAt(BlockPos pos) {
        for (Monument m : monuments) if (m.box.isVecInside(pos)) return m;
        return null;
    }

    public void scheduleBarrelRespawn(ServerWorld world, BlockPos pos, Block block) {
        if (monumentAt(pos) == null || block.getRegistryName() == null) return;
        long t = world.getGameTime() + WorldConfig.BARREL_RESPAWN_MINUTES.get() * 1200L;
        respawns.add(new Respawn(pos.toLong(), block.getRegistryName().toString(), t));
        markDirty();
    }

    public void tickRespawns(ServerWorld world) {
        if (respawns.isEmpty()) return;
        long now = world.getGameTime();
        Iterator<Respawn> it = respawns.iterator();
        boolean changed = false;
        while (it.hasNext()) {
            Respawn r = it.next();
            if (now < r.time) continue;
            BlockPos p = BlockPos.fromLong(r.pos);
            if (!world.isBlockLoaded(p)) continue;
            it.remove();
            changed = true;
            ResourceLocation rl = ResourceLocation.tryCreate(r.block);
            if (rl == null || !ForgeRegistries.BLOCKS.containsKey(rl)) continue;
            if (world.isAirBlock(p) && !world.isAirBlock(p.down())) {
                world.setBlockState(p, ForgeRegistries.BLOCKS.getValue(rl).getDefaultState(), 3);
            }
        }
        if (changed) markDirty();
    }

    @Override
    public void read(CompoundNBT nbt) {
        built = nbt.getBoolean("Built");
        started = nbt.getBoolean("Started");
        automatic = nbt.getBoolean("Automatic");
        version = nbt.getInt("LayoutVersion");
        terrainSeed = nbt.getLong("TerrainSeed");
        buildStage = nbt.getInt("BuildStage");
        buildCursor = nbt.getIntArray("BuildCursor");
        center = BlockPos.fromLong(nbt.getLong("Center"));
        monuments.clear();
        ListNBT list = nbt.getList("Monuments", Constants.NBT.TAG_COMPOUND);
        for (int i = 0; i < list.size(); i++) {
            CompoundNBT c = list.getCompound(i);
            int[] b = c.getIntArray("Box");
            if (b.length != 6) continue;
            monuments.add(new Monument(c.getString("Id"), new MutableBoundingBox(b[0], b[1], b[2], b[3], b[4], b[5]),
                    BlockPos.fromLong(c.getLong("Tp"))));
        }
        respawns.clear();
        ListNBT rl = nbt.getList("Respawns", Constants.NBT.TAG_COMPOUND);
        for (int i = 0; i < rl.size(); i++) {
            CompoundNBT c = rl.getCompound(i);
            respawns.add(new Respawn(c.getLong("Pos"), c.getString("Block"), c.getLong("Time")));
        }
    }

    @Override
    public CompoundNBT write(CompoundNBT nbt) {
        nbt.putBoolean("Built", built);
        nbt.putBoolean("Started", started);
        nbt.putBoolean("Automatic", automatic);
        nbt.putInt("LayoutVersion", version);
        nbt.putLong("TerrainSeed", terrainSeed);
        nbt.putInt("BuildStage", buildStage);
        nbt.putIntArray("BuildCursor", buildCursor);
        nbt.putLong("Center", center.toLong());
        ListNBT list = new ListNBT();
        for (Monument m : monuments) {
            CompoundNBT c = new CompoundNBT();
            c.putString("Id", m.id);
            c.putIntArray("Box", new int[]{m.box.minX, m.box.minY, m.box.minZ, m.box.maxX, m.box.maxY, m.box.maxZ});
            c.putLong("Tp", m.tp.toLong());
            list.add(c);
        }
        nbt.put("Monuments", list);
        ListNBT rl = new ListNBT();
        for (Respawn r : respawns) {
            CompoundNBT c = new CompoundNBT();
            c.putLong("Pos", r.pos);
            c.putString("Block", r.block);
            c.putLong("Time", r.time);
            rl.add(c);
        }
        nbt.put("Respawns", rl);
        return nbt;
    }
}
