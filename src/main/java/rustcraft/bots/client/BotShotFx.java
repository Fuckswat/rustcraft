package rustcraft.bots.client;

import java.util.Random;

import net.minecraft.block.BlockState;
import net.minecraft.client.Minecraft;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.entity.Entity;
import net.minecraft.particles.BlockParticleData;
import net.minecraft.particles.ParticleTypes;
import net.minecraft.particles.RedstoneParticleData;
import net.minecraft.util.SoundCategory;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.vector.Vector3d;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import rustcraft.bots.net.BotShotPacket;

/** Клиентские эффекты выстрела бота: вспышка у ствола, трассер, искры/крошка в месте попадания. */
@OnlyIn(Dist.CLIENT)
public final class BotShotFx {
    private BotShotFx() {}

    private static final RedstoneParticleData TRACER = new RedstoneParticleData(1.0F, 0.86F, 0.45F, 0.45F);

    public static void onShot(BotShotPacket msg) {
        Minecraft mc = Minecraft.getInstance();
        ClientWorld world = mc.world;
        if (world == null) {
            return;
        }
        Entity shooter = world.getEntityByID(msg.shooterId);
        if (shooter == null) {
            return;
        }
        Random rand = world.rand;
        Vector3d end = new Vector3d(msg.endX, msg.endY, msg.endZ);
        Vector3d eye = shooter.getEyePosition(1.0F);
        Vector3d dir = end.subtract(eye);
        double len = dir.length();
        if (len < 1.0E-3D) {
            return;
        }
        dir = dir.scale(1.0D / len);
        // ствол: чуть вперёд, вправо и ниже глаз
        float yawRad = shooter.getRotationYawHead() * ((float) Math.PI / 180F);
        Vector3d right = new Vector3d(-MathHelper.cos(yawRad), 0.0D, -MathHelper.sin(yawRad));
        Vector3d muzzle = eye.add(dir.scale(0.85D)).add(right.scale(0.28D)).add(0.0D, -0.32D, 0.0D);

        // вспышка
        world.addParticle(ParticleTypes.FLAME, muzzle.x, muzzle.y, muzzle.z,
                dir.x * 0.05D, dir.y * 0.05D, dir.z * 0.05D);
        world.addParticle(ParticleTypes.SMOKE, muzzle.x, muzzle.y, muzzle.z,
                dir.x * 0.02D, 0.02D, dir.z * 0.02D);
        if (rand.nextInt(2) == 0) {
            world.addParticle(ParticleTypes.CRIT, muzzle.x, muzzle.y, muzzle.z,
                    dir.x * 0.3D, dir.y * 0.3D, dir.z * 0.3D);
        }

        // трассер — короткоживущие светлые точки вдоль траектории
        if (msg.tracer) {
            Vector3d path = end.subtract(muzzle);
            double plen = path.length();
            int steps = (int) Math.min(80, plen / 0.5D);
            double offset = rand.nextDouble() * 0.5D;
            for (int i = 1; i < steps; i++) {
                double t = (i * 0.5D + offset) / plen;
                if (t >= 1.0D) {
                    break;
                }
                Vector3d p = muzzle.add(path.scale(t));
                world.addParticle(TRACER, p.x, p.y, p.z, 0.0D, 0.0D, 0.0D);
            }
        }

        // попадание
        if (msg.hitType == BotShotPacket.HIT_BLOCK) {
            BlockPos pos = new BlockPos(end.add(dir.scale(0.05D)));
            BlockState state = world.getBlockState(pos);
            if (!state.isAir()) {
                for (int i = 0; i < 4; i++) {
                    world.addParticle(new BlockParticleData(ParticleTypes.BLOCK, state), end.x, end.y, end.z,
                            (rand.nextDouble() - 0.5D) * 0.3D - dir.x * 0.2D, rand.nextDouble() * 0.3D,
                            (rand.nextDouble() - 0.5D) * 0.3D - dir.z * 0.2D);
                }
                world.playSound(end.x, end.y, end.z, state.getSoundType().getHitSound(), SoundCategory.BLOCKS,
                        0.5F, 1.4F + rand.nextFloat() * 0.3F, false);
            }
            world.addParticle(ParticleTypes.SMOKE, end.x, end.y, end.z, 0.0D, 0.03D, 0.0D);
        } else if (msg.hitType == BotShotPacket.HIT_ENTITY) {
            world.addParticle(ParticleTypes.DAMAGE_INDICATOR, end.x, end.y, end.z, 0.0D, 0.1D, 0.0D);
        }
    }
}
