package rustcraft.guns;

import net.minecraft.entity.Entity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.DamageSource;
import net.minecraft.util.EntityDamageSource;

/** Источники урона модуля. Сообщения о смерти: death.attack.rustcraft.bullet[.item], death.attack.rustcraft.bleeding[.player]. */
public final class GunDamage {
    private GunDamage() {}

    public static final String BULLET_ID = "rustcraft.bullet";

    public static final DamageSource BLEEDING = new DamageSource("rustcraft.bleeding").setDamageBypassesArmor();

    public static DamageSource bullet(Entity shooter) {
        return new EntityDamageSource(BULLET_ID, shooter).setProjectile();
    }

    public static DamageSource bullet(Entity shooter, Entity target) {
        DamageSource source=bullet(shooter);
        return target instanceof PlayerEntity ? source.setDamageBypassesArmor() : source;
    }
}
