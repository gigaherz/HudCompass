package dev.gigaherz.hudcompass.icons;

import dev.gigaherz.hudcompass.HudCompass;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.registries.IForgeRegistry;
import net.minecraftforge.registries.RegistryManager;

import javax.annotation.Nonnull;

public class IconDataRegistry
{
    @Nonnull
    public static <T extends IIconData<T>> CompoundTag serializeIcon(@Nonnull T iconData)
    {
        IconDataSerializer<T> serializer = iconData.getSerializer();
        ResourceLocation serializerId = HudCompass.ICON_DATA_SERIALIZERS_REGISTRY.get().getKey(serializer);
        if (serializerId == null)
        {
            throw new IllegalStateException(String.format("Serializer name is null %s", serializer.getClass().getName()));
        }
        CompoundTag tag = new CompoundTag();
        tag.putString("Type", serializerId.toString());
        tag = serializer.write(iconData, tag);
        return tag;
    }

    public static <T extends IIconData<T>> void serializeIcon(T iconData, FriendlyByteBuf buffer)
    {
        IconDataSerializer<T> serializer = iconData.getSerializer();
        if (!HudCompass.ICON_DATA_SERIALIZERS_REGISTRY.get().containsValue(serializer))
            throw new IllegalStateException("Could not find serializer in the registry! Make sure it's registered.");
        buffer.writeRegistryIdUnsafe(HudCompass.ICON_DATA_SERIALIZERS_REGISTRY.get(), serializer);
        serializer.write(iconData, buffer);
    }

    @Nonnull
    public static IIconData<?> deserializeIcon(CompoundTag tag)
    {
        ResourceLocation serializerId = new ResourceLocation(tag.getString("Type"));
        IconDataSerializer<?> serializer = HudCompass.ICON_DATA_SERIALIZERS_REGISTRY.get().getValue(serializerId);
        if (serializer == null)
        {
            throw new IllegalStateException(String.format("Serializer not registered %s", serializerId));
        }
        return serializer.read(tag);
    }

    @Nonnull
    public static IIconData<?> deserializeIcon(FriendlyByteBuf buffer)
    {
        IconDataSerializer<?> serializer = buffer.readRegistryIdUnsafe(HudCompass.ICON_DATA_SERIALIZERS_REGISTRY.get());
        if (serializer == null)
        {
            throw new IllegalStateException("Server returned unknown serializer");
        }
        return serializer.read(buffer);
    }
}
