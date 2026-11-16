package rustcraft.bots.ai;

import java.util.EnumSet;
import java.util.Random;

import javax.annotation.Nullable;

import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.ai.goal.Goal;
import net.minecraft.util.SoundEvents;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.vector.Vector3d;
import rustcraft.bots.AbstractScientistEntity;
import rustcraft.bots.BotGunfire;
import rustcraft.bots.BotWeapon;

/**
 * Бой учёного: держит дистанцию, стрейфится, стреляет очередями (хитскан), перезаряжается,
 * при низком HP отходит в укрытие и «колет шприц».
 */
public class ScientistCombatGoal extends Goal {
    private final AbstractScientistEntity bot;
    private final Random rand;

    private int reaction;
    private int unseenTicks;
    private int seenTicks;
    @Nullable
    private Vector3d lastSeenAim;
    private int strafeTimer;
    private boolean strafeLeft;
    private int repathTimer;
    private int burstLeft;
    private int shotCooldown;
    private int recoil;
    @Nullable
    private BlockPos cover;
    private int coverSearchCooldown;
    private float healBudget;
    private int healTimer;
    private boolean healing;

    public ScientistCombatGoal(AbstractScientistEntity bot) {
        this.bot = bot;
        this.rand = bot.getRNG();
        setMutexFlags(EnumSet.of(Goal.Flag.MOVE, Goal.Flag.LOOK));
    }

    @Override
    public boolean shouldExecute() {
        LivingEntity t = bot.getAttackTarget();
        return t != null && AbstractScientistEntity.isValidTarget(t);
    }

    @Override
    public boolean shouldContinueExecuting() {
        return shouldExecute();
    }

    @Override
    public void startExecuting() {
        bot.setAggroed(true);
        reaction = bot.rollReactionDelay();
        unseenTicks = 0;
        seenTicks = 0;
        lastSeenAim = null;
        burstLeft = 0;
        shotCooldown = 0;
        strafeTimer = 0;
        repathTimer = 0;
        cover = null;
        healBudget = bot.getMaxHealth() * 0.4F;
        healing = false;
    }

    @Override
    public void resetTask() {
        bot.setAggroed(false);
        bot.getNavigator().clearPath();
        cover = null;
    }

    /** Получил урон — реакция ускоряется (знает, откуда стреляют). */
    public void onHurt() {
        reaction = Math.min(reaction, 4);
        healing = false;
    }

    @Override
    public void tick() {
        LivingEntity target = bot.getAttackTarget();
        if (target == null) {
            return;
        }
        BotWeapon w = bot.getWeapon();
        if (shotCooldown > 0) {
            shotCooldown--;
        }
        double dist = bot.getDistance(target);

        Vector3d aim = bot.findVisibleAimPoint(target);
        boolean los = aim != null;
        if (los) {
            if (unseenTicks > 60) {
                reaction = Math.max(reaction, bot.rollReactionDelay() / 2);
            }
            unseenTicks = 0;
            seenTicks++;
            lastSeenAim = aim;
        } else {
            unseenTicks++;
            seenTicks = 0;
        }

        // потеря цели: слишком долго не видно, слишком далеко, или увели от дома
        if (unseenTicks > 400 || dist > 56.0D
                || bot.distanceSqFromHome(bot.getPositionVec()) > sq(AbstractScientistEntity.LEASH_RADIUS + 8.0D)) {
            bot.setAttackTarget(null);
            return;
        }

        // взгляд
        if (los) {
            bot.getLookController().setLookPositionWithEntity(target, 30.0F, 30.0F);
        } else if (lastSeenAim != null) {
            bot.getLookController().setLookPosition(lastSeenAim.x, lastSeenAim.y, lastSeenAim.z, 30.0F, 30.0F);
        }

        // движение
        boolean lowHp = bot.getHealth() < bot.getMaxHealth() * 0.35F;
        boolean wantCover = (lowHp && healBudget > 0.0F) || (bot.isReloading() && los && dist < 14.0D);
        if (wantCover) {
            moveToCover(target, los);
        } else {
            cover = null;
            healing = false;
            boolean targetOutOfLeash = bot.distanceSqFromHome(target.getPositionVec())
                    > sq(AbstractScientistEntity.LEASH_RADIUS);
            if ((!los || dist > w.prefMax || dist > w.range - 2) && !targetOutOfLeash) {
                chase(target, los);
            } else {
                engage(target, dist, w);
            }
        }

        // стрельба
        if (reaction > 0) {
            if (los) {
                reaction--;
            }
        } else if (los && dist <= w.range && isFacing(aim)) {
            tryFire(aim, dist, target, false);
        } else if (!los && bot.canSuppress() && unseenTicks < 30 && lastSeenAim != null
                && bot.getDistanceSq(lastSeenAim) < sq(w.range) && isFacing(lastSeenAim)) {
            // подавляющий огонь по последней видимой позиции
            tryFire(lastSeenAim, dist, null, true);
        }

        if (bot.getAmmo() <= 0) {
            bot.startReload();
        }
    }

