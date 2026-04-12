package dev.gigaherz.hudcompass.waypoints;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import dev.gigaherz.hudcompass.HudCompass;
import net.minecraft.core.Registry;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.VarInt;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.resources.ResourceKey;
import org.jetbrains.annotations.Nullable;

public record PointInfoType<T extends PointInfo<T>>(MapCodec<T> codec, StreamCodec<RegistryFriendlyByteBuf, T> streamCodec)
{
    public static final Codec<PointInfoType<?>> BY_ID_CODEC = HudCompass.POINT_INFO_TYPES_REGISTRY.byNameCodec();
    public static final StreamCodec<RegistryFriendlyByteBuf, PointInfoType<?>> BY_ID_STREAM_CODEC = new StreamCodec<>()
    {
        @Nullable
        public PointInfoType<?> decode(RegistryFriendlyByteBuf input)
        {
            int id = VarInt.read(input);
            return input.registryAccess().lookupOrThrow(HudCompass.POINT_INFO_TYPES_KEY).byId(id);
        }

        public void encode(RegistryFriendlyByteBuf output, PointInfoType<?> value)
        {
            int id = output.registryAccess().lookupOrThrow(HudCompass.POINT_INFO_TYPES_KEY).getIdOrThrow(value);
            VarInt.write(output, id);
        }
    };
}
