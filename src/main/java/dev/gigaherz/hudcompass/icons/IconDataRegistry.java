package dev.gigaherz.hudcompass.icons;

import net.minecraft.nbt.CompoundNBT;
import net.minecraft.network.PacketBuffer;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.registries.IForgeRegistry;
import net.minecraftforge.registries.RegistryManager;

import javax.annotation.Nonnull;

public class IconDataRegistry
{
    public static IForgeRegistry<IconDataSerializer<?>> REGISTRY = RegistryManager.ACTIVE.getRegistry(IconDataSerializer.class);

    @SuppressWarnings({"rawtypes", "unchecked"})
    @Nonnull
    public static CompoundNBT serializeIcon(@Nonnull IIconData<?> iconData)
    {
        IconDataSerializer serializer = iconData.getSerializer();
        ResourceLocation serializerId = serializer.getRegistryName();
        if (serializerId == null)
        {
            throw new IllegalStateException(String.format("Serializer name is null %s", serializer.getClass().getName()));
        }
        CompoundNBT tag = new CompoundNBT();
        tag.putString("Type", serializer.getRegistryName().toString());
        tag = serializer.write(iconData, tag);
        return tag;
    }

    @Nonnull
    public static IIconData<?> deserializeIcon(CompoundNBT tag)
    {
        ResourceLocation serializerId = new ResourceLocation(tag.getString("Type"));
        IconDataSerializer<?> serializer = REGISTRY.getValue(serializerId);
        if (serializer == null)
        {
            throw new IllegalStateException(String.format("Serializer not registered %s", serializerId));
        }
        return serializer.read(tag);
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    public static void serializeIcon(IIconData<?> iconData, PacketBuffer buffer)
    {
        IconDataSerializer serializer = iconData.getSerializer();
        if (!REGISTRY.containsValue(serializer))
            throw new IllegalStateException("Could not find serializer in the registry! Make sure it's registered.");
        buffer.writeRegistryIdUnsafe(REGISTRY, serializer);
        serializer.write(iconData, buffer);
    }

    @Nonnull
    public static IIconData<?> deserializeIcon(PacketBuffer buffer)
    {
        IconDataSerializer<?> serializer = buffer.readRegistryIdUnsafe(REGISTRY);
        if (serializer == null)
        {
            throw new IllegalStateException("Server returned unknown serializer");
        }
        return serializer.read(buffer);
    }
}
