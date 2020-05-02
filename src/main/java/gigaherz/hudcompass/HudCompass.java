package gigaherz.hudcompass;

import gigaherz.hudcompass.client.ClientHandler;
import gigaherz.hudcompass.client.HudOverlay;
import gigaherz.hudcompass.waypoints.BasicWaypoint;
import gigaherz.hudcompass.waypoints.PointInfoType;
import gigaherz.hudcompass.waypoints.PointsOfInterest;
import gigaherz.hudcompass.icons.BasicIconData;
import gigaherz.hudcompass.icons.IconDataSerializer;
import gigaherz.hudcompass.waypoints.SpawnPointInfo;
import net.minecraft.client.Minecraft;
import net.minecraft.client.entity.player.ClientPlayerEntity;
import net.minecraft.entity.player.ServerPlayerEntity;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.RegistryEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;
import net.minecraftforge.fml.event.lifecycle.FMLCommonSetupEvent;
import net.minecraftforge.fml.event.lifecycle.FMLLoadCompleteEvent;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import net.minecraftforge.fml.network.NetworkRegistry;
import net.minecraftforge.fml.network.simple.SimpleChannel;
import net.minecraftforge.registries.RegistryBuilder;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

@Mod(HudCompass.MODID)
public class HudCompass
{
    public static final String MODID = "hudcompass";

    public static HudCompass instance;

    public static final Logger LOGGER = LogManager.getLogger(MODID);

    public static final String CHANNEL = MODID;
    private static final String PROTOCOL_VERSION = "1.0";
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

        MinecraftForge.EVENT_BUS.addListener(this::playerTickEvent);
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
                new PointInfoType<>(BasicWaypoint::new).setRegistryName("basic"),
                new PointInfoType<>(SpawnPointInfo::new).setRegistryName("spawn")
        );
    }

    public void commonSetup(FMLCommonSetupEvent event)
    {
        PointsOfInterest.init();

        int messageNumber = 0;
        //channel.messageBuilder(SwapItems.class, messageNumber++).encoder(SwapItems::encode).decoder(SwapItems::new).consumer(SwapItems::handle).add();
        //channel.messageBuilder(BeltContentsChange.class, messageNumber++).encoder(BeltContentsChange::encode).decoder(BeltContentsChange::new).consumer(BeltContentsChange::handle).add();
        //channel.messageBuilder(OpenBeltSlotInventory.class, messageNumber++).encoder(OpenBeltSlotInventory::encode).decoder(OpenBeltSlotInventory::new).consumer(OpenBeltSlotInventory::handle).add();
        //channel.messageBuilder(ContainerSlotsHack.class, messageNumber++).encoder(ContainerSlotsHack::encode).decoder(ContainerSlotsHack::new).consumer(ContainerSlotsHack::handle).add();
        //channel.messageBuilder(SyncBeltSlotContents.class, messageNumber++).encoder(SyncBeltSlotContents::encode).decoder(SyncBeltSlotContents::new).consumer(SyncBeltSlotContents::handle).add();
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

    public static ResourceLocation location(String path)
    {
        return new ResourceLocation(MODID, path);
    }
}
