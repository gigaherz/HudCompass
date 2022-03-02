package dev.gigaherz.hudcompass;

import net.minecraftforge.common.ForgeConfigSpec;
import org.apache.commons.lang3.tuple.Pair;

public class ConfigData
{
    public static final ServerConfig SERVER;
    public static final ForgeConfigSpec SERVER_SPEC;

    static
    {
        final Pair<ServerConfig, ForgeConfigSpec> specPair = new ForgeConfigSpec.Builder().configure(ServerConfig::new);
        SERVER_SPEC = specPair.getRight();
        SERVER = specPair.getLeft();
    }

    public static final ClientConfig CLIENT;
    public static final ForgeConfigSpec CLIENT_SPEC;

    static
    {
        final Pair<ClientConfig, ForgeConfigSpec> specPair = new ForgeConfigSpec.Builder().configure(ClientConfig::new);
        CLIENT_SPEC = specPair.getRight();
        CLIENT = specPair.getLeft();
    }

    public static final CommonConfig COMMON;
    public static final ForgeConfigSpec COMMON_SPEC;

    static
    {
        final Pair<CommonConfig, ForgeConfigSpec> specPair = new ForgeConfigSpec.Builder().configure(CommonConfig::new);
        COMMON_SPEC = specPair.getRight();
        COMMON = specPair.getLeft();
    }

    public static class ClientConfig
    {
        public final ForgeConfigSpec.BooleanValue alwaysShowLabels;
        public final ForgeConfigSpec.BooleanValue alwaysShowFocusedLabel;
        public final ForgeConfigSpec.BooleanValue showAllLabelsOnSneak;
        public final ForgeConfigSpec.BooleanValue animateLabels;
        public final ForgeConfigSpec.BooleanValue enableXaeroMinimapIntegration;
        public final ForgeConfigSpec.EnumValue<DisplayWhen> displayWhen;
        public final ForgeConfigSpec.DoubleValue waypointFadeDistance;
        public final ForgeConfigSpec.DoubleValue waypointViewDistance;

        ClientConfig(ForgeConfigSpec.Builder builder)
        {
            builder.push("display");
            alwaysShowLabels = builder
                    .comment("If set to TRUE, the labels on the compass will always be visible, if FALSE (default), only the closest to the center of the compass will show the name.")
                    .define("alwaysShowLabels", false);
            alwaysShowFocusedLabel = builder
                    .comment("If set to FALSE, the closest waypoint to the center of the compass will not show the label, and sneak will be required to display it.")
                    .define("alwaysShowFocusedLabel", true);
            showAllLabelsOnSneak = builder
                    .comment("If set to FALSE, sneaking will only show the closest waypoint to the center of the compass.")
                    .define("showAllLabelsOnSneak", true);
            animateLabels = builder
                    .comment("If set to FALSE, support for sewing recipes will not be enabled regardless of the mod's presence.")
                    .define("animateLabels", true);
            displayWhen = builder
                    .comment("Choose when the compass is visible.",
                            " - NEVER: Don't display the compass (the mod remains active, just doesn't render).",
                            " - HAS_COMPASS: Only display HUD if a compass is in the inventory.",
                            " - HOLDING_COMPASS: Only display HUD if a compass is in the hand.",
                            " - ALWAYS: Always display the compass (default).")
                    .defineEnum("displayWhen", DisplayWhen.HOLDING_COMPASS);
            enableXaeroMinimapIntegration = builder
                    .comment("If set to FALSE, Xaero Minimap waypoints won't be displayed in the compass.")
                    .define("enableXaeroMinimapIntegration", true);
            waypointFadeDistance = builder
                    .comment("Sets the distance at which waypoints start to fade. Meaningless if waypointViewDistance=0. If this value is >= waypointViewDistance, it will never fade.")
                    .defineInRange("waypointFadeDistance", 195.0, 0.0, Double.MAX_VALUE);
            waypointViewDistance = builder
                    .comment("Sets the distance at which waypoints stop drawing. If set to 0, waypoints will never disappear.")
                    .defineInRange("waypointViewDistance", 200.0, 0.0, Double.MAX_VALUE);
            builder.pop();
        }
    }

    public enum DisplayWhen
    {
        NEVER,
        HOLDING_COMPASS,
        HAS_COMPASS,
        ALWAYS
    }

    public static class CommonConfig
    {
        public final ForgeConfigSpec.BooleanValue enableVanillaMapIntegration;
        public final ForgeConfigSpec.BooleanValue enableSpawnPointWaypoint;
        public final ForgeConfigSpec.BooleanValue disableServerHello;

        CommonConfig(ForgeConfigSpec.Builder builder)
        {
            builder.push("general");
            enableVanillaMapIntegration = builder
                    .comment("If set to FALSE, vanilla map waypoints won't be displayed in the compass.")
                    .define("enableVanillaMapIntegration", true);
            enableSpawnPointWaypoint = builder
                    .comment("If set to FALSE, the spawn point location will not be shown.")
                    .define("enableXaeroMinimapIntegration", true);
            disableServerHello = builder
                    .comment("If set to TRUE, the server will not advertise itself to the clients, making them work in client-only mode.")
                    .define("disableServerHello", false);
            builder.pop();
        }
    }

    public static class ServerConfig
    {
        ServerConfig(ForgeConfigSpec.Builder builder)
        {
            builder.push("general");
            builder.pop();
        }
    }

    // Baked config data, for performance
    public static boolean alwaysShowLabels;
    public static boolean alwaysShowFocusedLabel;
    public static boolean showAllLabelsOnSneak;
    public static boolean animateLabels;
    public static DisplayWhen displayWhen;
    public static double waypointViewDistance;
    public static double waypointFadeDistance;

    public static void refreshClient()
    {
        alwaysShowLabels = CLIENT.alwaysShowLabels.get();
        alwaysShowFocusedLabel = CLIENT.alwaysShowFocusedLabel.get();
        showAllLabelsOnSneak = CLIENT.showAllLabelsOnSneak.get();
        animateLabels = CLIENT.animateLabels.get();
        displayWhen = CLIENT.displayWhen.get();
        waypointFadeDistance = CLIENT.waypointFadeDistance.get();
        waypointViewDistance = CLIENT.waypointViewDistance.get();
    }

}
