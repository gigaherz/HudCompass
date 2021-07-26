package dev.gigaherz.hudcompass.network;

import dev.gigaherz.hudcompass.client.ClientHandler;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.fmllegacy.network.NetworkEvent;

import java.util.function.Supplier;

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

    public boolean handle(Supplier<NetworkEvent.Context> ctx)
    {
        ctx.get().enqueueWork(ClientHandler::handleServerHello);
        return true;
    }
}
