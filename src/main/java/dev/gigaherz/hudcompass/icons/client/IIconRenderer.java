package dev.gigaherz.hudcompass.icons.client;

import com.mojang.blaze3d.matrix.MatrixStack;
import dev.gigaherz.hudcompass.icons.IIconData;
import net.minecraft.client.renderer.texture.TextureManager;
import net.minecraft.entity.player.PlayerEntity;

public interface IIconRenderer<T extends IIconData<T>>
{
    void renderIcon(T data, PlayerEntity player, TextureManager textureManager, MatrixStack matrixStack, int x, int y, int alpha);
}
