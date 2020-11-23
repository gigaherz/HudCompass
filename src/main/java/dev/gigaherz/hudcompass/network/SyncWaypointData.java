package dev.gigaherz.hudcompass.network;

import com.google.common.collect.ImmutableList;
import com.mojang.datafixers.util.Pair;
import dev.gigaherz.hudcompass.client.ClientHandler;
import dev.gigaherz.hudcompass.waypoints.PointInfoRegistry;
import dev.gigaherz.hudcompass.waypoints.PointInfo;
import dev.gigaherz.hudcompass.waypoints.PointsOfInterest;
import net.minecraft.nbt.CompoundNBT;
import net.minecraft.nbt.ListNBT;
import net.minecraft.network.PacketBuffer;
import net.minecraft.util.RegistryKey;
import net.minecraft.util.ResourceLocation;
import net.minecraft.world.World;
import net.minecraftforge.common.util.Constants;
import net.minecraftforge.fml.network.NetworkEvent;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Supplier;

public class SyncWaypointData
{
    public final ListNBT points;

    public SyncWaypointData(ListNBT pointsData)
    {
        this.points = pointsData;
    }

    public SyncWaypointData(PacketBuffer buffer)
    {
        points = buffer.readCompoundTag().getList("Points", Constants.NBT.TAG_COMPOUND);
    }

    public void encode(PacketBuffer buffer)
    {
        CompoundNBT tag = new CompoundNBT();
        tag.put("Points", points);
        buffer.writeCompoundTag(tag);
    }

    public boolean handle(Supplier<NetworkEvent.Context> ctx)
    {
        ctx.get().enqueueWork(() -> ClientHandler.handleWaypointSync(this));
        return true;
    }
}
