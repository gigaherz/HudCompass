package dev.gigaherz.hudcompass.network;

import dev.gigaherz.hudcompass.client.ClientHandler;
import net.minecraft.network.PacketBuffer;
import net.minecraftforge.fml.network.NetworkEvent;

import java.util.function.Supplier;

public class ServerHello
{

    public ServerHello()
    {
    }

    public ServerHello(PacketBuffer buffer)
    {
    }

    public void encode(PacketBuffer buffer)
    {
    }

    public boolean handle(Supplier<NetworkEvent.Context> ctx)
    {
        ctx.get().enqueueWork(ClientHandler::handleServerHello);
        return true;
    }
}
