package dev.gigaherz.hudcompass;

import dev.gigaherz.hudcompass.client.ClientHandler;
import dev.gigaherz.hudcompass.client.HudOverlay;
import dev.gigaherz.hudcompass.icons.BasicIconData;
import dev.gigaherz.hudcompass.icons.IconDataSerializer;
import dev.gigaherz.hudcompass.integrations.journeymap.JourneymapIntegration;
import dev.gigaherz.hudcompass.integrations.server.PlayerTracker;
import dev.gigaherz.hudcompass.integrations.server.SpawnPointPoints;
import dev.gigaherz.hudcompass.integrations.server.VanillaMapPoints;
import dev.gigaherz.hudcompass.network.*;
import dev.gigaherz.hudcompass.waypoints.BasicWaypoint;
import dev.gigaherz.hudcompass.waypoints.PointInfo;
import dev.gigaherz.hudcompass.waypoints.PointInfoType;
import dev.gigaherz.hudcompass.waypoints.PointsOfInterest;
import net.minecraft.core.Registry;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.common.capabilities.RegisterCapabilitiesEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.EntityJoinLevelEvent;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.fml.IExtensionPoint;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.fml.ModLoadingContext;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.config.ModConfig;
import net.minecraftforge.fml.event.config.ModConfigEvent;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;
import net.minecraftforge.fml.event.lifecycle.FMLCommonSetupEvent;
import net.minecraftforge.fml.event.lifecycle.FMLLoadCompleteEvent;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import net.minecraftforge.network.NetworkDirection;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.PacketDistributor;
import net.minecraftforge.network.simple.SimpleChannel;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.IForgeRegistry;
import net.minecraftforge.registries.RegistryBuilder;
import net.minecraftforge.registries.RegistryObject;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.function.Supplier;
import java.util.function.UnaryOperator;

@Mod(HudCompass.MODID)
public class HudCompass
{
    public static final String MODID = "hudcompass";

    public static HudCompass instance;

    public static final Logger LOGGER = LogManager.getLogger(MODID);

    public static final ResourceKey<Registry<PointInfoType<?>>> POINT_INFO_TYPES_KEY = ResourceKey.createRegistryKey(location("point_info_types"));
    public static final ResourceKey<Registry<IconDataSerializer<?>>> ICON_DATA_SERIALIZERS_KEY = ResourceKey.createRegistryKey(location("icon_data_serializers"));

    public static final DeferredRegister<PointInfoType<?>> POINT_INFO_TYPES = DeferredRegister.create(POINT_INFO_TYPES_KEY, MODID);
    public static final DeferredRegister<IconDataSerializer<?>> ICON_DATA_SERIALIZERS = DeferredRegister.create(ICON_DATA_SERIALIZERS_KEY, MODID);

    public static final Supplier<IForgeRegistry<PointInfoType<?>>> POINT_INFO_TYPES_REGISTRY = POINT_INFO_TYPES.makeRegistry(() -> new RegistryBuilder<PointInfoType<?>>().disableSaving());
    public static final Supplier<IForgeRegistry<IconDataSerializer<?>>> ICON_DATA_SERIALIZERS_REGISTRY = ICON_DATA_SERIALIZERS.makeRegistry(() -> new RegistryBuilder<IconDataSerializer<?>>().disableSaving());

    public static final RegistryObject<IconDataSerializer<BasicIconData>> POI_SERIALIZER = ICON_DATA_SERIALIZERS.register("poi", BasicIconData.Serializer::new);
    public static final RegistryObject<IconDataSerializer<BasicIconData>> MAP_MARKER_SERIALIZER = ICON_DATA_SERIALIZERS.register("map_marker", BasicIconData.Serializer::new);

    public static final RegistryObject<PointInfoType<BasicWaypoint>> BASIC_WAYPOINT = POINT_INFO_TYPES.register("basic", () -> new PointInfoType<>(BasicWaypoint::new));

    private static final String PROTOCOL_VERSION = "1.1";
    public static SimpleChannel channel = NetworkRegistry.ChannelBuilder
            .named(new ResourceLocation(MODID, "main"))
            .clientAcceptedVersions((v) -> PROTOCOL_VERSION.equals(v) || NetworkRegistry.ABSENT.equals(v) || NetworkRegistry.ACCEPTVANILLA.equals(v))
            .serverAcceptedVersions((v) -> PROTOCOL_VERSION.equals(v) || NetworkRegistry.ABSENT.equals(v) || NetworkRegistry.ACCEPTVANILLA.equals(v))
            .networkProtocolVersion(() -> PROTOCOL_VERSION)
            .simpleChannel();

    public HudCompass()
    {
        instance = this;

        IEventBus modEventBus = FMLJavaModLoadingContext.get().getModEventBus();

        modEventBus.addListener(this::commonSetup);
        modEventBus.addListener(this::clientSetup);
        modEventBus.addListener(this::modConfig);
        modEventBus.addListener(this::registerCapabilities);

        POINT_INFO_TYPES.register(modEventBus);
        ICON_DATA_SERIALIZERS.register(modEventBus);

        MinecraftForge.EVENT_BUS.addListener(this::playerTickEvent);
        MinecraftForge.EVENT_BUS.addListener(this::playerLoggedIn);

        SpawnPointPoints.init();
        VanillaMapPoints.init();
        PlayerTracker.init();

        if (ModList.get().isLoaded("journeymap"))
        {
            JourneymapIntegration.staticInit();
        }

        ModLoadingContext modLoadingContext = ModLoadingContext.get();
        //modLoadingContext.registerConfig(ModConfig.Type.SERVER, ConfigData.SERVER_SPEC);
        modLoadingContext.registerConfig(ModConfig.Type.CLIENT, ConfigData.CLIENT_SPEC);
        modLoadingContext.registerConfig(ModConfig.Type.COMMON, ConfigData.COMMON_SPEC);

        //Make sure the mod being absent on the other network side does not cause the client to display the server as incompatible
        ModLoadingContext.get().registerExtensionPoint(IExtensionPoint.DisplayTest.class, () -> new IExtensionPoint.DisplayTest(() -> "", (a, b) -> true));
    }

    public void modConfig(ModConfigEvent event)
    {
        ModConfig config = event.getConfig();
        if (config.getSpec() == ConfigData.CLIENT_SPEC)
            ConfigData.refreshClient();
        else if (config.getSpec() == ConfigData.COMMON_SPEC)
            ConfigData.refreshCommon();
    }

    public void registerCapabilities(RegisterCapabilitiesEvent event)
    {
        PointsOfInterest.init(event);
    }

    public void commonSetup(FMLCommonSetupEvent event)
    {
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

    public void playerTickEvent(TickEvent.PlayerTickEvent event)
    {
        if (event.phase != TickEvent.Phase.END)
            return;

        if (!(event.player instanceof ServerPlayer player))
            return;

        PointsOfInterest.onTick(player);
    }

    public void playerLoggedIn(EntityJoinLevelEvent event)
    {
        if (ConfigData.COMMON.disableServerHello.get())
            return;

        if (event.getEntity() instanceof ServerPlayer sp && channel.isRemotePresent(sp.connection.getConnection()))
        {
            channel.send(PacketDistributor.PLAYER.with(() -> sp), new ServerHello());
        }
    }

    public static ResourceLocation location(String path)
    {
        return new ResourceLocation(MODID, path);
    }
}
