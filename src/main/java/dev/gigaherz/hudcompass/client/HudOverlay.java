package dev.gigaherz.hudcompass.client;

import com.google.common.collect.Lists;
import com.google.common.collect.Sets;
import com.mojang.blaze3d.matrix.MatrixStack;
import com.mojang.blaze3d.systems.RenderSystem;
import dev.gigaherz.hudcompass.ConfigData;
import dev.gigaherz.hudcompass.waypoints.PointInfo;
import dev.gigaherz.hudcompass.waypoints.PointsOfInterest;
import dev.gigaherz.hudcompass.waypoints.client.PointRenderer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.AbstractGui;
import net.minecraft.client.gui.FontRenderer;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.renderer.BufferBuilder;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.client.renderer.texture.TextureManager;
import net.minecraft.client.renderer.vertex.DefaultVertexFormats;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.item.Item;
import net.minecraft.item.Items;
import net.minecraft.tags.ITag;
import net.minecraft.tags.ItemTags;
import net.minecraft.util.IReorderingProcessor;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.vector.Matrix4f;
import net.minecraft.util.math.vector.Vector2f;
import net.minecraft.util.math.vector.Vector3d;
import net.minecraft.util.text.ITextComponent;
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
        this.font = mc.font;
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
    boolean needsPop = false;

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public void preOverlayHigh(RenderGameOverlayEvent.Pre event)
    {
        if (event.getType() == RenderGameOverlayEvent.ElementType.BOSSHEALTH && !mc.options.hideGui && canRender())
        {
            RenderSystem.pushMatrix();
            RenderSystem.translatef(0, 28, 0);
            needsPop = true;
        }
    }

    @SubscribeEvent(priority = EventPriority.LOWEST, receiveCanceled = true)
    public void preOverlay(RenderGameOverlayEvent.Pre event)
    {
        if (event.getType() == RenderGameOverlayEvent.ElementType.ALL)
        {
            drawnThisFrame = false;
            needsPop = false;
            return;
        }

        if (mc.options.hideGui || drawnThisFrame)
            return;

        if (NOT_BEFORE.contains(event.getType()))
            return;

        renderCompass(event.getMatrixStack());
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public void postOverlay(RenderGameOverlayEvent.Post event)
    {
        if (event.getType() == RenderGameOverlayEvent.ElementType.BOSSHEALTH && needsPop)
        {
            RenderSystem.popMatrix();
            needsPop = false;
        }

        if (event.getType() == RenderGameOverlayEvent.ElementType.ALL && needsPop)
        {
            RenderSystem.popMatrix();
            needsPop = false;
        }

        if (mc.options.hideGui || drawnThisFrame)
            return;

        if (event.getType() == RenderGameOverlayEvent.ElementType.ALL)
        {
            renderCompass(event.getMatrixStack());
        }
    }

    private void renderCompass(MatrixStack matrixStack)
    {
        if (!canRender()) return;

        if (mc.player == null) return;

        boolean isPaused = mc.isPaused();

        float elapsed = isPaused ? 0 : mc.getDeltaFrameTime();
        float partialTicks = isPaused ? 0 : mc.getFrameTime();

        int xPos = mc.getWindow().getGuiScaledWidth() / 2;
        float yaw = MathHelper.lerp(partialTicks, mc.player.yHeadRotO, mc.player.yHeadRot) % 360;
        //if (yaw > 180) yaw -= 360;
        if (yaw < 0) yaw += 360;

        RenderSystem.enableBlend();
        RenderSystem.disableAlphaTest();

        fillRect(matrixStack, xPos - 90, 10, xPos + 90, 18, 0x3f000000);

        //drawCenteredString(font, String.format("%f", yaw), xPos, 28, 0xFFFFFF);

        drawCardinalDirection(matrixStack, yaw, 0, xPos, "S");
        drawCardinalDirection(matrixStack, yaw, 90, xPos, "W");
        drawCardinalDirection(matrixStack, yaw, 180, xPos, "N");
        drawCardinalDirection(matrixStack, yaw, 270, xPos, "E");

        fillRect(matrixStack, xPos - 1.5f, 10, xPos - 0.5f, 18, 0x3FFFFFFF);
        fillRect(matrixStack, xPos + 0.5f, 10, xPos + 1.5f, 18, 0x3FFFFFFF);

        fillRect(matrixStack, xPos - 45 - 0.5f, 10, xPos - 45 + 0.5f, 18, 0x3FFFFFFF);
        fillRect(matrixStack, xPos + 45 - 0.5f, 10, xPos + 45 + 0.5f, 18, 0x3FFFFFFF);

        final PlayerEntity player = mc.player;
        double playerPosX = MathHelper.lerp(partialTicks, mc.player.xo, mc.player.getX());
        double playerPosY = MathHelper.lerp(partialTicks, mc.player.yo, mc.player.getY());
        double playerPosZ = MathHelper.lerp(partialTicks, mc.player.zo, mc.player.getZ());

        Vector3d playerPosition = new Vector3d(playerPosX, playerPosY, playerPosZ);

        final float yaw0 = yaw;
        final float elapsed0 = elapsed;
        player.getCapability(PointsOfInterest.INSTANCE).ifPresent(pts -> {
            List<PointInfo<?>> sortedPoints = Lists.newArrayList(pts.get(player.level).getPoints());
            sortedPoints.sort((a, b) -> {
                Vector3d positionA = a.getPosition(player, partialTicks);
                Vector3d positionB = b.getPosition(player, partialTicks);
                float angleA = Math.abs(angleDistance(yaw0, angleFromPoint(positionA, playerPosX, playerPosY, playerPosZ).x));
                float angleB = Math.abs(angleDistance(yaw0, angleFromPoint(positionB, playerPosX, playerPosY, playerPosZ).x));
                return (int) Math.signum(angleB - angleA);
            });
            for (PointInfo<?> point : sortedPoints)
            {
                Vector3d position = point.getPosition(player, partialTicks);
                Vector2f angleYd = angleFromPoint(position, playerPosX, playerPosY, playerPosZ);
                drawPoi(player, matrixStack, yaw0, angleYd.x, angleYd.y, xPos, point, point == pts.getTargetted(), elapsed0, position.subtract(playerPosition));
            }
        });

        drawnThisFrame = true;
    }

    private boolean canRender()
    {
        if (mc.player == null) return false;

        switch (ConfigData.displayWhen)
        {
            case NEVER:
                return false;
            case ALWAYS:
                return true;
            case HAS_COMPASS:
                return findCompassInInventory();
            case HOLDING_COMPASS:
                return findCompassInHands();
            default:
                throw new IllegalArgumentException();
        }
    }

    private static final ITag.INamedTag<Item> MAKES_HUDCOMPASS_VISIBLE = ItemTags.createOptional(new ResourceLocation("hudcompass:makes_hudcompass_visible"), Sets.newHashSet(() -> Items.COMPASS));

    private boolean findCompassInHands()
    {
        if (mc.player == null) return false;

        return mc.player.getMainHandItem().getItem().is(MAKES_HUDCOMPASS_VISIBLE)
                || mc.player.getOffhandItem().getItem().is(MAKES_HUDCOMPASS_VISIBLE);
    }

    private boolean findCompassInInventory()
    {
        if (mc.player == null) return false;

        PlayerInventory inv = mc.player.inventory;
        for (int i = 0; i < inv.getContainerSize(); i++)
        {
            if (inv.getItem(i).getItem().is(MAKES_HUDCOMPASS_VISIBLE))
                return true;
        }
        return false;
    }

    private Vector2f angleFromPoint(Vector3d position, double playerPosX, double playerPosY, double playerPosZ)
    {
        double xd = position.x - playerPosX;
        double yd = position.y - playerPosY;
        double zd = position.z - playerPosZ;
        return new Vector2f((float) Math.toDegrees(-Math.atan2(xd, zd)), (float) yd);
    }

    private void drawCardinalDirection(MatrixStack matrixStack, float yaw, float angle, int xPos, String text)
    {
        float nDist = angleDistance(yaw, angle);
        if (Math.abs(nDist) <= 90)
        {
            float nPos = xPos + nDist;
            fillRect(matrixStack, nPos - 0.5f, 10, nPos + 0.5f, 18, 0x7FFFFFFF);
            if (mc.options.backgroundForChatOnly)
                drawCenteredShadowString(matrixStack, font, text, nPos, 1, 0xFFFFFF);
            else
                drawCenteredBoxedString(matrixStack, font, text, nPos, 1, 0xFFFFFF);
        }
    }

    public void drawCenteredShadowString(MatrixStack matrixStack, FontRenderer font, String text, float x, float y, int color)
    {
        float width = font.width(text);
        font.drawShadow(matrixStack, text, x - width / 2, y, color);
    }

    public static void drawCenteredBoxedString(MatrixStack matrixStack, FontRenderer font, String text, float x, float y, int color)
    {
        Minecraft mc = Minecraft.getInstance();
        float width = font.width(text);
        float height = font.lineHeight;
        float width1 = width + 4;
        float height1 = height + 3;
        float x0 = x - width1 / 2;
        fillRect(matrixStack, x0, y, x0 + width1, y + height1, ((int) MathHelper.clamp(mc.options.textBackgroundOpacity * ((color >> 24) & 0xFF), 0, 255)) << 24);
        font.drawShadow(matrixStack, text, x - width / 2, y + 2, color);

        RenderSystem.enableBlend();
    }

    public static void drawCenteredBoxedString(MatrixStack matrixStack, FontRenderer font, ITextComponent text, float x, float y, int color)
    {
        IReorderingProcessor reodering = text.getVisualOrderText();
        Minecraft mc = Minecraft.getInstance();
        float width = font.width(reodering);
        float height = font.lineHeight;
        float width1 = width + 4;
        float height1 = height + 3;
        float x0 = x - width1 / 2;
        fillRect(matrixStack, x0, y, x0 + width1, y + height1, ((int) MathHelper.clamp(mc.options.textBackgroundOpacity * ((color >> 24) & 0xFF), 0, 255)) << 24);
        font.drawShadow(matrixStack, reodering, x - width / 2, y + 2, color);

        RenderSystem.enableBlend();
    }

    private void drawPoi(PlayerEntity player, MatrixStack matrixStack, float yaw, float angle, float yDelta, int xPos, PointInfo<?> point, boolean isTargetted, float elapsed, Vector3d subtract)
    {
        double fadeSqr = ConfigData.waypointViewDistance * ConfigData.waypointViewDistance;
        double distance2 = subtract.lengthSqr();

        if (distance2 > fadeSqr)
        {
            return;
        }

        double distance = Math.sqrt(distance2);

        double distanceFade = 1 - MathHelper.clamp((distance - ConfigData.waypointFadeDistance) / (ConfigData.waypointViewDistance - ConfigData.waypointFadeDistance), 0, 1);

        int alpha = (int) (255 * distanceFade);

        float nDist = angleDistance(yaw, angle);
        if (alpha > 0 && Math.abs(nDist) <= 90)
        {
            float nPos = xPos + nDist;
            matrixStack.pushPose();
            matrixStack.translate(nPos, 0, 0);

            PointRenderer.renderIcon(point, player, textureManager, matrixStack, 0, 14, alpha);
            boolean showLabel =
                    ConfigData.alwaysShowLabels ||
                            (ConfigData.alwaysShowFocusedLabel && isTargetted) ||
                            (ConfigData.showAllLabelsOnSneak && Screen.hasShiftDown());

            if (ConfigData.animateLabels)
            {
                if (showLabel && point.fade < 255)
                {
                    point.fade = Math.min(point.fade + 35 * elapsed, 255);
                }
                else if (!showLabel && point.fade > 0)
                {
                    point.fade = Math.max(point.fade - 35 * elapsed, 0);
                }
            }
            else
            {
                point.fade = showLabel ? 255 : 0;
            }

            if (point.fade > 3)
                PointRenderer.renderLabel(point, font, matrixStack, 0, 20, (int) point.fade);

            if (point.displayVerticalDistance(player))
            {
                if (yDelta >= 2) drawAboveArrow(matrixStack, nPos, yDelta);
                if (yDelta <= -2) drawBelowArrow(matrixStack, nPos, yDelta);
            }

            matrixStack.popPose();
        }
    }

    private void drawAboveArrow(MatrixStack matrixStack, float nPos, float yDelta)
    {
        int x = yDelta > 10 ? 8 : 0;
        int y = 0;
        textureManager.bind(LOCATION_POI_ICONS);
        RenderSystem.color4f(1, 1, 1, 1);
        blitRect(matrixStack, -4.5f, 4, x, y, 8, 8, 128, 128);
    }

    private void drawBelowArrow(MatrixStack matrixStack, float nPos, float yDelta)
    {
        int x = yDelta < -10 ? 24 : 16;
        int y = 0;
        textureManager.bind(LOCATION_POI_ICONS);
        RenderSystem.color4f(1, 1, 1, 1);
        blitRect(matrixStack, -4.5f, 16, x, y, 8, 8, 128, 128);
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
    private static void fillRect(MatrixStack matrixStack, float x0, float y0, float x1, float y1, int color)
    {
        RenderSystem.enableBlend();
        RenderSystem.disableAlphaTest();

        int a = (color >> 24 & 255);
        int r = (color >> 16 & 255);
        int g = (color >> 8 & 255);
        int b = (color & 255);
        Tessellator tess = Tessellator.getInstance();
        BufferBuilder builder = tess.getBuilder();
        RenderSystem.disableTexture();
        builder.begin(GL11.GL_QUADS, DefaultVertexFormats.POSITION_COLOR);
        Matrix4f matrix = matrixStack.last().pose();
        builder.vertex(matrix, x0, y1, 0.0f).color(r, g, b, a).endVertex();
        builder.vertex(matrix, x1, y1, 0.0f).color(r, g, b, a).endVertex();
        builder.vertex(matrix, x1, y0, 0.0f).color(r, g, b, a).endVertex();
        builder.vertex(matrix, x0, y0, 0.0f).color(r, g, b, a).endVertex();
        tess.end();
        RenderSystem.enableTexture();
    }

    private static void blitRect(MatrixStack matrixStack, float x0, float y0, float xt, float yt, float width, float height, int tWidth, int tHeight)
    {
        RenderSystem.enableBlend();
        RenderSystem.disableAlphaTest();

        float tx0 = xt / tWidth;
        float ty0 = yt / tHeight;
        float tx1 = tx0 + width / tWidth;
        float ty1 = ty0 + height / tHeight;

        float x1 = x0 + width;
        float y1 = y0 + height;

        Tessellator tess = Tessellator.getInstance();
        BufferBuilder builder = tess.getBuilder();
        builder.begin(7, DefaultVertexFormats.POSITION_TEX);
        Matrix4f matrix = matrixStack.last().pose();
        builder.vertex(matrix, x0, y1, 0.0f).uv(tx0, ty1).endVertex();
        builder.vertex(matrix, x1, y1, 0.0f).uv(tx1, ty1).endVertex();
        builder.vertex(matrix, x1, y0, 0.0f).uv(tx1, ty0).endVertex();
        builder.vertex(matrix, x0, y0, 0.0f).uv(tx0, ty0).endVertex();
        tess.end();
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
