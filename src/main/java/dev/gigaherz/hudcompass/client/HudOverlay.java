package dev.gigaherz.hudcompass.client;

import com.google.common.collect.Lists;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.*;
import dev.gigaherz.hudcompass.ConfigData;
import dev.gigaherz.hudcompass.HudCompass;
import dev.gigaherz.hudcompass.integrations.curios.CuriosIntegration;
import dev.gigaherz.hudcompass.waypoints.PointInfo;
import dev.gigaherz.hudcompass.waypoints.PointsOfInterest;
import dev.gigaherz.hudcompass.waypoints.client.PointRenderer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.texture.TextureManager;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.phys.Vec2;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RegisterGuiOverlaysEvent;
import net.minecraftforge.client.event.RenderGuiEvent;
import net.minecraftforge.client.event.RenderGuiOverlayEvent;
import net.minecraftforge.client.gui.overlay.ForgeGui;
import net.minecraftforge.client.gui.overlay.IGuiOverlay;
import net.minecraftforge.client.gui.overlay.VanillaGuiOverlay;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLConstructModEvent;
import org.joml.Matrix4f;
import top.theillusivec4.curios.api.CuriosApi;
import top.theillusivec4.curios.api.SlotResult;
import net.minecraft.world.item.Items;

import java.util.List;
import java.util.Optional;

@Mod.EventBusSubscriber(value = Dist.CLIENT, modid = HudCompass.MODID, bus = Mod.EventBusSubscriber.Bus.MOD)
public class HudOverlay implements IGuiOverlay
{
    public static final ResourceLocation LOCATION_MAP_ICONS = new ResourceLocation("minecraft", "textures/map/map_icons.png");
    public static final ResourceLocation LOCATION_POI_ICONS = new ResourceLocation("hudcompass", "textures/poi_icons.png");

    private final Minecraft mc;
    private final Font font;
    private final TextureManager textureManager;

    @SubscribeEvent
    public static void init(FMLConstructModEvent event)
    {
    }

    @SubscribeEvent
    public static void registerOverlay(RegisterGuiOverlaysEvent event)
    {
        event.registerAbove(VanillaGuiOverlay.BOSS_EVENT_PROGRESS.id(), "compass", new HudOverlay());
    }

    private HudOverlay()
    {
        this.mc = Minecraft.getInstance();
        this.font = mc.font;
        this.textureManager = mc.textureManager;
        MinecraftForge.EVENT_BUS.register(this);
    }

