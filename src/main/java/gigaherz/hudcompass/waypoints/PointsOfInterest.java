package gigaherz.hudcompass.waypoints;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.Maps;
import com.google.common.collect.Sets;
import gigaherz.hudcompass.HudCompass;
import gigaherz.hudcompass.icons.BasicIconData;
import gigaherz.hudcompass.network.AddWaypoint;
import gigaherz.hudcompass.network.SyncWaypointData;
import net.minecraft.client.entity.player.ClientPlayerEntity;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.player.ServerPlayerEntity;
import net.minecraft.nbt.CompoundNBT;
import net.minecraft.nbt.INBT;
import net.minecraft.nbt.ListNBT;
import net.minecraft.util.Direction;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.math.Vec3d;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.common.capabilities.CapabilityInject;
import net.minecraftforge.common.capabilities.CapabilityManager;
import net.minecraftforge.common.capabilities.ICapabilitySerializable;
import net.minecraftforge.common.util.INBTSerializable;
import net.minecraftforge.common.util.LazyOptional;
import net.minecraftforge.event.AttachCapabilitiesEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.fml.network.PacketDistributor;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.*;

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

                {
                    poi.setPlayer((PlayerEntity)entity);
                }

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

    private PlayerEntity player;

    private Set<PointInfo<?>> changed = Sets.newHashSet();
    private Set<PointInfo<?>> removed = Sets.newHashSet();

    private Map<UUID, PointInfo<?>> points = Maps.newHashMap();

    public boolean otherSideHasMod = false;

    {
        SpawnPointInfo spawn = new SpawnPointInfo();

        points.put(spawn.getInternalId(), spawn);
    }

    public Collection<PointInfo<?>> getPoints()
    {
        return points.values();
    }

    public void setPlayer(PlayerEntity player) {
        this.player = player;
    }

    public static void onTick(PlayerEntity player)
    {
        player.getCapability(PointsOfInterest.INSTANCE).ifPresent((pois) -> pois.tick());
    }

    private void tick()
    {
        points.values().forEach(point -> point.tick(player));

        if (!player.world.isRemote && (changed.size() > 0 || removed.size() > 0))
        {
            if (otherSideHasMod)
            {
                HudCompass.channel.send(PacketDistributor.PLAYER.with(() -> (ServerPlayerEntity) player),
                        new SyncWaypointData(false, ImmutableList.copyOf(points.values()), ImmutableList.of()));
            }
            changed.clear();
            removed.clear();
        }
    }

    public void addPoint(PointInfo<?> point)
    {
        point.setOwner(this);
        points.put(point.getInternalId(), point);
        if (!player.world.isRemote && otherSideHasMod)
            changed.add(point);
    }

    public void remove(PointInfo<?> point)
    {
        point.setOwner(null);
        points.remove(point.getInternalId());
        if (!player.world.isRemote && otherSideHasMod)
            removed.add(point);
    }

    public void markDirty(PointInfo<?> point)
    {
        if (!player.world.isRemote && otherSideHasMod)
            changed.add(point);
    }

    @Override
    public ListNBT serializeNBT()
    {
        ListNBT tag = new ListNBT();

        for(PointInfo<?> point : points.values())
        {
            if (!point.isDynamic())
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
            CompoundNBT pointTag = nbt.getCompound(i);
            PointInfo<?> point = PointInfoRegistry.deserializePoint(pointTag);
            points.put(point.getInternalId(), point);
        }
    }

    private void sendInitialSync()
    {
        if (otherSideHasMod)
        {
            HudCompass.channel.send(PacketDistributor.PLAYER.with(() -> (ServerPlayerEntity) player),
                    new SyncWaypointData(true, ImmutableList.copyOf(points.values()), ImmutableList.of()));
        }
    }

    public static void handleAddWaypoint(ServerPlayerEntity sender, AddWaypoint addWaypoint)
    {
        sender.getCapability(INSTANCE).ifPresent(points -> {
            BasicWaypoint waypoint = new BasicWaypoint(new Vec3d(addWaypoint.x, addWaypoint.y, addWaypoint.z), addWaypoint.label,
                    addWaypoint.isMarker
                            ? BasicIconData.mapMarker(addWaypoint.iconIndex)
                            : BasicIconData.poi(addWaypoint.iconIndex)
            );
            points.addPoint(waypoint);
        });
    }

    public static void handleSync(PlayerEntity player, SyncWaypointData packet)
    {
        player.getCapability(PointsOfInterest.INSTANCE).ifPresent(points -> {
            if (packet.replaceAll)
            {
                points.points.entrySet().removeIf(kv -> kv.getValue().isServerManaged());
            }
            else
            {
                points.points.entrySet().removeIf(kv -> packet.pointsRemoved.contains(kv.getValue().getInternalId()));
            }
            for(PointInfo<?> pt : packet.pointsAddedOrUpdated)
            {
                points.addPoint(pt);
            }
        });
    }

    public static void remoteHello(@Nullable PlayerEntity player)
    {
        if (player == null) return;
        player.getCapability(INSTANCE).ifPresent(points -> {
            points.otherSideHasMod = true;
            if (!player.world.isRemote)
                points.sendInitialSync();
        });
    }
}
