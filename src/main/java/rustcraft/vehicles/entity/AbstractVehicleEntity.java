package rustcraft.vehicles.entity;

import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.MoverType;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.player.ServerPlayerEntity;
import net.minecraft.fluid.FluidState;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.nbt.CompoundNBT;
import net.minecraft.network.IPacket;
import net.minecraft.network.datasync.DataParameter;
import net.minecraft.network.datasync.DataSerializers;
import net.minecraft.network.datasync.EntityDataManager;
import net.minecraft.particles.ParticleTypes;
import net.minecraft.tags.FluidTags;
import net.minecraft.util.ActionResultType;
import net.minecraft.util.DamageSource;
import net.minecraft.util.Hand;
import net.minecraft.util.SoundCategory;
import net.minecraft.util.SoundEvents;
import net.minecraft.util.math.AxisAlignedBB;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.RayTraceResult;
import net.minecraft.util.math.vector.Vector3d;
import net.minecraft.util.text.TranslationTextComponent;
import net.minecraft.world.Explosion;
import net.minecraft.world.World;
import net.minecraft.world.gen.Heightmap;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.fml.network.NetworkHooks;
import net.minecraftforge.fml.network.PacketDistributor;
import rustcraft.vehicles.VehicleUtil;
import rustcraft.vehicles.network.CorrectionPacket;
import rustcraft.vehicles.network.StatePacket;
import rustcraft.vehicles.network.VehicleNetwork;

import javax.annotation.Nullable;
import java.util.List;
import java.util.UUID;

/**
 * Базовый класс транспорта.
 *
 * Сетевая модель: клиент пилота симулирует движение сам (как ванильная лодка) и каждый тик
 * отправляет своё состояние серверу собственным пакетом {@link StatePacket} по каналу rustcraft:vehicles.
 * Сервер проверяет пакет (дистанция, загруженность чанка, коллизии) и принимает позицию,
 * остальные клиенты получают её обычным трекером сущностей и плавно интерполируют.
 * canPassengerSteer() намеренно false: ванильный CMoveVehiclePacket не используется,
 * поэтому сервер с allow-flight=false не кикает пилота за "floating a vehicle too long".
 * Без пилота физику считает сервер.
 */
public abstract class AbstractVehicleEntity extends Entity {
    private static final DataParameter<Float> HEALTH = EntityDataManager.createKey(AbstractVehicleEntity.class, DataSerializers.FLOAT);
    private static final DataParameter<Float> FUEL = EntityDataManager.createKey(AbstractVehicleEntity.class, DataSerializers.FLOAT);
    private static final DataParameter<Boolean> ENGINE = EntityDataManager.createKey(AbstractVehicleEntity.class, DataSerializers.BOOLEAN);
    private static final DataParameter<Float> ROLL = EntityDataManager.createKey(AbstractVehicleEntity.class, DataSerializers.FLOAT);
    private static final DataParameter<Float> THROTTLE = EntityDataManager.createKey(AbstractVehicleEntity.class, DataSerializers.FLOAT);
    private static final DataParameter<Integer> HURT_TIME = EntityDataManager.createKey(AbstractVehicleEntity.class, DataSerializers.VARINT);

    public static final int ACTION_ENGINE_TOGGLE = 0;
    public static final int ACTION_ENGINE_START = 1;

    // --- input of the local pilot (client only) ---
    protected boolean inForward, inBack, inLeft, inRight, inUp, inDown;

    // --- simulation state ---
    protected float rotorSpeed, prevRotorSpeed;   // 0..1 engine/rotor spool
    protected float rotorAngle, prevRotorAngle;   // visual, degrees
    protected float roll, prevRoll;               // visual bank, degrees (+ = right side down)
    protected float yawVelocity;
    protected float throttle;                     // -1..1 for boats, vertical/forward input for helis (visual/sound)
    private float deltaYaw;
    private float pendingImpact;

    // --- client interpolation ---
    private int lerpSteps;
    private double lerpX, lerpY, lerpZ;
    private float lerpYaw, lerpPitch;

    // --- server ---
    private int noPilotTicks;
    private int groundedIdleTicks;
    private boolean destroyed;
    private long movementBudgetTick = Long.MIN_VALUE;
    private double horizontalBudget = 2.2D, verticalBudget = 3.0D;
    private long lastActionTick = Long.MIN_VALUE;
    @Nullable private UUID owner;

