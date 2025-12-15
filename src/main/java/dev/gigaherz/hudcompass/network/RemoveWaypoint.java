package dev.gigaherz.hudcompass.network;

import dev.gigaherz.hudcompass.HudCompass;
import dev.gigaherz.hudcompass.waypoints.PointInfo;
import dev.gigaherz.hudcompass.waypoints.PointsOfInterest;
import net.minecraft.core.UUIDUtil;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import net.neoforged.neoforge.network.handling.IPayloadContext;

import java.util.UUID;

public record RemoveWaypoint(UUID id) implements CustomPacketPayload
{
    public static final Identifier ID = HudCompass.location("remove_waypoint");
    public static final Type<RemoveWaypoint> TYPE = new Type<>(ID);

    public static final StreamCodec<FriendlyByteBuf, RemoveWaypoint> STREAM_CODEC = StreamCodec.composite(
            UUIDUtil.STREAM_CODEC, RemoveWaypoint::id,
            RemoveWaypoint::new
    );

    @Override
    public Type<? extends CustomPacketPayload> type()
    {
        return TYPE;
    }

    public RemoveWaypoint(PointInfo<?> point)
    {
        this(point.getInternalId());
    }

    public void handle(IPayloadContext context)
    {
        context.enqueueWork(() -> {
            PointsOfInterest.handleRemoveWaypoint(context.player(), this);
        });
    }
}
