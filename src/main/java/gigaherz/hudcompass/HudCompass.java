package gigaherz.hudcompass;

import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.player.ServerPlayerEntity;
import net.minecraft.network.play.ServerPlayNetHandler;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.fml.ModLoadingContext;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.gameevent.TickEvent;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;
import net.minecraftforge.fml.event.lifecycle.FMLCommonSetupEvent;
import net.minecraftforge.fml.event.lifecycle.FMLLoadCompleteEvent;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import net.minecraftforge.fml.network.NetworkRegistry;
import net.minecraftforge.fml.network.simple.SimpleChannel;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

@Mod(HudCompass.MODID)
public class HudCompass
{
    public static final String MODID = "hudcompass";

    public static HudCompass instance;

    public static final Logger logger = LogManager.getLogger(MODID);

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

        ModLoadingContext modLoadingContext = ModLoadingContext.get();
        IEventBus modEventBus = FMLJavaModLoadingContext.get().getModEventBus();

        modEventBus.addListener(this::commonSetup);
        modEventBus.addListener(this::clientSetup);
        modEventBus.addListener(this::loadComplete);
    }

    public void commonSetup(FMLCommonSetupEvent event)
    {
        /*int messageNumber = 0;
        channel.registerMessage(messageNumber++, SwapItems.class, SwapItems::encode, SwapItems::new, SwapItems::handle);
        channel.registerMessage(messageNumber++, BeltContentsChange.class, BeltContentsChange::encode, BeltContentsChange::new, BeltContentsChange::handle);
        channel.registerMessage(messageNumber++, OpenBeltSlotInventory.class, OpenBeltSlotInventory::encode, OpenBeltSlotInventory::new, OpenBeltSlotInventory::handle);
        channel.registerMessage(messageNumber++, ContainerSlotsHack.class, ContainerSlotsHack::encode, ContainerSlotsHack::new, ContainerSlotsHack::handle);
        channel.registerMessage(messageNumber++, SyncBeltSlotContents.class, SyncBeltSlotContents::encode, SyncBeltSlotContents::new, SyncBeltSlotContents::handle);
        logger.debug("Final message number: " + messageNumber);
        */
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
        PointsOfInterest.CLIENT.updateByPosition(true);
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