    /** Client: engine sound instance is alive (managed by client code). */
    public boolean soundActive;

    protected AbstractVehicleEntity(EntityType<?> type, World world) {
        super(type, world);
        this.preventEntitySpawning = true;
    }

    // Per-vehicle configuration
    public abstract float getMaxHealth();
    /** Tank capacity in units (1 fuel item = {@link VehicleUtil#UNITS_PER_FUEL_ITEM}). */
    public abstract float getFuelCapacity();
    /** Units burned per tick at full load. */
    protected abstract float getFuelBurn();
    public abstract int getMaxPassengers();
    /** Seat i: {right, butt height above vehicle origin, forward} in blocks. */
    protected abstract double[] getSeat(int index);
    public abstract Item getVehicleItem();
    protected abstract float getExplosionPower();
    protected abstract int getScrapDrop();
    protected abstract int getFragmentDrop();
    public abstract boolean isHelicopter();
    /** Physics step on the authoritative side. Uses the in* inputs. */
    protected abstract void simulate();
    protected abstract float getSpoolUp();
    protected abstract float getSpoolDown();
    /** Impact speed (blocks/tick) above which a collision damages the vehicle. */
    protected abstract double getCrashThreshold();
    protected abstract double getCrashDamageFactor();
    /** Local position of the engine (for smoke): {right, up, forward}. */
    protected abstract double[] getEnginePos();
    protected abstract float getRotorDegreesPerTick();

    // Data
    @Override
    protected void registerData() {
        this.dataManager.register(HEALTH, getMaxHealth());
        this.dataManager.register(FUEL, 0.0F);
        this.dataManager.register(ENGINE, false);
        this.dataManager.register(ROLL, 0.0F);
        this.dataManager.register(THROTTLE, 0.0F);
        this.dataManager.register(HURT_TIME, 0);
    }

    public float getHealth() { return this.dataManager.get(HEALTH); }
    public void setHealth(float h) { this.dataManager.set(HEALTH, VehicleUtil.finite(h) ? VehicleUtil.clamp(h, 0.0F, getMaxHealth()) : getMaxHealth()); }
    public float getFuel() { return this.dataManager.get(FUEL); }
    public void setFuel(float f) { this.dataManager.set(FUEL, VehicleUtil.finite(f) ? VehicleUtil.clamp(f, 0.0F, getFuelCapacity()) : 0.0F); }
    public boolean isEngineOn() { return this.dataManager.get(ENGINE); }
    public void setEngineOn(boolean on) { this.dataManager.set(ENGINE, on); }
    public int getHurtTime() { return this.dataManager.get(HURT_TIME); }
    public void setHurtTime(int t) { this.dataManager.set(HURT_TIME, t); }
    public float getSyncedThrottle() { return this.dataManager.get(THROTTLE); }

    public float getRotorSpeed() { return rotorSpeed; }
    public float getRotorSpeed(float pt) { return MathHelper.lerp(pt, prevRotorSpeed, rotorSpeed); }
    public float getRotorAngle(float pt) { return MathHelper.lerp(pt, prevRotorAngle, rotorAngle); }
    public float getRoll(float pt) { return MathHelper.lerp(pt, prevRoll, roll); }
    /** Throttle for visuals/sound on any client. */
    public float getThrottleVisual() { return isClientPiloted() ? throttle : getSyncedThrottle(); }

    @Nullable public UUID getOwner() { return owner; }
    public void setOwner(@Nullable UUID owner) { this.owner = owner; }

    public void setInputs(boolean forward, boolean back, boolean left, boolean right, boolean up, boolean down) {
        this.inForward = forward;
        this.inBack = back;
        this.inLeft = left;
        this.inRight = right;
        this.inUp = up;
        this.inDown = down;
    }

    protected void clearInputs() {
        setInputs(false, false, false, false, false, false);
    }

    // Riding
    @Nullable
    @Override
    public Entity getControllingPassenger() {
        List<Entity> list = this.getPassengers();
        if (list.isEmpty()) return null;
        Entity first = list.get(0);
        return first instanceof PlayerEntity ? first : null;
    }

    public boolean hasPilot() {
        return getControllingPassenger() != null;
    }

    /** True on the client of the player who pilots this vehicle: that client simulates. */
    public boolean isClientPiloted() {
        if (!this.world.isRemote) return false;
        Entity c = getControllingPassenger();
        return c instanceof PlayerEntity && ((PlayerEntity) c).isUser();
    }

