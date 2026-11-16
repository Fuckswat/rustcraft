package rustcraft.vehicles.client.model;

import com.mojang.blaze3d.matrix.MatrixStack;
import com.mojang.blaze3d.vertex.IVertexBuilder;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.model.EntityModel;
import net.minecraft.client.renderer.model.ModelRenderer;
import rustcraft.vehicles.entity.AbstractVehicleEntity;

import java.util.ArrayList;
import java.util.List;

/**
 * База для сгенерированных моделей транспорта (см. tools/vehicles/gen_vehicles.py).
 * Части делятся на непрозрачные, стекло (полупрозрачный проход), маску воды (лодка) и вращающиеся роторы.
 */
public abstract class VehicleModel extends EntityModel<AbstractVehicleEntity> {
    public static final int SOLID = 0, GLASS = 1, MASK = 2, ROTOR_Y = 3, ROTOR_Y_REV = 4, ROTOR_X = 5, PROP_Z = 6;

    private final List<ModelRenderer> solid = new ArrayList<ModelRenderer>();
    private final List<ModelRenderer> glass = new ArrayList<ModelRenderer>();
    private final List<ModelRenderer> mask = new ArrayList<ModelRenderer>();
    private final List<ModelRenderer> spinning = new ArrayList<ModelRenderer>();
    private final List<int[]> spinKinds = new ArrayList<int[]>();
    private final List<Float> spinPhase = new ArrayList<Float>();

    protected VehicleModel(int texW, int texH) {
        super(RenderType::getEntityCutoutNoCull);
        this.textureWidth = texW;
        this.textureHeight = texH;
    }

    protected ModelRenderer part(int kind, float px, float py, float pz, float phaseDeg) {
        ModelRenderer r = new ModelRenderer(this);
        r.setRotationPoint(px, py, pz);
        switch (kind) {
            case GLASS:
                glass.add(r);
                break;
            case MASK:
                mask.add(r);
                break;
            case ROTOR_Y:
            case ROTOR_Y_REV:
            case ROTOR_X:
            case PROP_Z:
                solid.add(r);
                spinning.add(r);
                spinKinds.add(new int[]{kind});
                spinPhase.add((float) Math.toRadians(phaseDeg));
                break;
            default:
                solid.add(r);
        }
        return r;
    }

    /** @param angle rotor angle in radians */
    public void setRotor(float angle) {
        for (int i = 0; i < spinning.size(); i++) {
            ModelRenderer r = spinning.get(i);
            float ph = spinPhase.get(i);
            switch (spinKinds.get(i)[0]) {
                case ROTOR_Y:
                    r.rotateAngleY = angle + ph;
                    break;
                case ROTOR_Y_REV:
                    r.rotateAngleY = -angle + ph;
                    break;
                case ROTOR_X:
                    r.rotateAngleX = angle * 1.7F + ph;
                    break;
                case PROP_Z:
                    r.rotateAngleZ = angle * 2.0F + ph;
                    break;
                default:
            }
        }
    }

    @Override
    public void setRotationAngles(AbstractVehicleEntity entity, float limbSwing, float limbSwingAmount, float ageInTicks,
                                  float netHeadYaw, float headPitch) {
    }

    @Override
    public void render(MatrixStack ms, IVertexBuilder buffer, int light, int overlay, float r, float g, float b, float a) {
        for (ModelRenderer part : solid) part.render(ms, buffer, light, overlay, r, g, b, a);
    }

    public void renderGlass(MatrixStack ms, IVertexBuilder buffer, int light, int overlay) {
        for (ModelRenderer part : glass) part.render(ms, buffer, light, overlay, 1.0F, 1.0F, 1.0F, 1.0F);
    }

    public boolean hasMask() {
        return !mask.isEmpty();
    }

    public void renderMask(MatrixStack ms, IVertexBuilder buffer, int light) {
        for (ModelRenderer part : mask) part.render(ms, buffer, light, net.minecraft.client.renderer.texture.OverlayTexture.NO_OVERLAY);
    }
}
