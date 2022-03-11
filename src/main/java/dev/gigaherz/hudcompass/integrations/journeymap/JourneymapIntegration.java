package dev.gigaherz.hudcompass.integrations.journeymap;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.*;
import com.mojang.math.Matrix4f;
import dev.gigaherz.hudcompass.HudCompass;
import dev.gigaherz.hudcompass.icons.BasicIconData;
import dev.gigaherz.hudcompass.icons.IIconData;
import dev.gigaherz.hudcompass.icons.IconDataSerializer;
import dev.gigaherz.hudcompass.icons.client.IIconRenderer;
import dev.gigaherz.hudcompass.icons.client.IconRendererRegistry;
import dev.gigaherz.hudcompass.waypoints.PointInfo;
import dev.gigaherz.hudcompass.waypoints.PointInfoType;
import dev.gigaherz.hudcompass.waypoints.PointsOfInterest;
import journeymap.client.api.IClientAPI;
import journeymap.client.api.IClientPlugin;
import journeymap.client.api.display.Waypoint;
import journeymap.client.api.event.ClientEvent;
import journeymap.client.api.event.WaypointEvent;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.texture.TextureManager;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.TextComponent;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.RegistryObject;

import javax.annotation.Nonnull;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.EnumSet;
import java.util.UUID;


@journeymap.client.api.ClientPlugin
public class JourneymapIntegration implements IClientPlugin
{
    private static final DeferredRegister<PointInfoType<?>> PIT = HudCompass.POINT_INFO_TYPES;
    private static final DeferredRegister<IconDataSerializer<?>> IDS = HudCompass.ICON_DATA_SERIALIZERS;

    public static final RegistryObject<PointInfoType<JmWaypoint>> JM_WAYPOINT = PIT.register("journeymap", () -> new PointInfoType<>(JmWaypoint::new));

    public static final RegistryObject<JmIconData.Serializer> JM_ICON_DATA = IDS.register("journeymap", JmIconData.Serializer::new);

    private static IClientAPI API;

    public static void staticInit()
    {
        // Nothing to do here.
    }

    @Override
    public void initialize(IClientAPI jmClientApi)
    {
        API = jmClientApi;
        jmClientApi.subscribe(getModId(), EnumSet.of(ClientEvent.Type.WAYPOINT));

        IconRendererRegistry.registerRenderer(JM_ICON_DATA.get(), new JmIconDataRenderer());
    }

    @Override
    public String getModId()
    {
        return HudCompass.MODID;
    }

    @Override
    public void onEvent(ClientEvent event)
    {
        if (!(event instanceof WaypointEvent wpEvent))
            return;

        var player = Minecraft.getInstance().player;
        if (player == null) return;

        player.getCapability(PointsOfInterest.INSTANCE).ifPresent((pois) -> {
            PointsOfInterest.WorldPoints worldPoints = pois.get(player.level);

            String dimensionName = player.level.dimension().location().toString();

            var jmwp = wpEvent.waypoint;
            var id = getId(jmwp);

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
        var bytes = jmwp.getGuid().getBytes(StandardCharsets.UTF_8);
        return UUID.nameUUIDFromBytes(bytes);
    }

    public static class JmWaypoint extends PointInfo<JmWaypoint>
    {
        private final Waypoint jmWaypoint;

        public JmWaypoint(Waypoint jmWaypoint)
        {
            super(JM_WAYPOINT.get(), true, new TextComponent(jmWaypoint.getName()), BasicIconData.MISSING_ICON);
            this.jmWaypoint = jmWaypoint;

            this.dynamic();
            this.clientPoint();

            var id = getId(jmWaypoint);
            this.setInternalId(id);

            if (jmWaypoint.hasIcon())
            {
                this.setIconData(new JmIconData(jmWaypoint));
            }
        }

        public JmWaypoint()
        {
            super(JM_WAYPOINT.get(), true, null, BasicIconData.MISSING_ICON);

            throw new IllegalStateException("This waypoint is client-only and cannot be synchronized.");
        }

        @Override
        public Vec3 getPosition()
        {
            var p = jmWaypoint.getPosition();
            return new Vec3(p.getX(), p.getY(), p.getZ());
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

    private static class JmIconData implements IIconData<JmIconData>
    {
        private final Waypoint jmwp;

        public JmIconData(Waypoint jmwp)
        {
            this.jmwp = jmwp;
        }

        @Override
        public IconDataSerializer<JmIconData> getSerializer()
        {
            return JM_ICON_DATA.get();
        }

        public static class Serializer extends IconDataSerializer<JmIconData>
        {
            @Override
            public CompoundTag write(JmIconData data, CompoundTag tag)
            {
                throw new IllegalStateException("This icon data is dynamic and cannot be serialized.");
            }

            @Override
            public JmIconData read(CompoundTag tag)
            {
                throw new IllegalStateException("This icon data is dynamic and cannot be serialized.");
            }

            @Override
            public void write(JmIconData data, FriendlyByteBuf buffer)
            {
                throw new IllegalStateException("This icon data is client-only and cannot be synchronized.");
            }

            @Override
            public JmIconData read(FriendlyByteBuf buffer)
            {
                throw new IllegalStateException("This icon data is client-only and cannot be synchronized.");
            }
        }
    }

    private static class JmIconDataRenderer implements IIconRenderer<JmIconData>
    {
        @Override
        public void renderIcon(JmIconData data, Player player, TextureManager textureManager, PoseStack matrixStack, int x, int y, int alpha)
        {
            if (!data.jmwp.hasIcon())
                return;

            var m = data.jmwp.getIcon();
            var tex = m.getImageLocation();
            if (tex != null)
            {
                var w = m.getDisplayWidth();
                var h = m.getDisplayHeight();
/*
                while (w < 4) w*=2;
                while (w > 8) w/=2;

                while (h < 4) h*=2;
                while (h > 8) h/=2;
*/
                RenderSystem.setShaderTexture(0, tex);
                RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, alpha / 255.0f);

                blit(matrixStack, (float) (x - w / 2), (float) (y - h / 2), (float) w, (float) h,
                        m.getTextureX(), m.getTextureY(), m.getTextureWidth(), m.getTextureHeight(), m.getTextureWidth(), m.getTextureHeight());
            }
        }

        private static void blit(PoseStack pose, float x, float y, float w, float h, int minu, int minv, int uw, int vh, int tw, int th)
        {
            innerBlit(pose.last().pose(), x, x+w, y, y+h, minu/(float)tw, (minu+uw)/(float)tw, minv/(float)th, (minv+vh)/(float)th);
        }

        private static void innerBlit(Matrix4f pMatrix, float pX1, float pX2, float pY1, float pY2, float pMinU, float pMaxU, float pMinV, float pMaxV) {
            RenderSystem.setShader(GameRenderer::getPositionTexShader);
            BufferBuilder bufferbuilder = Tesselator.getInstance().getBuilder();
            bufferbuilder.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_TEX);
            bufferbuilder.vertex(pMatrix, pX1, pY2, 0).uv(pMinU, pMaxV).endVertex();
            bufferbuilder.vertex(pMatrix, pX2, pY2, 0).uv(pMaxU, pMaxV).endVertex();
            bufferbuilder.vertex(pMatrix, pX2, pY1, 0).uv(pMaxU, pMinV).endVertex();
            bufferbuilder.vertex(pMatrix, pX1, pY1, 0).uv(pMinU, pMinV).endVertex();
            bufferbuilder.end();
            BufferUploader.end(bufferbuilder);
        }
    }
}
