package dev.gigaherz.hudcompass.network;

import dev.gigaherz.hudcompass.waypoints.PointsOfInterest;
import net.minecraft.network.PacketBuffer;
import net.minecraftforge.fml.network.NetworkEvent;

import java.util.function.Supplier;

public class ClientHello
{

    public ClientHello()
    {
    }

    public ClientHello(PacketBuffer buffer)
    {
    }

    public void encode(PacketBuffer buffer)
    {
    }

    public boolean handle(Supplier<NetworkEvent.Context> ctx)
    {
        NetworkEvent.Context context = ctx.get();
        context.enqueueWork(() -> PointsOfInterest.remoteHello(context.getSender()));
        return true;
    }
}
