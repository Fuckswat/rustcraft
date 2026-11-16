package rustcraft.survival;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.AbstractGui;
import net.minecraft.client.resources.I18n;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.RenderTypeLookup;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.Hand;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.BlockRayTraceResult;
import net.minecraftforge.client.event.InputEvent;
import net.minecraftforge.client.event.RenderGameOverlayEvent;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;

/** Удержание ЛКМ работает даже при неразрушаемом стандартным способом узле. */
public final class SurvivalClient {
    private static int cooldown;
    private SurvivalClient() {}
    public static void init(IEventBus b) { b.addListener(SurvivalClient::setup); MinecraftForge.EVENT_BUS.register(SurvivalClient.class); }
    private static void setup(FMLClientSetupEvent e) {
        e.enqueueWork(() -> { RenderTypeLookup.setRenderLayer(SurvivalModule.HEMP.get(), RenderType.getCutout());
            RenderTypeLookup.setRenderLayer(SurvivalModule.CANOPY.get(), RenderType.getCutout()); });
    }
    private static BlockPos target(Minecraft mc) {
        return mc.world != null && mc.objectMouseOver instanceof BlockRayTraceResult ?
                ResourceNodes.root(mc.world, ((BlockRayTraceResult)mc.objectMouseOver).getPos()) : null;
    }
    @SubscribeEvent public static void input(InputEvent.ClickInputEvent e) {
        Minecraft mc = Minecraft.getInstance();
        if (e.isAttack() && mc.player != null && !mc.player.isCreative() && target(mc) != null) { e.setCanceled(true); e.setSwingHand(false); }
    }
    @SubscribeEvent public static void tick(TickEvent.ClientTickEvent e) {
        if (e.phase != TickEvent.Phase.END) return;
        Minecraft mc = Minecraft.getInstance();
        if (cooldown > 0) cooldown--;
        if (mc.player == null || mc.world == null || mc.currentScreen != null || mc.isGamePaused() || mc.player.isCreative() || mc.player.isSpectator()) return;
        BlockPos pos = target(mc);
        if (pos == null || !mc.gameSettings.keyBindAttack.isKeyDown() || cooldown > 0) return;
        TileEntity t = mc.world.getTileEntity(pos);
        if (!(t instanceof ResourceNodeTile) || ((ResourceNodeTile)t).hp() <= 0) return;
        if (!(mc.player.getHeldItemMainhand().getItem() instanceof HarvestToolItem) && ((ResourceNodeTile)t).kind() != ResourceNodeBlock.Kind.HEMP) return;
        cooldown = mc.player.getHeldItemMainhand().getItem() instanceof HarvestToolItem ? ((HarvestToolItem)mc.player.getHeldItemMainhand().getItem()).interval : 12;
        mc.player.swingArm(Hand.MAIN_HAND);
        HarvestNetwork.CHANNEL.sendToServer(new HarvestNetwork.Hit());
    }
    @SubscribeEvent public static void overlay(RenderGameOverlayEvent.Post e) {
        if (e.getType() != RenderGameOverlayEvent.ElementType.ALL) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.world == null || mc.currentScreen != null) return;
        BlockPos pos = target(mc); if (pos == null) return;
        TileEntity tile = mc.world.getTileEntity(pos); if (!(tile instanceof ResourceNodeTile)) return;
        ResourceNodeTile t = (ResourceNodeTile)tile;
        int x = e.getWindow().getScaledWidth()/2, y = e.getWindow().getScaledHeight()/2 + 34;
        String title = mc.world.getBlockState(pos).getBlock().getTranslatedName().getString();
        String text = t.hp() > 0 ? I18n.format("hud.rustcraft.harvest.hp", t.hp(), t.maxHp()) :
                I18n.format("hud.rustcraft.harvest.respawn", Math.max(0, (t.respawnAt()-mc.world.getGameTime()+19)/20));
        AbstractGui.fill(e.getMatrixStack(), x-78, y-3, x+78, y+31, 0xC0151A1D);
        mc.fontRenderer.drawStringWithShadow(e.getMatrixStack(), title, x-mc.fontRenderer.getStringWidth(title)/2F, y, 0xE5E0D3);
        mc.fontRenderer.drawStringWithShadow(e.getMatrixStack(), text, x-mc.fontRenderer.getStringWidth(text)/2F, y+11, 0xC4C1B8);
        AbstractGui.fill(e.getMatrixStack(), x-72, y+24, x+72, y+27, 0xFF343B39);
        AbstractGui.fill(e.getMatrixStack(), x-72, y+24, x-72+Math.round(144F*t.hp()/t.maxHp()), y+27, 0xFF91AC63);
    }
}
