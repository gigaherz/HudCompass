package gigaherz.hudcompass.waypoints;

import com.google.common.collect.Sets;
import gigaherz.hudcompass.HudCompass;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.nbt.CompoundNBT;
import net.minecraft.nbt.INBT;
import net.minecraft.nbt.ListNBT;
import net.minecraft.util.Direction;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.common.capabilities.CapabilityInject;
import net.minecraftforge.common.capabilities.CapabilityManager;
import net.minecraftforge.common.capabilities.ICapabilitySerializable;
import net.minecraftforge.common.util.INBTSerializable;
import net.minecraftforge.common.util.LazyOptional;
import net.minecraftforge.event.AttachCapabilitiesEvent;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.Set;

public class PointsOfInterest implements INBTSerializable<ListNBT>
{
    @Nonnull
    @CapabilityInject(PointsOfInterest.class)
    public static Capability<PointsOfInterest> INSTANCE = null; // assigned by forge

    public static void init()
    {
        CapabilityManager.INSTANCE.register(
                PointsOfInterest.class,
                new Capability.IStorage<PointsOfInterest>()
                {
                    @Nullable
                    @Override
                    public INBT writeNBT(Capability capability, PointsOfInterest instance, Direction side)
                    {
                        return instance.serializeNBT();
                    }

                    @Override
                    public void readNBT(Capability capability, PointsOfInterest instance, Direction side, INBT nbt)
                    {
                        if (!(nbt instanceof ListNBT))
                        {
                            HudCompass.LOGGER.error("Deserializing PointsOfInterest capability: stored nbt is not a List tag!");
                            return;
                        }
                        instance.deserializeNBT((ListNBT) nbt);
                    }
                }, PointsOfInterest::new
        );
        MinecraftForge.EVENT_BUS.addGenericListener(Entity.class, PointsOfInterest::attachEvent);
    }

    private static final ResourceLocation PROVIDER_KEY = HudCompass.location("poi_provider");
    private static void attachEvent(AttachCapabilitiesEvent<Entity> event)
    {
        Entity entity = event.getObject();
        if (entity instanceof PlayerEntity)
        {
            event.addCapability(PROVIDER_KEY, new ICapabilitySerializable<ListNBT>()
            {
                private final PointsOfInterest poi = new PointsOfInterest();
                private final LazyOptional<PointsOfInterest> poiSupplier = LazyOptional.of(() -> poi);

                @Override
                public ListNBT serializeNBT()
                {
                    return poi.serializeNBT();
                }

                @Override
                public void deserializeNBT(ListNBT nbt)
                {
                    poi.deserializeNBT(nbt);
                }

                @Nonnull
                @Override
                public <T> LazyOptional<T> getCapability(@Nonnull Capability<T> cap, @Nullable Direction side)
                {
                    if (cap == INSTANCE)
                        return poiSupplier.cast();
                    return LazyOptional.empty();
                }
            });
        }
    }

    private Set<PointInfo<?>> points = Sets.newHashSet();

    {
        points.add(new SpawnPointInfo());
    }

    public Set<PointInfo<?>> getPoints()
    {
        return points;
    }

    public void addPoint(PointInfo<?> pointInfo)
    {
        points.add(pointInfo);
    }

    public static void onTick(PlayerEntity player)
    {
        player.getCapability(PointsOfInterest.INSTANCE).ifPresent((pois) -> pois.tick(player));
    }

    private void tick(PlayerEntity player)
    {
        points.forEach(point -> point.tick(player));
    }


    @Override
    public ListNBT serializeNBT()
    {
        ListNBT tag = new ListNBT();

        for(PointInfo<?> point : points)
        {
            tag.add(PointInfoRegistry.serializePoint(point));
        }

        return tag;
    }

    @Override
    public void deserializeNBT(ListNBT nbt)
    {
        points.clear();
        for (int i = 0; i < nbt.size(); i++)
        {
            CompoundNBT pointTag = (CompoundNBT) nbt.get(i);
            points.add(PointInfoRegistry.deserializePoint(pointTag));
        }
    }
}
