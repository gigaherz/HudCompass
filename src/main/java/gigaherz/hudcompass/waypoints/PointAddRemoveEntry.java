package gigaherz.hudcompass.waypoints;

import dev.gigaherz.hudcompass.waypoints.PointInfo;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.resources.Identifier;

public record PointAddRemoveEntry(Identifier key, PointInfo<?> point)
{

    public static final StreamCodec<RegistryFriendlyByteBuf, PointAddRemoveEntry> STREAM_CODEC = StreamCodec.composite(
            Identifier.STREAM_CODEC, PointAddRemoveEntry::key,
            PointInfo.STREAM_CODEC, PointAddRemoveEntry::point,
            PointAddRemoveEntry::new
    );
}