    /** Vanilla vehicle-move packets are not used (see class comment). */
    @Override
    public boolean canPassengerSteer() {
        return false;
    }

    @Override
    protected boolean canFitPassenger(Entity passenger) {
        return this.getPassengers().size() < getMaxPassengers();
    }

    @Override
    public void updatePassenger(Entity passenger) {
        if (!this.isPassenger(passenger)) return;
        int idx = Math.max(0, this.getPassengers().indexOf(passenger));
        double[] seat = getSeat(Math.min(idx, getMaxPassengers() - 1));
        double yaw = Math.toRadians(this.rotationYaw);
        double pitch = Math.toRadians(this.rotationPitch);
        double fwd = seat[2] * Math.cos(pitch);
        double up = seat[1] - seat[2] * Math.sin(pitch);
        double right = seat[0];
        double x = this.getPosX() - Math.sin(yaw) * fwd - Math.cos(yaw) * right;
        double z = this.getPosZ() + Math.cos(yaw) * fwd - Math.sin(yaw) * right;
        double y = this.getPosY() + up - 0.4D + passenger.getYOffset();
        passenger.setPosition(x, y, z);
    }

    @Override
    public double getMountedYOffset() {
        return getSeat(0)[1] - 0.4D;
    }

    protected void clampPassengerYaw(Entity passenger) {
        passenger.setRenderYawOffset(this.rotationYaw);
        float f = MathHelper.wrapDegrees(passenger.rotationYaw - this.rotationYaw);
        float f1 = MathHelper.clamp(f, -120.0F, 120.0F);
        passenger.prevRotationYaw += f1 - f;
        passenger.rotationYaw += f1 - f;
        passenger.setRotationYawHead(passenger.rotationYaw);
    }

    @Override
    public void applyOrientationToEntity(Entity passenger) {
        clampPassengerYaw(passenger);
    }

    @Override
    public Vector3d func_230268_c_(LivingEntity rider) {
        // Dismount position: next to the seat side, then the other side, then behind / in front.
        int idx = Math.max(0, this.getPassengers().indexOf(rider));
        double[] seat = getSeat(Math.min(idx, getMaxPassengers() - 1));
        double yaw = Math.toRadians(this.rotationYaw);
        double fx = -Math.sin(yaw), fz = Math.cos(yaw);
        double rx = -Math.cos(yaw), rz = -Math.sin(yaw);
        double side = this.getWidth() * 0.5D + 0.5D;
        double sign = seat[0] >= 0 ? 1.0D : -1.0D;
        double[][] offsets = {
                {sign * side, seat[2]}, {-sign * side, seat[2]}, {0, -side - 0.6D}, {0, side + 0.6D}
        };
        double w = rider.getWidth() * 0.5D;
        double h = rider.getHeight();
        for (double[] o : offsets) {
            double cx = this.getPosX() + rx * o[0] + fx * o[1];
            double cz = this.getPosZ() + rz * o[0] + fz * o[1];
            for (double dy = 0.0D; dy <= 2.0D; dy += 0.5D) {
                double cy = this.getPosY() + dy;
                AxisAlignedBB bb = new AxisAlignedBB(cx - w, cy, cz - w, cx + w, cy + h, cz + w);
                if (this.world.hasNoCollisions(rider, bb)) {
                    return new Vector3d(cx, cy, cz);
                }
            }
        }
        return super.func_230268_c_(rider);
    }

    @Override
    public ActionResultType processInitialInteract(PlayerEntity player, Hand hand) {
        if (player.isSpectator()) return ActionResultType.PASS;
        ItemStack stack = player.getHeldItem(hand);
        if (VehicleUtil.isFuel(stack)) {
            if (!this.world.isRemote) refuel(player, stack);
            return ActionResultType.func_233537_a_(this.world.isRemote);
        }
        if (player.isSecondaryUseActive()) {
            if (stack.isEmpty() && hand == Hand.MAIN_HAND) {
                if (!this.world.isRemote) tryPickup(player);
                return ActionResultType.func_233537_a_(this.world.isRemote);
            }
            return ActionResultType.PASS;
        }
        if (this.isPassenger(player) || this.getPassengers().size() >= getMaxPassengers()) {
            return ActionResultType.PASS;
        }
        if (!this.world.isRemote) {
            return player.startRiding(this) ? ActionResultType.CONSUME : ActionResultType.PASS;
        }
        return ActionResultType.SUCCESS;
    }

