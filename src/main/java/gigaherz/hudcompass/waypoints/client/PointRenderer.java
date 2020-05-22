package gigaherz.hudcompass.waypoints.client;

import gigaherz.hudcompass.client.HudOverlay;
import gigaherz.hudcompass.icons.client.IconRendererRegistry;
import gigaherz.hudcompass.waypoints.PointInfo;
import net.minecraft.client.gui.FontRenderer;
import net.minecraft.client.renderer.texture.TextureManager;
import net.minecraft.entity.player.PlayerEntity;

public class PointRenderer
{
    public static void renderIcon(PointInfo info, PlayerEntity player, TextureManager textureManager, int x, int y)
    {
        IconRendererRegistry.renderIcon(info.getIconData(), player, textureManager, x, y);
    }

    public static void renderLabel(PointInfo info, FontRenderer font, int x, int y)
    {
        String label = info.getLabel();
        HudOverlay.drawCenteredBoxedString(font, label, x, y, 0xFFFFFF);
    }
}
