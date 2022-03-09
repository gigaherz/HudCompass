package dev.gigaherz.hudcompass.integrations.server;

import com.google.common.collect.Maps;
import com.mojang.blaze3d.matrix.MatrixStack;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.*;
import dev.gigaherz.hudcompass.ConfigData;
import dev.gigaherz.hudcompass.HudCompass;
import dev.gigaherz.hudcompass.icons.IIconData;
import dev.gigaherz.hudcompass.icons.IconDataSerializer;
import dev.gigaherz.hudcompass.icons.client.IIconRenderer;
import dev.gigaherz.hudcompass.icons.client.IconRendererRegistry;
import dev.gigaherz.hudcompass.waypoints.PointInfo;
import dev.gigaherz.hudcompass.waypoints.PointInfoType;
import dev.gigaherz.hudcompass.waypoints.PointsOfInterest;
import net.minecraft.client.Minecraft;
import net.minecraft.client.entity.player.AbstractClientPlayerEntity;
import net.minecraft.client.renderer.BufferBuilder;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.client.renderer.WorldVertexBufferUploader;
import net.minecraft.client.renderer.texture.TextureManager;
import net.minecraft.client.renderer.vertex.DefaultVertexFormats;
import net.minecraft.client.renderer.vertex.VertexFormat;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.player.ServerPlayerEntity;
import net.minecraft.nbt.CompoundNBT;
import net.minecraft.network.PacketBuffer;
import net.minecraft.scoreboard.Team;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.Util;
import net.minecraft.util.math.vector.Matrix4f;
import net.minecraft.util.math.vector.Vector3d;
import net.minecraft.util.text.ITextComponent;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.common.util.FakePlayer;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.RegistryObject;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ObjectHolder;
import org.lwjgl.opengl.GL11;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.Map;
import java.util.UUID;

public class PlayerTracker
{
    public static final PlayerTracker INSTANCE = new PlayerTracker();

    private static final ResourceLocation ADDON_ID = HudCompass.location("player_tracker");

    private static final DeferredRegister<PointInfoType<?>> PIT = HudCompass.POINT_INFO_TYPES;
    private static final DeferredRegister<IconDataSerializer<?>> IDS = HudCompass.ICON_DATA_SERIALIZERS;

    public static final RegistryObject<PointInfoType<PlayerWaypoint>> PLAYER_POINT = PIT.register("player", () -> new PointInfoType<>(PlayerWaypoint::new));

    public static final RegistryObject<PlayerIconData.Serializer> ICON_DATA = IDS.register("player", PlayerIconData.Serializer::new);

    public static void init()
    {
        IEventBus modEventBus = FMLJavaModLoadingContext.get().getModEventBus();

        modEventBus.addListener(INSTANCE::clientSetup);

        //MinecraftForge.EVENT_BUS.addListener(INSTANCE::clientTick);
        MinecraftForge.EVENT_BUS.addListener(INSTANCE::playerTick);
        MinecraftForge.EVENT_BUS.addListener(INSTANCE::startTracking);
        MinecraftForge.EVENT_BUS.addListener(INSTANCE::stopTracking);
    }

    private void clientSetup(FMLClientSetupEvent event)
    {
        IconRendererRegistry.registerRenderer(ICON_DATA.get(), new OtherPlayerRenderer());
    }

    private void startTracking(PlayerEvent.StartTracking event)
    {
        PlayerEntity player = event.getPlayer();
        if (player.level.isClientSide)
            return;

        if (event.getTarget() instanceof ServerPlayerEntity)
        {
            ServerPlayerEntity target = (ServerPlayerEntity) event.getTarget();
            if (!(target instanceof FakePlayer))
            {
                player.getCapability(PointsOfInterest.INSTANCE).ifPresent((pois) -> {
                    PointsOfInterest.WorldPoints worldPoints = pois.get(player.level);

                    PlayerDataAddon addon = pois.getOrCreateAddonData(ADDON_ID, PlayerDataAddon::new);

                    PlayerWaypoint wp = new PlayerWaypoint(target);

                    UUID uuid = target.getUUID();
                    addon.players.put(uuid, wp);
                    addon.teams.put(uuid, target.getTeam());
                    addon.playerTeam = player.getTeam();
                    if (ConfigData.playerDisplay == ConfigData.PlayerDisplay.ALL || (ConfigData.playerDisplay == ConfigData.PlayerDisplay.TEAM && target.getTeam() == player.getTeam()))
                    {
                        worldPoints.addPoint(wp);
                    }
                });
            }
        }
    }

