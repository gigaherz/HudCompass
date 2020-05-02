package gigaherz.hudcompass.icons.client;

import gigaherz.hudcompass.icons.BasicIconData;
import net.minecraft.client.gui.AbstractGui;
import net.minecraft.client.renderer.texture.TextureManager;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.ResourceLocation;

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

    public void renderIcon(BasicIconData data, PlayerEntity player, TextureManager textureManager, int x, int y)
    {
        textureManager.bindTexture(texture);
        int indexX = data.iconIndex % iconsPerRow;
        int indexY = data.iconIndex / iconsPerCol;
        AbstractGui.blit(x - iconW / 2, y - iconH / 2, indexX * iconW, indexY * iconH, iconW, iconH, texW, texH);
    }
}
