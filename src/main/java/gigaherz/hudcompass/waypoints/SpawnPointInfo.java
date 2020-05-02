package gigaherz.hudcompass.waypoints;

import gigaherz.hudcompass.icons.BasicIconData;
import net.minecraft.client.renderer.Vector3d;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.math.BlockPos;

public class SpawnPointInfo extends PointInfo
{
    public SpawnPointInfo()
    {
        super("Home", BasicIconData.mapMarker(8));
    }

    @Override
    public Vector3d getPosition(PlayerEntity player)
    {
        BlockPos spawn = player.getBedPosition().orElseGet(() -> player.world.getSpawnPoint());

        return new Vector3d(spawn.getX()+0.5, spawn.getY()+0.5, spawn.getZ()+0.5);
    }
}
