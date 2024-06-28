package dev.gigaherz.hudcompass.client;

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
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.loading.FMLPaths;
import net.neoforged.neoforge.event.TickEvent;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.File;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.Files;

@EventBusSubscriber(value = Dist.CLIENT, modid = HudCompass.MODID, bus = EventBusSubscriber.Bus.GAME)
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
        var pois = mc.player.getData(HudCompass.POINTS_OF_INTEREST_ATTACHMENT);
        {
            if (pois.otherSideHasMod)
                return;

            LOGGER.debug("Joined new dimension, loading...");

            Path path = getPath(mc);
            if (!Files.exists(path))
            {
                Path backup = Paths.get(path.toString() + ".bak");
                if (Files.exists(backup))
                {
                    path = backup;
                    LOGGER.debug("File did not exist, but a backup was found...");
                }
            }
            if (Files.exists(path))
            {
                try
                {
                    CompoundTag tag = NbtIo.read(path);

                    pois.clear();

                    ListTag list0 = tag.getList("Worlds", Tag.TAG_COMPOUND);
                    pois.deserializeNBT(mc.player.registryAccess(), list0);
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
        }
    }

    private static void saveToDisk(Minecraft mc)
    {
        var pois = mc.player.getData(HudCompass.POINTS_OF_INTEREST_ATTACHMENT);
        {
            if (pois.otherSideHasMod)
                return;

            if (pois.changeNumber > pois.savedNumber)
            {
                LOGGER.debug("Changes detected, saving.");
                try
                {
                    Path path = getPath(mc);
                    if (Files.exists(path))
                    {
                        LOGGER.debug("File already exists, keeping as backup.");
                        Path backup = Paths.get(path.toString() + ".bak");
                        Files.copy(path, backup);
                    }
                    else
                    {
                        Files.createDirectories(path);
                    }

                    CompoundTag tag0 = new CompoundTag();

                    ListTag list0 = pois.serializeNBT(mc.player.registryAccess());

                    tag0.put("Worlds", list0);

                    NbtIo.write(tag0, path);

                    pois.savedNumber = pois.changeNumber;

                    LOGGER.debug("Done!");
                }
                catch (IOException e)
                {
                    e.printStackTrace();
                }
            }
        }
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
