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
import net.minecraft.core.Direction;
import net.minecraft.core.Registry;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.dimension.DimensionType;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.common.capabilities.*;
import net.minecraftforge.common.util.LazyOptional;
import net.minecraftforge.event.AttachCapabilitiesEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.network.PacketDistributor;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.*;
import java.util.function.Supplier;

public class PointsOfInterest
{
    public static Capability<PointsOfInterest> INSTANCE = CapabilityManager.get(new CapabilityToken<>()
    {
    });
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

    public static void init(RegisterCapabilitiesEvent event)
    {
        MinecraftForge.EVENT_BUS.addGenericListener(Entity.class, PointsOfInterest::attachEvent);
        MinecraftForge.EVENT_BUS.addListener(PointsOfInterest::playerClone);

        event.register(PointsOfInterest.class);
    }

    private static final ResourceLocation PROVIDER_KEY = HudCompass.location("poi_provider");

    private static void attachEvent(AttachCapabilitiesEvent<Entity> event)
    {
        Entity entity = event.getObject();
        if (entity instanceof Player)
        {
            event.addCapability(PROVIDER_KEY, new ICapabilitySerializable<ListTag>()
            {
                private final PointsOfInterest poi = new PointsOfInterest();
                private final LazyOptional<PointsOfInterest> poiSupplier = LazyOptional.of(() -> poi);

                {
                    poi.setPlayer((Player) entity);
                }

                @Override
                public ListTag serializeNBT()
                {
                    return poi.write();
                }

                @Override
                public void deserializeNBT(ListTag nbt)
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
        Player oldPlayer = event.getOriginal();

        // FIXME: workaround for a forge issue that seems to be reappearing too often
        // at this time it's only needed when returning from the end alive
        oldPlayer.revive();

        Player newPlayer = event.getPlayer();
        newPlayer.getCapability(INSTANCE).ifPresent(newPois -> {
            oldPlayer.getCapability(INSTANCE).ifPresent(oldPois -> {
                newPois.transferFrom(oldPois);
            });
        });
    }

    private void transferFrom(PointsOfInterest oldPois)
    {
        for (WorldPoints w : oldPois.getAllWorlds())
        {
            get(w.worldKey, w.dimensionTypeKey).transferFrom(w);
        }
    }

    public static void onTick(Player player)
    {
        player.getCapability(PointsOfInterest.INSTANCE).ifPresent(PointsOfInterest::tick);
    }

    public Collection<WorldPoints> getAllWorlds()
    {
        return Collections.unmodifiableCollection(perWorld.values());
    }

    private final Set<PointInfo<?>> changed = Sets.newHashSet();
    private final Set<PointInfo<?>> removed = Sets.newHashSet();

    private final Map<ResourceKey<Level>, WorldPoints> perWorld = Maps.newHashMap();

    private Player player;

    public boolean otherSideHasMod = false;

    public PointsOfInterest()
    {
        //SpawnPointInfo spawn = new SpawnPointInfo(player);

        //get(spawn.getDimension())
        //points.put(spawn.getInternalId(), spawn);
    }

    public ListTag write()
    {
        ListTag list = new ListTag();

        for (Map.Entry<ResourceKey<Level>, WorldPoints> entry : perWorld.entrySet())
        {
            CompoundTag tag = new CompoundTag();
            tag.putString("World", entry.getKey().location().toString());
            if (entry.getValue().getDimensionTypeKey() != null)
                tag.putString("DimensionKey", entry.getValue().getDimensionTypeKey().location().toString());
            tag.put("POIs", entry.getValue().write());
            list.add(tag);
        }

        return list;
    }

    public void write(FriendlyByteBuf buffer)
    {
        buffer.writeVarInt(perWorld.size());
        for (Map.Entry<ResourceKey<Level>, WorldPoints> entry : perWorld.entrySet())
        {
            ResourceKey<Level> key = entry.getKey();
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

    public void read(ListTag nbt)
    {
        perWorld.clear();
        for (int i = 0; i < nbt.size(); i++)
        {
            CompoundTag tag = nbt.getCompound(i);
            ResourceKey<Level> key = ResourceKey.create(Registry.DIMENSION_REGISTRY, new ResourceLocation(tag.getString("World")));
            ResourceKey<DimensionType> dimType = null;
            if (tag.contains("DimensionKey", Tag.TAG_STRING))
                dimType = ResourceKey.create(Registry.DIMENSION_TYPE_REGISTRY, new ResourceLocation(tag.getString("DimensionKey")));
            WorldPoints p = get(key, dimType);
            p.read(tag.getList("POIs", Tag.TAG_COMPOUND));
        }
        savedNumber = changeNumber = 0;
    }

    public void read(FriendlyByteBuf buffer)
    {
        perWorld.values().forEach(pt -> pt.points.values().removeIf(PointInfo::isServerManaged));
        int numWorlds = buffer.readVarInt();
        for (int i = 0; i < numWorlds; i++)
        {
            ResourceKey<Level> key = ResourceKey.create(Registry.DIMENSION_REGISTRY, buffer.readResourceLocation());
            boolean hasDimensionType = buffer.readBoolean();
            ResourceKey<DimensionType> dimType = hasDimensionType
                    ? ResourceKey.create(Registry.DIMENSION_TYPE_REGISTRY, buffer.readResourceLocation())
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

    public void setPlayer(Player player)
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
            HudCompass.channel.send(PacketDistributor.PLAYER.with(() -> (ServerPlayer) player),
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

    public static void handleAddWaypoint(ServerPlayer sender, AddWaypoint addWaypoint)
    {
        sender.getCapability(INSTANCE).ifPresent(points -> {
            BasicWaypoint waypoint = new BasicWaypoint(new Vec3(addWaypoint.x, addWaypoint.y, addWaypoint.z), addWaypoint.label,
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
        else
        {
            applyUpdatesFromGui(toAdd, toUpdate, toRemove);
        }
    }

    public WorldPoints get(Level world)
    {
        return getInternal(world.dimension(), () -> getDimensionTypeKey(world, null));
    }

    public WorldPoints get(ResourceKey<Level> worldKey)
    {
        return get(worldKey, null);
    }

    public WorldPoints get(ResourceKey<Level> worldKey, @Nullable ResourceKey<DimensionType> dimensionTypeKey)
    {
        return getInternal(worldKey, () -> {
            if (player.level.dimension() == worldKey)
                return getDimensionTypeKey(player.level, dimensionTypeKey);

            MinecraftServer server = player.level.getServer();
            if (server == null)
                return dimensionTypeKey;

            Level world = server.getLevel(worldKey);
            if (world == null)
                return dimensionTypeKey;

            return getDimensionTypeKey(world, dimensionTypeKey);
        });
    }

    @Nullable
    private static ResourceKey<DimensionType> getDimensionTypeKey(Level world, @Nullable ResourceKey<DimensionType> fallback)
    {
        DimensionType dimType = world.dimensionType();
        ResourceLocation key = world.registryAccess().registryOrThrow(Registry.DIMENSION_TYPE_REGISTRY).getKey(dimType);
        if (key == null)
            return fallback;
        return ResourceKey.create(Registry.DIMENSION_TYPE_REGISTRY, key);
    }

    private WorldPoints getInternal(ResourceKey<Level> worldKey, Supplier<ResourceKey<DimensionType>> dimensionTypeKey)
    {
        return perWorld.computeIfAbsent(Objects.requireNonNull(worldKey), worldKey1 -> new WorldPoints(worldKey1, dimensionTypeKey.get()));
    }

    public static void handleRemoveWaypoint(ServerPlayer sender, RemoveWaypoint removeWaypoint)
    {
        sender.getCapability(INSTANCE).ifPresent(points -> points
                .find(removeWaypoint.id)
                .ifPresent(pt -> {
                    if (!pt.isDynamic())
                    {
                        var owner = pt.getOwner();
                        if (owner != null)
                            owner.removePoint(removeWaypoint.id);
                    }
                }));
    }

    private Optional<PointInfo<?>> find(UUID id)
    {
        return perWorld.values().stream().flatMap(world -> world.find(id).stream()).findAny();
    }

    private void remove(UUID pt)
    {
        getAllWorlds().forEach(w -> w.removePoint(pt));
    }

    public static void handleSync(Player player, byte[] packet)
    {
        player.getCapability(PointsOfInterest.INSTANCE).ifPresent(points -> {
            points.read(new FriendlyByteBuf(Unpooled.wrappedBuffer(packet)));
        });
    }

    public static void handleUpdateFromGui(ServerPlayer sender, UpdateWaypointsFromGui packet)
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
        for (UUID pt : pointsRemoved)
        {
            remove(pt);
        }
        for (Pair<ResourceLocation, PointInfo<?>> pt : pointsAdded)
        {
            get(ResourceKey.create(Registry.DIMENSION_REGISTRY, pt.getFirst())).addPoint(pt.getSecond());
        }
        for (Pair<ResourceLocation, PointInfo<?>> pt : pointsUpdated)
        {
            get(ResourceKey.create(Registry.DIMENSION_REGISTRY, pt.getFirst())).addPoint(pt.getSecond());
        }
    }

    public static void remoteHello(@Nullable Player player)
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
        private final ResourceKey<Level> worldKey;
        @Nullable
        private final ResourceKey<DimensionType> dimensionTypeKey;
        private Map<UUID, PointInfo<?>> points = Maps.newHashMap();

        public WorldPoints(ResourceKey<Level> worldKey, @Nullable ResourceKey<DimensionType> dimensionTypeKey)
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
                    Vec3 direction = point.getPosition(player, 1.0f).subtract(player.position());
                    Vec3 look = player.getLookAngle();
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
            if (oldPoint != null)
            {
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

        public ListTag write()
        {
            ListTag tag = new ListTag();

            for (PointInfo<?> point : points.values())
            {
                if (!point.isDynamic())
                    tag.add(PointInfoRegistry.serializePoint(point));
            }

            return tag;
        }

        public void write(FriendlyByteBuf buffer)
        {
            buffer.writeVarInt(points.size());
            for (PointInfo<?> point : points.values())
            {
                PointInfoRegistry.serializePoint(point, buffer);
            }
        }

        public void read(ListTag nbt)
        {
            points.clear();
            for (int i = 0; i < nbt.size(); i++)
            {
                CompoundTag pointTag = nbt.getCompound(i);
                PointInfo<?> point = PointInfoRegistry.deserializePoint(pointTag);
                points.put(point.getInternalId(), point);
            }
        }

        public void read(FriendlyByteBuf buffer)
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

        public ResourceKey<Level> getWorldKey()
        {
            return worldKey;
        }

        @Nullable
        public ResourceKey<DimensionType> getDimensionTypeKey()
        {
            return dimensionTypeKey;
        }

        public Optional<PointInfo<?>> find(UUID id)
        {
            return Optional.ofNullable(points.get(id));
        }

        public void transferFrom(WorldPoints w)
        {
            for (PointInfo<?> p : w.getPoints())
            {
                addPoint(p);
            }
        }
    }
}
