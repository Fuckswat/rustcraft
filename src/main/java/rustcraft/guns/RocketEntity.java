package rustcraft.guns;

import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.projectile.ProjectileItemEntity;
import net.minecraft.item.Item;
import net.minecraft.network.IPacket;
import net.minecraft.particles.ParticleTypes;
import net.minecraft.util.DamageSource;
import net.minecraft.util.math.EntityRayTraceResult;
import net.minecraft.util.math.RayTraceResult;
import net.minecraft.util.math.vector.Vector3d;
import net.minecraft.world.Explosion;
import net.minecraft.world.World;
import net.minecraftforge.fml.network.NetworkHooks;

/** Ракета гранатомёта: летит почти прямо, взрывается при попадании (createExplosion, сила 4, BREAK). */
public class RocketEntity extends ProjectileItemEntity {
    public static final float POWER = 4.0F;
    private static final int MAX_LIFE = 160;

    public RocketEntity(EntityType<? extends RocketEntity> type, World world) {
        super(type, world);
    }

    public RocketEntity(World world, LivingEntity shooter) {
        super(GunsModule.ROCKET.get(), shooter, world);
    }

    @Override
    protected Item getDefaultItem() {
        return GunsItems.AMMO_ROCKET.get();
    }

    @Override
    protected float getGravityVelocity() {
        return 0.006F;
    }

    @Override
    public void tick() {
        super.tick();
        if (!this.isAlive()) return;
        if (world.isRemote) {
            Vector3d m = getMotion();
            for (int i = 0; i < 3; i++) {
                double f = i / 3.0;
                double x = getPosX() - m.x * f, y = getPosY() + 0.1 - m.y * f, z = getPosZ() - m.z * f;
                world.addParticle(ParticleTypes.LARGE_SMOKE, x, y, z, rand.nextGaussian() * 0.01, 0.02, rand.nextGaussian() * 0.01);
                if (i == 0) world.addParticle(ParticleTypes.FLAME, x, y, z, -m.x * 0.1, -m.y * 0.1, -m.z * 0.1);
            }
        } else if (ticksExisted > MAX_LIFE) {
            explode();
        }
    }

    @Override
    protected boolean func_230298_a_(Entity entity) {
        // не цепляем стрелка на старте
        if (ticksExisted < 5 && entity == func_234616_v_()) return false;
        return super.func_230298_a_(entity);
    }

    @Override
    protected void onEntityHit(EntityRayTraceResult result) {
        super.onEntityHit(result);
        Entity shooter = func_234616_v_();
        result.getEntity().attackEntityFrom(DamageSource.causeThrownDamage(this, shooter), 8.0F);
    }

    @Override
    protected void onImpact(RayTraceResult result) {
        super.onImpact(result);
        if (!world.isRemote && isAlive()) {
            explode();
        }
    }

    private void explode() {
        world.createExplosion(this, getPosX(), getPosY(), getPosZ(), POWER, Explosion.Mode.BREAK);
        remove();
    }

    @Override
    public IPacket<?> createSpawnPacket() {
        return NetworkHooks.getEntitySpawningPacket(this);
    }
}
