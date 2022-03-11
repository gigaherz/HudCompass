package dev.gigaherz.hudcompass.integrations.server;

import com.google.common.collect.Maps;
import com.google.common.collect.Sets;
import dev.gigaherz.hudcompass.ConfigData;
import dev.gigaherz.hudcompass.HudCompass;
import dev.gigaherz.hudcompass.icons.BasicIconData;
import dev.gigaherz.hudcompass.waypoints.PointInfo;
import dev.gigaherz.hudcompass.waypoints.PointInfoType;
import dev.gigaherz.hudcompass.waypoints.PointsOfInterest;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.MapItem;
import net.minecraft.world.level.saveddata.maps.MapBanner;
import net.minecraft.world.level.saveddata.maps.MapDecoration;
import net.minecraft.world.level.saveddata.maps.MapItemSavedData;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.RegistryObject;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.Collections;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

public class VanillaMapPoints
{
    public static final VanillaMapPoints INSTANCE = new VanillaMapPoints();

    private static final ResourceLocation ADDON_ID = HudCompass.location("vanilla_map");

    private static final DeferredRegister<PointInfoType<?>> PIT = HudCompass.POINT_INFO_TYPES;
    public static final RegistryObject<PointInfoType<MapDecorationWaypoint>> DECORATION_TYPE = PIT.register("map_decoration", () -> new PointInfoType<>(MapDecorationWaypoint::new));
    public static final RegistryObject<PointInfoType<MapBannerWaypoint>> BANNER_TYPE = PIT.register("map_banner", () -> new PointInfoType<>(MapBannerWaypoint::new));

    public static void init()
    {
        IEventBus modEventBus = FMLJavaModLoadingContext.get().getModEventBus();

        modEventBus.addListener(INSTANCE::clientSetup);

        //MinecraftForge.EVENT_BUS.addListener(INSTANCE::clientTick);
        MinecraftForge.EVENT_BUS.addListener(INSTANCE::playerTick);
    }

    private void clientSetup(FMLClientSetupEvent event)
    {
        //IconRendererRegistry.registerRenderer(ICON_DATA.get(), new XaeroMinimapIntegration.XMWaypoints.XMWaypointRenderer());
    }

    private int counter = 0;

    private void playerTick(TickEvent.PlayerTickEvent event)
    {
        if (event.phase != TickEvent.Phase.END)
            return;

        if ((++counter) > 20)
        {
            counter = 0;

            Player player = event.player;
            if (player.level.isClientSide)
                return;

            player.getCapability(PointsOfInterest.INSTANCE).ifPresent((pois) -> {
                PointsOfInterest.WorldPoints worldPoints = pois.get(player.level);

                VanillaMapData addon = pois.getOrCreateAddonData(ADDON_ID, VanillaMapData::new);

                Set<MapItemSavedData> seenMaps = getMapData(player, worldPoints, addon);

                Set<MapItemSavedData> toRemove = new HashSet<>(addon.mapDecorations.keySet());
                toRemove.removeAll(seenMaps);

                for (MapItemSavedData remove : toRemove)
                {
                    Map<MapDecoration, PointInfo<?>> map = addon.mapDecorations.get(remove);

                    for (PointInfo<?> pt : map.values())
                    {
                        worldPoints.removePoint(pt);
                    }

                    addon.mapDecorations.remove(remove);
                }
            });
        }
    }

    @Nonnull
    private Set<MapItemSavedData> getMapData(Player player, PointsOfInterest.WorldPoints worldPoints, VanillaMapData addon)
    {
        if (!ConfigData.COMMON.enableVanillaMapIntegration.get())
            return Collections.emptySet();

        Set<MapItemSavedData> seenMaps = Sets.newHashSet();
        for (int slot = 0; slot < player.getInventory().getContainerSize(); slot++)
        {
            ItemStack stack = player.getInventory().getItem(slot);
            MapItemSavedData mapData = MapItem.getSavedData(stack, player.level);
            if (mapData != null && !seenMaps.contains(mapData) && mapData.dimension == worldPoints.getWorldKey())
            {
                seenMaps.add(mapData);

                Map<MapDecoration, PointInfo<?>> decorationPointInfoMap = addon.mapDecorations.computeIfAbsent(mapData, k -> Maps.newHashMap());

                for (MapBanner banner : mapData.bannerMarkers.values())
                {
                    MapDecoration decoration = mapData.decorations.get(banner.getId());
                    if (!decorationPointInfoMap.containsKey(decoration))
                    {
                        MapBannerWaypoint wp = new MapBannerWaypoint(banner, decoration);
                        decorationPointInfoMap.put(decoration, wp);
                        worldPoints.addPoint(wp);
                    }
                }

                for (Map.Entry<String, MapDecoration> kvp : mapData.decorations.entrySet())
                {
                    String decorationId = kvp.getKey();
                    MapDecoration decoration = kvp.getValue();

                    // skip players, they will be handled separately.
                    if (decoration.getType() == MapDecoration.Type.PLAYER ||
                            decoration.getType() == MapDecoration.Type.PLAYER_OFF_LIMITS ||
                            decoration.getType() == MapDecoration.Type.PLAYER_OFF_MAP)
                        continue;

                    if (!decorationPointInfoMap.containsKey(decoration))
                    {
                        MapDecorationWaypoint wp = new MapDecorationWaypoint(mapData, decoration);
                        decorationPointInfoMap.put(decoration, wp);
                        worldPoints.addPoint(wp);
                    }
                }

                Set<MapDecoration> toRemove = new HashSet<>(decorationPointInfoMap.keySet());
                toRemove.removeAll(mapData.decorations.values());

                for (MapDecoration remove : toRemove)
                {
                    worldPoints.removePoint(decorationPointInfoMap.get(remove));
                    decorationPointInfoMap.remove(remove);
                }
            }
        }
        return seenMaps;
    }

