package rustcraft.building;

import net.minecraft.entity.EntityType;
import net.minecraft.item.Item;

/** Параметры рейдовой взрывчатки. Сила взрыва → урон постройкам считается в RaidDamage. */
public enum ExplosiveKind {
    /** C4: 10 с, сила 5, без осечек. */
    C4(5.0F, 200, 200, 0.0F, 0.9F),
    /** Сумка: фитиль 6–10 с, сила 3, осечка 20 %. */
    SATCHEL(3.0F, 120, 200, 0.2F, 0.85F),
    /** Бобовая граната: 3–4 с, сила 2, осечка 25 %. */
    BEANCAN(2.0F, 60, 80, 0.25F, 1.1F);

    public final float power;
    public final int fuseMin;
    public final int fuseMax;
    public final float dudChance;
    public final float throwSpeed;

    ExplosiveKind(float power, int fuseMin, int fuseMax, float dudChance, float throwSpeed) {
        this.power = power;
        this.fuseMin = fuseMin;
        this.fuseMax = fuseMax;
        this.dudChance = dudChance;
        this.throwSpeed = throwSpeed;
    }

    public EntityType<ThrownExplosiveEntity> entityType() {
        switch (this) {
            case C4: return BRegistry.THROWN_C4.get();
            case SATCHEL: return BRegistry.THROWN_SATCHEL.get();
            default: return BRegistry.THROWN_BEANCAN.get();
        }
    }

    public Item item() {
        switch (this) {
            case C4: return BRegistry.TIMED_EXPLOSIVE.get();
            case SATCHEL: return BRegistry.SATCHEL_CHARGE.get();
            default: return BRegistry.BEANCAN_GRENADE.get();
        }
    }

    public static ExplosiveKind fromType(EntityType<?> type) {
        if (type == BRegistry.THROWN_C4.get()) return C4;
        if (type == BRegistry.THROWN_SATCHEL.get()) return SATCHEL;
        return BEANCAN;
    }
}
