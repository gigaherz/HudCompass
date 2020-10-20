package dev.gigaherz.hudcompass.client;

import dev.gigaherz.hudcompass.HudCompass;
import dev.gigaherz.hudcompass.icons.BasicIconData;
import dev.gigaherz.hudcompass.network.AddWaypoint;
import dev.gigaherz.hudcompass.network.ClientHello;
import dev.gigaherz.hudcompass.network.RemoveWaypoint;
import dev.gigaherz.hudcompass.network.SyncWaypointData;
import dev.gigaherz.hudcompass.waypoints.BasicWaypoint;
import dev.gigaherz.hudcompass.waypoints.PointInfo;
import dev.gigaherz.hudcompass.waypoints.PointsOfInterest;
import net.minecraft.client.Minecraft;
import net.minecraft.client.entity.player.ClientPlayerEntity;
import net.minecraft.client.settings.KeyBinding;
import net.minecraft.client.util.InputMappings;
import net.minecraft.util.math.vector.Vector3d;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.fml.client.registry.ClientRegistry;

public class ClientHandler
{
    public static KeyBinding ADD_WAYPOINT;
    public static KeyBinding REMOVE_WAYPOINT;

    public static void init()
    {
        MinecraftForge.EVENT_BUS.addListener(ClientHandler::clientTickEvent);
    }

    public static void initKeybinds()
    {
        ClientRegistry.registerKeyBinding(ADD_WAYPOINT =
                new KeyBinding("key.hudcompass.add_waypoint", InputMappings.INPUT_INVALID.getKeyCode(), "key.hudcompass.category"));

        ClientRegistry.registerKeyBinding(REMOVE_WAYPOINT =
                new KeyBinding("key.hudcompass.remove_waypoint", InputMappings.INPUT_INVALID.getKeyCode(), "key.hudcompass.category"));

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
                Vector3d position = mc.player.getPositionVec();
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

        if (REMOVE_WAYPOINT.isPressed())
        {
            mc.player.getCapability(PointsOfInterest.INSTANCE).ifPresent((pois) -> {
                PointInfo<?> targetted = pois.getTargetted();

                if (targetted != null)
                {
                    if (pois.otherSideHasMod)
                    {
                        HudCompass.channel.sendToServer(new RemoveWaypoint(targetted));
                    }
                    else
                    {
                        pois.remove(targetted);
                    }
                }
            });

            //noinspection StatementWithEmptyBody
            while (ADD_WAYPOINT.isPressed())
            {
                // eat
            }
        }
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
