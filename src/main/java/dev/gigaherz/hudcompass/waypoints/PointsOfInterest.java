package dev.gigaherz.hudcompass.waypoints;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.google.common.collect.Sets;
import com.mojang.datafixers.util.Pair;
import dev.gigaherz.hudcompass.ConfigData;
import dev.gigaherz.hudcompass.HudCompass;
import dev.gigaherz.hudcompass.icons.BasicIconData;
import dev.gigaherz.hudcompass.network.AddWaypoint;
import dev.gigaherz.hudcompass.network.RemoveWaypoint;
import dev.gigaherz.hudcompass.network.SyncWaypointData;
import dev.gigaherz.hudcompass.network.UpdateWaypointsFromGui;
import io.netty.buffer.Unpooled;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.player.ServerPlayerEntity;
import net.minecraft.nbt.CompoundNBT;
import net.minecraft.nbt.INBT;
import net.minecraft.nbt.ListNBT;
import net.minecraft.network.PacketBuffer;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.Direction;
import net.minecraft.util.RegistryKey;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.math.vector.Vector3d;
import net.minecraft.util.registry.Registry;
import net.minecraft.world.DimensionType;
import net.minecraft.world.World;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.common.capabilities.CapabilityInject;
import net.minecraftforge.common.capabilities.CapabilityManager;
import net.minecraftforge.common.capabilities.ICapabilitySerializable;
import net.minecraftforge.common.util.Constants;
import net.minecraftforge.common.util.LazyOptional;
import net.minecraftforge.event.AttachCapabilitiesEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.fml.network.PacketDistributor;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.*;
import java.util.function.Supplier;
import java.util.stream.Stream;

public class PointsOfInterest
{
    @Nonnull
    @CapabilityInject(PointsOfInterest.class)
    public static Capability<PointsOfInterest> INSTANCE = null; // assigned by forge
    private PointInfo<?> targetted;

    public int changeNumber;
    public int savedNumber;

    private final Map<ResourceLocation, Object> addonData = Maps.newHashMap();
    private List<Runnable> listeners = Lists.newArrayList();

