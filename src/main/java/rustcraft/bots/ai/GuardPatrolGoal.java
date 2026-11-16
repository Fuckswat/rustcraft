package rustcraft.bots.ai;

import java.util.EnumSet;
import java.util.Random;

import javax.annotation.Nullable;

import net.minecraft.entity.ai.goal.Goal;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.vector.Vector3d;
import rustcraft.bots.AbstractScientistEntity;

/**
 * Патруль вокруг «дома» (точки спавна): только точки с твёрдой опорой, без воды —
 * чтобы не уходить с нефтяной вышки. Если бот оказался далеко от дома, сначала возвращается.
 */
public class GuardPatrolGoal extends Goal {
    private static final int PATROL_RADIUS = 10;

    private final AbstractScientistEntity bot;
    private final double speed;
    private final Random rand;
    @Nullable
    private BlockPos dest;

    public GuardPatrolGoal(AbstractScientistEntity bot, double speed) {
        this.bot = bot;
        this.speed = speed;
        this.rand = bot.getRNG();
        setMutexFlags(EnumSet.of(Goal.Flag.MOVE));
    }

    @Override
    public boolean shouldExecute() {
        if (bot.getAttackTarget() != null || bot.isBeingRidden() || bot.isPassenger()) {
            return false;
        }
        boolean farFromHome = bot.distanceSqFromHome(bot.getPositionVec()) > (PATROL_RADIUS + 4) * (PATROL_RADIUS + 4);
        if (!farFromHome && rand.nextInt(90) != 0) {
            return false;
        }
        dest = farFromHome ? findNear(bot.getHome(), 3) : findNear(bot.getHome(), PATROL_RADIUS);
        if (dest == null && farFromHome) {
            dest = bot.getHome();
        }
        return dest != null;
    }

    @Override
    public void startExecuting() {
        if (dest != null) {
            bot.getNavigator().tryMoveToXYZ(dest.getX() + 0.5D, dest.getY(), dest.getZ() + 0.5D, speed);
        }
    }

    @Override
    public boolean shouldContinueExecuting() {
        return bot.getAttackTarget() == null && !bot.getNavigator().noPath();
    }

    @Override
    public void resetTask() {
        dest = null;
    }

    @Nullable
    private BlockPos findNear(BlockPos center, int radius) {
        for (int attempt = 0; attempt < 12; attempt++) {
            BlockPos p = center.add(rand.nextInt(radius * 2 + 1) - radius, 3, rand.nextInt(radius * 2 + 1) - radius);
            for (int dy = 0; dy < 8; dy++) {
                if (bot.isSafeStandPos(p)) {
                    Vector3d v = new Vector3d(p.getX() + 0.5D, p.getY(), p.getZ() + 0.5D);
                    if (bot.distanceSqFromHome(v) <= (radius + 1) * (radius + 1)) {
                        return p;
                    }
                    break;
                }
                p = p.down();
            }
        }
        return null;
    }
}
