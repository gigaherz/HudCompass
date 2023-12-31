package dev.gigaherz.hudcompass.network;

import com.google.common.collect.ImmutableList;
import com.mojang.datafixers.util.Pair;
import dev.gigaherz.hudcompass.waypoints.PointInfo;
import dev.gigaherz.hudcompass.waypoints.PointInfoRegistry;
import dev.gigaherz.hudcompass.waypoints.PointsOfInterest;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.NetworkEvent;

import java.util.UUID;
import java.util.function.Supplier;

public class UpdateWaypointsFromGui
{
    public final ImmutableList<Pair<ResourceLocation, PointInfo<?>>> pointsAdded;
    public final ImmutableList<Pair<ResourceLocation, PointInfo<?>>> pointsUpdated;
    public final ImmutableList<UUID> pointsRemoved;

    public UpdateWaypointsFromGui(ImmutableList<Pair<ResourceLocation, PointInfo<?>>> added, ImmutableList<Pair<ResourceLocation, PointInfo<?>>> updated, ImmutableList<UUID> removed)
    {
        this.pointsAdded = added;
        this.pointsUpdated = updated;
        this.pointsRemoved = removed;
    }

    public UpdateWaypointsFromGui(FriendlyByteBuf buffer)
    {
        ImmutableList.Builder<Pair<ResourceLocation, PointInfo<?>>> toAdd = ImmutableList.builder();
        ImmutableList.Builder<Pair<ResourceLocation, PointInfo<?>>> toUpdate = ImmutableList.builder();
        ImmutableList.Builder<UUID> toRemove = ImmutableList.builder();

        int numberToAdd = buffer.readVarInt();
        for (int i = 0; i < numberToAdd; i++)
        {
            ResourceLocation id = buffer.readResourceLocation();
            PointInfo<?> pt = PointInfoRegistry.deserializePointWithoutId(buffer);
            toAdd.add(Pair.of(id, pt));
        }

        int numberToUpdate = buffer.readVarInt();
        for (int i = 0; i < numberToUpdate; i++)
        {
            ResourceLocation id = buffer.readResourceLocation();
            PointInfo<?> pt = PointInfoRegistry.deserializePoint(buffer);
            toUpdate.add(Pair.of(id, pt));
        }

        int numberToRemove = buffer.readVarInt();
        for (int i = 0; i < numberToRemove; i++)
        {
            toRemove.add(buffer.readUUID());
        }

        pointsAdded = toAdd.build();
        pointsUpdated = toUpdate.build();
        pointsRemoved = toRemove.build();
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    public void encode(FriendlyByteBuf buffer)
    {
        buffer.writeVarInt(pointsAdded.size());
        pointsAdded.forEach(pt -> {
            buffer.writeResourceLocation(pt.getFirst());
            PointInfoRegistry.serializePointWithoutId((PointInfo)pt.getSecond(), buffer);
        });

        buffer.writeVarInt(pointsUpdated.size());
        pointsUpdated.forEach(pt -> {
            buffer.writeResourceLocation(pt.getFirst());
            PointInfoRegistry.serializePoint((PointInfo)pt.getSecond(), buffer);
        });

        buffer.writeVarInt(pointsRemoved.size());
        pointsRemoved.forEach(buffer::writeUUID);
    }

    public boolean handle(NetworkEvent.Context context)
    {
        context.enqueueWork(() -> {
            if (context.getSender() != null)
                PointsOfInterest.handleUpdateFromGui(context.getSender(), this);
        });
        return true;
    }
}
