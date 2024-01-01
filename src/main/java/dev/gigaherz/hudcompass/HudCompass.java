package dev.gigaherz.hudcompass;

import dev.gigaherz.hudcompass.icons.BasicIconData;
import dev.gigaherz.hudcompass.icons.IconDataSerializer;
import dev.gigaherz.hudcompass.integrations.journeymap.JourneymapIntegration;
import dev.gigaherz.hudcompass.integrations.server.PlayerTracker;
import dev.gigaherz.hudcompass.integrations.server.SpawnPointPoints;
import dev.gigaherz.hudcompass.integrations.server.VanillaMapPoints;
import dev.gigaherz.hudcompass.network.*;
import dev.gigaherz.hudcompass.waypoints.BasicWaypoint;
import dev.gigaherz.hudcompass.waypoints.PointInfoType;
import dev.gigaherz.hudcompass.waypoints.PointsOfInterest;
import net.minecraft.core.Registry;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.IExtensionPoint;
import net.neoforged.fml.ModList;
import net.neoforged.fml.ModLoadingContext;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.fml.event.config.ModConfigEvent;
import net.neoforged.neoforge.attachment.AttachmentType;
import net.neoforged.neoforge.capabilities.RegisterCapabilitiesEvent;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.TickEvent;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlerEvent;
import net.neoforged.neoforge.network.registration.IPayloadRegistrar;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.NeoForgeRegistries;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

@Mod(HudCompass.MODID)
public class HudCompass
{
    public static final String MODID = "hudcompass";

    public static final Logger LOGGER = LogManager.getLogger(MODID);

    public static final ResourceKey<Registry<PointInfoType<?>>> POINT_INFO_TYPES_KEY = ResourceKey.createRegistryKey(location("point_info_types"));
    public static final ResourceKey<Registry<IconDataSerializer<?>>> ICON_DATA_SERIALIZERS_KEY = ResourceKey.createRegistryKey(location("icon_data_serializers"));

    public static final DeferredRegister<PointInfoType<?>> POINT_INFO_TYPES = DeferredRegister.create(POINT_INFO_TYPES_KEY, MODID);
    public static final DeferredRegister<IconDataSerializer<?>> ICON_DATA_SERIALIZERS = DeferredRegister.create(ICON_DATA_SERIALIZERS_KEY, MODID);
    public static final DeferredRegister<AttachmentType<?>> ATTACHMENT_TYPES = DeferredRegister.create(NeoForgeRegistries.ATTACHMENT_TYPES, MODID);

    public static final Registry<PointInfoType<?>> POINT_INFO_TYPES_REGISTRY = POINT_INFO_TYPES.makeRegistry(builder -> {});
    public static final Registry<IconDataSerializer<?>> ICON_DATA_SERIALIZERS_REGISTRY = ICON_DATA_SERIALIZERS.makeRegistry(builder -> {});

    public static final DeferredHolder<IconDataSerializer<?>, IconDataSerializer<BasicIconData>> POI_SERIALIZER = ICON_DATA_SERIALIZERS.register("poi", BasicIconData.Serializer::new);
    public static final DeferredHolder<IconDataSerializer<?>, IconDataSerializer<BasicIconData>> MAP_MARKER_SERIALIZER = ICON_DATA_SERIALIZERS.register("map_marker", BasicIconData.Serializer::new);

    public static final DeferredHolder<PointInfoType<?>, PointInfoType<BasicWaypoint>> BASIC_WAYPOINT = POINT_INFO_TYPES.register("basic", () -> new PointInfoType<>(BasicWaypoint::new));

    public static final DeferredHolder<AttachmentType<?>, AttachmentType<PointsOfInterest>> POINTS_OF_INTEREST_ATTACHMENT = ATTACHMENT_TYPES.register("poi_provider", () ->
            AttachmentType.serializable(PointsOfInterest::new).build()
    );

    public HudCompass(IEventBus modEventBus)
    {
        modEventBus.addListener(this::registerPackets);
        modEventBus.addListener(this::modConfig);
        modEventBus.addListener(this::registerCapabilities);

        POINT_INFO_TYPES.register(modEventBus);
        ICON_DATA_SERIALIZERS.register(modEventBus);
        ATTACHMENT_TYPES.register(modEventBus);

        NeoForge.EVENT_BUS.addListener(this::playerTickEvent);
        NeoForge.EVENT_BUS.addListener(this::playerLoggedIn);

        SpawnPointPoints.init();
        VanillaMapPoints.init(modEventBus);
        PlayerTracker.init(modEventBus);
        PointsOfInterest.init(modEventBus);

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
    }

    private void registerPackets(RegisterPayloadHandlerEvent event)
    {
        final IPayloadRegistrar registrar = event.registrar(MODID).versioned("1.1.0").optional();
        registrar.play(AddWaypoint.ID, AddWaypoint::new, play -> play.server(AddWaypoint::handle));
        registrar.play(RemoveWaypoint.ID, RemoveWaypoint::new, play -> play.server(RemoveWaypoint::handle));
        registrar.play(ClientHello.ID, ClientHello::new, play -> play.server(ClientHello::handle));
        registrar.play(UpdateWaypointsFromGui.ID, UpdateWaypointsFromGui::new, play -> play.server(UpdateWaypointsFromGui::handle));
        registrar.play(ServerHello.ID, ServerHello::new, play -> play.client(ServerHello::handle));
        registrar.play(SyncWaypointData.ID, SyncWaypointData::new, play -> play.client(SyncWaypointData::handle));
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

        if (event.getEntity() instanceof ServerPlayer sp && sp.connection.isConnected(ServerHello.ID))
        {
            PacketDistributor.PLAYER.with(sp).send(new ServerHello());
        }
    }

    public static ResourceLocation location(String path)
    {
        return new ResourceLocation(MODID, path);
    }
}
