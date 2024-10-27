package dev.gigaherz.hudcompass.integrations.journeymap;

import dev.gigaherz.hudcompass.ConfigData;
import dev.gigaherz.hudcompass.HudCompass;
import dev.gigaherz.hudcompass.icons.BasicIconData;
import dev.gigaherz.hudcompass.waypoints.PointInfoType;
import dev.gigaherz.hudcompass.waypoints.PointsOfInterest;
import dev.gigaherz.hudcompass.waypoints.SpecificPointInfo;
import journeymap.api.v2.client.IClientAPI;
import journeymap.api.v2.client.IClientPlugin;
import journeymap.api.v2.common.event.CommonEventRegistry;
import journeymap.api.v2.common.event.common.WaypointEvent;
import journeymap.api.v2.client.model.MapImage;
import journeymap.api.v2.common.event.impl.ClientEvent;
import journeymap.api.v2.common.waypoint.Waypoint;
import net.minecraft.client.Minecraft;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

import javax.annotation.Nonnull;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.EnumSet;
import java.util.UUID;


@journeymap.api.v2.client.JourneyMapPlugin(apiVersion = "2.0.0")
public class JourneymapIntegration implements IClientPlugin
{
    private static final DeferredRegister<PointInfoType<?>> PIT = HudCompass.POINT_INFO_TYPES;

    public static final DeferredHolder<PointInfoType<?>, PointInfoType<JmWaypoint>> JM_WAYPOINT = PIT.register("journeymap", () -> new PointInfoType<>(JmWaypoint::new));

    public static void staticInit()
    {
        // Nothing to do here.
    }

    @Override
    public void initialize(IClientAPI jmClientApi)
    {
        CommonEventRegistry.WAYPOINT_EVENT.subscribe(getModId(), this::onWaypointEvent);
    }

    @Override
    public String getModId()
    {
        return HudCompass.MODID;
    }

    public void onWaypointEvent(WaypointEvent wpEvent)
    {
        var player = Minecraft.getInstance().player;
        if (player == null) return;

        if (!ConfigData.enableJourneymapIntegration)
            return;

        var pois = player.getData(HudCompass.POINTS_OF_INTEREST_ATTACHMENT);
        {
            PointsOfInterest.WorldPoints worldPoints = pois.get(player.level());

            String dimensionName = player.level().dimension().location().toString();

            var jmwp = wpEvent.waypoint;
            var id = getId(jmwp);

            boolean isVisible = jmwp.isEnabled() && (jmwp.getDimensions() == null || jmwp.getDimensions().contains(dimensionName));
            switch (wpEvent.getContext())
            {
                case DELETED:
                    worldPoints.removePoint(id);
                    break;
                case READ:
                case CREATE:
                    if (isVisible)
                    {
                        worldPoints.addPoint(new JmWaypoint(jmwp));
                    }
                    break;
                default:
                    if (isVisible)
                    {
                        worldPoints.addPoint(new JmWaypoint(jmwp));
                    }
                    else
                    {
                        worldPoints.removePoint(id);
                    }
                    break;
            }
        }
    }

    @Nonnull
    private static UUID getId(Waypoint jmwp)
    {
        var bytes = jmwp.getGuid().getBytes(StandardCharsets.UTF_8);
        return UUID.nameUUIDFromBytes(bytes);
    }

    public static class JmWaypoint extends SpecificPointInfo<JmWaypoint, BasicIconData>
    {
        private final Waypoint jmWaypoint;

        public JmWaypoint(Waypoint jmWaypoint)
        {
            super(JM_WAYPOINT.get(), true, Component.literal(jmWaypoint.getName()), BasicIconData.generic());
            this.jmWaypoint = jmWaypoint;

            this.dynamic();
            this.clientPoint();

            UUID id = getId(jmWaypoint);
            this.setInternalId(id);

            // TODO: icon textures
            var basicIconData = getIconData();
            int rgb = jmWaypoint.getIconColor();
            float r = ((rgb >> 16) & 0xFF) / 255.0f;
            float g = ((rgb >> 8) & 0xFF) / 255.0f;
            float b = ((rgb) & 0xFF) / 255.0f;
            float a = 1.0f; // icon.getOpacity();
            basicIconData.setColor(r, g, b, a);
        }

        public JmWaypoint()
        {
            super(JM_WAYPOINT.get(), true, null, BasicIconData.generic());

            throw new IllegalStateException("This waypoint is client-only and cannot be synchronized.");
        }

        @Override
        public Vec3 getPosition()
        {
            return Vec3.atCenterOf(jmWaypoint.getBlockPos());
        }

        @Override
        public Vec3 getPosition(Player player, float partialTicks)
        {
            return getPosition();
        }

        @Override
        protected void serializeAdditional(CompoundTag tag)
        {
            // Client-only
            throw new IllegalStateException("This waypoint is dynamic and cannot be serialized.");
        }

        @Override
        protected void deserializeAdditional(CompoundTag tag)
        {
            // Client-only
            throw new IllegalStateException("This waypoint is dynamic and cannot be serialized.");
        }

        @Override
        protected void serializeAdditional(FriendlyByteBuf tag)
        {
            // Client-only
            throw new IllegalStateException("This waypoint is client-only and cannot be synchronized.");
        }

        @Override
        protected void deserializeAdditional(FriendlyByteBuf tag)
        {
            // Client-only
            throw new IllegalStateException("This waypoint is client-only and cannot be synchronized.");
        }
    }
}
