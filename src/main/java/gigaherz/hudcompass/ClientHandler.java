package gigaherz.hudcompass;

import net.minecraft.client.Minecraft;
import net.minecraft.client.settings.KeyBinding;
import net.minecraft.client.util.InputMappings;
import net.minecraft.item.ItemStack;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.client.registry.ClientRegistry;
import net.minecraftforge.fml.common.gameevent.TickEvent;
import org.lwjgl.glfw.GLFW;

public class ClientHandler
{
    public static KeyBinding ADD_WAYPOINT;

    public static void initKeybinds()
    {
        ClientRegistry.registerKeyBinding(ADD_WAYPOINT =
                new KeyBinding("key.hudcompass.add_waypoint", GLFW.GLFW_KEY_R, "key.hudcompass.category"));

        MinecraftForge.EVENT_BUS.addListener(ClientHandler::handleKeys);
    }

    private static int autoPoint = 0;
    public static void handleKeys(TickEvent.ClientTickEvent ev)
    {
        Minecraft mc = Minecraft.getInstance();

        while (ADD_WAYPOINT.isPressed())
        {
            PointsOfInterest.CLIENT.addPoint(new PointInfo(mc.player.getPosition(), String.format("autopoint-%d",++autoPoint), 7));
        }
    }

    public static boolean isKeyDown(KeyBinding keybind)
    {
        return InputMappings.isKeyDown(Minecraft.getInstance().mainWindow.getHandle(), keybind.getKey().getKeyCode());
    }

}
