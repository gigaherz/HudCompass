package gigaherz.hudcompass;

import net.minecraft.client.gui.AbstractGui;
import net.minecraft.client.gui.FontRenderer;
import net.minecraft.client.renderer.Rectangle2d;
import net.minecraft.client.renderer.texture.TextureManager;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.math.BlockPos;

import javax.vecmath.Vector3d;

public class PointInfo
{
    public static final ResourceLocation LOCATION_MAP_ICONS = new ResourceLocation("minecraft", "textures/map/map_icons.png");
    public static final ResourceLocation LOCATION_POI_ICONS = new ResourceLocation("hudcompass", "textures/poi_icons.png");

    public static Vector3d toVec3d(BlockPos pos)
    {
        return new Vector3d(pos.getX()+0.5,pos.getY()+0.5,pos.getZ()+0.5);
    }

    private final Rectangle2d rectangle;
    private final String label;
    private final ResourceLocation iconTexture;
    private final Vector3d position;
    private boolean displayVerticalDistance = true;

    public PointInfo(BlockPos exactPosition, String label, int iconIndex)
    {
        this(toVec3d(exactPosition), label, iconIndex, false);
    }

    public PointInfo(BlockPos exactPosition, String label, int iconIndex, boolean isPoiIcon)
    {
        this(toVec3d(exactPosition), label, new Rectangle2d((iconIndex % 16)*8,(iconIndex / 16)*8, 8,8), isPoiIcon);
    }

    public PointInfo(BlockPos exactPosition, String label, Rectangle2d rectangle, boolean isPoiIcon)
    {
        this(toVec3d(exactPosition), label, rectangle, isPoiIcon ? LOCATION_POI_ICONS : LOCATION_MAP_ICONS);
    }

    public PointInfo(Vector3d exactPosition, String label, int iconIndex)
    {
        this(exactPosition, label, iconIndex, false);
    }

    public PointInfo(Vector3d exactPosition, String label, int iconIndex, boolean isPoiIcon)
    {
        this(exactPosition, label, new Rectangle2d((iconIndex % 16)*8,(iconIndex / 16)*8, 8,8), isPoiIcon);
    }

    public PointInfo(Vector3d exactPosition, String label, Rectangle2d rectangle, boolean isPoiIcon)
    {
        this(exactPosition, label, rectangle, isPoiIcon ? LOCATION_POI_ICONS : LOCATION_MAP_ICONS);
    }

    public PointInfo(Vector3d exactPosition, String label, Rectangle2d rectangle, ResourceLocation iconTexture)
    {
        this.iconTexture = iconTexture;
        this.rectangle = rectangle;
        this.label = label;
        this.position = exactPosition;
    }

    public void renderIcon(PlayerEntity player, TextureManager textureManager, int x, int y)
    {
        textureManager.bindTexture(getIconTexture(player));
        Rectangle2d rect = getIconRectangle(player);
        AbstractGui.blit(x-rect.getWidth()/2, y-rect.getHeight()/2, rect.getX(), rect.getY(), rect.getWidth(), rect.getHeight(), 128,128);
    }

    public void renderLabel(PlayerEntity player, FontRenderer font, int x, int y)
    {
        String label = getLabel(player);
        HudOverlay.drawCenteredBoxedString(font, label, x, y, 0xFFFFFF);
    }

    public ResourceLocation getIconTexture(PlayerEntity player)
    {
        return iconTexture;
    }

    public Rectangle2d getIconRectangle(PlayerEntity player)
    {
        return this.rectangle;
    }

    public String getLabel(PlayerEntity player)
    {
        return this.label;
    }

    public Vector3d getPosition(PlayerEntity player)
    {
        return position;
    }

    public PointInfo noVerticalDistance()
    {
        this.displayVerticalDistance = false;
        return this;
    }

    public boolean displayVerticalDistance(PlayerEntity player)
    {
        return displayVerticalDistance;
    }
}
