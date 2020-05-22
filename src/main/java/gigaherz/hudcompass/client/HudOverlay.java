package gigaherz.hudcompass.client;

import com.google.common.collect.Lists;
import com.google.common.collect.Sets;
import com.mojang.blaze3d.systems.RenderSystem;
import gigaherz.hudcompass.waypoints.PointInfo;
import gigaherz.hudcompass.waypoints.PointsOfInterest;
import gigaherz.hudcompass.waypoints.client.PointRenderer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.AbstractGui;
import net.minecraft.client.gui.FontRenderer;
import net.minecraft.client.renderer.BufferBuilder;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.client.renderer.texture.TextureManager;
import net.minecraft.client.renderer.vertex.DefaultVertexFormats;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec2f;
import net.minecraft.util.math.Vec3d;
import net.minecraftforge.client.event.RenderGameOverlayEvent;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import org.lwjgl.opengl.GL11;

import java.util.List;
import java.util.Set;

public class HudOverlay extends AbstractGui
{
    public static final ResourceLocation LOCATION_MAP_ICONS = new ResourceLocation("minecraft", "textures/map/map_icons.png");
    public static final ResourceLocation LOCATION_POI_ICONS = new ResourceLocation("hudcompass", "textures/poi_icons.png");

    private final Minecraft mc;
    private final FontRenderer font;
    private final TextureManager textureManager;

    public static void init()
    {
        MinecraftForge.EVENT_BUS.register(new HudOverlay());
    }

    private HudOverlay()
    {
        this.mc = Minecraft.getInstance();
        this.font = mc.fontRenderer;
        this.textureManager = mc.textureManager;
    }

    Set<RenderGameOverlayEvent.ElementType> NOT_BEFORE = Sets.newHashSet(
            RenderGameOverlayEvent.ElementType.ALL,
            RenderGameOverlayEvent.ElementType.VIGNETTE,
            RenderGameOverlayEvent.ElementType.HELMET,
            RenderGameOverlayEvent.ElementType.PORTAL,
            RenderGameOverlayEvent.ElementType.CROSSHAIRS,
            RenderGameOverlayEvent.ElementType.BOSSHEALTH,
            RenderGameOverlayEvent.ElementType.BOSSINFO
    );

    boolean drawnThisFrame = false;

