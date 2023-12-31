package dev.gigaherz.hudcompass.client;

import com.google.common.io.Files;
import dev.gigaherz.hudcompass.HudCompass;
import dev.gigaherz.hudcompass.waypoints.PointsOfInterest;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtIo;
import net.minecraft.nbt.Tag;
import net.minecraft.network.Connection;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.EntityJoinLevelEvent;
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

@Mod.EventBusSubscriber(value = Dist.CLIENT, modid = HudCompass.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public class ClientWaypointDatabase
{
    private static final Logger LOGGER = LogManager.getLogger();

    private static Path getPath(Minecraft mc)
    {
        if (mc.isLocalServer())
        {
            return mc.getSingleplayerServer().getServerDirectory().toPath().resolve("client_waypoints").resolve("waypoints.dat").toAbsolutePath();
        }
        else
        {
            Connection networkManager = mc.player.connection.getConnection();
            SocketAddress addr = networkManager.getRemoteAddress();
            String address;
            if (addr instanceof InetSocketAddress ip)
            {
                address = ip.getHostString() + "_" + ip.getPort();
            }
            else
            {
                address = addr.toString();
            }
            ResourceLocation dim = mc.player.level().dimension().location();
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
            if (!file.exists())
            {
                File backup = new File(file.getAbsolutePath() + ".bak");
                if (backup.exists())
                {
                    file = backup;
                    LOGGER.debug("File did not exist, but a backup was found...");
                }
            }
            if (file.exists())
            {
                try
                {
                    CompoundTag tag = NbtIo.read(file);

                    pois.clear();

                    ListTag list0 = tag.getList("Worlds", Tag.TAG_COMPOUND);
                    pois.read(list0);
                }
                catch (IOException e)
                {
                    e.printStackTrace();
                }
                LOGGER.debug("Done!");
            }
            else
            {
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
                    if (file.exists())
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

                    CompoundTag tag0 = new CompoundTag();

                    ListTag list0 = pois.write();

                    tag0.put("Worlds", list0);

                    NbtIo.write(tag0, file);

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
    public static void entityJoinWorld(EntityJoinLevelEvent event)
    {
        if (event.getLevel().isClientSide && event.getEntity() instanceof LocalPlayer)
        {
            populateFromDisk(Minecraft.getInstance());
        }
    }
}
