package dev.gigaherz.hudcompass.client;

import com.mojang.blaze3d.platform.InputConstants;
import dev.gigaherz.hudcompass.HudCompass;
import dev.gigaherz.hudcompass.icons.BasicIconData;
import dev.gigaherz.hudcompass.network.ClientHello;
import dev.gigaherz.hudcompass.network.RemoveWaypoint;
import dev.gigaherz.hudcompass.waypoints.BasicWaypoint;
import dev.gigaherz.hudcompass.waypoints.PointInfo;
import dev.gigaherz.hudcompass.waypoints.PointsOfInterest;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;
import net.neoforged.neoforge.event.TickEvent;
import net.neoforged.neoforge.network.PacketDistributor;

@Mod.EventBusSubscriber(value = Dist.CLIENT, modid = HudCompass.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public class ClientHandler
{
    public static KeyMapping ADD_WAYPOINT;
    public static KeyMapping REMOVE_WAYPOINT;
    public static KeyMapping EDIT_WAYPOINTS;

    @Mod.EventBusSubscriber(value = Dist.CLIENT, modid = HudCompass.MODID, bus = Mod.EventBusSubscriber.Bus.MOD)
    public static class ModBus
    {
        @SubscribeEvent
        public static void initKeybinds(RegisterKeyMappingsEvent event)
        {
            event.register(ADD_WAYPOINT =
                    new KeyMapping("key.hudcompass.add_waypoint", InputConstants.UNKNOWN.getValue(), "key.hudcompass.category"));

            event.register(REMOVE_WAYPOINT =
                    new KeyMapping("key.hudcompass.remove_waypoint", InputConstants.UNKNOWN.getValue(), "key.hudcompass.category"));

            event.register(EDIT_WAYPOINTS =
                    new KeyMapping("key.hudcompass.edit_waypoints", InputConstants.UNKNOWN.getValue(), "key.hudcompass.category"));
        }
    }

    @SubscribeEvent
    public static void handleKeys(TickEvent.ClientTickEvent ev)
    {
        Minecraft mc = Minecraft.getInstance();

        if (mc.player == null)
            return;

        if (ADD_WAYPOINT.consumeClick())
        {
            var pois = mc.player.getData(HudCompass.POINTS_OF_INTEREST_ATTACHMENT);
            {
                Vec3 position = mc.player.position();
                String label = String.format("{%1.2f, %1.2f, %1.2f}", position.x(), position.y(), position.z());

                pois.get(mc.player.level()).addPointRequest(new BasicWaypoint(position, label, BasicIconData.mapMarker(7)));
            }

            //noinspection StatementWithEmptyBody
            while (ADD_WAYPOINT.consumeClick())
            {
                // eat
            }
        }

        if (REMOVE_WAYPOINT.consumeClick())
        {
            var pois = mc.player.getData(HudCompass.POINTS_OF_INTEREST_ATTACHMENT);
            {
                PointInfo<?> targetted = pois.getTargetted();

                if (targetted != null && !targetted.isDynamic())
                {
                    if (pois.otherSideHasMod)
                    {
                        PacketDistributor.SERVER.noArg().send(new RemoveWaypoint(targetted));
                    }
                    else
                    {
                        pois.get(mc.player.level()).removePointRequest(targetted);
                    }
                }
            }

            //noinspection StatementWithEmptyBody
            while (ADD_WAYPOINT.consumeClick())
            {
                // eat
            }
        }

        if (EDIT_WAYPOINTS.consumeClick())
        {
            var pois = mc.player.getData(HudCompass.POINTS_OF_INTEREST_ATTACHMENT);
            {
                mc.setScreen(new ClientWaypointManagerScreen(pois));
            }

            //noinspection StatementWithEmptyBody
            while (ADD_WAYPOINT.consumeClick())
            {
                // eat
            }
        }
    }

    @SubscribeEvent
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
        PacketDistributor.SERVER.noArg().send(new ClientHello());
    }
}