    boolean needsPop = false;

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public void preOverlayHigh(RenderGuiOverlayEvent.Pre event)
    {
        if (event.getOverlay() == VanillaGuiOverlay.BOSS_EVENT_PROGRESS.type() && !mc.options.hideGui && canRender())
        {
            PoseStack matrixStack = event.getGuiGraphics().pose();
            matrixStack.pushPose();
            matrixStack.translate(0, 28, 0);
            needsPop = true;
        }
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public void postOverlay(RenderGuiOverlayEvent.Post event)
    {
        if (event.getOverlay() == VanillaGuiOverlay.BOSS_EVENT_PROGRESS.type() && needsPop)
        {
            PoseStack matrixStack = event.getGuiGraphics().pose();
            matrixStack.popPose();
            needsPop = false;
        }
    }

    @SubscribeEvent(priority = EventPriority.LOWEST, receiveCanceled = true)
    public void preRender(RenderGuiEvent.Pre event)
    {
        needsPop = false;
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public void postRender(RenderGuiEvent.Post event)
    {
        if (needsPop)
        {
            PoseStack matrixStack = event.getGuiGraphics().pose();
            matrixStack.popPose();
            needsPop = false;
        }
    }


    @Override
    public void render(ForgeGui gui, GuiGraphics graphics, float _partialTicks, int width, int height)
    {
        if (!canRender()) return;

        if (mc.player == null) return;


        boolean isPaused = mc.isPaused();

        float elapsed = isPaused ? 0 : mc.getDeltaFrameTime();
        float partialTicks = isPaused ? 0 : _partialTicks;

        int xPos = mc.getWindow().getGuiScaledWidth() / 2;
        float yaw = Mth.lerp(partialTicks, mc.player.yHeadRotO, mc.player.yHeadRot) % 360;
        //if (yaw > 180) yaw -= 360;
        if (yaw < 0) yaw += 360;

        RenderSystem.enableBlend();

        fillRect(graphics, xPos - 90, 10, xPos + 90, 18, 0x3f000000);

        //drawCenteredString(font, String.format("%f", yaw), xPos, 28, 0xFFFFFF);

        drawCardinalDirection(graphics, yaw, 0, xPos, "S");
        drawCardinalDirection(graphics, yaw, 90, xPos, "W");
        drawCardinalDirection(graphics, yaw, 180, xPos, "N");
        drawCardinalDirection(graphics, yaw, 270, xPos, "E");

        fillRect(graphics, xPos - 1.5f, 10, xPos - 0.5f, 18, 0x3FFFFFFF);
        fillRect(graphics, xPos + 0.5f, 10, xPos + 1.5f, 18, 0x3FFFFFFF);

        fillRect(graphics, xPos - 45 - 0.5f, 10, xPos - 45 + 0.5f, 18, 0x3FFFFFFF);
        fillRect(graphics, xPos + 45 - 0.5f, 10, xPos + 45 + 0.5f, 18, 0x3FFFFFFF);

        final Player player = mc.player;
        double playerPosX = Mth.lerp(partialTicks, mc.player.xo, mc.player.getX());
        double playerPosY = Mth.lerp(partialTicks, mc.player.yo, mc.player.getY());
        double playerPosZ = Mth.lerp(partialTicks, mc.player.zo, mc.player.getZ());

        var playerPosition = new Vec3(playerPosX, playerPosY, playerPosZ);

        final float yaw0 = yaw;
        final float elapsed0 = elapsed;
        player.getCapability(PointsOfInterest.INSTANCE).ifPresent(pts -> {
            List<PointInfo<?>> sortedPoints = Lists.newArrayList(pts.get(player.level()).getPoints());
            sortedPoints.sort((a, b) -> {
                Vec3 positionA = a.getPosition(player, partialTicks);
                Vec3 positionB = b.getPosition(player, partialTicks);
                float angleA = Math.abs(angleDistance(yaw0, angleFromPoint(positionA, playerPosX, playerPosY, playerPosZ).x));
                float angleB = Math.abs(angleDistance(yaw0, angleFromPoint(positionB, playerPosX, playerPosY, playerPosZ).x));
                return (int) Math.signum(angleB - angleA);
            });
            for (PointInfo<?> point : sortedPoints)
            {
                Vec3 position = point.getPosition(player, partialTicks);
                Vec2 angleYd = angleFromPoint(position, playerPosX, playerPosY, playerPosZ);
                drawPoi(player, graphics, yaw0, angleYd.x, angleYd.y, xPos, point, point == pts.getTargetted(), elapsed0, position.subtract(playerPosition));
            }
        });
    }

    private boolean canRender()
    {
        if (mc.player == null) return false;

        if (mc.options.keyPlayerList.isDown())
            return false;

        boolean hasCompassInCurios = ModList.get().isLoaded("curios") && findCompassInCurios(); // Only check Curios if installed

        return switch (ConfigData.displayWhen)
                {
                    case NEVER -> false;
                    case ALWAYS -> true;
                    case HAS_COMPASS -> findCompassInInventory() || hasCompassInCurios; // Added optional Curios check
                    case HOLDING_COMPASS -> findCompassInHands();
                };
    }

    private static final TagKey<Item> MAKES_HUDCOMPASS_VISIBLE = TagKey.create(Registries.ITEM, new ResourceLocation("hudcompass:makes_hudcompass_visible"));

    private boolean findCompassInHands()
    {
        if (mc.player == null) return false;

        return mc.player.getMainHandItem().is(MAKES_HUDCOMPASS_VISIBLE)
                || mc.player.getOffhandItem().is(MAKES_HUDCOMPASS_VISIBLE);
    }

    private boolean findCompassInInventory()
    {
        if (mc.player == null) return false;

        Inventory inv = mc.player.getInventory();
        for (int i = 0; i < inv.getContainerSize(); i++)
        {
            if (inv.getItem(i).is(MAKES_HUDCOMPASS_VISIBLE))
                return true;
        }
        return false;
    }

    private boolean findCompassInCurios() {
        return CuriosIntegration.findCompassInCurios(mc, MAKES_HUDCOMPASS_VISIBLE);
    }

    private Vec2 angleFromPoint(Vec3 position, double playerPosX, double playerPosY, double playerPosZ)
    {
        double xd = position.x - playerPosX;
        double yd = position.y - playerPosY;
        double zd = position.z - playerPosZ;
        return new Vec2((float) Math.toDegrees(-Math.atan2(xd, zd)), (float) yd);
    }

    private void drawCardinalDirection(GuiGraphics graphics, float yaw, float angle, int xPos, String text)
    {
        float nDist = angleDistance(yaw, angle);
        if (Math.abs(nDist) <= 90)
        {
            float nPos = xPos + nDist;
            fillRect(graphics, nPos - 0.5f, 10, nPos + 0.5f, 18, 0x7FFFFFFF);
            if (mc.options.backgroundForChatOnly().get())
                drawCenteredShadowString(graphics, font, text, nPos, 1, 0xFFFFFF);
            else
                drawCenteredBoxedString(graphics, font, text, nPos, 1, 0xFFFFFF);
        }
    }

    public void drawCenteredShadowString(GuiGraphics graphics, Font font, String text, float x, float y, int color)
    {
        float width = font.width(text);
        graphics.drawString(font, text, (x - width / 2), y, color, true);
    }

    public static void drawCenteredBoxedString(GuiGraphics graphics, Font font, String text, float x, float y, int color)
    {
        Minecraft mc = Minecraft.getInstance();
        float width = font.width(text);
        float height = font.lineHeight;
        float width1 = width + 4;
        float height1 = height + 3;
        float x0 = x - width1 / 2;

        int backgroundColor = ((int) Mth.clamp(mc.options.textBackgroundOpacity().get() * ((color >> 24) & 0xFF), 0, 255)) << 24;
        fillRect(graphics, x0, y, x0 + width1, y + height1, backgroundColor);

        graphics.drawString(font, text, x - width / 2, y + 2, color, false);

        RenderSystem.enableBlend();
    }

    public static void drawCenteredBoxedString(GuiGraphics graphics, Font font, Component text, float x, float y, int color)
    {
        FormattedCharSequence reodering = text.getVisualOrderText();
        Minecraft mc = Minecraft.getInstance();
        float width = font.width(reodering);
        float height = font.lineHeight;
        float width1 = width + 4;
        float height1 = height + 3;
        float x0 = x - width1 / 2;
        fillRect(graphics, x0, y, x0 + width1, y + height1, ((int) Mth.clamp(mc.options.textBackgroundOpacity().get() * ((color >> 24) & 0xFF), 0, 255)) << 24);
        graphics.drawString(font, reodering, x - width / 2, y + 2, color, true);

        RenderSystem.enableBlend();
    }

    private void drawPoi(Player player, GuiGraphics graphics, float yaw, float angle, float yDelta, int xPos, PointInfo<?> point, boolean isTargetted, float elapsed, Vec3 subtract)
    {
        var fadeSqr = ConfigData.waypointViewDistance * ConfigData.waypointViewDistance;
        double distance2 = subtract.lengthSqr();

        if (distance2 > fadeSqr)
        {
            return;
        }

        double distance = Math.sqrt(distance2);

        var distanceFade = 1 - Mth.clamp((distance - ConfigData.waypointFadeDistance) / (ConfigData.waypointViewDistance - ConfigData.waypointFadeDistance), 0, 1);

        var alpha = (int) (255 * distanceFade);

        float nDist = angleDistance(yaw, angle);
        if (alpha > 0 && Math.abs(nDist) <= 90)
        {
            float nPos = xPos + nDist;
            var poseStack = graphics.pose();
            poseStack.pushPose();
            poseStack.translate(nPos, 0, 0);

            PointRenderer.renderIcon(point, player, textureManager, graphics, 0, 14, alpha);
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

            var pointFade = distanceFade * point.fade;

            if (pointFade > 4)
                PointRenderer.renderLabel(point, font, graphics, 0, 20, (int) pointFade);

            if (point.displayVerticalDistance(player))
            {
                if (yDelta >= 2) drawAboveArrow(graphics, yDelta, alpha);
                if (yDelta <= -2) drawBelowArrow(graphics, yDelta, alpha);
            }

            poseStack.popPose();
        }
    }

    private void drawAboveArrow(GuiGraphics graphics, float yDelta, int alpha)
    {
        int x = yDelta > 10 ? 8 : 0;
        int y = 0;
        blitRect(graphics, -4.5f, 4, x, y, 8, 8, 128, 128, LOCATION_POI_ICONS, alpha);
    }

    private void drawBelowArrow(GuiGraphics graphics, float yDelta, int alpha)
    {
        int x = yDelta < -10 ? 24 : 16;
        int y = 0;
        blitRect(graphics, -4.5f, 16, x, y, 8, 8, 128, 128, LOCATION_POI_ICONS, alpha);
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
    private static void fillRect(GuiGraphics graphics, float x0, float y0, float x1, float y1, int color)
    {
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.setShader(GameRenderer::getPositionColorShader);
        RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, 1.0F);

        int a = (color >> 24 & 255);
        int r = (color >> 16 & 255);
        int g = (color >> 8 & 255);
        int b = (color & 255);
        Tesselator tess = Tesselator.getInstance();
        BufferBuilder builder = tess.getBuilder();
        builder.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_COLOR);
        Matrix4f matrix = graphics.pose().last().pose();
        builder.vertex(matrix, x0, y1, 0.0f).color(r, g, b, a).endVertex();
        builder.vertex(matrix, x1, y1, 0.0f).color(r, g, b, a).endVertex();
        builder.vertex(matrix, x1, y0, 0.0f).color(r, g, b, a).endVertex();
        builder.vertex(matrix, x0, y0, 0.0f).color(r, g, b, a).endVertex();
        tess.end();
    }

    private static void blitRect(GuiGraphics graphics, float x0, float y0, float xt, float yt, float width, float height, int tWidth, int tHeight, ResourceLocation texture, int alpha)
    {
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.setShader(GameRenderer::getPositionTexShader);
        RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, alpha / 255.0f);
        RenderSystem.setShaderTexture(0, texture);

        float tx0 = xt / tWidth;
        float ty0 = yt / tHeight;
        float tx1 = tx0 + width / tWidth;
        float ty1 = ty0 + height / tHeight;

        float x1 = x0 + width;
        float y1 = y0 + height;

        Tesselator tess = Tesselator.getInstance();
        BufferBuilder builder = tess.getBuilder();
        builder.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_TEX);
        Matrix4f matrix = graphics.pose().last().pose();
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
