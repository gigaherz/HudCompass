package dev.gigaherz.hudcompass.network;

import dev.gigaherz.hudcompass.HudCompass;
import dev.gigaherz.hudcompass.client.ClientHandler;
import dev.gigaherz.hudcompass.waypoints.PointsOfInterest;
import io.netty.buffer.Unpooled;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.handling.PlayPayloadContext;

public class SyncWaypointData implements CustomPacketPayload
{
    public static final ResourceLocation ID = HudCompass.location("sync_waypoint_data");

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

    public void write(FriendlyByteBuf buffer)
    {
        buffer.writeByteArray(bytes);
    }

    @Override
    public ResourceLocation id()
    {
        return ID;
    }

    public void handle(PlayPayloadContext context)
    {
        context.workHandler().execute(() -> ClientHandler.handleWaypointSync(bytes));
    }
}
