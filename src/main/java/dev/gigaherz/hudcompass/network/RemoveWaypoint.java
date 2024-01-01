package dev.gigaherz.hudcompass.network;

import dev.gigaherz.hudcompass.HudCompass;
import dev.gigaherz.hudcompass.waypoints.PointInfo;
import dev.gigaherz.hudcompass.waypoints.PointsOfInterest;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.handling.PlayPayloadContext;

import java.util.UUID;
import java.util.function.Supplier;

public class RemoveWaypoint implements CustomPacketPayload
{
    public static final ResourceLocation ID = HudCompass.location("remove_waypoint");

    public final UUID id;

    public RemoveWaypoint(PointInfo<?> point)
    {
        this.id = point.getInternalId();
    }

    public RemoveWaypoint(UUID point)
    {
        this.id = point;
    }

    public RemoveWaypoint(FriendlyByteBuf buffer)
    {
        this.id = buffer.readUUID();
    }

    public void write(FriendlyByteBuf buffer)
    {
        buffer.writeUUID(id);
    }

    @Override
    public ResourceLocation id()
    {
        return ID;
    }

    public void handle(PlayPayloadContext context)
    {
        context.workHandler().execute(() -> {
            PointsOfInterest.handleRemoveWaypoint(context.player().orElseThrow(), this);
        });
    }
}
