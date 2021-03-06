package dev.gigaherz.hudcompass;

import dev.gigaherz.hudcompass.client.ClientHandler;
import dev.gigaherz.hudcompass.client.HudOverlay;
import dev.gigaherz.hudcompass.icons.BasicIconData;
import dev.gigaherz.hudcompass.icons.IconDataSerializer;
import dev.gigaherz.hudcompass.integrations.server.SpawnPointPoints;
import dev.gigaherz.hudcompass.integrations.server.VanillaMapPoints;
import dev.gigaherz.hudcompass.integrations.xaerominimap.XaeroMinimapIntegration;
import dev.gigaherz.hudcompass.network.*;
import dev.gigaherz.hudcompass.waypoints.BasicWaypoint;
import dev.gigaherz.hudcompass.waypoints.PointInfoType;
import dev.gigaherz.hudcompass.waypoints.PointsOfInterest;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.ServerPlayerEntity;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.RegistryEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.EntityJoinWorldEvent;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.fml.ExtensionPoint;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.fml.ModLoadingContext;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.config.ModConfig;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;
import net.minecraftforge.fml.event.lifecycle.FMLCommonSetupEvent;
import net.minecraftforge.fml.event.lifecycle.FMLLoadCompleteEvent;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import net.minecraftforge.fml.network.FMLNetworkConstants;
import net.minecraftforge.fml.network.NetworkDirection;
import net.minecraftforge.fml.network.NetworkRegistry;
import net.minecraftforge.fml.network.PacketDistributor;
import net.minecraftforge.fml.network.simple.SimpleChannel;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.RegistryBuilder;
import org.apache.commons.lang3.tuple.Pair;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.function.Predicate;

@Mod(HudCompass.MODID)
public class HudCompass
{
    public static final String MODID = "hudcompass";

    public static HudCompass instance;

    public static final Logger LOGGER = LogManager.getLogger(MODID);

    public static final String CHANNEL = "main";
    private static final String PROTOCOL_VERSION = "1.1";
    public static SimpleChannel channel = NetworkRegistry.ChannelBuilder
            .named(new ResourceLocation(MODID, CHANNEL))
            .clientAcceptedVersions((v) -> PROTOCOL_VERSION.equals(v) || NetworkRegistry.ABSENT.equals(v) || NetworkRegistry.ACCEPTVANILLA.equals(v))
            .serverAcceptedVersions((v) -> PROTOCOL_VERSION.equals(v) || NetworkRegistry.ABSENT.equals(v) || NetworkRegistry.ACCEPTVANILLA.equals(v))
            .networkProtocolVersion(() -> PROTOCOL_VERSION)
            .simpleChannel();

    public HudCompass()
    {
        instance = this;

        IEventBus modEventBus = FMLJavaModLoadingContext.get().getModEventBus();

        modEventBus.addListener(this::newRegistry);
        modEventBus.addGenericListener(IconDataSerializer.class, this::iconDataSerializers);
        modEventBus.addGenericListener(PointInfoType.class, this::pointInfoTypes);
        modEventBus.addListener(this::commonSetup);
        modEventBus.addListener(this::clientSetup);
        modEventBus.addListener(this::loadComplete);
        modEventBus.addListener(this::modConfig);

        MinecraftForge.EVENT_BUS.addListener(this::playerTickEvent);
        MinecraftForge.EVENT_BUS.addListener(this::playerLoggedIn);

        SpawnPointPoints.init();
        VanillaMapPoints.init();

        if (ModList.get().isLoaded("xaerominimap"))
        {
            XaeroMinimapIntegration.init();
        }

        ModLoadingContext modLoadingContext = ModLoadingContext.get();
        //modLoadingContext.registerConfig(ModConfig.Type.SERVER, ConfigData.SERVER_SPEC);
        modLoadingContext.registerConfig(ModConfig.Type.CLIENT, ConfigData.CLIENT_SPEC);
        modLoadingContext.registerConfig(ModConfig.Type.COMMON, ConfigData.COMMON_SPEC);

        //Make sure the mod being absent on the other network side does not cause the client to display the server as incompatible
        ModLoadingContext.get().registerExtensionPoint(ExtensionPoint.DISPLAYTEST, () -> Pair.of(() -> FMLNetworkConstants.IGNORESERVERONLY, (a, b) -> true));
    }

