package rustcraft.guns.client;

import org.lwjgl.glfw.GLFW;

import net.minecraft.client.Minecraft;
import net.minecraft.client.entity.player.ClientPlayerEntity;
import net.minecraft.client.settings.KeyBinding;
import net.minecraft.item.ItemStack;
import net.minecraft.util.SoundEvents;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.RayTraceResult;
import net.minecraftforge.client.event.FOVUpdateEvent;
import net.minecraftforge.client.event.InputEvent;
import net.minecraftforge.client.settings.KeyConflictContext;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.client.registry.ClientRegistry;
import net.minecraftforge.fml.client.registry.RenderingRegistry;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;
import rustcraft.guns.GunItem;
import rustcraft.guns.GunNetwork;
import rustcraft.guns.GunStats;
import rustcraft.guns.GunsModule;

/**
 * Клиент: ввод (ЛКМ огонь / ПКМ прицел / R перезарядка), отдача, зум, регистрация рендера ракеты и HUD.
 */
public final class GunsClient {
    private GunsClient() {}

    public static final KeyBinding RELOAD = new KeyBinding("key.rustcraft.reload", KeyConflictContext.IN_GAME,
            net.minecraft.client.util.InputMappings.Type.KEYSYM, GLFW.GLFW_KEY_R, "key.categories.rustcraft");

    static int cooldown;
    static boolean wasDown;
    static int pendingClickTicks;
    static float recoilPitchLeft;
    static float recoilYawLeft;
    static int dryCooldown;

    public static void init(IEventBus modBus) {
        modBus.addListener(GunsClient::clientSetup);
        MinecraftForge.EVENT_BUS.register(GunsClient.class);
        MinecraftForge.EVENT_BUS.register(GunHud.class);
    }

    private static void clientSetup(FMLClientSetupEvent event) {
        ClientRegistry.registerKeyBinding(RELOAD);
        RenderingRegistry.registerEntityRenderingHandler(GunsModule.ROCKET.get(), RocketRenderer::new);
    }

    static GunStats heldGun(ClientPlayerEntity p) {
        if (p == null) return null;
        ItemStack s = p.getHeldItemMainhand();
        return s.getItem() instanceof GunItem ? ((GunItem) s.getItem()).getStats() : null;
    }

    static boolean isAiming() {
        Minecraft mc = Minecraft.getInstance();
        return mc.player != null && mc.currentScreen == null && heldGun(mc.player) != null
                && !mc.player.isSpectator() && mc.gameSettings.keyBindUseItem.isKeyDown();
    }


    /** Пока в руке оружие: ЛКМ не ломает блоки и не бьёт, ПКМ не используется (кроме взаимодействия с блоками). */
    @SubscribeEvent(priority = EventPriority.HIGH)
    public static void onClickInput(InputEvent.ClickInputEvent event) {
        Minecraft mc = Minecraft.getInstance();
        if (heldGun(mc.player) == null) return;
        if (event.isAttack()) {
            event.setCanceled(true);
            event.setSwingHand(false);
        } else if (event.isUseItem()) {
            RayTraceResult hit = mc.objectMouseOver;
            if (hit == null || hit.getType() != RayTraceResult.Type.BLOCK) {
                event.setCanceled(true);
                event.setSwingHand(false);
            }
        }
    }

    @SubscribeEvent
    public static void onMouse(InputEvent.MouseInputEvent event) {
        Minecraft mc = Minecraft.getInstance();
        if (event.getAction() != GLFW.GLFW_PRESS || mc.currentScreen != null) return;
        if (heldGun(mc.player) != null && mc.gameSettings.keyBindAttack.matchesMouseKey(event.getButton())) {
            pendingClickTicks = 4;
        }
    }

