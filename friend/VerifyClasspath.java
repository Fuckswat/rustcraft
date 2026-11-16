// Local launcher smoke check. Does not initialize Minecraft or open a window.
public final class VerifyClasspath {
    public static void main(String[] args) throws Exception {
        String[] classes = {
            "cpw.mods.modlauncher.Launcher",
            "net.minecraftforge.fml.loading.FMLLoader",
            "org.lwjgl.glfw.GLFW",
            "net.minecraft.client.main.Main"
        };
        for (String name : classes) {
            Class<?> type = Class.forName(name, false, VerifyClasspath.class.getClassLoader());
            // Obfuscated Minecraft signatures are resolved by Forge transforms at game startup.
            if (!name.equals("net.minecraft.client.main.Main")) type.getDeclaredMethods();
            System.out.println("OK " + name);
        }
        System.out.println("Java " + System.getProperty("java.version") + ": classpath verified, game not started.");
    }
}
