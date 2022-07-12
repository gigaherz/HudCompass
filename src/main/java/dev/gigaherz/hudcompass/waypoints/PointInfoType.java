package dev.gigaherz.hudcompass.waypoints;

import dev.gigaherz.hudcompass.icons.IIconData;

import java.util.function.Supplier;

public final class PointInfoType<T extends PointInfo<T>>
{
    private final Supplier<T> factory;

    public PointInfoType(Supplier<T> factory)
    {
        this.factory = factory;
    }

    public final T create()
    {
        return factory.get();
    }
}
