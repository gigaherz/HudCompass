package gigaherz.hudcompass.client;

import gigaherz.hudcompass.HudCompass;
import gigaherz.hudcompass.network.AddWaypoint;
import gigaherz.hudcompass.network.ClientHello;
import gigaherz.hudcompass.network.ServerHello;
import gigaherz.hudcompass.network.SyncWaypointData;
import gigaherz.hudcompass.waypoints.BasicWaypoint;
import gigaherz.hudcompass.waypoints.PointsOfInterest;
import gigaherz.hudcompass.icons.BasicIconData;
import net.minecraft.client.Minecraft;
import net.minecraft.client.entity.player.ClientPlayerEntity;
import net.minecraft.client.settings.KeyBinding;
import net.minecraft.client.util.InputMappings;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import net.minecraftforge.client.settings.KeyModifier;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.fml.client.registry.ClientRegistry;
import org.lwjgl.glfw.GLFW;

public class ClientHandler
{
    public static KeyBinding ADD_WAYPOINT;

    public static void init()
    {
        MinecraftForge.EVENT_BUS.addListener(ClientHandler::clientTickEvent);
    }

    public static void initKeybinds()
    {
        ClientRegistry.registerKeyBinding(ADD_WAYPOINT =
                new KeyBinding("key.hudcompass.add_waypoint", GLFW.GLFW_KEY_R, "key.hudcompass.category"));

        MinecraftForge.EVENT_BUS.addListener(ClientHandler::handleKeys);
    }

    public static void handleKeys(TickEvent.ClientTickEvent ev)
    {
        Minecraft mc = Minecraft.getInstance();

        if (mc.player == null)
            return;

        if (ADD_WAYPOINT.isPressed())
        {
            mc.player.getCapability(PointsOfInterest.INSTANCE).ifPresent((pois) -> {
                Vec3d position = mc.player.getPositionVec();
                String label = String.format("{%1.2f, %1.2f, %1.2f}", position.getX(), position.getY(), position.getZ());

                if (pois.otherSideHasMod)
                {
                    HudCompass.channel.sendToServer(new AddWaypoint(
                        label, position.getX(), position.getY(), position.getZ(), true, 7
                    ));
                }
                else
                {
                    pois.addPoint(new BasicWaypoint(position, label, BasicIconData.mapMarker(7)));
                }
            });

            //noinspection StatementWithEmptyBody
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

    public static void clientTickEvent(TickEvent.ClientTickEvent event)
    {
        ClientPlayerEntity player = Minecraft.getInstance().player;
        if (player != null)
        {
            PointsOfInterest.onTick(player);
        }
    }

    public static void handleWaypointSync(SyncWaypointData packet)
    {
        ClientPlayerEntity player = Minecraft.getInstance().player;
        if (player == null)
            return;
        PointsOfInterest.handleSync(player, packet);
    }

    public static void handleServerHello()
    {
        ClientPlayerEntity player = Minecraft.getInstance().player;
        if (player != null)
        {
            PointsOfInterest.remoteHello(player);
        }
        HudCompass.channel.sendToServer(new ClientHello());
    }
}
