package dev.gigaherz.hudcompass.mixin;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import dev.gigaherz.hudcompass.ConfigData;
import dev.gigaherz.hudcompass.client.HudOverlay;
import net.minecraft.client.gui.Gui;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(Gui.class)
public class GuiMixin
{

    @ModifyExpressionValue(method = "nextContextualInfoState()Lnet/minecraft/client/gui/Gui$ContextualInfo;",
                            at = @At(value = "INVOKE", target = "Lnet/minecraft/client/waypoints/ClientWaypointManager;hasWaypoints()Z"))
    private static boolean hudcompass$suppressLocatorBar(boolean canDisplayLocatorBar)
    {
        return canDisplayLocatorBar && ConfigData.shouldShowLocatorBar();
    }
}
