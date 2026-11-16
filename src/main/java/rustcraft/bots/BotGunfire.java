package rustcraft.bots;

import java.util.List;
import java.util.Optional;
import java.util.Random;
import java.util.UUID;

import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.ai.attributes.AttributeModifier;
import net.minecraft.entity.ai.attributes.Attributes;
import net.minecraft.entity.ai.attributes.ModifiableAttributeInstance;
import net.minecraft.util.SoundCategory;
import net.minecraft.util.SoundEvents;
import net.minecraft.util.math.AxisAlignedBB;
import net.minecraft.util.math.BlockRayTraceResult;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.RayTraceContext;
import net.minecraft.util.math.RayTraceResult;
import net.minecraft.util.math.vector.Vector3d;
import net.minecraft.world.World;
import rustcraft.bots.net.BotNetwork;
import rustcraft.bots.net.BotShotPacket;

/** Хитскан-стрельба ботов: собственный рейтрейс от глаз с разбросом, блоки останавливают пулю. */
public final class BotGunfire {
    private BotGunfire() {}

    private static final UUID NO_KNOCKBACK_ID = UUID.fromString("6b0f6c1e-3c55-4d0f-9a43-2f3f0cbd5b71");
    private static final AttributeModifier NO_KNOCKBACK = new AttributeModifier(NO_KNOCKBACK_ID,
            "rustcraft bot bullet", 1.0D, AttributeModifier.Operation.ADDITION);

    /**
     * Один выстрел (все дробины).
     *
     * @param aimPoint  точка прицеливания
     * @param spreadDeg разброс в градусах (сигма ≈ половина)
     * @param dmgMult   множитель урона (сложность)
     */
    public static void fire(AbstractScientistEntity shooter, Vector3d aimPoint, float spreadDeg, float dmgMult) {
        World world = shooter.world;
        BotWeapon w = shooter.getWeapon();
        Random rand = shooter.getRNG();
        Vector3d eye = shooter.getEyePosition(1.0F);
        Vector3d baseDir = aimPoint.subtract(eye).normalize();

        for (int p = 0; p < w.pellets; p++) {
            Vector3d dir = applySpread(baseDir, spreadDeg, rand);
            Vector3d end = eye.add(dir.scale(w.range));
            BlockRayTraceResult blockHit = world.rayTraceBlocks(new RayTraceContext(eye, end,
                    RayTraceContext.BlockMode.COLLIDER, RayTraceContext.FluidMode.NONE, shooter));
            Vector3d stop = blockHit.getType() == RayTraceResult.Type.MISS ? end : blockHit.getHitVec();

            Entity victim = null;
            Vector3d victimHit = null;
            double best = Double.MAX_VALUE;
            AxisAlignedBB sweep = new AxisAlignedBB(eye, stop).grow(1.0D);
            List<Entity> list = world.getEntitiesInAABBexcluding(shooter, sweep, e -> canBeShot(shooter, e));
            for (Entity e : list) {
                AxisAlignedBB box = e.getBoundingBox().grow(0.1D);
                Optional<Vector3d> hit = box.rayTrace(eye, stop);
                if (box.contains(eye)) {
                    victim = e;
                    victimHit = eye;
                    break;
                }
                if (hit.isPresent()) {
                    double d = eye.squareDistanceTo(hit.get());
                    if (d < best) {
                        best = d;
                        victim = e;
                        victimHit = hit.get();
                    }
                }
            }

            byte hitType;
            Vector3d fxEnd;
            if (victim != null) {
                hitType = BotShotPacket.HIT_ENTITY;
                fxEnd = victimHit;
                float dmg = w.damage * dmgMult;
                double dist = Math.sqrt(eye.squareDistanceTo(victimHit));
                if (w.pellets > 1) {
                    // дробь быстро теряет силу
                    dmg *= (float) MathHelper.clamp(1.0D - (dist - 6.0D) / 18.0D, 0.3D, 1.0D);
                }
                if(victim instanceof LivingEntity) dmg=rustcraft.guns.ArmorProtection.absorb((LivingEntity)victim,victimHit,dmg);
                hurt(shooter, victim, dmg);
            } else if (blockHit.getType() != RayTraceResult.Type.MISS) {
                hitType = BotShotPacket.HIT_BLOCK;
                fxEnd = blockHit.getHitVec();
            } else {
                hitType = BotShotPacket.HIT_NONE;
                fxEnd = end;
            }
            // трассер у винтовок через выстрел, у дроби — пара дробин
            boolean tracer = w.pellets > 1 ? p < 2 : rand.nextInt(2) == 0;
            BotNetwork.sendShot(shooter, new BotShotPacket(shooter.getEntityId(), fxEnd.x, fxEnd.y, fxEnd.z,
                    hitType, tracer));
        }
        playShotSound(shooter, w, rand);
    }

