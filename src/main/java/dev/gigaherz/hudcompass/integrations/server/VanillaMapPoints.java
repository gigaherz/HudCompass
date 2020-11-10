package dev.gigaherz.hudcompass.integrations.server;

import com.google.common.collect.Maps;
import com.google.common.collect.Sets;
import dev.gigaherz.hudcompass.HudCompass;
import dev.gigaherz.hudcompass.icons.BasicIconData;
import dev.gigaherz.hudcompass.waypoints.PointInfo;
import dev.gigaherz.hudcompass.waypoints.PointInfoType;
import dev.gigaherz.hudcompass.waypoints.PointsOfInterest;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.FilledMapItem;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.CompoundNBT;
import net.minecraft.network.PacketBuffer;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.math.vector.Vector3d;
import net.minecraft.world.storage.MapBanner;
import net.minecraft.world.storage.MapData;
import net.minecraft.world.storage.MapDecoration;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.RegistryObject;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import net.minecraftforge.registries.DeferredRegister;

import javax.annotation.Nullable;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

public class VanillaMapPoints
{
    public static final VanillaMapPoints INSTANCE = new VanillaMapPoints();

    private static final ResourceLocation ADDON_ID = HudCompass.location("vanilla_map");

    private static final DeferredRegister<PointInfoType<?>> PIT = HudCompass.makeDeferredPOI();
    //private static final DeferredRegister<IconDataSerializer<?>> IDS = HudCompass.makeDeferredIDS();
    public static final RegistryObject<PointInfoType<MapDecorationWaypoint>> DECORATION_TYPE = PIT.register("map_decoration", () -> new PointInfoType<>(MapDecorationWaypoint::new));
    public static final RegistryObject<PointInfoType<MapBannerWaypoint>> BANNER_TYPE = PIT.register("map_banner", () -> new PointInfoType<>(MapBannerWaypoint::new));
    //public static final RegistryObject<XaeroMinimapIntegration.XMWaypoints.XMIconData.Serializer> ICON_DATA = IDS.register("xmwaypoints", XaeroMinimapIntegration.XMWaypoints.XMIconData.Serializer::new);

    public static void init()
    {
        IEventBus modEventBus = FMLJavaModLoadingContext.get().getModEventBus();

        modEventBus.addListener(INSTANCE::clientSetup);

        PIT.register(modEventBus);
        //IDS.register(modEventBus);

        //MinecraftForge.EVENT_BUS.addListener(INSTANCE::clientTick);
        MinecraftForge.EVENT_BUS.addListener(INSTANCE::playerTick);
    }

    private void clientSetup(FMLClientSetupEvent event)
    {
        //IconRendererRegistry.registerRenderer(ICON_DATA.get(), new XaeroMinimapIntegration.XMWaypoints.XMWaypointRenderer());
    }