    private void refuel(PlayerEntity player, ItemStack stack) {
        float space = getFuelCapacity() - getFuel();
        int canTake = (int) Math.floor(space / VehicleUtil.UNITS_PER_FUEL_ITEM + 1.0E-4);
        if (canTake <= 0) {
            player.sendStatusMessage(new TranslationTextComponent("message.rustcraft.vehicle_tank_full"), true);
            return;
        }
        int n = Math.min(canTake, stack.getCount());
        setFuel(getFuel() + n * VehicleUtil.UNITS_PER_FUEL_ITEM);
        if (!player.abilities.isCreativeMode) stack.shrink(n);
        this.world.playSound(null, getPosX(), getPosY(), getPosZ(), SoundEvents.ITEM_BUCKET_EMPTY, SoundCategory.NEUTRAL, 0.8F, 0.8F);
        player.sendStatusMessage(new TranslationTextComponent("message.rustcraft.vehicle_refueled",
                n, Math.round(getFuel() * 100.0F / getFuelCapacity())), true);
    }

    private void tryPickup(PlayerEntity player) {
        if (!this.getPassengers().isEmpty()) {
            player.sendStatusMessage(new TranslationTextComponent("message.rustcraft.vehicle_not_empty"), true);
            return;
        }
        if (owner != null && !owner.equals(player.getUniqueID()) && !player.abilities.isCreativeMode) {
            player.sendStatusMessage(new TranslationTextComponent("message.rustcraft.vehicle_not_owner"), true);
            return;
        }
        if (getMotion().lengthSquared() > 0.04D) return;
        ItemStack item = new ItemStack(getVehicleItem());
        CompoundNBT tag = item.getOrCreateChildTag("VehicleData");
        tag.putFloat("Health", getHealth());
        tag.putFloat("Fuel", getFuel());
        if (!player.addItemStackToInventory(item)) {
            this.entityDropItem(item, 0.5F);
        }
        this.world.playSound(null, getPosX(), getPosY(), getPosZ(), SoundEvents.ITEM_ARMOR_EQUIP_IRON, SoundCategory.NEUTRAL, 1.0F, 0.8F);
        this.remove();
    }

    /** Apply data saved in a placement item. */
    public void loadFromItem(ItemStack stack) {
        CompoundNBT tag = stack.getChildTag("VehicleData");
        if (tag != null) {
            if (tag.contains("Health")) setHealth(tag.getFloat("Health"));
            if (tag.contains("Fuel")) setFuel(tag.getFloat("Fuel"));
        }
    }

    @Override
    public ItemStack getPickedResult(RayTraceResult target) {
        return new ItemStack(getVehicleItem());
    }

    // Tick
    @Override
    public void tick() {
        if (!this.world.isRemote && getHurtTime() > 0) setHurtTime(getHurtTime() - 1);
        super.tick();

        prevRotorSpeed = rotorSpeed;
        prevRotorAngle = rotorAngle;
        prevRoll = roll;
        float target = isEngineOn() ? 1.0F : 0.0F;
        if (rotorSpeed < target) rotorSpeed = Math.min(target, rotorSpeed + getSpoolUp());
        else rotorSpeed = Math.max(target, rotorSpeed - getSpoolDown());

        float yawBefore = this.rotationYaw;
        if (isClientPiloted()) {
            lerpSteps = 0;
            simulate();
            sanitize();
            sendState();
        } else if (this.world.isRemote) {
            tickLerp();
            roll += (this.dataManager.get(ROLL) - roll) * 0.35F;
        } else if (!hasPilot()) {
            clearInputs();
            simulate();
            sanitize();
            this.dataManager.set(ROLL, roll);
            this.dataManager.set(THROTTLE, 0.0F);
        }
        deltaYaw = MathHelper.wrapDegrees(this.rotationYaw - yawBefore);

        rotorAngle += rotorSpeed * getRotorDegreesPerTick();
        if (rotorAngle > 3600.0F) {
            rotorAngle -= 3600.0F;
            prevRotorAngle -= 3600.0F;
        }

        // passengers turn with the vehicle; never accumulate fall damage while seated
        for (Entity p : this.getPassengers()) {
            p.fallDistance = 0.0F;
            boolean rotate = this.world.isRemote ? (p instanceof PlayerEntity && ((PlayerEntity) p).isUser())
                    : !(p instanceof PlayerEntity);
            if (rotate && deltaYaw != 0.0F) {
                p.rotationYaw += deltaYaw;
                p.setRotationYawHead(p.getRotationYawHead() + deltaYaw);
            }
        }
        this.fallDistance = 0.0F;

        if (this.world.isRemote) clientEffects();
        else serverTick();
    }

