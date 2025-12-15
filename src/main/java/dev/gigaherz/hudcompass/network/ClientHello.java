package dev.gigaherz.hudcompass.network;

import dev.gigaherz.hudcompass.HudCompass;
import dev.gigaherz.hudcompass.waypoints.PointsOfInterest;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import net.neoforged.neoforge.network.handling.IPayloadContext;

public class ClientHello implements CustomPacketPayload
{
    public static final ClientHello INSTANCE = new ClientHello();

    public static final Identifier ID = HudCompass.location("client_hello");

    public static final Type<ClientHello> TYPE = new Type<>(ID);

    public static final StreamCodec<ByteBuf, ClientHello> STREAM_CODEC = StreamCodec.unit(INSTANCE);

    @Override
    public Type<? extends CustomPacketPayload> type()
    {
        return TYPE;
    }

    private ClientHello()
    {
    }

    public void handle(IPayloadContext context)
    {
        context.enqueueWork(() -> PointsOfInterest.remoteHello(context.player()));
    }
}
