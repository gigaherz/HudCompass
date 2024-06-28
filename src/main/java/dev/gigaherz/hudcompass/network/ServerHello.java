package dev.gigaherz.hudcompass.network;

import dev.gigaherz.hudcompass.HudCompass;
import dev.gigaherz.hudcompass.client.ClientHandler;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.handling.IPayloadContext;

public class ServerHello implements CustomPacketPayload
{
    public static final ServerHello INSTANCE = new ServerHello();

    public static final ResourceLocation ID = HudCompass.location("server_hello");
    public static final Type<ServerHello> TYPE = new Type<>(ID);

    public static final StreamCodec<ByteBuf, ServerHello> STREAM_CODEC = StreamCodec.unit(INSTANCE);

    @Override
    public Type<? extends CustomPacketPayload> type()
    {
        return TYPE;
    }

    private ServerHello()
    {
    }

    public void handle(IPayloadContext context)
    {
        context.enqueueWork(ClientHandler::handleServerHello);
    }
}
