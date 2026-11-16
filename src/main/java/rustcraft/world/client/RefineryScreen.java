package rustcraft.world.client;

import com.mojang.blaze3d.matrix.MatrixStack;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.gui.screen.inventory.ContainerScreen;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.text.ITextComponent;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import rustcraft.world.menu.RefineryContainer;

@OnlyIn(Dist.CLIENT)
public class RefineryScreen extends ContainerScreen<RefineryContainer> {
    private static final ResourceLocation TEX = new ResourceLocation("rustcraft", "textures/gui/world/refinery.png");

    public RefineryScreen(RefineryContainer container, PlayerInventory inv, ITextComponent title) {
        super(container, inv, title);
    }

    @Override
    public void render(MatrixStack ms, int mouseX, int mouseY, float partialTicks) {
        renderBackground(ms);
        super.render(ms, mouseX, mouseY, partialTicks);
        renderHoveredTooltip(ms, mouseX, mouseY);
    }

    @Override
    protected void drawGuiContainerBackgroundLayer(MatrixStack ms, float partialTicks, int mouseX, int mouseY) {
        RenderSystem.color4f(1.0F, 1.0F, 1.0F, 1.0F);
        minecraft.getTextureManager().bindTexture(TEX);
        int x = guiLeft;
        int y = guiTop;
        blit(ms, x, y, 0, 0, xSize, ySize);
        if (container.isBurning()) {
            int h = (int) Math.ceil(container.burnFraction() * 13.0F);
            blit(ms, x + 57, y + 37 + 13 - h, 176, 13 - h, 14, h + 1);
        }
        int w = (int) (container.cookFraction() * 24.0F);
        blit(ms, x + 79, y + 34, 176, 14, w + 1, 17);
    }
}
