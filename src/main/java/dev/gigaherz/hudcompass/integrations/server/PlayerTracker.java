package dev.gigaherz.hudcompass.integrations.server;

import com.google.common.collect.Maps;
import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import dev.gigaherz.hudcompass.ConfigData;
import dev.gigaherz.hudcompass.HudCompass;
import dev.gigaherz.hudcompass.client.HudOverlay;
import dev.gigaherz.hudcompass.icons.BasicIconData;
import dev.gigaherz.hudcompass.icons.IIconData;
import dev.gigaherz.hudcompass.icons.IconDataSerializer;
import dev.gigaherz.hudcompass.icons.client.IIconRenderer;
import dev.gigaherz.hudcompass.icons.client.IconRendererRegistry;
import dev.gigaherz.hudcompass.waypoints.*;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.texture.TextureManager;
import net.minecraft.core.UUIDUtil;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Util;
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
import java.util.Optional;
import java.util.UUID;

public class PlayerTracker
{
    public static final PlayerTracker INSTANCE = new PlayerTracker();

    private static final Identifier ADDON_ID = HudCompass.location("player_tracker");

    private static final DeferredRegister<PointInfoType<?>> PIT = HudCompass.POINT_INFO_TYPES;
    private static final DeferredRegister<IconDataSerializer<?>> IDS = HudCompass.ICON_DATA_SERIALIZERS;

    public static final DeferredHolder<PointInfoType<?>, PointInfoType<PlayerWaypoint>>
            PLAYER_POINT = PIT.register("player", () -> new PointInfoType<>(PlayerWaypoint.CODEC, PlayerWaypoint.STREAM_CODEC));

    public static final DeferredHolder<IconDataSerializer<?>, IconDataSerializer<PlayerIconData>>
            ICON_DATA = IDS.register("player", () -> new IconDataSerializer<>(PlayerIconData.CODEC, PlayerIconData.STREAM_CODEC));

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
        if (player.level().isClientSide())
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
        if (player.level().isClientSide())
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
            if (player.level().isClientSide())
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
        public static final MapCodec<PlayerWaypoint> CODEC = RecordCodecBuilder.mapCodec(instance ->
                codecFragment(instance).and(UUIDUtil.CODEC.fieldOf("player").forGetter(e -> e.playerUUID)).apply(instance, PlayerWaypoint::new)
        );

        public static final StreamCodec<RegistryFriendlyByteBuf, PlayerWaypoint> STREAM_CODEC = PointInfo.streamCodec(
                UUIDUtil.STREAM_CODEC, e -> e.playerUUID,
                PlayerWaypoint::new
        );

        private final UUID playerUUID;
        private Vec3 position;

        @SuppressWarnings("OptionalUsedAsFieldOrParameterType")
        protected PlayerWaypoint(UUID internalId, Optional<Component> label, boolean displayVerticalDistance, IIconData<?> iconData, UUID playerUUID)
        {
            super(PLAYER_POINT.get(), true, internalId, label, displayVerticalDistance, iconData);
            this.playerUUID = playerUUID;
        }

        public PlayerWaypoint(@Nonnull Player player)
        {
            super(PLAYER_POINT.get(), true, player.getDisplayName(), new PlayerIconData(player.getUUID()));

            dynamic();

            this.playerUUID = player.getUUID();
            this.position = player.position();
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
        public void tick(Player player)
        {
            if (!player.level().isClientSide())
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

    public static class PlayerIconData implements IIconData<PlayerIconData>
    {
        public static final MapCodec<PlayerIconData> CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
                UUIDUtil.CODEC.fieldOf("player").forGetter(e -> e.playerId)
        ).apply(instance, PlayerIconData::new));

        public static final StreamCodec<RegistryFriendlyByteBuf, PlayerIconData> STREAM_CODEC = StreamCodec.composite(
                UUIDUtil.STREAM_CODEC, (e -> e.playerId),
                PlayerIconData::new
        );

        private UUID playerId = Util.NIL_UUID;

        public PlayerIconData(UUID player)
        {
            playerId = player;
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
    }

    private static class PlayerDataAddon
    {
        public Map<UUID, PlayerWaypoint> players = Maps.newHashMap();
        public Map<UUID, Team> teams = Maps.newHashMap();
        @Nullable
        public Team playerTeam;
    }

    public static class OtherPlayerRenderer implements IIconRenderer<PlayerIconData>
    {
        @Override
        public void renderIcon(PlayerIconData data, Player player, TextureManager textureManager, GuiGraphicsExtractor graphics, int x, int y, int alpha)
        {
            drawUUID(player, data.playerId, graphics, x, y, alpha);
        }

        public static boolean drawUUID(Player player, UUID uuid, GuiGraphicsExtractor graphics, int x, int y, int alpha)
        {
            if (player.level().getPlayerByUUID(uuid) instanceof AbstractClientPlayer clientPlayer)
            {
                var tex = clientPlayer.getSkin().body().texturePath();

                drawFaceLayer(graphics, tex, x - 4, y - 4, 8, 8, 8);
                drawFaceLayer(graphics, tex, x - 4.5f, y - 4.5f, 9, 9, 40);
                return true;
            }

            return false;
        }

        private static void drawFaceLayer(GuiGraphicsExtractor graphics, Identifier tex, float x1, float y1, float w, float h, int tx)
        {
            var x2 = x1 + w;
            var y2 = y1 + h;
            var u1 = tx / 64f;
            var u2 = (tx + 8) / 64f;

            HudOverlay.blitRaw(graphics, tex, x1, x2, y1, y2, u1, 8/64f,  u2, 16/64f, 1, 1, 1, 1);

        }
    }
}
