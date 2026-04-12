package dev.gigaherz.hudcompass.integrations.client;

import com.mojang.serialization.*;
import dev.gigaherz.hudcompass.HudCompass;
import dev.gigaherz.hudcompass.icons.IIconData;
import dev.gigaherz.hudcompass.icons.IconDataSerializer;
import dev.gigaherz.hudcompass.icons.client.IIconRenderer;
import dev.gigaherz.hudcompass.icons.client.IconRendererRegistry;
import dev.gigaherz.hudcompass.integrations.server.PlayerTracker;
import dev.gigaherz.hudcompass.waypoints.PointInfo;
import dev.gigaherz.hudcompass.waypoints.PointInfoType;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.renderer.texture.TextureManager;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.util.ARGB;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.waypoints.TrackedWaypoint;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.stream.Stream;

public class LocatorBarPoints
{
    public static final LocatorBarPoints INSTANCE = new LocatorBarPoints();

    private static final DeferredRegister<PointInfoType<?>> PIT = HudCompass.POINT_INFO_TYPES;
    private static final DeferredRegister<IconDataSerializer<?>> IDS = HudCompass.ICON_DATA_SERIALIZERS;

    public static final DeferredHolder<PointInfoType<?>, PointInfoType<VanillaWaypointAdapter>>
            TYPE = PIT.register("vanilla_waypoint", () -> new PointInfoType<>(VanillaWaypointAdapter.CODEC, VanillaWaypointAdapter.STREAM_CODEC));

    public static final DeferredHolder<IconDataSerializer<?>, IconDataSerializer<VanillaWaypointIcon>>
            ICON_DATA = IDS.register("vanilla_waypoint", () -> new IconDataSerializer<>(VanillaWaypointIcon.CODEC, VanillaWaypointIcon.STREAM_CODEC));

    private final Map<TrackedWaypoint, VanillaWaypointAdapter> addedPoints = new HashMap<>();

    public static void init(IEventBus modEventBus)
    {
        modEventBus.addListener(INSTANCE::clientSetup);
    }

    private void clientSetup(FMLClientSetupEvent event)
    {
        IconRendererRegistry.registerRenderer(ICON_DATA.get(), new VanillaWaypointRenderer());

        NeoForge.EVENT_BUS.addListener(this::clientTick);
    }

    private void clientTick(ClientTickEvent.Post event)
    {
        var mc = Minecraft.getInstance();
        var player = mc.player;
        if (player == null)
            return;
  
        var pois = player.getData(HudCompass.POINTS_OF_INTEREST_ATTACHMENT);
        var byLevel = pois.get(player.level());

        var lostPoints = new HashMap<>(addedPoints);

        var cameraEntity = Objects.requireNonNullElse(Minecraft.getInstance().getCameraEntity(), player);

        var waypoints = player.connection.getWaypointManager();
        waypoints.forEachWaypoint(cameraEntity, waypoint -> {
            addedPoints.computeIfAbsent(waypoint, _ -> {
                var icon = new VanillaWaypointIcon(player, waypoint, cameraEntity);
                var point = new VanillaWaypointAdapter(player, waypoint, icon);

                byLevel.addPoint(point);

                return point;
            });
            lostPoints.remove(waypoint);
        });

        for(var entry : lostPoints.entrySet())
        {
            addedPoints.remove(entry.getKey());
            byLevel.removePoint(entry.getValue());
        }
    }

    public class VanillaWaypointAdapter extends PointInfo<VanillaWaypointAdapter>
    {
        public static final MapCodec<VanillaWaypointAdapter> CODEC = new MapCodec<>()
        {
            @Override
            public <T> Stream<T> keys(DynamicOps<T> ops)
            {
                return Stream.empty();
            }

            @Override
            public <T> DataResult<VanillaWaypointAdapter> decode(DynamicOps<T> ops, MapLike<T> input)
            {
                return DataResult.error(() -> "Cannot be encoded");
            }

            @Override
            public <T> RecordBuilder<T> encode(VanillaWaypointAdapter input, DynamicOps<T> ops, RecordBuilder<T> prefix)
            {
                return prefix.withErrorsFrom(DataResult.error(() -> "Cannot be encoded"));
            }
        };
        public static final StreamCodec<RegistryFriendlyByteBuf, VanillaWaypointAdapter> STREAM_CODEC = new StreamCodec<>()
        {
            @Override
            public VanillaWaypointAdapter decode(RegistryFriendlyByteBuf input)
            {
                //noinspection DataFlowIssue
                return null;
            }

            @Override
            public void encode(RegistryFriendlyByteBuf output, VanillaWaypointAdapter value)
            {
                /* do nothing */
            }
        };

