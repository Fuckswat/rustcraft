package rustcraft.building;

import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.LivingEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.CompoundNBT;
import net.minecraft.nbt.NBTUtil;
import net.minecraft.network.IPacket;
import net.minecraft.network.datasync.DataParameter;
import net.minecraft.network.datasync.DataSerializers;
import net.minecraft.network.datasync.EntityDataManager;
import net.minecraft.entity.projectile.ProjectileItemEntity;
import net.minecraft.particles.ParticleTypes;
import net.minecraft.particles.RedstoneParticleData;
import net.minecraft.util.SoundCategory;
import net.minecraft.util.SoundEvents;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.BlockRayTraceResult;
import net.minecraft.util.math.EntityRayTraceResult;
import net.minecraft.util.math.vector.Vector3d;
import net.minecraft.world.Explosion;
import net.minecraft.world.World;
import net.minecraft.world.server.ServerWorld;
import net.minecraftforge.fml.network.NetworkHooks;

/**
 * Брошенная взрывчатка (thrown_c4 / thrown_satchel / thrown_beancan).
 * Прилипает к блоку, в который попала, тикает фитиль, мигает/пищит, потом взрывается
 * через обычный world.createExplosion — урон постройкам считает RaidDamage.
 */
public class ThrownExplosiveEntity extends ProjectileItemEntity {
    private static final DataParameter<Boolean> STUCK = EntityDataManager.createKey(ThrownExplosiveEntity.class, DataSerializers.BOOLEAN);
    private static final DataParameter<Integer> FUSE = EntityDataManager.createKey(ThrownExplosiveEntity.class, DataSerializers.VARINT);

    private int fuse = -1;
    private boolean dud;
    private BlockPos stuckPos;

    public ThrownExplosiveEntity(EntityType<? extends ThrownExplosiveEntity> type, World world) {
        super(type, world);
    }

    public ThrownExplosiveEntity(EntityType<? extends ThrownExplosiveEntity> type, LivingEntity thrower, World world) {
        super(type, thrower, world);
        ExplosiveKind k = kind();
        this.fuse = k.fuseMin + (k.fuseMax > k.fuseMin ? rand.nextInt(k.fuseMax - k.fuseMin + 1) : 0);
        this.dud = rand.nextFloat() < k.dudChance;
        this.dataManager.set(FUSE, fuse);
    }

    public ExplosiveKind kind() {
        return ExplosiveKind.fromType(getType());
    }

    @Override
    protected Item getDefaultItem() {
        return kind().item();
    }

    @Override
    protected void registerData() {
        super.registerData();
        this.dataManager.register(STUCK, false);
        this.dataManager.register(FUSE, 200);
    }

    public boolean isStuck() {
        return this.dataManager.get(STUCK);
    }

    @Override
    public void tick() {
        if (isStuck()) {
            this.baseTick();
            this.setMotion(Vector3d.ZERO);
            if (!world.isRemote && stuckPos != null && world.isAirBlock(stuckPos)) {
                // Блок, к которому прилипли, уничтожен — падаем.
                this.dataManager.set(STUCK, false);
                this.setNoGravity(false);
                this.stuckPos = null;
            }
        } else {
            super.tick();
        }
        if (!this.isAlive()) return;

        if (world.isRemote) {
            clientEffects();
            return;
        }
        if (fuse < 0) {
            ExplosiveKind k = kind();
            fuse = k.fuseMin;
        }
        fuse--;
        if (fuse % 5 == 0) this.dataManager.set(FUSE, fuse);
        serverBeeps();
        if (fuse <= 0) {
            detonate();
        }
    }

    private void serverBeeps() {
        ExplosiveKind k = kind();
        if (k == ExplosiveKind.C4) {
            int period = fuse > 60 ? 20 : 8;
            if (fuse % period == 0) {
                world.playSound(null, getPosX(), getPosY(), getPosZ(), SoundEvents.BLOCK_NOTE_BLOCK_BIT,
                        SoundCategory.HOSTILE, 1.0F, fuse > 60 ? 1.4F : 1.9F);
            }
        } else if (ticksExisted == 2 || ticksExisted % 30 == 0) {
            world.playSound(null, getPosX(), getPosY(), getPosZ(), SoundEvents.ENTITY_TNT_PRIMED,
                    SoundCategory.HOSTILE, 0.5F, k == ExplosiveKind.BEANCAN ? 1.6F : 1.2F);
        }
    }

