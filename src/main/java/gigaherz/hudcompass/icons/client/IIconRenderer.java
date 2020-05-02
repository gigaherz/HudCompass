package gigaherz.hudcompass.icons.client;

import gigaherz.hudcompass.icons.IIconData;
import net.minecraft.client.renderer.texture.TextureManager;
import net.minecraft.entity.player.PlayerEntity;

public interface IIconRenderer<T extends IIconData<T>>
{
    void renderIcon(T data, PlayerEntity player, TextureManager textureManager, int x, int y);
}