    private void chase(LivingEntity target, boolean los) {
        if (--repathTimer <= 0) {
            repathTimer = 10 + rand.nextInt(6);
            bot.getNavigator().tryMoveToEntityLiving(target, los ? 0.85D : 1.0D);
        }
    }

    private void engage(LivingEntity target, double dist, BotWeapon w) {
        bot.getNavigator().clearPath();
        if (--strafeTimer <= 0) {
            strafeLeft = rand.nextBoolean();
            strafeTimer = 20 + rand.nextInt(40);
        }
        float forward = 0.0F;
        if (dist < w.prefMin) {
            forward = -0.5F;
        } else if (dist > w.prefMax - 3) {
            forward = 0.35F;
        }
        float side = strafeLeft ? 0.5F : -0.5F;
        if (!bot.isSafeMove(forward, side)) {
            strafeLeft = !strafeLeft;
            side = -side;
            strafeTimer = 30;
            if (!bot.isSafeMove(forward, side)) {
                side = 0.0F;
            }
        }
        if (!bot.isSafeMove(forward, side)) {
            forward = 0.0F;
            if (!bot.isSafeMove(0.0F, side)) {
                side = 0.0F;
            }
        }
        bot.getMoveHelper().strafe(forward, side);
        bot.faceEntity(target, 30.0F, 30.0F);
    }

    private void moveToCover(LivingEntity target, boolean seen) {
        if (coverSearchCooldown > 0) {
            coverSearchCooldown--;
        }
        if (cover == null && coverSearchCooldown <= 0) {
            cover = findCover(target);
            coverSearchCooldown = 20;
            if (cover != null) {
                bot.getNavigator().tryMoveToXYZ(cover.getX() + 0.5D, cover.getY(), cover.getZ() + 0.5D, 1.15D);
                if (rand.nextInt(2) == 0) {
                    bot.radioCallout();
                }
            }
        }
        if (cover != null) {
            boolean arrived = bot.getDistanceSq(cover.getX() + 0.5D, cover.getY(), cover.getZ() + 0.5D) < 2.0D;
            if (!arrived && bot.getNavigator().noPath()) {
                cover = null; // не дошёл — поищем снова
            }
        } else if (seen) {
            // укрытия нет — пятимся, если сзади не обрыв
            float side = strafeLeft ? 0.4F : -0.4F;
            float back = bot.isSafeMove(-0.6F, side) ? -0.6F : 0.0F;
            if (!bot.isSafeMove(back, side)) {
                side = 0.0F;
            }
            bot.getMoveHelper().strafe(back, side);
            bot.faceEntity(target, 30.0F, 30.0F);
        }

        // лечение в укрытии (имитация шприца/бинта)
        if (!seen && bot.getHealth() < bot.getMaxHealth() * 0.6F && healBudget > 0.0F) {
            if (!healing) {
                healing = true;
                healTimer = 30;
                bot.playSound(SoundEvents.ITEM_ARMOR_EQUIP_LEATHER, 0.7F, 1.4F);
            }
            if (--healTimer <= 0) {
                healTimer = 12;
                bot.heal(1.0F);
                healBudget -= 1.0F;
            }
        } else {
            healing = false;
        }
    }

