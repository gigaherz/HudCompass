package gigaherz.hudcompass.waypoints;

import dev.gigaherz.hudcompass.waypoints.PointInfo;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.resources.ResourceLocation;

public record PointAddRemoveEntry(ResourceLocation key, PointInfo<?> point)
{

    public static final StreamCodec<RegistryFriendlyByteBuf, PointAddRemoveEntry> STREAM_CODEC = StreamCodec.composite(
            ResourceLocation.STREAM_CODEC, PointAddRemoveEntry::key,
            PointInfo.STREAM_CODEC, PointAddRemoveEntry::point,
            PointAddRemoveEntry::new
    );
}
