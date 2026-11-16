package rustcraft.bots.client;

import net.minecraft.client.renderer.entity.model.BipedModel;
import net.minecraft.client.renderer.entity.model.PlayerModel;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import rustcraft.bots.AbstractScientistEntity;

/** Модель игрока (64x64 скин с внешним слоем); в бою — стойка с оружием двумя руками. */
@OnlyIn(Dist.CLIENT)
public class ScientistModel<T extends AbstractScientistEntity> extends PlayerModel<T> {

    public ScientistModel() {
        super(0.0F, false);
    }

    @Override
    public void setLivingAnimations(T entity, float limbSwing, float limbSwingAmount, float partialTick) {
        boolean holding = !entity.getHeldItemMainhand().isEmpty();
        if (entity.isAggressive() && holding) {
            this.rightArmPose = BipedModel.ArmPose.CROSSBOW_HOLD;
        } else {
            this.rightArmPose = holding ? BipedModel.ArmPose.ITEM : BipedModel.ArmPose.EMPTY;
        }
        this.leftArmPose = BipedModel.ArmPose.EMPTY;
        super.setLivingAnimations(entity, limbSwing, limbSwingAmount, partialTick);
    }
}
