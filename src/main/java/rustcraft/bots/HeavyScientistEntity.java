package rustcraft.bots;

import net.minecraft.block.BlockState;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.ai.attributes.AttributeModifierMap;
import net.minecraft.entity.ai.attributes.Attributes;
import net.minecraft.entity.monster.MonsterEntity;
import net.minecraft.util.SoundEvents;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

/** Тяжёлый учёный: ≈70 HP, тяжёлая броня, медленный, AK-47 с длинными очередями или помповый дробовик. */
public class HeavyScientistEntity extends AbstractScientistEntity {

    public HeavyScientistEntity(EntityType<? extends HeavyScientistEntity> type, World world) {
        super(type, world);
        this.experienceValue = 25;
    }

    public static AttributeModifierMap.MutableAttribute createAttributes() {
        return MonsterEntity.func_234295_eP_()
                .createMutableAttribute(Attributes.MAX_HEALTH, 70.0D)
                .createMutableAttribute(Attributes.MOVEMENT_SPEED, 0.23D)
                .createMutableAttribute(Attributes.FOLLOW_RANGE, 40.0D)
                .createMutableAttribute(Attributes.ARMOR, 12.0D)
                .createMutableAttribute(Attributes.ARMOR_TOUGHNESS, 4.0D)
                .createMutableAttribute(Attributes.KNOCKBACK_RESISTANCE, 0.7D)
                .createMutableAttribute(Attributes.ATTACK_DAMAGE, 5.0D);
    }

    @Override
    protected BotWeapon pickWeapon() {
        return this.rand.nextInt(5) < 2 ? BotWeapon.SHOTGUN : BotWeapon.HEAVY_AK;
    }

    @Override
    public String getDeathMessageKind() {
        return "heavy_scientist";
    }

    @Override
    public boolean canSuppress() {
        return true;
    }

    @Override
    protected float weaponDropChance() {
        return 0.08F;
    }

    @Override
    protected float voicePitch() {
        return 0.52F;
    }

    @Override
    protected void playStepSound(BlockPos pos, BlockState state) {
        super.playStepSound(pos, state);
        playSound(SoundEvents.ENTITY_IRON_GOLEM_STEP, 0.12F, 1.6F + this.rand.nextFloat() * 0.2F);
    }
}
