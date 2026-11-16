package rustcraft.vehicles.client;

import com.mojang.blaze3d.matrix.MatrixStack;
import net.minecraft.client.renderer.IRenderTypeBuffer;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererManager;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.vector.Vector3f;
import rustcraft.vehicles.client.model.VehicleModel;
import rustcraft.vehicles.entity.AbstractVehicleEntity;

/** Рендер транспорта: рыскание → тангаж → крен вокруг точки pivotHeight, роторы крутятся. */
public class VehicleRenderer<T extends AbstractVehicleEntity> extends EntityRenderer<T> {
    private final VehicleModel model;
    private final ResourceLocation texture;
    private final float pivotHeight;

    public VehicleRenderer(EntityRendererManager manager, VehicleModel model, String textureName, float pivotHeight, float shadow) {
        super(manager);
        this.model = model;
        this.texture = new ResourceLocation("rustcraft", "textures/entity/vehicles/" + textureName + ".png");
        this.pivotHeight = pivotHeight;
        this.shadowSize = shadow;
    }

    @Override
    public void render(T entity, float entityYaw, float partialTicks, MatrixStack ms, IRenderTypeBuffer buffers, int light) {
        ms.push();
        float pitch = MathHelper.lerp(partialTicks, entity.prevRotationPitch, entity.rotationPitch);
        float roll = entity.getRoll(partialTicks);
        float hurt = entity.getHurtTime() - partialTicks;
        if (hurt > 0.0F) {
            roll += MathHelper.sin(hurt * 1.3F) * hurt * 0.35F;
        }
        float bob = 0.0F;
        if (!entity.isHelicopter() && entity.isInWater()) {
            float t = entity.ticksExisted + partialTicks;
            bob = MathHelper.sin(t * 0.12F) * 0.025F;
            roll += MathHelper.sin(t * 0.09F) * 1.2F;
        }
        ms.translate(0.0D, pivotHeight + bob, 0.0D);
        ms.rotate(Vector3f.YP.rotationDegrees(180.0F - entityYaw));
        ms.rotate(Vector3f.XP.rotationDegrees(-pitch));
        ms.rotate(Vector3f.ZP.rotationDegrees(-roll));
        ms.scale(-1.0F, -1.0F, 1.0F);
        ms.translate(0.0D, pivotHeight - 1.5D, 0.0D);

        model.setRotor((float) Math.toRadians(entity.getRotorAngle(partialTicks)));
        model.render(ms, buffers.getBuffer(model.getRenderType(texture)), light, OverlayTexture.NO_OVERLAY, 1.0F, 1.0F, 1.0F, 1.0F);
        if (model.hasMask() && !entity.areEyesInFluid(net.minecraft.tags.FluidTags.WATER)) {
            model.renderMask(ms, buffers.getBuffer(RenderType.getWaterMask()), light);
        }
        model.renderGlass(ms, buffers.getBuffer(RenderType.getEntityTranslucent(texture)), light, OverlayTexture.NO_OVERLAY);
        ms.pop();
        super.render(entity, entityYaw, partialTicks, ms, buffers, light);
    }

    @Override
    public ResourceLocation getEntityTexture(T entity) {
        return texture;
    }
}