    private void stopTracking(PlayerEvent.StopTracking event)
    {
        PlayerEntity player = event.getPlayer();
        if (player.level.isClientSide)
            return;

        if (event.getTarget() instanceof ServerPlayerEntity)
        {
            ServerPlayerEntity target = (ServerPlayerEntity) event.getTarget();
            if (!(target instanceof FakePlayer))
            {
                player.getCapability(PointsOfInterest.INSTANCE).ifPresent((pois) -> {
                    PointsOfInterest.WorldPoints worldPoints = pois.get(player.level);

                    PlayerDataAddon addon = pois.getOrCreateAddonData(ADDON_ID, PlayerDataAddon::new);

                    UUID uuid = target.getUUID();
                    PlayerWaypoint wp = addon.players.get(uuid);
                    if (wp != null)
                    {
                        worldPoints.removePoint(wp);
                    }

                    addon.players.remove(uuid);
                    addon.teams.remove(uuid);
                });
            }
        }
    }

    private int counter = 0;

    private void playerTick(TickEvent.PlayerTickEvent event)
    {
        if (event.phase != TickEvent.Phase.END)
            return;

        if (ConfigData.playerDisplay != ConfigData.PlayerDisplay.TEAM)
            return;

        if ((++counter) > 20)
        {
            counter = 0;

            PlayerEntity player = event.player;
            if (player.level.isClientSide)
                return;

            player.getCapability(PointsOfInterest.INSTANCE).ifPresent((pois) -> {
                PointsOfInterest.WorldPoints worldPoints = pois.get(player.level);
                PlayerDataAddon addon = pois.getOrCreateAddonData(ADDON_ID, PlayerDataAddon::new);

                boolean changedTeam = addon.playerTeam != player.getTeam();
                addon.playerTeam = player.getTeam();

                for (PlayerWaypoint wp : addon.players.values())
                {
                    PlayerEntity target = player.level.getPlayerByUUID(wp.playerUUID);
                    if (target == null)
                        continue;

                    Team currentTeam = target.getTeam();
                    if (changedTeam || addon.teams.get(wp.playerUUID) != currentTeam)
                    {
                        addon.teams.put(wp.playerUUID, currentTeam);
                        worldPoints.removePoint(wp);
                        if (currentTeam == player.getTeam())
                            worldPoints.addPoint(wp);
                    }
                }
            });
        }
    }

    public static class PlayerWaypoint extends PointInfo<PlayerWaypoint>
    {
        private UUID playerUUID;
        private Vector3d position;

        public PlayerWaypoint(@Nonnull PlayerEntity player)
        {
            super(PLAYER_POINT.get(), true, player.getDisplayName(), new PlayerIconData(player.getUUID()));

            dynamic();

            this.playerUUID = player.getUUID();
            this.position = player.position();
        }

        public PlayerWaypoint()
        {
            super(PLAYER_POINT.get(), true, null, new PlayerIconData());

            dynamic();

            this.playerUUID = Util.NIL_UUID;
            this.position = Vector3d.ZERO;
        }

        @Override
        public Vector3d getPosition()
        {
            return position;
        }

        @Override
        public Vector3d getPosition(PlayerEntity player, float partialTicks)
        {
            PlayerEntity target = player.level.getPlayerByUUID(playerUUID);
            if (target == null)
                return position;

            return target.getPosition(partialTicks);
        }

        @Override
        protected void serializeAdditional(CompoundNBT tag)
        {
            tag.putUUID("PlayerEntity", playerUUID);
        }

        @Override
        protected void deserializeAdditional(CompoundNBT tag)
        {
            playerUUID = tag.getUUID("PlayerEntity");
            if (this.getIconData() instanceof PlayerIconData)
            {
                PlayerIconData iconData = (PlayerIconData) this.getIconData();
                iconData.setPlayer(playerUUID);
            }
        }

        @Override
        protected void serializeAdditional(PacketBuffer buffer)
        {
            buffer.writeUUID(playerUUID);
        }

