package rustcraft.world.map;

import net.minecraft.block.AbstractButtonBlock;
import net.minecraft.block.AbstractRailBlock;
import net.minecraft.block.AbstractSignBlock;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.block.CarpetBlock;
import net.minecraft.block.DoorBlock;
import net.minecraft.block.FlowerPotBlock;
import net.minecraft.block.LadderBlock;
import net.minecraft.block.LanternBlock;
import net.minecraft.block.LeverBlock;
import net.minecraft.block.PressurePlateBlock;
import net.minecraft.block.StairsBlock;
import net.minecraft.block.TorchBlock;
import net.minecraft.block.TrapDoorBlock;
import net.minecraft.state.properties.DoorHingeSide;
import net.minecraft.state.properties.DoubleBlockHalf;
import net.minecraft.state.properties.Half;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.Direction;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.MutableBoundingBox;
import rustcraft.world.WorldRegistry;
import rustcraft.world.tile.NpcSpawnerTile;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

/**
 * Буфер блоков сооружения в локальных координатах (origin + x,y,z). Последняя запись побеждает.
 * Выполняется {@link BlueprintTask}: сначала столбы до дна, потом обычные блоки снизу вверх,
 * потом «навесные» (лестницы, фонари, двери), потом настройка тайлов.
 */
public class Blueprint {
    public final BlockPos origin;
    Map<Long, BlockState> blocks = new HashMap<>();
    final List<long[]> pillars = new ArrayList<>(); // {absPosLong, stateIndex}
    final List<BlockState> pillarStates = new ArrayList<>();
    final List<Object[]> post = new ArrayList<>(); // {BlockPos, Consumer<TileEntity>}
    int minX = Integer.MAX_VALUE, minY = Integer.MAX_VALUE, minZ = Integer.MAX_VALUE;
    int maxX = Integer.MIN_VALUE, maxY = Integer.MIN_VALUE, maxZ = Integer.MIN_VALUE;

    public Blueprint(BlockPos origin) {
        this.origin = origin;
    }

    private void track(int x, int y, int z) {
        if (x < minX) minX = x;
        if (y < minY) minY = y;
        if (z < minZ) minZ = z;
        if (x > maxX) maxX = x;
        if (y > maxY) maxY = y;
        if (z > maxZ) maxZ = z;
    }

    public BlockPos abs(int x, int y, int z) {
        return origin.add(x, y, z);
    }

    // базовые операции
    public void set(int x, int y, int z, BlockState s) {
        blocks.put(abs(x, y, z).toLong(), s);
        track(x, y, z);
    }

    public void set(int x, int y, int z, Block b) {
        set(x, y, z, b.getDefaultState());
    }

    public BlockState get(int x, int y, int z) {
        return blocks.get(abs(x, y, z).toLong());
    }

    public boolean has(int x, int y, int z) {
        return blocks.containsKey(abs(x, y, z).toLong());
    }

    public void fill(int x1, int y1, int z1, int x2, int y2, int z2, BlockState s) {
        for (int x = Math.min(x1, x2); x <= Math.max(x1, x2); x++)
            for (int y = Math.min(y1, y2); y <= Math.max(y1, y2); y++)
                for (int z = Math.min(z1, z2); z <= Math.max(z1, z2); z++)
                    set(x, y, z, s);
    }

    public void fill(int x1, int y1, int z1, int x2, int y2, int z2, Block b) {
        fill(x1, y1, z1, x2, y2, z2, b.getDefaultState());
    }

    public void air(int x1, int y1, int z1, int x2, int y2, int z2) {
        fill(x1, y1, z1, x2, y2, z2, Blocks.AIR.getDefaultState());
    }

