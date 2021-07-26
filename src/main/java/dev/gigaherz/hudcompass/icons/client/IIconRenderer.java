package dev.gigaherz.hudcompass.icons.client;

import com.mojang.blaze3d.vertex.PoseStack;
import dev.gigaherz.hudcompass.icons.IIconData;
import net.minecraft.client.renderer.texture.TextureManager;
import net.minecraft.world.entity.player.Player;

public interface IIconRenderer<T extends IIconData<T>>
{
    void renderIcon(T data, Player player, TextureManager textureManager, PoseStack matrixStack, int x, int y);
}
