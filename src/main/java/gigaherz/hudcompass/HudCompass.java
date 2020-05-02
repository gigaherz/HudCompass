package gigaherz.hudcompass;

import gigaherz.hudcompass.waypoints.PointsOfInterest;
import gigaherz.hudcompass.icons.BasicIconData;
import gigaherz.hudcompass.icons.IconDataSerializer;
import net.minecraft.client.Minecraft;
import net.minecraft.entity.player.ServerPlayerEntity;
import net.minecraft.network.play.ServerPlayNetHandler;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.api.distmarker.Dist;
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
        modEventBus.addListener(this::commonSetup);
        modEventBus.addListener(this::clientSetup);
        modEventBus.addListener(this::loadComplete);
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    public void newRegistry(RegistryEvent.NewRegistry event)
    {
        new RegistryBuilder()
                .setName(HudCompass.location("icon_data_serializers"))
                .setType(IconDataSerializer.class)
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

    public void commonSetup(FMLCommonSetupEvent event)
    {
        PointsOfInterest.init();
    }

    public void clientSetup(FMLClientSetupEvent event)
    {

    }

    public void loadComplete(FMLLoadCompleteEvent event)
    {
        DistExecutor.runWhenOn(Dist.CLIENT, () -> () -> {
            ClientHandler.initKeybinds();
            HudOverlay.init();
        });
    }

    public void clientTickEvent(TickEvent.ClientTickEvent event)
    {
        Minecraft.getInstance().player.getCapability(PointsOfInterest.INSTANCE).ifPresent((pois) -> pois.updateByPosition(true));
    }

    public void playerTickEvent(TickEvent.PlayerTickEvent event)
    {
        if (!(event.player instanceof ServerPlayerEntity))
            return;

        ServerPlayerEntity player = (ServerPlayerEntity)event.player;
        ServerPlayNetHandler connection = player.connection;

    }

    public static ResourceLocation location(String path)
    {
        return new ResourceLocation(MODID, path);
    }
}
