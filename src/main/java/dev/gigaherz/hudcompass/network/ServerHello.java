package dev.gigaherz.hudcompass.network;

import dev.gigaherz.hudcompass.HudCompass;
import dev.gigaherz.hudcompass.client.ClientHandler;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.handling.PlayPayloadContext;

public class ServerHello implements CustomPacketPayload
{
    public static final ResourceLocation ID = HudCompass.location("server_hello");

    public ServerHello()
    {
    }

    public ServerHello(FriendlyByteBuf buffer)
    {
    }

    public void write(FriendlyByteBuf buffer)
    {
    }

    @Override
    public ResourceLocation id()
    {
        return ID;
    }

    public void handle(PlayPayloadContext context)
    {
        context.workHandler().execute(ClientHandler::handleServerHello);
    }
}
