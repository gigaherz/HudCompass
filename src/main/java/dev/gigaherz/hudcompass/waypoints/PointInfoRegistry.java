package dev.gigaherz.hudcompass.waypoints;

import dev.gigaherz.hudcompass.HudCompass;
import dev.gigaherz.hudcompass.icons.IIconData;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.registries.IForgeRegistry;
import net.minecraftforge.registries.RegistryManager;

import javax.annotation.Nonnull;

public class PointInfoRegistry
{
    @Nonnull
    public static <T extends PointInfo<T>> CompoundTag serializePoint(@Nonnull T pointInfo)
    {
        PointInfoType<? extends T> type = pointInfo.getType();
        ResourceLocation typeId = HudCompass.POINT_INFO_TYPES_REGISTRY.get().getKey(type);
        if (typeId == null)
        {
            throw new IllegalStateException(String.format("Serializer name is null %s", type.getClass().getName()));
        }
        CompoundTag tag = new CompoundTag();
        tag.putString("Type", typeId.toString());
        return pointInfo.write(tag);
    }

    public static <T extends PointInfo<T>> void serializePoint(T pointInfo, FriendlyByteBuf buffer)
    {
        PointInfoType<?> type = pointInfo.getType();
        buffer.writeRegistryIdUnsafe(HudCompass.POINT_INFO_TYPES_REGISTRY.get(), type);
        pointInfo.writeToPacket(buffer);
    }

    public static <T extends PointInfo<T>> void serializePointWithoutId(T pointInfo, FriendlyByteBuf buffer)
    {
        PointInfoType<? extends T> type = pointInfo.getType();
        buffer.writeRegistryIdUnsafe(HudCompass.POINT_INFO_TYPES_REGISTRY.get(), type);
        pointInfo.writeToPacketWithoutId(buffer);
    }

    @Nonnull
    public static PointInfo<?> deserializePoint(CompoundTag tag)
    {
        ResourceLocation typeId = new ResourceLocation(tag.getString("Type"));
        PointInfoType<?> type = HudCompass.POINT_INFO_TYPES_REGISTRY.get().getValue(typeId);
        if (type == null)
        {
            throw new IllegalStateException(String.format("Serializer not registered %s", typeId));
        }
        PointInfo<?> info = type.create();
        info.read(tag);
        return info;
    }

    @Nonnull
    public static PointInfo<?> deserializePoint(FriendlyByteBuf buffer)
    {
        PointInfoType<?> serializer = buffer.readRegistryIdUnsafe(HudCompass.POINT_INFO_TYPES_REGISTRY.get());
        if (serializer == null)
        {
            throw new IllegalStateException("Server returned unknown serializer");
        }
        PointInfo<?> info = serializer.create();
        info.readFromPacket(buffer);
        return info;
    }

    @Nonnull
    public static PointInfo<?> deserializePointWithoutId(FriendlyByteBuf buffer)
    {
        PointInfoType<?> serializer = buffer.readRegistryIdUnsafe(HudCompass.POINT_INFO_TYPES_REGISTRY.get());
        if (serializer == null)
        {
            throw new IllegalStateException("Server returned unknown serializer");
        }
        PointInfo<?> info = serializer.create();
        info.readFromPacketWithoutId(buffer);
        return info;
    }
}
