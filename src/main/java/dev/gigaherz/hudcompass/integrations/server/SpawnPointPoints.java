package dev.gigaherz.hudcompass.integrations.server;

import dev.gigaherz.hudcompass.ConfigData;
import dev.gigaherz.hudcompass.HudCompass;
import dev.gigaherz.hudcompass.icons.BasicIconData;
import dev.gigaherz.hudcompass.waypoints.BasicWaypoint;
import dev.gigaherz.hudcompass.waypoints.PointInfoType;
import dev.gigaherz.hudcompass.waypoints.PointsOfInterest;
import net.minecraft.world.entity.player.Player;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.level.Level;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.IEventBus;
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

            Player player = event.player;
            if (player.level.isClientSide)
                return;

            ServerPlayer serverPlayer = (ServerPlayer)player;

            player.getCapability(PointsOfInterest.INSTANCE).ifPresent((pois) -> {

                SpawnPointAddon addon = pois.getOrCreateAddonData(ADDON_ID, SpawnPointAddon::new);

                ResourceKey<Level> worldKey = serverPlayer.getRespawnDimension();
                BlockPos spawnPosition = serverPlayer.getRespawnPosition();

                boolean enabled = ConfigData.COMMON.enableSpawnPointWaypoint.get();
                boolean hasWaypoint = addon.waypoint != null;
                boolean dimensionChanged = addon.spawnWorld != worldKey;
                boolean positionChanged = !Objects.equals(addon.spawnPosition, spawnPosition);
                boolean waypointChanged = dimensionChanged || positionChanged;

                boolean hasBed = spawnPosition != null;

                if (hasWaypoint && (!enabled || !hasBed || waypointChanged))
                {
                    pois.get(addon.spawnWorld).removePoint(addon.waypoint);
                    addon.waypoint = null;
                    addon.spawnWorld = null;
                    addon.spawnPosition = null;
                }

                if (enabled && hasBed && (!hasWaypoint || waypointChanged))
                {
                    addon.spawnWorld = worldKey;
                    addon.spawnPosition = spawnPosition;
                    addon.waypoint = new BasicWaypoint(BasicWaypoint.TYPE, Vec3.atCenterOf(spawnPosition), "Home", BasicIconData.mapMarker(8))
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
        public ResourceKey<Level> spawnWorld;
        public BlockPos spawnPosition;
    }
}