    public void modConfig(ModConfig.ModConfigEvent event)
    {
        ModConfig config = event.getConfig();
        if (config.getSpec() == ConfigData.CLIENT_SPEC)
            ConfigData.refreshClient();
    }


    public static DeferredRegister<PointInfoType<?>> makeDeferredPOI()
    {
        //noinspection unchecked
        return (DeferredRegister<PointInfoType<?>>)(Object)
                DeferredRegister.create(PointInfoType.class, MODID);
    }

    public static DeferredRegister<IconDataSerializer<?>> makeDeferredIDS()
    {
        //noinspection unchecked
        return (DeferredRegister<IconDataSerializer<?>>)(Object)
                DeferredRegister.create(IconDataSerializer.class, MODID);
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    public void newRegistry(RegistryEvent.NewRegistry event)
    {
        new RegistryBuilder()
                .setName(HudCompass.location("icon_data_serializers"))
                .setType(IconDataSerializer.class)
                .disableSaving()
                .create();
        new RegistryBuilder()
                .setName(HudCompass.location("point_info_serializers"))
                .setType(PointInfoType.class)
                .disableSaving()
                .create();
    }

    public void iconDataSerializers(RegistryEvent.Register<IconDataSerializer<?>> event)
    {
        event.getRegistry().registerAll(
                BasicIconData.Serializer.POI_SERIALIZER.setRegistryName("poi"),
                BasicIconData.Serializer.MAP_SERIALIZER.setRegistryName("map_marker")
        );
    }

    public void pointInfoTypes(RegistryEvent.Register<PointInfoType<?>> event)
    {
        event.getRegistry().registerAll(
                new PointInfoType<>(BasicWaypoint::new).setRegistryName("basic")
        );
    }

    public void commonSetup(FMLCommonSetupEvent event)
    {
        PointsOfInterest.init();

        int messageNumber = 0;
        channel.messageBuilder(AddWaypoint.class, messageNumber++, NetworkDirection.PLAY_TO_SERVER).encoder(AddWaypoint::encode).decoder(AddWaypoint::new).consumer(AddWaypoint::handle).add();
        channel.messageBuilder(RemoveWaypoint.class, messageNumber++, NetworkDirection.PLAY_TO_SERVER).encoder(RemoveWaypoint::encode).decoder(RemoveWaypoint::new).consumer(RemoveWaypoint::handle).add();
        channel.messageBuilder(ServerHello.class, messageNumber++, NetworkDirection.PLAY_TO_CLIENT).encoder(ServerHello::encode).decoder(ServerHello::new).consumer(ServerHello::handle).add();
        channel.messageBuilder(ClientHello.class, messageNumber++, NetworkDirection.PLAY_TO_SERVER).encoder(ClientHello::encode).decoder(ClientHello::new).consumer(ClientHello::handle).add();
        channel.messageBuilder(SyncWaypointData.class, messageNumber++, NetworkDirection.PLAY_TO_CLIENT).encoder(SyncWaypointData::encode).decoder(SyncWaypointData::new).consumer(SyncWaypointData::handle).add();
        channel.messageBuilder(UpdateWaypointsFromGui.class, messageNumber++, NetworkDirection.PLAY_TO_SERVER).encoder(UpdateWaypointsFromGui::encode).decoder(UpdateWaypointsFromGui::new).consumer(UpdateWaypointsFromGui::handle).add();
        LOGGER.debug("Final message number: " + messageNumber);
    }

    public void clientSetup(FMLClientSetupEvent event)
    {
        ClientHandler.init();
    }

    public void loadComplete(FMLLoadCompleteEvent event)
    {
        DistExecutor.runWhenOn(Dist.CLIENT, () -> () -> {
            ClientHandler.initKeybinds();
            HudOverlay.init();
        });
    }

    public void playerTickEvent(TickEvent.PlayerTickEvent event)
    {
        if (!(event.player instanceof ServerPlayerEntity))
            return;

        ServerPlayerEntity player = (ServerPlayerEntity)event.player;

        PointsOfInterest.onTick(player);
    }

    public void playerLoggedIn(EntityJoinWorldEvent event)
    {
        if (ConfigData.COMMON.disableServerHello.get())
            return;

        Entity player = event.getEntity();
        if (player instanceof ServerPlayerEntity)
        {
            channel.send(PacketDistributor.PLAYER.with(() -> (ServerPlayerEntity) player), new ServerHello());
        }
    }

    public static ResourceLocation location(String path)
    {
        return new ResourceLocation(MODID, path);
    }
}
