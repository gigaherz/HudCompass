package dev.gigaherz.hudcompass.waypoints;

import dev.gigaherz.hudcompass.HudCompass;
import io.netty.buffer.ByteBuf;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.resources.ResourceLocation;

import javax.annotation.Nonnull;

public class PointInfoRegistry
{

    public static final StreamCodec<ByteBuf, PointInfoType<?>> BY_ID_STREAM_CODEC = ByteBufCodecs.idMapper(HudCompass.POINT_INFO_TYPES_REGISTRY);

    @Nonnull
    public static <T extends PointInfo<T>> CompoundTag serializePoint(@Nonnull T pointInfo, HolderLookup.Provider provider)
    {
        PointInfoType<? extends T> type = pointInfo.getType();
        ResourceLocation typeId = HudCompass.POINT_INFO_TYPES_REGISTRY.getKey(type);
        if (typeId == null)
        {
            throw new IllegalStateException(String.format("Serializer name is null %s", type.getClass().getName()));
        }
        CompoundTag tag = new CompoundTag();
        tag.putString("Type", typeId.toString());
        return pointInfo.write(tag, provider);
    }

    public static <T extends PointInfo<T>> void serializePoint(RegistryFriendlyByteBuf buffer, T pointInfo)
    {
        PointInfoType<?> type = pointInfo.getType();
        BY_ID_STREAM_CODEC.encode(buffer, type);
        pointInfo.writeToPacket(buffer);
    }

    static void serializePointWithoutId(RegistryFriendlyByteBuf buffer, PointInfo<?> pointInfo)
    {
        PointInfoType<?> type = pointInfo.getType();
        BY_ID_STREAM_CODEC.encode(buffer, type);
        pointInfo.writeToPacketWithoutId(buffer);
    }

    @Nonnull
    public static PointInfo<?> deserializePoint(CompoundTag tag, HolderLookup.Provider provider)
    {
        ResourceLocation typeId = new ResourceLocation(tag.getString("Type"));
        PointInfoType<?> type = HudCompass.POINT_INFO_TYPES_REGISTRY.get(typeId);
        if (type == null)
        {
            throw new IllegalStateException(String.format("Serializer not registered %s", typeId));
        }
        PointInfo<?> info = type.create();
        info.read(tag, provider);
        return info;
    }

    @Nonnull
    public static PointInfo<?> deserializePoint(RegistryFriendlyByteBuf buffer)
    {
        PointInfoType<?> serializer = BY_ID_STREAM_CODEC.decode(buffer);
        if (serializer == null)
        {
            throw new IllegalStateException("Server returned unknown serializer");
        }
        PointInfo<?> info = serializer.create();
        info.readFromPacket(buffer);
        return info;
    }

    @Nonnull
    public static PointInfo<?> deserializePointWithoutId(RegistryFriendlyByteBuf buffer)
    {
        PointInfoType<?> serializer = BY_ID_STREAM_CODEC.decode(buffer);
        if (serializer == null)
        {
            throw new IllegalStateException("Server returned unknown serializer");
        }
        PointInfo<?> info = serializer.create();
        info.readFromPacketWithoutId(buffer);
        return info;
    }
}
