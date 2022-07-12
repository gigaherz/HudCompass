package dev.gigaherz.hudcompass.icons;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.FriendlyByteBuf;

public abstract class IconDataSerializer<T extends IIconData<T>>
{
    public abstract CompoundTag write(T data, CompoundTag tag);

    public abstract T read(CompoundTag tag);

    public abstract void write(T data, FriendlyByteBuf buffer);

    public abstract T read(FriendlyByteBuf buffer);
}
