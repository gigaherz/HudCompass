package dev.gigaherz.hudcompass.integrations.journeymap;

import dev.gigaherz.hudcompass.ConfigData;
import dev.gigaherz.hudcompass.HudCompass;
import dev.gigaherz.hudcompass.icons.BasicIconData;
import dev.gigaherz.hudcompass.icons.IIconData;
import dev.gigaherz.hudcompass.waypoints.PointInfo;
import dev.gigaherz.hudcompass.waypoints.PointInfoType;
import dev.gigaherz.hudcompass.waypoints.PointsOfInterest;
import journeymap.client.api.IClientAPI;
import journeymap.client.api.IClientPlugin;
import journeymap.client.api.display.Waypoint;
import journeymap.client.api.event.ClientEvent;
import journeymap.client.api.event.WaypointEvent;
import journeymap.client.api.model.MapImage;
import net.minecraft.client.Minecraft;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.nbt.CompoundNBT;
import net.minecraft.network.PacketBuffer;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.vector.Vector3d;
import net.minecraft.util.text.StringTextComponent;
import net.minecraftforge.fml.RegistryObject;
import net.minecraftforge.registries.DeferredRegister;

import javax.annotation.Nonnull;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.EnumSet;
import java.util.UUID;


@journeymap.client.api.ClientPlugin
public class JourneymapIntegration implements IClientPlugin
{
    private static final DeferredRegister<PointInfoType<?>> PIT = HudCompass.POINT_INFO_TYPES;

    public static final RegistryObject<PointInfoType<JmWaypoint>> JM_WAYPOINT = PIT.register("journeymap", () -> new PointInfoType<>(JmWaypoint::new));

    public static void staticInit()
    {
        // Nothing to do here.
    }

    @Override
    public void initialize(IClientAPI jmClientApi)
    {
        jmClientApi.subscribe(getModId(), EnumSet.of(ClientEvent.Type.WAYPOINT));
    }

    @Override
    public String getModId()
    {
        return HudCompass.MODID;
    }

    @Override
    public void onEvent(ClientEvent event)
    {
        if (!(event instanceof WaypointEvent))
            return;

        WaypointEvent wpEvent = (WaypointEvent) event;

        PlayerEntity player = Minecraft.getInstance().player;
        if (player == null) return;

        if (!ConfigData.enableJourneymapIntegration)
            return;

        player.getCapability(PointsOfInterest.INSTANCE).ifPresent((pois) -> {
            PointsOfInterest.WorldPoints worldPoints = pois.get(player.level);

            String dimensionName = player.level.dimension().location().toString();

            Waypoint jmwp = wpEvent.waypoint;
            UUID id = getId(jmwp);

            boolean isVisible = jmwp.isEnabled() && (jmwp.getDisplayDimensions() == null || Arrays.asList(jmwp.getDisplayDimensions()).contains(dimensionName));
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
        });
    }

    @Nonnull
    private static UUID getId(Waypoint jmwp)
    {
        byte[] bytes = jmwp.getGuid().getBytes(StandardCharsets.UTF_8);
        return UUID.nameUUIDFromBytes(bytes);
    }

    public static class JmWaypoint extends PointInfo<JmWaypoint>
    {
        private final Waypoint jmWaypoint;

        public JmWaypoint(Waypoint jmWaypoint)
        {
            super(JM_WAYPOINT.get(), true, new StringTextComponent(jmWaypoint.getName()), BasicIconData.poi(5));
            this.jmWaypoint = jmWaypoint;

            this.dynamic();
            this.clientPoint();

            UUID id = getId(jmWaypoint);
            this.setInternalId(id);

            if (jmWaypoint.hasIcon())
            {
                IIconData<?> iconData = getIconData();
                if (iconData instanceof BasicIconData)
                {
                    BasicIconData basicIconData = (BasicIconData) iconData;

                    MapImage icon = jmWaypoint.getIcon();
                    int rgb = icon.getColor();
                    float r = ((rgb >> 16) & 0xFF) / 255.0f;
                    float g = ((rgb >> 8) & 0xFF) / 255.0f;
                    float b = ((rgb) & 0xFF) / 255.0f;
                    float a = 1.0f; // icon.getOpacity();
                    basicIconData.setColor(r, g, b, a);
                }
            }
        }

        public JmWaypoint()
        {
            super(JM_WAYPOINT.get(), true, null, BasicIconData.poi(5));

            throw new IllegalStateException("This waypoint is client-only and cannot be synchronized.");
        }

        @Override
        public Vector3d getPosition()
        {
            return Vector3d.atCenterOf(jmWaypoint.getPosition());
        }

        @Override
        public Vector3d getPosition(PlayerEntity player, float partialTicks)
        {
            return getPosition();
        }

        @Override
        protected void serializeAdditional(CompoundNBT tag)
        {
            // Client-only
            throw new IllegalStateException("This waypoint is dynamic and cannot be serialized.");
        }

        @Override
        protected void deserializeAdditional(CompoundNBT tag)
        {
            // Client-only
            throw new IllegalStateException("This waypoint is dynamic and cannot be serialized.");
        }

        @Override
        protected void serializeAdditional(PacketBuffer tag)
        {
            // Client-only
            throw new IllegalStateException("This waypoint is client-only and cannot be synchronized.");
        }

        @Override
        protected void deserializeAdditional(PacketBuffer tag)
        {
            // Client-only
            throw new IllegalStateException("This waypoint is client-only and cannot be synchronized.");
        }
    }
}
