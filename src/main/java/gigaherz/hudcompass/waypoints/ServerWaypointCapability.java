package gigaherz.hudcompass.waypoints;

import com.google.common.collect.Sets;
import gigaherz.hudcompass.icons.BasicIconData;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.FilledMapItem;
import net.minecraft.item.ItemStack;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.storage.MapBanner;
import net.minecraft.world.storage.MapData;
import net.minecraft.world.storage.MapDecoration;

import java.util.List;
import java.util.Map;
import java.util.Set;

public class ServerWaypointCapability
{
    /**
     * Doesn't work. The server chooses not to sync the x/z of the center of the map.
     * @param player
     * @param sortedPoints
     */
    private void addMapPoints(PlayerEntity player, List<PointInfo> sortedPoints)
    {
        Set<MapData> seenMaps = Sets.newHashSet();
        for(int slot = 0;slot < player.inventory.getSizeInventory();slot++)
        {
            ItemStack stack = player.inventory.getStackInSlot(slot);
            MapData mapData = FilledMapItem.getMapData(stack, player.world);
            if (mapData != null && !seenMaps.contains(mapData))
            {
                seenMaps.add(mapData);

                int scale = 1<<mapData.scale;

                Set<MapDecoration> seenDecorations = Sets.newHashSet();
                for(MapBanner banner : mapData.banners.values())
                {
                    MapDecoration decoration = mapData.mapDecorations.get(banner.getMapDecorationId());
                    seenDecorations.add(decoration);
                    int icon = decoration.getImage();
                    sortedPoints.add(new PointInfo(banner.getPos(), banner.getMapDecorationId(), BasicIconData.mapMarker(icon)));
                }
                for(Map.Entry<String, MapDecoration> kvp : mapData.mapDecorations.entrySet())
                {
                    String decorationId = kvp.getKey();
                    MapDecoration decoration = kvp.getValue();
                    float decoX =(decoration.getX()-0.5f)*0.5f;
                    float decoZ =(decoration.getY()-0.5f)*0.5f;
                    float worldX = mapData.xCenter + decoX * scale;
                    float worldZ = mapData.zCenter + decoZ * scale;

                    // skip players, they will be handled separately.
                    if (/*decoration.getType() == MapDecoration.Type.PLAYER ||*/
                            decoration.getType() == MapDecoration.Type.PLAYER_OFF_LIMITS ||
                                    decoration.getType() == MapDecoration.Type.PLAYER_OFF_MAP ||
                                    seenDecorations.contains(decoration))
                        continue;
                    int icon = decoration.getImage();
                    sortedPoints.add(new PointInfo(new BlockPos(worldX,0,worldZ), decorationId, BasicIconData.mapMarker(icon)).noVerticalDistance());
                }
            }
        }
    }

}
