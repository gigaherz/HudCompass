package dev.gigaherz.hudcompass.client;

import com.google.common.io.Files;
import dev.gigaherz.hudcompass.HudCompass;
import dev.gigaherz.hudcompass.waypoints.PointInfo;
import dev.gigaherz.hudcompass.waypoints.PointInfoRegistry;
import dev.gigaherz.hudcompass.waypoints.PointsOfInterest;
import net.minecraft.client.Minecraft;
import net.minecraft.client.entity.player.ClientPlayerEntity;
import net.minecraft.nbt.CompoundNBT;
import net.minecraft.nbt.CompressedStreamTools;
import net.minecraft.nbt.ListNBT;
import net.minecraft.network.NetworkManager;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.common.util.Constants;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.EntityJoinWorldEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.loading.FMLPaths;

import java.io.File;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.nio.file.Path;

@Mod.EventBusSubscriber(value= Dist.CLIENT, modid= HudCompass.MODID, bus= Mod.EventBusSubscriber.Bus.FORGE)
public class ClientWaypointDatabase
{
    private static Path getPath(Minecraft mc)
    {
        if (mc.isIntegratedServerRunning())
        {
            return mc.getIntegratedServer().getDataDirectory().toPath().resolve("waypoints.dat").toAbsolutePath();
        }
        else
        {
            NetworkManager networkManager = mc.player.connection.getNetworkManager();
            SocketAddress addr = networkManager.getRemoteAddress();
            String address;
            if (addr instanceof InetSocketAddress)
            {
                InetSocketAddress ip = ((InetSocketAddress) addr);
                address = ip.getHostString() + "_" + ip.getPort();
            }
            else
            {
                address = addr.toString();
            }
            ResourceLocation dim = mc.player.world.getDimensionKey().getLocation();
            String dimension = dim.getNamespace() + "_" + dim.getPath();
            return FMLPaths.GAMEDIR.get().resolve("server_waypoints").resolve(address).resolve(dimension).resolve("waypoints.dat").toAbsolutePath();
        }
    }

    private static void populateFromDisk(Minecraft mc)
    {
        mc.player.getCapability(PointsOfInterest.INSTANCE).ifPresent((pois) -> {
            if (pois.otherSideHasMod)
                return;

            Path filePath = getPath(mc);
            File file = filePath.toFile();
            if(!file.exists())
            {
                File backup = new File(file.getAbsolutePath() + ".bak");
                if (backup.exists())
                {
                    file = backup;
                }
            }
            if(file.exists())
            {
                try
                {
                    CompoundNBT tag = CompressedStreamTools.read(file);

                    pois.clear();

                    ListNBT list = tag.getList("Waypoints", Constants.NBT.TAG_COMPOUND);
                    for (int i = 0; i < list.size(); i++)
                    {
                        CompoundNBT waypointTag = list.getCompound(i);
                        PointInfo<?> waypoint = PointInfoRegistry.deserializePoint(waypointTag);
                        pois.addPoint(waypoint);
                    }
                }
                catch (IOException e)
                {
                    e.printStackTrace();
                }
            }
        });
    }

    private static void saveToDisk(Minecraft mc)
    {
        mc.player.getCapability(PointsOfInterest.INSTANCE).ifPresent((pois) -> {
            if (pois.otherSideHasMod)
                return;

            if (pois.changeNumber > pois.savedNumber)
            {
                try
                {
                    Path filePath = getPath(mc);
                    File file = filePath.toFile();
                    if(file.exists())
                    {
                        File backup = new File(file.getAbsolutePath() + ".bak");
                        //noinspection UnstableApiUsage
                        Files.copy(file, backup);
                    }

                    CompoundNBT tag = new CompoundNBT();

                    ListNBT list = new ListNBT();
                    for (PointInfo<?> point : pois.getPoints())
                    {
                        if (point.isDynamic()) continue;
                        CompoundNBT waypointTag = PointInfoRegistry.serializePoint(point);
                        list.add(waypointTag);
                    }

                    tag.put("Waypoints", list);

                    file.getParentFile().mkdirs();

                    CompressedStreamTools.write(tag, file);

                    pois.savedNumber = pois.changeNumber;
                }
                catch (IOException e)
                {
                    e.printStackTrace();
                }
            }
        });
    }

    @SubscribeEvent
    public static void clientTick(TickEvent.ClientTickEvent event)
    {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player != null)
        {
            saveToDisk(mc);
        }
    }

    @SubscribeEvent
    public static void serverStarted(EntityJoinWorldEvent event)
    {
        if (event.getWorld().isRemote && event.getEntity() instanceof ClientPlayerEntity)
        {
            populateFromDisk(Minecraft.getInstance());
        }
    }
}
