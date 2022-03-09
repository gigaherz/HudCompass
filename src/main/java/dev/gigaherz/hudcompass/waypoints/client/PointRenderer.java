package dev.gigaherz.hudcompass.waypoints.client;

import com.mojang.blaze3d.matrix.MatrixStack;
import dev.gigaherz.hudcompass.client.HudOverlay;
import dev.gigaherz.hudcompass.icons.client.IconRendererRegistry;
import dev.gigaherz.hudcompass.waypoints.PointInfo;
import net.minecraft.client.gui.FontRenderer;
import net.minecraft.client.renderer.texture.TextureManager;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.text.ITextComponent;

public class PointRenderer
{
    public static void renderIcon(PointInfo<?> info, PlayerEntity player, TextureManager textureManager, MatrixStack matrixStack, int x, int y, int alpha)
    {
        IconRendererRegistry.renderIcon(info.getIconData(), player, textureManager, matrixStack, x, y, alpha);
    }

    public static void renderLabel(PointInfo<?> info, FontRenderer font, MatrixStack matrixStack, int x, int y, int alpha)
    {
        ITextComponent label = info.getLabel();
        if (label != null && label.getString().length() > 0)
        {
            HudOverlay.drawCenteredBoxedString(matrixStack, font, label, x, y, (alpha << 24) | 0xFFFFFF);
        }
    }
}