    private void sanitize() {
        Vector3d m = getMotion();
        if (!VehicleUtil.finite(m.x, m.y, m.z)) setMotion(Vector3d.ZERO);
        if (!VehicleUtil.finite(rotationYaw, rotationPitch, roll, yawVelocity)) {
            rotationYaw = 0.0F;
            rotationPitch = 0.0F;
            roll = 0.0F;
            yawVelocity = 0.0F;
        }
        if (!VehicleUtil.finite(getPosX(), getPosY(), getPosZ())) {
            setPosition(lastTickPosX, lastTickPosY, lastTickPosZ);
        }
    }

    private void sendState() {
        Vector3d m = getMotion();
        VehicleNetwork.CHANNEL.sendToServer(new StatePacket(getEntityId(), getPosX(), getPosY(), getPosZ(),
                rotationYaw, rotationPitch, roll, m.x, m.y, m.z, throttle, pendingImpact));
        pendingImpact = 0.0F;
    }

    private void tickLerp() {
        if (lerpSteps > 0) {
            double x = getPosX() + (lerpX - getPosX()) / lerpSteps;
            double y = getPosY() + (lerpY - getPosY()) / lerpSteps;
            double z = getPosZ() + (lerpZ - getPosZ()) / lerpSteps;
            float yaw = this.rotationYaw + MathHelper.wrapDegrees(lerpYaw - this.rotationYaw) / lerpSteps;
            float pitch = this.rotationPitch + (lerpPitch - this.rotationPitch) / lerpSteps;
            --lerpSteps;
            this.setPosition(x, y, z);
            this.setRotation(yaw, pitch);
        }
    }

    @Override
    public void setPositionAndRotationDirect(double x, double y, double z, float yaw, float pitch, int steps, boolean teleport) {
        if (isClientPiloted()) return; // our own client is authoritative while piloting
        lerpX = x;
        lerpY = y;
        lerpZ = z;
        lerpYaw = yaw;
        lerpPitch = pitch;
        lerpSteps = 4;
    }

    @Override
    public void setVelocity(double x, double y, double z) {
        if (isClientPiloted()) return;
        this.setMotion(x, y, z);
    }

    /** Server correction for the pilot client (invalid move). */
    public void forceClientPosition(double x, double y, double z, float yaw) {
        this.setPosition(x, y, z);
        this.rotationYaw = yaw;
        this.prevRotationYaw = yaw;
        this.setMotion(Vector3d.ZERO);
        this.lerpSteps = 0;
    }

