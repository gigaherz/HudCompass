package dev.gigaherz.hudcompass.icons;

import com.mojang.serialization.MapCodec;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;

public interface IIconData<T extends IIconData<T>>
{
    MapCodec<IIconData<?>>
            DISPATCH_CODEC = IconDataSerializer.BY_ID_CODEC.dispatchMap(IIconData::getSerializer, IconDataSerializer::codec);

    StreamCodec<RegistryFriendlyByteBuf, IIconData<?>>
            DISPATCH_STREAM_CODEC = IconDataSerializer.BY_ID_STREAM_CODEC.dispatch(IIconData::getSerializer, IconDataSerializer::streamCodec);

    IconDataSerializer<T> getSerializer();
}
