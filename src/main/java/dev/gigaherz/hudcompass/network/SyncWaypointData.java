package dev.gigaherz.hudcompass.network;

import dev.gigaherz.hudcompass.client.ClientHandler;
import dev.gigaherz.hudcompass.waypoints.PointsOfInterest;
import io.netty.buffer.Unpooled;
import net.minecraft.network.PacketBuffer;
import net.minecraftforge.fml.network.NetworkEvent;

import java.util.Arrays;
import java.util.function.Supplier;

public class SyncWaypointData
{
    public byte[] bytes;

    public SyncWaypointData(PointsOfInterest pointsData)
    {
        PacketBuffer temp = new PacketBuffer(Unpooled.buffer());
        pointsData.write(temp);
        bytes = new byte[temp.readableBytes()];
        temp.readBytes(bytes, 0, bytes.length);
    }

    public SyncWaypointData(PacketBuffer buffer)
    {
        bytes = buffer.readByteArray();
    }

    public void encode(PacketBuffer buffer)
    {
        buffer.writeByteArray(bytes);
    }

    public boolean handle(Supplier<NetworkEvent.Context> ctx)
    {
        ctx.get().enqueueWork(() -> ClientHandler.handleWaypointSync(bytes));
        return true;
    }
}
