package rustcraft.vehicles.entity;

import net.minecraft.entity.EntityType;
import net.minecraft.entity.MoverType;
import net.minecraft.item.Item;
import net.minecraft.particles.ParticleTypes;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.vector.Vector3d;
import net.minecraft.world.World;
import rustcraft.vehicles.VehicleUtil;
import rustcraft.vehicles.VehiclesModule;

/**
 * RHIB — быстрая моторная лодка: водитель + 3 пассажира.
 * W/S — газ/реверс, A/D — руль. Тянет только в воде; на суше еле ползёт (скребёт днищем).
 */
public class RhibEntity extends AbstractVehicleEntity {
    private static final double[][] SEATS = {
            {0.0D, 0.75D, -0.31D},    // driver behind the console
            {0.31D, 0.625D, -0.94D},
            {-0.31D, 0.625D, -0.94D},
            {0.0D, 0.5625D, 1.19D},   // bow
    };
    private static final double FLOAT_DEPTH = 0.34D;

    private boolean wasInWater;

    public RhibEntity(EntityType<? extends RhibEntity> type, World world) {
        super(type, world);
    }

    @Override public float getMaxHealth() { return 200.0F; }
    @Override public float getFuelCapacity() { return 50 * 100.0F; }
    @Override protected float getFuelBurn() { return 0.3F; }
    @Override public int getMaxPassengers() { return 4; }
    @Override protected double[] getSeat(int index) { return SEATS[Math.min(index, SEATS.length - 1)]; }
    @Override public Item getVehicleItem() { return VehiclesModule.RHIB_ITEM.get(); }
    @Override protected float getExplosionPower() { return 2.5F; }
    @Override protected int getScrapDrop() { return 6; }
    @Override protected int getFragmentDrop() { return 40; }
    @Override public boolean isHelicopter() { return false; }
    @Override protected float getSpoolUp() { return 0.05F; }
    @Override protected float getSpoolDown() { return 0.03F; }
    @Override protected double getCrashThreshold() { return 0.55D; }
    @Override protected double getCrashDamageFactor() { return 60.0D; }
    @Override protected double[] getEnginePos() { return new double[]{0.0D, 1.0D, -2.0D}; }
    @Override protected float getRotorDegreesPerTick() { return 60.0F; }
    @Override protected double getDrownDepth() { return 1.1D; }

    @Override
    protected float fuelLoadFactor() {
        return 0.25F + 0.75F * Math.abs(throttle);
    }

