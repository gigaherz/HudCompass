package dev.gigaherz.hudcompass.integrations.server;

import dev.gigaherz.hudcompass.ConfigData;
import dev.gigaherz.hudcompass.HudCompass;
import dev.gigaherz.hudcompass.icons.BasicIconData;
import dev.gigaherz.hudcompass.waypoints.BasicWaypoint;
import dev.gigaherz.hudcompass.waypoints.PointInfoType;
import dev.gigaherz.hudcompass.waypoints.PointsOfInterest;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.player.ServerPlayerEntity;
import net.minecraft.util.RegistryKey;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.vector.Vector3d;
import net.minecraft.world.World;
import net.minecraft.world.server.ServerWorld;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.RegistryObject;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import net.minecraftforge.registries.DeferredRegister;

import javax.annotation.Nullable;
import java.util.Objects;

public class SpawnPointPoints
{
    public static final SpawnPointPoints INSTANCE = new SpawnPointPoints();

    private static final ResourceLocation ADDON_ID = HudCompass.location("spawn_point");

    private static final DeferredRegister<PointInfoType<?>> PIT = HudCompass.makeDeferredPOI();

    public static void init()
    {
        IEventBus modEventBus = FMLJavaModLoadingContext.get().getModEventBus();

        PIT.register(modEventBus);

        MinecraftForge.EVENT_BUS.addListener(INSTANCE::playerTick);
    }

    private int counter = 0;
    private void playerTick(TickEvent.PlayerTickEvent event)
    {
        if ((++counter) > 20)
        {
            counter = 0;

            PlayerEntity player = event.player;
            if (player.world.isRemote)
                return;

            ServerPlayerEntity serverPlayer = (ServerPlayerEntity)player;

            player.getCapability(PointsOfInterest.INSTANCE).ifPresent((pois) -> {

                SpawnPointAddon addon = pois.getOrCreateAddonData(ADDON_ID, SpawnPointAddon::new);

                RegistryKey<World> worldKey = serverPlayer.func_241141_L_();
                BlockPos spawnPosition = serverPlayer.func_241140_K_();

                boolean enabled = ConfigData.COMMON.enableSpawnPointWaypoint.get();
                boolean hasWaypoint = addon.waypoint != null;
                boolean dimensionChanged = addon.spawnWorld != worldKey;
                boolean positionChanged = Objects.equals(addon.spawnPosition, spawnPosition);
                boolean waypointChanged = dimensionChanged || positionChanged;

                boolean hasBed = spawnPosition != null;

                if (hasWaypoint && (!enabled || !hasBed || waypointChanged))
                {
                    pois.get(addon.spawnWorld).removePoint(addon.waypoint);
                    addon.waypoint = null;
                    addon.spawnWorld = null;
                }

                if (enabled && hasBed && (!hasWaypoint || waypointChanged))
                {
                    addon.spawnWorld = worldKey;
                    addon.waypoint = new BasicWaypoint(BasicWaypoint.TYPE, Vector3d.copyCentered(spawnPosition), "Home", BasicIconData.mapMarker(8))
                            .dynamic();
                    pois.get(addon.spawnWorld).addPoint(addon.waypoint);
                }
            });
        }
    }

    private static class SpawnPointAddon
    {
        @Nullable
        public BasicWaypoint waypoint;
        public RegistryKey<World> spawnWorld;
        public BlockPos spawnPosition;
    }
}