    @Nullable
    private BlockPos findCover(LivingEntity target) {
        Vector3d threatEye = target.getEyePosition(1.0F);
        BlockPos origin = bot.getPosition();
        BlockPos best = null;
        double bestScore = -Double.MAX_VALUE;
        for (int i = 0; i < 16; i++) {
            BlockPos p = origin.add(rand.nextInt(17) - 8, rand.nextInt(5) - 2, rand.nextInt(17) - 8);
            // ищем пол в столбце
            for (int dy = 0; dy < 4 && !bot.isSafeStandPos(p); dy++) {
                p = p.down();
            }
            if (!bot.isSafeStandPos(p)) {
                continue;
            }
            Vector3d pv = new Vector3d(p.getX() + 0.5D, p.getY(), p.getZ() + 0.5D);
            if (bot.distanceSqFromHome(pv) > sq(AbstractScientistEntity.LEASH_RADIUS)) {
                continue;
            }
            Vector3d eyeThere = pv.add(0.0D, bot.getEyeHeight(), 0.0D);
            if (bot.hasClearShot(threatEye, eyeThere)) {
                continue; // оттуда видно
            }
            double score = pv.squareDistanceTo(target.getPositionVec()) * 0.1D - bot.getDistanceSq(pv);
            if (score > bestScore) {
                bestScore = score;
                best = p;
            }
        }
        return best;
    }

    private boolean isFacing(Vector3d point) {
        double dx = point.x - bot.getPosX();
        double dz = point.z - bot.getPosZ();
        float yaw = (float) (MathHelper.atan2(dz, dx) * (180D / Math.PI)) - 90.0F;
        return Math.abs(MathHelper.wrapDegrees(bot.rotationYawHead - yaw)) < 35.0F;
    }

    private void tryFire(Vector3d aim, double dist, @Nullable LivingEntity target, boolean suppress) {
        if (shotCooldown > 0 || bot.isReloading()) {
            return;
        }
        if (bot.getAmmo() <= 0) {
            bot.startReload();
            return;
        }
        BotWeapon w = bot.getWeapon();
        if (burstLeft <= 0) {
            burstLeft = w.burstMin + rand.nextInt(w.burstMax - w.burstMin + 1);
            recoil = 0;
        }

        float spread = w.spread + (float) dist * 0.04F + recoil * 0.35F;
        if (target != null) {
            Vector3d m = target.getMotion();
            double speed = Math.sqrt(m.x * m.x + m.z * m.z);
            spread += (float) Math.min(speed * 12.0D, 4.0D);
            if (seenTicks < 20) {
                spread += 1.0F; // только что увидел
            }
        }
        if (!bot.getNavigator().noPath() || bot.getMotion().lengthSquared() > 0.003D) {
            spread += 0.8F;
        }
        if (suppress) {
            spread += 3.0F;
        }
        switch (bot.world.getDifficulty()) {
            case EASY:
                spread *= 1.4F;
                break;
            case HARD:
                spread *= 0.85F;
                break;
            default:
                break;
        }

        BotGunfire.fire(bot, aim, spread, bot.difficultyDamageMult());
        bot.consumeAmmo();
        burstLeft--;
        recoil++;
        shotCooldown = burstLeft > 0 ? w.shotInterval
                : Math.max(w.shotInterval, w.pauseMin + rand.nextInt(Math.max(1, w.pauseMax - w.pauseMin + 1)));
        if (bot.getAmmo() <= 0) {
            burstLeft = 0;
            bot.startReload();
        }
    }

    private static double sq(double d) {
        return d * d;
    }
}
