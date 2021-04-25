package dev.gigaherz.hudcompass.util;

import com.google.common.collect.Maps;
import com.mojang.datafixers.util.Pair;
import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.DynamicOps;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.common.util.Lazy;
import net.minecraftforge.registries.ForgeRegistry;
import net.minecraftforge.registries.IForgeRegistry;
import net.minecraftforge.registries.IForgeRegistryEntry;

import java.util.Map;
import java.util.function.Supplier;

/**
 * Gets a codec that encodes a regsitry entry by its ID.
 *
 * How to use:
 *
 *   Declare as:
 *     public static final Lazy<Codec<MyThing>> CODEC = ForgeRegistryEntryCodec.getOrcreate(MY_THING_REGISTRY_SUPPLIER);
 *
 *   Then reference like:
 *     CODEC.get().fieldOf("whatever")
 *
 * @param <T> The type of registry entry this codec deals with
 */
public class ForgeRegistryEntryCodec<T extends IForgeRegistryEntry<T>> implements Codec<T>
{
    private static final Map<IForgeRegistry<?>, Codec<?>> cache = Maps.newConcurrentMap();
    private final ForgeRegistry<T> forgeRegistry;

    public static <T extends IForgeRegistryEntry<T>> Codec<T> getOrCreate(IForgeRegistry<T> registry)
    {
        //noinspection unchecked
        return (Codec<T>)cache.computeIfAbsent(registry, ForgeRegistryEntryCodec::new);
    }

    public static <T extends IForgeRegistryEntry<T>> Supplier<Codec<T>> getOrCreate(Supplier<IForgeRegistry<T>> registry)
    {
        return Lazy.of(() -> getOrCreate(registry.get()));
    }

    private final IForgeRegistry<T> registry;

    private ForgeRegistryEntryCodec(IForgeRegistry<T> registry)
    {
        this.registry = registry;
        this.forgeRegistry = registry instanceof ForgeRegistry ? (ForgeRegistry<T>)registry : null;
    }

    public <TOps> DataResult<Pair<T, TOps>> decode(DynamicOps<TOps> ops, TOps data)
    {
        if (ops.compressMaps() && forgeRegistry != null)
        {
            return ops.getNumberValue(data).flatMap((registryId) -> {
                T t = forgeRegistry.getValue(registryId.intValue());

                if (t == null)
                    return DataResult.error("Unknown registry id: " + registryId);

                return DataResult.success(t);
            }).map((encodedValue) -> {
                return Pair.of((T) encodedValue, ops.empty());
            });
        }
        return ResourceLocation.CODEC.decode(ops, data).flatMap((encodedRegistryPair) -> {
            T t = registry.getValue(encodedRegistryPair.getFirst());

            if (t == null)
                return DataResult.error("Unknown registry key: " + encodedRegistryPair.getFirst());

            return DataResult.success(Pair.of(t, encodedRegistryPair.getSecond()));
        });
    }

    public <TOps> DataResult<TOps> encode(T entry, DynamicOps<TOps> ops, TOps data) {
        ResourceLocation resourcelocation = this.registry.getKey(entry);
        if (resourcelocation == null)
            return DataResult.error("Unknown registry element " + entry);

        if (ops.compressMaps() && forgeRegistry != null)
            return ops.mergeToPrimitive(data, ops.createInt(forgeRegistry.getID(entry)));

        return ops.mergeToPrimitive(data, ops.createString(resourcelocation.toString()));
    }
}
