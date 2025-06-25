package dev.gigaherz.hudcompass.integrations.server;

import com.google.common.collect.Maps;
import com.google.common.collect.Sets;
import com.mojang.serialization.Codec;
import dev.gigaherz.hudcompass.ConfigData;
import dev.gigaherz.hudcompass.HudCompass;
import dev.gigaherz.hudcompass.icons.BasicIconData;
import dev.gigaherz.hudcompass.waypoints.PointInfo;
import dev.gigaherz.hudcompass.waypoints.PointInfoType;
import dev.gigaherz.hudcompass.waypoints.PointsOfInterest;
import dev.gigaherz.hudcompass.waypoints.SpecificPointInfo;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.MapItem;
import net.minecraft.world.level.saveddata.maps.*;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

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
    public static final DeferredHolder<PointInfoType<?>, PointInfoType<MapDecorationWaypoint>> DECORATION_TYPE = PIT.register("map_decoration", () -> new PointInfoType<>(MapDecorationWaypoint::new));
    public static final DeferredHolder<PointInfoType<?>, PointInfoType<MapBannerWaypoint>> BANNER_TYPE = PIT.register("map_banner", () -> new PointInfoType<>(MapBannerWaypoint::new));

    public static void init(IEventBus modEventBus)
    {
        modEventBus.addListener(INSTANCE::clientSetup);

        //MinecraftForge.EVENT_BUS.addListener(INSTANCE::clientTick);
        NeoForge.EVENT_BUS.addListener(INSTANCE::playerTick);
    }

    private void clientSetup(FMLClientSetupEvent event)
    {
        //IconRendererRegistry.registerRenderer(ICON_DATA.get(), new XaeroMinimapIntegration.XMWaypoints.XMWaypointRenderer());
    }

    private int counter = 0;

    private void playerTick(PlayerTickEvent.Post event)
    {
        if ((++counter) > 20)
        {
            counter = 0;

            Player player = event.getEntity();
            if (player.level().isClientSide)
                return;

            var pois = player.getData(HudCompass.POINTS_OF_INTEREST_ATTACHMENT);
            {
                PointsOfInterest.WorldPoints worldPoints = pois.get(player.level());

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
            }
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
            MapItemSavedData mapData = MapItem.getSavedData(stack, player.level());
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
                    if (decoration.type() == MapDecorationTypes.PLAYER ||
                            decoration.type() == MapDecorationTypes.PLAYER_OFF_LIMITS ||
                            decoration.type() == MapDecorationTypes.PLAYER_OFF_MAP)
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

    public static class MapBannerWaypoint extends SpecificPointInfo<MapBannerWaypoint, BasicIconData>
    {
        private final MapBanner banner;
        private Vec3 position;

        public MapBannerWaypoint(MapBanner banner, MapDecoration decoration)
        {
            super(BANNER_TYPE.get(), true, banner.name().orElse(null), BasicIconData.basic(decoration.getSpriteLocation()));
            dynamic();
            this.banner = banner;
            this.position = Vec3.atCenterOf(banner.pos());
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
        protected void serializeAdditional(ValueOutput output)
        {
            output.putDouble("X", position.x);
            output.putDouble("Y", position.y);
            output.putDouble("Z", position.z);
        }

        @Override
        protected void deserializeAdditional(ValueInput input)
        {
            position = new Vec3(
                    input.read("X", Codec.DOUBLE).orElseThrow(),
                    input.read("Y", Codec.DOUBLE).orElseThrow(),
                    input.read("Z", Codec.DOUBLE).orElseThrow()
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

    public static class MapDecorationWaypoint extends SpecificPointInfo<MapDecorationWaypoint, BasicIconData>
    {
        private final MapDecoration decoration;
        private Vec3 position;

        public MapDecorationWaypoint(MapItemSavedData mapData, MapDecoration decoration)
        {
            super(DECORATION_TYPE.get(), true, null, BasicIconData.basic(decoration.getSpriteLocation()));

            dynamic();
            noVerticalDistance();

            float decoX = (decoration.x() - 0.5f) * 0.5f;
            float decoZ = (decoration.y() - 0.5f) * 0.5f;

            int scale = 1 << mapData.scale;
            float worldX = mapData.centerX + decoX * scale;
            float worldZ = mapData.centerZ + decoZ * scale;

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
        protected void serializeAdditional(ValueOutput output)
        {
            output.putDouble("X", position.x);
            output.putDouble("Y", position.y);
            output.putDouble("Z", position.z);
        }

        @Override
        protected void deserializeAdditional(ValueInput input)
        {
            position = new Vec3(
                    input.read("X", Codec.DOUBLE).orElseThrow(),
                    input.read("Y", Codec.DOUBLE).orElseThrow(),
                    input.read("Z", Codec.DOUBLE).orElseThrow()
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
