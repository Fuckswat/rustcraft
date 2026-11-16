package rustcraft.progression.client;

import java.util.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.ScreenManager;
import net.minecraft.util.text.TranslationTextComponent;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;
import net.minecraft.client.settings.KeyBinding;
import net.minecraftforge.client.settings.KeyConflictContext;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.fml.client.registry.ClientRegistry;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import org.lwjgl.glfw.GLFW;
import rustcraft.progression.*;

public final class ProgressionClient {
    private ProgressionClient() {}
    public static final Set<String> LEARNED=new HashSet<>();
    public static long kitReady;
    public static double craftCost=1,researchCost=1;
    public static String message="";
    public static final KeyBinding CRAFT=new KeyBinding("key.rustcraft.craft",KeyConflictContext.IN_GAME,net.minecraft.client.util.InputMappings.Type.KEYSYM,GLFW.GLFW_KEY_C,"key.categories.rustcraft");
    public static void init(IEventBus bus) {bus.addListener(ProgressionClient::setup);MinecraftForge.EVENT_BUS.register(ProgressionClient.class);}
    private static void setup(FMLClientSetupEvent e) {e.enqueueWork(()->{ScreenManager.registerFactory(ProgressionModule.MENU.get(),ProgressionScreen::new);ClientRegistry.registerKeyBinding(CRAFT);});}
    @SubscribeEvent public static void tick(TickEvent.ClientTickEvent e) {if(e.phase!=TickEvent.Phase.END)return;Minecraft mc=Minecraft.getInstance();while(CRAFT.isPressed())if(mc.player!=null&&mc.currentScreen==null)ProgressionNetwork.CHANNEL.sendToServer(new ProgressionNetwork.Open());}
    public static void accept(ProgressionNetwork.State s) {LEARNED.clear();LEARNED.addAll(s.learned);kitReady=System.currentTimeMillis()+s.cooldown;craftCost=s.craftCost;researchCost=s.researchCost;message=s.message;if(!message.isEmpty()&&Minecraft.getInstance().player!=null)Minecraft.getInstance().player.sendStatusMessage(new TranslationTextComponent(message),true);}
}
