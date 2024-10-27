package dev.gigaherz.hudcompass.integrations.server;

import com.google.common.collect.Maps;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.*;
import dev.gigaherz.hudcompass.ConfigData;
import dev.gigaherz.hudcompass.HudCompass;
import dev.gigaherz.hudcompass.icons.IIconData;
import dev.gigaherz.hudcompass.icons.IconDataSerializer;
import dev.gigaherz.hudcompass.icons.client.IIconRenderer;
import dev.gigaherz.hudcompass.icons.client.IconRendererRegistry;
import dev.gigaherz.hudcompass.waypoints.PointInfoType;
import dev.gigaherz.hudcompass.waypoints.PointsOfInterest;
import dev.gigaherz.hudcompass.waypoints.SpecificPointInfo;
import net.minecraft.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.texture.TextureManager;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.scores.Team;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.common.util.FakePlayer;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

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

    public static final DeferredHolder<PointInfoType<?>, PointInfoType<PlayerWaypoint>> PLAYER_POINT = PIT.register("player", () -> new PointInfoType<>(PlayerWaypoint::new));

    public static final DeferredHolder<IconDataSerializer<?>, PlayerIconData.Serializer> ICON_DATA = IDS.register("player", PlayerIconData.Serializer::new);

    public static void init(IEventBus modEventBus)
    {
        modEventBus.addListener(INSTANCE::clientSetup);

        //MinecraftForge.EVENT_BUS.addListener(INSTANCE::clientTick);
        NeoForge.EVENT_BUS.addListener(INSTANCE::playerTick);
        NeoForge.EVENT_BUS.addListener(INSTANCE::startTracking);
        NeoForge.EVENT_BUS.addListener(INSTANCE::stopTracking);
    }

    private void clientSetup(FMLClientSetupEvent event)
    {
        IconRendererRegistry.registerRenderer(ICON_DATA.get(), new OtherPlayerRenderer());
    }

    private void startTracking(PlayerEvent.StartTracking event)
    {
        Player player = event.getEntity();
        if (player.level().isClientSide)
            return;

        if (event.getTarget() instanceof ServerPlayer target && !(target instanceof FakePlayer))
        {
            var pois = player.getData(HudCompass.POINTS_OF_INTEREST_ATTACHMENT);
            {
                PointsOfInterest.WorldPoints worldPoints = pois.get(player.level());

                PlayerDataAddon addon = pois.getOrCreateAddonData(ADDON_ID, PlayerDataAddon::new);

                var wp = new PlayerWaypoint(target);

                UUID uuid = target.getUUID();
                addon.players.put(uuid, wp);
                addon.teams.put(uuid, target.getTeam());
                addon.playerTeam = player.getTeam();
                if (ConfigData.playerDisplay == ConfigData.PlayerDisplay.ALL || (ConfigData.playerDisplay == ConfigData.PlayerDisplay.TEAM && target.getTeam() == player.getTeam()))
                {
                    worldPoints.addPoint(wp);
                }
            }
        }
    }

    private void stopTracking(PlayerEvent.StopTracking event)
    {
        Player player = event.getEntity();
        if (player.level().isClientSide)
            return;

        if (event.getTarget() instanceof ServerPlayer target && !(target instanceof FakePlayer))
        {
            var pois = player.getData(HudCompass.POINTS_OF_INTEREST_ATTACHMENT);
            {
                PointsOfInterest.WorldPoints worldPoints = pois.get(player.level());

                PlayerDataAddon addon = pois.getOrCreateAddonData(ADDON_ID, PlayerDataAddon::new);

                UUID uuid = target.getUUID();
                var wp = addon.players.get(uuid);
                if (wp != null)
                {
                    worldPoints.removePoint(wp);
                }

                addon.players.remove(uuid);
                addon.teams.remove(uuid);
            }
        }
    }

    private int counter = 0;

    private void playerTick(PlayerTickEvent.Pre event)
    {
        if (ConfigData.playerDisplay != ConfigData.PlayerDisplay.TEAM)
            return;

        if ((++counter) > 20)
        {
            counter = 0;

            Player player = event.getEntity();
            if (player.level().isClientSide)
                return;

            var pois = player.getData(HudCompass.POINTS_OF_INTEREST_ATTACHMENT);
            {
                PointsOfInterest.WorldPoints worldPoints = pois.get(player.level());
                PlayerDataAddon addon = pois.getOrCreateAddonData(ADDON_ID, PlayerDataAddon::new);

                boolean changedTeam = addon.playerTeam != player.getTeam();
                addon.playerTeam = player.getTeam();

                for (var wp : addon.players.values())
                {
                    Player target = player.level().getPlayerByUUID(wp.playerUUID);
                    if (target == null)
                        continue;

                    var currentTeam = target.getTeam();
                    if (changedTeam || addon.teams.get(wp.playerUUID) != currentTeam)
                    {
                        addon.teams.put(wp.playerUUID, currentTeam);
                        worldPoints.removePoint(wp);
                        if (currentTeam == player.getTeam())
                            worldPoints.addPoint(wp);
                    }
                }
            }
        }
    }

    public static class PlayerWaypoint extends SpecificPointInfo<PlayerWaypoint, PlayerIconData>
    {
        private UUID playerUUID;
        private Vec3 position;

        public PlayerWaypoint(@Nonnull Player player)
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
            this.position = Vec3.ZERO;
        }

        @Override
        public Vec3 getPosition()
        {
            return position;
        }

        @Override
        public Vec3 getPosition(Player player, float partialTicks)
        {
            var target = player.level().getPlayerByUUID(playerUUID);
            if (target == null)
                return position;

            return target.getPosition(partialTicks);
        }

        @Override
        protected void serializeAdditional(CompoundTag tag)
        {
            tag.putUUID("Player", playerUUID);
        }

        @Override
        protected void deserializeAdditional(CompoundTag tag)
        {
            playerUUID = tag.getUUID("Player");
            this.getIconData().setPlayer(playerUUID);
        }

        @Override
        protected void serializeAdditional(FriendlyByteBuf buffer)
        {
            buffer.writeUUID(playerUUID);
        }

        @Override
        protected void deserializeAdditional(FriendlyByteBuf buffer)
        {
            playerUUID = buffer.readUUID();
            this.getIconData().setPlayer(playerUUID);
        }

        @Override
        public void tick(Player player)
        {
            if (!player.level().isClientSide)
                return;

            var target = player.level().getPlayerByUUID(playerUUID);
            if (target == null)
                return;

            var newPos = target.position();
            var newLabel = target.getDisplayName();

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
            return ICON_DATA.get();
        }

        public void setPlayer(UUID playerId)
        {
            this.playerId = playerId;
        }

        private static class Serializer extends IconDataSerializer<PlayerIconData>
        {
            @Override
            public CompoundTag write(PlayerIconData data, CompoundTag tag)
            {
                tag.putUUID("Player", data.playerId);
                return tag;
            }

            @Override
            public PlayerIconData read(CompoundTag tag)
            {
                return new PlayerIconData(tag.getUUID("Player"));
            }

            @Override
            public PlayerIconData read(FriendlyByteBuf buffer)
            {
                return new PlayerIconData(buffer.readUUID());
            }

            @Override
            public void write(PlayerIconData data, FriendlyByteBuf buffer)
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
        public void renderIcon(PlayerIconData data, Player player, TextureManager textureManager, GuiGraphics graphics, int x, int y, int alpha)
        {
            if (player.level().getPlayerByUUID(data.playerId) instanceof AbstractClientPlayer clientPlayer)
            {
                var tex = clientPlayer.getSkin().texture();

                drawFaceLayer(graphics, tex, x - 4, y - 4, 8, 8, 8);
                drawFaceLayer(graphics, tex, x - 4.5f, y - 4.5f, 9, 9, 40);
            }
        }

        private static void drawFaceLayer(GuiGraphics graphics, ResourceLocation tex, float x1, float y1, float w, float h, int tx)
        {
            var pMatrix = graphics.pose().last().pose();
            var x2 = x1 + w;
            var y2 = y1 + h;
            var u1 = tx / 64f;
            var u2 = (tx + 8) / 64f;

            var source = Minecraft.getInstance().renderBuffers().bufferSource();
            var bufferbuilder = source.getBuffer(RenderType.guiTextured(tex));

            bufferbuilder.addVertex(pMatrix, x1, y2, 0).setUv(u1, 16f / 64f).setColor(-1);
            bufferbuilder.addVertex(pMatrix, x2, y2, 0).setUv(u2, 16f / 64f).setColor(-1);
            bufferbuilder.addVertex(pMatrix, x2, y1, 0).setUv(u2, 8f / 64f).setColor(-1);
            bufferbuilder.addVertex(pMatrix, x1, y1, 0).setUv(u1, 8f / 64f).setColor(-1);
        }
    }
}
