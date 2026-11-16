package rustcraft.vehicles.entity;

import net.minecraft.entity.EntityType;
import net.minecraft.entity.MoverType;
import net.minecraft.particles.ParticleTypes;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.vector.Vector3d;
import net.minecraft.world.World;
import rustcraft.vehicles.VehicleUtil;

/**
 * Аркадная модель полёта вертолёта.
 * W/S — наклон носа (тяга вперёд/назад вдоль курса), A/D — рыскание с креном,
 * Space — набор высоты, Left Alt / Left Ctrl — снижение. Без ввода — автоматическое зависание
 * (контроллер вертикальной скорости), если ротор раскручен. Без пилота — плавная автопосадка.
 */
public abstract class AbstractHeliEntity extends AbstractVehicleEntity {
    protected static final double GRAVITY = 0.055D;
    private static final double FORWARD_DRAG = 0.97D;
    private static final double SIDE_DRAG = 0.88D;

    protected AbstractHeliEntity(EntityType<?> type, World world) {
        super(type, world);
    }

    /** Max horizontal speed, blocks/tick. */
    protected abstract double getMaxSpeed();
    protected abstract double getClimbRate();
    protected abstract double getDescendRate();
    protected abstract float getMaxYawRate();
    protected abstract float getMaxPitch();
    protected abstract float getPitchRate();

    @Override
    public boolean isHelicopter() {
        return true;
    }

    @Override
    protected double getCrashThreshold() {
        return 0.36D;
    }

    @Override
    protected float getRotorDegreesPerTick() {
        return 47.0F;
    }

    @Override
    protected float fuelLoadFactor() {
        return 0.6F + 0.4F * Math.abs(throttle);
    }

    @Override
    protected void simulate() {
        float rotor = this.rotorSpeed;
        boolean grounded = this.onGround;
        boolean piloted = hasPilot();
        Vector3d m = getMotion();
        double mx = m.x, my = m.y, mz = m.z;
        if (!VehicleUtil.finite(mx, my, mz)) {
            mx = my = mz = 0.0D;
        }

        // ---- yaw (A/D) ----
        float yawIn = (inRight ? 1.0F : 0.0F) - (inLeft ? 1.0F : 0.0F);
        float yawAuthority = grounded ? (rotor > 0.7F ? 0.5F : 0.0F) : rotor;
        float targetYawVel = yawIn * getMaxYawRate() * yawAuthority;
        yawVelocity += (targetYawVel - yawVelocity) * 0.2F;
        if (Math.abs(yawVelocity) < 0.001F) yawVelocity = 0.0F;
        this.rotationYaw += yawVelocity;

        // ---- pitch (W/S) ----
        float pitchIn = (inForward ? 1.0F : 0.0F) - (inBack ? 1.0F : 0.0F);
        float targetPitch = 0.0F;
        if (!grounded && rotor > 0.2F) {
            targetPitch = pitchIn > 0 ? getMaxPitch() : (pitchIn < 0 ? -getMaxPitch() * 0.6F : 0.0F);
        }
        this.rotationPitch = MathHelper.approach(this.rotationPitch, targetPitch, getPitchRate());

        double yaw = Math.toRadians(this.rotationYaw);
        double fx = -Math.sin(yaw), fz = Math.cos(yaw);
        double rx = -Math.cos(yaw), rz = -Math.sin(yaw);
        double fwd = mx * fx + mz * fz;
        double side = mx * rx + mz * rz;

        // ---- roll (visual bank into the turn) ----
        float targetRoll = grounded ? 0.0F : yawIn * 16.0F * rotor + (float) (-side * 10.0D);
        this.roll += (VehicleUtil.clamp(targetRoll, -30.0F, 30.0F) - this.roll) * 0.15F;

        // ---- vertical ----
        float vertIn = (inUp ? 1.0F : 0.0F) - (inDown ? 1.0F : 0.0F);
        double lift = 0.0D;
        if (rotor > 0.05F) {
            double targetVy;
            if (!piloted) targetVy = -0.18D;                  // auto-landing when the pilot bailed out
            else if (vertIn > 0) targetVy = getClimbRate();
            else if (vertIn < 0) targetVy = -getDescendRate();
            else targetVy = 0.0D;                               // hover assist
            double thrust = GRAVITY + (targetVy - my) * 0.18D;
            thrust = VehicleUtil.clamp(thrust, 0.0D, GRAVITY + 0.1D);
            lift = thrust * rotor * rotor;
        }
        my = (my - GRAVITY + lift) * 0.98D;

        // ---- horizontal ----
        double pitchRad = Math.toRadians(this.rotationPitch);
        double accel = 0.0D;
        if (!grounded) {
            accel = Math.sin(pitchRad) / Math.sin(Math.toRadians(getMaxPitch())) * getMaxSpeed() * (1.0D - FORWARD_DRAG) * rotor;
        }
        fwd = fwd * FORWARD_DRAG + accel;
        side = side * SIDE_DRAG;
        if (grounded) {
            fwd *= 0.6D;
            side *= 0.6D;
        }
        double maxH = getMaxSpeed() * 1.15D;
        fwd = VehicleUtil.clamp(fwd, -maxH, maxH);
        side = VehicleUtil.clamp(side, -maxH, maxH);

        // ---- water: skids float, engine dies if submerged (handled server side) ----
        double surface = waterSurface();
        if (!Double.isNaN(surface)) {
            double depth = surface - getPosY();
            if (depth > 0.0D) {
                my += Math.min(depth, 1.5D) * 0.09D;
                my *= 0.7D;
                fwd *= 0.85D;
                side *= 0.8D;
            }
        }

        mx = fx * fwd + rx * side;
        mz = fz * fwd + rz * side;
        my = VehicleUtil.clamp(my, -2.5D, 1.0D);
        throttle = pitchIn != 0 ? pitchIn : vertIn;

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
        double vLost = this.collidedVertically ? Math.abs(before.y) : 0.0D;
        registerImpact(Math.max(hLost, vLost));
        if (this.onGround && getMotion().y < 0) {
            this.setMotion(getMotion().x, 0.0D, getMotion().z);
        }
    }

    @Override
    protected void clientEffects() {
        super.clientEffects();
        // rotor wash over the ground / water below
        if (rotorSpeed > 0.6F) {
            double agl = getAltitudeAboveGround();
            if (agl < 6.0D) {
                double groundY = getPosY() - agl + 0.1D;
                int n = agl < 3.0D ? 3 : 1;
                for (int i = 0; i < n; i++) {
                    double a = this.rand.nextDouble() * Math.PI * 2.0D;
                    double r = 1.5D + this.rand.nextDouble() * 2.0D;
                    this.world.addParticle(ParticleTypes.POOF,
                            getPosX() + Math.cos(a) * r, groundY, getPosZ() + Math.sin(a) * r,
                            Math.cos(a) * 0.25D, 0.02D, Math.sin(a) * 0.25D);
                }
            }
        }
    }
}
