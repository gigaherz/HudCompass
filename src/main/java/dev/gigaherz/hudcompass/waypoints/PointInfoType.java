package dev.gigaherz.hudcompass.waypoints;

import net.minecraftforge.registries.ForgeRegistryEntry;

import java.util.function.Supplier;

public final class PointInfoType<T extends PointInfo> extends ForgeRegistryEntry<PointInfoType<?>>
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
