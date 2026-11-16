package rustcraft.bots.client;

import com.mojang.blaze3d.matrix.MatrixStack;

import net.minecraft.client.renderer.entity.BipedRenderer;
import net.minecraft.client.renderer.entity.EntityRendererManager;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import rustcraft.RustCraft;
import rustcraft.bots.AbstractScientistEntity;

/** Рендер учёных: модель игрока + слой предмета в руке (BipedRenderer добавляет его сам). */
@OnlyIn(Dist.CLIENT)
public class ScientistRenderer<T extends AbstractScientistEntity> extends BipedRenderer<T, ScientistModel<T>> {
    public static final ResourceLocation SCIENTIST_TEX =
            new ResourceLocation(RustCraft.MODID, "textures/entity/bots/scientist.png");
    public static final ResourceLocation HEAVY_TEX =
            new ResourceLocation(RustCraft.MODID, "textures/entity/bots/heavy_scientist.png");

    private final ResourceLocation texture;
    private final float scale;

    public ScientistRenderer(EntityRendererManager manager, ResourceLocation texture, float scale) {
        super(manager, new ScientistModel<T>(), 0.5F);
        this.texture = texture;
        this.scale = scale;
    }

    @Override
    public ResourceLocation getEntityTexture(T entity) {
        return texture;
    }

    @Override
    protected void preRenderCallback(T entity, MatrixStack matrix, float partialTick) {
        matrix.scale(scale, scale, scale);
    }
}
