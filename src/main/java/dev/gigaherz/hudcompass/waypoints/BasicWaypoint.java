package dev.gigaherz.hudcompass.waypoints;

import dev.gigaherz.hudcompass.icons.IIconData;
import net.minecraft.nbt.CompoundNBT;
import net.minecraft.network.PacketBuffer;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.vector.Vector3d;
import net.minecraftforge.registries.ObjectHolder;

public class BasicWaypoint extends PointInfo<BasicWaypoint>
{
    @ObjectHolder("hudcompass:basic")
    public static PointInfoType<BasicWaypoint> TYPE = null;

    private Vector3d position;

    public BasicWaypoint()
    {
        super(TYPE, false);
    }

    public BasicWaypoint(BlockPos exactPosition, String label, IIconData<?> iconData)
    {
        this(toVec3d(exactPosition), label, iconData);
    }

    public BasicWaypoint(Vector3d exactPosition, String label, IIconData<?> iconData)
    {
        this(TYPE, exactPosition, label, iconData);
    }

    public BasicWaypoint(PointInfoType<? extends BasicWaypoint> type, Vector3d exactPosition, String label, IIconData<?> iconData)
    {
        super(type, false, label, iconData);
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

    @Override
    protected void serializeAdditional(CompoundNBT tag)
    {
        tag.putDouble("X", position.x);
        tag.putDouble("Y", position.y);
        tag.putDouble("Z", position.z);
    }

    @Override
    protected void deserializeAdditional(CompoundNBT tag)
    {
        position = new Vector3d(
                tag.getDouble("X"),
                tag.getDouble("Y"),
                tag.getDouble("Z")
        );
    }

    @Override
    protected void serializeAdditional(PacketBuffer buffer)
    {
        buffer.writeDouble(position.x);
        buffer.writeDouble(position.y);
        buffer.writeDouble(position.z);
    }

    @Override
    protected void deserializeAdditional(PacketBuffer buffer)
    {
        position = new Vector3d(
                buffer.readDouble(),
                buffer.readDouble(),
                buffer.readDouble()
        );
    }
}
