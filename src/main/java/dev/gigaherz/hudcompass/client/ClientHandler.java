package dev.gigaherz.hudcompass.client;

import dev.gigaherz.hudcompass.HudCompass;
import dev.gigaherz.hudcompass.icons.BasicIconData;
import dev.gigaherz.hudcompass.network.ClientHello;
import dev.gigaherz.hudcompass.network.RemoveWaypoint;
import dev.gigaherz.hudcompass.waypoints.BasicWaypoint;
import dev.gigaherz.hudcompass.waypoints.PointInfo;
import dev.gigaherz.hudcompass.waypoints.PointsOfInterest;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.KeyMapping;
import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.client.ClientRegistry;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.TickEvent;

public class ClientHandler
{
    public static KeyMapping ADD_WAYPOINT;
    public static KeyMapping REMOVE_WAYPOINT;
    public static KeyMapping EDIT_WAYPOINTS;

    public static void init()
    {
        MinecraftForge.EVENT_BUS.addListener(ClientHandler::clientTickEvent);
    }

    public static void initKeybinds()
    {
        ClientRegistry.registerKeyBinding(ADD_WAYPOINT =
                new KeyMapping("key.hudcompass.add_waypoint", InputConstants.UNKNOWN.getValue(), "key.hudcompass.category"));

        ClientRegistry.registerKeyBinding(REMOVE_WAYPOINT =
                new KeyMapping("key.hudcompass.remove_waypoint", InputConstants.UNKNOWN.getValue(), "key.hudcompass.category"));

        ClientRegistry.registerKeyBinding(EDIT_WAYPOINTS =
                new KeyMapping("key.hudcompass.edit_waypoints", InputConstants.UNKNOWN.getValue(), "key.hudcompass.category"));

        MinecraftForge.EVENT_BUS.addListener(ClientHandler::handleKeys);
    }

    public static void handleKeys(TickEvent.ClientTickEvent ev)
    {
        Minecraft mc = Minecraft.getInstance();

        if (mc.player == null)
            return;

        if (ADD_WAYPOINT.consumeClick())
        {
            mc.player.getCapability(PointsOfInterest.INSTANCE).ifPresent((pois) -> {
                Vec3 position = mc.player.position();
                String label = String.format("{%1.2f, %1.2f, %1.2f}", position.x(), position.y(), position.z());

                pois.get(mc.player.level).addPointRequest(new BasicWaypoint(position, label, BasicIconData.mapMarker(7)));
            });

            //noinspection StatementWithEmptyBody
            while (ADD_WAYPOINT.consumeClick())
            {
                // eat
            }
        }

        if (REMOVE_WAYPOINT.consumeClick())
        {
            mc.player.getCapability(PointsOfInterest.INSTANCE).ifPresent((pois) -> {
                PointInfo<?> targetted = pois.getTargetted();

                if (targetted != null && !targetted.isDynamic())
                {
                    if (pois.otherSideHasMod)
                    {
                        HudCompass.channel.sendToServer(new RemoveWaypoint(targetted));
                    }
                    else
                    {
                        pois.get(mc.player.level).removePointRequest(targetted);
                    }
                }
            });

            //noinspection StatementWithEmptyBody
            while (ADD_WAYPOINT.consumeClick())
            {
                // eat
            }
        }

        if (EDIT_WAYPOINTS.consumeClick())
        {
            mc.player.getCapability(PointsOfInterest.INSTANCE).ifPresent((pois) -> {
                mc.setScreen(new ClientWaypointManagerScreen(pois));
            });

            //noinspection StatementWithEmptyBody
            while (ADD_WAYPOINT.consumeClick())
            {
                // eat
            }
        }
    }

    public static void clientTickEvent(TickEvent.ClientTickEvent event)
    {
        LocalPlayer player = Minecraft.getInstance().player;
        if (player != null)
        {
            PointsOfInterest.onTick(player);
        }
    }

    public static void handleWaypointSync(byte[] bytes)
    {
        LocalPlayer player = Minecraft.getInstance().player;
        if (player == null)
            return;
        PointsOfInterest.handleSync(player, bytes);
    }

    public static void handleServerHello()
    {
        LocalPlayer player = Minecraft.getInstance().player;
        if (player != null)
        {
            PointsOfInterest.remoteHello(player);
        }
        HudCompass.channel.sendToServer(new ClientHello());
    }
}
