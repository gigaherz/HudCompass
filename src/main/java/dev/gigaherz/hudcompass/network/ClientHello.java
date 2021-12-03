package dev.gigaherz.hudcompass.network;

import dev.gigaherz.hudcompass.waypoints.PointsOfInterest;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

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

    public boolean handle(Supplier<NetworkEvent.Context> ctx)
    {
        NetworkEvent.Context context = ctx.get();
        context.enqueueWork(() -> PointsOfInterest.remoteHello(context.getSender()));
        return true;
    }
}