    /** Только периметр по X/Z на высотах y1..y2. */
    public void walls(int x1, int y1, int z1, int x2, int y2, int z2, BlockState s) {
        int ax = Math.min(x1, x2), bx = Math.max(x1, x2), az = Math.min(z1, z2), bz = Math.max(z1, z2);
        for (int y = Math.min(y1, y2); y <= Math.max(y1, y2); y++) {
            for (int x = ax; x <= bx; x++) {
                set(x, y, az, s);
                set(x, y, bz, s);
            }
            for (int z = az; z <= bz; z++) {
                set(ax, y, z, s);
                set(bx, y, z, s);
            }
        }
    }

    public void walls(int x1, int y1, int z1, int x2, int y2, int z2, Block b) {
        walls(x1, y1, z1, x2, y2, z2, b.getDefaultState());
    }

    /** Коробка: пол y1, потолок y2, стены, внутри воздух. */
    public void room(int x1, int y1, int z1, int x2, int y2, int z2, BlockState wall, BlockState floor, BlockState ceil) {
        int ax = Math.min(x1, x2), bx = Math.max(x1, x2), az = Math.min(z1, z2), bz = Math.max(z1, z2);
        fill(ax, y1, az, bx, y1, bz, floor);
        fill(ax, y2, az, bx, y2, bz, ceil);
        walls(ax, y1 + 1, az, bx, y2 - 1, bz, wall);
        air(ax + 1, y1 + 1, az + 1, bx - 1, y2 - 1, bz - 1);
    }

    /** Столб от y вниз до первого твёрдого (не жидкость/не воздух) блока мира. */
    public void pillarDown(int x, int y, int z, BlockState s) {
        int idx = pillarStates.indexOf(s);
        if (idx < 0) {
            pillarStates.add(s);
            idx = pillarStates.size() - 1;
        }
        pillars.add(new long[]{abs(x, y, z).toLong(), idx});
        track(x, y, z);
    }

    public void pillarDown(int x, int y, int z, Block b) {
        pillarDown(x, y, z, b.getDefaultState());
    }

    /** Линия Брезенхема (3D) блоков. */
    public void line(int x1, int y1, int z1, int x2, int y2, int z2, BlockState s) {
        int n = Math.max(Math.abs(x2 - x1), Math.max(Math.abs(y2 - y1), Math.abs(z2 - z1)));
        for (int i = 0; i <= n; i++) {
            double t = n == 0 ? 0 : (double) i / n;
            set((int) Math.round(x1 + (x2 - x1) * t), (int) Math.round(y1 + (y2 - y1) * t), (int) Math.round(z1 + (z2 - z1) * t), s);
        }
    }

    // готовые состояния
    public static BlockState stairs(Block b, Direction facing) {
        return b.getDefaultState().with(StairsBlock.FACING, facing).with(StairsBlock.HALF, Half.BOTTOM);
    }

    public static BlockState stairsTop(Block b, Direction facing) {
        return b.getDefaultState().with(StairsBlock.FACING, facing).with(StairsBlock.HALF, Half.TOP);
    }

    /** facing — куда «смотрит» лестница (от стены). Опорный блок — с противоположной стороны. */
    public static BlockState ladder(Direction facing) {
        return Blocks.LADDER.getDefaultState().with(LadderBlock.FACING, facing);
    }

    public static BlockState lantern(boolean hanging) {
        return Blocks.LANTERN.getDefaultState().with(LanternBlock.HANGING, hanging);
    }

    public static BlockState trapdoorTop(Block b, Direction facing) {
        return b.getDefaultState().with(TrapDoorBlock.HORIZONTAL_FACING, facing).with(TrapDoorBlock.HALF, Half.TOP);
    }

    /** Вертикальная лестница-шахта: блоки ladder на x,z от y1 до y2 (опора должна быть со стороны -facing). */
    public void ladderColumn(int x, int z, int y1, int y2, Direction facing) {
        for (int y = Math.min(y1, y2); y <= Math.max(y1, y2); y++) set(x, y, z, ladder(facing));
    }