    /** Server: state reported by the pilot's client. */
    public void acceptClientState(ServerPlayerEntity sender, StatePacket p) {
        if (this.removed || getControllingPassenger() != sender) return;
        if (!VehicleUtil.finite(p.x, p.y, p.z, p.yaw, p.pitch, p.roll, p.mx, p.my, p.mz, p.throttle, p.impact)) return;
        long now = this.world.getGameTime();
        if (movementBudgetTick != now) {
            long elapsed = movementBudgetTick == Long.MIN_VALUE ? 1L : Math.max(0L, now - movementBudgetTick);
            horizontalBudget = Math.min(4.4D, horizontalBudget + elapsed * 1.25D);
            verticalBudget = Math.min(6.0D, verticalBudget + elapsed * 2.6D);
            movementBudgetTick = now;
        }
        double ox = getPosX(), oy = getPosY(), oz = getPosZ();
        double dx = p.x - ox, dy = p.y - oy, dz = p.z - oz;
        double horizontal = Math.sqrt(dx * dx + dz * dz);
        boolean ok = horizontal <= horizontalBudget && Math.abs(dy) <= verticalBudget;
        ok = ok && Math.abs(p.x) < 29999984.0D && Math.abs(p.z) < 29999984.0D && p.y > -64.0D && p.y < 512.0D;
        ok = ok && this.world.isBlockLoaded(new BlockPos(p.x, p.y, p.z));
        ok = ok && this.world.getWorldBorder().contains(this.getBoundingBox().offset(dx, dy, dz));
        if (ok && (dx != 0 || dy != 0 || dz != 0)) {
            // Sweep the complete path on the server; endpoint-only checks allow tunnelling through walls.
            this.move(MoverType.SELF, new Vector3d(dx, dy, dz));
            ok = Math.abs(getPosX() - p.x) < 0.02D && Math.abs(getPosY() - p.y) < 0.02D
                    && Math.abs(getPosZ() - p.z) < 0.02D;
        }
        if (!ok) {
            this.setPosition(ox, oy, oz);
            VehicleNetwork.CHANNEL.send(PacketDistributor.PLAYER.with(() -> sender),
                    new CorrectionPacket(getEntityId(), ox, oy, oz, rotationYaw));
            return;
        }
        horizontalBudget -= horizontal;
        verticalBudget -= Math.abs(dy);
        float impactLimit = (float) Math.min(4.0D, getMotion().length() + 0.12D);
        this.setPositionAndRotation(p.x, p.y, p.z, p.yaw, VehicleUtil.clamp(p.pitch, -60.0F, 60.0F));
        this.setMotion(VehicleUtil.clamp(p.mx, -1.5D, 1.5D), VehicleUtil.clamp(p.my, -2.6D, 1.0D), VehicleUtil.clamp(p.mz, -1.5D, 1.5D));
        this.roll = VehicleUtil.clamp(p.roll, -60.0F, 60.0F);
        this.throttle = VehicleUtil.clamp(p.throttle, -1.0F, 1.0F);
        this.dataManager.set(ROLL, this.roll);
        this.dataManager.set(THROTTLE, this.throttle);
        if (p.impact > 0) applyImpact(Math.min(p.impact, impactLimit));
    }

    /** Server: pilot pressed an engine key. */
    public void handleAction(ServerPlayerEntity sender, int action) {
        if (this.removed || getControllingPassenger() != sender) return;
        if (action != ACTION_ENGINE_TOGGLE && action != ACTION_ENGINE_START) return;
        long actionTick = this.world.getGameTime();
        if (lastActionTick != Long.MIN_VALUE && actionTick - lastActionTick < 4L) return;
        lastActionTick = actionTick;
        if (action == ACTION_ENGINE_TOGGLE && isEngineOn()) {
            setEngineOn(false);
            this.world.playSound(null, getPosX(), getPosY(), getPosZ(), SoundEvents.BLOCK_IRON_TRAPDOOR_CLOSE, SoundCategory.NEUTRAL, 1.0F, 0.6F);
            return;
        }
        if (isEngineOn()) return;
        if (getFuel() <= 0.0F) {
            sender.sendStatusMessage(new TranslationTextComponent("message.rustcraft.vehicle_no_fuel"), true);
            return;
        }
        if (isEngineDrowned()) {
            sender.sendStatusMessage(new TranslationTextComponent("message.rustcraft.vehicle_drowned"), true);
            return;
        }
        setEngineOn(true);
        this.world.playSound(null, getPosX(), getPosY(), getPosZ(), SoundEvents.BLOCK_IRON_TRAPDOOR_OPEN, SoundCategory.NEUTRAL, 1.0F, 0.5F);
    }

    protected boolean isEngineDrowned() {
        double top = waterSurface();
        return !Double.isNaN(top) && top - getPosY() > getDrownDepth();
    }

    /** Water depth (above vehicle origin) at which the engine dies. */
    protected double getDrownDepth() {
        return 0.9D;
    }

    private void serverTick() {
        if (isEngineOn()) {
            float burn = getFuelBurn() * fuelLoadFactor();
            float f = getFuel() - burn;
            if (f <= 0.0F) {
                f = 0.0F;
                setEngineOn(false);
            }
            setFuel(f);
            if (isEngineDrowned()) setEngineOn(false);
            if (!hasPilot()) {
                noPilotTicks++;
                if (this.onGround || isInWater()) groundedIdleTicks++;
                else groundedIdleTicks = 0;
                if (groundedIdleTicks > 40 || noPilotTicks > 2400) setEngineOn(false);
            } else {
                noPilotTicks = 0;
                groundedIdleTicks = 0;
            }
        }
        if (pendingImpact > 0.0F) {
            applyImpact(pendingImpact);
            pendingImpact = 0.0F;
        }
        if (isEngineDrowned() && isHelicopter() && this.ticksExisted % 20 == 0) {
            hurt(2.0F);
        }
        if (getHealth() <= 0.0F) destroy();
    }

