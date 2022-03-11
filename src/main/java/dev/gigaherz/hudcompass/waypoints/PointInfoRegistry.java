package dev.gigaherz.hudcompass.waypoints;

import net.minecraft.nbt.CompoundNBT;
import net.minecraft.network.PacketBuffer;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.registries.IForgeRegistry;
import net.minecraftforge.registries.RegistryManager;

import javax.annotation.Nonnull;

public class PointInfoRegistry
{
    public static IForgeRegistry<PointInfoType<?>> REGISTRY = RegistryManager.ACTIVE.getRegistry(PointInfoType.class);

    @SuppressWarnings({"rawtypes", "unchecked"})
    @Nonnull
    public static CompoundNBT serializePoint(@Nonnull PointInfo<?> iconData)
    {
        PointInfoType type = iconData.getType();
        ResourceLocation typeId = type.getRegistryName();
        if (typeId == null)
        {
            throw new IllegalStateException(String.format("Serializer name is null %s", type.getClass().getName()));
        }
        CompoundNBT tag = new CompoundNBT();
        tag.putString("Type", type.getRegistryName().toString());
        tag = iconData.write(tag);
        return tag;
    }

    @Nonnull
    public static PointInfo<?> deserializePoint(CompoundNBT tag)
    {
        ResourceLocation typeId = new ResourceLocation(tag.getString("Type"));
        PointInfoType<?> type = REGISTRY.getValue(typeId);
        if (type == null)
        {
            throw new IllegalStateException(String.format("Serializer not registered %s", typeId));
        }
        PointInfo<?> info = type.create();
        info.read(tag);
        return info;
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    public static void serializePoint(PointInfo<?> iconData, PacketBuffer buffer)
    {
        PointInfoType type = iconData.getType();
        buffer.writeRegistryIdUnsafe(REGISTRY, type);
        iconData.writeToPacket(buffer);
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    public static void serializePointWithoutId(PointInfo<?> iconData, PacketBuffer buffer)
    {
        PointInfoType type = iconData.getType();
        buffer.writeRegistryIdUnsafe(REGISTRY, type);
        iconData.writeToPacketWithoutId(buffer);
    }

    @Nonnull
    public static PointInfo<?> deserializePoint(PacketBuffer buffer)
    {
        PointInfoType<?> serializer = buffer.readRegistryIdUnsafe(REGISTRY);
        if (serializer == null)
        {
            throw new IllegalStateException("Server returned unknown serializer");
        }
        PointInfo<?> info = serializer.create();
        info.readFromPacket(buffer);
        return info;
    }

    @Nonnull
    public static PointInfo<?> deserializePointWithoutId(PacketBuffer buffer)
    {
        PointInfoType<?> serializer = buffer.readRegistryIdUnsafe(REGISTRY);
        if (serializer == null)
        {
            throw new IllegalStateException("Server returned unknown serializer");
        }
        PointInfo<?> info = serializer.create();
        info.readFromPacketWithoutId(buffer);
        return info;
    }
}
