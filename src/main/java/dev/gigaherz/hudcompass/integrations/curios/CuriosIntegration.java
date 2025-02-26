package dev.gigaherz.hudcompass.integrations.curios;

import net.minecraft.client.Minecraft;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;
import net.minecraftforge.fml.ModList;
import top.theillusivec4.curios.api.CuriosApi;
import top.theillusivec4.curios.api.SlotResult;

import java.util.Optional;

public class CuriosIntegration {
    public static boolean findCompassInCurios(Minecraft mc, TagKey<Item> itemTag) {
        if (mc.player == null) return false;

        // Check if Curios is loaded before using its API
        if (!ModList.get().isLoaded("curios")) return false;

        try {
            // Use Curios API safely
            Optional<SlotResult> curioSlot = CuriosApi.getCuriosHelper()
                    .findFirstCurio(mc.player, itemStack -> itemStack.is(itemTag));

            return curioSlot.isPresent();
        } catch (Exception e) {
            // If something goes wrong, fail gracefully and return false
            return false;
        }
    }
}