        private final TrackedWaypoint waypoint;
        private Vec3 position;

        public VanillaWaypointAdapter(Player player, TrackedWaypoint waypoint, VanillaWaypointIcon icon)
        {
            var level = player.level();
            var label = waypoint.id().map(
                    uuid -> {
                        var player0 = level.getEntity(uuid);
                        if (player0 != null)
                            return player0.getDisplayName();
                        return null;
                    },
                    Component::literal
            );

            super(TYPE.get(), true, label, icon);
            this.waypoint = waypoint;
            tick(player);
        }

        @Override
        public Vec3 getPosition(Player player, float partialTicks)
        {
            return position;
        }

        @Override
        public void tick(Player player)
        {
            var mc = Minecraft.getInstance();
            var tickRateManager = mc.level.tickRateManager();
            var yaw = Math.toRadians(-player.getYHeadRot() - waypoint.yawAngleToCamera(player.level(), mc.gameRenderer.getMainCamera(), entity ->
                    mc.getDeltaTracker().getGameTimeDeltaPartialTick(!tickRateManager.isEntityFrozen(entity))));
            var dist = Math.sqrt(waypoint.distanceSquared(player));
            this.position = player.position().add(
                    Mth.sin(yaw) * dist,
                    0,
                    Mth.cos(yaw) * dist
            );
        }
    }

    public class VanillaWaypointIcon implements IIconData<VanillaWaypointIcon>
    {
        public static final MapCodec<VanillaWaypointIcon> CODEC = new MapCodec<>()
        {
            @Override
            public <T> Stream<T> keys(DynamicOps<T> ops)
            {
                return Stream.empty();
            }

            @Override
            public <T> DataResult<VanillaWaypointIcon> decode(DynamicOps<T> ops, MapLike<T> input)
            {
                return DataResult.error(() -> "Cannot be encoded");
            }

            @Override
            public <T> RecordBuilder<T> encode(VanillaWaypointIcon input, DynamicOps<T> ops, RecordBuilder<T> prefix)
            {
                return prefix.withErrorsFrom(DataResult.error(() -> "Cannot be encoded"));
            }
        };
        public static final StreamCodec<RegistryFriendlyByteBuf, VanillaWaypointIcon> STREAM_CODEC = new StreamCodec<>()
        {
            @Override
            public VanillaWaypointIcon decode(RegistryFriendlyByteBuf input)
            {
                //noinspection DataFlowIssue
                return null;
            }

            @Override
            public void encode(RegistryFriendlyByteBuf output, VanillaWaypointIcon value)
            {
                /* do nothing */
            }
        };

        private final TrackedWaypoint waypoint;
        private final Entity cameraEntity;
        private final UUID target;

        public VanillaWaypointIcon(Player player, TrackedWaypoint waypoint, Entity cameraEntity)
        {
            var target = waypoint.id().map(x -> x,_ -> null);

            this.waypoint = waypoint;
            this.cameraEntity = cameraEntity;
            this.target = target;
        }

        @Override
        public IconDataSerializer<VanillaWaypointIcon> getSerializer()
        {
            return ICON_DATA.get();
        }
    }

    public class VanillaWaypointRenderer implements IIconRenderer<VanillaWaypointIcon>
    {

        @Override
        public void renderIcon(VanillaWaypointIcon data, Player player, TextureManager textureManager, GuiGraphicsExtractor graphics, int x, int y, int alpha)
        {
            if (!PlayerTracker.OtherPlayerRenderer.drawUUID(player, data.target, graphics, x, y, alpha))
            {
                var waypoint = data.waypoint;
                var cameraEntity = data.cameraEntity;
                var icon = waypoint.icon();
                var style = Minecraft.getInstance().getWaypointStyles().get(icon.style);
                var distance = Mth.sqrt((float) waypoint.distanceSquared(cameraEntity));
                var sprite = style.sprite(distance);
                int color = icon.color
                        .orElseGet(
                                () -> waypoint.id()
                                        .map(
                                                uuid -> ARGB.setBrightness(ARGB.color(255, uuid.hashCode()), 0.9F),
                                                name -> ARGB.setBrightness(ARGB.color(255, name.hashCode()), 0.9F)
                                        )
                        );
                color = (color & 0xFFFFFF) | (alpha << 24);
                graphics.blitSprite(RenderPipelines.GUI_TEXTURED, sprite, x - 4, y - 4, 9, 9, color);
            }
        }
    }
}
