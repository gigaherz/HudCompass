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
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;

import javax.annotation.Nonnull;

public class IconDataRegistry
{

    public static final StreamCodec<ByteBuf, IconDataSerializer<?>> BY_ID_STREAM_CODEC = ByteBufCodecs.idMapper(HudCompass.ICON_DATA_SERIALIZERS_REGISTRY);

    @Nonnull
    public static <T extends IIconData<T>> void serializeIcon(@Nonnull T iconData, ValueOutput output)
    {
        IconDataSerializer<T> serializer = iconData.getSerializer();
        Identifier serializerId = HudCompass.ICON_DATA_SERIALIZERS_REGISTRY.getKey(serializer);
        if (serializerId == null)
        {
            throw new IllegalStateException(String.format("Serializer name is null %s", serializer.getClass().getName()));
        }
        output.putString("Type", serializerId.toString());
        serializer.write(iconData, output);
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
    public static IIconData<?> deserializeIcon(ValueInput input)
    {
        Identifier serializerId = Identifier.parse(input.getString("Type").orElseThrow());
        IconDataSerializer<?> serializer = HudCompass.ICON_DATA_SERIALIZERS_REGISTRY.get(serializerId).map(Holder.Reference::value).orElse(null);
        if (serializer == null)
        {
            throw new IllegalStateException(String.format("Serializer not registered %s", serializerId));
        }
        return serializer.read(input);
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
