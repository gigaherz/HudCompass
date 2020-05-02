package gigaherz.hudcompass.waypoints;

import gigaherz.hudcompass.icons.IIconData;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.nbt.CompoundNBT;
import net.minecraft.network.PacketBuffer;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import net.minecraftforge.registries.ObjectHolder;

public class BasicWaypoint extends PointInfo<BasicWaypoint>
{
    @ObjectHolder("hudcompass:basic")
    public static PointInfoType<BasicWaypoint> TYPE = null;

    private Vec3d position;

    public BasicWaypoint()
    {
        super(TYPE);
    }

    public BasicWaypoint(BlockPos exactPosition, String label, IIconData<?> iconData)
    {
        this(toVec3d(exactPosition), label, iconData);
    }

    public BasicWaypoint(Vec3d exactPosition, String label, IIconData<?> iconData)
    {
        this(TYPE, exactPosition, label, iconData);
    }

    public BasicWaypoint(PointInfoType<? extends BasicWaypoint> type, Vec3d exactPosition, String label, IIconData<?> iconData)
    {
        super(type, label, iconData);
        this.position = exactPosition;
    }

    @Override
    public Vec3d getPosition(PlayerEntity player)
    {
        return position;
    }

    public void setPosition(Vec3d position)
    {
        this.position = position;
    }

    @Override
    protected void serializeAdditional(CompoundNBT tag)
    {
        tag.putDouble("X", position.x);
        tag.putDouble("Y", position.x);
        tag.putDouble("Z", position.x);
    }

    @Override
    protected void deserializeAdditional(CompoundNBT tag)
    {
        position = new Vec3d(
                tag.getDouble("X"),
                tag.getDouble("Y"),
                tag.getDouble("Z")
        );
    }

    @Override
    protected void serializeAdditional(PacketBuffer buffer)
    {
        buffer.writeDouble(position.x);
        buffer.writeDouble(position.x);
        buffer.writeDouble(position.x);
    }

    @Override
    protected void deserializeAdditional(PacketBuffer buffer)
    {
        position = new Vec3d(
                buffer.readDouble(),
                buffer.readDouble(),
                buffer.readDouble()
        );
    }
}
