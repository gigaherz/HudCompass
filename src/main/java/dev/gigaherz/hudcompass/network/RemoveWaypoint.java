package dev.gigaherz.hudcompass.network;

import dev.gigaherz.hudcompass.waypoints.PointInfo;
import dev.gigaherz.hudcompass.waypoints.PointsOfInterest;
import net.minecraft.network.FriendlyByteBuf;
import net.neoforged.neoforge.network.NetworkEvent;

import java.util.UUID;
import java.util.function.Supplier;

public class RemoveWaypoint
{
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

    public void encode(FriendlyByteBuf buffer)
    {
        buffer.writeUUID(id);
    }

    public boolean handle(NetworkEvent.Context context)
    {
        context.enqueueWork(() -> {
            PointsOfInterest.handleRemoveWaypoint(context.getSender(), this);
        });
        return true;
    }
}
