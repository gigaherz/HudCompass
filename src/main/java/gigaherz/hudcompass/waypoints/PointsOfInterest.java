package gigaherz.hudcompass.waypoints;

import com.google.common.collect.Sets;
import gigaherz.hudcompass.HudCompass;
import net.minecraft.nbt.INBT;
import net.minecraft.nbt.ListNBT;
import net.minecraft.util.Direction;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.common.capabilities.CapabilityInject;
import net.minecraftforge.common.capabilities.CapabilityManager;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.Set;

public class PointsOfInterest
{
    @Nonnull
    @CapabilityInject(PointsOfInterest.class)
    public static Capability<PointsOfInterest> INSTANCE = null; // assigned by forge

    public static void init()
    {
        CapabilityManager.INSTANCE.register(
                PointsOfInterest.class,
                new Storage(), PointsOfInterest::new
        );
    }

    private static class Storage implements Capability.IStorage<PointsOfInterest>
    {
        @Nullable
        @Override
        public INBT writeNBT(Capability<PointsOfInterest> capability, PointsOfInterest instance, Direction side)
        {
            ListNBT tag = new ListNBT();

            for(PointInfo point : instance.points)
            {
                tag.add(point.serializeNBT());
            }

            return tag;
        }

        @Override
        public void readNBT(Capability<PointsOfInterest> capability, PointsOfInterest instance, Direction side, INBT nbt)
        {
            if (!(nbt instanceof ListNBT))
            {
                HudCompass.LOGGER.error("Deserializing PointsOfInterest capability: stored nbt is not a List tag!");
                return;
            }
            ListNBT tag = (ListNBT)nbt;
            for(int i=0;i<tag.size();i++)
            {
                INBT item = tag.get(i);

            }
        }
    }

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
}
