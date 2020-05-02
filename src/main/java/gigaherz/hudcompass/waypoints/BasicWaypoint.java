package gigaherz.hudcompass.waypoints;

import gigaherz.hudcompass.icons.IIconData;
import net.minecraft.client.renderer.Vector3d;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.nbt.CompoundNBT;
import net.minecraft.util.math.BlockPos;

public class BasicWaypoint extends PointInfo
{
    private Vector3d position;

    public BasicWaypoint(BlockPos exactPosition, String label, IIconData<?> iconData)
    {
        this(toVec3d(exactPosition), label, iconData);
    }

    public BasicWaypoint(Vector3d exactPosition, String label, IIconData<?> iconData)
    {
        super(label, iconData);
        this.position = exactPosition;
    }

    @Override
    public Vector3d getPosition(PlayerEntity player)
    {
        return position;
    }

    @Override
    public CompoundNBT serializeNBT()
    {
        CompoundNBT tag =  super.serializeNBT();
        tag.putDouble("X", position.x);
        tag.putDouble("Y", position.x);
        tag.putDouble("Z", position.x);
        return tag;
    }

    @Override
    public void deserializeNBT(CompoundNBT nbt)
    {
        super.deserializeNBT(nbt);
        position = new Vector3d(
                nbt.getDouble("X"),
                nbt.getDouble("Y"),
                nbt.getDouble("Z")
        );
    }
}
