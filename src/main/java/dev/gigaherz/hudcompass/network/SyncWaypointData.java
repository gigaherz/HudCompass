package dev.gigaherz.hudcompass.network;

import dev.gigaherz.hudcompass.client.ClientHandler;
import dev.gigaherz.hudcompass.waypoints.PointsOfInterest;
import io.netty.buffer.Unpooled;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

public class SyncWaypointData
{
    public byte[] bytes;

    public SyncWaypointData(PointsOfInterest pointsData)
    {
        FriendlyByteBuf temp = new FriendlyByteBuf(Unpooled.buffer());
        pointsData.write(temp);
        bytes = new byte[temp.readableBytes()];
        temp.readBytes(bytes, 0, bytes.length);
    }

    public SyncWaypointData(FriendlyByteBuf buffer)
    {
        bytes = buffer.readByteArray();
    }

    public void encode(FriendlyByteBuf buffer)
    {
        buffer.writeByteArray(bytes);
    }

    public boolean handle(Supplier<NetworkEvent.Context> ctx)
    {
        ctx.get().enqueueWork(() -> ClientHandler.handleWaypointSync(bytes));
        return true;
    }
}
