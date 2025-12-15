package dev.gigaherz.hudcompass.network;

import dev.gigaherz.hudcompass.HudCompass;
import dev.gigaherz.hudcompass.icons.BasicIconData;
import dev.gigaherz.hudcompass.waypoints.BasicWaypoint;
import dev.gigaherz.hudcompass.waypoints.PointsOfInterest;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.network.handling.IPayloadContext;

public record AddWaypoint(
        String label,
        double x,
        double y,
        double z,
        Identifier spriteName
) implements CustomPacketPayload
{
    public static final Identifier ID = HudCompass.location("add_waypoint");

    public static final Type<AddWaypoint> TYPE = new Type<>(ID);

    public static final StreamCodec<FriendlyByteBuf, AddWaypoint> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.stringUtf8(256), AddWaypoint::label,
            ByteBufCodecs.DOUBLE, AddWaypoint::x,
            ByteBufCodecs.DOUBLE, AddWaypoint::y,
            ByteBufCodecs.DOUBLE, AddWaypoint::z,
            Identifier.STREAM_CODEC, AddWaypoint::spriteName,
            AddWaypoint::new
    );

    @Override
    public Type<? extends CustomPacketPayload> type()
    {
        return TYPE;
    }

    public static AddWaypoint of(BasicWaypoint point)
    {
        Vec3 position = point.getPosition();
        BasicIconData data = (BasicIconData) point.getIconData();
        return new AddWaypoint(
                point.getLabelText(),
                position.x,
                position.y,
                position.z,
                data.spriteName
        );
    }

    public void handle(IPayloadContext context)
    {
        context.enqueueWork(() ->
                PointsOfInterest.handleAddWaypoint(context.player(), this)
        );
    }
}