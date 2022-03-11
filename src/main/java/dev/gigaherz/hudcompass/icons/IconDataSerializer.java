package dev.gigaherz.hudcompass.icons;

import net.minecraft.nbt.CompoundNBT;
import net.minecraft.network.PacketBuffer;
import net.minecraftforge.registries.ForgeRegistryEntry;

public abstract class IconDataSerializer<T extends IIconData<T>> extends ForgeRegistryEntry<IconDataSerializer<?>>
{
    public abstract CompoundNBT write(T data, CompoundNBT tag);

    public abstract T read(CompoundNBT tag);

    public abstract void write(T data, PacketBuffer buffer);

    public abstract T read(PacketBuffer buffer);
}
