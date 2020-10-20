package dev.gigaherz.hudcompass.network;

import dev.gigaherz.hudcompass.waypoints.PointInfo;
import dev.gigaherz.hudcompass.waypoints.PointsOfInterest;
import net.minecraft.network.PacketBuffer;
import net.minecraftforge.fml.network.NetworkEvent;

import java.util.UUID;
import java.util.function.Supplier;

public class RemoveWaypoint
{
    public final UUID id;

    public RemoveWaypoint(PointInfo<?> point)
    {
        this.id = point.getInternalId();
    }

    public RemoveWaypoint(PacketBuffer buffer)
    {
        this.id = buffer.readUniqueId();
    }

    public void encode(PacketBuffer buffer)
    {
        buffer.writeUniqueId(id);
    }

    public boolean handle(Supplier<NetworkEvent.Context> ctx)
    {
        NetworkEvent.Context context = ctx.get();
        context.enqueueWork(() -> {
            PointsOfInterest.handleRemoveWaypoint(context.getSender(), this);
        });
        return true;
    }
}
