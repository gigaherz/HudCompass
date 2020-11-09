package dev.gigaherz.hudcompass.waypoints;

import dev.gigaherz.hudcompass.icons.IIconData;
import net.minecraft.nbt.CompoundNBT;
import net.minecraft.network.PacketBuffer;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.vector.Vector3d;
import net.minecraft.util.text.StringTextComponent;
import net.minecraftforge.registries.ObjectHolder;

import javax.annotation.Nullable;

public class BasicWaypoint extends PointInfo<BasicWaypoint>
{
    @ObjectHolder("hudcompass:basic")
    public static PointInfoType<BasicWaypoint> TYPE = null;

    private String label;

    private Vector3d position;

    public BasicWaypoint()
    {
        super(TYPE, false);
    }

    public BasicWaypoint(BlockPos exactPosition, String label, IIconData<?> iconData)
    {
        this(toVec3d(exactPosition), label, iconData);
        this.label = label;
    }

    public BasicWaypoint(Vector3d exactPosition, String label, IIconData<?> iconData)
    {
        this(TYPE, exactPosition, label, iconData);
    }

    public BasicWaypoint(PointInfoType<? extends BasicWaypoint> type, Vector3d exactPosition, String label, IIconData<?> iconData)
    {
        super(type, false, new StringTextComponent(label), iconData);
        this.position = exactPosition;
    }

    @Override
    public Vector3d getPosition()
    {
        return position;
    }

    public void setPosition(Vector3d position)
    {
        if (MathHelper.epsilonEquals(position.squareDistanceTo(position),0))
        {
            this.position = position;
            markDirty();
        }
    }

    public String getLabelText()
    {
        return label;
    }

    public void setLabelText(String label)
    {
        this.label = label;
        super.setLabel(label.length() > 0 ? new StringTextComponent(label) : null);
    }

    @Override
    protected void serializeAdditional(CompoundNBT tag)
    {
        tag.putDouble("X", position.x);
        tag.putDouble("Y", position.y);
        tag.putDouble("Z", position.z);
        tag.putString("Text", label);
    }

    @Override
    protected void deserializeAdditional(CompoundNBT tag)
    {
        position = new Vector3d(
                tag.getDouble("X"),
                tag.getDouble("Y"),
                tag.getDouble("Z")
        );
        label = tag.getString("Text");
    }

    @Override
    protected void serializeAdditional(PacketBuffer buffer)
    {
        buffer.writeDouble(position.x);
        buffer.writeDouble(position.y);
        buffer.writeDouble(position.z);
        buffer.writeString(label, 1024);
    }

    @Override
    protected void deserializeAdditional(PacketBuffer buffer)
    {
        position = new Vector3d(
                buffer.readDouble(),
                buffer.readDouble(),
                buffer.readDouble()
        );
        label = buffer.readString(1024);
    }
}
