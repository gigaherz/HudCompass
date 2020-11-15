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
import net.minecraft.util.RegistryKey;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.Util;
import net.minecraft.util.registry.Registry;
import net.minecraft.util.text.ChatType;
import net.minecraft.util.text.StringTextComponent;
import net.minecraft.world.World;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.common.util.Constants;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.EntityJoinWorldEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.loading.FMLPaths;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.File;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.nio.file.Path;

@Mod.EventBusSubscriber(value= Dist.CLIENT, modid= HudCompass.MODID, bus= Mod.EventBusSubscriber.Bus.FORGE)
public class ClientWaypointDatabase
{
    private static final Logger LOGGER = LogManager.getLogger();

    private static Path getPath(Minecraft mc)
    {
        if (mc.isIntegratedServerRunning())
        {
            return mc.getIntegratedServer().getDataDirectory().toPath().resolve("client_waypoints").resolve("waypoints.dat").toAbsolutePath();
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

            LOGGER.debug("Joined new dimension, loading...");

            Path filePath = getPath(mc);
            File file = filePath.toFile();
            if(!file.exists())
            {
                File backup = new File(file.getAbsolutePath() + ".bak");
                if (backup.exists())
                {
                    file = backup;
                    LOGGER.debug("File did not exist, but a backup was found...");
                }
            }
            if(file.exists())
            {
                try
                {
                    CompoundNBT tag = CompressedStreamTools.read(file);

                    pois.clear();

                    ListNBT list0 = tag.getList("Worlds", Constants.NBT.TAG_COMPOUND);
                    pois.deserializeNBT(list0);
                }
                catch (IOException e)
                {
                    e.printStackTrace();
                }
                LOGGER.debug("Done!");
            }
            else {
                LOGGER.debug("File did not exist.");
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
                LOGGER.debug("Changes detected, saving.");
                try
                {
                    Path filePath = getPath(mc);
                    File file = filePath.toFile();
                    if(file.exists())
                    {
                        LOGGER.debug("File already exists, keeping as backup.");
                        File backup = new File(file.getAbsolutePath() + ".bak");
                        //noinspection UnstableApiUsage
                        Files.copy(file, backup);
                    }
                    else
                    {
                        file.getParentFile().mkdirs();
                    }

                    CompoundNBT tag0 = new CompoundNBT();

                    ListNBT list0 = pois.serializeNBT();

                    tag0.put("Worlds", list0);

                    CompressedStreamTools.write(tag0, file);

                    pois.savedNumber = pois.changeNumber;

                    LOGGER.debug("Done!");
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
    public static void entityJoinWorld(EntityJoinWorldEvent event)
    {
        if (event.getWorld().isRemote && event.getEntity() instanceof ClientPlayerEntity)
        {
            populateFromDisk(Minecraft.getInstance());
        }
    }
}
