package dev.gigaherz.hudcompass;

import com.google.common.collect.Lists;
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

        ClientConfig(ForgeConfigSpec.Builder builder)
        {
            builder.push("display");
            alwaysShowLabels = builder
                    .comment("If set to TRUE, the labels on the compass will always be visible, if FALSE (default), only the closest to the center of the compass will show the name.")
                    .translation("text.hudcompass.config.always_show_labels")
                    .define("alwaysShowLabels", false);
            alwaysShowFocusedLabel = builder
                    .comment("If set to FALSE, the closest waypoint to the center of the compass will not show the label, and sneak will be required to display it.")
                    .translation("text.hudcompass.config.always_show_focused_labels")
                    .define("alwaysShowFocusedLabel", true);
            showAllLabelsOnSneak = builder
                    .comment("If set to FALSE, sneaking will only show the closest waypoint to the center of the compass.")
                    .translation("text.hudcompass.config.show_all_labels_on_sneak")
                    .define("showAllLabelsOnSneak", true);
            animateLabels = builder
                    .comment("If set to FALSE, support for sewing recipes will not be enabled regardless of the mod's presence.")
                    .translation("text.hudcompass.config.disable_anvil_update")
                    .define("animateLabels", true);
            enableXaeroMinimapIntegration = builder
                    .comment("If set to FALSE, Xaero Minimap waypoints won't be displayed in the compass.")
                    .translation("text.hudcompass.config.enable_xaero_minimap")
                    .define("enableXaeroMinimapIntegration", true);
            builder.pop();
        }
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
                    .translation("text.hudcompass.config.enable_vanilla_map")
                    .define("enableVanillaMapIntegration", true);
            enableSpawnPointWaypoint = builder
                    .comment("If set to FALSE, the spawn point location will not be shown.")
                    .translation("text.hudcompass.config.disable_anvil_update")
                    .define("enableXaeroMinimapIntegration", true);
            disableServerHello = builder
                    .comment("If set to TRUE, the server will not advertise itself to the clients, making them work in client-only mode.")
                    .translation("text.hudcompass.config.disable_anvil_update")
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

    public static void refreshClient()
    {
        alwaysShowLabels = CLIENT.alwaysShowLabels.get();
        alwaysShowFocusedLabel = CLIENT.alwaysShowFocusedLabel.get();
        showAllLabelsOnSneak = CLIENT.showAllLabelsOnSneak.get();
        animateLabels = CLIENT.animateLabels.get();
    }

}
