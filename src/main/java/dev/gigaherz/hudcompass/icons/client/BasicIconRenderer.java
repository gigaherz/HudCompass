package dev.gigaherz.hudcompass.icons.client;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;
import dev.gigaherz.hudcompass.icons.BasicIconData;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.texture.TextureManager;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Player;

public class BasicIconRenderer implements IIconRenderer<BasicIconData>
{
    public final int texW;
    public final int texH;
    public final int iconW;
    public final int iconH;
    public final int iconsPerRow;
    public final int iconsPerCol;

    private final ResourceLocation texture;

    public BasicIconRenderer(ResourceLocation texture)
    {
        this(texture, 128, 128, 8, 8);
    }

    public BasicIconRenderer(ResourceLocation texture, int texW, int texH, int iconW, int iconH)
    {
        this.texture = texture;
        this.texW = texW;
        this.texH = texH;
        this.iconW = iconW;
        this.iconH = iconH;
        this.iconsPerRow = texW / iconW;
        this.iconsPerCol = texH / iconH;
    }

    public void renderIcon(BasicIconData data, Player player, TextureManager textureManager, GuiGraphics graphics, int x, int y, int alpha)
    {
        RenderSystem.setShaderColor(data.r, data.g, data.b, data.a * (alpha / 255.0f));
        int indexX = data.iconIndex % iconsPerRow;
        int indexY = data.iconIndex / iconsPerCol;
        graphics.blit(texture, x - iconW / 2, y - iconH / 2, indexX * iconW, indexY * iconH, iconW, iconH, texW, texH);
    }
}