    @SubscribeEvent
    public static void onKey(InputEvent.KeyInputEvent event) {
        Minecraft mc = Minecraft.getInstance();
        if (event.getAction() != GLFW.GLFW_PRESS || mc.currentScreen != null) return;
        if (heldGun(mc.player) != null && mc.gameSettings.keyBindAttack.matchesKey(event.getKey(), event.getScanCode())) {
            pendingClickTicks = 4;
        }
    }

    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        Minecraft mc = Minecraft.getInstance();
        ClientPlayerEntity p = mc.player;
        if (p == null || mc.world == null) {
            cooldown = 0; wasDown = false; pendingClickTicks = 0; recoilPitchLeft = 0; recoilYawLeft = 0;
            return;
        }
        if (cooldown > 0) cooldown--;
        if (dryCooldown > 0) dryCooldown--;
        GunHud.tick();

        GunStats st = heldGun(p);
        while (RELOAD.isPressed()) {
            if (st != null && mc.currentScreen == null) {
                GunNetwork.CHANNEL.sendToServer(new GunNetwork.ReloadMsg());
            }
        }
        if (st == null || mc.currentScreen != null || p.isSpectator() || mc.isGamePaused()) {
            wasDown = false;
            pendingClickTicks = 0;
            return;
        }
        boolean down = mc.gameSettings.keyBindAttack.isKeyDown();
        boolean pending = pendingClickTicks > 0;
        boolean trigger = st.auto ? (down || pending) : pending;
        if (trigger && cooldown <= 0 && !p.isHandActive()) {
            pendingClickTicks = 0;
            ItemStack stack = p.getHeldItemMainhand();
            if (GunItem.isReloading(stack)) {
                // ждём конца перезарядки
            } else if (GunItem.getAmmo(stack) <= 0) {
                if (dryCooldown <= 0 && (pending || !wasDown)) {
                    p.playSound(SoundEvents.BLOCK_DISPENSER_FAIL, 0.6F, 1.8F);
                    GunNetwork.CHANNEL.sendToServer(new GunNetwork.ReloadMsg()); // авто-перезарядка
                    dryCooldown = 10;
                }
            } else {
                boolean aiming = isAiming();
                GunNetwork.CHANNEL.sendToServer(new GunNetwork.FireMsg(p.rotationYaw, p.rotationPitch, aiming));
                cooldown = st.fireInterval;
                float mul = (aiming ? 0.65F : 1.0F) * (p.isSneaking() ? 0.8F : 1.0F);
                recoilPitchLeft += st.recoilPitch * mul * (0.85F + p.getRNG().nextFloat() * 0.3F);
                recoilYawLeft += (p.getRNG().nextFloat() * 2.0F - 1.0F) * st.recoilYaw * mul;
            }
        }
        if (pendingClickTicks > 0) pendingClickTicks--;
        wasDown = down;
    }

    /** Плавная отдача: подбрасываем камеру частями каждый кадр. */
    @SubscribeEvent
    public static void onRenderTick(TickEvent.RenderTickEvent event) {
        if (event.phase != TickEvent.Phase.START) return;
        ClientPlayerEntity p = Minecraft.getInstance().player;
        if (p == null || (Math.abs(recoilPitchLeft) < 0.001F && Math.abs(recoilYawLeft) < 0.001F)) return;
        float dp = Math.abs(recoilPitchLeft) < 0.05F ? recoilPitchLeft : recoilPitchLeft * 0.35F;
        float dy = Math.abs(recoilYawLeft) < 0.05F ? recoilYawLeft : recoilYawLeft * 0.35F;
        recoilPitchLeft -= dp;
        recoilYawLeft -= dy;
        p.rotationPitch = MathHelper.clamp(p.rotationPitch - dp, -90.0F, 90.0F);
        p.prevRotationPitch = MathHelper.clamp(p.prevRotationPitch - dp, -90.0F, 90.0F);
        p.rotationYaw += dy;
        p.prevRotationYaw += dy;
    }

    /** Прицеливание (ПКМ): зум. */
    @SubscribeEvent
    public static void onFov(FOVUpdateEvent event) {
        Minecraft mc = Minecraft.getInstance();
        if (event.getEntity() != mc.player || !isAiming()) return;
        GunStats st = heldGun(mc.player);
        if (st != null) event.setNewfov(event.getNewfov() * st.zoom);
    }
}