        @Override
        protected void deserializeAdditional(PacketBuffer buffer)
        {
            playerUUID = buffer.readUUID();
            if (this.getIconData() instanceof PlayerIconData)
            {
                PlayerIconData iconData = (PlayerIconData) this.getIconData();
                iconData.setPlayer(playerUUID);
            }
        }

        @Override
        public void tick(PlayerEntity player)
        {
            if (!(player.level instanceof ClientWorld))
                return;

            PlayerEntity target = player.level.getPlayerByUUID(playerUUID);
            if (target == null)
                return;

            Vector3d newPos = target.position();
            ITextComponent newLabel = target.getDisplayName();

            position = newPos;
            setLabel(newLabel);
        }
    }

    private static class PlayerIconData implements IIconData<PlayerIconData>
    {
        private UUID playerId = Util.NIL_UUID;

        public PlayerIconData(UUID player)
        {
            playerId = player;
        }

        public PlayerIconData()
        {
        }

        @Override
        public IconDataSerializer<PlayerIconData> getSerializer()
        {
            return Serializer.INSTANCE;
        }

        public void setPlayer(UUID playerId)
        {
            this.playerId = playerId;
        }

        private static class Serializer extends IconDataSerializer<PlayerIconData>
        {
            @ObjectHolder("hudcompass:player")
            public static Serializer INSTANCE;

            @Override
            public CompoundNBT write(PlayerIconData data, CompoundNBT tag)
            {
                tag.putUUID("PlayerEntity", data.playerId);
                return tag;
            }

            @Override
            public PlayerIconData read(CompoundNBT tag)
            {
                return new PlayerIconData(tag.getUUID("PlayerEntity"));
            }

            @Override
            public PlayerIconData read(PacketBuffer buffer)
            {
                return new PlayerIconData(buffer.readUUID());
            }

            @Override
            public void write(PlayerIconData data, PacketBuffer buffer)
            {
                buffer.writeUUID(data.playerId);
            }
        }
    }

    private static class PlayerDataAddon
    {
        public Map<UUID, PlayerWaypoint> players = Maps.newHashMap();
        public Map<UUID, Team> teams = Maps.newHashMap();
        @Nullable
        public Team playerTeam;
    }

    private static class OtherPlayerRenderer implements IIconRenderer<PlayerIconData>
    {
        @Override
        public void renderIcon(PlayerIconData data, PlayerEntity player, TextureManager textureManager, MatrixStack matrixStack, int x, int y, int alpha)
        {
            PlayerEntity target = player.level.getPlayerByUUID(data.playerId);
            if (target instanceof AbstractClientPlayerEntity)
            {
                AbstractClientPlayerEntity clientPlayer = (AbstractClientPlayerEntity) target;

                ResourceLocation tex = clientPlayer.getSkinTextureLocation();

                Minecraft.getInstance().getTextureManager().bind(tex);
                RenderSystem.color4f(1, 1, 1, 1);

                drawFaceLayer(matrixStack, x - 4, y - 4, 8, 8, 8);
                drawFaceLayer(matrixStack, x - 4.5f, y - 4.5f, 9, 9, 40);
            }
        }

        private static void drawFaceLayer(MatrixStack pose, float x1, float y1, float w, float h, int tx)
        {
            Matrix4f pMatrix = pose.last().pose();
            float x2 = x1 + w;
            float y2 = y1 + h;
            float u1 = tx / 64f;
            float u2 = (tx + 8) / 64f;

            BufferBuilder bufferbuilder = Tessellator.getInstance().getBuilder();
            bufferbuilder.begin(GL11.GL_QUADS, DefaultVertexFormats.POSITION_TEX);
            bufferbuilder.vertex(pMatrix, x1, y2, 0).uv(u1, 16f/64f).endVertex();
            bufferbuilder.vertex(pMatrix, x2, y2, 0).uv(u2, 16f/64f).endVertex();
            bufferbuilder.vertex(pMatrix, x2, y1, 0).uv(u2, 8f/64f).endVertex();
            bufferbuilder.vertex(pMatrix, x1, y1, 0).uv(u1, 8f/64f).endVertex();
            bufferbuilder.end();
            WorldVertexBufferUploader.end(bufferbuilder);
        }
    }
}
