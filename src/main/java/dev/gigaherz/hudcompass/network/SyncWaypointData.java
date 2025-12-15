package dev.gigaherz.hudcompass.network;

import dev.gigaherz.hudcompass.HudCompass;
import dev.gigaherz.hudcompass.client.ClientHandler;
import dev.gigaherz.hudcompass.waypoints.PointsOfInterest;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import net.minecraft.core.RegistryAccess;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import net.neoforged.neoforge.network.connection.ConnectionType;
import net.neoforged.neoforge.network.handling.IPayloadContext;

public record SyncWaypointData(byte[] bytes) implements CustomPacketPayload
{
    public static final StreamCodec<ByteBuf, SyncWaypointData> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.byteArray(1 << 20L), SyncWaypointData::bytes,
            SyncWaypointData::new
    );

    public static final Identifier ID = HudCompass.location("sync_waypoint_data");
    public static final Type<SyncWaypointData> TYPE = new Type<>(ID);

    @Override
    public Type<? extends CustomPacketPayload> type()
    {
        return TYPE;
    }

    public static SyncWaypointData of(PointsOfInterest pointsData, RegistryAccess registryAccess)
    {
        var temp = new RegistryFriendlyByteBuf(Unpooled.buffer(), registryAccess, ConnectionType.NEOFORGE);
        pointsData.write(temp);
        var bytes = new byte[temp.readableBytes()];
        temp.readBytes(bytes, 0, bytes.length);
        return new SyncWaypointData(bytes);
    }

    public void handle(IPayloadContext context)
    {
        context.enqueueWork(() -> ClientHandler.handleWaypointSync(bytes));
    }
}
