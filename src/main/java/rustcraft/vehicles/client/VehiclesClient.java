package rustcraft.vehicles.client;

import com.mojang.blaze3d.matrix.MatrixStack;
import net.minecraft.client.GameSettings;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.AbstractGui;
import net.minecraft.client.gui.FontRenderer;
import net.minecraft.client.resources.I18n;
import net.minecraft.client.settings.KeyBinding;
import net.minecraft.client.settings.PointOfView;
import net.minecraft.client.util.InputMappings;
import net.minecraft.entity.Entity;
import net.minecraft.util.SoundEvents;
import net.minecraftforge.client.event.RenderGameOverlayEvent;
import net.minecraftforge.client.settings.KeyConflictContext;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.client.registry.ClientRegistry;
import net.minecraftforge.fml.client.registry.RenderingRegistry;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;
import org.lwjgl.glfw.GLFW;
import rustcraft.vehicles.VehiclesModule;
import rustcraft.vehicles.client.model.MinicopterModel;
import rustcraft.vehicles.client.model.RhibModel;
import rustcraft.vehicles.client.model.ScrapHeliModel;
import rustcraft.vehicles.entity.AbstractVehicleEntity;
import rustcraft.vehicles.network.ActionPacket;
import rustcraft.vehicles.network.VehicleNetwork;

/** Клиентская часть транспорта: рендеры, клавиши, ввод пилота, камера, HUD, звук. */
public final class VehiclesClient {
    private VehiclesClient() {}

    public static final String CATEGORY = "key.categories.rustcraft.vehicles";
    public static KeyBinding KEY_ENGINE;
    public static KeyBinding KEY_DESCEND;

    private static AbstractVehicleEntity lastVehicle;
    private static PointOfView savedPov;
    private static int autoStartCooldown;

    public static void init(IEventBus modBus) {
        modBus.addListener(VehiclesClient::clientSetup);
        MinecraftForge.EVENT_BUS.addListener(VehiclesClient::onClientTick);
        MinecraftForge.EVENT_BUS.addListener(VehiclesClient::onOverlay);
    }

    private static void clientSetup(FMLClientSetupEvent event) {
        RenderingRegistry.registerEntityRenderingHandler(VehiclesModule.MINICOPTER.get(),
                m -> new VehicleRenderer<>(m, new MinicopterModel(), "minicopter", 1.0F, 1.3F));
        RenderingRegistry.registerEntityRenderingHandler(VehiclesModule.SCRAP_HELI.get(),
                m -> new VehicleRenderer<>(m, new ScrapHeliModel(), "scrap_heli", 1.2F, 1.9F));
        RenderingRegistry.registerEntityRenderingHandler(VehiclesModule.RHIB.get(),
                m -> new VehicleRenderer<>(m, new RhibModel(), "rhib", 0.4F, 1.3F));
        KEY_ENGINE = new KeyBinding("key.rustcraft.vehicle_engine", KeyConflictContext.IN_GAME,
                InputMappings.Type.KEYSYM, GLFW.GLFW_KEY_G, CATEGORY);
        KEY_DESCEND = new KeyBinding("key.rustcraft.vehicle_descend", KeyConflictContext.IN_GAME,
                InputMappings.Type.KEYSYM, GLFW.GLFW_KEY_LEFT_ALT, CATEGORY);
        event.enqueueWork(() -> {
            ClientRegistry.registerKeyBinding(KEY_ENGINE);
            ClientRegistry.registerKeyBinding(KEY_DESCEND);
        });
    }

    /** Called from the entity (client side) when its rotor/engine starts spinning. */
    public static void startEngineSound(AbstractVehicleEntity v) {
        Minecraft mc = Minecraft.getInstance();
        if (v.isHelicopter()) {
            mc.getSoundHandler().play(new EngineSound(v, SoundEvents.ENTITY_BEE_LOOP, 0.5F, 0.35F, 0.12F, 1.8F, true));
            mc.getSoundHandler().play(new EngineSound(v, SoundEvents.ITEM_ELYTRA_FLYING, 0.55F, 0.35F, 0.1F, 0.7F, false));
        } else {
            mc.getSoundHandler().play(new EngineSound(v, SoundEvents.ENTITY_BEE_LOOP, 0.75F, 0.35F, 0.55F, 1.4F, true));
        }
    }

