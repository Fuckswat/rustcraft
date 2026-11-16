package rustcraft.guns.client;

import java.util.Random;

import net.minecraft.block.BlockState;
import net.minecraft.client.Minecraft;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.entity.Entity;
import net.minecraft.particles.BlockParticleData;
import net.minecraft.particles.ParticleTypes;
import net.minecraft.particles.RedstoneParticleData;
import net.minecraft.util.HandSide;
import net.minecraft.util.SoundCategory;
import net.minecraft.util.SoundEvents;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.vector.Vector3d;
import rustcraft.guns.GunNetwork;

/** Визуальные эффекты выстрела на клиенте: вспышка, трассеры, искры/крошка/кровь в точке попадания. */
public final class GunClientFx {
    private GunClientFx() {}

    private static final RedstoneParticleData TRACER = new RedstoneParticleData(1.0F, 0.86F, 0.5F, 0.35F);
    private static final RedstoneParticleData BLOOD = new RedstoneParticleData(0.55F, 0.02F, 0.02F, 1.0F);

    public static void onShot(GunNetwork.ShotFxMsg m) {
        Minecraft mc = Minecraft.getInstance();
        ClientWorld world = mc.world;
        if (world == null) return;
        Random rand = world.rand;
        Entity shooter = world.getEntityByID(m.shooterId);
        boolean local = shooter != null && shooter == mc.player;

        Vector3d muzzle = new Vector3d(m.sx, m.sy, m.sz);
        Vector3d look = null;
        if (shooter != null) {
            look = Vector3d.fromPitchYaw(shooter.rotationPitch, shooter.rotationYaw);
            Vector3d right = Vector3d.fromPitchYaw(0.0F, shooter.rotationYaw + 90.0F);
            boolean leftHanded = local && mc.gameSettings.mainHand == HandSide.LEFT;
            double side = leftHanded ? -1.0 : 1.0;
            boolean firstPerson = local && mc.gameSettings.getPointOfView().func_243192_a();
            Vector3d eye = shooter.getEyePosition(1.0F);
            if (firstPerson) {
                muzzle = eye.add(look.scale(1.1)).add(right.scale(0.28 * side)).add(0, -0.22, 0);
            } else {
                muzzle = eye.add(look.scale(1.0)).add(right.scale(0.38 * side)).add(0, -0.38, 0);
            }
        }

        // вспышка у дула
        Vector3d fl = look != null ? look : Vector3d.ZERO;
        for (int i = 0; i < 2; i++) {
            world.addParticle(ParticleTypes.FLAME, muzzle.x, muzzle.y, muzzle.z,
                    fl.x * 0.03 + rand.nextGaussian() * 0.005, fl.y * 0.03 + 0.005, fl.z * 0.03 + rand.nextGaussian() * 0.005);
        }
        for (int i = 0; i < 2; i++) {
            world.addParticle(ParticleTypes.SMOKE, muzzle.x + fl.x * 0.2, muzzle.y + fl.y * 0.2, muzzle.z + fl.z * 0.2,
                    fl.x * 0.02, 0.02, fl.z * 0.02);
        }
        if (!local || !mc.gameSettings.getPointOfView().func_243192_a()) {
            world.addParticle(ParticleTypes.CRIT, muzzle.x, muzzle.y, muzzle.z, fl.x * 0.6, fl.y * 0.6, fl.z * 0.6);
        }

        int n = m.types.length;
        boolean hitAny = false, hitHead = false, blockSound = false;
        for (int i = 0; i < n; i++) {
            Vector3d end = new Vector3d(m.ends[i * 3], m.ends[i * 3 + 1], m.ends[i * 3 + 2]);
            Vector3d d = end.subtract(muzzle);
            double len = d.length();
            if (len < 0.01) continue;
            Vector3d dir = d.scale(1.0 / len);

            // трассер
            double step = n > 1 ? 2.5 : 1.4;
            int count = (int) Math.min(48, len / step);
            double off = 1.2 + rand.nextDouble() * step;
            for (int k = 0; k < count && off < len; k++, off += step) {
                world.addParticle(TRACER, true, muzzle.x + dir.x * off, muzzle.y + dir.y * off, muzzle.z + dir.z * off, 0, 0, 0);
            }
            if (n == 1 || i % 3 == 0) {
                double o2 = 2.0 + rand.nextDouble() * 2.0;
                if (o2 < len) {
                    world.addParticle(ParticleTypes.CRIT, true, muzzle.x + dir.x * o2, muzzle.y + dir.y * o2, muzzle.z + dir.z * o2,
                            dir.x * 2.5, dir.y * 2.5, dir.z * 2.5);
                }
            }

            byte type = m.types[i];
            if (type == GunNetwork.ShotFxMsg.HIT_BLOCK) {
                BlockPos pos = new BlockPos(end.add(dir.scale(0.05)));
                BlockState state = world.getBlockState(pos);
                if (!state.isAir(world, pos)) {
                    BlockParticleData data = new BlockParticleData(ParticleTypes.BLOCK, state);
                    for (int k = 0; k < (n > 1 ? 2 : 6); k++) {
                        world.addParticle(data, end.x, end.y, end.z,
                                -dir.x * 0.15 + rand.nextGaussian() * 0.08, 0.12 + rand.nextDouble() * 0.1,
                                -dir.z * 0.15 + rand.nextGaussian() * 0.08);
                    }
                    if (!blockSound) {
                        blockSound = true;
                        world.playSound(end.x, end.y, end.z, state.getSoundType().getHitSound(), SoundCategory.BLOCKS,
                                0.7F, 1.4F + rand.nextFloat() * 0.3F, false);
                    }
                }
                world.addParticle(ParticleTypes.SMOKE, end.x - dir.x * 0.05, end.y - dir.y * 0.05, end.z - dir.z * 0.05, 0, 0.01, 0);
                if (rand.nextInt(3) == 0) {
                    world.addParticle(ParticleTypes.CRIT, end.x - dir.x * 0.05, end.y - dir.y * 0.05, end.z - dir.z * 0.05,
                            -dir.x * 0.3, 0.2, -dir.z * 0.3);
                }
            } else if (type == GunNetwork.ShotFxMsg.HIT_ENTITY || type == GunNetwork.ShotFxMsg.HIT_HEAD) {
                hitAny = true;
                if (type == GunNetwork.ShotFxMsg.HIT_HEAD) hitHead = true;
                for (int k = 0; k < (n > 1 ? 3 : 8); k++) {
                    world.addParticle(BLOOD, end.x + rand.nextGaussian() * 0.08, end.y + rand.nextGaussian() * 0.08,
                            end.z + rand.nextGaussian() * 0.08, 0, 0, 0);
                }
            }
        }

        if (local && hitAny && mc.player != null) {
            GunHud.onHit(hitHead);
            mc.player.playSound(SoundEvents.ENTITY_ARROW_HIT_PLAYER, 0.35F, hitHead ? 1.9F : 1.3F);
            if (hitHead) mc.player.playSound(SoundEvents.ENTITY_PLAYER_ATTACK_CRIT, 0.6F, 1.4F);
        }
    }
}