    @SuppressWarnings("unchecked")
    public <T> T getOrCreateAddonData(ResourceLocation addonId, Supplier<T> factory)
    {
        return (T) addonData.computeIfAbsent(addonId, key -> factory.get());
    }

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
                        return instance.write();
                    }

                    @Override
                    public void readNBT(Capability capability, PointsOfInterest instance, Direction side, INBT nbt)
                    {
                        if (!(nbt instanceof ListNBT))
                        {
                            HudCompass.LOGGER.error("Deserializing PointsOfInterest capability: stored nbt is not a List tag!");
                            return;
                        }
                        instance.read((ListNBT) nbt);
                    }
                }, PointsOfInterest::new
        );

        MinecraftForge.EVENT_BUS.addGenericListener(Entity.class, PointsOfInterest::attachEvent);
        MinecraftForge.EVENT_BUS.addListener(PointsOfInterest::playerClone);
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
                    return poi.write();
                }

                @Override
                public void deserializeNBT(ListNBT nbt)
                {
                    poi.read(nbt);
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

    private static void playerClone(PlayerEvent.Clone event)
    {
        PlayerEntity oldPlayer = event.getOriginal();

        // FIXME: workaround for a forge issue that seems to be reappearing too often
        // at this time it's only needed when returning from the end alive
        oldPlayer.revive();

        PlayerEntity newPlayer = event.getPlayer();
        newPlayer.getCapability(INSTANCE).ifPresent(newPois -> {
            oldPlayer.getCapability(INSTANCE).ifPresent(oldPois -> {
                newPois.transferFrom(oldPois);
            });
        });
    }

    private void transferFrom(PointsOfInterest oldPois)
    {
        for(WorldPoints w : oldPois.getAllWorlds())
        {
            get(w.worldKey, w.dimensionTypeKey).transferFrom(w);
        }
    }

    public static void onTick(PlayerEntity player)
    {
        player.getCapability(PointsOfInterest.INSTANCE).ifPresent(PointsOfInterest::tick);
    }

    public Collection<WorldPoints> getAllWorlds()
    {
        return Collections.unmodifiableCollection(perWorld.values());
    }

    private final Set<PointInfo<?>> changed = Sets.newHashSet();
    private final Set<PointInfo<?>> removed = Sets.newHashSet();

    private final Map<RegistryKey<World>, WorldPoints> perWorld = Maps.newHashMap();

    private PlayerEntity player;

    public boolean otherSideHasMod = false;

    public PointsOfInterest()
    {
        //SpawnPointInfo spawn = new SpawnPointInfo(player);

        //get(spawn.getDimension())
        //points.put(spawn.getInternalId(), spawn);
    }

    public ListNBT write()
    {
        ListNBT list = new ListNBT();

        for (Map.Entry<RegistryKey<World>, WorldPoints> entry : perWorld.entrySet())
        {
            CompoundNBT tag = new CompoundNBT();
            tag.putString("World", entry.getKey().location().toString());
            if (entry.getValue().getDimensionTypeKey() != null)
                tag.putString("DimensionKey", entry.getValue().getDimensionTypeKey().location().toString());
            tag.put("POIs", entry.getValue().write());
            list.add(tag);
        }

        return list;
    }

    public void write(PacketBuffer buffer)
    {
        buffer.writeVarInt(perWorld.size());
        for (Map.Entry<RegistryKey<World>, WorldPoints> entry : perWorld.entrySet())
        {
            RegistryKey<World> key = entry.getKey();
            WorldPoints value = entry.getValue();

            buffer.writeResourceLocation(key.location());
            if (value.getDimensionTypeKey() != null)
            {
                buffer.writeBoolean(true);
                buffer.writeResourceLocation(value.getDimensionTypeKey().location());
            }
            else
            {
                buffer.writeBoolean(false);
            }
            value.write(buffer);
        }
    }

    public void read(ListNBT nbt)
    {
        perWorld.clear();
        for (int i = 0; i < nbt.size(); i++)
        {
            CompoundNBT tag = nbt.getCompound(i);
            RegistryKey<World> key = RegistryKey.create(Registry.DIMENSION_REGISTRY, new ResourceLocation(tag.getString("World")));
            RegistryKey<DimensionType> dimType = null;
            if (tag.contains("DimensionKey", Constants.NBT.TAG_STRING))
                dimType = RegistryKey.create(Registry.DIMENSION_TYPE_REGISTRY, new ResourceLocation(tag.getString("DimensionKey")));
            WorldPoints p = get(key, dimType);
            p.read(tag.getList("POIs", Constants.NBT.TAG_COMPOUND));
        }
        savedNumber = changeNumber = 0;
    }

    public void read(PacketBuffer buffer)
    {
        perWorld.values().forEach(pt -> pt.points.values().removeIf(PointInfo::isServerManaged));
        int numWorlds = buffer.readVarInt();
        for (int i = 0; i < numWorlds; i++)
        {
            RegistryKey<World> key = RegistryKey.create(Registry.DIMENSION_REGISTRY, buffer.readResourceLocation());
            boolean hasDimensionType = buffer.readBoolean();
            RegistryKey<DimensionType> dimType = hasDimensionType
                    ? RegistryKey.create(Registry.DIMENSION_TYPE_REGISTRY, buffer.readResourceLocation())
                    : null;
            WorldPoints p = get(key, dimType);
            p.read(buffer);
        }
        savedNumber = changeNumber = 0;
    }

    public void clear()
    {
        perWorld.values().forEach(WorldPoints::clear);
        perWorld.clear();
    }

    public void setTargetted(PointInfo<?> targetted)
    {
        this.targetted = targetted;
    }

    public PointInfo<?> getTargetted()
    {
        return targetted;
    }

    public void setPlayer(PlayerEntity player)
    {
        this.player = player;
    }

    public void tick()
    {
        perWorld.values().forEach(WorldPoints::tick);
    }

    private void sendInitialSync()
    {
        sendSync();
    }

    private void sendSync()
    {
        if (ConfigData.COMMON.disableServerHello.get())
            return;

        if (otherSideHasMod)
        {
            HudCompass.channel.send(PacketDistributor.PLAYER.with(() -> (ServerPlayerEntity) player),
                    new SyncWaypointData(this)
            );
        }
    }

    private void sendUpdateFromGui(
            ImmutableList<Pair<ResourceLocation, PointInfo<?>>> toAdd,
            ImmutableList<Pair<ResourceLocation, PointInfo<?>>> toUpdate,
            ImmutableList<UUID> toRemove)
    {

        HudCompass.channel.sendToServer(
                new UpdateWaypointsFromGui(toAdd, toUpdate, toRemove)
        );
    }

    public static void handleAddWaypoint(ServerPlayerEntity sender, AddWaypoint addWaypoint)
    {
        sender.getCapability(INSTANCE).ifPresent(points -> {
            BasicWaypoint waypoint = new BasicWaypoint(new Vector3d(addWaypoint.x, addWaypoint.y, addWaypoint.z), addWaypoint.label,
                    addWaypoint.isMarker
                            ? BasicIconData.mapMarker(addWaypoint.iconIndex)
                            : BasicIconData.poi(addWaypoint.iconIndex)
            );
            points.get(sender.level).addPoint(waypoint);
        });
    }

    public void updateFromGui(
            ImmutableList<Pair<ResourceLocation, PointInfo<?>>> toAdd,
            ImmutableList<Pair<ResourceLocation, PointInfo<?>>> toUpdate,
            ImmutableList<UUID> toRemove)
    {
        if (player.level.isClientSide && otherSideHasMod)
        {
            sendUpdateFromGui(toAdd, toUpdate, toRemove);
        }
        else {
            applyUpdatesFromGui(toAdd, toUpdate, toRemove);
        }
    }

    public WorldPoints get(World world)
    {
        return getInternal(world.dimension(), () -> getDimensionTypeKey(world, null));
    }

    public WorldPoints get(RegistryKey<World> worldKey)
    {
        return get(worldKey, null);
    }

    public WorldPoints get(RegistryKey<World> worldKey, @Nullable RegistryKey<DimensionType> dimensionTypeKey)
    {
        return getInternal(worldKey, () -> {
            if (player.level.dimension() == worldKey)
                return getDimensionTypeKey(player.level, dimensionTypeKey);

            MinecraftServer server = player.level.getServer();
            if (server == null)
                return dimensionTypeKey;

            World world = server.getLevel(worldKey);
            if (world == null)
                return dimensionTypeKey;

            return getDimensionTypeKey(world, dimensionTypeKey);
        });
    }

    @Nullable
    private static RegistryKey<DimensionType> getDimensionTypeKey(World world, @Nullable RegistryKey<DimensionType> fallback)
    {
        DimensionType dimType = world.dimensionType();
        ResourceLocation key = world.registryAccess().dimensionTypes().getKey(dimType);
        if (key == null)
            return fallback;
        return RegistryKey.create(Registry.DIMENSION_TYPE_REGISTRY, key);
    }

    private WorldPoints getInternal(RegistryKey<World> worldKey, Supplier<RegistryKey<DimensionType>> dimensionTypeKey)
    {
        return perWorld.computeIfAbsent(Objects.requireNonNull(worldKey), worldKey1 -> new WorldPoints(worldKey1, dimensionTypeKey.get()));
    }

    public static void handleRemoveWaypoint(ServerPlayerEntity sender, RemoveWaypoint removeWaypoint)
    {
        sender.getCapability(INSTANCE).ifPresent(points -> points
                .find(removeWaypoint.id)
                .ifPresent(pt -> {
            if (!pt.isDynamic())
            {
                WorldPoints owner = pt.getOwner();
                if (owner != null)
                    owner.removePoint(removeWaypoint.id);
            }
        }));
    }

    private Optional<PointInfo<?>> find(UUID id)
    {
        return perWorld.values().stream().<PointInfo<?>>flatMap(world -> world.find(id).map(Stream::of).orElseGet(Stream::empty)).findAny();
    }

    private void remove(UUID pt)
    {
        getAllWorlds().forEach(w -> w.removePoint(pt));
    }

    public static void handleSync(PlayerEntity player, byte[] packet)
    {
        player.getCapability(PointsOfInterest.INSTANCE).ifPresent(points -> {
            points.read(new PacketBuffer(Unpooled.wrappedBuffer(packet)));
        });
    }

    public static void handleUpdateFromGui(ServerPlayerEntity sender, UpdateWaypointsFromGui packet)
    {
        sender.getCapability(INSTANCE).ifPresent(points -> {
            ImmutableList<Pair<ResourceLocation, PointInfo<?>>> pointsAdded = packet.pointsAdded;
            ImmutableList<Pair<ResourceLocation, PointInfo<?>>> pointsUpdated = packet.pointsUpdated;
            ImmutableList<UUID> pointsRemoved = packet.pointsRemoved;
            points.applyUpdatesFromGui(pointsAdded, pointsUpdated, pointsRemoved);
        });
    }

    private void applyUpdatesFromGui(ImmutableList<Pair<ResourceLocation, PointInfo<?>>> pointsAdded, ImmutableList<Pair<ResourceLocation, PointInfo<?>>> pointsUpdated, ImmutableList<UUID> pointsRemoved)
    {
        for(UUID pt : pointsRemoved)
        {
            remove(pt);
        }
        for(Pair<ResourceLocation, PointInfo<?>> pt : pointsAdded)
        {
            get(RegistryKey.create(Registry.DIMENSION_REGISTRY, pt.getFirst())).addPoint(pt.getSecond());
        }
        for(Pair<ResourceLocation, PointInfo<?>> pt : pointsUpdated)
        {
            get(RegistryKey.create(Registry.DIMENSION_REGISTRY, pt.getFirst())).addPoint(pt.getSecond());
        }
    }

    public static void remoteHello(@Nullable PlayerEntity player)
    {
        if (player == null) return;
        player.getCapability(INSTANCE).ifPresent(points -> {
            points.otherSideHasMod = true;
            if (!player.level.isClientSide)
                points.sendInitialSync();
        });
    }

    public void addListener(Runnable onSyncReceived)
    {
        listeners.add(onSyncReceived);
    }

    public void removeListener(Runnable onSyncReceived)
    {
        listeners.remove(onSyncReceived);
    }

    public class WorldPoints
    {
        private final RegistryKey<World> worldKey;
        @Nullable
        private final RegistryKey<DimensionType> dimensionTypeKey;
        private Map<UUID, PointInfo<?>> points = Maps.newHashMap();

        public WorldPoints(RegistryKey<World> worldKey, @Nullable RegistryKey<DimensionType> dimensionTypeKey)
        {
            this.worldKey = worldKey;
            this.dimensionTypeKey = dimensionTypeKey;
        }

        public Collection<PointInfo<?>> getPoints()
        {
            return points.values();
        }

        private void tick()
        {
            for (PointInfo<?> point : points.values())
            {
                point.tick(player);
            }

            if (player.level.isClientSide && player.level.dimension() == worldKey)
            {
                PointInfo<?> closest = null;
                double closestAngle = Double.POSITIVE_INFINITY;
                for (PointInfo<?> point : points.values())
                {
                    Vector3d direction = point.getPosition(player, 1.0f).subtract(player.position());
                    Vector3d look = player.getLookAngle();
                    direction = direction.normalize();
                    look = look.normalize();
                    double dot = direction.x * look.x + direction.z * look.z;
                    double m1 = Math.sqrt(direction.x * direction.x + direction.z * direction.z);
                    double m2 = Math.sqrt(look.x * look.x + look.z * look.z);
                    double angle = Math.abs(Math.acos(dot / (m1 * m2)));
                    if (angle < closestAngle)
                    {
                        closest = point;
                        closestAngle = angle;
                    }
                }

                if (closest != null && closestAngle < Math.toRadians(15))
                {
                    setTargetted(closest);
                }
                else
                {
                    setTargetted(null);
                }
            }

            if (!player.level.isClientSide && (changed.size() > 0 || removed.size() > 0))
            {
                sendSync();
                changed.clear();
                removed.clear();
            }
        }

        public void addPointRequest(PointInfo<?> point)
        {
            if (otherSideHasMod && player.level.isClientSide && point instanceof BasicWaypoint)
            {
                HudCompass.channel.sendToServer(new AddWaypoint((BasicWaypoint) point));
            }
            else
            {
                addPoint(point);
            }
        }

        public void addPoint(PointInfo<?> point)
        {
            point.setOwner(this);
            PointInfo<?> oldPoint = points.put(point.getInternalId(), point);
            if (oldPoint != null) {
                oldPoint.setOwner(null);
            }
            if (!player.level.isClientSide && otherSideHasMod)
            {
                changed.add(point);
            }
            if (!point.isDynamic())
                changeNumber++;
        }

        public void removePointRequest(PointInfo<?> point)
        {
            UUID id = point.getInternalId();
            if (otherSideHasMod && player.level.isClientSide)
            {
                HudCompass.channel.sendToServer(new RemoveWaypoint(id));
            }
            else
            {
                removePoint(id);
            }
        }

        public void removePoint(PointInfo<?> point)
        {
            removePoint(point.getInternalId());
        }

        public void removePoint(UUID id)
        {
            PointInfo<?> point = points.get(id);
            if (point != null)
            {
                point.setOwner(null);
                points.remove(point.getInternalId());
                if (!player.level.isClientSide && otherSideHasMod)
                {
                    removed.add(point);
                }
                if (!point.isDynamic())
                    changeNumber++;
            }
        }

        public void clear()
        {
            boolean nonDynamic = points.values().stream().anyMatch(point -> !point.isDynamic());
            removed.addAll(points.values());
            points.clear();
            if (nonDynamic)
                changeNumber++;
        }

        public void markDirty(PointInfo<?> point)
        {
            if (!player.level.isClientSide && otherSideHasMod)
            {
                changed.add(point);
            }
            if (!point.isDynamic())
                changeNumber++;
        }

        public ListNBT write()
        {
            ListNBT tag = new ListNBT();

            for (PointInfo<?> point : points.values())
            {
                if (!point.isDynamic())
                    tag.add(PointInfoRegistry.serializePoint(point));
            }

            return tag;
        }

        public void write(PacketBuffer buffer)
        {
            buffer.writeVarInt(points.size());
            for (PointInfo<?> point : points.values())
            {
                PointInfoRegistry.serializePoint(point, buffer);
            }
        }

        public void read(ListNBT nbt)
        {
            points.clear();
            for (int i = 0; i < nbt.size(); i++)
            {
                CompoundNBT pointTag = nbt.getCompound(i);
                PointInfo<?> point = PointInfoRegistry.deserializePoint(pointTag);
                points.put(point.getInternalId(), point);
            }
        }

        public void read(PacketBuffer buffer)
        {
            // IT BROKE WHEN IT WAS A METHOD REFERENCE
            points.values().removeIf(pointInfo -> pointInfo.isServerManaged());
            int numPoints = buffer.readVarInt();
            for (int i = 0; i < numPoints; i++)
            {
                PointInfo<?> point = PointInfoRegistry.deserializePoint(buffer);
                points.put(point.getInternalId(), point);
            }
        }

        public RegistryKey<World> getWorldKey()
        {
            return worldKey;
        }

        @Nullable
        public RegistryKey<DimensionType> getDimensionTypeKey()
        {
            return dimensionTypeKey;
        }

        public Optional<PointInfo<?>> find(UUID id)
        {
            return Optional.ofNullable(points.get(id));
        }

        public void transferFrom(WorldPoints w)
        {
            for(PointInfo<?> p : w.getPoints())
            {
                addPoint(p);
            }
        }
    }

}