    private static boolean canBeShot(AbstractScientistEntity shooter, Entity e) {
        if (e == null || !e.isAlive() || !e.canBeCollidedWith() || e.isSpectator()) {
            return false;
        }
        if (e instanceof AbstractScientistEntity) {
            return false; // своих не задеваем
        }
        return !shooter.isRidingSameEntity(e);
    }

    private static void hurt(AbstractScientistEntity shooter, Entity victim, float dmg) {
        BotDamageSource src = new BotDamageSource(shooter, shooter.getDeathMessageKind());
        if(victim instanceof PlayerEntity) src.setDamageBypassesArmor();
        if (victim instanceof LivingEntity) {
            LivingEntity living = (LivingEntity) victim;
            // автоматическая очередь не должна гаситься кадрами неуязвимости и толкать игрока
            living.hurtResistantTime = 0;
            ModifiableAttributeInstance kb = living.getAttribute(Attributes.KNOCKBACK_RESISTANCE);
            boolean added = false;
            if (kb != null && kb.getModifier(NO_KNOCKBACK_ID) == null) {
                kb.applyNonPersistentModifier(NO_KNOCKBACK);
                added = true;
            }
            try {
                living.attackEntityFrom(src, dmg);
            } finally {
                if (added) {
                    kb.removeModifier(NO_KNOCKBACK_ID);
                }
            }
        } else {
            victim.attackEntityFrom(src, dmg);
        }
    }

    static Vector3d applySpread(Vector3d dir, float spreadDeg, Random rand) {
        if (spreadDeg <= 0.0F) {
            return dir;
        }
        double sigma = Math.toRadians(spreadDeg) * 0.5D;
        // ортонормированный базис вокруг направления
        Vector3d up = Math.abs(dir.y) > 0.95D ? new Vector3d(1, 0, 0) : new Vector3d(0, 1, 0);
        Vector3d right = dir.crossProduct(up).normalize();
        Vector3d realUp = right.crossProduct(dir).normalize();
        double a = rand.nextGaussian() * sigma;
        double b = rand.nextGaussian() * sigma;
        return dir.add(right.scale(Math.tan(a))).add(realUp.scale(Math.tan(b))).normalize();
    }

    private static void playShotSound(AbstractScientistEntity shooter, BotWeapon w, Random rand) {
        World world = shooter.world;
        double x = shooter.getPosX();
        double y = shooter.getPosYEye();
        double z = shooter.getPosZ();
        float jitter = (rand.nextFloat() - 0.5F) * 0.1F;
        switch (w.soundKind) {
            case 0: // ПП — сухой, высокий
                world.playSound(null, x, y, z, SoundEvents.ENTITY_FIREWORK_ROCKET_BLAST, SoundCategory.HOSTILE,
                        2.6F, 1.75F + jitter);
                world.playSound(null, x, y, z, SoundEvents.ITEM_CROSSBOW_SHOOT, SoundCategory.HOSTILE,
                        1.2F, 1.9F + jitter);
                break;
            case 2: // дробовик — глухой бах
                world.playSound(null, x, y, z, SoundEvents.ENTITY_GENERIC_EXPLODE, SoundCategory.HOSTILE,
                        0.9F, 1.9F + jitter);
                world.playSound(null, x, y, z, SoundEvents.ENTITY_FIREWORK_ROCKET_LARGE_BLAST, SoundCategory.HOSTILE,
                        3.0F, 0.7F + jitter);
                break;
            default: // винтовка
                world.playSound(null, x, y, z, SoundEvents.ENTITY_FIREWORK_ROCKET_BLAST, SoundCategory.HOSTILE,
                        3.2F, 1.25F + jitter);
                world.playSound(null, x, y, z, SoundEvents.ITEM_CROSSBOW_SHOOT, SoundCategory.HOSTILE,
                        1.4F, 1.45F + jitter);
                break;
        }
    }
}