    private static void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.START) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.world == null) {
            lastVehicle = null;
            savedPov = null;
            return;
        }
        GameSettings gs = mc.gameSettings;
        Entity riding = mc.player.getRidingEntity();
        AbstractVehicleEntity v = riding instanceof AbstractVehicleEntity ? (AbstractVehicleEntity) riding : null;

        // camera: third person while in a helicopter, restore on exit
        if (v != lastVehicle) {
            if (v != null && v.isHelicopter() && savedPov == null) {
                savedPov = gs.getPointOfView();
                gs.setPointOfView(PointOfView.THIRD_PERSON_BACK);
            } else if ((v == null || !v.isHelicopter()) && savedPov != null) {
                gs.setPointOfView(savedPov);
                savedPov = null;
            }
            lastVehicle = v;
        }

        if (autoStartCooldown > 0) autoStartCooldown--;
        if (v == null || v.getControllingPassenger() != mc.player) {
            while (KEY_ENGINE != null && KEY_ENGINE.isPressed()) {
                // swallow presses while not piloting
            }
            return;
        }
        boolean free = mc.currentScreen == null;
        boolean fwd = free && gs.keyBindForward.isKeyDown();
        boolean back = free && gs.keyBindBack.isKeyDown();
        boolean left = free && gs.keyBindLeft.isKeyDown();
        boolean right = free && gs.keyBindRight.isKeyDown();
        boolean up = free && gs.keyBindJump.isKeyDown();
        boolean down = free && ((KEY_DESCEND != null && KEY_DESCEND.isKeyDown()) || gs.keyBindSprint.isKeyDown());
        v.setInputs(fwd, back, left, right, up, down);

        while (KEY_ENGINE != null && KEY_ENGINE.isPressed()) {
            VehicleNetwork.CHANNEL.sendToServer(new ActionPacket(v.getEntityId(), AbstractVehicleEntity.ACTION_ENGINE_TOGGLE));
            autoStartCooldown = 40;
        }
        // auto-start: pressing W or Space with a cold engine starts it
        if (!v.isEngineOn() && v.getFuel() > 0.0F && (fwd || up) && autoStartCooldown <= 0) {
            VehicleNetwork.CHANNEL.sendToServer(new ActionPacket(v.getEntityId(), AbstractVehicleEntity.ACTION_ENGINE_START));
            autoStartCooldown = 40;
        }
    }

    private static void onOverlay(RenderGameOverlayEvent.Post event) {
        if (event.getType() != RenderGameOverlayEvent.ElementType.ALL) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.gameSettings.hideGUI || mc.gameSettings.showDebugInfo) return;
        Entity riding = mc.player.getRidingEntity();
        if (!(riding instanceof AbstractVehicleEntity)) return;
        AbstractVehicleEntity v = (AbstractVehicleEntity) riding;
        MatrixStack ms = event.getMatrixStack();
        FontRenderer fr = mc.fontRenderer;

        float fuel = v.getFuelCapacity() > 0 ? v.getFuel() / v.getFuelCapacity() : 0.0F;
        float hp = v.getHealth() / v.getMaxHealth();
        double dx = v.getPosX() - v.prevPosX, dy = v.getPosY() - v.prevPosY, dz = v.getPosZ() - v.prevPosZ;
        int kmh = (int) Math.round(Math.sqrt(dx * dx + dz * dz) * 20.0D * 3.6D);
        int vs = (int) Math.round(dy * 20.0D);
        boolean pilot = v.getControllingPassenger() == mc.player;

        int x = 6;
        int lines = v.isHelicopter() ? 6 : 5;
        int h = lines * 11 + (pilot ? 22 : 0);
        int y = event.getWindow().getScaledHeight() / 2 - h / 2;
        int w = 150;
        AbstractGui.fill(ms, x - 4, y - 4, x + w, y + h, 0x90000000);

        fr.drawStringWithShadow(ms, v.getDisplayName().getString(), x, y, 0xFFE0A040);
        y += 11;
        drawBar(ms, fr, I18n.format("hud.rustcraft.vehicle_fuel"), fuel, x, y, w - 8, fuel < 0.15F ? 0xFFE04030 : 0xFFE0C040);
        y += 11;
        drawBar(ms, fr, I18n.format("hud.rustcraft.vehicle_health"), hp, x, y, w - 8, hp < 0.3F ? 0xFFE04030 : 0xFF50C050);
        y += 11;
        if (v.isHelicopter()) {
            fr.drawStringWithShadow(ms, I18n.format("hud.rustcraft.vehicle_altitude",
                    (int) Math.floor(v.getPosY()), (int) Math.floor(v.getAltitudeAboveGround()), vs), x, y, 0xFFFFFFFF);
            y += 11;
        }
        fr.drawStringWithShadow(ms, I18n.format("hud.rustcraft.vehicle_speed", kmh), x, y, 0xFFFFFFFF);
        y += 11;
        boolean on = v.isEngineOn();
        fr.drawStringWithShadow(ms, I18n.format(on ? "hud.rustcraft.vehicle_engine_on" : "hud.rustcraft.vehicle_engine_off"),
                x, y, on ? 0xFF60E060 : 0xFFE06060);
        y += 11;
        if (pilot) {
            String eng = KEY_ENGINE != null ? KEY_ENGINE.func_238171_j_().getString() : "G";
            String desc = KEY_DESCEND != null ? KEY_DESCEND.func_238171_j_().getString() : "L-Alt";
            String hint1 = v.isHelicopter() ? I18n.format("hud.rustcraft.vehicle_hint_heli1", desc)
                    : I18n.format("hud.rustcraft.vehicle_hint_boat1");
            String hint2 = I18n.format("hud.rustcraft.vehicle_hint2", eng);
            ms.push();
            ms.scale(0.75F, 0.75F, 1.0F);
            fr.drawStringWithShadow(ms, hint1, x / 0.75F, y / 0.75F, 0xFFAAAAAA);
            fr.drawStringWithShadow(ms, hint2, x / 0.75F, (y + 10) / 0.75F, 0xFFAAAAAA);
            ms.pop();
        }
    }

    private static void drawBar(MatrixStack ms, FontRenderer fr, String label, float frac, int x, int y, int w, int color) {
        frac = Math.max(0.0F, Math.min(1.0F, frac));
        int pct = Math.round(frac * 100.0F);
        String text = label + " " + pct + "%";
        fr.drawStringWithShadow(ms, text, x, y, 0xFFFFFFFF);
        int bx = x + 72;
        int bw = w - 72;
        AbstractGui.fill(ms, bx, y + 1, bx + bw, y + 7, 0xFF303030);
        AbstractGui.fill(ms, bx, y + 1, bx + Math.round(bw * frac), y + 7, color);
    }
}
