package dev.gigaherz.hudcompass.waypoints;

import dev.gigaherz.hudcompass.icons.IIconData;
import dev.gigaherz.hudcompass.icons.IconDataRegistry;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ComponentSerialization;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;

import javax.annotation.Nullable;
import java.util.Objects;
import java.util.UUID;

public abstract class PointInfo<T extends PointInfo<T>>
{
    @SuppressWarnings({"rawtypes", "unchecked"})
    public static final StreamCodec<RegistryFriendlyByteBuf, PointInfo<?>> STREAM_CODEC = StreamCodec.of(
            PointInfoRegistry::serializePointWithoutId,
            PointInfoRegistry::deserializePointWithoutId
    );


    private final PointInfoType<? extends T> type;
    @Nullable
    private PointsOfInterest.WorldPoints owner;
    private UUID internalId;
    @Nullable
    private Component label;
    private IIconData<?> iconData;
    private boolean displayVerticalDistance = true;
    private boolean isServerProvided = true; // not used in the server
    private boolean isDynamic; // will not be saved to disk

    // For rendering purposes...
    public float fade;

    /* For SERVER side use */
    public PointInfo(PointInfoType<? extends T> type, boolean isDynamic)
    {
        this.isDynamic = isDynamic;
        this.type = type;
        this.internalId = UUID.randomUUID();
    }

    /* For CLIENT side use */
    public PointInfo(PointInfoType<T> type, boolean isDynamic, @Nullable Component label, IIconData<?> iconData)
    {
        this(type, isDynamic);
        this.label = label;
        this.iconData = iconData;
    }

    public PointInfoType<? extends T> getType()
    {
        return type;
    }

    @Nullable
    public final PointsOfInterest.WorldPoints getOwner()
    {
        return owner;
    }

    public UUID getInternalId()
    {
        return internalId;
    }

    public void setInternalId(UUID uuid)
    {
        internalId = uuid;
    }

    public abstract Vec3 getPosition();

    public abstract Vec3 getPosition(Player player, float partialTicks);

    @Nullable
    public Component getLabel()
    {
        return this.label;
    }

    public void setLabel(@Nullable Component text)
    {
        if (!Objects.equals(label, text))
            markDirty();
        this.label = text;
    }

    public IIconData<?> getIconData()
    {
        return iconData;
    }

    protected void setIconData(IIconData<?> iconData)
    {
        this.iconData = iconData;
    }

    @SuppressWarnings("unchecked")
    public T dynamic()
    {
        this.isDynamic = true;
        return (T) this;
    }

    @SuppressWarnings("unchecked")
    public final T noVerticalDistance()
    {
        this.displayVerticalDistance = false;
        return (T) this;
    }

    public boolean displayVerticalDistance(Player player)
    {
        return displayVerticalDistance;
    }

    public void clientPoint()
    {
        isServerProvided = false;
    }

    public boolean isServerManaged()
    {
        return isServerProvided;
    }

    public boolean isDynamic()
    {
        return isDynamic;
    }

    public void tick(Player player)
    {
    }

    void setOwner(@Nullable PointsOfInterest.WorldPoints owner)
    {
        this.owner = owner;
    }

    public void markDirty()
    {
        if (owner != null)
        {
            owner.markDirty(this);
        }
    }

    public final CompoundTag write(CompoundTag tag, HolderLookup.Provider provider)
    {
        tag.putString("ID", internalId.toString());
        if (label != null) tag.putString("Label", Component.Serializer.toJson(label, provider));
        tag.put("Icon", IconDataRegistry.serializeIcon((IIconData)iconData));
        tag.putBoolean("DisplayVerticalDistance", displayVerticalDistance);
        serializeAdditional(tag);
        return tag;
    }

    public final void read(CompoundTag tag, HolderLookup.Provider provider)
    {
        internalId = UUID.fromString(tag.getString("ID"));
        if (tag.contains("Label", Tag.TAG_STRING))
            label = Component.Serializer.fromJson(tag.getString("Label"), provider);
        else
            label = null;
        //noinspection unchecked
        iconData = IconDataRegistry.deserializeIcon(tag.getCompound("Icon"));
        displayVerticalDistance = tag.getBoolean("DisplayVerticalDistance");
        deserializeAdditional(tag);
    }

    public final void writeToPacket(RegistryFriendlyByteBuf buffer)
    {
        buffer.writeUUID(internalId);
        writeToPacketWithoutId(buffer);
    }

    public final void writeToPacketWithoutId(RegistryFriendlyByteBuf buffer)
    {
        boolean hasLabel = label != null;
        buffer.writeBoolean(hasLabel);
        if (hasLabel)
            ComponentSerialization.STREAM_CODEC.encode(buffer, label);
        IconDataRegistry.serializeIcon((IIconData)iconData, buffer);
        buffer.writeBoolean(displayVerticalDistance);
        buffer.writeBoolean(isDynamic);
        serializeAdditional(buffer);
    }

    public final void readFromPacket(RegistryFriendlyByteBuf buffer)
    {
        internalId = buffer.readUUID();
        readFromPacketWithoutId(buffer);
    }

    public final void readFromPacketWithoutId(RegistryFriendlyByteBuf buffer)
    {
        boolean hasLabel = buffer.readBoolean();
        if (hasLabel)
            label = ComponentSerialization.STREAM_CODEC.decode(buffer);
        else
            label = null;
        iconData = IconDataRegistry.deserializeIcon(buffer);
        displayVerticalDistance = buffer.readBoolean();
        isDynamic = buffer.readBoolean();
        deserializeAdditional(buffer);
    }

    protected abstract void serializeAdditional(CompoundTag tag);

    protected abstract void deserializeAdditional(CompoundTag tag);

    protected abstract void serializeAdditional(FriendlyByteBuf tag);

    protected abstract void deserializeAdditional(FriendlyByteBuf tag);
}