    private void clientEffects() {
        ExplosiveKind k = kind();
        int f = this.dataManager.get(FUSE);
        double x = getPosX(), y = getPosY() + getHeight() / 2.0, z = getPosZ();
        if (k == ExplosiveKind.C4) {
            int period = f > 60 ? 20 : 8;
            if (ticksExisted % period < 3) {
                world.addParticle(new RedstoneParticleData(1.0F, 0.0F, 0.0F, 1.0F), x, y + 0.15, z, 0, 0, 0);
            }
        } else {
            if (ticksExisted % 2 == 0) {
                world.addParticle(ParticleTypes.SMOKE, x, y + 0.2, z, 0, 0.02, 0);
            }
            if (ticksExisted % 4 == 0) {
                world.addParticle(ParticleTypes.FLAME, x, y + 0.2, z, 0, 0.01, 0);
            }
        }
    }

    private void detonate() {
        this.remove();
        ExplosiveKind k = kind();
        if (dud) {
            world.playSound(null, getPosX(), getPosY(), getPosZ(), SoundEvents.BLOCK_FIRE_EXTINGUISH, SoundCategory.HOSTILE, 0.7F, 1.0F);
            if (world instanceof ServerWorld) {
                ((ServerWorld) world).spawnParticle(ParticleTypes.SMOKE, getPosX(), getPosY() + 0.2, getPosZ(), 8, 0.1, 0.1, 0.1, 0.02);
            }
            // Осечка — заряд можно подобрать и бросить снова.
            this.entityDropItem(new ItemStack(k.item()));
            return;
        }
        world.createExplosion(this, getPosX(), getPosY() + getHeight() / 2.0, getPosZ(), k.power, Explosion.Mode.BREAK);
    }

    /** Попали в блок — прилипаем к поверхности. */
    @Override
    protected void func_230299_a_(BlockRayTraceResult hit) {
        super.func_230299_a_(hit);
        if (isStuck()) return;
        Vector3d v = hit.getHitVec();
        Vector3d n = Vector3d.copy(hit.getFace().getDirectionVec());
        double half = getWidth() / 2.0;
        double px = v.x + n.x * (half + 0.01);
        double py = v.y + n.y * (half + 0.01) - getHeight() / 2.0;
        double pz = v.z + n.z * (half + 0.01);
        if (n.y > 0) py = v.y + 0.01;
        if (n.y < 0) py = v.y - getHeight() - 0.01;
        this.setPosition(px, py, pz);
        this.setMotion(Vector3d.ZERO);
        this.setNoGravity(true);
        this.stuckPos = hit.getPos();
        this.dataManager.set(STUCK, true);
        if (!world.isRemote) {
            world.playSound(null, px, py, pz, kind() == ExplosiveKind.C4 ? SoundEvents.BLOCK_SLIME_BLOCK_PLACE : SoundEvents.BLOCK_WOOL_PLACE,
                    SoundCategory.HOSTILE, 0.8F, 1.0F);
        }
    }

    @Override
    protected void onEntityHit(EntityRayTraceResult hit) {
        // Сквозь существ пролетаем.
    }

    @Override
    protected boolean func_230298_a_(Entity target) {
        return false;
    }

    @Override
    protected float getGravityVelocity() {
        return 0.05F;
    }

    @Override
    public boolean canBeCollidedWith() {
        return false;
    }

    @Override
    public void writeAdditional(CompoundNBT nbt) {
        super.writeAdditional(nbt);
        nbt.putInt("Fuse", fuse);
        nbt.putBoolean("Dud", dud);
        nbt.putBoolean("Stuck", isStuck());
        if (stuckPos != null) nbt.put("StuckPos", NBTUtil.writeBlockPos(stuckPos));
    }

    @Override
    public void readAdditional(CompoundNBT nbt) {
        super.readAdditional(nbt);
        fuse = nbt.contains("Fuse") ? nbt.getInt("Fuse") : -1;
        dud = nbt.getBoolean("Dud");
        boolean stuck = nbt.getBoolean("Stuck");
        this.dataManager.set(STUCK, stuck);
        if (stuck) setNoGravity(true);
        stuckPos = nbt.contains("StuckPos") ? NBTUtil.readBlockPos(nbt.getCompound("StuckPos")) : null;
        if (fuse >= 0) this.dataManager.set(FUSE, fuse);
    }

    @Override
    public IPacket<?> createSpawnPacket() {
        return NetworkHooks.getEntitySpawningPacket(this);
    }
}
