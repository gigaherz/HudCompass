package dev.gigaherz.hudcompass.network;

import dev.gigaherz.hudcompass.HudCompass;
import dev.gigaherz.hudcompass.waypoints.PointsOfInterest;
import gigaherz.hudcompass.waypoints.PointAddRemoveEntry;
import net.minecraft.core.UUIDUtil;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import net.neoforged.neoforge.network.handling.IPayloadContext;

import java.util.List;
import java.util.UUID;

public record UpdateWaypointsFromGui(
        List<PointAddRemoveEntry> pointsAdded,
        List<PointAddRemoveEntry> pointsUpdated,
        List<UUID> pointsRemoved
        ) implements CustomPacketPayload
{
    public static final StreamCodec<RegistryFriendlyByteBuf, UpdateWaypointsFromGui> STREAM_CODEC = StreamCodec.composite(
            PointAddRemoveEntry.STREAM_CODEC.apply(ByteBufCodecs.list()), UpdateWaypointsFromGui::pointsAdded,
            PointAddRemoveEntry.STREAM_CODEC.apply(ByteBufCodecs.list()), UpdateWaypointsFromGui::pointsUpdated,
            UUIDUtil.STREAM_CODEC.apply(ByteBufCodecs.list()), UpdateWaypointsFromGui::pointsRemoved,
            UpdateWaypointsFromGui::new
    );

    public static final Identifier ID = HudCompass.location("update_waypoints_from_gui");
    public static final Type<UpdateWaypointsFromGui> TYPE = new Type<>(ID);

    @Override
    public Type<? extends CustomPacketPayload> type()
    {
        return TYPE;
    }

    public void handle(IPayloadContext context)
    {
        context.enqueueWork(() -> PointsOfInterest.handleUpdateFromGui(context.player(), this));
    }
}