    @SubscribeEvent(priority = EventPriority.LOWEST, receiveCanceled = true)
    public void preOverlay(RenderGameOverlayEvent.Pre event)
    {
        if (event.getType() == RenderGameOverlayEvent.ElementType.ALL)
        {
            drawnThisFrame = false;
            return;
        }

        if (mc.gameSettings.hideGUI || drawnThisFrame)
            return;

        if (NOT_BEFORE.contains(event.getType()))
            return;

        renderCompass();
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public void postOverlay(RenderGameOverlayEvent.Post event)
    {
        if (mc.gameSettings.hideGUI || drawnThisFrame)
            return;

        if (event.getType() == RenderGameOverlayEvent.ElementType.ALL)
        {
            renderCompass();
        }
    }

    float partialTicks;

    private void renderCompass()
    {
        if (!mc.isGamePaused())
        {
            partialTicks = mc.getRenderPartialTicks();
        }

        int xPos = mc.getMainWindow().getScaledWidth() / 2;
        float yaw = MathHelper.lerp(partialTicks, mc.player.prevRotationYawHead, mc.player.rotationYawHead) % 360;
        //if (yaw > 180) yaw -= 360;
        if (yaw < 0) yaw += 360;

        RenderSystem.enableBlend();
        RenderSystem.disableAlphaTest();

        fillRect(xPos - 90, 10, xPos + 90, 18, 0x3f000000);

        //drawCenteredString(font, String.format("%f", yaw), xPos, 28, 0xFFFFFF);

        drawCardinalDirection(yaw, 0, xPos, "S");
        drawCardinalDirection(yaw, 90, xPos, "W");
        drawCardinalDirection(yaw, 180, xPos, "N");
        drawCardinalDirection(yaw, 270, xPos, "E");

        fillRect(xPos - 1.5f, 10, xPos - 0.5f, 18, 0x3FFFFFFF);
        fillRect(xPos + 0.5f, 10, xPos + 1.5f, 18, 0x3FFFFFFF);

        fillRect(xPos - 45 - 0.5f, 10, xPos - 45 + 0.5f, 18, 0x3FFFFFFF);
        fillRect(xPos + 45 - 0.5f, 10, xPos + 45 + 0.5f, 18, 0x3FFFFFFF);

        PlayerEntity player = mc.player;
        double playerPosX = MathHelper.lerp(partialTicks, mc.player.prevPosX, mc.player.getPosX());
        double playerPosY = MathHelper.lerp(partialTicks, mc.player.prevPosY, mc.player.getPosY());
        double playerPosZ = MathHelper.lerp(partialTicks, mc.player.prevPosZ, mc.player.getPosZ());

        final float yaw0 = yaw;
        mc.player.getCapability(PointsOfInterest.INSTANCE).ifPresent(pts -> {
            List<PointInfo> sortedPoints = Lists.newArrayList(pts.getPoints());
            sortedPoints.sort((a,b) -> {
                Vec3d positionA = a.getPosition();
                Vec3d positionB = b.getPosition();
                float angleA = Math.abs(angleDistance(yaw0, angleFromPoint(positionA, playerPosX, playerPosY, playerPosZ).x));
                float angleB = Math.abs(angleDistance(yaw0, angleFromPoint(positionB, playerPosX, playerPosY, playerPosZ).x));
                return (int)Math.signum(angleB-angleA);
            });
            for (PointInfo point : sortedPoints)
            {
                Vec3d position = point.getPosition();
                Vec2f angleYd = angleFromPoint(position, playerPosX, playerPosY, playerPosZ);
                drawPoi(player, yaw0, angleYd.x, angleYd.y, xPos, point);
            }
        });

        drawnThisFrame = true;
    }

    private Vec2f angleFromPoint(Vec3d position, double playerPosX, double playerPosY, double playerPosZ)
    {
        double xd = position.x - playerPosX;
        double yd = position.y - playerPosY;
        double zd = position.z - playerPosZ;
        return new Vec2f((float) Math.toDegrees(-Math.atan2(xd, zd)), (float)yd);
    }

    private void drawCardinalDirection(float yaw, float angle, int xPos, String text)
    {
        float nDist = angleDistance(yaw, angle);
        if (Math.abs(nDist) <= 90)
        {
            float nPos = xPos + nDist;
            fillRect(nPos-0.5f, 10, nPos+0.5f, 18, 0x7FFFFFFF);
            if (mc.gameSettings.accessibilityTextBackground)
                drawCenteredShadowString(font, text, nPos, 1, 0xFFFFFF);
            else
                drawCenteredBoxedString(font, text, nPos, 1, 0xFFFFFF);
        }
    }

    public void drawCenteredShadowString(FontRenderer font, String text, float x, float y, int color)
    {
        float width = font.getStringWidth(text);
        font.drawStringWithShadow(text, x - width / 2, y, color);
    }

    public static void drawCenteredBoxedString(FontRenderer font, String text, float x, float y, int color)
    {
        Minecraft mc = Minecraft.getInstance();
        float width = font.getStringWidth(text);
        float height = font.FONT_HEIGHT;
        float width1 = width+4;
        float height1 = height+3;
        float x0 = x-width1/2;
        fillRect(x0, y, x0 + width1, y + height1, ((int) MathHelper.clamp(mc.gameSettings.accessibilityTextBackgroundOpacity * 255, 0, 255)) << 24);
        font.drawStringWithShadow(text, x-width/2, y+2, color);
    }

    private void drawPoi(PlayerEntity player, float yaw, float angle, float yDelta, int xPos, PointInfo point)
    {
        float nDist = angleDistance(yaw, angle);
        if (Math.abs(nDist) <= 90)
        {
            float nPos = xPos + nDist;
            RenderSystem.pushMatrix();
            RenderSystem.translatef(nPos, 0, 0);

            PointRenderer.renderIcon(point, player, textureManager, 0, 14);
            PointRenderer.renderLabel(point, font, 0, 20);

            if (point.displayVerticalDistance(player))
            {
                if (yDelta >= 2) drawAboveArrow(nPos, yDelta);
                if (yDelta <= -2) drawBelowArrow(nPos, yDelta);
            }

            RenderSystem.popMatrix();
        }
    }

    private void drawAboveArrow(float nPos, float yDelta)
    {
        int x = yDelta > 10 ? 8 : 0;
        int y = 0;
        textureManager.bindTexture(LOCATION_POI_ICONS);
        blitRect(-4.5f, 4, x, y, 8, 8, 128, 128);
    }

    private void drawBelowArrow(float nPos, float yDelta)
    {
        int x = yDelta < -10 ? 24 : 16;
        int y = 0;
        textureManager.bindTexture(LOCATION_POI_ICONS);
        blitRect(-4.5f, 16, x, y, 8, 8, 128, 128);
    }

    /**
     * Because the vanilla one messes with the GL state too much.
     *
     * @param x0    First X coord
     * @param y0    First Y coord
     * @param x1    Second X coord
     * @param y1    Second Y coord
     * @param color Rectangle color
     */
    private static void fillRect(float x0, float y0, float x1, float y1, int color)
    {
        RenderSystem.enableBlend();
        RenderSystem.disableAlphaTest();

        int a = (color >> 24 & 255);
        int r = (color >> 16 & 255);
        int g = (color >> 8 & 255);
        int b = (color & 255);
        Tessellator tess = Tessellator.getInstance();
        BufferBuilder builder = tess.getBuffer();
        RenderSystem.disableTexture();
        builder.begin(GL11.GL_QUADS, DefaultVertexFormats.POSITION_COLOR);
        builder.pos((double) x0, (double) y1, 0.0D).color(r, g, b, a).endVertex();
        builder.pos((double) x1, (double) y1, 0.0D).color(r, g, b, a).endVertex();
        builder.pos((double) x1, (double) y0, 0.0D).color(r, g, b, a).endVertex();
        builder.pos((double) x0, (double) y0, 0.0D).color(r, g, b, a).endVertex();
        tess.draw();
        RenderSystem.enableTexture();
    }

    private static void blitRect(float x0, float y0, float xt, float yt, float width, float height, int tWidth, int tHeight)
    {
        RenderSystem.enableBlend();
        RenderSystem.disableAlphaTest();

        float tx0 = xt / tWidth;
        float ty0 = yt / tHeight;
        float tx1 = tx0 + width/tWidth;
        float ty1 = ty0 + height/tHeight;

        float x1 = x0 + width;
        float y1 = y0 + height;

        Tessellator tess = Tessellator.getInstance();
        BufferBuilder builder = tess.getBuffer();
        builder.begin(7, DefaultVertexFormats.POSITION_TEX);
        builder.pos(x0, y1, 0.0D).tex(tx0,ty1).endVertex();
        builder.pos(x1, y1, 0.0D).tex(tx1,ty1).endVertex();
        builder.pos(x1, y0, 0.0D).tex(tx1,ty0).endVertex();
        builder.pos(x0, y0, 0.0D).tex(tx0,ty0).endVertex();
        tess.draw();
    }

    private float angleDistance(float yaw, float other)
    {
        float dist = other - yaw;
        if (dist > 0)
        {
            return dist > 180 ? (dist - 360) : dist;
        }
        else
        {
            return dist < -180 ? (dist + 360) : dist;
        }
    }
}
