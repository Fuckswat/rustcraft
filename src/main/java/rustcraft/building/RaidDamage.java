package rustcraft.building;

import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.vector.Vector3d;
import net.minecraft.world.Explosion;
import net.minecraft.world.World;
import net.minecraftforge.event.world.ExplosionEvent;
import net.minecraftforge.fml.common.ObfuscationReflectionHelper;

import java.lang.reflect.Field;
import java.util.HashSet;
import java.util.Set;

/**
 * Урон постройкам от ЛЮБОГО взрыва в мире (C4, ракеты, бобовки, TNT, криперы...).
 * Ванилла наши блоки не ломает: они убираются из списка, а HP списывается по силе взрыва и расстоянию.
 */
public final class RaidDamage {
    private RaidDamage() {}

    private static Field sizeField;
    private static boolean sizeFieldFailed;

    /** Сила взрыва (приватное поле Explosion.size, SRG field_77280_f). */
    public static float explosionPower(Explosion explosion) {
        if (!sizeFieldFailed) {
            try {
                if (sizeField == null) {
                    sizeField = ObfuscationReflectionHelper.findField(Explosion.class, "field_77280_f");
                }
                return sizeField.getFloat(explosion);
            } catch (Exception e) {
                sizeFieldFailed = true;
            }
        }
        return 4.0F;
    }

    /**
     * Урон в эпицентре по силе: бобовка(2)≈40, сумка(3)≈120, ракета/TNT(4)≈140, C4(5)≈550.
     * Кусочно-линейно между опорными точками, выше 5 — растёт дальше.
     */
    public static float centerDamage(float power) {
        float[] p = {0F, 1F, 2F, 3F, 4F, 5F};
        float[] d = {0F, 10F, 40F, 120F, 140F, 550F};
        if (power >= 5F) return 550F + (power - 5F) * 200F;
        for (int i = 1; i < p.length; i++) {
            if (power <= p[i]) {
                float t = (power - p[i - 1]) / (p[i] - p[i - 1]);
                return d[i - 1] + (d[i] - d[i - 1]) * t;
            }
        }
        return 550F;
    }

    /** Радиус, в котором взрыв задевает постройки. */
    public static double radius(float power) {
        return 0.75 + power * 0.35;
    }

    /** Полный урон до 0.75 блока от центра постройки, дальше быстро падает (кубически). */
    public static float falloff(double dist, double radius) {
        if (dist <= 0.75) return 1F;
        if (dist >= radius) return 0F;
        double t = (dist - 0.75) / (radius - 0.75);
        double f = 1.0 - t;
        return (float) (f * f * f);
    }

    public static boolean isProtected(Block b) {
        return b instanceof IRustStructure || b instanceof ToolCupboardBlock;
    }

    public static void onDetonate(ExplosionEvent.Detonate event) {
        World world = event.getWorld();
        if (world.isRemote) return;
        // Ванилла не должна мгновенно ломать наши блоки.
        event.getAffectedBlocks().removeIf(p -> isProtected(world.getBlockState(p).getBlock()));

        Explosion explosion = event.getExplosion();
        float power = explosionPower(explosion);
        float center = centerDamage(power);
        if (center <= 0F) return;
        double r = radius(power);
        Vector3d c = explosion.getPosition();

        Set<BlockPos> done = new HashSet<BlockPos>();
        int ri = MathHelper.ceil(r);
        BlockPos origin = new BlockPos(c);
        for (BlockPos mp : BlockPos.getAllInBoxMutable(origin.add(-ri, -ri - 1, -ri), origin.add(ri, ri, ri))) {
            BlockState state = world.getBlockState(mp);
            if (!(state.getBlock() instanceof IRustStructure)) continue;
            IRustStructure s = (IRustStructure) state.getBlock();
            BlockPos main = s.getMainPos(state, mp).toImmutable();
            if (!done.add(main)) continue;

            double dist;
            if (s.getKind().door) {
                // Дверь — колонна 1x2x1: расстояние до ближайшей точки + 0.5.
                double nx = MathHelper.clamp(c.x, main.getX(), main.getX() + 1.0);
                double ny = MathHelper.clamp(c.y, main.getY(), main.getY() + 2.0);
                double nz = MathHelper.clamp(c.z, main.getZ(), main.getZ() + 1.0);
                dist = Math.sqrt(c.squareDistanceTo(nx, ny, nz)) + 0.5;
            } else {
                dist = Math.sqrt(c.squareDistanceTo(main.getX() + 0.5, main.getY() + 0.5, main.getZ() + 0.5));
            }
            float f = falloff(dist, r);
            if (f <= 0F) continue;
            f *= occlusion(world, c, main, s.getKind().door);
            float dmg = center * f;
            if (dmg >= 1F) {
                BuildingLogic.damage(world, main, dmg);
            }
        }
    }

    /** Каждая другая постройка на линии взрыв→цель режет урон до 20 % (стены прикрывают то, что за ними). */
    private static float occlusion(World world, Vector3d from, BlockPos target, boolean door) {
        Vector3d to = new Vector3d(target.getX() + 0.5, target.getY() + (door ? 1.0 : 0.5), target.getZ() + 0.5);
        Vector3d delta = to.subtract(from);
        double len = delta.length();
        if (len < 0.5) return 1F;
        int steps = (int) Math.ceil(len / 0.2);
        float mult = 1F;
        BlockPos last = null;
        BlockPos.Mutable mp = new BlockPos.Mutable();
        for (int i = 1; i < steps; i++) {
            Vector3d p = from.add(delta.scale(i / (double) steps));
            mp.setPos(p.x, p.y, p.z);
            if (mp.equals(target) || (door && mp.equals(target.up()))) continue;
            if (last != null && last.equals(mp)) continue;
            last = mp.toImmutable();
            BlockState st = world.getBlockState(mp);
            if (st.getBlock() instanceof IRustStructure) {
                mult *= 0.2F;
            }
        }
        return mult;
    }
}
