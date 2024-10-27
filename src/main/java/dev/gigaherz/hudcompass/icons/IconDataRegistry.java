package dev.gigaherz.hudcompass.icons;

import dev.gigaherz.hudcompass.HudCompass;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufUtil;
import net.minecraft.core.Holder;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.resources.ResourceLocation;

import javax.annotation.Nonnull;

public class IconDataRegistry
{

    public static final StreamCodec<ByteBuf, IconDataSerializer<?>> BY_ID_STREAM_CODEC = ByteBufCodecs.idMapper(HudCompass.ICON_DATA_SERIALIZERS_REGISTRY);

    @Nonnull
    public static <T extends IIconData<T>> CompoundTag serializeIcon(@Nonnull T iconData)
    {
        IconDataSerializer<T> serializer = iconData.getSerializer();
        ResourceLocation serializerId = HudCompass.ICON_DATA_SERIALIZERS_REGISTRY.getKey(serializer);
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
        if (!HudCompass.ICON_DATA_SERIALIZERS_REGISTRY.containsValue(serializer))
            throw new IllegalStateException("Could not find serializer in the registry! Make sure it's registered.");
        BY_ID_STREAM_CODEC.encode(buffer, serializer);
        serializer.write(iconData, buffer);
    }

    @Nonnull
    public static IIconData<?> deserializeIcon(CompoundTag tag)
    {
        ResourceLocation serializerId = ResourceLocation.parse(tag.getString("Type"));
        IconDataSerializer<?> serializer = HudCompass.ICON_DATA_SERIALIZERS_REGISTRY.get(serializerId).map(Holder.Reference::value).orElse(null);
        if (serializer == null)
        {
            throw new IllegalStateException(String.format("Serializer not registered %s", serializerId));
        }
        return serializer.read(tag);
    }

    @Nonnull
    public static IIconData<?> deserializeIcon(FriendlyByteBuf buffer)
    {
        IconDataSerializer<?> serializer = BY_ID_STREAM_CODEC.decode(buffer);
        if (serializer == null)
        {
            throw new IllegalStateException("Server returned unknown serializer");
        }
        return serializer.read(buffer);
    }
}
