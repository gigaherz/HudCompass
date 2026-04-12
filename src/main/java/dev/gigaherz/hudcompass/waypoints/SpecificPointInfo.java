package dev.gigaherz.hudcompass.waypoints;

import dev.gigaherz.hudcompass.icons.IIconData;
import net.minecraft.network.chat.Component;
import net.minecraft.world.phys.Vec3;

import javax.annotation.Nullable;
import java.util.Optional;
import java.util.UUID;

public abstract class SpecificPointInfo<T extends PointInfo<T>, I extends IIconData<I>> extends PointInfo<T>
{
    public SpecificPointInfo(PointInfoType<? extends T> type, boolean isDynamic)
    {
        super(type, isDynamic);
    }

    public SpecificPointInfo(PointInfoType<T> type, boolean isDynamic, @Nullable Component label, I iconData)
    {
        super(type, isDynamic, label, iconData);
    }

    @SuppressWarnings("OptionalUsedAsFieldOrParameterType")
    protected SpecificPointInfo(PointInfoType<T> type, boolean isDynamic, UUID internalId, Optional<Component> label, boolean displayVerticalDistance, IIconData<?> iconData)
    {
        super(type, isDynamic, internalId, label, displayVerticalDistance, iconData);
    }


    @SuppressWarnings("unchecked")
    @Override
    public I getIconData()
    {
        return (I) super.getIconData();
    }
}