    private void playerTick(TickEvent.PlayerTickEvent event)
    {
        PlayerEntity player = event.player;
        if (player.world.isRemote)
            return;

        player.getCapability(PointsOfInterest.INSTANCE).ifPresent((pois) -> {
            PointsOfInterest.WorldPoints worldPoints = pois.get(player.world);

            VanillaMapData addon = pois.getOrCreateAddonData(ADDON_ID, VanillaMapData::new);

            Set<MapData> seenMaps = Sets.newHashSet();
            for(int slot = 0;slot < player.inventory.getSizeInventory();slot++)
            {
                ItemStack stack = player.inventory.getStackInSlot(slot);
                MapData mapData = FilledMapItem.getMapData(stack, player.world);
                if (mapData != null && !seenMaps.contains(mapData) && mapData.dimension == worldPoints.getWorldKey())
                {
                    seenMaps.add(mapData);

                    Map<MapDecoration, PointInfo<?>> decorationPointInfoMap = addon.mapDecorations.computeIfAbsent(mapData, k -> Maps.newHashMap());

                    for(MapBanner banner : mapData.banners.values())
                    {
                        MapDecoration decoration = mapData.mapDecorations.get(banner.getMapDecorationId());
                        if (!decorationPointInfoMap.containsKey(decoration))
                        {
                            MapBannerWaypoint wp = new MapBannerWaypoint(banner, decoration);
                            decorationPointInfoMap.put(decoration, wp);
                            worldPoints.addPoint(wp);
                        }
                    }

                    for(Map.Entry<String, MapDecoration> kvp : mapData.mapDecorations.entrySet())
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
                            MapDecorationWaypoint wp = new MapDecorationWaypoint(mapData, decoration, decorationId);
                            decorationPointInfoMap.put(decoration, wp);
                            worldPoints.addPoint(wp);
                        }
                    }

                    Set<MapDecoration> toRemove = new HashSet<>(decorationPointInfoMap.keySet());
                    toRemove.removeAll(mapData.mapDecorations.values());

                    for(MapDecoration remove : toRemove)
                    {
                        worldPoints.removePoint(decorationPointInfoMap.get(remove));
                        decorationPointInfoMap.remove(remove);
                    }
                }
            }

            Set<MapData> toRemove = new HashSet<>(addon.mapDecorations.keySet());
            toRemove.removeAll(seenMaps);

            for(MapData remove : toRemove)
            {
                Map<MapDecoration, PointInfo<?>> map = addon.mapDecorations.get(remove);

                for(PointInfo<?> pt : map.values())
                {
                    worldPoints.removePoint(pt);
                }

                addon.mapDecorations.remove(remove);
            }
        });
    }

    public static class MapBannerWaypoint extends PointInfo<MapBannerWaypoint>
    {
        private final MapBanner banner;
        private Vector3d position;

        public MapBannerWaypoint(MapBanner banner, MapDecoration decoration)
        {
            super(BANNER_TYPE.get(), true, banner.getName(), BasicIconData.mapMarker(decoration.getImage()));
            dynamic();
            this.banner = banner;
            this.position = Vector3d.copyCentered(banner.getPos());
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
        public Vector3d getPosition()
        {
            return position;
        }

        @Override
        protected void serializeAdditional(CompoundNBT tag)
        {
            tag.putDouble("X", position.x);
            tag.putDouble("Y", position.y);
            tag.putDouble("Z", position.z);
        }

        @Override
        protected void deserializeAdditional(CompoundNBT tag)
        {
            position = new Vector3d(
                    tag.getDouble("X"),
                    tag.getDouble("Y"),
                    tag.getDouble("Z")
            );
        }

        @Override
        protected void serializeAdditional(PacketBuffer buffer)
        {
            buffer.writeDouble(position.x);
            buffer.writeDouble(position.y);
            buffer.writeDouble(position.z);
        }

        @Override
        protected void deserializeAdditional(PacketBuffer buffer)
        {
            position = new Vector3d(
                    buffer.readDouble(),
                    buffer.readDouble(),
                    buffer.readDouble()
            );
        }
    }

    public static class MapDecorationWaypoint extends PointInfo<MapDecorationWaypoint>
    {
        private final MapDecoration decoration;
        private Vector3d position;

        public MapDecorationWaypoint(MapData mapData, MapDecoration decoration, String decorationId)
        {
            super(DECORATION_TYPE.get(), true, null, BasicIconData.mapMarker(decoration.getImage()));

            dynamic();
            noVerticalDistance();

            float decoX =(decoration.getX()-0.5f)*0.5f;
            float decoZ =(decoration.getY()-0.5f)*0.5f;

            int scale = 1<<mapData.scale;
            float worldX = mapData.xCenter + decoX * scale;
            float worldZ = mapData.zCenter + decoZ * scale;

            this.decoration = decoration;
            this.position = new Vector3d(worldX, 0, worldZ);
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
        public Vector3d getPosition()
        {
            return position;
        }

        @Override
        protected void serializeAdditional(CompoundNBT tag)
        {
            tag.putDouble("X", position.x);
            tag.putDouble("Y", position.y);
            tag.putDouble("Z", position.z);
        }

        @Override
        protected void deserializeAdditional(CompoundNBT tag)
        {
            position = new Vector3d(
                    tag.getDouble("X"),
                    tag.getDouble("Y"),
                    tag.getDouble("Z")
            );
        }

        @Override
        protected void serializeAdditional(PacketBuffer buffer)
        {
            buffer.writeDouble(position.x);
            buffer.writeDouble(position.y);
            buffer.writeDouble(position.z);
        }

        @Override
        protected void deserializeAdditional(PacketBuffer buffer)
        {
            position = new Vector3d(
                    buffer.readDouble(),
                    buffer.readDouble(),
                    buffer.readDouble()
            );
        }
    }

    private class VanillaMapData
    {
        public Map<MapData, Map<MapDecoration, PointInfo<?>>> mapDecorations = Maps.newHashMap();
    }
}
