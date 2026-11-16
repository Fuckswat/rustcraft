package rustcraft.guns.client;

import com.mojang.blaze3d.matrix.MatrixStack;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.IRenderTypeBuffer;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererManager;
import net.minecraft.client.renderer.model.ItemCameraTransforms;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.inventory.container.PlayerContainer;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.vector.Vector3f;
import rustcraft.guns.RocketEntity;

/** Рисует ракету 3D-моделью предмета ammo_rocket, повёрнутой по направлению полёта (нос модели = +X). */
public class RocketRenderer extends EntityRenderer<RocketEntity> {
    public RocketRenderer(EntityRendererManager manager) {
        super(manager);
        this.shadowSize = 0.15F;
    }

    @Override
    public void render(RocketEntity entity, float entityYaw, float partialTicks, MatrixStack ms, IRenderTypeBuffer buffer, int light) {
        ms.push();
        ms.translate(0.0D, 0.15D, 0.0D);
        ms.rotate(Vector3f.YP.rotationDegrees(MathHelper.lerp(partialTicks, entity.prevRotationYaw, entity.rotationYaw) - 90.0F));
        ms.rotate(Vector3f.ZP.rotationDegrees(MathHelper.lerp(partialTicks, entity.prevRotationPitch, entity.rotationPitch)));
        ms.scale(1.1F, 1.1F, 1.1F);
        Minecraft.getInstance().getItemRenderer().renderItem(entity.getItem(), ItemCameraTransforms.TransformType.FIXED,
                light, OverlayTexture.NO_OVERLAY, ms, buffer);
        ms.pop();
        super.render(entity, entityYaw, partialTicks, ms, buffer, light);
    }

    @Override
    public ResourceLocation getEntityTexture(RocketEntity entity) {
        return PlayerContainer.LOCATION_BLOCKS_TEXTURE;
    }
}
