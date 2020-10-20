package dev.gigaherz.hudcompass.network;

import com.google.common.collect.ImmutableList;
import dev.gigaherz.hudcompass.client.ClientHandler;
import dev.gigaherz.hudcompass.waypoints.PointInfoRegistry;
import dev.gigaherz.hudcompass.waypoints.PointInfo;
import net.minecraft.network.PacketBuffer;
import net.minecraftforge.fml.network.NetworkEvent;

import java.util.UUID;
import java.util.function.Supplier;

public class SyncWaypointData
{
    public final boolean replaceAll;
    public final ImmutableList<PointInfo<?>> pointsAddedOrUpdated;
    public final ImmutableList<UUID> pointsRemoved;

    public SyncWaypointData(boolean replaceAll, ImmutableList<PointInfo<?>> addedOrUpdated, ImmutableList<UUID> removed)
    {
        this.replaceAll = replaceAll;
        this.pointsAddedOrUpdated = addedOrUpdated;
        this.pointsRemoved = removed;
    }

    public SyncWaypointData(PacketBuffer buffer)
    {
        ImmutableList.Builder<PointInfo<?>> toAdd = ImmutableList.builder();
        ImmutableList.Builder<UUID> toRemove = ImmutableList.builder();

        replaceAll = buffer.readBoolean();

        int numberToAdd = buffer.readVarInt();
        for(int i=0;i<numberToAdd;i++)
        {
            toAdd.add(PointInfoRegistry.deserializePoint(buffer));
        }

        int numberToRemove = buffer.readVarInt();
        for(int i=0;i<numberToRemove;i++)
        {
            toRemove.add(buffer.readUniqueId());
        }

        pointsAddedOrUpdated = toAdd.build();
        pointsRemoved = toRemove.build();
    }

    public void encode(PacketBuffer buffer)
    {
        buffer.writeBoolean(replaceAll);

        buffer.writeVarInt(pointsAddedOrUpdated.size());
        pointsAddedOrUpdated.forEach(pt -> PointInfoRegistry.serializePoint(pt, buffer));

        buffer.writeVarInt(pointsRemoved.size());
        pointsRemoved.forEach(buffer::writeUniqueId);
    }

    public boolean handle(Supplier<NetworkEvent.Context> ctx)
    {
        ctx.get().enqueueWork(() -> ClientHandler.handleWaypointSync(this));
        return true;
    }
}