    public void door(int x, int y, int z, Block door, Direction facing, boolean rightHinge) {
        BlockState s = door.getDefaultState().with(DoorBlock.FACING, facing)
                .with(DoorBlock.HINGE, rightHinge ? DoorHingeSide.RIGHT : DoorHingeSide.LEFT).with(DoorBlock.OPEN, false);
        set(x, y, z, s.with(DoorBlock.HALF, DoubleBlockHalf.LOWER));
        set(x, y + 1, z, s.with(DoorBlock.HALF, DoubleBlockHalf.UPPER));
    }

    /** Ключ-дверь с указанным считывателем (reader ставится в позицию rx,ry,rz и смотрит readerFacing). */
    public void keycardDoor(int x, int y, int z, Direction doorFacing, int level, int rx, int ry, int rz, Direction readerFacing) {
        door(x, y, z, WorldRegistry.KEYCARD_DOOR.get(), doorFacing, false);
        Block reader = level == 1 ? WorldRegistry.KEYCARD_READER.get()
                : level == 2 ? WorldRegistry.KEYCARD_READER_BLUE.get() : WorldRegistry.KEYCARD_READER_RED.get();
        set(rx, ry, rz, reader.getDefaultState().with(net.minecraft.block.HorizontalBlock.HORIZONTAL_FACING, readerFacing));
    }

    // лут и NPC
    public void crateBasic(int x, int y, int z) {
        set(x, y, z, WorldRegistry.CRATE_BASIC.get());
    }

    public void crateMilitary(int x, int y, int z) {
        set(x, y, z, WorldRegistry.CRATE_MILITARY.get());
    }

    public void crateElite(int x, int y, int z) {
        set(x, y, z, WorldRegistry.CRATE_ELITE.get());
    }

    public void barrel(int x, int y, int z) {
        set(x, y, z, WorldRegistry.LOOT_BARREL.get());
    }

    public void oilBarrel(int x, int y, int z) {
        set(x, y, z, WorldRegistry.OIL_BARREL.get());
    }

    public void lockedCrate(int x, int y, int z, Direction facing) {
        set(x, y, z, WorldRegistry.LOCKED_CRATE.get().getDefaultState().with(net.minecraft.block.HorizontalBlock.HORIZONTAL_FACING, facing));
    }

    /** Спавнер ставится В ПОЛ (x,y,z — блок пола), NPC появляются на y+1. */
    public void spawner(int x, int y, int z, String entity, int count, int radius) {
        set(x, y, z, WorldRegistry.NPC_SPAWNER.get());
        final String e = entity;
        final int c = count, r = radius;
        onTile(x, y, z, te -> {
            if (te instanceof NpcSpawnerTile) ((NpcSpawnerTile) te).configure(e, c, r);
        });
    }

    public void scientists(int x, int y, int z, int count, int radius) {
        spawner(x, y, z, "rustcraft:scientist", count, radius);
    }

    public void heavies(int x, int y, int z, int count, int radius) {
        spawner(x, y, z, "rustcraft:heavy_scientist", count, radius);
    }

    public void onTile(int x, int y, int z, Consumer<TileEntity> c) {
        post.add(new Object[]{abs(x, y, z), c});
    }

    // служебное
    public MutableBoundingBox worldBox() {
        if (minX > maxX) return new MutableBoundingBox(origin, origin);
        return new MutableBoundingBox(origin.getX() + minX, origin.getY() + minY, origin.getZ() + minZ,
                origin.getX() + maxX, origin.getY() + maxY, origin.getZ() + maxZ);
    }

    public int size() {
        return blocks.size() + pillars.size();
    }

    static boolean isAttachable(BlockState s) {
        Block b = s.getBlock();
        return b instanceof LadderBlock || b instanceof TorchBlock || b instanceof LanternBlock || b instanceof DoorBlock
                || b instanceof AbstractButtonBlock || b instanceof LeverBlock || b instanceof AbstractRailBlock
                || b instanceof CarpetBlock || b instanceof PressurePlateBlock || b instanceof AbstractSignBlock
                || b instanceof FlowerPotBlock;
    }
}
