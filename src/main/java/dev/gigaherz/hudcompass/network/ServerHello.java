package dev.gigaherz.hudcompass.network;

import dev.gigaherz.hudcompass.client.ClientHandler;
import net.minecraft.network.FriendlyByteBuf;
import net.neoforged.neoforge.network.NetworkEvent;

public class ServerHello
{

    public ServerHello()
    {
    }

    public ServerHello(FriendlyByteBuf buffer)
    {
    }

    public void encode(FriendlyByteBuf buffer)
    {
    }

    public void handle(NetworkEvent.Context ctx)
    {
        ctx.enqueueWork(ClientHandler::handleServerHello);
    }
}
