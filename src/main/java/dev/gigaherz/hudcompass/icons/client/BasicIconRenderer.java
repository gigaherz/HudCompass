package dev.gigaherz.hudcompass.icons.client;

import com.mojang.blaze3d.systems.RenderSystem;
import dev.gigaherz.hudcompass.icons.BasicIconData;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.texture.TextureManager;
import net.minecraft.world.entity.player.Player;
import dev.gigaherz.hudcompass.client.HudOverlay;

public record BasicIconRenderer()
        implements IIconRenderer<BasicIconData>
{
    public static final int ICON_WIDTH = 8;
    public static final int ICON_HEIGHT = 8;

    public void renderIcon(BasicIconData data, Player player, TextureManager textureManager, GuiGraphics graphics, int x, int y, int alpha)
    {
        var xStart = x - ICON_WIDTH / 2;
        var yStart = y - ICON_HEIGHT / 2;

        HudOverlay.drawMapIcon(graphics, data.spriteName,
                xStart, xStart + ICON_WIDTH,
                yStart, yStart + ICON_HEIGHT,
                data.r, data.g, data.b, data.a * (alpha / 255.0f));
    }
}
