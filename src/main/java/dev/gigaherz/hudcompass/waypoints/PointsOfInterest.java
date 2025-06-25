package dev.gigaherz.hudcompass.waypoints;

import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.google.common.collect.Sets;
import dev.gigaherz.hudcompass.ConfigData;
import dev.gigaherz.hudcompass.HudCompass;
import dev.gigaherz.hudcompass.icons.BasicIconData;
import dev.gigaherz.hudcompass.network.AddWaypoint;
import dev.gigaherz.hudcompass.network.RemoveWaypoint;
import dev.gigaherz.hudcompass.network.SyncWaypointData;
import dev.gigaherz.hudcompass.network.UpdateWaypointsFromGui;
import gigaherz.hudcompass.waypoints.PointAddRemoveEntry;
import io.netty.buffer.Unpooled;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.ExtraCodecs;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.dimension.DimensionType;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.attachment.IAttachmentHolder;
import net.neoforged.neoforge.common.util.ValueIOSerializable;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.connection.ConnectionType;
import org.jetbrains.annotations.Nullable;

import java.util.*;
import java.util.function.Supplier;

public class PointsOfInterest implements ValueIOSerializable
{
    private PointInfo<?> targetted;

    public int changeNumber;
    public int savedNumber;

    private final Map<ResourceLocation, Object> addonData = Maps.newHashMap();
    private final List<Runnable> listeners = Lists.newArrayList();

    @SuppressWarnings("unchecked")
    public <T> T getOrCreateAddonData(ResourceLocation addonId, Supplier<T> factory)
    {
        return (T) addonData.computeIfAbsent(addonId, key -> factory.get());
    }

    public static PointsOfInterest duplicate(PointsOfInterest oldPois, IAttachmentHolder holder, HolderLookup.Provider provider)
    {
        var newPois = new PointsOfInterest(holder);
        newPois.transferFrom(oldPois);
        return newPois;
    }

    public void transferFrom(PointsOfInterest oldPois)
    {
        for (WorldPoints w : oldPois.getAllWorlds())
        {
            get(w.worldKey, w.dimensionTypeKey).transferFrom(w);
        }
    }

    public static void onTick(Player player)
    {
        player.getData(HudCompass.POINTS_OF_INTEREST_ATTACHMENT).tick();
    }

    public Collection<WorldPoints> getAllWorlds()
    {
        return Collections.unmodifiableCollection(perWorld.values());
    }

    private final Set<PointInfo<?>> changed = Sets.newHashSet();
    private final Set<PointInfo<?>> removed = Sets.newHashSet();

    private final Map<ResourceKey<Level>, WorldPoints> perWorld = Maps.newHashMap();

    private final Player player;

    public boolean otherSideHasMod = false;

    public PointsOfInterest(IAttachmentHolder holder)
    {
        this.player = (Player)holder;
        //SpawnPointInfo spawn = new SpawnPointInfo(player);

        //get(spawn.getDimension())
        //points.put(spawn.getInternalId(), spawn);
    }

    @Override
    public void serialize(ValueOutput valueOutput)
    {
        serialize(valueOutput.childrenList("Worlds"));
    }

    @Override
    public void deserialize(ValueInput valueInput)
    {
        deserialize(valueInput.childrenListOrEmpty("Worlds"));
    }


    public void serialize(ValueOutput.ValueOutputList list)
    {
        for (Map.Entry<ResourceKey<Level>, WorldPoints> entry : perWorld.entrySet())
        {
            var element = list.addChild();
            element.putString("World", entry.getKey().location().toString());
            if (entry.getValue().getDimensionTypeKey() != null)
                element.putString("DimensionKey", entry.getValue().getDimensionTypeKey().location().toString());
            entry.getValue().write(element.childrenList("POIs"));
        }
    }

