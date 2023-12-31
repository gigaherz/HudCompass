package dev.gigaherz.hudcompass.network;

import dev.gigaherz.hudcompass.HudCompass;
import dev.gigaherz.hudcompass.icons.BasicIconData;
import dev.gigaherz.hudcompass.waypoints.BasicWaypoint;
import dev.gigaherz.hudcompass.waypoints.PointsOfInterest;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.network.NetworkEvent;

public class AddWaypoint
{
    public final String label;
    public final double x;
    public final double y;
    public final double z;
    public final boolean isMarker;
    public final int iconIndex;

    public AddWaypoint(String label, double x, double y, double z, boolean isMarker, int iconIndex)
    {
        this.label = label;
        this.x = x;
        this.y = y;
        this.z = z;
        this.isMarker = isMarker;
        this.iconIndex = iconIndex;
    }

    public AddWaypoint(BasicWaypoint point)
    {
        Vec3 position = point.getPosition();
        this.label = point.getLabelText();
        this.x = position.x;
        this.y = position.y;
        this.z = position.z;
        BasicIconData data = (BasicIconData) point.getIconData();
        this.isMarker = data.getSerializer() == HudCompass.MAP_MARKER_SERIALIZER.get();
        this.iconIndex = data.iconIndex;
    }

    public AddWaypoint(FriendlyByteBuf buffer)
    {
        this.label = buffer.readUtf(256);
        this.x = buffer.readDouble();
        this.y = buffer.readDouble();
        this.z = buffer.readDouble();
        this.isMarker = buffer.readBoolean();
        this.iconIndex = buffer.readVarInt();
    }

    public void encode(FriendlyByteBuf buffer)
    {
        buffer.writeUtf(label);
        buffer.writeDouble(x);
        buffer.writeDouble(y);
        buffer.writeDouble(z);
        buffer.writeBoolean(isMarker);
        buffer.writeVarInt(iconIndex);
    }

    public void handle(NetworkEvent.Context context)
    {
        context.enqueueWork(() -> {
            PointsOfInterest.handleAddWaypoint(context.getSender(), this);
        });
    }
}
