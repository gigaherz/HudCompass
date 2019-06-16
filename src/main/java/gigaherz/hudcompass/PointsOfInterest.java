package gigaherz.hudcompass;

import com.google.common.collect.Sets;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.math.BlockPos;

import javax.vecmath.Vector3d;
import java.util.Set;

public class PointsOfInterest
{
    public static final PointsOfInterest CLIENT = new PointsOfInterest();

    private Set<PointInfo> points = Sets.newHashSet();

    {
        points.add(new SpawnPointInfo());
    }

    public Set<PointInfo> getPoints()
    {
        return points;
    }

    public void updateByPosition(boolean onTheClient)
    {
        if (onTheClient)
        {
        }
    }

    public void addPoint(PointInfo pointInfo)
    {
        points.add(pointInfo);
    }

    private static class SpawnPointInfo extends PointInfo
    {
        public SpawnPointInfo()
        {
            super(new Vector3d(), "Home", 8);
        }

        @Override
        public Vector3d getPosition(PlayerEntity player)
        {
            BlockPos spawn = player.getBedPosition().orElseGet(() -> player.world.getSpawnPoint());

            return new Vector3d(spawn.getX()+0.5, spawn.getY()+0.5, spawn.getZ()+0.5);
        }
    }
}