    public void deserialize(ValueInput.ValueInputList list)
    {
        perWorld.clear();
        for(var element : list)
        {
            ResourceKey<Level> key = ResourceKey.create(Registries.DIMENSION, ResourceLocation.parse(element.getString("World").orElseThrow()));
            ResourceKey<DimensionType> dimType = null;
            var dimKey = element.getString("DimensionKey");
            if (dimKey.isPresent())
                dimType = ResourceKey.create(Registries.DIMENSION_TYPE, ResourceLocation.parse(dimKey.get()));
            WorldPoints p = get(key, dimType);
            p.read(element.childrenList("POIs").orElseThrow());
        }
        savedNumber = changeNumber = 0;
    }

    public void write(RegistryFriendlyByteBuf buffer)
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

    public void read(RegistryFriendlyByteBuf buffer)
    {
        perWorld.values().forEach(pt -> pt.points.values().removeIf(PointInfo::isServerManaged));
        int numWorlds = buffer.readVarInt();
        for (int i = 0; i < numWorlds; i++)
        {
            ResourceKey<Level> key = ResourceKey.create(Registries.DIMENSION, buffer.readResourceLocation());
            boolean hasDimensionType = buffer.readBoolean();
            ResourceKey<DimensionType> dimType = hasDimensionType
                    ? ResourceKey.create(Registries.DIMENSION_TYPE, buffer.readResourceLocation())
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

    public void setTargetted(@Nullable PointInfo<?> targetted)
    {
        this.targetted = targetted;
    }

    public PointInfo<?> getTargetted()
    {
        return targetted;
    }

    public void tick()
    {
        perWorld.values().forEach(WorldPoints::tick);
    }

    private void sendInitialSync(RegistryAccess registryAccess)
    {
        sendSync(registryAccess);
    }

    private void sendSync(RegistryAccess registryAccess)
    {
        if (ConfigData.COMMON.disableServerHello.get())
            return;

        if (otherSideHasMod)
        {
            PacketDistributor.sendToPlayer((ServerPlayer) player, SyncWaypointData.of(this, registryAccess));
        }
    }

    private void sendUpdateFromGui(
            List<PointAddRemoveEntry> toAdd,
            List<PointAddRemoveEntry> toUpdate,
            List<UUID> toRemove)
    {

        PacketDistributor.sendToServer(new UpdateWaypointsFromGui(toAdd, toUpdate, toRemove));
    }

    public static void handleAddWaypoint(Player sender, AddWaypoint addWaypoint)
    {
        var points = sender.getData(HudCompass.POINTS_OF_INTEREST_ATTACHMENT);
        {
            BasicWaypoint waypoint = new BasicWaypoint(new Vec3(addWaypoint.x(), addWaypoint.y(), addWaypoint.z()), addWaypoint.label(),
                    BasicIconData.basic(addWaypoint.spriteName())
            );
            points.get(sender.level()).addPoint(waypoint);
        }
    }

    public void updateFromGui(
            List<PointAddRemoveEntry> toAdd,
            List<PointAddRemoveEntry> toUpdate,
            List<UUID> toRemove)
    {
        if (player.level().isClientSide && otherSideHasMod)
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
            if (player.level().dimension() == worldKey)
                return getDimensionTypeKey(player.level(), dimensionTypeKey);

            MinecraftServer server = player.level().getServer();
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
        ResourceLocation key = world.registryAccess().lookupOrThrow(Registries.DIMENSION_TYPE).getKey(dimType);
        if (key == null)
            return fallback;
        return ResourceKey.create(Registries.DIMENSION_TYPE, key);
    }

    private WorldPoints getInternal(ResourceKey<Level> worldKey, Supplier<ResourceKey<DimensionType>> dimensionTypeKey)
    {
        return perWorld.computeIfAbsent(Objects.requireNonNull(worldKey), worldKey1 -> new WorldPoints(worldKey1, dimensionTypeKey.get()));
    }

    public static void handleRemoveWaypoint(Player sender, RemoveWaypoint removeWaypoint)
    {
        var points = sender.getData(HudCompass.POINTS_OF_INTEREST_ATTACHMENT);
        points.find(removeWaypoint.id()).ifPresent(pt -> {
            if (!pt.isDynamic())
            {
                var owner = pt.getOwner();
                if (owner != null)
                    owner.removePoint(removeWaypoint.id());
            }
        });
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
        var points = player.getData(HudCompass.POINTS_OF_INTEREST_ATTACHMENT);
        points.read(new RegistryFriendlyByteBuf(Unpooled.wrappedBuffer(packet), player.registryAccess(), ConnectionType.NEOFORGE));
    }

    public static void handleUpdateFromGui(Player sender, UpdateWaypointsFromGui packet)
    {
        var points = sender.getData(HudCompass.POINTS_OF_INTEREST_ATTACHMENT);
        {
            points.applyUpdatesFromGui(packet.pointsAdded(), packet.pointsUpdated(), packet.pointsRemoved());
        }
    }

    private void applyUpdatesFromGui(
            List<PointAddRemoveEntry> pointsAdded,
            List<PointAddRemoveEntry> pointsUpdated,
            List<UUID> pointsRemoved)
    {
        for (UUID pt : pointsRemoved)
        {
            remove(pt);
        }
        for (var pt : pointsAdded)
        {
            get(ResourceKey.create(Registries.DIMENSION, pt.key())).addPoint(pt.point());
        }
        for (var pt : pointsUpdated)
        {
            get(ResourceKey.create(Registries.DIMENSION, pt.key())).addPoint(pt.point());
        }
    }

    public static void remoteHello(@Nullable Player player)
    {
        if (player == null) return;
        var points = player.getData(HudCompass.POINTS_OF_INTEREST_ATTACHMENT);
        {
            points.otherSideHasMod = true;
            if (!player.level().isClientSide)
                points.sendInitialSync(player.registryAccess());
        }
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
        private final Map<UUID, PointInfo<?>> points = Maps.newHashMap();

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

            if (player.level().isClientSide && player.level().dimension() == worldKey)
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

            if (!player.level().isClientSide && (changed.size() > 0 || removed.size() > 0))
            {
                sendSync(player.registryAccess());
                changed.clear();
                removed.clear();
            }
        }

        public void addPointRequest(PointInfo<?> point)
        {
            if (otherSideHasMod && player.level().isClientSide && point instanceof BasicWaypoint)
            {
                PacketDistributor.sendToServer(AddWaypoint.of((BasicWaypoint) point));
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
            if (!player.level().isClientSide && otherSideHasMod)
            {
                changed.add(point);
            }
            if (!point.isDynamic())
                changeNumber++;
        }

        public void removePointRequest(PointInfo<?> point)
        {
            UUID id = point.getInternalId();
            if (otherSideHasMod && player.level().isClientSide)
            {
                PacketDistributor.sendToServer(new RemoveWaypoint(id));
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
                if (!player.level().isClientSide && otherSideHasMod)
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
            if (!player.level().isClientSide && otherSideHasMod)
            {
                changed.add(point);
            }
            if (!point.isDynamic())
                changeNumber++;
        }

        @SuppressWarnings({"rawtypes", "unchecked"})
        public void write(ValueOutput.ValueOutputList outputList)
        {
            for (PointInfo point : points.values())
            {
                if (!point.isDynamic())
                {
                    var child = outputList.addChild();
                    PointInfoRegistry.serializePoint(point, child);
                }
            }
        }

        @SuppressWarnings({"rawtypes", "unchecked"})
        public void write(RegistryFriendlyByteBuf buffer)
        {
            buffer.writeVarInt(points.size());
            for (PointInfo point : points.values())
            {
                PointInfoRegistry.serializePoint(buffer, point);
            }
        }

        public void read(ValueInput.ValueInputList list)
        {
            points.clear();
            for (var child : list)
            {
                PointInfo<?> point = PointInfoRegistry.deserializePoint(child);
                points.put(point.getInternalId(), point);
            }
        }

        public void read(RegistryFriendlyByteBuf buffer)
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
