package dev.gigaherz.hudcompass.network;

import dev.gigaherz.hudcompass.HudCompass;
import dev.gigaherz.hudcompass.waypoints.PointsOfInterest;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.handling.PlayPayloadContext;

public class ClientHello implements CustomPacketPayload
{
    public static final ResourceLocation ID = HudCompass.location("client_hello");

    public ClientHello()
    {
    }

    public ClientHello(FriendlyByteBuf buffer)
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
        context.workHandler().execute(() -> PointsOfInterest.remoteHello(context.player().orElseThrow()));
    }
}
