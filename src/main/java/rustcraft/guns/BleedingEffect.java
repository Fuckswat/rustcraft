package rustcraft.guns;

import net.minecraft.entity.LivingEntity;
import net.minecraft.potion.Effect;
import net.minecraft.potion.EffectType;

/** Кровотечение: периодический урон, снимается бинтом или аптечкой (и молоком). */
public class BleedingEffect extends Effect {
    public BleedingEffect() {
        super(EffectType.HARMFUL, 0x8A0B0B);
    }

    @Override
    public void performEffect(LivingEntity entity, int amplifier) {
        if (!entity.world.isRemote) {
            entity.attackEntityFrom(GunDamage.BLEEDING, 0.5F + 0.5F * amplifier);
        }
    }

    @Override
    public boolean isReady(int duration, int amplifier) {
        int interval = Math.max(10, 40 >> amplifier); // раз в 2 c (быстрее на более высоком уровне)
        return duration % interval == 0;
    }
}
