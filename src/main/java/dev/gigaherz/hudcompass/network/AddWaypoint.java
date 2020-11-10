package dev.gigaherz.hudcompass.network;

import dev.gigaherz.hudcompass.icons.BasicIconData;
import dev.gigaherz.hudcompass.waypoints.BasicWaypoint;
import dev.gigaherz.hudcompass.waypoints.PointsOfInterest;
import net.minecraft.network.PacketBuffer;
import net.minecraftforge.fml.network.NetworkEvent;

import java.util.function.Supplier;

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
        this.label = point.getLabelText();
        this.x = point.getPosition().x;
        this.y = point.getPosition().y;
        this.z = point.getPosition().z;
        BasicIconData data = (BasicIconData)point.getIconData();
        this.isMarker = data.getSerializer() == BasicIconData.Serializer.MAP_SERIALIZER;
        this.iconIndex = data.iconIndex;
    }

    public AddWaypoint(PacketBuffer buffer)
    {
        this.label = buffer.readString(256);
        this.x = buffer.readDouble();
        this.y = buffer.readDouble();
        this.z = buffer.readDouble();
        this.isMarker = buffer.readBoolean();
        this.iconIndex = buffer.readVarInt();
    }

    public void encode(PacketBuffer buffer)
    {
        buffer.writeString(label);
        buffer.writeDouble(x);
        buffer.writeDouble(y);
        buffer.writeDouble(z);
        buffer.writeBoolean(isMarker);
        buffer.writeVarInt(iconIndex);
    }

    public boolean handle(Supplier<NetworkEvent.Context> ctx)
    {
        NetworkEvent.Context context = ctx.get();
        context.enqueueWork(() -> {
            PointsOfInterest.handleAddWaypoint(context.getSender(), this);
        });
        return true;
    }
}
