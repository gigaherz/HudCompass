package dev.gigaherz.hudcompass;

import dev.gigaherz.hudcompass.icons.BasicIconData;
import dev.gigaherz.hudcompass.icons.IconDataSerializer;
//import dev.gigaherz.hudcompass.integrations.journeymap.JourneymapIntegration;
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
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.ModList;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.fml.event.config.ModConfigEvent;
import net.neoforged.neoforge.attachment.AttachmentType;
import net.neoforged.neoforge.capabilities.RegisterCapabilitiesEvent;
import net.neoforged.neoforge.client.gui.IConfigScreenFactory;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.NeoForgeRegistries;
import net.neoforged.neoforge.client.gui.ConfigurationScreen;

@Mod(HudCompass.MODID)
public class HudCompass
{
    public static final String MODID = "hudcompass";

    public static final ResourceKey<Registry<PointInfoType<?>>> POINT_INFO_TYPES_KEY = ResourceKey.createRegistryKey(location("point_info_types"));
    public static final ResourceKey<Registry<IconDataSerializer<?>>> ICON_DATA_SERIALIZERS_KEY = ResourceKey.createRegistryKey(location("icon_data_serializers"));

    public static final DeferredRegister<PointInfoType<?>> POINT_INFO_TYPES = DeferredRegister.create(POINT_INFO_TYPES_KEY, MODID);
    public static final DeferredRegister<IconDataSerializer<?>> ICON_DATA_SERIALIZERS = DeferredRegister.create(ICON_DATA_SERIALIZERS_KEY, MODID);
    public static final DeferredRegister<AttachmentType<?>> ATTACHMENT_TYPES = DeferredRegister.create(NeoForgeRegistries.ATTACHMENT_TYPES, MODID);

    public static final Registry<PointInfoType<?>> POINT_INFO_TYPES_REGISTRY = POINT_INFO_TYPES.makeRegistry(builder -> {});
    public static final Registry<IconDataSerializer<?>> ICON_DATA_SERIALIZERS_REGISTRY = ICON_DATA_SERIALIZERS.makeRegistry(builder -> {});

    public static final DeferredHolder<IconDataSerializer<?>, IconDataSerializer<BasicIconData>> BASIC_SERIALIZER = ICON_DATA_SERIALIZERS.register("basic", BasicIconData.Serializer::new);

    public static final DeferredHolder<PointInfoType<?>, PointInfoType<BasicWaypoint>> BASIC_WAYPOINT = POINT_INFO_TYPES.register("basic", () -> new PointInfoType<>(BasicWaypoint::new));

    public static final DeferredHolder<AttachmentType<?>, AttachmentType<PointsOfInterest>> POINTS_OF_INTEREST_ATTACHMENT = ATTACHMENT_TYPES.register("poi_provider", () ->
            AttachmentType.serializable(PointsOfInterest::new).copyOnDeath().copyHandler(PointsOfInterest::duplicate).build()
    );

    public HudCompass(ModContainer container, IEventBus modEventBus)
    {
        modEventBus.addListener(this::registerPackets);
        modEventBus.addListener(this::modConfigLoad);
        modEventBus.addListener(this::modConfigReload);
        modEventBus.addListener(this::registerCapabilities);

        POINT_INFO_TYPES.register(modEventBus);
        ICON_DATA_SERIALIZERS.register(modEventBus);
        ATTACHMENT_TYPES.register(modEventBus);

        NeoForge.EVENT_BUS.addListener(this::playerTickEvent);
        NeoForge.EVENT_BUS.addListener(this::playerLoggedIn);

        SpawnPointPoints.init();
        VanillaMapPoints.init(modEventBus);
        PlayerTracker.init(modEventBus);

        if (ModList.get().isLoaded("journeymap"))
        {
            //JourneymapIntegration.staticInit();
        }

        container.registerConfig(ModConfig.Type.CLIENT, ConfigData.CLIENT_SPEC);
        container.registerConfig(ModConfig.Type.COMMON, ConfigData.COMMON_SPEC);

    }

    public void modConfigLoad(ModConfigEvent.Loading event)
    {
        refreshConfig(event);
    }

    public void modConfigReload(ModConfigEvent.Reloading event)
    {
        refreshConfig(event);
    }

    private void refreshConfig(ModConfigEvent event)
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

    private void registerPackets(RegisterPayloadHandlersEvent event)
    {
        final PayloadRegistrar registrar = event.registrar(MODID).versioned("1.1.0").optional();
        registrar.playToServer(AddWaypoint.TYPE, AddWaypoint.STREAM_CODEC, AddWaypoint::handle);
        registrar.playToServer(RemoveWaypoint.TYPE, RemoveWaypoint.STREAM_CODEC, RemoveWaypoint::handle);
        registrar.playToServer(ClientHello.TYPE, ClientHello.STREAM_CODEC, ClientHello::handle);
        registrar.playToServer(UpdateWaypointsFromGui.TYPE, UpdateWaypointsFromGui.STREAM_CODEC, UpdateWaypointsFromGui::handle);
        registrar.playToClient(ServerHello.TYPE, ServerHello.STREAM_CODEC, ServerHello::handle);
        registrar.playToClient(SyncWaypointData.TYPE, SyncWaypointData.STREAM_CODEC, SyncWaypointData::handle);
    }

    public void playerTickEvent(PlayerTickEvent.Post event)
    {
        if (!(event.getEntity() instanceof ServerPlayer player))
            return;

        PointsOfInterest.onTick(player);
    }

    public void playerLoggedIn(EntityJoinLevelEvent event)
    {
        if (ConfigData.COMMON.disableServerHello.get())
            return;

        if (event.getEntity() instanceof ServerPlayer serverPlayer && serverPlayer.connection.hasChannel(ServerHello.ID))
        {
            PacketDistributor.sendToPlayer(serverPlayer, ServerHello.INSTANCE);
        }
    }

    public static ResourceLocation location(String path)
    {
        return ResourceLocation.fromNamespaceAndPath(MODID, path);
    }
}
