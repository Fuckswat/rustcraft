package rustcraft.bots;

import net.minecraft.entity.EntityType;
import net.minecraft.entity.ai.attributes.AttributeModifierMap;
import net.minecraft.entity.ai.attributes.Attributes;
import net.minecraft.entity.monster.MonsterEntity;
import net.minecraft.world.World;

/** Учёный: 100 «растовских» HP ≈ 30 HP, лёгкая броня, MP5 или AK-47. */
public class ScientistEntity extends AbstractScientistEntity {

    public ScientistEntity(EntityType<? extends ScientistEntity> type, World world) {
        super(type, world);
    }

    public static AttributeModifierMap.MutableAttribute createAttributes() {
        return MonsterEntity.func_234295_eP_()
                .createMutableAttribute(Attributes.MAX_HEALTH, 30.0D)
                .createMutableAttribute(Attributes.MOVEMENT_SPEED, 0.30D)
                .createMutableAttribute(Attributes.FOLLOW_RANGE, 40.0D)
                .createMutableAttribute(Attributes.ARMOR, 4.0D)
                .createMutableAttribute(Attributes.KNOCKBACK_RESISTANCE, 0.1D)
                .createMutableAttribute(Attributes.ATTACK_DAMAGE, 3.0D);
    }

    @Override
    protected BotWeapon pickWeapon() {
        return this.rand.nextBoolean() ? BotWeapon.MP5 : BotWeapon.AK47;
    }

    @Override
    public String getDeathMessageKind() {
        return "scientist";
    }

    @Override
    protected float weaponDropChance() {
        return 0.05F;
    }

    @Override
    protected float voicePitch() {
        return 0.68F;
    }
}