    protected float fuelLoadFactor() {
        return 1.0F;
    }

    /** Called by simulate() after move(): report collision speed loss. */
    protected void registerImpact(double impact) {
        if (impact > getCrashThreshold()) {
            pendingImpact = (float) Math.max(pendingImpact, impact);
        }
    }

    private void applyImpact(float impact) {
        if (impact <= getCrashThreshold()) return;
        float dmg = (float) ((impact - getCrashThreshold()) * getCrashDamageFactor());
        this.world.playSound(null, getPosX(), getPosY(), getPosZ(), SoundEvents.ENTITY_ZOMBIE_ATTACK_IRON_DOOR,
                SoundCategory.NEUTRAL, Math.min(2.0F, 0.6F + impact), 0.7F);
        for (Entity p : this.getPassengers()) {
            p.attackEntityFrom(DamageSource.FLY_INTO_WALL, dmg * 0.12F);
        }
        hurt(dmg);
    }

    private void hurt(float amount) {
        if (!VehicleUtil.finite(amount) || amount <= 0.0F || this.removed) return;
        setHurtTime(10);
        setHealth(getHealth() - amount);
        if (getHealth() <= 0.0F) destroy();
    }

    @Override
    public boolean attackEntityFrom(DamageSource source, float amount) {
        if (this.isInvulnerableTo(source)) return false;
        if (this.world.isRemote || this.removed) return true;
        Entity attacker = source.getTrueSource();
        if (attacker != null && this.isPassenger(attacker) && !source.isExplosion()) return false;
        float dmg = amount;
        if (source.isExplosion()) dmg *= 2.5F;
        else if (source.isFireDamage()) dmg *= 0.5F;
        if (attacker instanceof PlayerEntity && ((PlayerEntity) attacker).abilities.isCreativeMode
                && source.getImmediateSource() == attacker) {
            dmg = Math.max(dmg, getMaxHealth() * 0.25F);
        }
        hurt(dmg);
        return true;
    }

    private void destroy() {
        if (this.world.isRemote || destroyed) return;
        destroyed = true;
        double x = getPosX(), y = getPosY() + 0.6D, z = getPosZ();
        this.removePassengers();
        this.remove();
        this.world.createExplosion(this, x, y, z, getExplosionPower(), false, Explosion.Mode.NONE);
        Item scrap = VehicleUtil.itemOr("rustcraft:scrap", Items.IRON_NUGGET);
        Item frags = VehicleUtil.itemOr("rustcraft:metal_fragments", Items.IRON_NUGGET);
        dropLoot(new ItemStack(scrap, getScrapDrop() / 2 + this.rand.nextInt(getScrapDrop() / 2 + 1)), x, y, z);
        int fr = getFragmentDrop();
        while (fr > 0) {
            int n = Math.min(64, fr);
            dropLoot(new ItemStack(frags, n), x, y, z);
            fr -= n;
        }
    }

    private void dropLoot(ItemStack stack, double x, double y, double z) {
        if (stack.isEmpty()) return;
        net.minecraft.entity.item.ItemEntity ie = new net.minecraft.entity.item.ItemEntity(this.world, x, y, z, stack);
        ie.setMotion((this.rand.nextDouble() - 0.5D) * 0.3D, 0.3D, (this.rand.nextDouble() - 0.5D) * 0.3D);
        ie.setDefaultPickupDelay();
        this.world.addEntity(ie);
    }

