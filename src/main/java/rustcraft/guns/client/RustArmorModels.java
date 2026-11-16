package rustcraft.guns.client;

import java.util.HashMap;
import java.util.Map;
import net.minecraft.entity.LivingEntity;
import net.minecraft.inventory.EquipmentSlotType;
import net.minecraft.client.renderer.entity.model.BipedModel;
import net.minecraft.client.renderer.model.ModelRenderer;
import rustcraft.guns.RustArmorItem;
import rustcraft.guns.RustArmorMaterial;

/** Original fitted wood slats, road signs and steel face/chest plates. */
public final class RustArmorModels {
    private static final Map<String,BipedModel<LivingEntity>> MODELS = new HashMap<>();
    private RustArmorModels() {}

    @SuppressWarnings({"rawtypes","unchecked"})
    public static BipedModel<?> get(RustArmorItem item, EquipmentSlotType slot, BipedModel<?> base) {
        String key=item.rustMaterial.name()+slot.name();
        BipedModel<LivingEntity> model=MODELS.computeIfAbsent(key,k -> create(item.rustMaterial,slot));
        ((BipedModel)base).setModelAttributes(model);
        model.bipedHead.copyModelAngles(base.bipedHead);
        model.bipedBody.copyModelAngles(base.bipedBody);
        model.bipedRightArm.copyModelAngles(base.bipedRightArm);
        model.bipedLeftArm.copyModelAngles(base.bipedLeftArm);
        model.bipedRightLeg.copyModelAngles(base.bipedRightLeg);
        model.bipedLeftLeg.copyModelAngles(base.bipedLeftLeg);
        return model;
    }

    private static ModelRenderer part(BipedModel<?> model,int u,int v) {
        return new ModelRenderer(model,u,v).setTextureSize(64,32);
    }

    private static BipedModel<LivingEntity> create(RustArmorMaterial mat,EquipmentSlotType slot) {
        BipedModel<LivingEntity> m=new BipedModel<>(slot==EquipmentSlotType.LEGS?0.4F:0.6F);
        m.setVisible(false);
        boolean wood=mat==RustArmorMaterial.RUST_WOOD, steel=mat==RustArmorMaterial.RUST_METAL;
        boolean cloth=mat==RustArmorMaterial.RUST_CLOTH;
        if(slot==EquipmentSlotType.HEAD) {
            m.bipedHead=part(m,0,0);
            if(steel) {
                m.bipedHead.addBox(-4.4F,-3.8F,-4.8F,8.8F,3.8F,1.1F);
                m.bipedHead.addBox(-4.4F,-7.5F,-4.8F,8.8F,1.5F,1.1F);
                m.bipedHead.addBox(-4.5F,-6F,-4.8F,1.5F,2.4F,1.1F);
                m.bipedHead.addBox(3F,-6F,-4.8F,1.5F,2.4F,1.1F);
                m.bipedHead.addBox(-4.6F,-5.0F,2.5F,9.2F,1.8F,2F);
            } else m.bipedHead.addBox(-4F,-8F,-4F,8,8,8,cloth?0.5F:0.8F);
            m.bipedHead.showModel=true;
        } else if(slot==EquipmentSlotType.CHEST) {
            m.bipedBody=part(m,16,16);
            if(wood) {
                for(int i=0;i<4;i++) {
                    m.bipedBody.addBox(-4F+i*2F,0.5F,-3.2F,1.7F,11F,1.4F);
                    m.bipedBody.addBox(-4F+i*2F,0.5F,1.8F,1.7F,11F,1.4F);
                }
            } else m.bipedBody.addBox(-4F,0F,-2F,8,12,4,cloth?0.45F:0.8F);
            m.bipedBody.showModel=true;
            m.bipedRightArm=part(m,40,16);m.bipedLeftArm=part(m,40,16);
            m.bipedRightArm.addBox(-3F,-2F,-2F,4,cloth?12:4,4,0.55F);
            m.bipedLeftArm.addBox(-1F,-2F,-2F,4,cloth?12:4,4,0.55F);
            m.bipedRightArm.showModel=true;m.bipedLeftArm.showModel=true;
        } else {
            m.bipedRightLeg=part(m,0,16);m.bipedLeftLeg=part(m,0,16);
            float y=slot==EquipmentSlotType.FEET?8:0;
            int height=slot==EquipmentSlotType.FEET?4:cloth?12:8;
            m.bipedRightLeg.addBox(-2F,y,-2F,4,height,4,0.55F);
            m.bipedLeftLeg.addBox(-2F,y,-2F,4,height,4,0.55F);
            m.bipedRightLeg.showModel=true;m.bipedLeftLeg.showModel=true;
        }
        return m;
    }
}
