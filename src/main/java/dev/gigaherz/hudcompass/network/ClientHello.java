package dev.gigaherz.hudcompass.network;

import dev.gigaherz.hudcompass.waypoints.PointsOfInterest;
import net.minecraft.network.FriendlyByteBuf;
import net.neoforged.neoforge.network.NetworkEvent;

public class ClientHello
{

    public ClientHello()
    {
    }

    public ClientHello(FriendlyByteBuf buffer)
    {
    }

    public void encode(FriendlyByteBuf buffer)
    {
    }

    public void handle(NetworkEvent.Context context)
    {
        context.enqueueWork(() -> PointsOfInterest.remoteHello(context.getSender()));
    }
}
