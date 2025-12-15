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
import net.neoforged.bus.api.IEventBus;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.event.lifecycle.FMLConstructModEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;
import net.neoforged.neoforge.client.gui.ConfigurationScreen;
import net.neoforged.neoforge.client.gui.IConfigScreenFactory;
import net.neoforged.neoforge.client.network.ClientPacketDistributor;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.network.PacketDistributor;

@Mod(value = HudCompass.MODID, dist = Dist.CLIENT)
public class ClientHandler
{
    public static KeyMapping.Category CATEGORY = new KeyMapping.Category(HudCompass.location("key.hudcompass.category"));
    public static KeyMapping ADD_WAYPOINT;
    public static KeyMapping REMOVE_WAYPOINT;
    public static KeyMapping EDIT_WAYPOINTS;

    public ClientHandler(ModContainer container, IEventBus modEventBus)
    {
        modEventBus.addListener(this::initKeybinds);

        NeoForge.EVENT_BUS.addListener(this::handleKeys);
        NeoForge.EVENT_BUS.addListener(this::clientTickEvent);

        container.registerExtensionPoint(IConfigScreenFactory.class, ConfigurationScreen::new);
    }

    public void initKeybinds(RegisterKeyMappingsEvent event)
    {
        event.registerCategory(CATEGORY);
        event.register(ADD_WAYPOINT =
                new KeyMapping("key.hudcompass.add_waypoint", InputConstants.UNKNOWN.getValue(), CATEGORY));

        event.register(REMOVE_WAYPOINT =
                new KeyMapping("key.hudcompass.remove_waypoint", InputConstants.UNKNOWN.getValue(), CATEGORY));

        event.register(EDIT_WAYPOINTS =
                new KeyMapping("key.hudcompass.edit_waypoints", InputConstants.UNKNOWN.getValue(), CATEGORY));
    }

    public void handleKeys(ClientTickEvent.Pre ev)
    {
        Minecraft mc = Minecraft.getInstance();

        if (mc.player == null)
            return;

        if (ADD_WAYPOINT != null && ADD_WAYPOINT.consumeClick())
        {
            var pois = mc.player.getData(HudCompass.POINTS_OF_INTEREST_ATTACHMENT);
            {
                Vec3 position = mc.player.position();
                String label = String.format("{%1.2f, %1.2f, %1.2f}", position.x(), position.y(), position.z());

                pois.get(mc.player.level()).addPointRequest(new BasicWaypoint(position, label, BasicIconData.mapDecoration("player_off_limits")));
            }

            //noinspection StatementWithEmptyBody
            while (ADD_WAYPOINT.consumeClick())
            {
                // eat
            }
        }

        if (REMOVE_WAYPOINT != null && REMOVE_WAYPOINT.consumeClick())
        {
            var pois = mc.player.getData(HudCompass.POINTS_OF_INTEREST_ATTACHMENT);
            {
                PointInfo<?> targetted = pois.getTargetted();

                if (targetted != null && !targetted.isDynamic())
                {
                    if (pois.otherSideHasMod)
                    {
                        ClientPacketDistributor.sendToServer(new RemoveWaypoint(targetted));
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

        if (EDIT_WAYPOINTS != null && EDIT_WAYPOINTS.consumeClick())
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

    public void clientTickEvent(ClientTickEvent.Pre event)
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
        ClientPacketDistributor.sendToServer(ClientHello.INSTANCE);
    }
}
