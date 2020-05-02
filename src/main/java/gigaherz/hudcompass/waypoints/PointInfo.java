package gigaherz.hudcompass.waypoints;

import gigaherz.hudcompass.icons.IIconData;
import gigaherz.hudcompass.icons.IconDataRegistry;
import net.minecraft.client.renderer.Vector3d;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.nbt.CompoundNBT;
import net.minecraft.util.math.BlockPos;
import net.minecraftforge.common.util.INBTSerializable;

public abstract class PointInfo implements INBTSerializable<CompoundNBT>
{
    public static Vector3d toVec3d(BlockPos pos)
    {
        return new Vector3d(pos.getX()+0.5,pos.getY()+0.5,pos.getZ()+0.5);
    }

    private String label;
    private IIconData<?> iconData;
    private boolean displayVerticalDistance = true;

    public PointInfo(String label, IIconData<?> iconData)
    {
        this.label = label;
        this.iconData = iconData;
    }

    public abstract Vector3d getPosition(PlayerEntity player);

    public String getLabel(PlayerEntity player)
    {
        return this.label;
    }

    public PointInfo noVerticalDistance()
    {
        this.displayVerticalDistance = false;
        return this;
    }

    public boolean displayVerticalDistance(PlayerEntity player)
    {
        return displayVerticalDistance;
    }

    @Override
    public CompoundNBT serializeNBT()
    {
        CompoundNBT tag = new CompoundNBT();
        tag.putString("Label", label);
        tag.put("Icon", IconDataRegistry.serializeIcon(iconData));
        tag.putBoolean("DisplayVerticalDistance", displayVerticalDistance);
        return tag;
    }

    @Override
    public void deserializeNBT(CompoundNBT nbt)
    {
        label = nbt.getString("Label");
        iconData = IconDataRegistry.deserializeIcon(nbt.getCompound("Icon"));
        displayVerticalDistance = nbt.getBoolean("DisplayVerticalDistance");
    }

    public IIconData<?> getIconData()
    {
        return iconData;
    }
}
