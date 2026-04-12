package dev.gigaherz.hudcompass.icons;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import dev.gigaherz.hudcompass.HudCompass;
import dev.gigaherz.hudcompass.waypoints.PointInfoType;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.VarInt;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import org.jetbrains.annotations.Nullable;

public record IconDataSerializer<T extends IIconData<T>>(MapCodec<T> codec, StreamCodec<RegistryFriendlyByteBuf, T> streamCodec)
{
    public static final Codec<IconDataSerializer<?>> BY_ID_CODEC = HudCompass.ICON_DATA_SERIALIZERS_REGISTRY.byNameCodec();
    public static final StreamCodec<RegistryFriendlyByteBuf, IconDataSerializer<?>> BY_ID_STREAM_CODEC = new StreamCodec<>()
    {
        @Nullable
        public IconDataSerializer<?> decode(RegistryFriendlyByteBuf input)
        {
            int id = VarInt.read(input);
            return input.registryAccess().lookupOrThrow(HudCompass.ICON_DATA_SERIALIZERS_KEY).byId(id);
        }

        public void encode(RegistryFriendlyByteBuf output, IconDataSerializer<?> value)
        {
            int id = output.registryAccess().lookupOrThrow(HudCompass.ICON_DATA_SERIALIZERS_KEY).getIdOrThrow(value);
            VarInt.write(output, id);
        }
    };
}
