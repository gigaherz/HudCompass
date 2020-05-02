package gigaherz.hudcompass;

import gigaherz.hudcompass.waypoints.PointInfo;
import gigaherz.hudcompass.waypoints.PointsOfInterest;
import gigaherz.hudcompass.icons.BasicIconData;
import net.minecraft.client.Minecraft;
import net.minecraft.client.settings.KeyBinding;
import net.minecraft.client.util.InputMappings;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.fml.client.registry.ClientRegistry;
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

        if (ADD_WAYPOINT.isPressed())
        {
            Minecraft.getInstance().player.getCapability(PointsOfInterest.INSTANCE).ifPresent((pois) -> {
                pois.addPoint(new PointInfo(mc.player.getPosition(), String.format("autopoint-%d",++autoPoint), BasicIconData.mapMarker(7)));
            });


            while (ADD_WAYPOINT.isPressed())
            {
                // eat
            }
        }
    }

    public static boolean isKeyDown(KeyBinding keybind)
    {
        if (keybind.isInvalid())
            return false;
        return InputMappings.isKeyDown(Minecraft.getInstance().getMainWindow().getHandle(), keybind.getKey().getKeyCode())
                && keybind.getKeyConflictContext().isActive() && keybind.getKeyModifier().isActive(keybind.getKeyConflictContext());
    }
}