    public static class MapBannerWaypoint extends PointInfo<MapBannerWaypoint>
    {
        private final MapBanner banner;
        private Vec3 position;

        public MapBannerWaypoint(MapBanner banner, MapDecoration decoration)
        {
            super(BANNER_TYPE.get(), true, banner.getName(), BasicIconData.mapMarker(decoration.getType().getIcon()));
            dynamic();
            this.banner = banner;
            this.position = Vec3.atCenterOf(banner.getPos());
        }

        public MapBannerWaypoint()
        {
            super(BANNER_TYPE.get(), true);
            banner = null;
        }

        @Nullable
        public MapBanner getBanner()
        {
            return banner;
        }

        @Override
        public Vec3 getPosition()
        {
            return position;
        }

        @Override
        public Vec3 getPosition(Player player, float partialTicks)
        {
            return position;
        }

        @Override
        protected void serializeAdditional(CompoundTag tag)
        {
            tag.putDouble("X", position.x);
            tag.putDouble("Y", position.y);
            tag.putDouble("Z", position.z);
        }

        @Override
        protected void deserializeAdditional(CompoundTag tag)
        {
            position = new Vec3(
                    tag.getDouble("X"),
                    tag.getDouble("Y"),
                    tag.getDouble("Z")
            );
        }

        @Override
        protected void serializeAdditional(FriendlyByteBuf buffer)
        {
            buffer.writeDouble(position.x);
            buffer.writeDouble(position.y);
            buffer.writeDouble(position.z);
        }

        @Override
        protected void deserializeAdditional(FriendlyByteBuf buffer)
        {
            position = new Vec3(
                    buffer.readDouble(),
                    buffer.readDouble(),
                    buffer.readDouble()
            );
        }
    }

    public static class MapDecorationWaypoint extends PointInfo<MapDecorationWaypoint>
    {
        private final MapDecoration decoration;
        private Vec3 position;

        public MapDecorationWaypoint(MapItemSavedData mapData, MapDecoration decoration)
        {
            super(DECORATION_TYPE.get(), true, null, BasicIconData.mapMarker(decoration.getType().getIcon()));

            dynamic();
            noVerticalDistance();

            float decoX = (decoration.getX() - 0.5f) * 0.5f;
            float decoZ = (decoration.getY() - 0.5f) * 0.5f;

            int scale = 1 << mapData.scale;
            float worldX = mapData.x + decoX * scale;
            float worldZ = mapData.z + decoZ * scale;

            this.decoration = decoration;
            this.position = new Vec3(worldX, 0, worldZ);
        }

        public MapDecorationWaypoint()
        {
            super(DECORATION_TYPE.get(), true);
            decoration = null;
        }

        @Nullable
        public MapDecoration getDecoration()
        {
            return decoration;
        }

        @Override
        public Vec3 getPosition()
        {
            return position;
        }

        @Override
        public Vec3 getPosition(Player player, float partialTicks)
        {
            return position;
        }

        @Override
        protected void serializeAdditional(CompoundTag tag)
        {
            tag.putDouble("X", position.x);
            tag.putDouble("Y", position.y);
            tag.putDouble("Z", position.z);
        }

        @Override
        protected void deserializeAdditional(CompoundTag tag)
        {
            position = new Vec3(
                    tag.getDouble("X"),
                    tag.getDouble("Y"),
                    tag.getDouble("Z")
            );
        }

        @Override
        protected void serializeAdditional(FriendlyByteBuf buffer)
        {
            buffer.writeDouble(position.x);
            buffer.writeDouble(position.y);
            buffer.writeDouble(position.z);
        }

        @Override
        protected void deserializeAdditional(FriendlyByteBuf buffer)
        {
            position = new Vec3(
                    buffer.readDouble(),
                    buffer.readDouble(),
                    buffer.readDouble()
            );
        }
    }

    private class VanillaMapData
    {
        public Map<MapItemSavedData, Map<MapDecoration, PointInfo<?>>> mapDecorations = Maps.newHashMap();
    }
}
