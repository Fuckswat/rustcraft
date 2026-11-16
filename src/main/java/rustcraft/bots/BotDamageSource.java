package rustcraft.bots;

import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.util.EntityDamageSource;
import net.minecraft.util.text.ITextComponent;
import net.minecraft.util.text.TranslationTextComponent;

/**
 * Пуля учёного. Сообщение о смерти: death.attack.rustcraft.bot_shot.&lt;scientist|heavy_scientist&gt;.
 * Урон не масштабируется ванильной сложностью — множитель считается в {@link BotGunfire}.
 */
public class BotDamageSource extends EntityDamageSource {
    private final String kind;

    public BotDamageSource(Entity shooter, String kind) {
        super("rustcraft.bot_shot", shooter);
        this.kind = kind;
        setProjectile();
    }

    @Override
    public ITextComponent getDeathMessage(LivingEntity victim) {
        return new TranslationTextComponent("death.attack.rustcraft.bot_shot." + kind,
                victim.getDisplayName(), damageSourceEntity.getDisplayName());
    }

    @Override
    public boolean isDifficultyScaled() {
        return false;
    }
}
