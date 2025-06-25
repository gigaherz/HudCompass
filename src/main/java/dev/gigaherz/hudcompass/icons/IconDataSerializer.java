package dev.gigaherz.hudcompass.icons;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;

public abstract class IconDataSerializer<T extends IIconData<T>>
{
    public abstract void write(T data, ValueOutput output);

    public abstract T read(ValueInput input);

    public abstract void write(T data, FriendlyByteBuf buffer);

    public abstract T read(FriendlyByteBuf buffer);
}