    @Override
    protected void simulate() {
        Vector3d m = getMotion();
        double mx = m.x, my = m.y, mz = m.z;
        if (!VehicleUtil.finite(mx, my, mz)) {
            mx = my = mz = 0.0D;
        }
        double surface = waterSurface();
        double depth = Double.isNaN(surface) ? -1.0D : surface - getPosY();
        boolean inWater = depth > 0.02D;
        wasInWater = inWater;

        float thr = (inForward ? 1.0F : 0.0F) - (inBack ? 0.5F : 0.0F);
        float steer = (inRight ? 1.0F : 0.0F) - (inLeft ? 1.0F : 0.0F);
        boolean powered = isEngineOn() && rotorSpeed > 0.3F;
        throttle = powered ? thr : 0.0F;

        double yaw = Math.toRadians(this.rotationYaw);
        double fx = -Math.sin(yaw), fz = Math.cos(yaw);
        double fwd = mx * fx + mz * fz;

        // ---- steering ----
        float steerAuthority = inWater ? VehicleUtil.clamp((float) Math.abs(fwd) / 0.35F, 0.3F, 1.0F) : 0.0F;
        float dir = fwd < -0.02D ? -1.0F : 1.0F;
        float targetYawVel = steer * 3.6F * steerAuthority * dir;
        yawVelocity += (targetYawVel - yawVelocity) * 0.25F;
        if (Math.abs(yawVelocity) < 0.001F) yawVelocity = 0.0F;
        this.rotationYaw += yawVelocity;

        yaw = Math.toRadians(this.rotationYaw);
        fx = -Math.sin(yaw);
        fz = Math.cos(yaw);
        double rx = -Math.cos(yaw), rz = -Math.sin(yaw);
        fwd = mx * fx + mz * fz;
        double side = mx * rx + mz * rz;

        if (inWater) {
            if (powered) fwd += thr * 0.028D * rotorSpeed;
            fwd *= 0.968D;
            side *= 0.8D;
            // buoyancy spring towards the float line + damping
            my += (depth - FLOAT_DEPTH) * 0.12D;
            if (depth > 1.2D) my += 0.04D;
            my *= 0.75D;
        } else {
            my = (my - 0.06D) * 0.98D;
            if (this.onGround) {
                // beached: scrape slowly, the prop barely helps
                fwd *= 0.5D;
                side *= 0.5D;
                if (powered) fwd += thr * 0.006D;
            } else {
                fwd *= 0.995D;
                side *= 0.95D;
            }
        }
        fwd = VehicleUtil.clamp(fwd, -0.35D, 1.0D);
        side = VehicleUtil.clamp(side, -1.0D, 1.0D);
        my = VehicleUtil.clamp(my, -2.0D, 1.0D);

        // ---- visuals: bow lifts with speed, bank into turns ----
        float targetPitch = inWater ? (float) (-fwd * 9.0D) - (powered && thr > 0 ? 1.5F : 0.0F) : 0.0F;
        this.rotationPitch = MathHelper.approach(this.rotationPitch, targetPitch, 0.8F);
        float targetRoll = inWater ? steer * (float) Math.abs(fwd) * 12.0F : 0.0F;
        this.roll += (targetRoll - this.roll) * 0.2F;

        mx = fx * fwd + rx * side;
        mz = fz * fwd + rz * side;
        Vector3d before = new Vector3d(mx, my, mz);
        this.setMotion(before);
        this.move(MoverType.SELF, before);
        Vector3d after = getMotion();
        double hLost = 0.0D;
        if (this.collidedHorizontally) {
            double hb = Math.sqrt(before.x * before.x + before.z * before.z);
            double ha = Math.sqrt(after.x * after.x + after.z * after.z);
            hLost = Math.max(0.0D, hb - ha);
        }
        double vLost = this.collidedVertically && before.y < 0 ? -before.y : 0.0D;
        registerImpact(Math.max(hLost, vLost));
    }

    @Override
    protected void clientEffects() {
        super.clientEffects();
        double dx = getPosX() - this.prevPosX, dz = getPosZ() - this.prevPosZ;
        double speed = Math.sqrt(dx * dx + dz * dz);
        double surface = waterSurface();
        if (Double.isNaN(surface) || surface - getPosY() < 0.02D) return;
        if (speed > 0.12D) {
            // wake behind the stern + spray from the bow
            int n = speed > 0.5D ? 4 : 2;
            for (int i = 0; i < n; i++) {
                Vector3d p = localToWorld((rand.nextDouble() - 0.5D) * 1.6D, 0.0D, -2.2D - rand.nextDouble() * 0.6D);
                this.world.addParticle(ParticleTypes.SPLASH, p.x, surface + 0.05D, p.z, 0.0D, 0.1D, 0.0D);
                this.world.addParticle(ParticleTypes.BUBBLE, p.x, surface - 0.3D, p.z, 0.0D, 0.0D, 0.0D);
            }
            for (int s = -1; s <= 1; s += 2) {
                Vector3d p = localToWorld(s * 1.15D, 0.0D, 1.6D);
                this.world.addParticle(ParticleTypes.SPLASH, p.x, surface + 0.1D, p.z, -dx * 0.5D, 0.15D, -dz * 0.5D);
            }
            if (rand.nextInt(3) == 0) {
                Vector3d p = localToWorld((rand.nextDouble() - 0.5D) * 2.0D, 0.0D, -3.0D);
                this.world.addParticle(ParticleTypes.FISHING, p.x, surface, p.z, 0.0D, 0.0D, 0.0D);
            }
        } else if (isEngineOn() && rand.nextInt(4) == 0) {
            Vector3d p = localToWorld(0.0D, 0.0D, -2.1D);
            this.world.addParticle(ParticleTypes.BUBBLE, p.x, surface - 0.2D, p.z, 0.0D, 0.02D, 0.0D);
        }
    }

    public boolean wasInWater() {
        return wasInWater;
    }
}