    // Effects (client)
    protected void clientEffects() {
        float hp = getHealth() / getMaxHealth();
        double[] e = getEnginePos();
        Vector3d ep = localToWorld(e[0], e[1], e[2]);
        if (hp < 0.5F && this.rand.nextFloat() < (0.5F - hp) * 2.2F) {
            this.world.addParticle(hp < 0.25F ? ParticleTypes.LARGE_SMOKE : ParticleTypes.SMOKE,
                    ep.x + (rand.nextDouble() - 0.5D) * 0.6D, ep.y, ep.z + (rand.nextDouble() - 0.5D) * 0.6D,
                    0.0D, 0.08D, 0.0D);
        }
        if (hp < 0.25F && this.rand.nextInt(5) == 0) {
            this.world.addParticle(ParticleTypes.FLAME, ep.x, ep.y, ep.z, 0.0D, 0.03D, 0.0D);
        }
        if (isEngineOn() && this.rand.nextInt(6) == 0) {
            this.world.addParticle(ParticleTypes.SMOKE, ep.x, ep.y + 0.2D, ep.z, 0.0D, 0.05D, 0.0D);
        }
        if (!soundActive && rotorSpeed > 0.01F) {
            soundActive = true;
            DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> rustcraft.vehicles.client.VehiclesClient.startEngineSound(this));
        }
    }

    public Vector3d localToWorld(double right, double up, double forward) {
        double yaw = Math.toRadians(this.rotationYaw);
        double x = getPosX() - Math.sin(yaw) * forward - Math.cos(yaw) * right;
        double z = getPosZ() + Math.cos(yaw) * forward - Math.sin(yaw) * right;
        return new Vector3d(x, getPosY() + up, z);
    }

    /** Highest water surface Y touching the vehicle's box, NaN if none. */
    protected double waterSurface() {
        AxisAlignedBB bb = this.getBoundingBox();
        int x0 = MathHelper.floor(bb.minX), x1 = MathHelper.ceil(bb.maxX);
        int y0 = MathHelper.floor(bb.minY - 0.5D), y1 = MathHelper.ceil(bb.maxY);
        int z0 = MathHelper.floor(bb.minZ), z1 = MathHelper.ceil(bb.maxZ);
        double top = Double.NaN;
        BlockPos.Mutable mp = new BlockPos.Mutable();
        for (int x = x0; x < x1; x++) {
            for (int z = z0; z < z1; z++) {
                for (int y = y0; y < y1; y++) {
                    mp.setPos(x, y, z);
                    FluidState fs = this.world.getFluidState(mp);
                    if (fs.isTagged(FluidTags.WATER)) {
                        double t = y + fs.getActualHeight(this.world, mp);
                        if (Double.isNaN(top) || t > top) top = t;
                    }
                }
            }
        }
        return top;
    }

    /** Height above the ground/water below (for HUD). */
    public double getAltitudeAboveGround() {
        int ground = this.world.getHeight(Heightmap.Type.MOTION_BLOCKING, MathHelper.floor(getPosX()), MathHelper.floor(getPosZ()));
        return Math.max(0.0D, getPosY() - ground);
    }

    // Misc entity overrides
    @Override
    protected void updateFallState(double y, boolean onGround, net.minecraft.block.BlockState state, BlockPos pos) {
        this.fallDistance = 0.0F; // crash damage is handled by simulate(); never forward fall damage to passengers
    }

    @Override
    public boolean onLivingFall(float distance, float damageMultiplier) {
        return false;
    }

    @Override
    public boolean canBeCollidedWith() {
        return this.isAlive();
    }

    @Override
    public boolean canBePushed() {
        return false;
    }

    @Override
    public boolean func_241845_aY() {
        return true;
    }

    @Override
    public void applyEntityCollision(Entity other) {
        if (this.isRidingSameEntity(other) || other.noClip || this.noClip) return;
        double dx = other.getPosX() - this.getPosX();
        double dz = other.getPosZ() - this.getPosZ();
        double d = MathHelper.absMax(dx, dz);
        if (d >= 0.01D) {
            d = Math.sqrt(d);
            dx /= d;
            dz /= d;
            double k = Math.min(1.0D, 1.0D / d) * 0.05D;
            if (!other.isBeingRidden()) other.addVelocity(dx * k, 0.0D, dz * k);
        }
    }

    @Override
    protected boolean canTriggerWalking() {
        return false;
    }

    @Override
    public boolean isInRangeToRenderDist(double distance) {
        return distance < 160.0D * 160.0D;
    }

    @Override
    public AxisAlignedBB getRenderBoundingBox() {
        return this.getBoundingBox().grow(3.5D, 1.5D, 3.5D);
    }

    @Override
    public IPacket<?> createSpawnPacket() {
        return NetworkHooks.getEntitySpawningPacket(this);
    }

    @Override
    protected void readAdditional(CompoundNBT tag) {
        if (tag.contains("Health")) setHealth(tag.getFloat("Health"));
        if (tag.contains("Fuel")) setFuel(tag.getFloat("Fuel"));
        if (tag.hasUniqueId("Owner")) owner = tag.getUniqueId("Owner");
    }

    @Override
    protected void writeAdditional(CompoundNBT tag) {
        tag.putFloat("Health", getHealth());
        tag.putFloat("Fuel", getFuel());
        if (owner != null) tag.putUniqueId("Owner", owner);
    }
}
